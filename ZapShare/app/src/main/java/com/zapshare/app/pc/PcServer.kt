package com.zapshare.app.pc

import android.content.Context
import android.net.Uri
import android.os.Build
import com.zapshare.app.data.HistoryStore
import com.zapshare.app.data.Prefs
import com.zapshare.app.data.ShareItem
import com.zapshare.app.transfer.HotspotCreds
import com.zapshare.app.transfer.HotspotManager
import com.zapshare.app.transfer.NetUtils
import com.zapshare.app.transfer.TransferService
import com.zapshare.app.util.FileUtils
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import kotlin.random.Random

/**
 * "Connect to PC": a tiny web server on the phone. The PC opens http://<phone-ip>:8888 in any browser
 * (same Wi-Fi, or the PC joins the phone's offline hotspot). No app needed on the PC.
 */
class PcServer(private val ctx: Context, port: Int, private val token: String) : NanoHTTPD(port) {

    private val page: String by lazy {
        ctx.assets.open("pc/index.html").bufferedReader().use { it.readText() }
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        if (uri == "/" || uri == "/index.html") return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", page)
        val key = session.parameters["k"]?.firstOrNull() ?: session.headers["x-zap-key"]
        if (key != token) return newFixedLengthResponse(Response.Status.FORBIDDEN, MIME_PLAINTEXT, "Wrong access code")
        return try {
            when {
                uri == "/api/info" -> json(JSONObject().put("name", Prefs.deviceName).put("model", Build.MODEL))
                uri == "/api/files" -> listFiles()
                uri == "/dl" -> download(session.parameters["id"]?.firstOrNull())
                uri == "/upload" && session.method == Method.POST -> upload(session)
                else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
            }
        } catch (e: Exception) {
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, e.message ?: "Error")
        }
    }

    private fun json(o: Any) = newFixedLengthResponse(Response.Status.OK, "application/json", o.toString())

    private fun listFiles(): Response {
        val st = PcShare.state.value
        fun arr(prefix: String, list: List<ShareItem>) = JSONArray().apply {
            list.forEachIndexed { i, f ->
                put(JSONObject().put("id", "$prefix$i").put("name", f.name).put("size", f.size).put("mime", f.mime))
            }
        }
        return json(JSONObject().put("shared", arr("s", st.shared)).put("received", arr("r", st.received)))
    }

    private fun download(id: String?): Response {
        val st = PcShare.state.value
        val item = when {
            id == null -> null
            id.startsWith("s") -> st.shared.getOrNull(id.drop(1).toIntOrNull() ?: -1)
            id.startsWith("r") -> st.received.getOrNull(id.drop(1).toIntOrNull() ?: -1)
            else -> null
        } ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
        val ins = ctx.contentResolver.openInputStream(item.uri)
            ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
        val size = if (item.size > 0) item.size else -1L
        val r = if (size > 0) newFixedLengthResponse(Response.Status.OK, item.mime, ins, size)
        else newChunkedResponse(Response.Status.OK, item.mime, ins)
        val enc = URLEncoder.encode(item.name, "UTF-8").replace("+", "%20")
        r.addHeader("Content-Disposition", "attachment; filename=\"${item.name.replace("\"", "")}\"; filename*=UTF-8''$enc")
        PcShare.log("PC downloaded ${item.name}")
        return r
    }

    private fun upload(session: IHTTPSession): Response {
        val files = HashMap<String, String>()
        session.parseBody(files)
        val tmpPath = files["file"] ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "No file")
        val name = FileUtils.safeName(File(session.parameters["file"]?.firstOrNull() ?: "upload.bin").name)
        val tmp = File(tmpPath)
        val mime = FileUtils.mimeOf(name)
        val saved = FileUtils.copyToDownloads(ctx, Uri.fromFile(tmp), name, mime, "PC")
        val item = ShareItem(saved, name, tmp.length(), mime)
        PcShare.state.update { it.copy(received = it.received + item) }
        HistoryStore.add(name, item.size, mime, false, "PC", saved)
        PcShare.log("Received $name from PC")
        return json(JSONObject().put("ok", true))
    }
}

/** Holds the PC-share session so the screen can navigate away (e.g. to pick files) and come back. */
object PcShare {
    data class State(
        val running: Boolean = false,
        val url: String? = null,
        val token: String = "",
        val hotspot: HotspotCreds? = null,
        val shared: List<ShareItem> = emptyList(),
        val received: List<ShareItem> = emptyList(),
        val log: List<String> = emptyList(),
        val error: String? = null
    )

    val state = MutableStateFlow(State())
    private var server: PcServer? = null
    private var hotspotMgr: HotspotManager? = null

    suspend fun start(ctx: Context, withHotspot: Boolean) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        stop(ctx)
        try {
            var creds: HotspotCreds? = null
            if (withHotspot) {
                hotspotMgr = HotspotManager(ctx).also { creds = it.start() }
            }
            val token = (1..6).map { Random.nextInt(10) }.joinToString("")
            val s = PcServer(ctx.applicationContext, NetUtils.PC_PORT, token)
            s.start(NanoHTTPD.SOCKET_READ_TIMEOUT, true)
            server = s
            var ip: String? = null
            for (i in 0 until 10) {
                ip = NetUtils.bestLocalIp(withHotspot)
                if (ip != null) break
                kotlinx.coroutines.delay(400)
            }
            state.update {
                it.copy(running = true, token = token, hotspot = creds, error = null,
                    url = "http://${ip ?: "0.0.0.0"}:${NetUtils.PC_PORT}/?k=$token")
            }
            TransferService.acquire(ctx, "pc")
        } catch (e: Exception) {
            stop(ctx)
            state.update { it.copy(error = e.message ?: "Could not start") }
        }
    }

    fun addShared(items: List<ShareItem>) =
        state.update { s -> s.copy(shared = (s.shared + items).distinctBy { it.key }) }

    fun removeShared(item: ShareItem) = state.update { s -> s.copy(shared = s.shared - item) }

    fun log(msg: String) = state.update { it.copy(log = (listOf(msg) + it.log).take(50)) }

    fun stop(ctx: Context) {
        runCatching { server?.stop() }
        server = null
        hotspotMgr?.stop()
        hotspotMgr = null
        state.update { it.copy(running = false, url = null, hotspot = null) }
        TransferService.release(ctx, "pc")
    }
}
