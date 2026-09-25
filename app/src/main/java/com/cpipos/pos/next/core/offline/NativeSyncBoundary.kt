package com.cpipos.pos.next.core.offline

/**
 * A trusted backend MUST re-check the authenticated user, tenant, branch, employee,
 * device, package and shift. It MUST atomically create order/items/payment/stock
 * with the outbox idempotency key, and replay the same receipt on retry.
 *
 * BANK_TRANSFER must stay unverified until server-side bank confirmation.
 */
data class VerifiedSaleSession(
    val tenantId: String,
    val branchId: String,
    val employeeId: String,
    val deviceId: String,
    val expiresAtMs: Long
)

sealed interface SubmitSaleResult {
    data class Accepted(val serverOrderId: String) : SubmitSaleResult
    data object RetryLater : SubmitSaleResult
    data object RequiresReview : SubmitSaleResult
}

fun interface NativeSaleBackend {
    suspend fun submitAuthenticated(session: VerifiedSaleSession, sale: OutboxSale): SubmitSaleResult
}

/** No backend implementation is installed until the transactional RPC is reviewed. */
class NativeSyncBoundary(
    private val outbox: NativeSaleOutbox,
    private val backend: NativeSaleBackend
) {
    suspend fun submitOne(session: VerifiedSaleSession, sale: OutboxSale): SubmitSaleResult {
        require(System.currentTimeMillis() < session.expiresAtMs) { "Session expired: reauthenticate" }
        require(sale.tenantId == session.tenantId && sale.branchId == session.branchId)
        require(sale.employeeId == session.employeeId && sale.deviceId == session.deviceId)
        require(sale.state == OutboxState.PENDING || sale.state == OutboxState.RETRY)
        check(outbox.claim(session.tenantId, session.branchId, sale.id)) { "Bill already claimed or settled" }
        val result = try {
            backend.submitAuthenticated(session, sale)
        } catch (_: Exception) {
            SubmitSaleResult.RetryLater
        }
        when (result) {
            is SubmitSaleResult.Accepted ->
                check(outbox.confirm(session.tenantId, session.branchId, sale.id, result.serverOrderId))
            SubmitSaleResult.RetryLater ->
                check(outbox.markFailure(session.tenantId, session.branchId, sale.id, true, "backend_retry"))
            SubmitSaleResult.RequiresReview ->
                check(outbox.markFailure(session.tenantId, session.branchId, sale.id, false, "manual_review"))
        }
        return result
    }
}
