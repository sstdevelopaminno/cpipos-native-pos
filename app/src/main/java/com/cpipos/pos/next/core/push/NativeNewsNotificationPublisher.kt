package com.cpipos.pos.next.core.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.cpipos.pos.next.MainActivity
import com.cpipos.pos.next.R
import com.cpipos.pos.next.core.webpos.WebNewsFeed
import com.cpipos.pos.next.core.webpos.WebNewsItem
import com.cpipos.pos.next.core.webpos.WebPosSession
import java.util.UUID

/** OS alerts for the same published news returned by /api/pos/news. */
object NativeNewsNotificationPublisher {
    private const val PREFS = "cpipos_news_alerts_v1"
    private const val CHANNEL = "cpipos_news"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun orderKey(item: WebNewsItem) = item.publishedAt + "|" + item.id

    fun publishFeed(context: Context, session: WebPosSession, feed: WebNewsFeed) {
        val shownKey = "shown:${session.tenantId}:${session.employeeId}"
        val visible = feed.items.mapTo(hashSetOf()) { it.id }
        val shown = prefs(context).getString(shownKey, "").orEmpty()
            .split(",").filter { it.isNotBlank() }
        shown.filter { it !in visible }.forEach {
            NotificationManagerCompat.from(context).cancel(it.hashCode())
        }
        if (shown.any { it !in visible }) {
            prefs(context).edit().putString(shownKey,
                shown.filter { it in visible }.joinToString(",")).apply()
        }
        val newest = feed.items.maxByOrNull(::orderKey) ?: return
        val key = "latest:${session.tenantId}:${session.employeeId}"
        val old = prefs(context).getString(key, null)
        val newestKey = orderKey(newest)
        if (old == null) {
            // A freshly signed-in device should not replay the full news archive.
            prefs(context).edit().putString(key, newestKey).apply()
            return
        }
        if (newestKey <= old) return
        if (!NativeQrNotificationPublisher.notificationsAllowed(context)) return
        val pending = feed.items.filter { !it.isRead && orderKey(it) > old }
            .sortedBy(::orderKey)
        pending.forEach { item ->
            show(context, session.tenantId, session.employeeId,
                item.id, item.title, item.body)
        }
        prefs(context).edit().putString(key, newestKey).apply()
    }

    fun show(context: Context, tenantId: String, userId: String,
        newsId: String, title: String, body: String): Boolean {
        if (runCatching { UUID.fromString(newsId) }.isFailure ||
            !NativeFcmRegistration.sameBoundUser(context, tenantId, userId) ||
            !NativeQrNotificationPublisher.notificationsAllowed(context)) return false
        if (!NativeFcmRegistration.markReceived(context, "news:$newsId")) return false
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel(
            CHANNEL, "ข่าวสาร CpIPOS", NotificationManager.IMPORTANCE_HIGH))
        val launch = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("cpipos_news_id", newsId)
            putExtra("cpipos_news_tenant_id", tenantId)
            putExtra("cpipos_news_user_id", userId)
        }
        val pending = PendingIntent.getActivity(context, newsId.hashCode(), launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_qr_notification)
            .setContentTitle(title.take(100))
            .setContentText(body.take(180))
            .setStyle(NotificationCompat.BigTextStyle().bigText(body.take(1000)))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .build()
        NotificationManagerCompat.from(context).notify(newsId.hashCode(), notification)
        val shownKey = "shown:$tenantId:$userId"
        val shown = prefs(context).getString(shownKey, "").orEmpty()
            .split(",").filter { it.isNotBlank() }
        prefs(context).edit().putString(shownKey,
            (shown + newsId).distinct().takeLast(100).joinToString(",")).apply()
        return true
    }
}
