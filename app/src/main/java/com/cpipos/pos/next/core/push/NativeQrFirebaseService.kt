package com.cpipos.pos.next.core.push

import com.cpipos.pos.next.NativePosApplication
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/** Receives DATA-only FCM when Firebase is configured for this APK. */
class NativeQrFirebaseService : FirebaseMessagingService() {
    companion object {
        fun ensureChannel(context: android.content.Context) {
            NativeQrNotificationPublisher.ensureChannels(context)
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        NativeFcmRegistration.saveLatestToken(this, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        if (data["type"] != "native_qr_event") return
        if ((application as? NativePosApplication)?.isForeground == true) return

        val soundEnabled = data["sound_enabled"]?.let {
            when (it.lowercase()) {
                "true" -> true
                "false" -> false
                else -> null
            }
        }
        NativeQrNotificationPublisher.show(
            context = this,
            eventId = data["event_id"] ?: return,
            tableId = data["table_id"] ?: return,
            tenantId = data["tenant_id"] ?: return,
            branchId = data["branch_id"] ?: return,
            eventType = data["event_type"] ?: return,
            tableCode = data["table_code"].orEmpty(),
            soundEnabledOverride = soundEnabled
        )
    }
}
