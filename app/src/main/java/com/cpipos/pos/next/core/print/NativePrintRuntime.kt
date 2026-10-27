package com.cpipos.pos.next.core.print

import android.content.Context
import com.cpipos.pos.next.core.push.NativeFcmRegistration
import com.cpipos.pos.next.core.security.NativeAndroidDeveloperGuard
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
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Native POS 2.0 print worker. CpIPOS server owns routing/queue truth; this
 * process only claims jobs routed to this POS device and executes physical I/O.
 */
object NativePrintRuntime {
    private val lock = Any()
    private var runtime: Worker? = null

    fun start(context: Context, client: WebPosClient, session: WebPosSession) {
        synchronized(lock) {
            val current = runtime
            if (current?.sameSession(session) == true && current.isRunning()) return
            current?.stop()
            runtime = Worker(context.applicationContext, client, session).also { it.start() }
        }
    }

    fun stop() {
        synchronized(lock) {
            runtime?.stop()
            runtime = null
        }
    }

    fun wake() {
        synchronized(lock) { runtime?.wake() }
    }

    fun diagnostics(): String = synchronized(lock) {
        runtime?.diagnostics()?.toString() ?: JSONObject()
            .put("running", false)
            .toString()
    }

    private class Worker(
        private val context: Context,
        private val client: WebPosClient,
        private val session: WebPosSession
    ) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val transport = NativePrinterTransport(context)
        private val prefs = context.getSharedPreferences(
            "cpipos_native_print_runtime_v2", Context.MODE_PRIVATE
        )
        private val wakeRequested = AtomicBoolean(false)
        private var job: Job? = null
        private var lastError: String? = null
        private var lastJobId: String? = null
        private var lastTransport: String? = null
        private var lastSuccessAt: Long? = null
        private var lastHeartbeatAt = 0L
        private var idleIndex = 0
        // Process-local fallback if persisting the dedupe ledger fails.
        private val recentPrinted = LinkedHashMap<String, Long>()
        private val scopeKey =
            session.tenantId + ":" + session.branchId + ":" + session.deviceCode

        fun sameSession(other: WebPosSession): Boolean =
            other.id == session.id &&
                other.tenantId == session.tenantId &&
                other.branchId == session.branchId &&
                other.deviceCode == session.deviceCode

        fun isRunning(): Boolean = job?.isActive == true

        fun start() {
            if (job?.isActive == true) return
            job = scope.launch { loop() }
        }

        fun stop() {
            job?.cancel()
            scope.cancel()
        }

        fun wake() {
            wakeRequested.set(true)
        }

        fun diagnostics(): JSONObject = JSONObject()
            .put("running", isRunning())
            .put("device_code", session.deviceCode)
            .put("last_job_id", lastJobId)
            .put("last_transport", lastTransport)
            .put("last_success_at", lastSuccessAt)
            .put("last_error", lastError)

        private suspend fun loop() {
            while (scope.isActive) {
                val claimed = try {
                    tick()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    lastError = failure.message ?: failure.javaClass.simpleName
                    0
                }
                val seconds = if (claimed > 0 || wakeRequested.getAndSet(false)) {
                    idleIndex = 0
                    1L
                } else {
                    val value = IDLE_SECONDS[idleIndex.coerceIn(0, IDLE_SECONDS.lastIndex)]
                    if (idleIndex < IDLE_SECONDS.lastIndex) idleIndex += 1
                    value
                }
                var waited = 0L
                while (waited < seconds * 1000L && scope.isActive && !wakeRequested.get()) {
                    delay(250L)
                    waited += 250L
                }
            }
        }

        private suspend fun tick(): Int {
            // The print worker can outlive MainActivity. Never rely solely on
            // Compose dialogs for tenant developer/ADB enforcement.
            val policy = NativeAndroidDeveloperGuard.cachedEnabled(context, session.tenantId)
            if (policy == null || (policy && NativeAndroidDeveloperGuard.inspect(context).detected)) {
                lastError = "android_developer_security_guard"
                return 0
            }
            val agentKey = getOrBootstrapKey() ?: return 0
            if (System.currentTimeMillis() - lastHeartbeatAt >= 60_000L) {
                try {
                    client.heartbeatNativePrintAgent(agentKey)
                    lastHeartbeatAt = System.currentTimeMillis()
                } catch (failure: WebPosApiException) {
                    if (failure.status == 401 || failure.status == 403) {
                        clearAgentKey()
                        return 0
                    }
                }
            }
            val rows = try {
                client.claimNativePrintJobs(agentKey)
            } catch (failure: WebPosApiException) {
                if (failure.status == 401 || failure.status == 403) clearAgentKey()
                throw failure
            }
            for (index in 0 until rows.length()) {
                val row = rows.optJSONObject(index) ?: continue
                process(agentKey, row)
            }
            return rows.length()
        }

