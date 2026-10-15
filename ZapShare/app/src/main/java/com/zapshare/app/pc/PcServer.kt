package com.zapshare.app.pc

import android.content.Context
import android.net.ConnectivityManager
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.net.URLEncoder
import kotlin.random.Random

/**
 * "Connect to PC": a tiny web server on the phone. The PC opens http://<phone-ip>:8888 in any browser
 * (same Wi-Fi, or the PC joins the phone's offline hotspot). No app needed on the PC.
 */
class PcServer(
    private val ctx: Context,
    port: Int,
    private val token: String,
    hostname: String? = null
) : NanoHTTPD(hostname, port) {

    private val page: String by lazy {
        ctx.assets.open("pc/index.html").bufferedReader().use { it.readText() }
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val response = if (uri == "/" || uri == "/index.html") {
            newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", page)
        } else {
            val key = session.parameters["k"]?.firstOrNull() ?: session.headers["x-zap-key"]
            if (key != token) newFixedLengthResponse(Response.Status.FORBIDDEN, MIME_PLAINTEXT, "Wrong access code")
            else try {
                when {
                    uri == "/api/info" -> json(JSONObject().put("name", Prefs.deviceName).put("model", Build.MODEL))
                    uri == "/api/files" -> listFiles()
                    uri == "/dl" -> download(session.parameters["id"]?.firstOrNull())
                    uri == "/upload" && session.method == Method.POST -> upload(session)
                    else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
                }
            } catch (e: Exception) {
                val msg = e.message?.takeIf { it.isNotBlank() } ?: "Could not receive the file"
                PcShare.log("Upload failed: $msg")
                newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, msg)
            }
        }
        response.addHeader("Connection", "close")
        response.addHeader("Cache-Control", "no-store")
        return response
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
        val tmp = File(tmpPath)
        if (!tmp.isFile) return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "No file")
        val name = FileUtils.safeName(File(session.parameters["file"]?.firstOrNull() ?: "upload.bin").name)
        val mime = FileUtils.mimeOf(name)
        val size = tmp.length()
        val saved = saveReceived(tmp, name, mime)
        val item = ShareItem(saved, name, size, mime)
        PcShare.state.update { it.copy(received = it.received + item) }
        HistoryStore.add(name, item.size, mime, false, "PC", saved)
        PcShare.log("Received $name from PC")
        return json(JSONObject().put("ok", true))
    }

    /**
     * The hotspot has no internet. Saving while the process is pinned to that network
     * fails on some phones, so the file is written on the normal network and the
     * hotspot binding is put back before the reply is sent.
     */
    private fun saveReceived(tmp: File, name: String, mime: String): Uri {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val ap = PcShare.boundApNetwork()
        if (ap != null) runCatching { cm.bindProcessToNetwork(null) }
        try {
            val out = FileUtils.createOutput(ctx, name, mime, "PC")
            var ok = false
            try {
                FileInputStream(tmp).use { input -> out.stream.use { input.copyTo(it, 256 * 1024) } }
                ok = true
            } finally {
                if (!ok) runCatching { out.stream.close() }
                FileUtils.finishOutput(ctx, out, ok)
            }
            if (!ok) throw IllegalStateException("Could not save the file on the phone")
            return out.uri
        } finally {
            if (ap != null) runCatching { cm.bindProcessToNetwork(ap) }
        }
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
    private var networkBound = false
    private var apNetwork: android.net.Network? = null

    fun boundApNetwork(): android.net.Network? = apNetwork

    suspend fun start(ctx: Context, withHotspot: Boolean) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        stop(ctx)
        try {
            val before = NetUtils.assignedIps()
            var creds: HotspotCreds? = null
            if (withHotspot) {
                hotspotMgr = HotspotManager(ctx).also { creds = it.start() }
            }
            val app = ctx.applicationContext
            System.setProperty("java.io.tmpdir", app.cacheDir.absolutePath)
            val ip = waitForAddress(app, withHotspot, before)
                ?: throw IllegalStateException(
                    if (withHotspot) "Hotspot started, but this phone has no address yet. Turn Wi-Fi on and try again."
                    else "No Wi-Fi address found. Join the same Wi-Fi as the PC, or create a hotspot."
                )
            // The soft-AP network appears a moment after its IP. Bind to that network, then open an IPv4 socket.
            val cm = app.getSystemService(ConnectivityManager::class.java)
            if (withHotspot) {
                var ap: android.net.Network? = null
                repeat(20) {
                    ap = NetUtils.hotspotNetwork(app, ip)
                    if (ap != null) return@repeat
                    delay(250)
                }
                if (ap != null) {
                    cm.bindProcessToNetwork(ap)
                    apNetwork = ap
                    networkBound = true
                } else {
                    apNetwork = null
                    cm.bindProcessToNetwork(null)
                }
            } else {
                cm.bindProcessToNetwork(null)
            }
            val token = (1..6).map { Random.nextInt(10) }.joinToString("")
            server = listen(app, ip, token)
            state.update {
                it.copy(
                    running = true, token = token, hotspot = creds, error = null,
                    url = pageUrl(ip, token)
                )
            }
            TransferService.acquire(ctx, "pc")
        } catch (e: Exception) {
            stop(ctx)
            state.update { it.copy(error = e.message ?: "Could not start") }
        }
    }

    private suspend fun waitForAddress(ctx: Context, withHotspot: Boolean, before: Set<String>): String? {
        repeat(if (withHotspot) 25 else 8) {
            val ip = if (withHotspot) NetUtils.hotspotIp(before, ctx) else NetUtils.bestLocalIp(false, ctx)
            if (ip != null) return ip
            delay(400)
        }
        return null
    }

    /** Bind the IPv4 hotspot address. A blank hostname opens an IPv6 socket the PC cannot use. */
    private suspend fun listen(ctx: Context, ip: String, token: String): PcServer {
        var last: Exception? = null
        repeat(4) {
            val s = PcServer(ctx, NetUtils.PC_PORT, token, ip)
            try {
                s.start(180_000, true)
                return s
            } catch (e: Exception) {
                last = e
                runCatching { s.stop() }
                delay(300)
            }
        }
        throw last ?: IllegalStateException("Could not open the PC page")
    }

    private fun pageUrl(ip: String, token: String) = "http://$ip:${NetUtils.PC_PORT}/?k=$token"

    fun addShared(items: List<ShareItem>) =
        state.update { s -> s.copy(shared = (s.shared + items).distinctBy { it.key }) }

    fun removeShared(item: ShareItem) = state.update { s -> s.copy(shared = s.shared - item) }

    fun log(msg: String) = state.update { it.copy(log = (listOf(msg) + it.log).take(50)) }

    fun stop(ctx: Context) {
        runCatching { server?.stop() }
        server = null
        hotspotMgr?.stop()
        hotspotMgr = null
        if (networkBound) {
            runCatching {
                ctx.applicationContext.getSystemService(ConnectivityManager::class.java).bindProcessToNetwork(null)
            }
            networkBound = false
            apNetwork = null
        }
        state.update { it.copy(running = false, url = null, hotspot = null) }
        TransferService.release(ctx, "pc")
    }
}
