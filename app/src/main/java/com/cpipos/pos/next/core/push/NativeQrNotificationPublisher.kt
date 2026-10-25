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
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.cpipos.pos.next.MainActivity
import com.cpipos.pos.next.R
import com.cpipos.pos.next.core.webpos.NativeQrNotificationPreferences
import com.cpipos.pos.next.core.webpos.WebQrActivityEvent
import java.util.UUID

object NativeQrNotificationPublisher {
    const val CHANNEL_SOUND_ID = "cpipos_qr_operations_sound_v2"
    const val CHANNEL_SILENT_ID = "cpipos_qr_operations_silent_v2"
    const val LISTENER_CHANNEL_ID = "cpipos_qr_background_listener_v1"
    const val LISTENER_NOTIFICATION_ID = 0x435049

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val sound = NotificationChannel(
            CHANNEL_SOUND_ID,
            "CpIPOS • QR โต๊ะ (มีเสียง)",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "แจ้งอาหารใหม่ เรียกพนักงาน และเรียกชำระเงินจากโต๊ะ"
            enableVibration(true)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                    .build()
            )
        }
        val silent = NotificationChannel(
            CHANNEL_SILENT_ID,
            "CpIPOS • QR โต๊ะ (เงียบ)",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "แจ้งเตือน QR โต๊ะโดยไม่เล่นเสียง"
            enableVibration(true)
            setSound(null, null)
        }
        val listener = NotificationChannel(
            LISTENER_CHANNEL_ID,
            "CpIPOS • รับแจ้งเตือนเบื้องหลัง",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "คงการรับเหตุการณ์ QR ขณะออกจากหน้าจอแอป"
            setSound(null, null)
            enableVibration(false)
        }
        manager.createNotificationChannel(sound)
        manager.createNotificationChannel(silent)
        manager.createNotificationChannel(listener)
    }

    fun listenerNotification(context: Context): android.app.Notification {
        ensureChannels(context)
        val launch = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            LISTENER_NOTIFICATION_ID,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(context, LISTENER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_qr_notification)
            .setContentTitle("CpIPOS พร้อมรับแจ้งเตือน QR")
            .setContentText("รับออเดอร์ เรียกพนักงาน และเรียกชำระเงินขณะออกจากแอป")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(pending)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun show(
        context: Context,
        eventId: String,
        tableId: String,
        tenantId: String,
        branchId: String,
        eventType: String,
        tableCode: String,
        soundEnabledOverride: Boolean? = null
    ): Boolean {
        if (eventType !in setOf("order", "call_staff", "request_checkout")) return false
        if (runCatching { UUID.fromString(eventId) }.isFailure ||
            runCatching { UUID.fromString(tableId) }.isFailure ||
            runCatching { UUID.fromString(tenantId) }.isFailure ||
            runCatching { UUID.fromString(branchId) }.isFailure) return false
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED) return false
        if (!NativeFcmRegistration.markReceived(context, eventId)) return false

        val policy = NativeQrNotificationPreferences(context).read(tenantId, branchId)
        if (eventType == "order" && !policy.popupEnabled) return false
        val soundEnabled = soundEnabledOverride ?: policy.soundEnabled
        ensureChannels(context)

        val shortTable = tableCode.take(24)
        val title = when (eventType) {
            "order" -> "มีรายการสั่งอาหารใหม่"
            "call_staff" -> "ลูกค้าเรียกพนักงาน"
            else -> "ลูกค้าเรียกชำระเงิน"
        }
        val body = when (eventType) {
            "order" -> "โต๊ะ $shortTable สั่งอาหารเข้ามา"
            "call_staff" -> "โต๊ะ $shortTable ต้องการพนักงาน"
            else -> "โต๊ะ $shortTable ขอชำระเงิน"
        }
        val launch = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("cpipos_qr_table_id", tableId)
            putExtra("cpipos_qr_tenant_id", tenantId)
            putExtra("cpipos_qr_branch_id", branchId)
        }
        val pending = PendingIntent.getActivity(
            context,
            eventId.hashCode(),
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val channelId = if (soundEnabled) CHANNEL_SOUND_ID else CHANNEL_SILENT_ID
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_qr_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
        if (soundEnabled) {
            builder.setDefaults(NotificationCompat.DEFAULT_ALL)
        } else {
            builder.setSilent(true).setDefaults(NotificationCompat.DEFAULT_VIBRATE)
        }
        NotificationManagerCompat.from(context).notify(eventId.hashCode(), builder.build())
        return true
    }

    fun show(context: Context, sessionTenant: String, sessionBranch: String, event: WebQrActivityEvent): Boolean =
        show(
            context = context,
            eventId = event.id,
            tableId = event.tableId,
            tenantId = sessionTenant,
            branchId = sessionBranch,
            eventType = event.type,
            tableCode = event.tableCode
        )
}
