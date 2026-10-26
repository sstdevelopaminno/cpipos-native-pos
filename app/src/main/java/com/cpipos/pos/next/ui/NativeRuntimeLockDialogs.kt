package com.cpipos.pos.next.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cpipos.pos.next.core.webpos.WebSubscriptionRuntime
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

private val runtimeMoney = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.US))

@Composable
internal fun NativeSubscriptionLockDialog(
    runtime: WebSubscriptionRuntime?,
    onBilling: () -> Unit
) {
    val state = runtime ?: return
    if (!state.locked || state.exempt) return

    AlertDialog(
        onDismissRequest = {},
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                "แพ็กเกจหมดอายุ / ระบบถูกระงับ",
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF172B4E)
            )
        },
        text = {
            Column {
                Text(
                    "แพ็กเกจ " + state.packageName + " ไม่สามารถใช้งานระบบ POS ต่อได้ในขณะนี้ " +
                        "กรุณาชำระหรือต่ออายุแพ็กเกจ ระบบจะปลดล็อกอัตโนมัติหลัง CpIPOS / IT ยืนยันสถานะ",
                    color = Color(0xFF66758D),
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )
                state.amountDue?.takeIf { it > 0.0 }?.let { amount ->
                    Text(
                        "ยอดอ้างอิง: " +
                            (if (state.currency == "THB") "฿" else state.currency + " ") +
                            runtimeMoney.format(amount),
                        modifier = Modifier.padding(top = 10.dp),
                        color = Color(0xFF176DED),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onBilling,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF176DED))
            ) {
                Text("ไปชำระแพ็กเกจ", fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
internal fun NativeMdmDeviceLockDialog() {
    AlertDialog(
        onDismissRequest = {},
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                "อุปกรณ์ถูกปิดใช้งานโดย IT",
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF172B4E)
            )
        },
        text = {
            Text(
                "เครื่อง POS นี้ถูกล็อกจากระบบ MDM กรุณาติดต่อผู้ดูแล IT " +
                    "แอปจะตรวจสอบสถานะกับ CpIPOS อัตโนมัติและปลดล็อกเมื่อได้รับคำสั่งเปิดใช้งาน",
                color = Color(0xFF66758D),
                fontSize = 13.sp,
                lineHeight = 20.sp
            )
        },
        confirmButton = {}
    )
}
