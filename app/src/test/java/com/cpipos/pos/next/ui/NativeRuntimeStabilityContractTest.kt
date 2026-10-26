package com.cpipos.pos.next.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeRuntimeStabilityContractTest {
    private fun source(path: String): String = File(path).readText()

    @Test
    fun nativeSessionBindsPhysicalInstallToCanonicalPosDevice() {
        val client = source("src/main/java/com/cpipos/pos/next/core/webpos/WebPosClient.kt")
        val live = source("src/main/java/com/cpipos/pos/next/ui/MobilePosLiveScreen.kt")
        assertTrue(client.contains("android_install_id"))
        assertTrue(client.contains("X-CpIPOS-Android-POS"))
        assertTrue(client.contains("X-CpIPOS-Install-Id"))
        assertTrue(live.contains("androidInstallId = NativeFcmRegistration.installationId(ctx)"))
    }

    @Test
    fun nativeMdmUsesExistingCpiposHeartbeatAndNeverEmbedsItCredentials() {
        val mdm = source("src/main/java/com/cpipos/pos/next/core/mdm/NativeMdmRuntime.kt")
        val client = source("src/main/java/com/cpipos/pos/next/core/webpos/WebPosClient.kt")
        assertTrue(client.contains("/api/android-pos/mdm/heartbeat"))
        assertTrue(mdm.contains("android-native-pos-v2"))
        assertTrue(mdm.contains("NativePrintRuntime.diagnostics()"))
        assertTrue(mdm.contains("reload_webview"))
        assertTrue(mdm.contains("refresh_runtime"))
        assertFalse(mdm.contains("service_role"))
        assertFalse(mdm.contains("SUPABASE_SERVICE"))
        assertFalse(mdm.contains("it_admin"))
    }

    @Test
    fun subscriptionExpiryLocksNativeButKeepsBillingReachable() {
        val client = source("src/main/java/com/cpipos/pos/next/core/webpos/WebPosClient.kt")
        val live = source("src/main/java/com/cpipos/pos/next/ui/MobilePosLiveScreen.kt")
        val dialog = source("src/main/java/com/cpipos/pos/next/ui/NativeSubscriptionLifecycleDialog.kt")
        assertTrue(client.contains("/api/pos/billing/runtime"))
        assertTrue(client.contains("fun lockedAt"))
        assertTrue(live.contains("if (locked) 15_000L else 60_000L"))
        assertTrue(live.contains("step != \"billing\""))
        assertTrue(live.contains("step = \"billing\""))
        assertTrue(dialog.contains("แพ็กเกจครบกำหนดชำระแล้ว"))
        assertTrue(dialog.contains("ปลดล็อกอัตโนมัติ"))
    }

    @Test
    fun printingKeepsServerQueueAuthorityAndIdempotentAck() {
        val runtime = source("src/main/java/com/cpipos/pos/next/core/print/NativePrintRuntime.kt")
        val transport = source("src/main/java/com/cpipos/pos/next/core/print/NativePrinterTransport.kt")
        val client = source("src/main/java/com/cpipos/pos/next/core/webpos/WebPosClient.kt")
        assertTrue(runtime.contains("claimNativePrintJobs"))
        assertTrue(runtime.contains("wasPrinted(jobId)"))
        assertTrue(runtime.contains("rememberPrinted(jobId)"))
        assertTrue(runtime.contains("ackNativePrintJob"))
        assertTrue(runtime.contains("failNativePrintJob"))
        assertTrue(client.contains("/api/print-agent/v1/jobs/claim"))
        assertTrue(client.contains("/api/print-agent/v1/jobs/$jobId/ack"))
        assertTrue(client.contains("/api/print-agent/v1/jobs/$jobId/fail"))
        assertTrue(transport.contains("NETWORK_ESC_POS"))
        assertTrue(transport.contains("LOCAL_BRIDGE"))
        assertTrue(transport.contains("BLUETOOTH_BRIDGE"))
        assertTrue(transport.contains("windows-874"))
    }

    @Test
    fun lockedSubscriptionSuppressesOperationalQrNavigation() {
        val live = source("src/main/java/com/cpipos/pos/next/ui/MobilePosLiveScreen.kt")
        assertTrue(live.contains("subscriptionRuntime?.lockedAt(subscriptionClock) != true"))
        assertTrue(live.contains("NativeQrGlobalAlerts"))
    }
}
