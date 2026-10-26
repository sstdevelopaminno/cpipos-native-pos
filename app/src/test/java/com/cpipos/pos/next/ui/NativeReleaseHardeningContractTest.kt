package com.cpipos.pos.next.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeReleaseHardeningContractTest {
    private fun source(path: String): String = File(path).readText()

    @Test
    fun subscriptionLifecycleUsesCanonicalCpiposRuntimeAndKeepsBillingReachable() {
        val client = source("src/main/java/com/cpipos/pos/next/core/webpos/WebPosClient.kt")
        val live = source("src/main/java/com/cpipos/pos/next/ui/MobilePosLiveScreen.kt")
        val more = source("src/main/java/com/cpipos/pos/next/ui/NativeMoreScreen.kt")
        val dialog = source("src/main/java/com/cpipos/pos/next/ui/NativeRuntimeLockDialogs.kt")

        assertTrue(client.contains("/api/pos/billing/runtime?reason="))
        assertTrue(client.contains("subscription_runtime_scope_mismatch"))
        assertTrue(live.contains("subscriptionRuntime?.locked == true"))
        assertTrue(live.contains("5_000L"))
        assertTrue(live.contains("openPackageBilling()"))
        assertTrue(live.contains("subscriptionRuntime.takeUnless { billingVisible || mdmLocked }"))
        assertTrue(more.contains("if (menu.key == \"billing\") true"))
        assertTrue(dialog.contains("ไปชำระแพ็กเกจ"))
    }

    @Test
    fun preEntryExpiryUsesCanonicalAuthenticatedCheckout() {
        val client = source("src/main/java/com/cpipos/pos/next/core/webpos/WebPosClient.kt")
        val screen = source("src/main/java/com/cpipos/pos/next/ui/NativePreEntrySubscriptionScreen.kt")
        val live = source("src/main/java/com/cpipos/pos/next/ui/MobilePosLiveScreen.kt")
        val flow = source("src/main/java/com/cpipos/pos/next/ui/LivePosFlowScreens.kt")

        assertTrue(client.contains("entry_access"))
        assertTrue(client.contains("/api/auth/subscription-checkout"))
        assertTrue(client.contains("checkout_token"))
        assertTrue(screen.contains("client.listEntryDevices()"))
        assertTrue(screen.contains("delay(5_000L)"))
        assertTrue(screen.contains("4 * 1024 * 1024"))
        assertTrue(live.contains("\"preentry_billing\""))
        assertTrue(live.contains("availableDevices?.entryAllowed != false"))
        assertTrue(flow.contains("entryAllowed && !busy"))
        assertFalse(screen.contains("/api/public/subscription-checkout"))
    }

    @Test
    fun mdmUsesExistingDeviceHeartbeatAndAckControlPlane() {
        val client = source("src/main/java/com/cpipos/pos/next/core/webpos/WebPosClient.kt")
        val mdm = source("src/main/java/com/cpipos/pos/next/core/mdm/NativeDeviceManagementRuntime.kt")
        val live = source("src/main/java/com/cpipos/pos/next/ui/MobilePosLiveScreen.kt")

        assertTrue(client.contains("/api/pos/device-heartbeat"))
        assertTrue(client.contains("/api/pos/device-commands/ack"))
        assertTrue(client.contains("android_native_pos"))
        assertTrue(mdm.contains("\"disable_device\""))
        assertTrue(mdm.contains("\"enable_device\""))
        assertTrue(mdm.contains("device_or_session_blocked"))
        assertTrue(mdm.contains("client.testAssignedNativePrinter(session)"))
        assertTrue(live.contains("NativeDeviceManagementRuntime.start"))
        assertTrue(live.contains("NativeDeviceManagementRuntime.stop"))
        assertTrue(live.contains("if (mdmLocked)"))
    }

    @Test
    fun nativePrintPathStaysOnCanonicalQueueWithDedupe() {
        val runtime = source("src/main/java/com/cpipos/pos/next/core/print/NativePrintRuntime.kt")
        val client = source("src/main/java/com/cpipos/pos/next/core/webpos/WebPosClient.kt")

        assertTrue(runtime.contains("SupervisorJob() + Dispatchers.IO"))
        assertTrue(runtime.contains("client.claimNativePrintJobs(agentKey)"))
        assertTrue(runtime.contains("client.ackNativePrintJob"))
        assertTrue(runtime.contains("client.failNativePrintJob"))
        assertTrue(runtime.contains("wasPrinted(jobId)"))
        assertTrue(runtime.contains("PRINTED_TTL_MS"))
        assertTrue(client.contains("/api/android-pos/print-agent/bootstrap"))
        assertTrue(client.contains("/api/print-agent/v1/jobs/claim"))
        assertTrue(client.contains("/api/backoffice/printers/test"))
        assertTrue(client.contains("printer_not_assigned_to_device"))
    }

    @Test
    fun backgroundControlPlaneAvoidsRedundantSessionPreflight() {
        val client = source("src/main/java/com/cpipos/pos/next/core/webpos/WebPosClient.kt")
        listOf("subscriptionRuntime", "sendDeviceHeartbeat", "ackDeviceCommands").forEach { method ->
            val start = client.indexOf("fun $method")
            assertTrue("Missing $method", start >= 0)
            val window = client.substring(start, kotlin.math.min(client.length, start + 1800))
            assertFalse("$method should rely on authoritative endpoint session guard", window.contains("requireSameLiveSession"))
        }
    }
}
