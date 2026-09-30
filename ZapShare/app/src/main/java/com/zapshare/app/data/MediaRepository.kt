package com.zapshare.app.data

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import com.zapshare.app.util.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** All MediaStore / PackageManager queries live here. Every call is main-safe. */
object MediaRepository {

    private val FILES: Uri get() = MediaStore.Files.getContentUri("external")

    private val docMimes = listOf(
        "application/pdf", "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/vnd.ms-powerpoint",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "text/plain", "text/csv", "text/html", "application/rtf", "application/epub+zip",
        "application/json", "application/xml", "text/xml",
        "application/vnd.oasis.opendocument.text"
    )

    suspend fun images(ctx: Context) = withContext(Dispatchers.IO) {
        query(ctx, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, media = true)
    }

    suspend fun videos(ctx: Context) = withContext(Dispatchers.IO) {
        query(ctx, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, media = true, duration = true)
    }

    suspend fun audio(ctx: Context) = withContext(Dispatchers.IO) {
        query(
            ctx, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, audio = true, duration = true,
            selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 OR ${MediaStore.MediaColumns.SIZE} > 500000",
            sort = "${MediaStore.MediaColumns.DISPLAY_NAME} COLLATE NOCASE ASC"
        )
    }

    suspend fun documents(ctx: Context) = withContext(Dispatchers.IO) {
        val ph = docMimes.joinToString(",") { "?" }
        query(ctx, FILES, selection = "${MediaStore.MediaColumns.MIME_TYPE} IN ($ph)", args = docMimes.toTypedArray())
    }

    suspend fun apkFiles(ctx: Context) = withContext(Dispatchers.IO) {
        query(ctx, FILES, selection = "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?", args = arrayOf("%.apk"))
    }

    suspend fun archives(ctx: Context) = withContext(Dispatchers.IO) {
        query(
            ctx, FILES,
            selection = "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ? OR ${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ? OR ${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?",
            args = arrayOf("%.zip", "%.rar", "%.7z")
        )
    }

