package com.cpipos.pos.next.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.cpipos.pos.next.core.webpos.WebPosEntitlements

private val settingsHubInk = Color(0xFF172B4E)
private val settingsHubBlue = Color(0xFF176DED)
private val settingsHubMuted = Color(0xFF71849B)

@Composable
internal fun NativeSettingsHubScreen(
    client: WebPosClient,
    session: WebPosSession,
    onTab: (String) -> Unit,
    entitlements: WebPosEntitlements? = null
) {
    var page by remember(session.id) { mutableStateOf("hub") }
    var packageLockTarget by remember(session.id) { mutableStateOf<String?>(null) }

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
        "branches" -> {
            NativeBranchSettingsScreen(client, session, { page = "hub" }, ::routeBottom)
            return
        }
        "devices" -> {
            NativeDeviceSettingsScreen(client, session, { page = "hub" }, ::routeBottom)
            return
        }
        "users" -> {
            NativePosUsersSettingsScreen(client, session, { page = "hub" }, ::routeBottom)
            return
        }
        "table_qr" -> {
            NativeTableQrSettingsScreen(client, session, { page = "hub" }, ::routeBottom)
            return
        }
        "taxes" -> {
            NativeTaxSettingsScreen(client, session, { page = "hub" }, ::routeBottom)
            return
        }
        "language" -> {
            NativeLanguageSettingsScreen(session, { page = "hub" }, ::routeBottom)
            return
        }
        "order_kitchen" -> {
            NativeOrderKitchenSettingsScreen(client, session, entitlements, { page = "hub" }, ::routeBottom)
            return
        }
        "support" -> {
            NativeHelpCenterScreen(client, session, { page = "hub" }, ::routeBottom)
            return
        }
    }

    Surface(Modifier.fillMaxSize(), color = Color(0xFFF5F9FF)) {
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().statusBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 17.dp, end = 17.dp, top = 15.dp, bottom = 212.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    nativeUiText("settings.title", "ตั้งค่า"),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = settingsHubInk
                )
                Text(
                    session.branchName + " · เครื่อง " + session.deviceCode,
                    fontSize = 12.sp,
                    color = settingsHubMuted
                )

                fun allowed(feature: String, menu: String): Boolean =
                    entitlements?.allows(
                        featureCode = feature,
                        menuKey = menu,
                        parentMenuKey = "main.settings"
                    ) ?: true

                val storeAllowed = allowed("core_pos_sales", "settings.store")
                SettingsHubCard(
                    icon = "🏪",
                    title = nativeUiText("settings.store","ตั้งชื่อร้านค้า / บริษัท"),
                    subtitle = "ชื่อร้าน โลโก้ ที่อยู่ และเบอร์ติดต่อ · ใช้ร่วมกับ CpIPOS และใบเสร็จ",
                    locked = !storeAllowed,
                    onClick = {
                        if (storeAllowed) page = "store"
                        else packageLockTarget = "ตั้งชื่อร้านค้า / บริษัท"
                    }
                )

                val branchesAllowed = allowed("branch_management", "settings.branches")
                SettingsHubCard(
                    icon = "🏬",
                    title = nativeUiText("settings.branches","เพิ่มสาขา"),
                    subtitle = "เพิ่ม แก้ไข และปิดใช้งานสาขา · ใช้ข้อมูลชุดเดียวกับ CpIPOS",
                    locked = !branchesAllowed,
                    onClick = { if (branchesAllowed) page = "branches" else packageLockTarget = "เพิ่มสาขา" }
                )

                val devicesAllowed = allowed("mobile_device_enrollment", "settings.devices")
                SettingsHubCard(
                    icon = "🧾",
                    title = nativeUiText("settings.devices","เพิ่มเครื่องแคชเชียร์"),
                    subtitle = "ผูกเครื่อง POS กับสาขา รหัสเครื่อง Counter สถานะ และนโยบายล็อกอิน",
                    locked = !devicesAllowed,
                    onClick = { if (devicesAllowed) page = "devices" else packageLockTarget = "เพิ่มเครื่องแคชเชียร์" }
                )

                val usersAllowed = allowed("user_management", "settings.users")
                SettingsHubCard(
                    icon = "👥",
                    title = nativeUiText("settings.users","ผู้ใช้งาน POS"),
                    subtitle = "พนักงาน บทบาท รหัสพนักงาน PIN Permission Role และขอบเขตเครื่อง",
                    locked = !usersAllowed,
                    onClick = { if (usersAllowed) page = "users" else packageLockTarget = "ผู้ใช้งาน POS" }
                )

                val tableQrAllowed = (entitlements?.menuEnabled("settings.table_qr", "main.settings") ?: true) &&
                    (entitlements?.featureEnabled("table_management") ?: true) &&
                    (entitlements?.featureEnabled("qr_table_ordering") ?: true)
                SettingsHubCard(
                    icon = "▦",
                    title = nativeUiText("settings.table_qr","ตั้งค่า QR โต๊ะ"),
                    subtitle = "กำหนดอายุ QR แบบตามเวลา/ชั่วโมง หรือใช้งานจนปิดบิล",
                    locked = !tableQrAllowed,
                    onClick = { if (tableQrAllowed) page = "table_qr" else packageLockTarget = "ตั้งค่า QR โต๊ะ" }
                )

                val taxesAllowed = allowed("core_pos_sales", "settings.taxes")
                SettingsHubCard(
                    icon = "％",
                    title = nativeUiText("settings.taxes","ตั้งค่าภาษี"),
                    subtitle = "VAT และภาษีหัก ณ ที่จ่าย · คำนวณจากยอดหลังส่วนลดเหมือน CpIPOS",
                    locked = !taxesAllowed,
                    onClick = { if (taxesAllowed) page = "taxes" else packageLockTarget = "ตั้งค่าภาษี" }
                )

                val languageAllowed = entitlements?.menuEnabled("settings.language", "main.settings") ?: true
                SettingsHubCard(
                    icon = "🌐",
                    title = nativeUiText("settings.language","เปลี่ยนภาษา"),
                    subtitle = "ไทย / English / ລາວ ตามตัวเลือกของ CpIPOS",
                    locked = !languageAllowed,
                    onClick = { if (languageAllowed) page = "language" else packageLockTarget = "เปลี่ยนภาษา" }
                )

                val orderKitchenAllowed = (entitlements?.menuEnabled("settings.order_kitchen", "main.settings") ?: true) &&
                    ((entitlements?.featureEnabled("qr_table_ordering") ?: true) ||
                        (entitlements?.featureEnabled("kitchen_printing") ?: true))
                SettingsHubCard(
                    icon = "🍳",
                    title = nativeUiText("settings.order_kitchen","การแจ้งเตือนออเดอร์และครัว"),
                    subtitle = "POP UP, ส่งเข้าครัวอัตโนมัติ และพิมพ์ครัวอัตโนมัติ พร้อม IT override",
                    locked = !orderKitchenAllowed,
                    onClick = { if (orderKitchenAllowed) page = "order_kitchen" else packageLockTarget = "การแจ้งเตือนออเดอร์และครัว" }
                )

                val supportAllowed = entitlements?.menuEnabled("settings.support", "main.settings") ?: true
                SettingsHubCard(
                    icon = "🎧",
                    title = nativeUiText("settings.support","ศูนย์ช่วยเหลือ"),
                    subtitle = "CpIPOS Support Center · LINE และช่องทางติดต่อบริษัท",
                    locked = !supportAllowed,
                    onClick = { if (supportAllowed) page = "support" else packageLockTarget = "ศูนย์ช่วยเหลือ" }
                )

                val notificationsAllowed = allowed("qr_table_ordering", "settings.notifications")
                SettingsHubCard(
                    icon = "🔔",
                    title = nativeUiText("settings.notifications","ตั้งค่าการแจ้งเตือน"),
                    subtitle = "POP UP, เสียง, ระดับเสียง และสถานะ Android Notification",
                    locked = !notificationsAllowed,
                    onClick = {
                        if (notificationsAllowed) page = "notifications"
                        else packageLockTarget = "ตั้งค่าการแจ้งเตือน"
                    }
                )

                val paymentsAllowed = allowed("core_pos_sales", "settings.payments")
                SettingsHubCard(
                    icon = "💳",
                    title = nativeUiText("settings.payments","ตั้งค่าชำระเงิน"),
                    subtitle = "บัญชีรับโอน PromptPay และ QR · ใช้ร่วมกับ CpIPOS และหน้าชำระเงิน",
                    locked = !paymentsAllowed,
                    onClick = {
                        if (paymentsAllowed) page = "payments"
                        else packageLockTarget = "ตั้งค่าชำระเงิน"
                    }
                )

                val printerAllowed = allowed("core_pos_sales", "settings.printers")
                SettingsHubCard(
                    icon = "🖨️",
                    title = nativeUiText("settings.printers","ตั้งค่า เครื่องปริ้นใบเสร็จ"),
                    subtitle = "ค้นหาและเชื่อมต่อ LAN / USB / Bluetooth พร้อมกำหนดเส้นทางพิมพ์",
                    locked = !printerAllowed,
                    onClick = {
                        if (printerAllowed) page = "printer"
                        else packageLockTarget = "ตั้งค่าเครื่องปริ้นใบเสร็จ"
                    }
                )
            }

            HomeBottomMenu(
                modifier = Modifier.align(Alignment.BottomCenter),
                selectedTab = "settings",
                onMenuClick = ::routeBottom
            )
        }
    }

    NativePackageLockDialog(
        targetLabel = packageLockTarget,
        onDismiss = { packageLockTarget = null }
    )
}

@Composable
private fun SettingsHubCard(
    icon: String,
    title: String,
    subtitle: String,
    locked: Boolean = false,
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
            if (locked) {
                Surface(
                    color = Color(0xFFFFF3DD),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(
                        "แพ็กเกจ",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = Color(0xFF9B6518),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.width(6.dp))
            }
            Text("›", color = if (locked) settingsHubMuted else settingsHubBlue, fontSize = 28.sp)
        }
    }
}
