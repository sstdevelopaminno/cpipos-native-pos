package com.cpipos.pos.next.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeSettingsPermissionContractTest {
    private fun source(path: String): String = File(path).readText()

    @Test
    fun allManagementSettingsWorkWithoutOpenShift() {
        val client = source("src/main/java/com/cpipos/pos/next/core/webpos/WebPosClient.kt")
        listOf(
            "settingsBranchesSnapshot",
            "saveSettingsBranch",
            "settingsDevices",
            "saveSettingsDevice",
            "posUsers",
            "createPosUser",
            "taxSettings",
            "orderKitchenPolicy",
            "tableQrPolicy",
            "supportConversations"
        ).forEach { method ->
            val start = client.indexOf("fun $method")
            assertTrue("Missing client method: $method", start >= 0)
            val window = client.substring(start, kotlin.math.min(client.length, start + 900))
            assertTrue(
                "$method must not require an open shift",
                window.contains("requireShift = false")
            )
            assertTrue(
                "$method must not require canSell",
                window.contains("requireSell = false")
            )
        }
    }

    @Test
    fun ownerOnlyBranchAndDeviceActionsUseServerMetadata() {
        val client = source("src/main/java/com/cpipos/pos/next/core/webpos/WebPosClient.kt")
        val screens = source("src/main/java/com/cpipos/pos/next/ui/NativeSettingsManagementScreens.kt")

        assertTrue(client.contains("WebSettingsBranchesSnapshot"))
        assertTrue(client.contains("canManage = metadata?.optBoolean(\"can_manage\", false)"))
        assertTrue(screens.contains("if (canManage)"))
        assertTrue(screens.contains("สงวนสำหรับ Owner"))
    }

    @Test
    fun kitchenPrintingHasIndependentPackageGate() {
        val hub = source("src/main/java/com/cpipos/pos/next/ui/NativeSettingsHubScreen.kt")
        val screens = source("src/main/java/com/cpipos/pos/next/ui/NativeSettingsManagementScreens.kt")
        val client = source("src/main/java/com/cpipos/pos/next/core/webpos/WebPosClient.kt")

        assertTrue(hub.contains("featureEnabled(\"qr_table_ordering\")"))
        assertTrue(hub.contains("featureEnabled(\"kitchen_printing\")"))
        assertTrue(screens.contains("canQrOrdering"))
        assertTrue(screens.contains("canKitchenPrinting"))
        assertTrue(client.contains("canKitchenPrinting: Boolean"))
    }

    @Test
    fun supportCenterUsesNativePosSessionApi() {
        val client = source("src/main/java/com/cpipos/pos/next/core/webpos/WebPosClient.kt")
        val screens = source("src/main/java/com/cpipos/pos/next/ui/NativeSettingsManagementScreens.kt")

        assertTrue(client.contains("/api/pos/support-chat/conversations"))
        assertTrue(screens.contains("เริ่มแชทกับ IT Support"))
        assertTrue(screens.contains("ส่งข้อความ"))
        assertTrue(screens.contains("จบการสนทนา"))
        assertFalse(
            "Native help center must not depend on the browser-only CpIPOS support page",
            screens.contains("/preview/pos/payments/support")
        )
    }
}
