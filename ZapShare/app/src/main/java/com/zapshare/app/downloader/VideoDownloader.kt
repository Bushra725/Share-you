package com.zapshare.app.downloader

import android.content.Context
import android.net.Uri
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.zapshare.app.data.HistoryStore
import com.zapshare.app.util.FileUtils
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Social-media video downloader powered by the open-source yt-dlp engine (runs fully on-device,
 * no API key, no server). Works for public Facebook, Instagram, X/Twitter, TikTok and 1000+ sites.
 */
object VideoDownloader {
    enum class State { QUEUED, FETCHING, DOWNLOADING, SAVING, DONE, FAILED, CANCELLED }

    data class Task(
        val id: String,
        val url: String,
        val platform: String,
        val audioOnly: Boolean,
        val title: String? = null,
        val thumbnail: String? = null,
        val progress: Float = 0f,
        val eta: Long = 0,
        val state: State = State.QUEUED,
        val resultUri: Uri? = null,
        val fileName: String? = null,
        val error: String? = null
    )

    val tasks = MutableStateFlow<List<Task>>(emptyList())
    val engineStatus = MutableStateFlow("Preparing engine…")
    private val ready = CompletableDeferred<Boolean>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var app: Context

    fun initAsync(ctx: Context) {
        app = ctx.applicationContext
        tasks.value = loadSaved()
        scope.launch {
            try {
                YoutubeDL.getInstance().init(ctx.applicationContext)
                FFmpeg.getInstance().init(ctx.applicationContext)
                engineStatus.value = "Ready"
                ready.complete(true)
            } catch (e: Throwable) {
                engineStatus.value = "Engine failed: ${e.message}"
                ready.complete(false)
            }
        }
    }

    suspend fun awaitReady(): Boolean = ready.await()

    fun platformOf(url: String): String {
        val u = url.lowercase()
        return when {
            "instagram." in u || "instagr.am" in u -> "Instagram"
            "facebook." in u || "fb.watch" in u || "fb.com" in u -> "Facebook"
            "twitter." in u || "x.com" in u || "t.co/" in u -> "X (Twitter)"
            "tiktok." in u -> "TikTok"
            "whatsapp" in u -> "WhatsApp"
            else -> "Web"
        }
    }

    fun extractUrl(text: String?): String? =
        text?.let { Regex("https?://\\S+").find(it)?.value?.trimEnd('.', ',', ')', ']') }

    fun download(ctx: Context, rawUrl: String, audioOnly: Boolean) {
        val url = extractUrl(rawUrl) ?: rawUrl.trim()
        val task = Task(UUID.randomUUID().toString(), url, platformOf(url), audioOnly)
        tasks.update { listOf(task) + it }
        scope.launch { run(ctx.applicationContext, task) }
    }

