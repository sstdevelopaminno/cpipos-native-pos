package com.cpipos.pos.next.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cpipos.pos.next.core.webpos.WebPosClient
import com.cpipos.pos.next.core.webpos.WebPosSession

private val settingsHubInk = Color(0xFF172B4E)
private val settingsHubBlue = Color(0xFF176DED)
private val settingsHubMuted = Color(0xFF71849B)

@Composable
internal fun NativeSettingsHubScreen(
    client: WebPosClient,
    session: WebPosSession,
    onTab: (String) -> Unit
) {
    var page by remember(session.id) { mutableStateOf("hub") }

    fun routeBottom(tab: String) {
        if (tab == "settings") page = "hub" else onTab(tab)
    }

    when (page) {
        "store" -> {
            NativeStoreSettingsScreen(
                client = client,
                session = session,
                onBack = { page = "hub" },
                onTab = ::routeBottom
            )
            return
        }
        "notifications" -> {
            NativeNotificationSettingsScreen(
                client = client,
                session = session,
                onTab = ::routeBottom,
                onBack = { page = "hub" }
            )
            return
        }
        "payments" -> {
            NativePaymentSettingsScreen(
                client = client,
                session = session,
                onBack = { page = "hub" },
                onTab = ::routeBottom
            )
            return
        }
        "printer" -> {
            NativePrinterSettingsScreen(
                client = client,
                session = session,
                onBack = { page = "hub" },
                onTab = ::routeBottom
            )
            return
        }
    }

    Surface(Modifier.fillMaxSize(), color = Color(0xFFF5F9FF)) {
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().statusBarsPadding()
                    .padding(start = 17.dp, end = 17.dp, top = 15.dp, bottom = 112.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    "ตั้งค่า",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = settingsHubInk
                )
                Text(
                    session.branchName + " · เครื่อง " + session.deviceCode,
                    fontSize = 12.sp,
                    color = settingsHubMuted
                )

                SettingsHubCard(
                    icon = "🏪",
                    title = "ตั้งชื่อร้านค้า / บริษัท",
                    subtitle = "ชื่อร้าน โลโก้ ที่อยู่ และเบอร์ติดต่อ · ใช้ร่วมกับ CpIPOS และใบเสร็จ",
                    onClick = { page = "store" }
                )
                SettingsHubCard(
                    icon = "🔔",
                    title = "ตั้งค่าการแจ้งเตือน",
                    subtitle = "POP UP, เสียง, ระดับเสียง และสถานะ Android Notification",
                    onClick = { page = "notifications" }
                )
                SettingsHubCard(
                    icon = "💳",
                    title = "ตั้งค่าชำระเงิน",
                    subtitle = "บัญชีรับโอน PromptPay และ QR · ใช้ร่วมกับ CpIPOS และหน้าชำระเงิน",
                    onClick = { page = "payments" }
                )
                SettingsHubCard(
                    icon = "🖨️",
                    title = "ตั้งค่า เครื่องปริ้นใบเสร็จ",
                    subtitle = "ค้นหาและเชื่อมต่อ LAN / USB / Bluetooth พร้อมกำหนดเส้นทางพิมพ์",
                    onClick = { page = "printer" }
                )
            }

            HomeBottomMenu(
                modifier = Modifier.align(Alignment.BottomCenter),
                selectedTab = "settings",
                onMenuClick = ::routeBottom
            )
        }
    }
}

@Composable
private fun SettingsHubCard(
    icon: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(18.dp),
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            Surface(
                color = Color(0xFFE8F2FF),
                shape = RoundedCornerShape(13.dp)
            ) {
                Text(icon, fontSize = 25.sp, modifier = Modifier.padding(9.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    fontWeight = FontWeight.ExtraBold,
                    color = settingsHubInk,
                    fontSize = 15.sp
                )
                Text(
                    subtitle,
                    color = settingsHubMuted,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
            Text("›", color = settingsHubBlue, fontSize = 28.sp)
        }
    }
}
