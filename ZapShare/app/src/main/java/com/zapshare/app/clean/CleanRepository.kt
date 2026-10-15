package com.zapshare.app.clean

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Size
import com.zapshare.app.data.FileCategory
import com.zapshare.app.data.MediaFile
import com.zapshare.app.data.MediaRepository
import com.zapshare.app.data.MediaRepository.toMediaFile
import com.zapshare.app.util.Permissions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.ArrayDeque

/** A cleanable bucket of files, e.g. "Screenshots" or "Short videos". */
data class CleanGroup(
    val id: String,
    val title: String,
    val subtitle: String,
    val files: List<MediaFile>,
    /** For similar photos / duplicates: sets of look-alike files. The first file of each set is kept by default. */
    val clusters: List<List<MediaFile>>? = null
) {
    val size: Long get() = files.sumOf { it.size }
    /** Bytes freed if every file except the first of each cluster is deleted. */
    val reclaimable: Long get() = clusters?.sumOf { c -> c.drop(1).sumOf { it.size } } ?: size
}

/** Progress shown on scanning screens. */
data class ScanProgress(
    val running: Boolean = false,
    val label: String = "",
    val files: Int = 0,
    val bytes: Long = 0,
    val fraction: Float? = null
)

/** WhatsApp / Telegram media found on the phone. */
data class ChatScan(val photos: List<MediaFile>, val videos: List<MediaFile>, val audio: List<MediaFile>) {
    val all get() = photos + videos + audio
    val size get() = all.sumOf { it.size }
}

enum class ChatApp(
    val key: String,
    val label: String,
    val color: Int,
    val packages: List<String>,
    /** Folders relative to internal storage, newest Android layout first. */
    val roots: List<String>,
    /** Lower-case text that appears in the file path of this app's media. */
    val pathKey: String
) {
    WHATSAPP(
        "whatsapp", "WhatsApp", Color.parseColor("#25D366"),
        listOf("com.whatsapp", "com.whatsapp.w4b"),
        listOf(
            "Android/media/com.whatsapp/WhatsApp/Media",
            "Android/media/com.whatsapp.w4b/WhatsApp Business/Media",
            "WhatsApp/Media",
            "WhatsApp Business/Media"
        ),
        "whatsapp"
    ),
    TELEGRAM(
        "telegram", "Telegram", Color.parseColor("#229ED9"),
        listOf("org.telegram.messenger", "org.telegram.messenger.web", "org.thunderdog.challegram"),
        listOf(
            "Android/media/org.telegram.messenger/Telegram",
            "Android/media/org.telegram.messenger.web/Telegram",
            "Telegram",
            "Download/Telegram"
        ),
        "telegram"
    );

    fun isInstalled(ctx: Context) = packages.any { ctx.packageManager.getLaunchIntentForPackage(it) != null }

    companion object {
        fun of(key: String) = entries.firstOrNull { it.key == key } ?: WHATSAPP
    }
}

/**
 * Scanners for the Clean section. Everything runs on the phone. Results are kept here so the
 * detail screens (and "go back, come back") don't need to rescan.
 */
object CleanRepository {
    const val MB = 1024L * 1024
    private const val DAY = 24L * 60 * 60 * 1000
    private const val YEAR = 365 * DAY

    /** "video" / "photo" / "audio" / "dupes" → groups found by the last scan. */
    val groups = MutableStateFlow<Map<String, List<CleanGroup>>>(emptyMap())
    /** "whatsapp" / "telegram" → media found by the last scan. */
    val chats = MutableStateFlow<Map<String, ChatScan>>(emptyMap())

    private fun MediaFile.p() = (path ?: "").replace('\\', '/').lowercase()
    private fun MediaFile.inChatApp() = p().contains("/whatsapp") || p().contains("/telegram")
    private fun MediaFile.inDownloads() = p().contains("/download/")
    private fun MediaFile.isOld(now: Long) = dateModified in 1 until now - YEAR

    private fun group(id: String, title: String, subtitle: String, files: List<MediaFile>) =
        CleanGroup(id, title, subtitle, files.distinctBy { it.uri.toString() }.sortedByDescending { it.size })

