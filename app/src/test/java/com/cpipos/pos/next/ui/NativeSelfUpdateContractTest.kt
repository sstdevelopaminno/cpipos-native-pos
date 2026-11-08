package com.cpipos.pos.next.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeSelfUpdateContractTest {
    private fun source(relative: String) = File("src/main/java/com/cpipos/pos/next/$relative").readText()

    @Test fun customerMayCheckAndInstallOnlySignedStableRelease() {
        val menu = source("ui/NativeMoreScreen.kt")
        val screen = source("ui/NativeManualUpdateScreen.kt")
        val manager = source("core/update/NativeApkUpdateManager.kt")
        assertTrue(menu.contains("MoreMenu(\"updates\""))
        assertTrue(menu.contains("allowInstall = allowApkInstall"))
        assertTrue(screen.contains("NativeApkUpdateManager.checkAndStart"))
        assertTrue(screen.contains("enabled = allowInstall"))
        assertTrue(screen.contains("NativeApkUpdateManager.openSystemInstaller(context)"))
        assertTrue(manager.contains("EXPECTED_CERT"))
        assertTrue(manager.contains("verifyApk(appContext, file, offer)"))
        assertTrue(manager.contains("checkMutex.withLock"))
        assertTrue(manager.contains("ACTION_MANAGE_UNKNOWN_APP_SOURCES"))
        assertFalse(screen.contains("PackageInstaller.Session"))
    }

    @Test fun liveSaleAndUnresolvedDiningJournalBlockInstaller() {
        val main = source("ui/MobilePosLiveScreen.kt")
        val dining = source("ui/NativeDiningOrderScreen.kt")
        assertTrue(main.contains("allowApkInstall = !busy && !mdmLocked"))
        assertTrue(main.contains("takeaway.cart.isEmpty()"))
        assertTrue(main.contains("it.configuredSelections.isNotEmpty()"))
        assertTrue(main.contains("unresolvedSaleCount == 0"))
        assertTrue(dining.contains("draft.pendingKitchenOrderId == null"))
    }
}
