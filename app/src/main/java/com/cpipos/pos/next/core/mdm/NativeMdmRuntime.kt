package com.cpipos.pos.next.core.mdm

import android.app.ActivityManager
import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import android.os.SystemClock
import com.cpipos.pos.next.BuildConfig
import com.cpipos.pos.next.core.print.NativePrintRuntime
import com.cpipos.pos.next.core.webpos.WebNativeMdmHeartbeat
import com.cpipos.pos.next.core.webpos.WebPosClient
import com.cpipos.pos.next.core.webpos.WebPosSession
import com.cpipos.pos.next.core.push.NativeFcmRegistration
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * App-level MDM heartbeat for the Native POS runtime.
 *
 * Authority remains CpIPOS/CpiPOS-001. The app never receives IT credentials.
 * Device-wide destructive actions are intentionally not implemented here unless
 * Android Device Owner enrollment is added in a separate, explicitly approved flow.
 */
object NativeMdmRuntime {
    private val lock = Any()
    private var worker: Worker? = null

    fun start(
        context: Context,
        client: WebPosClient,
        session: WebPosSession,
        onRefreshRequested: () -> Unit
    ) {
        synchronized(lock) {
            val current = worker
            if (current?.sameSession(session) == true && current.isRunning()) return
            current?.stop()
            worker = Worker(
                context.applicationContext,
                client,
                session,
                onRefreshRequested
            ).also { it.start() }
        }
    }

    fun stop() {
        synchronized(lock) {
            worker?.stop()
            worker = null
        }
    }

    fun diagnostics(): String = synchronized(lock) {
        worker?.diagnostics()?.toString() ?: JSONObject()
            .put("running", false)
            .toString()
    }

    private class Worker(
        private val context: Context,
        private val client: WebPosClient,
        private val session: WebPosSession,
        private val onRefreshRequested: () -> Unit
    ) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val main = Handler(Looper.getMainLooper())
        private var job: Job? = null
        private var lastHeartbeatAt: Long? = null
        private var lastError: String? = null
        private var pairedDeviceCode: String? = null
        private var lastCommand = JSONObject()

        fun sameSession(other: WebPosSession): Boolean =
            other.id == session.id &&
                other.tenantId == session.tenantId &&
                other.branchId == session.branchId &&
                other.deviceCode == session.deviceCode

        fun isRunning(): Boolean = job?.isActive == true

        fun start() {
            if (job?.isActive == true) return
            job = scope.launch {
                var immediate = true
                while (isActive) {
                    val commandsApplied = try {
                        heartbeat(if (immediate) "started" else "periodic")
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        lastError = failure.message ?: failure.javaClass.simpleName
                        false
                    }
                    immediate = false
                    delay(if (commandsApplied) 1_000L else HEARTBEAT_MS)
                }
            }
        }

        fun stop() {
            job?.cancel()
            scope.cancel()
        }

        fun diagnostics(): JSONObject = JSONObject()
            .put("running", isRunning())
            .put("paired_device_code", pairedDeviceCode)
            .put("last_heartbeat_at", lastHeartbeatAt)
            .put("last_error", lastError)
            .put("last_command", lastCommand)

        private suspend fun heartbeat(reason: String): Boolean {
            val installationId = NativeFcmRegistration.installationId(context)
            val response = client.nativeMdmHeartbeat(
                session = session,
                installationId = installationId,
                payload = snapshot(reason, installationId)
            )
            pairedDeviceCode = response.pairedDeviceCode
            lastHeartbeatAt = System.currentTimeMillis()
            lastError = null
            return applyCommands(response)
        }