    // ===================================================================== videos
    suspend fun scanVideos(ctx: Context, onProgress: (ScanProgress) -> Unit): List<CleanGroup> = withContext(Dispatchers.IO) {
        onProgress(ScanProgress(true, "Reading videos…"))
        val all = MediaRepository.queryWhere(ctx, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, null, null, media = true, duration = true)
        onProgress(ScanProgress(true, "Sorting ${all.size} videos…", all.size, all.sumOf { it.size }))
        val now = System.currentTimeMillis()
        val result = listOf(
            group("screen", "Screen recordings", "Recordings of your screen",
                all.filter { val p = it.p(); p.contains("screen") && (p.contains("record") || p.contains("capture")) }),
            group("short", "Short clips", "Videos shorter than 5 seconds — often accidental",
                all.filter { it.duration in 1 until 5_000 }),
            group("large", "Large videos", "Videos over 100 MB",
                all.filter { it.size > 100 * MB }),
            group("chat", "Chat app videos", "Received in WhatsApp, Telegram and similar apps",
                all.filter { it.inChatApp() }),
            group("downloads", "Downloaded videos", "Saved in your Download folder",
                all.filter { it.inDownloads() }),
            group("old", "Old videos", "Not changed for over a year",
                all.filter { it.isOld(now) }),
        ).filter { it.files.isNotEmpty() }
        groups.update { it + ("video" to result) }
        result
    }

    // ===================================================================== audio
    suspend fun scanAudio(ctx: Context, onProgress: (ScanProgress) -> Unit): List<CleanGroup> = withContext(Dispatchers.IO) {
        onProgress(ScanProgress(true, "Reading audio…"))
        val all = MediaRepository.queryWhere(ctx, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, null, null, audio = true, duration = true)
        onProgress(ScanProgress(true, "Sorting ${all.size} audio files…", all.size, all.sumOf { it.size }))
        val now = System.currentTimeMillis()
        val result = listOf(
            group("voice", "Voice notes", "Voice messages from chat apps",
                all.filter { val p = it.p(); p.contains("voice") || p.endsWith(".opus") }),
            group("recordings", "Recordings", "Voice and call recordings",
                all.filter { val p = it.p(); p.contains("record") || p.contains("/call") }),
            group("chat", "Chat app audio", "Audio received in WhatsApp, Telegram and similar apps",
                all.filter { it.inChatApp() }),
            group("short", "Short audio", "Clips shorter than 10 seconds",
                all.filter { it.duration in 1 until 10_000 }),
            group("large", "Large audio", "Audio files over 20 MB",
                all.filter { it.size > 20 * MB }),
            group("old", "Old audio", "Not changed for over a year",
                all.filter { it.isOld(now) }),
        ).filter { it.files.isNotEmpty() }
        groups.update { it + ("audio" to result) }
        result
    }

    // ===================================================================== photos
    /** Similar and blurry checks look at the most recent photos only, to keep the scan quick. */
    private const val MAX_ANALYSED_PHOTOS = 1500

    suspend fun scanPhotos(ctx: Context, onProgress: (ScanProgress) -> Unit): List<CleanGroup> = withContext(Dispatchers.IO) {
        onProgress(ScanProgress(true, "Reading photos…"))
        val all = MediaRepository.queryWhere(ctx, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, null, null, media = true)
        val now = System.currentTimeMillis()

        // --- look at pixels: similar (perceptual hash) + blurry (sharpness)
        val recent = all.sortedByDescending { it.dateModified }.take(MAX_ANALYSED_PHOTOS)
        val hashes = HashMap<String, Long>()
        val blurry = ArrayList<MediaFile>()
        recent.forEachIndexed { i, f ->
            if (i % 10 == 0) onProgress(ScanProgress(true, "Checking photos ${i + 1}/${recent.size}…", i, 0, i.toFloat() / recent.size))
            val bmp = thumbnail(ctx, f) ?: return@forEachIndexed
            try {
                hashes[f.uri.toString()] = dHash(bmp)
                if (sharpness(bmp) < BLUR_THRESHOLD) blurry += f
            } finally { bmp.recycle() }
        }
        val clusters = similarClusters(recent, hashes)

        val result = listOf(
            CleanGroup("similar", "Similar photos", "Look-alike shots. The best one of each set is kept",
                clusters.flatten(), clusters),
            group("blurry", "Blurry photos", "Possibly out of focus — check before deleting", blurry),
            group("screenshots", "Screenshots", "Screen captures pile up fast",
                all.filter { it.p().contains("screenshot") }),
            group("chat", "Chat app photos", "Received in WhatsApp, Telegram and similar apps",
                all.filter { it.inChatApp() }),
            group("large", "Large photos", "Photos over 5 MB", all.filter { it.size > 5 * MB }),
            group("downloads", "Downloaded images", "Saved in your Download folder", all.filter { it.inDownloads() }),
            group("old", "Old photos", "Not changed for over a year", all.filter { it.isOld(now) }),
        ).filter { it.files.isNotEmpty() }
        groups.update { it + ("photo" to result) }
        result
    }

