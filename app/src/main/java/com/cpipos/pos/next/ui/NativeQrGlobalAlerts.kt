package com.cpipos.pos.next.ui

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.cpipos.pos.next.core.webpos.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import kotlin.math.roundToInt

private val noticeBlue = Color(0xFF176DED)
private val noticeInk = Color(0xFF192C4D)

/**
 * App-wide IN-APP QR order/service popups, analogous to POS Web global alert.
 * This is foreground polling, NOT FCM/OS push when the app is killed.
 */
@Composable
internal fun NativeQrGlobalAlerts(
    client: WebPosClient,
    session: WebPosSession,
    canNavigate: Boolean,
    onGoToTable: (WebQrActivityEvent) -> Unit
) {
    val context = LocalContext.current
    val store = remember(context.applicationContext) {
        NativeQrAlertStore(context.applicationContext)
    }
    val policyStore = remember(context.applicationContext) {
        NativeQrNotificationPreferences(context.applicationContext)
    }
    var notificationSettings by remember(session.tenantId, session.branchId) {
        mutableStateOf(WebPosNotificationSettings())
    }
    var alerts by remember(session.tenantId, session.branchId, session.deviceCode) {
        mutableStateOf<List<WebQrActivityEvent>>(emptyList())
    }
    var apiError by remember(session.tenantId, session.branchId, session.deviceCode) {
        mutableStateOf(false)
    }
    var persistBusy by remember(session.tenantId, session.branchId, session.deviceCode) {
        mutableStateOf(false)
    }
    val scope = rememberCoroutineScope()
    val event = alerts.firstOrNull()

    LaunchedEffect(session.tenantId, session.branchId, session.deviceCode) {
        val local = withContext(Dispatchers.IO) {
            policyStore.read(session.tenantId, session.branchId) to store.readPending(session)
        }
        notificationSettings = local.first
        alerts = local.second
    }

    // Keep Android sound behavior aligned with the same branch setting used by CpIPOS Web.
    // Cashiers without settings:view keep the last verified local value; the QR activity
    // endpoint remains authoritative for whether order popups are emitted.
    LaunchedEffect(client, session.id) {
        while (true) {
            try {
                val server = client.notificationSettings(session)
                withContext(Dispatchers.IO) {
                    policyStore.save(session.tenantId, session.branchId, server)
                }
                notificationSettings = server
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: WebPosApiException) {
                if (failure.status == 401 || failure.status == 403) return@LaunchedEffect
            } catch (_: Exception) {
                // Keep cached policy while offline; operational QR polling is independent.
            }
            delay(60_000L)
        }
    }

    // One register-scoped poller for ALL Native screens; not restarted on tabs.
    LaunchedEffect(client, session.id) {
        var cursor = withContext(Dispatchers.IO) {
            store.cursor(session)
        } ?: Instant.now().minusSeconds(15).toString()
        var delayMs = 3000L
        while (true) {
            try {
                // Retry ONLY service acknowledgements; never retry a food submission.
                val pendingAcks = withContext(Dispatchers.IO) {
                    store.readAcks(session).entries.take(5)
                }
                for ((id, goToTable) in pendingAcks) {
                    try {
                        client.acknowledgeQrService(session, id, goToTable)
                        withContext(Dispatchers.IO) { store.confirmedAck(session, id) }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        // Server may have acknowledged already; retry is idempotent.
                    }
                }
                val result = client.qrActivity(session, cursor)
                apiError = false
                val seen = withContext(Dispatchers.IO) { store.readSeen(session) }
                val queued = alerts.map { it.id }.toSet()
                val fresh = result.events.filter {
                    it.id !in seen && it.id !in queued
                }
                if (fresh.isNotEmpty()) {
                    val next = (alerts + fresh).distinctBy { it.id }.takeLast(50)
                    withContext(Dispatchers.IO) { store.savePending(session, next) }
                    alerts = next
                }
                // Backend returns an inclusive/unchanged cursor when idle.
                // Timestamp overlap + local IDs prevents skipping same-ms events.
                val newer = runCatching { Instant.parse(result.cursor) }.getOrNull()
                val old = runCatching { Instant.parse(cursor) }.getOrNull()
                if (newer != null && old != null) {
                    val overlapped = newer.minusMillis(1000)
                    if (overlapped.isAfter(old)) {
                        cursor = overlapped.toString()
                        withContext(Dispatchers.IO) { store.setCursor(session, cursor) }
                    }
                }
                delayMs = if (fresh.isNotEmpty() || result.events.size >= 45) {
                    3000L
                } else (delayMs + 2000L).coerceAtMost(15000L)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: WebPosApiException) {
                apiError = true
                if (e.status == 401 || e.status == 403) return@LaunchedEffect
                delayMs = 15000L
            } catch (_: Exception) {
                apiError = true
                delayMs = 15000L
            }
            delay(delayMs)
        }
    }

    LaunchedEffect(event?.id) {
        if (event != null && notificationSettings.soundEnabled) {
            val volume = (notificationSettings.soundVolume * 100f)
                .roundToInt().coerceIn(1, 100)
            val sound = runCatching {
                ToneGenerator(AudioManager.STREAM_NOTIFICATION, volume)
            }.getOrNull()
            if (sound != null) {
                try {
                    sound.startTone(ToneGenerator.TONE_PROP_BEEP, 450)
                    delay(600L)
                } finally {
                    sound.release()
                }
            }
        }
    }

    fun closeCurrent(goToTable: Boolean) {
        val current = alerts.firstOrNull() ?: return
        if (persistBusy) return
        scope.launch {
            persistBusy = true
            try {
                val next = alerts.filterNot { it.id == current.id }
                withContext(Dispatchers.IO) {
                    store.markSeen(session, current.id)
                    if (current.type != "order") {
                        store.queueAck(session, current.id, goToTable)
                    }
                    store.savePending(session, next)
                }
                alerts = next
                if (goToTable) onGoToTable(current)
                if (current.type != "order") {
                    try {
                        client.acknowledgeQrService(session, current.id, goToTable)
                        withContext(Dispatchers.IO) { store.confirmedAck(session, current.id) }
                        apiError = false
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        // Persisted ACK will retry next foreground poll.
                        apiError = true
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Do not silently hide an unpersisted service ACK.
                apiError = true
            } finally {
                persistBusy = false
            }
        }
    }

    if (event != null) {
        Popup(
            alignment = Alignment.TopEnd,
            properties = PopupProperties(focusable = false, dismissOnClickOutside = false)
        ) {
            Surface(
                modifier = Modifier.statusBarsPadding().padding(11.dp)
                    .widthIn(max = 345.dp),
                color = Color(0xFFFAFCFF),
                shape = RoundedCornerShape(20.dp),
                shadowElevation = 16.dp
            ) {
                Column(Modifier.padding(15.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = Color(0xFFE8F2FF), shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(when (event.type) {
                                "call_staff" -> "🔔"
                                "request_checkout" -> "💳"
                                else -> "🍜"
                            }, modifier = Modifier.padding(8.dp), fontSize = 24.sp)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(when (event.type) {
                                "call_staff" -> "โต๊ะ ${event.tableCode} เรียกพนักงาน"
                                "request_checkout" -> "โต๊ะ ${event.tableCode} เรียกชำระเงิน"
                                else -> "มีรายการสั่งอาหารจากโต๊ะ ${event.tableCode}"
                            }, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold,
                                color = noticeInk)
                            if (event.type == "order") {
                                Text("${event.itemCount} รายการ" +
                                    (event.subtotal?.let { " • ฿" + it.bahtText() } ?: ""),
                                    fontSize = 11.sp, color = Color(0xFF637895))
                            }
                        }
                    }
                    if (alerts.size > 1) {
                        Text("มีอีก ${alerts.size - 1} การแจ้งเตือน",
                            color = noticeBlue, fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp)
                    }
                    if (apiError) {
                        Text("เครือข่ายไม่เสถียร ระบบจะตรวจสอบการรับทราบอีกครั้ง",
                            color = Color(0xFFB35B35), fontSize = 10.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            enabled = canNavigate && !persistBusy,
                            modifier = Modifier.weight(1f),
                            onClick = { closeCurrent(true) },
                            contentPadding = PaddingValues(horizontal = 9.dp, vertical = 9.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = noticeBlue)
                        ) { Text("ไปที่โต๊ะ ${event.tableCode}", fontSize = 11.sp) }
                        OutlinedButton(
                            modifier = Modifier.weight(0.75f),
                            enabled = !persistBusy,
                            onClick = { closeCurrent(false) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 9.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("รับทราบ / ปิด", fontSize = 11.sp) }
                    }
                    if (event.type == "order") {
                        Text("การปิดแจ้งเตือนไม่ใช่การยืนยันรายการอาหาร " +
                            "ยังต้องตรวจรับที่โต๊ะตามระบบหลัก",
                            fontSize = 10.sp, color = Color(0xFF72839D))
                    }
                }
            }
        }
    }
}
