package com.cpipos.pos.next.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeReleaseHardeningContractTest {
    private fun source(path: String): String = File(path).readText()

    private fun functionBlock(source: String, name: String): String {
        val start = source.indexOf("fun $name")
        require(start >= 0) { "Missing $name" }
        val next = source.indexOf("\n    suspend fun ", start + name.length + 4)
            .takeIf { it >= 0 }
            ?: source.indexOf("\n    fun ", start + name.length + 4).takeIf { it >= 0 }
            ?: source.length
        return source.substring(start, next)
    }

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
        assertTrue(live.contains("subscriptionOperational"))
        assertTrue(live.contains("mdmLocked || !subscriptionOperational"))
        assertTrue(live.contains("NativePrintRuntime.stop()"))
        assertTrue(live.contains("NativeQrBackgroundService.stop(ctx)"))
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
        assertTrue(mdm.contains("_lastHealth.value = \"blocked\""))
        assertTrue(mdm.contains("delay(if (blocked) 15_000L else backoff())"))
        assertTrue(mdm.contains("client.testAssignedNativePrinter(session)"))
        assertTrue(live.contains("NativeDeviceManagementRuntime.start"))
        assertTrue(live.contains("NativeDeviceManagementRuntime.stop"))
        assertTrue(live.contains("if (mdmLocked)"))
    }

    @Test
    fun mdmITDisableCannotBeUndoneByRoutineHeartbeatAndSurvivesRestart() {
        val mdm = source("src/main/java/com/cpipos/pos/next/core/mdm/NativeDeviceManagementRuntime.kt")
        val store = source("src/main/java/com/cpipos/pos/next/core/mdm/NativeDeviceLockStore.kt")
        assertTrue(mdm.contains("_deviceLocked.value = it.restoreDeviceLock()"))
        assertTrue(mdm.contains("updateDeviceLock(true)"))
        assertTrue(mdm.contains("updateDeviceLock(false)"))
        assertTrue(mdm.contains("lockStore.setLocked(locked)"))
        assertFalse(mdm.contains("_lastHealth.value = result.status\n                    _deviceLocked.value = false"))
        assertTrue(store.contains("tenantId + \":\" + branchId + \":\" + deviceCode"))
        assertTrue(store.contains("prefs.getBoolean(key, false)"))
        assertTrue(store.contains("prefs.edit().putBoolean(key, value).commit()"))
    }

    @Test
    fun androidDeveloperGuardUsesITTenantPolicyAndBlocksOperationalWorkers() {
        val detector = source("src/main/java/com/cpipos/pos/next/core/security/NativeAndroidDeveloperGuard.kt")
        val client = source("src/main/java/com/cpipos/pos/next/core/webpos/WebPosClient.kt")
        val live = source("src/main/java/com/cpipos/pos/next/ui/MobilePosLiveScreen.kt")
        val dialog = source("src/main/java/com/cpipos/pos/next/ui/NativeDeveloperGuardDialog.kt")
        assertTrue(detector.contains("Settings.Global.DEVELOPMENT_SETTINGS_ENABLED"))
        assertTrue(detector.contains("Settings.Global.ADB_ENABLED"))
        assertTrue(detector.contains("adb_wifi_enabled"))
        assertTrue(detector.contains("tenantId"))
        assertTrue(client.contains("/api/pos/android-security-policy"))
        assertTrue(client.contains("android_security_policy_scope_mismatch"))
        assertTrue(live.contains("developerGuardBlocked"))
        assertTrue(live.contains("!developerGuardBlocked && !developerGuardPending"))
        assertTrue(live.contains("NativePrintRuntime.stop()"))
        assertTrue(live.contains("NativeQrBackgroundService.stop(ctx)"))
        assertTrue(live.contains("NativeDeveloperGuardDialog(signals = developerSignals)"))
        assertTrue(dialog.contains("dismissOnBackPress = false"))
        assertTrue(live.contains("NativeDeveloperPolicyLoadingDialog()"))
        assertTrue(live.contains("verifiedForAlerts != null && !mdmLocked && subscriptionOperational"))
        val printWorker = source("src/main/java/com/cpipos/pos/next/core/print/NativePrintRuntime.kt")
        val backgroundQr = source("src/main/java/com/cpipos/pos/next/core/push/NativeQrBackgroundService.kt")
        assertTrue(printWorker.contains("NativeAndroidDeveloperGuard.inspect(context).detected"))
        assertTrue(backgroundQr.contains("NativeAndroidDeveloperGuard.inspect(this).detected"))
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
        assertTrue(runtime.contains(".commit()"))
        assertTrue(runtime.contains("print_dedupe_ledger_persist_failed"))
        assertTrue(client.contains("/api/android-pos/print-agent/bootstrap"))
        assertTrue(client.contains("/api/print-agent/v1/jobs/claim"))
        assertTrue(client.contains("/api/backoffice/printers/test"))
        assertTrue(client.contains("printer_not_assigned_to_device"))
    }

    @Test
    fun physicalReceiptSuccessIsNeverMisreportedAsRetryableFailureAfterAckTimeout() {
        val runtime = source("src/main/java/com/cpipos/pos/next/core/print/NativePrintRuntime.kt")
        assertTrue(runtime.contains("var physicalOutputCompleted = false"))
        assertTrue(runtime.contains("physicalOutputCompleted = true"))
        assertTrue(runtime.contains("if (!physicalOutputCompleted) runCatching"))
        assertTrue(runtime.contains("print_ack_pending:"))
        assertTrue(runtime.contains("recentPrinted[jobId] = now"))
        assertTrue(runtime.contains("if (recentPrinted.containsKey(jobId)) return true"))
    }

    @Test
    fun mdmTelemetryIdentifiesNativeAndroidWithoutPretendingToBeWindowsBridge() {
        val client = source("src/main/java/com/cpipos/pos/next/core/webpos/WebPosClient.kt")
        val block = functionBlock(client, "sendDeviceHeartbeat")
        assertTrue(block.contains(".put(\"telemetry_profile\", \"android\")"))
        assertTrue(block.contains(".put(\"source\", \"pos_native_android\")"))
        assertTrue(block.contains(".put(\"cpi_windows_runtime_running\", false)"))
        assertTrue(block.contains(".put(\"local_bridge_online\", false)"))
    }

    @Test
    fun bluetoothPrintingHasBoundedConnectAndSettingsClearBottomNav() {
        val transport = source("src/main/java/com/cpipos/pos/next/core/print/NativePrinterTransport.kt")
        val printer = source("src/main/java/com/cpipos/pos/next/ui/NativePrinterSettingsScreen.kt")
        val payment = source("src/main/java/com/cpipos/pos/next/ui/NativePaymentSettingsScreen.kt")
        val notifications = source("src/main/java/com/cpipos/pos/next/ui/NativeNotificationSettingsScreen.kt")
        val store = source("src/main/java/com/cpipos/pos/next/ui/NativeStoreSettingsScreen.kt")

        assertTrue(transport.contains("BLUETOOTH_CONNECT_TIMEOUT_SECONDS = 7L"))
        assertTrue(transport.contains("future.get(BLUETOOTH_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)"))
        assertTrue(transport.contains("\"bluetooth_connect_timeout\""))
        listOf(printer, payment, notifications, store).forEach {
            assertTrue(it.contains("bottom = 156.dp"))
            assertFalse(it.contains("bottom = 112.dp"))
        }
    }

    @Test
    fun releaseUsesCompatibleAndroidxFragmentForActivityResultApi() {
        val gradle = source("build.gradle.kts")
        assertTrue(gradle.contains("androidx.fragment:fragment-ktx:1.9.1"))
    }

    @Test
    fun backgroundControlPlaneAvoidsRedundantSessionPreflight() {
        val client = source("src/main/java/com/cpipos/pos/next/core/webpos/WebPosClient.kt")
        listOf("subscriptionRuntime", "sendDeviceHeartbeat", "ackDeviceCommands").forEach { method ->
            val block = functionBlock(client, method)
            assertFalse("$method should rely on authoritative endpoint session guard", block.contains("requireSameLiveSession"))
        }
    }
}