    private const val BLUR_THRESHOLD = 45.0

    private fun thumbnail(ctx: Context, f: MediaFile): Bitmap? = runCatching {
        if (Build.VERSION.SDK_INT >= 29) {
            ctx.contentResolver.loadThumbnail(f.uri, Size(160, 160), null)
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ctx.contentResolver.openInputStream(f.uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 160) sample *= 2
            ctx.contentResolver.openInputStream(f.uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            }
        }
    }.getOrNull()

    private fun lum(c: Int) = (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000

    /** 64-bit difference hash: similar pictures give hashes that differ in only a few bits. */
    private fun dHash(b: Bitmap): Long {
        val s = Bitmap.createScaledBitmap(b, 9, 8, true)
        var h = 0L
        for (y in 0 until 8) for (x in 0 until 8) {
            h = (h shl 1) or (if (lum(s.getPixel(x, y)) > lum(s.getPixel(x + 1, y))) 1L else 0L)
        }
        if (s !== b) s.recycle()
        return h
    }

    /** Variance of the Laplacian on a small grey image. Low = few sharp edges = probably blurry. */
    private fun sharpness(b: Bitmap): Double {
        val w = 128
        val h = (128f * b.height / b.width).toInt().coerceIn(32, 256)
        val s = Bitmap.createScaledBitmap(b, w, h, true)
        val px = IntArray(w * h)
        s.getPixels(px, 0, w, 0, 0, w, h)
        if (s !== b) s.recycle()
        val g = IntArray(px.size) { lum(px[it]) }
        var sum = 0.0; var sq = 0.0; var n = 0
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val i = y * w + x
            val lap = (g[i - 1] + g[i + 1] + g[i - w] + g[i + w] - 4 * g[i]).toDouble()
            sum += lap; sq += lap * lap; n++
        }
        if (n == 0) return 1000.0
        val mean = sum / n
        return sq / n - mean * mean
    }

    /** Photos taken close together whose hashes nearly match form a cluster. */
    private fun similarClusters(photos: List<MediaFile>, hashes: Map<String, Long>): List<List<MediaFile>> {
        val sorted = photos.filter { hashes.containsKey(it.uri.toString()) }.sortedBy { it.dateModified }
        val out = ArrayList<List<MediaFile>>()
        var current = ArrayList<MediaFile>()
        for (f in sorted) {
            val head = current.firstOrNull()
            val close = head != null &&
                f.dateModified - current.last().dateModified <= 15 * 60 * 1000 &&
                java.lang.Long.bitCount(hashes[head.uri.toString()]!! xor hashes[f.uri.toString()]!!) <= 10
            if (close) current.add(f) else {
                if (current.size >= 2) out += current.sortedByDescending { it.size }   // keep the biggest (best quality) first
                current = arrayListOf(f)
            }
        }
        if (current.size >= 2) out += current.sortedByDescending { it.size }
        return out.sortedByDescending { c -> c.drop(1).sumOf { it.size } }
    }

