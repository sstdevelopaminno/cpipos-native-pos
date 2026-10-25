package com.cpipos.pos.next.core.webpos

import android.content.Context

/**
 * Device cache of the branch notification policy already owned by CpIPOS Web.
 * It is only a fallback for foreground/background Android rendering; the server
 * remains authoritative and is refreshed after a verified POS session starts.
 */
class NativeQrNotificationPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "cpipos_native_qr_notification_policy_v1", Context.MODE_PRIVATE
    )

    private fun prefix(tenantId: String, branchId: String) = "$tenantId:$branchId"

    fun read(tenantId: String, branchId: String): WebPosNotificationSettings {
        val key = prefix(tenantId, branchId)
        return WebPosNotificationSettings(
            popupEnabled = prefs.getBoolean("$key:popup", true),
            soundEnabled = prefs.getBoolean("$key:sound", true),
            soundVolume = prefs.getFloat("$key:volume", 0.8f).coerceIn(0f, 1f)
        )
    }

    fun save(
        tenantId: String,
        branchId: String,
        settings: WebPosNotificationSettings
    ) {
        val key = prefix(tenantId, branchId)
        check(
            prefs.edit()
                .putBoolean("$key:popup", settings.popupEnabled)
                .putBoolean("$key:sound", settings.soundEnabled)
                .putFloat("$key:volume", settings.soundVolume.coerceIn(0f, 1f))
                .commit()
        )
    }
}