    /** Everything received by ZapShare (Download/ZapShare/...). */
    suspend fun received(ctx: Context) = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            query(
                ctx, MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                selection = "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?",
                args = arrayOf("${Environment.DIRECTORY_DOWNLOADS}/${FileUtils.ROOT_DIR}/%")
            )
        } else {
            val root = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), FileUtils.ROOT_DIR)
            root.walkTopDown().filter { it.isFile }.map { it.toMediaFile() }
                .sortedByDescending { it.dateModified }.toList()
        }
    }

    suspend fun search(ctx: Context, q: String) = withContext(Dispatchers.IO) {
        if (q.isBlank()) emptyList<MediaFile>() else query(
            ctx, FILES, selection = "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?",
            args = arrayOf("%$q%"), limit = 300
        )
    }

    suspend fun largeFiles(ctx: Context, minBytes: Long = 50L * 1024 * 1024) = withContext(Dispatchers.IO) {
        query(
            ctx, FILES, selection = "${MediaStore.MediaColumns.SIZE} > ?", args = arrayOf(minBytes.toString()),
            sort = "${MediaStore.MediaColumns.SIZE} DESC", limit = 300
        )
    }

    suspend fun recent(ctx: Context, limit: Int = 40) = withContext(Dispatchers.IO) {
        query(
            ctx, FILES,
            selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} != ${MediaStore.Files.FileColumns.MEDIA_TYPE_NONE}",
            limit = limit
        )
    }

    suspend fun byCategory(ctx: Context, c: FileCategory): List<MediaFile> = when (c) {
        FileCategory.IMAGE -> images(ctx)
        FileCategory.VIDEO -> videos(ctx)
        FileCategory.AUDIO -> audio(ctx)
        FileCategory.DOCUMENT -> documents(ctx)
        FileCategory.APK -> apkFiles(ctx)
        FileCategory.ARCHIVE -> archives(ctx)
        FileCategory.OTHER -> received(ctx)
    }

    suspend fun apps(ctx: Context, includeSystem: Boolean = false): List<AppInfo> = withContext(Dispatchers.IO) {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolves = if (Build.VERSION.SDK_INT >= 33)
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        else @Suppress("DEPRECATION") pm.queryIntentActivities(intent, 0)
        resolves.map { it.activityInfo.packageName }.distinct().mapNotNull { pkg ->
            runCatching {
                @Suppress("DEPRECATION") val ai = pm.getApplicationInfo(pkg, 0)
                @Suppress("DEPRECATION") val pi = pm.getPackageInfo(pkg, 0)
                val system = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                    (ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
                if (system && !includeSystem) return@runCatching null
                val splits = ai.splitSourceDirs?.toList() ?: emptyList()
                AppInfo(
                    packageName = pkg,
                    label = ai.loadLabel(pm).toString(),
                    versionName = pi.versionName,
                    apkPath = ai.sourceDir,
                    size = File(ai.sourceDir).length() + splits.sumOf { File(it).length() },
                    splitPaths = splits,
                    installTime = pi.firstInstallTime,
                    isSystem = system
                )
            }.getOrNull()
        }.sortedBy { it.label.lowercase() }
    }

    data class StorageInfo(val total: Long, val free: Long) { val used get() = total - free }

    fun storage(): StorageInfo {
        val st = StatFs(Environment.getExternalStorageDirectory().path)
        return StorageInfo(st.totalBytes, st.availableBytes)
    }

    fun albumArtUri(albumId: Long): Uri =
        ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)

    // ---------------------------------------------------------------------------------------
    private fun query(
        ctx: Context,
        collection: Uri,
        selection: String? = null,
        args: Array<String>? = null,
        sort: String = "${MediaStore.MediaColumns.DATE_MODIFIED} DESC",
        limit: Int = 0,
        media: Boolean = false,
        audio: Boolean = false,
        duration: Boolean = false
    ): List<MediaFile> {
        val proj = mutableListOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATE_MODIFIED,
            "_data"
        )
        if (duration) proj += "duration"
        if (media) proj += "bucket_display_name"
        if (audio) proj += listOf("artist", "album", "album_id")
        val out = ArrayList<MediaFile>()
        runCatching {
            ctx.contentResolver.query(collection, proj.toTypedArray(), selection, args, sort)?.use { c ->
                val idI = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nI = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val sI = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val mI = c.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                val dI = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                val pI = c.getColumnIndex("_data")
                val durI = c.getColumnIndex("duration")
                val bI = c.getColumnIndex("bucket_display_name")
                val arI = c.getColumnIndex("artist")
                val alI = c.getColumnIndex("album")
                val aidI = c.getColumnIndex("album_id")
                while (c.moveToNext()) {
                    val name = c.getString(nI) ?: continue
                    val id = c.getLong(idI)
                    out += MediaFile(
                        uri = ContentUris.withAppendedId(collection, id),
                        name = name,
                        size = c.getLong(sI),
                        mime = c.getString(mI) ?: FileUtils.mimeOf(name),
                        dateModified = c.getLong(dI) * 1000,
                        path = if (pI >= 0) c.getString(pI) else null,
                        duration = if (durI >= 0) c.getLong(durI) else 0,
                        bucket = if (bI >= 0) c.getString(bI) else null,
                        artist = if (arI >= 0) c.getString(arI)?.takeIf { it != "<unknown>" } else null,
                        album = if (alI >= 0) c.getString(alI) else null,
                        albumId = if (aidI >= 0) c.getLong(aidI) else 0
                    )
                    if (limit > 0 && out.size >= limit) break
                }
            }
        }
        return out
    }

    fun File.toMediaFile() = MediaFile(
        uri = Uri.fromFile(this), name = name, size = length(),
        mime = FileUtils.mimeOf(name), dateModified = lastModified(), path = absolutePath
    )
}
