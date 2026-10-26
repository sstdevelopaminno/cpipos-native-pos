package com.cpipos.pos.next.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun NativePackageLockDialog(
    targetLabel: String?,
    onDismiss: () -> Unit
) {
    if (targetLabel == null) return

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                "ฟีเจอร์นี้ยังไม่เปิดในแพ็กเกจ",
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF172B4E)
            )
        },
        text = {
            Text(
                "“$targetLabel” ไม่ได้เปิดใช้งานในแพ็กเกจหรือการตั้งค่าปัจจุบันจากระบบ IT " +
                    "กรุณาติดต่อผู้ดูแลระบบ IT หรือเปลี่ยนแพ็กเกจเพื่อเปิดใช้งานฟีเจอร์นี้",
                color = Color(0xFF66758D),
                fontSize = 13.sp,
                lineHeight = 20.sp
            )
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF176DED))
            ) {
                Text("รับทราบ", fontWeight = FontWeight.Bold)
            }
        }
    )
}
