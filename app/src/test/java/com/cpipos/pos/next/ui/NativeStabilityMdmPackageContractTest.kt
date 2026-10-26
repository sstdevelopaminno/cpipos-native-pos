package com.cpipos.pos.next.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeStabilityMdmPackageContractTest {
    private fun src(path: String): String = File(path).readText()

    @Test
    fun packageLifecycleBlocksEntryButPreservesBillingRecovery() {
        val client = src("src/main/java/com/cpipos/pos/next/core/webpos/WebPosClient.kt")
        val live = src("src/main/java/com/cpipos/pos/next/ui/MobilePosLiveScreen.kt")
        val preEntry = src("src/main/java/com/cpipos/pos/next/ui/NativePreEntrySubscriptionScreen.kt")

        assertTrue(client.contains("entry_access"))
        assertTrue(client.contains("payment_review"))
        assertTrue(client.contains("/api/auth/subscription-checkout"))
        assertTrue(client.contains("/api/pos/billing/runtime"))
        assertTrue(live.contains("showEntryPackageLock"))
        assertTrue(live.contains("\"preentry_billing\""))
        assertTrue(live.contains("\"subscription_locked\""))
        assertTrue(live.contains("subscriptionRuntimeLocked"))
        assertTrue(live.contains("NativePrintRuntime.stop()"))
        assertTrue(live.contains("NativeQrBackgroundService.stop(ctx)"))
        assertTrue(preEntry.contains("ส่งหลักฐานการชำระ"))
        assertTrue(preEntry.contains("ตรวจสอบสิทธิ์อีกครั้ง"))

        val overviewStart = client.indexOf("suspend fun subscriptionOverview")
        val submitStart = client.indexOf("suspend fun submitSubscriptionRequest")
        assertTrue(overviewStart > 0 && submitStart > overviewStart)
        val overviewWindow = client.substring(overviewStart, submitStart)
        assertFalse(overviewWindow.contains("requireSameLiveSession"))
    }

    @Test
    fun nativeMdmUsesAuthenticatedServerAllowlistAndAcknowledgesCommands() {
        val client = src("src/main/java/com/cpipos/pos/next/core/webpos/WebPosClient.kt")
        val mdm = src("src/main/java/com/cpipos/pos/next/core/mdm/NativeMdmRuntime.kt")
        val live = src("src/main/java/com/cpipos/pos/next/ui/MobilePosLiveScreen.kt")

        assertTrue(client.contains("/api/pos/device-heartbeat"))
        assertTrue(client.contains("/api/pos/device-commands/ack"))
        assertTrue(client.contains("\"agent_surface\", \"native-android\""))
        assertTrue(mdm.contains("COMMAND_ALLOWLIST"))
        assertTrue(mdm.contains("\"disable_device\""))
        assertTrue(mdm.contains("\"enable_device\""))
        assertTrue(mdm.contains("\"test_printer\""))
        assertTrue(mdm.contains("unsupported_on_native_android"))
        assertFalse(mdm.contains("Runtime.getRuntime().exec"))
        assertFalse(mdm.contains("ProcessBuilder"))
        assertTrue(mdm.contains("\"telemetry_profile\", \"android\""))
        assertTrue(live.contains("NativeMdmRuntime.start"))
        assertTrue(live.contains("mdmCommandRevision"))
    }

    @Test
    fun printRuntimeKeepsServerQueueDedupeAndPhysicalTransports() {
        val runtime = src("src/main/java/com/cpipos/pos/next/core/print/NativePrintRuntime.kt")
        val transport = src("src/main/java/com/cpipos/pos/next/core/print/NativePrinterTransport.kt")
        val client = src("src/main/java/com/cpipos/pos/next/core/webpos/WebPosClient.kt")

        assertTrue(runtime.contains("printed_job_ids"))
        assertTrue(runtime.contains("android-dedupe:"))
        assertTrue(runtime.contains("claimPrintJobs"))
        assertTrue(runtime.contains("ackPrintJobSuccess"))
        assertTrue(runtime.contains("ackPrintJobFailure"))
        assertTrue(runtime.contains("idleBackoffMs"))
        assertTrue(client.contains("/api/pos/print-agent/jobs/claim"))
        assertTrue(client.contains("/api/pos/print-agent/jobs/"))
        assertTrue(transport.contains("Socket("))
        assertTrue(transport.contains("UsbManager"))
        assertTrue(transport.contains("BluetoothAdapter"))
        assertTrue(transport.contains("ESC/POS"))
        assertTrue(transport.contains("autoCut"))
    }

    @Test
    fun lockedRuntimeStopsSaleServicesButKeepsMdmAlive() {
        val live = src("src/main/java/com/cpipos/pos/next/ui/MobilePosLiveScreen.kt")
        val mdmStart = live.indexOf("NativeMdmRuntime.start")
        val lockBranch = live.indexOf("if (subscriptionRuntimeLocked)")
        val printStop = live.indexOf("NativePrintRuntime.stop()", lockBranch)
        val qrStop = live.indexOf("NativeQrBackgroundService.stop(ctx)", lockBranch)

        assertTrue(mdmStart > 0)
        assertTrue(lockBranch > mdmStart)
        assertTrue(printStop > lockBranch)
        assertTrue(qrStop > lockBranch)
        assertTrue(live.contains("client.subscriptionRuntime(active, reason = \"native\")"))
        assertTrue(live.contains("kotlinx.coroutines.delay(5_000L)"))
    }
}
