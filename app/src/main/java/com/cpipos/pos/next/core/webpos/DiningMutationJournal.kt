package com.cpipos.pos.next.core.webpos

import android.content.Context
import org.json.JSONObject
import java.util.UUID

/**
 * Fail-closed local evidence for an uncertain dine-in table write/payment.
 * No PIN, cookie, secrets, or receipt card data is stored. The pending key
 * survives app process death and a new POS session on the same device.
 */
internal class DiningMutationJournal(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "cpipos_native_dining_journal_v1", Context.MODE_PRIVATE
    )

    data class Pending(
        val requestId: String, val kind: String,
        val orderId: String?, val quantities: Map<String, Int>
    )

    private fun key(s: WebPosSession, tableId: String) =
        "dining:" + s.tenantId + ":" + s.branchId + ":" + s.deviceCode + ":" + tableId

    @Synchronized
    fun pending(s: WebPosSession, tableId: String): Pending? {
        val raw = prefs.getString(key(s, tableId), null) ?: return null
        return runCatching {
            val json = JSONObject(raw)
            val data = json.optJSONObject("quantities") ?: JSONObject()
            val qty = data.keys().asSequence().associateWith { data.getInt(it) }
            Pending(
                requestId = json.getString("request_id"),
                kind = json.getString("kind"),
                orderId = json.optString("order_id").takeIf { it.isNotBlank() },
                quantities = qty
            )
        }.getOrElse {
            // Garbled journal is not safe to overwrite. The UI must not
            // re-submit until a recovery procedure is performed.
            Pending("", "corrupted", null, emptyMap())
        }
    }

    @Synchronized
    fun prepare(
        s: WebPosSession, tableId: String, kind: String, orderId: String?,
        requestId: String, quantities: Map<String, Int>
    ) {
        require(UUID.fromString(tableId).toString() == tableId)
        require(UUID.fromString(requestId).toString() == requestId)
        require(kind in setOf("items", "payment", "cancel"))
        require(pending(s, tableId) == null) { "unresolved_dining_mutation" }
        val q = JSONObject()
        quantities.forEach { (id, count) -> q.put(id, count) }
        val document = JSONObject()
            .put("request_id", requestId)
            .put("kind", kind)
            .put("order_id", orderId ?: "")
            .put("quantities", q)
        check(prefs.edit().putString(key(s, tableId), document.toString()).commit()) {
            "dining_journal_persist_failed"
        }
    }

    @Synchronized
    fun confirmed(s: WebPosSession, tableId: String, requestId: String) {
        val stored = pending(s, tableId)
        require(stored?.requestId == requestId) { "dining_journal_key_mismatch" }
        check(prefs.edit().remove(key(s, tableId)).commit()) {
            "dining_journal_clear_failed"
        }
    }
}
