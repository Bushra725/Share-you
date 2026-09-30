package com.zapshare.app.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow

object Prefs {
    private lateinit var sp: SharedPreferences

    /** 0 = follow system, 1 = light, 2 = dark */
    val theme = MutableStateFlow(0)
    val name = MutableStateFlow("")
    val avatarFlow = MutableStateFlow(0)

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("zapshare", Context.MODE_PRIVATE)
        theme.value = sp.getInt("theme", 0)
        name.value = deviceName
        avatarFlow.value = avatar
    }

    private fun defaultName(): String {
        val m = Build.MODEL ?: "Android"
        val brand = (Build.MANUFACTURER ?: "").replaceFirstChar { it.uppercase() }
        return (if (m.startsWith(brand, true)) m else "$brand $m").take(24)
    }

    var deviceName: String
        get() = sp.getString("name", null) ?: defaultName()
        set(v) { sp.edit().putString("name", v.take(24)).apply(); name.value = v.take(24) }

    var avatar: Int
        get() = sp.getInt("avatar", 0)
        set(v) { sp.edit().putInt("avatar", v).apply(); avatarFlow.value = v }

    var onboarded: Boolean
        get() = sp.getBoolean("onboarded", false)
        set(v) = sp.edit().putBoolean("onboarded", v).apply()

    var themeMode: Int
        get() = sp.getInt("theme", 0)
        set(v) { sp.edit().putInt("theme", v).apply(); theme.value = v }

    /** In group mode, the host forwards every file it receives to all other members. */
    var groupRelay: Boolean
        get() = sp.getBoolean("group_relay", true)
        set(v) = sp.edit().putBoolean("group_relay", v).apply()

    var keepScreenOn: Boolean
        get() = sp.getBoolean("keep_screen_on", true)
        set(v) = sp.edit().putBoolean("keep_screen_on", v).apply()

    // ---- Premium ----
    var premiumPurchased: Boolean
        get() = sp.getBoolean("premium", false)
        set(v) = sp.edit().putBoolean("premium", v).apply()
    var premiumDebug: Boolean
        get() = sp.getBoolean("premium_debug", false)
        set(v) = sp.edit().putBoolean("premium_debug", v).apply()

    // ---- Safebox ----
    var vaultPinHash: String?
        get() = sp.getString("vault_pin", null)
        set(v) = sp.edit().putString("vault_pin", v).apply()
    var vaultSalt: String?
        get() = sp.getString("vault_salt", null)
        set(v) = sp.edit().putString("vault_salt", v).apply()
    var vaultDek: String?
        get() = sp.getString("vault_dek", null)
        set(v) = sp.edit().putString("vault_dek", v).apply()
    var vaultBiometric: Boolean
        get() = sp.getBoolean("vault_bio", false)
        set(v) = sp.edit().putBoolean("vault_bio", v).apply()

    val avatars = listOf("🐱", "🦊", "🐼", "🐸", "🐵", "🦄", "🐯", "🐨", "🐙", "🐧", "🦁", "🐶")
}
