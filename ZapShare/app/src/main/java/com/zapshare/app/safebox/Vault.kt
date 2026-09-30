package com.zapshare.app.safebox

import android.content.Context
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.zapshare.app.data.Prefs
import com.zapshare.app.util.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Safebox: private, encrypted file vault.
 *  - Each file is encrypted with AES-256-GCM in 1 MB chunks (works for multi-GB videos without OOM).
 *  - The data key is random and wrapped by a non-exportable Android Keystore key (hardware-backed where available).
 *  - Access is gated by a PIN (PBKDF2-hashed) and optionally fingerprint / face unlock.
 *  - File names are hidden too: stored as random ids with an encrypted index.
 */
object Vault {
    data class Entry(val id: String, val name: String, val mime: String, val size: Long, val added: Long)

    private const val KEK_ALIAS = "zapshare_vault_kek"
    private const val MAGIC = 0x5A56_4C54 // "ZVLT"
    private const val CHUNK = 1 shl 20
    private const val PBKDF2_ITER = 60_000

    val unlocked = MutableStateFlow(false)
    val entries = MutableStateFlow<List<Entry>>(emptyList())
    /** Skip the background auto-lock while the system file picker is open. */
    var suppressAutoLock = false
    private var dek: SecretKey? = null
    private val rnd = SecureRandom()

    private fun dir(ctx: Context) = File(ctx.filesDir, "vault").apply { mkdirs() }
    private fun indexFile(ctx: Context) = File(dir(ctx), "index.bin")
    private fun tmpDir(ctx: Context) = File(ctx.cacheDir, "vault_tmp").apply { mkdirs() }

    fun isSetUp() = Prefs.vaultPinHash != null && Prefs.vaultDek != null

    // ------------------------------------------------------------------ PIN
    suspend fun setup(ctx: Context, pin: String) = withContext(Dispatchers.IO) {
        val salt = ByteArray(16).also(rnd::nextBytes)
        val raw = ByteArray(32).also(rnd::nextBytes)
        Prefs.vaultSalt = b64(salt)
        Prefs.vaultPinHash = b64(pbkdf2(pin, salt))
        Prefs.vaultDek = wrap(raw)
        dek = SecretKeySpec(raw, "AES")
        entries.value = emptyList()
        saveIndex(ctx)
        unlocked.value = true
    }

    suspend fun unlockWithPin(ctx: Context, pin: String): Boolean = withContext(Dispatchers.IO) {
        val salt = Prefs.vaultSalt?.let(::unb64) ?: return@withContext false
        val expected = Prefs.vaultPinHash?.let(::unb64) ?: return@withContext false
        if (!MessageDigest.isEqual(expected, pbkdf2(pin, salt))) return@withContext false
        open(ctx); true
    }

    /** Called after a successful BiometricPrompt. */
    suspend fun unlockAfterBiometric(ctx: Context) = withContext(Dispatchers.IO) { open(ctx) }

    suspend fun changePin(oldPin: String, newPin: String, ctx: Context): Boolean {
        if (!unlockWithPin(ctx, oldPin)) return false
        val salt = ByteArray(16).also(rnd::nextBytes)
        Prefs.vaultSalt = b64(salt)
        Prefs.vaultPinHash = b64(withContext(Dispatchers.IO) { pbkdf2(newPin, salt) })
        return true
    }

    private fun open(ctx: Context) {
        dek = SecretKeySpec(unwrap(Prefs.vaultDek!!), "AES")
        loadIndex(ctx)
        unlocked.value = true
    }

    fun lock(ctx: Context) {
        dek = null
        entries.value = emptyList()
        unlocked.value = false
        tmpDir(ctx).deleteRecursively()
    }

    // ------------------------------------------------------------------ files
    suspend fun add(ctx: Context, uri: Uri): Entry = withContext(Dispatchers.IO) {
        val key = dek ?: throw IllegalStateException("Locked")
        val meta = FileUtils.queryMeta(ctx, uri)
        val id = UUID.randomUUID().toString()
        val target = File(dir(ctx), "$id.bin")
        try {
            openSource(ctx, uri).use { ins ->
                FileOutputStream(target).use { encrypt(ins, it, key) }
            }
        } catch (e: Exception) { target.delete(); throw e }
        val size = if (meta.size > 0) meta.size else -1
        val e = Entry(id, meta.name, meta.mime, size, System.currentTimeMillis())
        entries.value = listOf(e) + entries.value
        saveIndex(ctx)
        e
    }

    /** Decrypts into a private temp file for previewing. Deleted when the vault locks. */
    suspend fun openTemp(ctx: Context, e: Entry): File = withContext(Dispatchers.IO) {
        val key = dek ?: throw IllegalStateException("Locked")
        val out = File(tmpDir(ctx), FileUtils.safeName(e.name))
        if (!out.exists()) {
            FileInputStream(File(dir(ctx), "${e.id}.bin")).use { ins ->
                FileOutputStream(out).use { decrypt(ins, it, key) }
            }
        }
        out
    }

