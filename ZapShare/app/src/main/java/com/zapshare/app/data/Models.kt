package com.zapshare.app.data

import android.net.Uri
import com.zapshare.app.util.FileUtils

enum class FileCategory(val label: String, val folder: String) {
    IMAGE("Photos", "Photos"),
    VIDEO("Videos", "Videos"),
    AUDIO("Music", "Music"),
    DOCUMENT("Documents", "Documents"),
    APK("Apps", "Apps"),
    ARCHIVE("Archives", "Files"),
    OTHER("Files", "Files")
}

/** A file on the device (from MediaStore or a direct path). */
data class MediaFile(
    val uri: Uri,
    val name: String,
    val size: Long,
    val mime: String,
    val dateModified: Long,          // epoch millis
    val path: String? = null,
    val duration: Long = 0,
    val artist: String? = null,
    val album: String? = null,
    val albumId: Long = 0,
    val bucket: String? = null
) {
    val category: FileCategory get() = FileUtils.categoryOf(mime, name)
    fun toShareItem() = ShareItem(uri, name, size, mime)
}

data class AppInfo(
    val packageName: String,
    val label: String,
    val versionName: String?,
    val apkPath: String,
    val size: Long,
    val splitPaths: List<String>,
    val installTime: Long,
    val isSystem: Boolean
) {
    fun toShareItem() = ShareItem(
        Uri.fromFile(java.io.File(apkPath)),
        "${label.replace(Regex("[\\\\/:*?\"<>|]"), "_")}.apk",
        java.io.File(apkPath).length(),
        "application/vnd.android.package-archive"
    )
}

/** Anything that can be sent over the wire. */
data class ShareItem(
    val uri: Uri,
    val name: String,
    val size: Long,
    val mime: String
) {
    val key: String get() = uri.toString()
}
