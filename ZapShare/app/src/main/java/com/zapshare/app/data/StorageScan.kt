package com.zapshare.app.data

import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import com.zapshare.app.util.Permissions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.ArrayDeque

/** Scan of internal storage: every indexed file, junk, and files from the last 7 days. */
object StorageScan {
    data class Report(
        val all: List<MediaFile>,
        val junk: List<MediaFile>,
        val fresh: List<MediaFile>
    ) {
        val allSize get() = all.sumOf { it.size }
        val junkSize get() = junk.sumOf { it.size }
        val freshSize get() = fresh.sumOf { it.size }
    }

    val report = MutableStateFlow<Report?>(null)
    val scanning = MutableStateFlow(false)
    val status = MutableStateFlow("Preparing scan…")

    private val junkExt = setOf("tmp", "temp", "log", "cache", "crdownload", "part", "dmp", "bak")
    private const val WEEK_MS = 7L * 24 * 60 * 60 * 1000

    suspend fun run(ctx: Context) {
        if (scanning.value) return
        scanning.value = true
        status.value = "Reading files…"
        try {
            val indexed = withContext(Dispatchers.IO) {
                MediaRepository.indexedFiles(ctx)
            }
            status.value = "Looking for junk…"
            val built = withContext(Dispatchers.IO) { build(ctx, indexed) { n -> status.value = "Scanned $n items…" } }
            report.value = built
            status.value = "Scan finished"
        } finally {
            scanning.value = false
        }
    }

    /** Drops files that were cleaned, and leaves anything the user cancelled. */
    suspend fun dropGone(ctx: Context) = withContext(Dispatchers.IO) {
        val current = report.value ?: return@withContext
        fun alive(f: MediaFile): Boolean {
            if (f.uri.scheme == "file") return File(f.path ?: f.uri.path ?: return false).exists()
            return runCatching {
                ctx.contentResolver.query(f.uri, arrayOf(MediaStore.MediaColumns._ID), null, null, null)
                    ?.use { it.moveToFirst() } == true
            }.getOrDefault(true)
        }
        report.value = Report(
            all = current.all.filter(::alive),
            junk = current.junk.filter(::alive),
            fresh = current.fresh.filter(::alive)
        )
    }

    private fun build(ctx: Context, indexed: List<MediaFile>, tick: (Int) -> Unit): Report {
        val cutoff = System.currentTimeMillis() - WEEK_MS
        val junk = ArrayList<MediaFile>()
        val seen = HashSet<String>()
        for (f in indexed) {
            val key = f.path ?: f.uri.toString()
            if (!seen.add(key)) continue
            if (isJunk(f.name, f.path)) junk += f
        }
        var n = indexed.size
        if (Permissions.hasAllFiles()) {
            val root = Environment.getExternalStorageDirectory()
            if (root != null && root.isDirectory) collectJunk(root, seen, junk) {
                n++
                if (n % 400 == 0) tick(n)
            }
        }
        junk.sortByDescending { it.size }
        val fresh = indexed.filter { it.dateModified >= cutoff && !isJunk(it.name, it.path) }
            .sortedByDescending { it.dateModified }
        return Report(
            all = indexed.distinctBy { it.uri.toString() },
            junk = junk.distinctBy { it.path ?: it.uri.toString() },
            fresh = fresh.distinctBy { it.uri.toString() }
        )
    }

    private fun collectJunk(root: File, seen: MutableSet<String>, out: MutableList<MediaFile>, tick: () -> Unit) {
        val stack = ArrayDeque<File>()
        stack.add(root)
        var steps = 0
        while (stack.isNotEmpty() && steps < 250_000) {
            val dir = stack.removeLast()
            val kids = dir.listFiles() ?: continue
            for (f in kids) {
                steps++
                if (steps % 400 == 0) tick()
                if (f.isDirectory) {
                    val name = f.name.lowercase()
                    when (name) {
                        "cache", ".cache", "code_cache", ".thumbnails", "thumbnails", "tmp", ".tmp", ".trash" ->
                            addTree(f, seen, out, tick)
                        else -> if (name != "lost.dir") stack.add(f)
                    }
                } else if (isJunk(f.name, f.absolutePath) && seen.add(f.absolutePath)) {
                    out += mediaFile(f)
                }
            }
        }
    }

    private fun addTree(dir: File, seen: MutableSet<String>, out: MutableList<MediaFile>, tick: () -> Unit) {
        val stack = ArrayDeque<File>()
        stack.add(dir)
        var steps = 0
        while (stack.isNotEmpty() && steps < 20_000) {
            val d = stack.removeLast()
            val kids = d.listFiles() ?: continue
            for (f in kids) {
                steps++
                if (steps % 200 == 0) tick()
                if (f.isDirectory) stack.add(f)
                else if (seen.add(f.absolutePath)) out += mediaFile(f)
            }
        }
    }

    private fun isJunk(name: String, path: String?): Boolean {
        val n = name.lowercase()
        val ext = n.substringAfterLast('.', "")
        if (ext in junkExt || n == "thumbs.db" || n == ".ds_store") return true
        val p = path?.replace('\\', '/')?.lowercase().orEmpty()
        return p.contains("/cache/") || p.contains("/.cache/") || p.contains("/code_cache/") ||
            p.contains("/.thumbnails/") || p.contains("/.trash/") || p.contains("/lost.dir/")
    }
}

private fun mediaFile(file: File) = MediaRepository.run { file.toMediaFile() }