    // ===================================================================== big files
    /** Big files split into Photos / Videos / Music / Other. */
    suspend fun bigFiles(ctx: Context, minBytes: Long): Map<String, List<MediaFile>> = withContext(Dispatchers.IO) {
        val files = MediaRepository.queryWhere(
            ctx, MediaStore.Files.getContentUri("external"),
            "${MediaStore.MediaColumns.SIZE} >= ?", arrayOf(minBytes.toString()), duration = true
        ).filterNot { File(it.path ?: "").isDirectory }.sortedByDescending { it.size }
        linkedMapOf(
            "Photos" to files.filter { it.category == FileCategory.IMAGE },
            "Videos" to files.filter { it.category == FileCategory.VIDEO },
            "Music" to files.filter { it.category == FileCategory.AUDIO },
            "Other" to files.filter { it.category != FileCategory.IMAGE && it.category != FileCategory.VIDEO && it.category != FileCategory.AUDIO },
        )
    }

    // ===================================================================== duplicates
    suspend fun scanDuplicates(ctx: Context, onProgress: (ScanProgress) -> Unit): List<CleanGroup> = withContext(Dispatchers.IO) {
        onProgress(ScanProgress(true, "Reading files…"))
        val files = MediaRepository.indexedFiles(ctx)
            .filter { it.size >= 4096 && !File(it.path ?: "").isDirectory }
            .distinctBy { it.path ?: it.uri.toString() }
        // 1. Same size is required for identical files.
        val candidates = files.groupBy { it.size }.values.filter { it.size >= 2 }.flatten()
        // 2. Quick fingerprint (start + end of the file), then 3. full checksum to be sure.
        val quick = HashMap<String, MutableList<MediaFile>>()
        candidates.forEachIndexed { i, f ->
            if (i % 20 == 0) onProgress(ScanProgress(true, "Comparing files ${i + 1}/${candidates.size}…", i, 0, i.toFloat() / candidates.size.coerceAtLeast(1) * 0.5f))
            val key = runCatching { "${f.size}:${partialHash(ctx, f)}" }.getOrNull() ?: return@forEachIndexed
            quick.getOrPut(key) { mutableListOf() }.add(f)
        }
        val confirm = quick.values.filter { it.size >= 2 }
        val clusters = ArrayList<List<MediaFile>>()
        confirm.forEachIndexed { i, set ->
            onProgress(ScanProgress(true, "Confirming duplicates ${i + 1}/${confirm.size}…", i, 0, 0.5f + i.toFloat() / confirm.size * 0.5f))
            if (set.first().size > 300 * MB) {   // very large: the quick fingerprint + equal size is trusted
                clusters += set.sortedBy { it.dateModified }
            } else {
                set.groupBy { runCatching { fullHash(ctx, it) }.getOrNull() }
                    .filter { (k, v) -> k != null && v.size >= 2 }
                    .values.forEach { clusters += it.sortedBy { f -> f.dateModified } }   // keep the oldest (the original)
            }
        }
        clusters.sortByDescending { c -> c.drop(1).sumOf { it.size } }
        val result = if (clusters.isEmpty()) emptyList() else listOf(
            CleanGroup("dupes", "Duplicate files", "Exact copies. The oldest copy of each is kept", clusters.flatten(), clusters)
        )
        groups.update { it + ("dupes" to result) }
        result
    }

    private fun open(ctx: Context, f: MediaFile): InputStream? =
        if (f.uri.scheme == "file") File(f.uri.path!!).inputStream() else ctx.contentResolver.openInputStream(f.uri)