    private suspend fun run(ctx: Context, t: Task) {
        fun upd(f: (Task) -> Task) = tasks.update { l -> l.map { if (it.id == t.id) f(it) else it } }
        if (!awaitReady()) { upd { it.copy(state = State.FAILED, error = engineStatus.value) }; return }
        val dir = File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "dl/${t.id}").apply { mkdirs() }
        try {
            upd { it.copy(state = State.FETCHING) }
            runCatching { YoutubeDL.getInstance().getInfo(t.url) }.getOrNull()?.let { info ->
                upd { it.copy(title = info.title, thumbnail = info.thumbnail) }
            }
            if (tasks.value.firstOrNull { it.id == t.id }?.state == State.CANCELLED) return

            val req = YoutubeDLRequest(t.url).apply {
                addOption("-o", "${dir.absolutePath}/%(title).60s.%(ext)s")
                addOption("--no-playlist")
                addOption("--no-mtime")
                addOption("--restrict-filenames")
                if (t.audioOnly) {
                    addOption("-x")
                    addOption("--audio-format", "mp3")
                } else {
                    addOption("-f", "bv*[ext=mp4]+ba[ext=m4a]/b[ext=mp4]/bv*+ba/b")
                    addOption("--merge-output-format", "mp4")
                }
            }
            upd { it.copy(state = State.DOWNLOADING) }
            YoutubeDL.getInstance().execute(req, t.id) { progress: Float, eta: Long, _: String ->
                upd { it.copy(progress = (progress / 100f).coerceIn(0f, 1f), eta = eta) }
            }
            val file = dir.listFiles()?.filter { it.isFile && !it.name.endsWith(".part") }?.maxByOrNull { it.length() }
                ?: throw IllegalStateException("Nothing was downloaded")
            upd { it.copy(state = State.SAVING, progress = 1f) }
            val detected = FileUtils.mimeOf(file.name)
            val mime = when {
                t.audioOnly -> if (detected.startsWith("audio/")) detected else "audio/mpeg"
                detected.startsWith("video/") -> detected
                else -> "video/mp4"
            }
            val uri = FileUtils.copyToGallery(ctx, Uri.fromFile(file), file.name, mime, video = !t.audioOnly)
            HistoryStore.add(file.name, file.length(), mime, false, t.platform, uri)
            upd { it.copy(state = State.DONE, resultUri = uri, fileName = file.name) }
            persist()
        } catch (e: Throwable) {
            val cancelled = tasks.value.firstOrNull { it.id == t.id }?.state == State.CANCELLED
            if (!cancelled) {
                val msg = (e.message ?: "Download failed").lines().lastOrNull { it.isNotBlank() }?.take(220)
                upd { it.copy(state = State.FAILED, error = friendly(msg ?: "Download failed", t.platform)) }
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun friendly(msg: String, platform: String): String = when {
        "login" in msg.lowercase() || "rate-limit" in msg.lowercase() || "private" in msg.lowercase() ->
            "$platform requires login for this post (only public posts are supported). $msg"
        "Unsupported URL" in msg -> "This link is not supported."
        else -> msg
    }

    fun cancel(id: String) {
        tasks.update { l -> l.map { if (it.id == id) it.copy(state = State.CANCELLED) else it } }
        runCatching { YoutubeDL.getInstance().destroyProcessById(id) }
    }

    fun remove(id: String) {
        tasks.update { l -> l.filterNot { it.id == id } }
        persist()
    }

    private fun storeFile(): File = File(app.filesDir, "downloads.json")

    private fun loadSaved(): List<Task> {
        val file = File(app.filesDir, "downloads.json")
        if (!file.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Task(
                    id = o.getString("id"),
                    url = o.optString("url"),
                    platform = o.optString("platform", "Web"),
                    audioOnly = o.optBoolean("audio"),
                    title = o.optString("title").ifEmpty { null },
                    state = State.DONE,
                    resultUri = o.optString("uri").ifEmpty { null }?.let { Uri.parse(it) },
                    fileName = o.optString("file").ifEmpty { null }
                )
            }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    private fun persist() {
        if (!::app.isInitialized) return
        val arr = JSONArray()
        tasks.value.filter { it.state == State.DONE && it.resultUri != null }.forEach { t ->
            arr.put(
                JSONObject()
                    .put("id", t.id)
                    .put("url", t.url)
                    .put("platform", t.platform)
                    .put("audio", t.audioOnly)
                    .put("title", t.title ?: "")
                    .put("uri", t.resultUri.toString())
                    .put("file", t.fileName ?: "")
            )
        }
        runCatching { storeFile().writeText(arr.toString()) }
    }

    /** Social sites change often. Updating yt-dlp fixes most "stopped working" issues. Free. */
    suspend fun updateEngine(ctx: Context): String {
        if (!awaitReady()) return engineStatus.value
        return try {
            val status = YoutubeDL.getInstance().updateYoutubeDL(ctx.applicationContext, YoutubeDL.UpdateChannel.STABLE)
            when (status?.name) {
                "DONE" -> "Downloader engine updated"
                "ALREADY_UP_TO_DATE" -> "Downloader engine is already up to date"
                else -> "Engine checked"
            }
        } catch (e: Throwable) {
            "Update failed: ${e.message}"
        }
    }
}
