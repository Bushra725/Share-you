package com.zapshare.app.data

import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import com.zapshare.app.util.Permissions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.ArrayDeque

/** Scan of internal storage: every indexed file, junk, and files from the last 7 days. */
object StorageScan {
    data class Report(
        val all: List<MediaFile>,
        val junk: List<MediaFile>,
        val fresh: List<MediaFile>,
        val big: List<MediaFile>,
        val redundant: List<MediaFile>
    ) {
        val allSize get() = all.sumOf { it.size }
        val junkSize get() = junk.sumOf { it.size }
        val freshSize get() = fresh.sumOf { it.size }
        val bigSize get() = big.sumOf { it.size }
        val redundantSize get() = redundant.sumOf { it.size }
    }

    const val BIG_BYTES = 50L * 1024 * 1024

    val report = MutableStateFlow<Report?>(null)
    val scanning = MutableStateFlow(false)
    val status = MutableStateFlow("Preparing scan…")

    private val junkExt = setOf("tmp", "temp", "log", "cache", "crdownload", "part", "dmp", "bak")
    private val mediaExt = setOf(
        "jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "bmp", "dng",
        "mp4", "mkv", "mov", "3gp", "avi", "webm", "m4v",
        "mp3", "m4a", "aac", "wav", "flac", "ogg", "opus"
    )
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
            fresh = current.fresh.filter(::alive),
            big = current.big.filter(::alive),
            redundant = current.redundant.filter(::alive)
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
        val unique = indexed.distinctBy { it.path ?: it.uri.toString() }
        val big = unique.filter { it.size >= BIG_BYTES && !isJunk(it.name, it.path) }
            .sortedByDescending { it.size }
        return Report(
            all = unique,
            junk = junk.distinctBy { it.path ?: it.uri.toString() },
            fresh = fresh.distinctBy { it.uri.toString() },
            big = big,
            redundant = redundantCopies(ctx, unique)
        )
    }

    /** Extra copies: same size and the same opening bytes. The oldest copy is kept out of this list. */
    private fun redundantCopies(ctx: Context, files: List<MediaFile>): List<MediaFile> {
        val groups = files.filter { it.size >= 64 * 1024 }.groupBy { it.size }.values.filter { it.size >= 2 }
        val out = ArrayList<MediaFile>()
        var hashed = 0
        for (group in groups) {
            val buckets = HashMap<String, MutableList<MediaFile>>()
            for (f in group) {
                val key = if (hashed < 300) {
                    hashed++
                    runCatching { fingerprint(ctx, f) }.getOrNull() ?: f.name.lowercase()
                } else f.name.lowercase()
                buckets.getOrPut(key) { mutableListOf() }.add(f)
            }
            buckets.values.filter { it.size >= 2 }.forEach { copies ->
                out += copies.sortedBy { it.dateModified }.drop(1)
            }
        }
        return out.distinctBy { it.path ?: it.uri.toString() }.sortedByDescending { it.size }
    }

    private fun fingerprint(ctx: Context, f: MediaFile): String {
        val md = MessageDigest.getInstance("MD5")
        val buf = ByteArray(64 * 1024)
        val stream = if (f.uri.scheme == "file") File(f.path ?: return f.name).inputStream()
        else ctx.contentResolver.openInputStream(f.uri) ?: return f.name
        stream.use { ins ->
            val n = ins.read(buf)
            if (n > 0) md.update(buf, 0, n)
        }
        return md.digest().joinToString("") { "%02x".format(it) }
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
                        "cache", ".cache", "code_cache", ".thumbnails", "thumbnails", "tmp", ".tmp" ->
                            addTree(f, seen, out, tick)
                        else -> if (name != "lost.dir" && name != ".trash") stack.add(f)
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
                else if (isJunk(f.name, f.absolutePath) && seen.add(f.absolutePath)) out += mediaFile(f)
            }
        }
    }

    private fun isJunk(name: String, path: String?): Boolean {
        val n = name.lowercase()
        val ext = n.substringAfterLast('.', "")
        if (ext in mediaExt) return false
        val p = path?.replace('\\', '/')?.lowercase().orEmpty()
        if (isGallery(p) || p.contains("/.trash/") || p.contains("/dcim/") || p.contains("/pictures/")) return false
        val cache = p.contains("/cache/") || p.contains("/.cache/") || p.contains("/code_cache/") ||
            p.contains("/.thumbnails/") || p.contains("/thumbnails/") || p.contains("/lost.dir/") ||
            p.contains("/tmp/") || p.contains("/.tmp/")
        if (cache) return true
        return ext in junkExt || n == "thumbs.db" || n == ".ds_store"
    }

    /** Camera roll, pictures, and movies are not junk, even when a cleaner lists them nearby. */
    private fun isGallery(path: String): Boolean {
        if (path.contains("/.thumbnails/") || path.contains("/thumbnails/") || path.contains("/cache/")) return false
        return path.contains("/dcim/") || path.contains("/pictures/") || path.contains("/movies/") ||
            path.contains("/music/") || path.contains("/screenshots/") || path.contains("/camera/")
    }
}

private fun mediaFile(file: File) = MediaRepository.run { file.toMediaFile() }