        private suspend fun getOrBootstrapKey(): String? {
            prefs.getString("agent_key:" + scopeKey, null)
                ?.takeIf { it.startsWith("cpi_pa_") }
                ?.let { return it }
            return try {
                val data = client.bootstrapNativePrintAgent(
                    session,
                    NativeFcmRegistration.installationId(context)
                )
                val key = data.optString("agent_key").trim()
                if (!key.startsWith("cpi_pa_")) {
                    lastError = "print_agent_key_missing"
                    null
                } else {
                    prefs.edit().putString("agent_key:" + scopeKey, key).apply()
                    lastError = null
                    key
                }
            } catch (failure: Exception) {
                lastError = failure.message ?: "print_agent_bootstrap_failed"
                null
            }
        }

        private fun clearAgentKey() {
            prefs.edit().remove("agent_key:" + scopeKey).apply()
            lastHeartbeatAt = 0L
        }

        private suspend fun process(agentKey: String, row: JSONObject) {
            val jobId = row.optString("id").trim()
            val attemptId = row.optString("agent_attempt_id").trim()
            if (jobId.isEmpty() || attemptId.isEmpty()) return
            lastJobId = jobId

            if (wasPrinted(jobId)) {
                runCatching {
                    client.ackNativePrintJob(
                        agentKey, jobId, attemptId,
                        "android-dedupe:" + jobId, 0, "dedupe"
                    )
                }
                return
            }

            val parsed = try {
                transport.parseJob(row)
            } catch (failure: NativePrintException) {
                runCatching {
                    client.failNativePrintJob(
                        agentKey, jobId, attemptId,
                        failure.code, failure.message ?: failure.code, failure.retryable
                    )
                }
                return
            }

            // An ACK/network failure *after* successful physical output is not a
            // print failure. Never mark such a job failed/retryable on the server.
            var physicalOutputCompleted = false
            try {
                val result = withContext(Dispatchers.IO) { transport.print(parsed) }
                physicalOutputCompleted = true
                // Persist before ACK so a reclaimed server lease is deduplicated.
                val ledgerPersisted = rememberPrinted(jobId)
                lastTransport = result.transport
                lastSuccessAt = System.currentTimeMillis()
                try {
                    client.ackNativePrintJob(
                        agentKey = agentKey,
                        jobId = jobId,
                        attemptId = attemptId,
                        providerJobId = result.providerJobId,
                        bytesSent = result.bytesSent,
                        transport = result.transport
                    )
                    lastError = if (ledgerPersisted) null else "print_dedupe_ledger_persist_failed"
                } catch (failure: Exception) {
                    if (failure is CancellationException) throw failure
                    // Leave the lease to be reconciled by the server. Our local
                    // dedupe ledger will ACK a later claim without printing again.
                    lastError = "print_ack_pending:" + failure.javaClass.simpleName
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: NativePrintException) {
                lastError = failure.code + ":" + failure.message.orEmpty()
                if (!physicalOutputCompleted) runCatching {
                    client.failNativePrintJob(
                        agentKey, jobId, attemptId,
                        failure.code, failure.message ?: failure.code, failure.retryable
                    )
                }
            } catch (failure: Exception) {
                lastError = if (physicalOutputCompleted)
                    "print_ack_pending:" + failure.javaClass.simpleName
                else failure.message ?: "native_print_failed"
                if (!physicalOutputCompleted) runCatching {
                    client.failNativePrintJob(
                        agentKey, jobId, attemptId,
                        "native_print_failed",
                        failure.message ?: "Native print failed",
                        true
                    )
                }
            }
        }

        private fun wasPrinted(jobId: String): Boolean {
            val now = System.currentTimeMillis()
            recentPrinted.entries.removeAll { now - it.value >= PRINTED_TTL_MS }
            if (recentPrinted.containsKey(jobId)) return true
            val ledger = readLedger(now)
            val at = ledger.optLong(jobId, 0L)
            return at > 0L && now - at < PRINTED_TTL_MS
        }

        private fun rememberPrinted(jobId: String): Boolean {
            val now = System.currentTimeMillis()
            recentPrinted[jobId] = now
            while (recentPrinted.size > 120) {
                val oldest = recentPrinted.keys.firstOrNull() ?: break
                recentPrinted.remove(oldest)
            }
            val ledger = readLedger(now)
            ledger.put(jobId, now)
            val keys = ledger.keys().asSequence().toList()
            if (keys.size > 120) {
                keys.sortedBy { ledger.optLong(it, Long.MAX_VALUE) }
                    .take(keys.size - 120)
                    .forEach { ledger.remove(it) }
            }
            // This runs on the print worker's Dispatchers.IO scope, not the UI
            // thread. commit() closes the crash window between physical output
            // and the server ACK so a reclaimed lease can be deduplicated.
            return prefs.edit()
                .putString("printed:" + scopeKey, ledger.toString())
                .commit()
        }

        private fun readLedger(now: Long): JSONObject {
            val ledger = runCatching {
                JSONObject(prefs.getString("printed:" + scopeKey, "{}").orEmpty())
            }.getOrElse { JSONObject() }
            ledger.keys().asSequence().toList().forEach { key ->
                val at = ledger.optLong(key, 0L)
                if (at <= 0L || now - at > PRINTED_TTL_MS) ledger.remove(key)
            }
            return ledger
        }
    }

    private val IDLE_SECONDS = longArrayOf(1L, 3L, 8L, 15L, 30L)
    private const val PRINTED_TTL_MS = 24L * 60L * 60L * 1000L
}
