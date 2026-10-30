package com.zapshare.app.toolbar

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.zapshare.app.MainActivity
import com.zapshare.app.R
import com.zapshare.app.data.Prefs
import com.zapshare.app.ui.navigation.Routes

/** Ongoing notification with Transfer, Discover, Files, and Clean. */
object ShareToolbar {
    const val EXTRA_ROUTE = "toolbar_route"
    private const val CHANNEL = "toolbar"
    private const val NOTIFICATION_ID = 4101

    fun sync(ctx: Context) {
        if (Prefs.toolbarEnabled) show(ctx) else hide(ctx)
    }

    fun show(ctx: Context) {
        val app = ctx.applicationContext
        val nm = app.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Toolbar", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Shortcuts in the notification bar"
                    setSound(null, null)
                }
            )
        }
        val color = Prefs.toolbarStyle == "color"
        val views = RemoteViews(app.packageName, R.layout.notification_toolbar)
        if (color) {
            views.setInt(R.id.toolbar_root, "setBackgroundColor", 0xFF10243F.toInt())
            listOf(R.id.tb_transfer_label, R.id.tb_discover_label, R.id.tb_files_label, R.id.tb_clean_label)
                .forEach { views.setTextColor(it, 0xFFFFFFFF.toInt()) }
        }
        views.setOnClickPendingIntent(R.id.tb_transfer, pending(app, Routes.SEND, 1))
        views.setOnClickPendingIntent(R.id.tb_discover, pending(app, Routes.PLAYER, 2))
        views.setOnClickPendingIntent(R.id.tb_files, pending(app, Routes.FILES, 3))
        views.setOnClickPendingIntent(R.id.tb_clean, pending(app, Routes.CLEAN, 4))
        val notification = NotificationCompat.Builder(app, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_zap)
            .setCustomContentView(views)
            .setCustomBigContentView(views)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setContentIntent(pending(app, Routes.HOME, 0))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        if (Build.VERSION.SDK_INT < 33 || NotificationManagerCompat.from(app).areNotificationsEnabled()) {
            nm.notify(NOTIFICATION_ID, notification)
        }
    }

    fun hide(ctx: Context) {
        ctx.applicationContext.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }

    /** Pulls the notification shade down so the new toolbar is visible. */
    fun expand(ctx: Context) {
        runCatching {
            val service = ctx.getSystemService("statusbar")
            val method = Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel")
            method.invoke(service)
        }
    }

    private fun pending(ctx: Context, route: String, code: Int): PendingIntent {
        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_ROUTE, route)
        }
        return PendingIntent.getActivity(ctx, code, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
