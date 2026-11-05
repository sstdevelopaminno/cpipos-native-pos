package com.cpipos.pos.next.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.cpipos.pos.next.core.update.NativeApkUpdateManager
import kotlinx.coroutines.delay

/**
 * Safe, opt-out-of-prompt update: release lookup and APK download start on app open.
 * Android's own install confirmation remains mandatory for sideloaded apps.
 * This overlay does not change, re-send, or settle a cashier's active transaction.
 */
@Composable
internal fun NativeStableUpdateOverlay() {
    val context = LocalContext.current
    val update by NativeApkUpdateManager.state.collectAsState()
    var postponedVersion by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { NativeApkUpdateManager.checkAndStart(context.applicationContext) }

    LaunchedEffect(update.phase) {
        if (update.phase == "downloading") {
            while (true) {
                delay(3000L)
                NativeApkUpdateManager.poll(context.applicationContext)
                if (NativeApkUpdateManager.state.value.phase != "downloading") break
            }
        }
    }

    if (update.phase == "ready" && update.versionName != postponedVersion) {
        AlertDialog(
            onDismissRequest = { postponedVersion = update.versionName },
            title = { Text("CpiPOS Mobile มีเวอร์ชันใหม่") },
            text = { Text("ดาวน์โหลดเวอร์ชัน " + update.versionName +
                " และตรวจสอบไฟล์ APK กับลายเซ็นแล้ว กดติดตั้งเพื่อเปิดหน้าติดตั้งของ Android " +
                "ระบบจะขอให้ยืนยันการอัปเดต และจะไม่ลบข้อมูลหรือสร้างบิลใหม่") },
            confirmButton = {
                TextButton(onClick = {
                    NativeApkUpdateManager.openSystemInstaller(context)
                }) { Text("ติดตั้งอัปเดต") }
            },
            dismissButton = {
                TextButton(onClick = { postponedVersion = update.versionName }) {
                    Text("ภายหลัง")
                }
            }
        )
    }
}
