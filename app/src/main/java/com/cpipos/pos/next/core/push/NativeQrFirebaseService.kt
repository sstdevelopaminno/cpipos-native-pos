package com.cpipos.pos.next.core.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.cpipos.pos.next.MainActivity
import com.cpipos.pos.next.NativePosApplication
import com.cpipos.pos.next.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import java.util.UUID

/** Receives DATA-only FCM while Android permits the app to run in background. */
class NativeQrFirebaseService : FirebaseMessagingService() {
    companion object {
        private const val CHANNEL_ID = "cpipos_qr_operations_v1"
        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID, "CpIPOS • แจ้งเตือน QR โต๊ะ",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "แจ้งอาหารใหม่ เรียกพนักงาน และเรียกชำระเงินจากโต๊ะ"
                    enableVibration(true)
                    setSound(
                        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                        android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION).build()
                    )
                }
                (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                    .createNotificationChannel(channel)
            }
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        NativeFcmRegistration.saveLatestToken(this, token)
        // Registration requires a verified POS branch cookie. Next POS session
        // activation securely syncs the rotated token to CpIPOS server.
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val d = message.data
        if (d["type"] != "native_qr_event") return
        val eventId = d["event_id"] ?: return
        val tableId = d["table_id"] ?: return
        val tenantId = d["tenant_id"] ?: return
        val branchId = d["branch_id"] ?: return
        val eventType = d["event_type"] ?: return
        if (eventType !in setOf("order", "call_staff", "request_checkout")) return
        if (runCatching { UUID.fromString(eventId) }.isFailure ||
            runCatching { UUID.fromString(tableId) }.isFailure ||
            runCatching { UUID.fromString(tenantId) }.isFailure ||
            runCatching { UUID.fromString(branchId) }.isFailure) return
        if (!NativeFcmRegistration.sameBoundBranch(this, tenantId, branchId)) return
        // The in-app global alert already handles active foreground work.
        if ((application as? NativePosApplication)?.isForeground == true) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED) return
        if (!NativeFcmRegistration.markReceived(this, eventId)) return

        ensureChannel(this)
        val tableCode = d["table_code"]?.take(24).orEmpty()
        val title = when (eventType) {
            "order" -> "มีรายการสั่งอาหารใหม่"
            "call_staff" -> "ลูกค้าเรียกพนักงาน"
            else -> "ลูกค้าเรียกชำระเงิน"
        }
        val body = when (eventType) {
            "order" -> "โต๊ะ $tableCode สั่งอาหารเข้ามา"
            "call_staff" -> "โต๊ะ $tableCode ต้องการพนักงาน"
            else -> "โต๊ะ $tableCode ขอชำระเงิน"
        }
        val launch = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("cpipos_qr_table_id", tableId)
            putExtra("cpipos_qr_tenant_id", tenantId)
            putExtra("cpipos_qr_branch_id", branchId)
        }
        val pi = PendingIntent.getActivity(
            this, eventId.hashCode(), launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_qr_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()
        NotificationManagerCompat.from(this).notify(eventId.hashCode(), notification)
    }
}
