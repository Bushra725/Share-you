package com.zapshare.app.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat

object Permissions {

    fun media(): Array<String> = if (Build.VERSION.SDK_INT >= 33) arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.READ_MEDIA_AUDIO
    ) else if (Build.VERSION.SDK_INT >= 29) arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)

    /** Photos only, so the prompt is separate from videos. */
    fun photos(): Array<String> = if (Build.VERSION.SDK_INT >= 33)
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES) else media()

    /** Videos only, so the prompt is separate from photos. */
    fun videos(): Array<String> = if (Build.VERSION.SDK_INT >= 33)
        arrayOf(Manifest.permission.READ_MEDIA_VIDEO) else media()

    fun audio(): Array<String> = if (Build.VERSION.SDK_INT >= 33)
        arrayOf(Manifest.permission.READ_MEDIA_AUDIO) else media()

    /** Needed to create / join the offline hotspot and discover devices. */
    fun nearby(): Array<String> = if (Build.VERSION.SDK_INT >= 33)
        arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES, Manifest.permission.ACCESS_FINE_LOCATION)
    else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

    fun notifications(): Array<String> =
        if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.POST_NOTIFICATIONS) else emptyArray()

    fun camera() = arrayOf(Manifest.permission.CAMERA)

    fun granted(ctx: Context, perms: Array<String>) =
        perms.all { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }

    /** On Android 14+ users may grant only "selected photos" — treat as usable. */
    fun hasMedia(ctx: Context): Boolean = granted(ctx, media()) || hasSelectedVisual(ctx)

    fun hasPhotos(ctx: Context): Boolean = granted(ctx, photos()) || hasSelectedVisual(ctx)

    fun hasVideos(ctx: Context): Boolean = granted(ctx, videos()) || hasSelectedVisual(ctx)

    private fun hasSelectedVisual(ctx: Context) =
        Build.VERSION.SDK_INT >= 34 && granted(ctx, arrayOf(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED))

    fun hasAllFiles(): Boolean = Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager()

    fun allFilesIntent(ctx: Context): Intent =
        if (Build.VERSION.SDK_INT >= 30)
            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${ctx.packageName}"))
        else appSettings(ctx)

    fun appSettings(ctx: Context) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))

    /** Android 8–12 require Location services ON to start a local-only hotspot / scan Wi-Fi. */
    fun locationOn(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33) return true
        val lm = ctx.getSystemService(LocationManager::class.java)
        return LocationManagerCompat.isLocationEnabled(lm)
    }

    fun locationSettings() = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)

    fun canInstallApks(ctx: Context) = ctx.packageManager.canRequestPackageInstalls()

    fun installSourceIntent(ctx: Context) =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
}
