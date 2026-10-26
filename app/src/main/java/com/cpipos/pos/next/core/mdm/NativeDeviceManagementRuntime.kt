package com.cpipos.pos.next.core.mdm

import android.content.Context
import android.util.Log
import com.cpipos.pos.next.core.print.NativePrintRuntime
import com.cpipos.pos.next.core.push.NativeFcmRegistration
import com.cpipos.pos.next.core.webpos.WebDeviceAction
import com.cpipos.pos.next.core.webpos.WebDeviceCommandAck
import com.cpipos.pos.next.core.webpos.WebPosApiException
import com.cpipos.pos.next.core.webpos.WebPosClient
import com.cpipos.pos.next.core.webpos.WebPosSession
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Authenticated Native POS MDM bridge.
 *
 * CpIPOS remains the authority: the app only reports health, receives commands
 * already scoped to the active POS device, applies the safe Native subset, and
 * acknowledges results through the existing POS control-plane API.
 */
object NativeDeviceManagementRuntime {
    private val guard = Any()
    private var worker: Worker? = null

    private val _deviceLocked = MutableStateFlow(false)
    val deviceLocked: StateFlow<Boolean> = _deviceLocked

    private val _refreshRevision = MutableStateFlow(0)
    val refreshRevision: StateFlow<Int> = _refreshRevision

    private val _lastHealth = MutableStateFlow("offline")
    val lastHealth: StateFlow<String> = _lastHealth

    fun start(context: Context, client: WebPosClient, session: WebPosSession) {
        synchronized(guard) {
            val current = worker
            if (current?.sameSession(session) == true && current.running()) return
            current?.stop()
            worker = Worker(context.applicationContext, client, session).also { it.start() }
        }
    }

    fun stop() {
        synchronized(guard) {
            worker?.stop()
            worker = null
            _deviceLocked.value = false
            _lastHealth.value = "offline"
        }
    }

    fun requestRefresh() {
        _refreshRevision.value = _refreshRevision.value + 1
    }

    private class Worker(
        private val context: Context,
        private val client: WebPosClient,
        private val session: WebPosSession
    ) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private var job: Job? = null
        private val failures = AtomicInteger(0)

        fun sameSession(other: WebPosSession): Boolean =
            other.id == session.id &&
                other.tenantId == session.tenantId &&
                other.branchId == session.branchId &&
                other.deviceCode == session.deviceCode

        fun running(): Boolean = job?.isActive == true

        fun start() {
            if (job?.isActive == true) return
            job = scope.launch { loop() }
        }

        fun stop() {
            job?.cancel()
            scope.cancel()
        }

        private suspend fun loop() {
            while (scope.isActive) {
                try {
                    val installId = NativeFcmRegistration.installationId(context)
                    val printer = runCatching { JSONObject(NativePrintRuntime.diagnostics()) }
                        .getOrElse { JSONObject().put("running", false).put("last_error", "diagnostics_unavailable") }
                    val result = client.sendDeviceHeartbeat(session, installId, printer)
                    _lastHealth.value = result.status
                    failures.set(0)
                    if (result.pendingActions.isNotEmpty()) {
                        handle(result.pendingActions)
                    }
                    delay(30_000L)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: WebPosApiException) {
                    if (failure.status == 401 || failure.status == 403 || failure.status == 409) {
                        Log.w("CpIPOSMDM", "MDM heartbeat rejected: " + failure.code)
                    } else {
                        Log.w("CpIPOSMDM", "MDM heartbeat failed: " + failure.code)
                    }
                    _lastHealth.value = "degraded"
                    delay(backoff())
                } catch (failure: Exception) {
                    Log.w("CpIPOSMDM", "MDM heartbeat failed: " + failure.javaClass.simpleName)
                    _lastHealth.value = "degraded"
                    delay(backoff())
                }
            }
        }

        private fun backoff(): Long {
            val count = failures.incrementAndGet().coerceAtMost(4)
            return when (count) {
                1 -> 15_000L
                2 -> 30_000L
                3 -> 60_000L
                else -> 120_000L
            }
        }

        private suspend fun handle(actions: List<WebDeviceAction>) {
            val results = actions.take(10).map { action ->
                when (action.commandType) {
                    "disable_device" -> {
                        _deviceLocked.value = true
                        WebDeviceCommandAck(action.id, action.commandType, true)
                    }
                    "enable_device" -> {
                        _deviceLocked.value = false
                        WebDeviceCommandAck(action.id, action.commandType, true)
                    }
                    "reload_ui", "refresh_config" -> {
                        _refreshRevision.value = _refreshRevision.value + 1
                        WebDeviceCommandAck(action.id, action.commandType, true)
                    }
                    "request_diagnostics", "request_diagnostics_bundle" ->
                        WebDeviceCommandAck(action.id, action.commandType, true)
                    "test_network" ->
                        WebDeviceCommandAck(action.id, action.commandType, true)
                    "test_printer" -> {
                        NativePrintRuntime.wake()
                        val running = runCatching {
                            JSONObject(NativePrintRuntime.diagnostics()).optBoolean("running", false)
                        }.getOrDefault(false)
                        WebDeviceCommandAck(
                            action.id,
                            action.commandType,
                            applied = running,
                            phase = if (running) "accepted" else "executed",
                            errorCode = if (running) null else "native_print_runtime_inactive",
                            errorMessage = if (running) null else "Native print runtime is not active for this POS session."
                        )
                    }
                    "check_update" ->
                        WebDeviceCommandAck(
                            action.id, action.commandType, false,
                            errorCode = "native_update_check_not_interactive",
                            errorMessage = "Signed release updates are managed by the Native Mobile release channel."
                        )
                    "restart_app" ->
                        WebDeviceCommandAck(
                            action.id, action.commandType, false,
                            errorCode = "restart_requires_operator",
                            errorMessage = "Android Native POS does not force-stop/restart itself remotely."
                        )
                    "clear_print_queue", "restart_local_bridge", "restart_print_service" ->
                        WebDeviceCommandAck(
                            action.id, action.commandType, false,
                            errorCode = "unsupported_native_command",
                            errorMessage = "This legacy bridge command is not applicable to Android Native POS."
                        )
                    else ->
                        WebDeviceCommandAck(
                            action.id, action.commandType, false,
                            errorCode = "unknown_native_command",
                            errorMessage = "Unsupported Android Native POS MDM command."
                        )
                }
            }
            if (results.isNotEmpty()) {
                runCatching { client.ackDeviceCommands(session, results) }
                    .onFailure { failure ->
                        Log.w("CpIPOSMDM", "MDM command ACK failed: " + failure.javaClass.simpleName)
                    }
            }
        }
    }
}