    /** Moves the file back to public storage (Download/ZapShare/Restored). */
    suspend fun restore(ctx: Context, e: Entry): Uri = withContext(Dispatchers.IO) {
        val key = dek ?: throw IllegalStateException("Locked")
        val out = FileUtils.createOutput(ctx, e.name, e.mime, "Restored")
        var ok = false
        try {
            FileInputStream(File(dir(ctx), "${e.id}.bin")).use { ins -> out.stream.use { decrypt(ins, it, key) } }
            ok = true
        } finally { FileUtils.finishOutput(ctx, out, ok) }
        delete(ctx, e)
        out.uri
    }

    fun delete(ctx: Context, e: Entry) {
        File(dir(ctx), "${e.id}.bin").delete()
        entries.value = entries.value.filterNot { it.id == e.id }
        saveIndex(ctx)
    }

    private fun openSource(ctx: Context, uri: Uri): InputStream {
        if (uri.scheme == "file") {
            val path = uri.path ?: throw FileNotFoundException("Missing file path")
            return FileInputStream(path)
        }
        return ctx.contentResolver.openInputStream(uri)
            ?: throw FileNotFoundException("Cannot read file")
    }

    // ------------------------------------------------------------------ crypto
    private fun encrypt(input: InputStream, output: OutputStream, key: SecretKey) {
        val ins = BufferedInputStream(input, CHUNK)
        val out = DataOutputStream(BufferedOutputStream(output, 256 * 1024))
        out.writeInt(MAGIC); out.writeInt(CHUNK)
        val buf = ByteArray(CHUNK)
        var index = 0L
        while (true) {
            val n = readUpTo(ins, buf)
            if (n <= 0) break
            val iv = ByteArray(12).also(rnd::nextBytes)
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
            c.updateAAD(ByteBuffer.allocate(8).putLong(index).array())
            val ct = c.doFinal(buf, 0, n)
            out.write(iv); out.writeInt(ct.size); out.write(ct)
            index++
            if (n < CHUNK) break
        }
        out.flush()
    }

    private fun decrypt(input: InputStream, output: OutputStream, key: SecretKey) {
        val ins = DataInputStream(BufferedInputStream(input, 256 * 1024))
        if (ins.readInt() != MAGIC) throw IOException("Not a vault file")
        ins.readInt() // chunk size (informational)
        var index = 0L
        val iv = ByteArray(12)
        while (true) {
            val first = ins.read()
            if (first == -1) break
            iv[0] = first.toByte()
            ins.readFully(iv, 1, 11)
            val len = ins.readInt()
            if (len <= 0 || len > CHUNK + 64) throw IOException("Corrupted vault file")
            val ct = ByteArray(len)
            ins.readFully(ct)
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            c.updateAAD(ByteBuffer.allocate(8).putLong(index).array())
            output.write(c.doFinal(ct))
            index++
        }
        output.flush()
    }

    private fun readUpTo(ins: InputStream, buf: ByteArray): Int {
        var total = 0
        while (total < buf.size) {
            val n = ins.read(buf, total, buf.size - total)
            if (n < 0) break
            total += n
        }
        return total
    }

    private fun saveIndex(ctx: Context) {
        val key = dek ?: return
        val arr = JSONArray()
        entries.value.forEach {
            arr.put(JSONObject().put("id", it.id).put("n", it.name).put("m", it.mime).put("s", it.size).put("t", it.added))
        }
        val iv = ByteArray(12).also(rnd::nextBytes)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        indexFile(ctx).writeBytes(iv + c.doFinal(arr.toString().toByteArray()))
    }

    private fun loadIndex(ctx: Context) {
        val key = dek ?: return
        val f = indexFile(ctx)
        if (!f.exists()) { entries.value = emptyList(); return }
        val bytes = f.readBytes()
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        val arr = JSONArray(String(c.doFinal(bytes, 12, bytes.size - 12)))
        entries.value = (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Entry(o.getString("id"), o.getString("n"), o.getString("m"), o.getLong("s"), o.getLong("t"))
        }
    }

    private fun kek(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(KEK_ALIAS, null) as? SecretKey)?.let { return it }
        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        kg.init(
            KeyGenParameterSpec.Builder(KEK_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return kg.generateKey()
    }

    private fun wrap(raw: ByteArray): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, kek())
        return b64(c.iv + c.doFinal(raw))
    }

    private fun unwrap(s: String): ByteArray {
        val bytes = unb64(s)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, kek(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        return c.doFinal(bytes, 12, bytes.size - 12)
    }

    private fun pbkdf2(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITER, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun unb64(s: String) = Base64.decode(s, Base64.NO_WRAP)
}
