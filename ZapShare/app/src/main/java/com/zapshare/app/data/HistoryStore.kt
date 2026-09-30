package com.zapshare.app.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class HistoryEntry(
    val name: String,
    val size: Long,
    val mime: String,
    val sent: Boolean,
    val peer: String,
    val time: Long,
    val uri: String?
)

/** Tiny JSON-file backed transfer history (no database needed). */
object HistoryStore {
    private lateinit var file: File
    val entries = MutableStateFlow<List<HistoryEntry>>(emptyList())

    fun init(ctx: Context) {
        file = File(ctx.filesDir, "history.json")
        entries.value = runCatching {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                HistoryEntry(
                    o.getString("n"), o.getLong("s"), o.optString("m"), o.getBoolean("out"),
                    o.optString("p"), o.getLong("t"), o.optString("u").ifEmpty { null }
                )
            }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun add(name: String, size: Long, mime: String, sent: Boolean, peer: String, uri: Uri?) {
        val e = HistoryEntry(name, size, mime, sent, peer, System.currentTimeMillis(), uri?.toString())
        val list = (listOf(e) + entries.value).take(500)
        entries.value = list
        save(list)
    }

    @Synchronized
    fun clear() { entries.value = emptyList(); save(emptyList()) }

    private fun save(list: List<HistoryEntry>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject().put("n", it.name).put("s", it.size).put("m", it.mime).put("out", it.sent)
                    .put("p", it.peer).put("t", it.time).put("u", it.uri ?: "")
            )
        }
        runCatching { file.writeText(arr.toString()) }
    }

    val totalSent get() = entries.value.filter { it.sent }.sumOf { it.size }
    val totalReceived get() = entries.value.filter { !it.sent }.sumOf { it.size }
}
