package com.cpipos.pos.next.core.webpos

import android.content.Context
import java.util.UUID

/**
 * A narrowly scoped, durable fail-closed marker before an audited sales-list
 * delete request. Stores order UUID only (no PIN, approval token, receipt,
 * password or bank data). Prevents repeating an uncertain stock-restoring
 * mutation after Android process death.
 */
internal class SalesDeleteJournal(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "cpipos_sales_delete_pending_v1", Context.MODE_PRIVATE
    )

    private fun key(session: WebPosSession): String =
        session.tenantId + ":" + session.branchId + ":" + session.deviceCode

    @Synchronized
    fun pending(session: WebPosSession): Set<String> =
        prefs.getStringSet(key(session), emptySet())?.toSet() ?: emptySet()

    @Synchronized
    fun prepare(session: WebPosSession, orderId: String) {
        require(UUID.fromString(orderId).toString() == orderId)
        val ids = pending(session)
        check(orderId !in ids) { "sales_delete_reconciliation_required" }
        check(prefs.edit().putStringSet(key(session), ids + orderId).commit()) {
            "sales_delete_journal_not_durable"
        }
    }

    @Synchronized
    fun confirmed(session: WebPosSession, orderId: String) {
        val ids = pending(session)
        require(orderId in ids) { "sales_delete_journal_key_mismatch" }
        check(prefs.edit().putStringSet(key(session), ids - orderId).commit()) {
            "sales_delete_journal_clear_failed"
        }
    }
}
