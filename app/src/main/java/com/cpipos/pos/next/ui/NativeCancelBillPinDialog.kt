package com.cpipos.pos.next.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

/**
 * PIN entry shared by table-list long-press and the actual dine-in cart.
 * Never put PIN in a State that survives this dialog, a journal or log.
 * Verification, configured staff exceptions and approval expiry live in
 * CpIPOS Web's /api/backoffice/approvals/pin.
 */
@Composable
internal fun NativeCancelBillPinDialog(
    title: String,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onApprove: (String) -> Unit
) {
    var pin by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        shape = RoundedCornerShape(22.dp),
        title = { Text(title, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "กรอกรหัส PIN ผู้มีสิทธิ์อนุมัติการยกเลิกบิล ตามที่ตั้งค่าใน CpIPOS หลัก",
                    color = Color(0xFF627590)
                )
                OutlinedTextField(
                    value = pin,
                    onValueChange = { raw ->
                        if (!busy && raw.length <= 12 && raw.all { it.isDigit() }) pin = raw
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("รหัส PIN ผู้อนุมัติ") },
                    singleLine = true,
                    enabled = !busy,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
                error?.let {
                    Text(it, color = Color(0xFFB23B47))
                }
                Text(
                    "ยกเลิกเฉพาะบิลที่ยังไม่ชำระ ระบบไม่ลบยอดขายที่ชำระแล้ว",
                    color = Color(0xFF667B96)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (pin.length in 4..12) {
                        val submitted = pin
                        pin = ""
                        onApprove(submitted)
                    }
                },
                enabled = !busy && pin.length in 4..12
            ) {
                Text(if (busy) "กำลังตรวจสิทธิ์..." else "อนุมัติและยกเลิกบิล")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("กลับ") }
        }
    )
}
