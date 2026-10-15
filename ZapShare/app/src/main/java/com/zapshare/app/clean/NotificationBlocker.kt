package com.zapshare.app.clean

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Notification Blocker. The user gives ZapShare "Notification access" once; after that this service
 * silently removes notifications from the apps they chose and keeps a list so nothing is lost.
 * Ongoing notifications (music, navigation, calls, downloads in progress) are never removed.
 */
class NotificationBlockerService : NotificationListenerService() {

    override fun onListenerConnected() {
        NotifyBlock.init(this)
        NotifyBlock.connected.value = true
        NotifyBlock.service = this
        sweep()
    }

    override fun onListenerDisconnected() {
        NotifyBlock.connected.value = false
        if (NotifyBlock.service === this) NotifyBlock.service = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        NotifyBlock.init(this)
        if (shouldBlock(sbn)) {
            NotifyBlock.record(sbn)
            runCatching { cancelNotification(sbn.key) }
        }
    }

    /** Removes notifications already showing from apps that were just blocked. */
    fun sweep() {
        runCatching { activeNotifications }.getOrNull()?.forEach { sbn ->
            if (shouldBlock(sbn)) {
                NotifyBlock.record(sbn)
                runCatching { cancelNotification(sbn.key) }
            }
        }
    }

    private fun shouldBlock(sbn: StatusBarNotification): Boolean =
        NotifyBlock.enabled.value &&
            sbn.packageName != packageName &&
            sbn.packageName in NotifyBlock.blocked.value &&
            !sbn.isOngoing &&
            (sbn.notification.flags and Notification.FLAG_FOREGROUND_SERVICE) == 0
}

object NotifyBlock {
    data class Record(val pkg: String, val title: String, val text: String, val time: Long)

    val enabled = MutableStateFlow(false)
    val blocked = MutableStateFlow<Set<String>>(emptySet())
    val records = MutableStateFlow<List<Record>>(emptyList())
    val connected = MutableStateFlow(false)
    @Volatile internal var service: NotificationBlockerService? = null
    private var sp: SharedPreferences? = null

    fun init(ctx: Context) {
        if (sp != null) return
        val p = ctx.applicationContext.getSharedPreferences("notify_block", Context.MODE_PRIVATE)
        sp = p
        enabled.value = p.getBoolean("enabled", false)
        blocked.value = p.getStringSet("blocked", emptySet())?.toSet() ?: emptySet()
        records.value = runCatching {
            val arr = JSONArray(p.getString("records", "[]") ?: "[]")
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Record(o.getString("p"), o.optString("t"), o.optString("x"), o.getLong("w"))
            }
        }.getOrDefault(emptyList())
    }

    fun hasAccess(ctx: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)

    /** Opens the system screen where the user turns on notification access for ZapShare. */
    fun accessIntent(ctx: Context): Intent {
        val component = ComponentName(ctx, NotificationBlockerService::class.java).flattenToString()
        return Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            .putExtra(":settings:fragment_args_key", component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun setEnabled(v: Boolean) {
        enabled.value = v
        sp?.edit()?.putBoolean("enabled", v)?.apply()
        if (v) service?.sweep()
    }

    fun setBlocked(pkg: String, block: Boolean) {
        val next = if (block) blocked.value + pkg else blocked.value - pkg
        blocked.value = next
        sp?.edit()?.putStringSet("blocked", next)?.apply()
        if (block) service?.sweep()
    }

    fun setAll(pkgs: Collection<String>, block: Boolean) {
        val next = if (block) blocked.value + pkgs else blocked.value - pkgs.toSet()
        blocked.value = next
        sp?.edit()?.putStringSet("blocked", next)?.apply()
        if (block) service?.sweep()
    }

    @Synchronized
    fun record(sbn: StatusBarNotification) {
        val e = sbn.notification.extras
        val r = Record(
            sbn.packageName,
            e?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
            e?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty(),
            sbn.postTime
        )
        val next = (listOf(r) + records.value).take(300)
        records.value = next
        save(next)
    }

    fun clearRecords() { records.value = emptyList(); save(emptyList()) }

    private fun save(list: List<Record>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("p", it.pkg).put("t", it.title).put("x", it.text).put("w", it.time)) }
        sp?.edit()?.putString("records", arr.toString())?.apply()
    }
}
