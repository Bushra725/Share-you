package com.zapshare.app.transfer

import android.net.Uri
import com.zapshare.app.data.Prefs
import com.zapshare.app.util.FileUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.net.Socket
import java.util.UUID

/**
 * One live TCP connection to another ZapShare device. Bi-directional: both sides can send
 * files at any time. Writes are serialized through a queue, reads run in their own loop.
 */
class Peer(private val socket: Socket, private val mgr: TransferManager) {
    val id: String = UUID.randomUUID().toString()
    @Volatile var name: String = "Device"
    @Volatile var avatar: Int = 0
    @Volatile var helloReceived = false
    @Volatile private var closed = false

    private val input = DataInputStream(BufferedInputStream(socket.getInputStream(), Protocol.BUF))
    private val output = DataOutputStream(BufferedOutputStream(socket.getOutputStream(), Protocol.BUF))
    private val outbox = Channel<Job>(Channel.UNLIMITED)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private sealed class Job {
        data object Hello : Job()
        data class Peers(val list: List<PeerInfo>) : Job()
        data class File(val item: TransferItem, val uri: Uri) : Job()
    }

    fun start() {
        scope.launch { writerLoop() }
        scope.launch { readerLoop() }
        outbox.trySend(Job.Hello)
    }

    fun enqueueFile(item: TransferItem, uri: Uri) { outbox.trySend(Job.File(item, uri)) }
    fun enqueuePeers(list: List<PeerInfo>) { outbox.trySend(Job.Peers(list)) }

    // ---------------------------------------------------------------- writing
    private suspend fun writerLoop() {
        try {
            for (job in outbox) {
                when (job) {
                    is Job.Hello -> {
                        writeHeader(
                            Protocol.T_HELLO,
                            JSONObject().put("name", Prefs.deviceName).put("avatar", Prefs.avatar).put("v", 1)
                        )
                        output.flush()
                    }
                    is Job.Peers -> {
                        val arr = JSONArray()
                        job.list.forEach { arr.put(JSONObject().put("id", it.id).put("name", it.name).put("avatar", it.avatar)) }
                        writeHeader(Protocol.T_PEERS, JSONObject().put("peers", arr))
                        output.flush()
                    }
                    is Job.File -> sendFile(job.item, job.uri)
                }
            }
        } catch (e: Exception) {
            close()
        }
    }

    private fun writeHeader(type: Byte, json: JSONObject) {
        val bytes = json.toString().toByteArray(Charsets.UTF_8)
        output.writeByte(type.toInt())
        output.writeInt(bytes.size)
        output.write(bytes)
    }

    private fun sendFile(item: TransferItem, uri: Uri) {
        if (closed) { mgr.markFailed(item.id, "Disconnected"); return }
        mgr.markActive(item.id)
        val ins = try {
            mgr.context.contentResolver.openInputStream(uri) ?: throw IOException("Cannot open file")
        } catch (e: Exception) {
            mgr.markFailed(item.id, e.message ?: "Cannot open file")   // nothing written yet, stream still in sync
            return
        }
        try {
            ins.use { stream ->
                writeHeader(
                    Protocol.T_FILE,
                    JSONObject().put("name", item.name).put("size", item.size).put("mime", item.mime)
                )
                val buf = ByteArray(Protocol.BUF)
                var remaining = item.size
                while (remaining > 0) {
                    val n = stream.read(buf, 0, minOf(buf.size.toLong(), remaining).toInt())
                    if (n < 0) throw EOFException("File changed while sending")
                    output.write(buf, 0, n)
                    remaining -= n
                    mgr.addProgress(item.id, n.toLong())
                }
                output.flush()
            }
            mgr.markSent(item)
        } catch (e: Exception) {
            mgr.markFailed(item.id, e.message ?: "Send failed")
            // Stream is out of sync after a partial file: drop the connection.
            close()
            throw e
        }
    }

    // ---------------------------------------------------------------- reading
    private fun readerLoop() {
        try {
            while (!closed) {
                val type = input.readByte()
                val len = input.readInt()
                if (len < 0 || len > 4 * 1024 * 1024) throw IOException("Bad frame")
                val bytes = ByteArray(len)
                input.readFully(bytes)
                val json = JSONObject(String(bytes, Charsets.UTF_8))
                when (type) {
                    Protocol.T_HELLO -> {
                        name = json.optString("name", "Device")
                        avatar = json.optInt("avatar", 0)
                        helloReceived = true
                        mgr.onPeerHello(this)
                    }
                    Protocol.T_FILE -> receiveFile(json)
                    Protocol.T_PEERS -> {
                        val arr = json.optJSONArray("peers") ?: JSONArray()
                        mgr.onPeerList((0 until arr.length()).map {
                            val o = arr.getJSONObject(it)
                            PeerInfo(o.getString("id"), o.optString("name"), o.optInt("avatar"))
                        })
                    }
                    Protocol.T_BYE -> break
                }
            }
        } catch (_: Exception) {
        } finally {
            close()
        }
    }

    private fun receiveFile(json: JSONObject) {
        val size = json.getLong("size")
        val name = FileUtils.safeName(json.optString("name", "file"))
        val mime = json.optString("mime").ifEmpty { FileUtils.mimeOf(name) }
        val item = mgr.startIncoming(this, name, size, mime)
        val sub = FileUtils.categoryOf(mime, name).folder
        val out = runCatching { FileUtils.createOutput(mgr.context, name, mime, sub) }.getOrNull()
        var ok = false
        try {
            val buf = ByteArray(Protocol.BUF)
            var remaining = size
            while (remaining > 0) {
                val n = input.read(buf, 0, minOf(buf.size.toLong(), remaining).toInt())
                if (n < 0) throw EOFException("Connection lost")
                out?.stream?.write(buf, 0, n)   // if we can't write, still drain to stay in sync
                remaining -= n
                mgr.addProgress(item.id, n.toLong())
            }
            out?.stream?.flush()
            ok = out != null
        } finally {
            out?.let {
                runCatching { it.stream.close() }
                FileUtils.finishOutput(mgr.context, it, ok)
            }
            if (ok) mgr.markReceived(this, item, out!!.uri)
            else mgr.markFailed(item.id, if (out == null) "No storage access" else "Interrupted")
        }
    }

    fun info() = PeerInfo(id, name, avatar)

    fun close() {
        if (closed) return
        closed = true
        outbox.close()
        runCatching { socket.close() }
        mgr.onPeerClosed(this)
        scope.cancel()
    }
}
