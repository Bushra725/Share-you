package com.zapshare.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import com.zapshare.app.data.HistoryStore
import com.zapshare.app.data.Prefs
import com.zapshare.app.downloader.VideoDownloader
import com.zapshare.app.premium.Ads
import com.zapshare.app.premium.Premium
import com.zapshare.app.transfer.TransferManager

class ZapApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        HistoryStore.init(this)
        TransferManager.init(this)
        createChannels()
        Premium.init(this)
        Ads.init(this)
        VideoDownloader.initAsync(this)
    }

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_TRANSFER, "File transfer", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_DOWNLOAD, "Downloads", NotificationManager.IMPORTANCE_LOW))
    }

    /** Coil loader that can also draw video thumbnails. */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components { add(VideoFrameDecoder.Factory()) }
        .crossfade(true)
        .build()

    companion object {
        const val CH_TRANSFER = "transfer"
        const val CH_DOWNLOAD = "downloads"
    }
}