        private fun applyCommands(response: WebNativeMdmHeartbeat): Boolean {
            var applied = false
            for (index in 0 until response.commands.length()) {
                val command = response.commands.optJSONObject(index) ?: continue
                val action = command.optString("action").trim().lowercase()
                val commandId = command.optString("id").trim()
                if (action.isBlank()) continue

                val result = when (action) {
                    "ping", "collect_diagnostics" -> JSONObject().put("ok", true)
                    "reload_webview", "navigate_home" -> {
                        main.post(onRefreshRequested)
                        JSONObject()
                            .put("ok", true)
                            .put("native_action", "refresh_runtime")
                    }
                    "test_printer_connection" -> {
                        NativePrintRuntime.wake()
                        val print = runCatching {
                            JSONObject(NativePrintRuntime.diagnostics())
                        }.getOrElse { JSONObject() }
                        val running = print.optBoolean("running", false)
                        val error = print.optString("last_error").trim()
                        JSONObject()
                            .put("ok", running && error.isBlank())
                            .put("retryable", true)
                            .put("print_runtime", print)
                    }
                    "test_printer_verification" -> {
                        // Never claim target-specific physical verification from
                        // generic runtime telemetry. The normal print-agent job path
                        // remains the source of truth for actual printed output.
                        NativePrintRuntime.wake()
                        JSONObject()
                            .put("ok", false)
                            .put("retryable", false)
                            .put("code", "native_target_verification_requires_print_job")
                    }
                    else -> JSONObject()
                        .put("ok", false)
                        .put("retryable", false)
                        .put("code", "native_command_not_supported")
                }

                lastCommand = JSONObject()
                    .put("command_id", commandId)
                    .put("action", action)
                    .put("source", "native_mdm_heartbeat")
                    .put("at_ms", System.currentTimeMillis())
                    .put("result", result)
                applied = true
            }
            return applied
        }

        private fun snapshot(reason: String, installationId: String): JSONObject {
            val activity = context.getSystemService(ActivityManager::class.java)
            val battery = context.getSystemService(BatteryManager::class.java)
            val memoryInfo = ActivityManager.MemoryInfo().also { info ->
                runCatching { activity?.getMemoryInfo(info) }
            }
            val storage = runCatching { StatFs(File(context.filesDir.absolutePath).absolutePath) }.getOrNull()
            val print = runCatching { JSONObject(NativePrintRuntime.diagnostics()) }
                .getOrElse { JSONObject().put("running", false) }

            return JSONObject()
                .put("reason", reason)
                .put("install_id", installationId)
                .put("timestamp_ms", System.currentTimeMillis())
                .put("safe_command_allowlist", JSONArray(listOf(
                    "ping",
                    "collect_diagnostics",
                    "reload_webview",
                    "navigate_home",
                    "test_printer_connection",
                    "test_printer_verification"
                )))
                .put("runtime_capabilities", JSONObject()
                    .put("schema_version", 4)
                    .put("native_ui", true)
                    .put("printer", JSONObject()
                        .put("target_probe", false)
                        .put("one_time_verification_print", false)
                        .put("explicit_assignment_first", true)
                        .put("auto_setup", false)
                        .put("automatic_reassignment", false)
                        .put("assignment_protection", "preserve_existing_or_require_confirmation")))
                .put("app", JSONObject()
                    .put("package", BuildConfig.APPLICATION_ID)
                    .put("version_name", BuildConfig.VERSION_NAME)
                    .put("version_code", BuildConfig.VERSION_CODE)
                    .put("runtime", "android-native-pos-v2")
                    .put("tenant_id", session.tenantId)
                    .put("branch_id", session.branchId)
                    .put("device_code", session.deviceCode))
                .put("device", JSONObject()
                    .put("manufacturer", Build.MANUFACTURER)
                    .put("brand", Build.BRAND)
                    .put("model", Build.MODEL)
                    .put("device", Build.DEVICE)
                    .put("product", Build.PRODUCT)
                    .put("sdk_int", Build.VERSION.SDK_INT)
                    .put("android_release", Build.VERSION.RELEASE)
                    .put("uptime_ms", SystemClock.uptimeMillis()))
                .put("health", JSONObject()
                    .put("battery_percent", battery?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY))
                    .put("available_memory_mb", memoryInfo.availMem / 1024L / 1024L)
                    .put("low_memory", memoryInfo.lowMemory)
                    .put("available_storage_mb", storage?.availableBytes?.div(1024L)?.div(1024L)))
                .put("printer", JSONObject()
                    .put("source", "native_print_agent")
                    .put("runtime", print)
                    .put("last_error", print.optString("last_error")))
                .put("last_command", lastCommand)
        }
    }

    private const val HEARTBEAT_MS = 60_000L
}
