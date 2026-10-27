package com.cpipos.pos.next.core.push

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.cpipos.pos.next.BuildConfig
import com.cpipos.pos.next.NativePosApplication
import com.cpipos.pos.next.core.security.NativeAndroidDeveloperGuard
import com.cpipos.pos.next.core.webpos.WebPosApiException
import com.cpipos.pos.next.core.webpos.WebPosClient
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Zero-cost fallback for QR operational alerts when Firebase has not been activated yet.
 *
 * Android requires an ongoing foreground-service notification while the app UI is closed.
 * This service only reads the authenticated QR activity endpoint. It never creates/changes
 * orders, payments, stock or table state.
 */
class NativeQrBackgroundService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var worker: Job? = null
    private val client by lazy { WebPosClient(BuildConfig.WEB_POS_API_URL, applicationContext) }
    private val prefs by lazy {
        applicationContext.getSharedPreferences("cpipos_qr_background_v1", Context.MODE_PRIVATE)
    }

    override fun onCreate() {
        super.onCreate()
        NativeQrNotificationPublisher.ensureChannels(this)
        startForeground(
            NativeQrNotificationPublisher.LISTENER_NOTIFICATION_ID,
            NativeQrNotificationPublisher.listenerNotification(this)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (worker?.isActive != true) worker = scope.launch { pollLoop() }
        return START_STICKY
    }

    override fun onDestroy() {
        worker?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun pollLoop() {
        var session = runCatching { client.synchronizedShiftSession() }.getOrNull()
        var sessionRefreshAt = System.currentTimeMillis()
        var delayMs = 4_000L

        while (scope.isActive) {
            try {
                // The Compose foreground already owns a register-scoped QR poller.
                // Do not run a second network poller at the same time; on older
                // Android devices duplicate HTTP work competes with cashier UI.
                if ((application as? NativePosApplication)?.isForeground == true) {
                    delay(5_000L)
                    continue
                }

                if (session == null || System.currentTimeMillis() - sessionRefreshAt > 5 * 60_000L) {
                    session = client.synchronizedShiftSession()
                    sessionRefreshAt = System.currentTimeMillis()
                }
                val active = session ?: run {
                    stopSelf()
                    return
                }
                // Service survives activity/task removal: never poll QR under
                // a tenant's active developer/ADB security restriction.
                val policy = NativeAndroidDeveloperGuard.cachedEnabled(this, active.tenantId)
                if (policy == null || (policy && NativeAndroidDeveloperGuard.inspect(this).detected)) {
                    delay(5_000L)
                    continue
                }
                val key = "cursor:" + active.tenantId + ":" + active.branchId + ":" + active.deviceCode
                var cursor = prefs.getString(key, null)
                    ?: Instant.now().minusSeconds(20).toString()

                val result = client.qrActivity(active, cursor)
                result.events.forEach { event ->
                    NativeQrNotificationPublisher.show(
                        context = this,
                        sessionTenant = active.tenantId,
                        sessionBranch = active.branchId,
                        event = event
                    )
                }

                val newer = runCatching { Instant.parse(result.cursor) }.getOrNull()
                val older = runCatching { Instant.parse(cursor) }.getOrNull()
                if (newer != null && older != null && newer.isAfter(older)) {
                    cursor = newer.minusMillis(750).toString()
                    prefs.edit().putString(key, cursor).apply()
                }
                delayMs = if (result.events.isNotEmpty()) 3_000L else 6_000L
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: WebPosApiException) {
                if (failure.status == 401 || failure.status == 403) {
                    stopSelf()
                    return
                }
                delayMs = 15_000L
            } catch (_: Exception) {
                session = null
                delayMs = 15_000L
            }
            delay(delayMs)
        }
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context.applicationContext, NativeQrBackgroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context.applicationContext, intent)
            } else {
                context.applicationContext.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.applicationContext.stopService(
                Intent(context.applicationContext, NativeQrBackgroundService::class.java)
            )
        }
    }
}
