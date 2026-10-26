package com.cpipos.pos.next.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeSettingsManagementContractTest {
    private fun source(path: String): String =
        File(path).readText()

    @Test
    fun settingsHubContainsAllRequestedCpiposMenus() {
        val source = source("src/main/java/com/cpipos/pos/next/ui/NativeSettingsHubScreen.kt")
        listOf(
            "เพิ่มสาขา",
            "เพิ่มเครื่องแคชเชียร์",
            "ผู้ใช้งาน POS",
            "ตั้งค่า QR โต๊ะ",
            "ตั้งค่าภาษี",
            "เปลี่ยนภาษา",
            "การแจ้งเตือนออเดอร์และครัว",
            "ศูนย์ช่วยเหลือ"
        ).forEach { label -> assertTrue("Missing settings menu: $label", source.contains(label)) }

        listOf(
            "settings.branches",
            "settings.devices",
            "settings.users",
            "settings.table_qr",
            "settings.taxes",
            "settings.language",
            "settings.order_kitchen",
            "settings.support"
        ).forEach { key -> assertTrue("Missing package/menu key: $key", source.contains(key)) }
    }

    @Test
    fun managementFormsUseCanonicalCpiposFields() {
        val source = source("src/main/java/com/cpipos/pos/next/ui/NativeSettingsManagementScreens.kt")
        listOf(
            "รหัสสาขา",
            "ชื่อสาขา",
            "รหัสเครื่อง",
            "ชื่อเครื่อง",
            "รหัสพนักงาน",
            "Permission Role",
            "อายุ QR (นาที 15-1440)",
            "ชื่อรายการภาษี",
            "ส่งออเดอร์เข้าครัวอัตโนมัติ",
            "CpIPOS Support Center"
        ).forEach { field -> assertTrue("Missing canonical mobile form field: $field", source.contains(field)) }
    }
}
