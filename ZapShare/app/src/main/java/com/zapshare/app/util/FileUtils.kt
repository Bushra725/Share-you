package com.zapshare.app.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import com.zapshare.app.data.FileCategory
import com.zapshare.app.data.Prefs
import com.zapshare.app.data.ShareItem
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FileUtils {
    const val ROOT_DIR = "ZapShare"

    // ---------- formatting ----------
    fun formatSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var v = bytes / 1024.0
        var i = 0
        while (v >= 1024 && i < units.size - 1) { v /= 1024; i++ }
        return String.format(Locale.US, if (v < 10) "%.2f %s" else "%.1f %s", v, units[i])
    }

    fun formatSpeed(bytesPerSec: Double): String = formatSize(bytesPerSec.toLong()) + "/s"

    fun formatDuration(ms: Long): String {
        val total = ms / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.US, "%d:%02d", m, s)
    }

    fun formatDate(ms: Long): String =
        SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(ms))

    // ---------- types ----------
    fun extension(name: String) = name.substringAfterLast('.', "").lowercase(Locale.US)

    fun mimeOf(name: String): String {
        val ext = extension(name)
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: when (ext) {
            "apk" -> "application/vnd.android.package-archive"
            "kt", "md", "log", "ini", "cfg", "yml", "yaml", "gradle" -> "text/plain"
            "mkv" -> "video/x-matroska"
            "opus" -> "audio/opus"
            else -> "application/octet-stream"
        }
    }

    private val docExt = setOf(
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "rtf", "csv", "odt", "ods",
        "odp", "epub", "md", "json", "xml", "html", "htm", "log"
    )
    private val archiveExt = setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz", "apks", "xapk")

    fun categoryOf(mime: String?, name: String): FileCategory {
        val ext = extension(name)
        val m = mime ?: ""
        return when {
            m.startsWith("image/") -> FileCategory.IMAGE
            m.startsWith("video/") -> FileCategory.VIDEO
            m.startsWith("audio/") -> FileCategory.AUDIO
            ext == "apk" || m == "application/vnd.android.package-archive" -> FileCategory.APK
            ext in archiveExt -> FileCategory.ARCHIVE
            ext in docExt || m.startsWith("text/") || m == "application/pdf" -> FileCategory.DOCUMENT
            else -> FileCategory.OTHER
        }
    }

    fun isTextLike(mime: String, name: String): Boolean {
        val ext = extension(name)
        return mime.startsWith("text/") || ext in setOf(
            "txt", "md", "json", "xml", "csv", "log", "kt", "java", "js", "ts", "py", "c", "cpp",
            "h", "html", "htm", "css", "yml", "yaml", "ini", "cfg", "gradle", "sh", "srt"
        )
    }

    fun safeName(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001f]"), "_").trim().ifEmpty { "file" }.take(180)

    // ---------- metadata ----------
    fun queryMeta(ctx: Context, uri: Uri): ShareItem {
        if (uri.scheme == "file") {
            val f = File(uri.path ?: "")
            return ShareItem(uri, f.name, f.length(), mimeOf(f.name))
        }
        var name = uri.lastPathSegment ?: "file"
        var size = -1L
        runCatching {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                ?.use { c ->
                    if (c.moveToFirst()) {
                        val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val si = c.getColumnIndex(OpenableColumns.SIZE)
                        if (ni >= 0 && !c.isNull(ni)) name = c.getString(ni)
                        if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
                    }
                }
        }
        val mime = ctx.contentResolver.getType(uri) ?: mimeOf(name)
        return ShareItem(uri, name, size, mime)
    }

    // ---------- intents ----------
    fun providerUri(ctx: Context, file: File): Uri =
        FileProvider.getUriForFile(ctx, "${ctx.packageName}.provider", file)

    fun shareableUri(ctx: Context, uri: Uri): Uri =
        if (uri.scheme == "file") providerUri(ctx, File(uri.path!!)) else uri

    fun openWith(ctx: Context, uri: Uri, mime: String) {
        try {
            val i = Intent(Intent.ACTION_VIEW)
                .setDataAndType(shareableUri(ctx, uri), mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(Intent.createChooser(i, "Open with").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Toast.makeText(ctx, "No app can open this file", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareExternal(ctx: Context, uris: List<Uri>, mime: String = "*/*") {
        if (uris.isEmpty()) return
        val converted = ArrayList(uris.map { shareableUri(ctx, it) })
        val i = if (converted.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, converted[0])
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, converted)
        }
        i.type = mime
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(i, "Share via").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun installApk(ctx: Context, uri: Uri) {
        val i = Intent(Intent.ACTION_VIEW)
            .setDataAndType(shareableUri(ctx, uri), "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        try { ctx.startActivity(i) } catch (e: Exception) {
            Toast.makeText(ctx, "Unable to start installer", Toast.LENGTH_SHORT).show()
        }
    }

    // ---------- saving into Download/ZapShare/<sub>, or the folder chosen in Settings ----------
    class PendingOutput(val uri: Uri, val stream: OutputStream, val file: File?, val saf: Boolean = false)

    fun saveLocationLabel(): String {
        val raw = Prefs.saveTreeUri ?: return "Download/$ROOT_DIR"
        return runCatching {
            val id = DocumentsContract.getTreeDocumentId(Uri.parse(raw))
            Uri.decode(id).substringAfter(':').ifBlank { "Selected folder" }
        }.getOrDefault("Selected folder")
    }

    fun publicDir(sub: String): File =
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "$ROOT_DIR/$sub")

    fun createOutput(ctx: Context, name: String, mime: String, sub: String): PendingOutput {
        val clean = safeName(name)
        val tree = Prefs.saveTreeUri
        if (!tree.isNullOrBlank()) {
            val saved = runCatching { createInTree(ctx, Uri.parse(tree), clean, mime, sub) }.getOrNull()
            if (saved != null) return saved
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, clean)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeOf(clean).takeIf { it != "application/octet-stream" } ?: mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$ROOT_DIR/$sub")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val resolver = ctx.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("Cannot create $clean")
            val os = resolver.openOutputStream(uri) ?: throw IOException("Cannot write $clean")
            return PendingOutput(uri, BufferedOutputStream(os, 256 * 1024), null)
        } else {
            val dir = publicDir(sub).apply { mkdirs() }
            val f = uniqueFile(dir, clean)
            return PendingOutput(Uri.fromFile(f), BufferedOutputStream(FileOutputStream(f), 256 * 1024), f)
        }
    }

    private fun createInTree(ctx: Context, treeUri: Uri, name: String, mime: String, sub: String): PendingOutput? {
        val resolver = ctx.contentResolver
        val parent = treeChild(ctx, treeUri, sub) ?: return null
        val type = mimeOf(name).takeIf { it != "application/octet-stream" } ?: mime
        val doc = DocumentsContract.createDocument(resolver, parent, type, name) ?: return null
        val os = resolver.openOutputStream(doc) ?: return null
        return PendingOutput(doc, BufferedOutputStream(os, 256 * 1024), null, saf = true)
    }

    private fun treeChild(ctx: Context, treeUri: Uri, sub: String): Uri? {
        val resolver = ctx.contentResolver
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        val root = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootId)
        if (sub.isBlank()) return root
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, rootId)
        resolver.query(
            children,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE),
            null, null, null
        )?.use { cursor ->
            val idCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
            while (cursor.moveToNext()) {
                val mime = if (mimeCol >= 0) cursor.getString(mimeCol) else null
                val display = if (nameCol >= 0) cursor.getString(nameCol) else null
                if (display == sub && mime == DocumentsContract.Document.MIME_TYPE_DIR && idCol >= 0) {
                    return DocumentsContract.buildDocumentUriUsingTree(treeUri, cursor.getString(idCol))
                }
            }
        }
        val created = DocumentsContract.createDocument(resolver, root, DocumentsContract.Document.MIME_TYPE_DIR, sub)
        return created ?: root
    }

    fun finishOutput(ctx: Context, out: PendingOutput, success: Boolean) {
        if (out.saf) {
            if (!success) runCatching { ctx.contentResolver.delete(out.uri, null, null) }
            return
        }
        if (out.file != null) {
            if (success) MediaScannerConnection.scanFile(ctx, arrayOf(out.file.absolutePath), null, null)
            else out.file.delete()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (success) {
                val v = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                ctx.contentResolver.update(out.uri, v, null, null)
            } else {
                runCatching { ctx.contentResolver.delete(out.uri, null, null) }
            }
        }
    }

    /**
     * Copies a video into DCIM/ZapShare (or audio into Music/ZapShare) so it shows in the
     * system gallery / music app, not only in the Downloads folder.
     */
    fun copyToGallery(ctx: Context, src: Uri, name: String, mime: String, video: Boolean): Uri {
        val clean = safeName(name)
        val resolved = mimeOf(clean).takeIf { it != "application/octet-stream" } ?: mime
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val collection = if (video) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            }
            val relative = if (video) {
                "${Environment.DIRECTORY_DCIM}/$ROOT_DIR"
            } else {
                "${Environment.DIRECTORY_MUSIC}/$ROOT_DIR"
            }
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, clean)
                put(MediaStore.MediaColumns.MIME_TYPE, resolved)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relative)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
                put(MediaStore.MediaColumns.DATE_ADDED, System.currentTimeMillis() / 1000)
                if (video) put(MediaStore.Video.Media.DATE_TAKEN, System.currentTimeMillis())
            }
            val resolver = ctx.contentResolver
            val uri = resolver.insert(collection, values) ?: throw IOException("Cannot save $clean to the gallery")
            var ok = false
            try {
                val os = resolver.openOutputStream(uri) ?: throw IOException("Cannot write $clean")
                BufferedOutputStream(os, 256 * 1024).use { out ->
                    resolver.openInputStream(src)?.use { it.copyTo(out, 256 * 1024) }
                        ?: throw IOException("Cannot read source")
                }
                ok = true
            } finally {
                if (ok) {
                    val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                    resolver.update(uri, done, null, null)
                } else {
                    runCatching { resolver.delete(uri, null, null) }
                }
            }
            return uri
        }
        val root = Environment.getExternalStoragePublicDirectory(
            if (video) Environment.DIRECTORY_DCIM else Environment.DIRECTORY_MUSIC
        )
        val dir = File(root, ROOT_DIR).apply { mkdirs() }
        val dest = uniqueFile(dir, clean)
        ctx.contentResolver.openInputStream(src)?.use { ins ->
            FileOutputStream(dest).use { ins.copyTo(it, 256 * 1024) }
        } ?: throw IOException("Cannot read source")
        MediaScannerConnection.scanFile(ctx, arrayOf(dest.absolutePath), arrayOf(resolved), null)
        return Uri.fromFile(dest)
    }

    /** Copies any uri/file into Download/ZapShare/<sub>, returns the new uri. */
    fun copyToDownloads(ctx: Context, src: Uri, name: String, mime: String, sub: String): Uri {
        val out = createOutput(ctx, name, mime, sub)
        var ok = false
        try {
            ctx.contentResolver.openInputStream(src)?.use { ins ->
                out.stream.use { os -> ins.copyTo(os, 256 * 1024) }
            } ?: throw IOException("Cannot read source")
            ok = true
        } finally {
            if (!ok) runCatching { out.stream.close() }
            finishOutput(ctx, out, ok)
        }
        return out.uri
    }

    fun uniqueFile(dir: File, name: String): File {
        var f = File(dir, name)
        if (!f.exists()) return f
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
        var i = 1
        while (f.exists()) { f = File(dir, "$base ($i)$ext"); i++ }
        return f
    }

    /** Copies a content uri to the app cache so tools that need a real path (FFmpeg, PdfRenderer...) can use it. */
    fun copyToCache(ctx: Context, uri: Uri, name: String): File {
        val dir = File(ctx.cacheDir, "work").apply { mkdirs() }
        val f = File(dir, "${System.currentTimeMillis()}_${safeName(name)}")
        ctx.contentResolver.openInputStream(uri)?.use { ins ->
            FileOutputStream(f).use { ins.copyTo(it, 256 * 1024) }
        } ?: throw IOException("Cannot read $name")
        return f
    }

    fun toast(ctx: Context, msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
}
