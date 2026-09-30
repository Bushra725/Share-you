package com.zapshare.app.transfer

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.zapshare.app.MainActivity
import com.zapshare.app.R
import com.zapshare.app.ZapApp
import com.zapshare.app.util.FileUtils
import java.util.Collections

/**
 * Keeps the process alive (and Wi-Fi at full speed) while a transfer session or the PC server runs.
 * Uses reference keys so transfer + PC share can run at the same time.
 */
class TransferService : Service() {
    private var wifiLock: WifiManager.WifiLock? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        ServiceCompat.startForeground(this, NOTIF_ID, build(this, "Ready to share", null), type)
        if (wifiLock == null) {
            val wm = applicationContext.getSystemService(WifiManager::class.java)
            @Suppress("DEPRECATION")
            val mode = if (Build.VERSION.SDK_INT >= 34) WifiManager.WIFI_MODE_FULL_LOW_LATENCY else WifiManager.WIFI_MODE_FULL_HIGH_PERF
            wifiLock = wm.createWifiLock(mode, "zapshare:wifi").apply { setReferenceCounted(false); acquire() }
            val pm = getSystemService(PowerManager::class.java)
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "zapshare:transfer").apply {
                setReferenceCounted(false); acquire(6 * 60 * 60 * 1000L)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        runCatching { wifiLock?.release() }
        runCatching { wakeLock?.release() }
        super.onDestroy()
    }

    companion object {
        private const val NOTIF_ID = 42
        private val keys = Collections.synchronizedSet(mutableSetOf<String>())
        private var lastNotify = 0L

        fun acquire(ctx: Context, key: String) {
            keys.add(key)
            runCatching { ContextCompat.startForegroundService(ctx, Intent(ctx, TransferService::class.java)) }
        }

        fun release(ctx: Context, key: String) {
            keys.remove(key)
            if (keys.isEmpty()) ctx.stopService(Intent(ctx, TransferService::class.java))
        }

        fun updateProgress(ctx: Context, items: List<TransferItem>, speed: Double) {
            if (keys.isEmpty()) return
            val now = System.currentTimeMillis()
            if (now - lastNotify < 1000) return
            lastNotify = now
            val active = items.filter { it.status == ItemStatus.ACTIVE || it.status == ItemStatus.WAITING }
            val text = if (active.isEmpty()) "${items.count { it.status == ItemStatus.DONE }} file(s) done"
            else "${active.size} in progress • ${FileUtils.formatSpeed(speed)}"
            val total = active.sumOf { it.size }.coerceAtLeast(1)
            val done = active.sumOf { it.transferred }
            val pct = if (active.isEmpty()) null else ((done * 100) / total).toInt()
            runCatching { NotificationManagerCompat.from(ctx).notify(NOTIF_ID, build(ctx, text, pct)) }
        }

        private fun build(ctx: Context, text: String, pct: Int?): Notification {
            val pi = PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            return NotificationCompat.Builder(ctx, ZapApp.CH_TRANSFER)
                .setSmallIcon(R.drawable.ic_stat_zap)
                .setContentTitle("ZapShare")
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(pi)
                .apply { if (pct != null) setProgress(100, pct, false) }
                .build()
        }
    }
}
