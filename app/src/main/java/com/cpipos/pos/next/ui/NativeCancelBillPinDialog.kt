package com.cpipos.pos.next.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Shared masked PIN entry for table cancellations and sales-list voids.
 * PIN only exists in transient composition state, never in APK constants,
 * server logs, journal, disk or receipt data. Server owns all authorization.
 */
@Composable
internal fun NativeCancelBillPinDialog(
    title: String,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onApprove: (String) -> Unit,
    actionLabel: String = "ยืนยันการยกเลิก",
    actionNote: String = "ระบบจะตรวจสิทธิ์จากเซิร์ฟเวอร์ก่อนเปลี่ยนสถานะบิล"
) {
    var pin by remember(title) { mutableStateOf("") }
    val ink = Color(0xFF172749)
    val blue = Color(0xFF176DED)
    Dialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = !busy, dismissOnClickOutside = !busy
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(27.dp),
            color = Color(0xFFFDFEFF),
            shadowElevation = 13.dp
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 21.dp, vertical = 19.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier.size(52.dp)
                        .background(Color(0xFFE7F0FF), CircleShape),
                    contentAlignment = Alignment.Center
                ) { Text("🔐", fontSize = 26.sp) }
                Text(title, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold,
                    color = ink, textAlign = TextAlign.Center)
                Text(
                    "กรอกรหัส PIN ของผู้มีสิทธิ์อนุมัติ ตามการตั้งค่า CpIPOS หลัก",
                    fontSize = 12.sp, color = Color(0xFF73839A),
                    textAlign = TextAlign.Center
                )
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFFF0F5FD),
                    shape = RoundedCornerShape(17.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            if (pin.isEmpty()) "• • • •" else List(pin.length) { "*" }.joinToString(" "),
                            fontSize = 26.sp, fontWeight = FontWeight.ExtraBold,
                            color = if (pin.isEmpty()) Color(0xFFABB9CE) else blue,
                            textAlign = TextAlign.Center
                        )
                        Text("${pin.length} หลัก", fontSize = 11.sp,
                            color = Color(0xFF8796AD))
                    }
                }
                val keys = listOf(
                    listOf("1", "2", "3"), listOf("4", "5", "6"),
                    listOf("7", "8", "9"), listOf("ล้าง", "0", "⌫")
                )
                keys.forEach { keysRow ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        keysRow.forEach { key ->
                            OutlinedButton(
                                onClick = {
                                    if (!busy) {
                                        pin = when (key) {
                                            "ล้าง" -> ""
                                            "⌫" -> pin.dropLast(1)
                                            else -> if (pin.length < 12) pin + key else pin
                                        }
                                    }
                                },
                                enabled = !busy,
                                modifier = Modifier.weight(1f).height(52.dp),
                                shape = RoundedCornerShape(15.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                            ) {
                                Text(key,
                                    fontSize = if (key == "ล้าง") 13.sp else 23.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (key == "ล้าง") Color(0xFFB94A60) else ink)
                            }
                        }
                    }
                }
                error?.let {
                    Text(it, color = Color(0xFFB4233A),
                        fontSize = 12.sp, textAlign = TextAlign.Center)
                }
                Text(
                    actionNote,
                    fontSize = 11.sp, color = Color(0xFF8091A6),
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = {
                        if (!busy && pin.length in 4..12) {
                            val submitted = pin
                            pin = ""
                            onApprove(submitted)
                        }
                    },
                    enabled = !busy && pin.length in 4..12,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(15.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = blue)
                ) {
                    if (busy) CircularProgressIndicator(
                        modifier = Modifier.size(20.dp), color = Color.White
                    )
                    else Text(actionLabel, fontSize = 15.sp,
                        fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = onDismiss, enabled = !busy) {
                    Text("กลับ", color = Color(0xFF6E809A))
                }
            }
        }
    }
}
