package com.cpipos.pos.next.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
 * Phase advances to success only after server-authoritative checkout
 * (and durable journal acknowledgement) completed.
 */
@Composable
internal fun PaymentFeedbackOverlay(phase: String) {
    if (phase == "idle") return
    val complete = phase == "success"
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp), color = Color.White,
            shadowElevation = 12.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 34.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (complete) {
                    Box(
                        modifier = Modifier.size(88.dp)
                            .background(Color(0xFFE5F8EC), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✓", fontSize = 53.sp,
                            fontWeight = FontWeight.ExtraBold, color = Color(0xFF1B9B51))
                    }
                } else {
                    Box(modifier = Modifier.size(88.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(69.dp),
                            color = Color(0xFF176EED),
                            strokeWidth = 6.dp
                        )
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    if (complete) "ขายสำเร็จ" else "กำลังบันทึกการขาย",
                    fontSize = 21.sp, fontWeight = FontWeight.ExtraBold,
                    color = if (complete) Color(0xFF16874B) else Color(0xFF122B52),
                    textAlign = TextAlign.Center
                )
                Text(
                    if (complete) "ระบบบันทึกยอดขายเรียบร้อยแล้ว"
                    else "กรุณารอระบบยืนยันรายการ อย่าปิดแอปหรือกดชำระซ้ำ",
                    fontSize = 12.sp, color = Color(0xFF6C7E99),
                    textAlign = TextAlign.Center, lineHeight = 19.sp
                )
            }
        }
    }
}
