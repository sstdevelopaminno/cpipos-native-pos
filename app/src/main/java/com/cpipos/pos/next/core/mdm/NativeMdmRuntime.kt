package com.cpipos.pos.next.core.mdm

import android.content.Context
import android.os.Build
import androidx.compose.runtime.mutableIntStateOf
import com.cpipos.pos.next.BuildConfig
import com.cpipos.pos.next.core.print.NativePrintRuntime
import com.cpipos.pos.next.core.push.NativeFcmRegistration
import com.cpipos.pos.next.core.webpos.WebPosApiException
import com.cpipos.pos.next.core.webpos.WebPosClient
import com.cpipos.pos.next.core.webpos.WebPosSession
import java.util.concurrent.atomic.AtomicBoolean
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
 * Native MDM health/command bridge.
 *
 * CpIPOS server remains the sole command authority. Android executes only this
 * fixed allowlist and acknowledges every delivered command. No arbitrary shell,
 * URL, SQL, package install or privileged IT credential is accepted here.
 */
object NativeMdmRuntime {
    val commandRevision = mutableIntStateOf(0)

    private val lock = Any()
    private var worker: Worker? = null

    fun start(context: Context, client: WebPosClient, session: WebPosSession) {
        synchronized(lock) {
            val active = worker
            if (active?.sameSession(session) == true && active.isRunning()) return
            active?.stop()
            worker = Worker(context.applicationContext, client, session).also { it.start() }
        }
    }

    fun stop() {
        synchronized(lock) {
            worker?.stop()
            worker = null
        }
    }

    fun diagnostics(): JSONObject = synchronized(lock) {
        worker?.diagnostics() ?: JSONObject().put("running", false)
    }

    private class Worker(
        private val context: Context,
        private val client: WebPosClient,
        private val session: WebPosSession
    ) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val running = AtomicBoolean(false)
        private var job: Job? = null
        private var lastSuccessAt: Long? = null
        private var lastError: String? = null
        private var heartbeatCount = 0L
        private val installationId = NativeFcmRegistration.installationId(context)

        fun sameSession(other: WebPosSession): Boolean =
            session.id == other.id &&
                session.tenantId == other.tenantId &&
                session.branchId == other.branchId &&
                session.deviceCode == other.deviceCode

        fun isRunning(): Boolean = running.get() && job?.isActive == true

        fun start() {
            if (!running.compareAndSet(false, true)) return
            job = scope.launch {
                while (isActive) {
                    try {
                        heartbeat()
                        lastError = null
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        lastError = when (failure) {
                            is WebPosApiException -> failure.code + ":" + failure.status
                            else -> failure.javaClass.simpleName
                        }
                    }
                    delay(60_000L)
                }
            }
        }

        fun stop() {
            running.set(false)
            job?.cancel()
            scope.cancel()
        }

        fun diagnostics(): JSONObject = JSONObject()
            .put("running", isRunning())
            .put("heartbeat_count", heartbeatCount)
            .put("last_success_at", lastSuccessAt)
            .put("last_error", lastError)
            .put("installation_id", installationId)
            .put("app_version", BuildConfig.VERSION_NAME)

        private suspend fun heartbeat() {
            val payload = JSONObject()
                .put("captured_at", java.time.Instant.now().toString())
                .put("identity", JSONObject()
                    .put("device_code", session.deviceCode)
                    .put("machine_id", installationId)
                    .put("runtime_version", "native-android-v2")
                    .put("app_version", BuildConfig.VERSION_NAME))
                .put("connectivity", JSONObject()
                    .put("internet_online", true)
                    .put("server_reachable", true)
                    .put("last_seen_at", java.time.Instant.now().toString()))
                .put("system", JSONObject()
                    .put("manufacturer", Build.MANUFACTURER)
                    .put("brand", Build.BRAND)
                    .put("model", Build.MODEL)
                    .put("sdk_int", Build.VERSION.SDK_INT)
                    .put("android_release", Build.VERSION.RELEASE))
                .put("runtime", JSONObject()
                    .put("cpi_windows_runtime_running", true)
                    .put("local_bridge_online", true)
                    .put("bridge_version", "native-android-v2")
                    .put("print_queue_busy", false)
                    .put("last_error", lastError))
                .put("peripherals", JSONObject())
                .put("metadata", JSONObject()
                    .put("runtime", "cpipos-native-android")
                    .put("native_generation", "2.0")
                    .put("print_runtime", JSONObject(NativePrintRuntime.diagnostics()))
                    .put("mdm_command_allowlist", JSONArray(COMMAND_ALLOWLIST.toList())))

            val data = client.nativeDeviceHeartbeat(payload)
            heartbeatCount += 1
            lastSuccessAt = System.currentTimeMillis()
            val commands = data.optJSONArray("pending_actions") ?: JSONArray()
            val results = JSONArray()
            for (index in 0 until commands.length()) {
                val command = commands.optJSONObject(index) ?: continue
                execute(command)?.let(results::put)
            }
            if (results.length() > 0) client.ackNativeDeviceCommands(results)
        }

        private fun execute(command: JSONObject): JSONObject? {
            val id = command.optString("id").trim()
            val type = command.optString("command_type").trim()
            if (!UUID_PATTERN.matches(id) || type !in COMMAND_ALLOWLIST) return null

            var applied = true
            var phase = "executed"
            var errorCode: String? = null
            var errorMessage: String? = null

            when (type) {
                "request_diagnostics_bundle", "request_diagnostics", "test_network" -> Unit
                "reload_ui", "refresh_config", "disable_device", "enable_device" -> {
                    commandRevision.intValue += 1
                }
                "test_printer" -> {
                    NativePrintRuntime.wake()
                    phase = "accepted"
                }
                "check_update" -> {
                    // The installed version is reported in every heartbeat. A future
                    // signed managed updater can consume this same command contract.
                    phase = "accepted"
                }
                "restart_app" -> {
                    applied = false
                    errorCode = "native_foreground_restart_not_supported"
                    errorMessage = "Native POS will not force-kill a foreground sale session."
                }
                "clear_print_queue", "restart_local_bridge", "restart_print_service" -> {
                    applied = false
                    errorCode = "unsupported_on_native_android"
                    errorMessage = "This command targets a bridge/service that Native POS does not use."
                }
            }

            return JSONObject()
                .put("id", id)
                .put("command_type", type)
                .put("applied", applied)
                .put("phase", phase)
                .put("error_code", errorCode ?: JSONObject.NULL)
                .put("error_message", errorMessage ?: JSONObject.NULL)
        }
    }

    private val COMMAND_ALLOWLIST = setOf(
        "request_diagnostics_bundle",
        "request_diagnostics",
        "reload_ui",
        "restart_app",
        "test_network",
        "test_printer",
        "clear_print_queue",
        "restart_local_bridge",
        "restart_print_service",
        "refresh_config",
        "check_update",
        "disable_device",
        "enable_device"
    )
    private val UUID_PATTERN = Regex(
        "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$"
    )
}
