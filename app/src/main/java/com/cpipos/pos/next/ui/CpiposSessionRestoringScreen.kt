package com.cpipos.pos.next.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cpipos.pos.next.R

/**
 * Visual-only loading state while the original SSO cookie is verified by Web POS.
 * Animated arcs have no timers, extra API requests or session side effects.
 */
@Composable
internal fun CpiposSessionRestoringScreen(
    headline: String = "กำลังตรวจสอบการเข้าสู่ระบบ",
    subtitle: String = "เชื่อมต่อบัญชีร้านและสิทธิ์การใช้งาน..."
) {
    val animation = rememberInfiniteTransition(label = "cpipos-session-loader")
    val rotation by animation.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1250, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spinner-rotation"
    )
    val reverseRotation by animation.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "inner-spinner"
    )
    val pulse by animation.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1050),
            repeatMode = RepeatMode.Reverse
        ),
        label = "logo-pulse"
    )

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF5F9FF)) {
        Box(
            modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                .background(Brush.verticalGradient(
                    colors = listOf(Color(0xFFFBFCFF), Color(0xFFEFF6FF), Color(0xFFF8FCFF))
                )),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Box(
                    modifier = Modifier.size(168.dp)
                        .semantics { contentDescription = "กำลังตรวจสอบเซสชัน CpIPOS" },
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(150.dp).rotate(rotation)) {
                        drawArc(
                            brush = Brush.sweepGradient(
                                listOf(
                                    Color(0xFF1166F2), Color(0xFF27B2FF),
                                    Color(0xFF71E0FD), Color(0xFF7358F8),
                                    Color(0xFF1166F2)
                                )
                            ),
                            startAngle = -90f,
                            sweepAngle = 275f,
                            useCenter = false,
                            style = Stroke(width = 15.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                    Canvas(modifier = Modifier.size(116.dp).rotate(reverseRotation)) {
                        drawArc(
                            color = Color(0x66569CEF),
                            startAngle = -90f,
                            sweepAngle = 130f,
                            useCenter = false,
                            style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                    Box(
                        modifier = Modifier.size((88f * pulse).dp)
                            .shadow(6.dp, CircleShape)
                            .background(Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_cpipos_receipt_logo),
                            contentDescription = "โลโก้ CpIPOS",
                            modifier = Modifier.size(64.dp)
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "CpIPOS",
                    color = Color(0xFF15365A),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 24.sp
                )
                Text(
                    headline,
                    color = Color(0xFF223A5C),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp
                )
                Text(
                    subtitle,
                    color = Color(0xFF7386A1),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
