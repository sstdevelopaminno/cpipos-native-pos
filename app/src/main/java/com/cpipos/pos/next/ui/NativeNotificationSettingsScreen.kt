package com.cpipos.pos.next.ui

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.provider.Settings
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.core.content.ContextCompat
import com.cpipos.pos.next.core.push.NativeFcmRegistration
import com.cpipos.pos.next.core.push.NativeQrBackgroundService
import com.cpipos.pos.next.core.push.NativeQrNotificationPublisher
import com.cpipos.pos.next.core.webpos.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val notificationBlue = Color(0xFF176DED)
private val notificationInk = Color(0xFF172B4E)

@Composable
internal fun NativeNotificationSettingsScreen(
    client: WebPosClient,
    session: WebPosSession,
    onTab: (String) -> Unit,
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val localStore = remember(context.applicationContext) {
        NativeQrNotificationPreferences(context.applicationContext)
    }
    val scope = rememberCoroutineScope()
    var settings by remember(session.id) {
        mutableStateOf(localStore.read(session.tenantId, session.branchId))
    }
    var loading by remember(session.id) { mutableStateOf(true) }
    var saving by remember(session.id) { mutableStateOf(false) }
    var error by remember(session.id) { mutableStateOf<String?>(null) }
    var savedNotice by remember(session.id) { mutableStateOf("") }
    var permissionRevision by remember { mutableIntStateOf(0) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionRevision++
        if (granted) {
            NativeQrBackgroundService.start(context)
            savedNotice = "อนุญาต Android Notification แล้ว"
        } else {
            error = "ยังไม่ได้อนุญาต Android Notification กรุณากดอนุญาตหรือเปิดสิทธิ์จาก Android Settings"
        }
    }

    LaunchedEffect(client, session.id) {
        try {
            val server = client.notificationSettings(session)
            settings = server
            localStore.save(session.tenantId, session.branchId, server)
            error = null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = when (failure) {
                is WebPosApiException -> when (failure.status) {
                    401, 403 -> "บัญชีนี้ไม่มีสิทธิ์แก้ไขการตั้งค่าแจ้งเตือนของสาขา"
                    else -> "โหลดค่าจาก CpIPOS ไม่สำเร็จ (" + failure.code + ")"
                }
                else -> "โหลดค่าการแจ้งเตือนไม่สำเร็จ กรุณาตรวจสอบเครือข่าย"
            }
        } finally {
            loading = false
        }
    }

    fun saveSettings() {
        if (saving || loading) return
        saving = true
        error = null
        scope.launch {
            try {
                val saved = client.updateNotificationSettings(session, settings)
                settings = saved
                localStore.save(session.tenantId, session.branchId, saved)
                savedNotice = "บันทึกการแจ้งเตือนแล้ว"
                delay(1800)
                savedNotice = ""
            } catch (failure: Exception) {
                error = when (failure) {
                    is WebPosApiException -> when (failure.status) {
                        401, 403 -> "ไม่มีสิทธิ์บันทึกการตั้งค่านี้"
                        else -> "บันทึกไม่สำเร็จ (" + failure.code + ")"
                    }
                    else -> "บันทึกไม่สำเร็จ กรุณาตรวจสอบอินเทอร์เน็ต"
                }
            } finally {
                saving = false
            }
        }
    }

    fun testSound() {
        if (!settings.soundEnabled) return
        scope.launch {
            val tone = runCatching {
                ToneGenerator(
                    AudioManager.STREAM_NOTIFICATION,
                    (settings.soundVolume * 100f).roundToInt().coerceIn(1, 100)
                )
            }.getOrNull() ?: return@launch
            try {
                tone.startTone(ToneGenerator.TONE_PROP_BEEP, 450)
                delay(550)
            } finally {
                tone.release()
            }
        }
    }

    @Suppress("UNUSED_VARIABLE")
    val refreshPermissionState = permissionRevision
    val firebaseReady = NativeFcmRegistration.hasFirebase(context)
    val pushBound = firebaseReady &&
        NativeFcmRegistration.sameBoundBranch(context, session.tenantId, session.branchId)
    val runtimePermissionGranted = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
    val notificationAllowed = NativeQrNotificationPublisher.notificationsAllowed(context)
    val backgroundListenerReady = notificationAllowed && settings.popupEnabled

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF5F9FF)) {
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().statusBarsPadding()
                    .padding(start = 17.dp, end = 17.dp, top = 15.dp, bottom = 112.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onBack != null) {
                        TextButton(onClick = onBack) { Text("‹ ตั้งค่า") }
                    }
                    Column(Modifier.weight(1f)) {
                        Text("ตั้งค่าการแจ้งเตือน", fontSize = 24.sp,
                            color = notificationInk, fontWeight = FontWeight.ExtraBold)
                        Text(
                            session.branchName + " · ใช้ค่าชุดเดียวกับ CpIPOS",
                            color = Color(0xFF71849B), fontSize = 12.sp
                        )
                    }
                }

                if (loading) {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = notificationBlue)
                    }
                } else {
                    Surface(color = Color.White, shape = RoundedCornerShape(18.dp)) {
                        Column(
                            Modifier.fillMaxWidth().padding(17.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            SettingSwitchRow(
                                title = "POP UP จาก QR โต๊ะ",
                                subtitle = "แจ้งสั่งอาหาร เรียกพนักงาน และเรียกชำระเงิน",
                                checked = settings.popupEnabled,
                                enabled = !saving,
                                onChecked = { settings = settings.copy(popupEnabled = it) }
                            )
                            HorizontalDivider(color = Color(0xFFE8EEF7))
                            SettingSwitchRow(
                                title = "เสียงแจ้งเตือน",
                                subtitle = "เล่นเสียงเมื่อมีรายการ QR ใหม่เข้ามา",
                                checked = settings.soundEnabled,
                                enabled = !saving,
                                onChecked = { settings = settings.copy(soundEnabled = it) }
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(
                                    "ระดับเสียง " + Math.round(settings.soundVolume * 100) + "%",
                                    fontWeight = FontWeight.Bold, color = notificationInk
                                )
                                Slider(
                                    value = settings.soundVolume,
                                    onValueChange = {
                                        settings = settings.copy(soundVolume = it.coerceIn(0f, 1f))
                                    },
                                    valueRange = 0f..1f,
                                    enabled = settings.soundEnabled && !saving
                                )
                                OutlinedButton(
                                    onClick = { testSound() },
                                    enabled = settings.soundEnabled && !saving
                                ) { Text("ทดสอบเสียง") }
                            }
                        }
                    }

                    Surface(color = Color.White, shape = RoundedCornerShape(18.dp)) {
                        Column(
                            Modifier.fillMaxWidth().padding(17.dp),
                            verticalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            Text("Android Push Notification", fontWeight = FontWeight.ExtraBold,
                                color = notificationInk)
                            PushStateRow("Background Listener", backgroundListenerReady)
                            PushStateRow("สิทธิ์แจ้งเตือน Android", notificationAllowed)
                            PushStateRow("Firebase ใน APK (เสริม)", firebaseReady)
                            PushStateRow("ผูก FCM กับสาขานี้ (เสริม)", pushBound)

                            if (!runtimePermissionGranted && Build.VERSION.SDK_INT >= 33) {
                                Button(
                                    onClick = {
                                        notificationPermissionLauncher.launch(
                                            Manifest.permission.POST_NOTIFICATIONS
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = notificationBlue)
                                ) {
                                    Text("อนุญาต Android Notification", fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = {
                                        context.startActivity(
                                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                            }
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("เปิด Android Settings")
                                }
                            } else if (!notificationAllowed) {
                                OutlinedButton(
                                    onClick = {
                                        context.startActivity(
                                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                            }
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("เปิดสิทธิ์แจ้งเตือนใน Android Settings")
                                }
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        val shown = NativeQrNotificationPublisher.showTest(
                                            context, settings.soundEnabled
                                        )
                                        savedNotice = if (shown)
                                            "ส่ง Android Notification ทดสอบแล้ว"
                                        else "ส่ง Notification ไม่สำเร็จ กรุณาตรวจสิทธิ์ Android"
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("ทดสอบ Android Notification")
                                }
                            }

                            Text(
                                if (firebaseReady)
                                    "ระบบใช้ FCM เมื่อพร้อม และมี Background Listener เป็น fallback"
                                else
                                    "Firebase ยังไม่ถูกตั้งค่า แต่ Background Listener สามารถรับ QR ตอนออกจากแอปได้ " +
                                        "เมื่อ Android Notification ได้รับอนุญาตและเปิด POP UP จาก QR โต๊ะ",
                                fontSize = 11.sp,
                                color = if (backgroundListenerReady) Color(0xFF178A50)
                                    else Color(0xFFB05A36)
                            )
                        }
                    }

                    error?.let {
                        Text(it, color = Color(0xFFB33A49), fontSize = 12.sp)
                    }
                    if (savedNotice.isNotBlank()) {
                        Text(savedNotice, color = Color(0xFF178A50), fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { saveSettings() },
                        enabled = !saving && error?.contains("ไม่มีสิทธิ์") != true,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = notificationBlue)
                    ) {
                        if (saving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                        } else Text("บันทึกการตั้งค่า", fontWeight = FontWeight.Bold)
                    }
                }
            }

            HomeBottomMenu(
                modifier = Modifier.align(Alignment.BottomCenter),
                selectedTab = "settings",
                onMenuClick = onTab
            )
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onChecked: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, color = notificationInk)
            Text(subtitle, fontSize = 11.sp, color = Color(0xFF71849B))
        }
        Switch(checked = checked, onCheckedChange = onChecked, enabled = enabled)
    }
}

@Composable
private fun PushStateRow(label: String, ready: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 12.sp, color = Color(0xFF5F728D))
        Text(
            if (ready) "พร้อม" else "ยังไม่พร้อม",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (ready) Color(0xFF178A50) else Color(0xFFB05A36)
        )
    }
}