    private fun partialHash(ctx: Context, f: MediaFile): String {
        val md = MessageDigest.getInstance("MD5")
        val chunk = 64 * 1024
        open(ctx, f)?.use { ins ->
            val buf = ByteArray(chunk)
            val n = ins.read(buf); if (n > 0) md.update(buf, 0, n)
            val skip = f.size - 2L * chunk
            if (skip > 0) {
                var left = skip
                while (left > 0) { val s = ins.skip(left); if (s <= 0) break; left -= s }
                val m = ins.read(buf); if (m > 0) md.update(buf, 0, m)
            }
        } ?: error("unreadable")
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun fullHash(ctx: Context, f: MediaFile): String {
        val md = MessageDigest.getInstance("MD5")
        open(ctx, f)?.use { ins ->
            val buf = ByteArray(256 * 1024)
            while (true) { val n = ins.read(buf); if (n < 0) break; md.update(buf, 0, n) }
        } ?: error("unreadable")
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    // ===================================================================== WhatsApp / Telegram
    suspend fun scanChat(ctx: Context, app: ChatApp, onProgress: (ScanProgress) -> Unit): ChatScan = withContext(Dispatchers.IO) {
        val found = LinkedHashMap<String, MediaFile>()
        var bytes = 0L
        fun add(f: MediaFile) {
            if (f.category != FileCategory.IMAGE && f.category != FileCategory.VIDEO && f.category != FileCategory.AUDIO) return
            val key = (f.path ?: f.uri.toString()).lowercase()
            if (found.put(key, f) == null) {
                bytes += f.size
                if (found.size % 25 == 0) onProgress(ScanProgress(true, folderLabel(f), found.size, bytes))
            }
        }
        // With all-files access we can walk the app's folders directly (includes "Sent" and voice notes,
        // which MediaStore does not index).
        if (Permissions.hasAllFiles()) {
            val root = Environment.getExternalStorageDirectory()
            app.roots.map { File(root, it) }.filter { it.isDirectory }.forEach { dir ->
                walk(dir) { add(it.toMediaFile()) }
            }
        }
        // MediaStore covers whatever is indexed (works without all-files access).
        val like = "%/${app.pathKey}%"
        listOf(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        ).forEach { uri ->
            MediaRepository.queryWhere(ctx, uri, "_data LIKE ?", arrayOf(like), duration = uri != MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                .forEach { add(it) }
        }
        onProgress(ScanProgress(true, "Finishing…", found.size, bytes, 1f))
        val all = found.values.sortedByDescending { it.dateModified }
        val scan = ChatScan(
            photos = all.filter { it.category == FileCategory.IMAGE },
            videos = all.filter { it.category == FileCategory.VIDEO },
            audio = all.filter { it.category == FileCategory.AUDIO },
        )
        chats.update { it + (app.key to scan) }
        scan
    }

    private fun folderLabel(f: MediaFile): String =
        f.path?.substringBeforeLast('/')?.substringAfterLast('/')?.let { "Scanning $it…" } ?: "Scanning…"

    private fun walk(root: File, onFile: (File) -> Unit) {
        val stack = ArrayDeque<File>()
        stack.add(root)
        var steps = 0
        while (stack.isNotEmpty() && steps < 200_000) {
            val dir = stack.removeLast()
            for (f in dir.listFiles() ?: continue) {
                steps++
                if (f.isDirectory) stack.add(f)
                else if (f.name != ".nomedia" && f.length() > 0) onFile(f)
            }
        }
    }

    /** Quick size estimate for the hub cards (MediaStore only, no folder walk). */
    suspend fun quickChatSize(ctx: Context, app: ChatApp): Long = withContext(Dispatchers.IO) {
        MediaRepository.queryWhere(ctx, MediaStore.Files.getContentUri("external"), "_data LIKE ?", arrayOf("%/${app.pathKey}%"))
            .filterNot { File(it.path ?: "").isDirectory }.sumOf { it.size }
    }

    // ===================================================================== after deleting
    /** Removes files that no longer exist from every stored result. */
    suspend fun prune(ctx: Context) = withContext(Dispatchers.IO) {
        val cache = HashMap<String, Boolean>()
        fun alive(f: MediaFile): Boolean = cache.getOrPut(f.uri.toString()) { exists(ctx, f) }
        groups.update { m ->
            m.mapValues { (_, list) ->
                list.map { g ->
                    val clusters = g.clusters?.map { c -> c.filter(::alive) }?.filter { it.size >= 2 }
                    g.copy(files = if (clusters != null) clusters.flatten() else g.files.filter(::alive), clusters = clusters)
                }.filter { it.files.isNotEmpty() }
            }
        }
        chats.update { m ->
            m.mapValues { (_, s) -> ChatScan(s.photos.filter(::alive), s.videos.filter(::alive), s.audio.filter(::alive)) }
        }
    }

    fun exists(ctx: Context, f: MediaFile): Boolean {
        if (f.uri.scheme == "file") return File(f.uri.path ?: return false).exists()
        return runCatching {
            ctx.contentResolver.query(f.uri, arrayOf(MediaStore.MediaColumns._ID), null, null, null)?.use { it.moveToFirst() } == true
        }.getOrDefault(true)
    }

    fun uriOf(f: MediaFile): Uri = f.uri
}
