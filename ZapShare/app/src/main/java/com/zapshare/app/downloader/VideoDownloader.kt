package com.zapshare.app.downloader

import android.content.Context
import android.net.Uri
import com.zapshare.app.data.HistoryStore
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
 * Recent-download list. The on-device video engine is omitted from this release so the
 * install stays small enough for Play. New downloads are not available.
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
    val engineStatus = MutableStateFlow("Video download is not included in this version")
    private val ready = CompletableDeferred<Boolean>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var app: Context

    fun initAsync(ctx: Context) {
        app = ctx.applicationContext
        tasks.value = loadSaved()
        engineStatus.value = "Video download is not included in this version"
        if (!ready.isCompleted) ready.complete(false)
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
        upd { it.copy(state = State.FAILED, error = engineStatus.value) }
    }

    fun cancel(id: String) {
        tasks.update { l -> l.map { if (it.id == id) it.copy(state = State.CANCELLED) else it } }
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

    suspend fun updateEngine(ctx: Context): String = engineStatus.value
}
