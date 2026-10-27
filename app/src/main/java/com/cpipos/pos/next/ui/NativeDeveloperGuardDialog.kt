package com.cpipos.pos.next.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.DialogProperties
import com.cpipos.pos.next.core.security.AndroidDeveloperSignals

/** IT opt-in rule: show a non-dismissible notice while prohibited Android flags are enabled. */
@Composable
fun NativeDeveloperGuardDialog(signals: AndroidDeveloperSignals) {
    if (!signals.detected) return
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text("กรุณาปิดโหมดนักพัฒนา", fontWeight = FontWeight.Bold) },
        text = {
            Text(
                "ฝ่าย IT ของร้านนี้เปิดระบบป้องกัน Android ไว้\n" +
                "ตรวจพบ: ${signals.details}\n\n" +
                "กรุณาเปิดการตั้งค่า Android และปิด Developer Options รวมถึง " +
                "USB Debugging / Wireless Debugging แล้วกลับเข้าแอปอีกครั้ง " +
                "ระบบจะตรวจสอบสถานะให้อัตโนมัติ\n\n" +
                "การเชื่อมต่อเครื่องพิมพ์ USB ตามปกติไม่ถือว่าเป็นการเปิดโหมดนักพัฒนา"
            )
        },
        confirmButton = {
            Button(onClick = {
                // Open ordinary OS settings; no attempt to change protected flags.
                runCatching {
                    context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }) { Text("เปิดการตั้งค่า Android") }
        }
    )
}

/** No cashier transactions before the first authenticated IT policy read. */
@Composable
fun NativeDeveloperPolicyLoadingDialog() {
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text("กำลังตรวจสอบความปลอดภัย", fontWeight = FontWeight.Bold) },
        text = { Text("กำลังอ่านนโยบาย Android ของร้านจาก CpIPOS / IT กรุณาตรวจสอบอินเทอร์เน็ต หากระบบยังไม่ตอบกลับ หน้าขายจะไม่เปิดเพื่อป้องกันการข้ามเงื่อนไขความปลอดภัย") },
        confirmButton = {}
    )
}
