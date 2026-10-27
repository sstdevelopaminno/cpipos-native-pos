package com.cpipos.pos.next.core.security

import android.content.Context
import android.provider.Settings

data class AndroidDeveloperSignals(
    val developerOptions: Boolean,
    val usbDebugging: Boolean,
    val wirelessDebugging: Boolean
) {
    val detected: Boolean
        get() = developerOptions || usbDebugging || wirelessDebugging
    val details: String
        get() = listOfNotNull(
            "Developer Options".takeIf { developerOptions },
            "USB Debugging".takeIf { usbDebugging },
            "Wireless Debugging".takeIf { wirelessDebugging }
        ).joinToString(", ")
}

/**
 * Android's publicly available Settings.Global flags, no root or special
 * permissions. This detects standard settings, NOT a tamper-proof device
 * attestation: modified ROMs, rooted devices and patched APKs can bypass it.
 * Ordinary USB use (e.g. USB receipt printers) is never itself blocked.
 */
object NativeAndroidDeveloperGuard {
    fun inspect(context: Context): AndroidDeveloperSignals {
        fun isOn(name: String): Boolean = runCatching {
            Settings.Global.getInt(context.contentResolver, name, 0) == 1
        }.getOrDefault(false)
        return AndroidDeveloperSignals(
            developerOptions = isOn(Settings.Global.DEVELOPMENT_SETTINGS_ENABLED),
            usbDebugging = isOn(Settings.Global.ADB_ENABLED),
            wirelessDebugging = isOn("adb_wifi_enabled")
        )
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("cpipos_android_developer_guard_v1", Context.MODE_PRIVATE)

    fun cachedEnabled(context: Context, tenantId: String): Boolean? {
        val key = "policy:" + tenantId
        val store = prefs(context)
        return if (store.contains(key)) store.getBoolean(key, false) else null
    }

    fun rememberEnabled(context: Context, tenantId: String, enabled: Boolean): Boolean =
        prefs(context).edit().putBoolean("policy:" + tenantId, enabled).commit()
}
