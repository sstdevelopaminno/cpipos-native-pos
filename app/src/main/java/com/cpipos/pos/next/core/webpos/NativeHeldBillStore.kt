package com.cpipos.pos.next.core.webpos

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * CpIPOS Web currently parks unsent cart drafts in browser localStorage.
 * Native mirrors that LOCAL-DRAFT semantics per tenant/branch/device; these are
 * not server-created orders and therefore not cross-device synced.
 */
internal class NativeHeldBillStore(context: Context) {
    data class Draft(
        val id: String, val savedAt: Long,
        val saleId: String, val items: Map<String, Int>,
        val discountKind: String, val discountInput: String
    )
    private val prefs = context.applicationContext.getSharedPreferences(
        "cpipos_native_held_bills_v1", Context.MODE_PRIVATE
    )
    private fun key(s: WebPosSession) =
        s.tenantId + ":" + s.branchId + ":" + s.deviceCode

    @Synchronized
    fun all(s: WebPosSession): List<Draft> {
        val array = runCatching {
            JSONArray(prefs.getString(key(s), "[]") ?: "[]")
        }.getOrElse { throw IllegalStateException("held_bills_corrupted_require_review") }
        return (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            val items = o.getJSONObject("items")
            Draft(
                id = o.getString("id"), savedAt = o.getLong("saved_at"),
                saleId = o.getString("sale_id"),
                items = items.keys().asSequence().associateWith { items.getInt(it) },
                discountKind = o.optString("discount_kind", "amount"),
                discountInput = o.optString("discount_input", "")
            )
        }.sortedByDescending { it.savedAt }
    }

    @Synchronized
    fun hold(s: WebPosSession, saleId: String, items: Map<String, Int>,
             discountKind: String, discountInput: String): Draft {
        require(UUID.fromString(saleId).toString() == saleId)
        require(items.isNotEmpty() && items.values.all { it in 1..9999 })
        val existing = all(s)
        require(existing.none { it.saleId == saleId }) {
            "held_sale_uuid_already_exists"
        }
        val held = Draft(
            id = UUID.randomUUID().toString(),
            savedAt = System.currentTimeMillis(),
            saleId = saleId,
            items = items.toMap(),
            discountKind = discountKind,
            discountInput = discountInput
        )
        write(s, existing + held)
        return held
    }

    @Synchronized
    fun update(
        s: WebPosSession, id: String,
        items: Map<String, Int>, discountKind: String, discountInput: String
    ) {
        require(items.isNotEmpty() && items.values.all { it in 1..9999 })
        val existing = all(s)
        check(existing.any { it.id == id }) { "held_draft_not_found" }
        write(s, existing.map { entry ->
            if (entry.id == id) entry.copy(
                items = items.toMap(),
                discountKind = discountKind,
                discountInput = discountInput
            ) else entry
        })
    }

    @Synchronized
    fun remove(s: WebPosSession, id: String) {
        val existing = all(s)
        require(existing.any { it.id == id })
        write(s, existing.filterNot { it.id == id })
    }

    private fun write(s: WebPosSession, drafts: List<Draft>) {
        val rows = JSONArray()
        drafts.forEach { draft ->
            val items = JSONObject()
            draft.items.forEach { (id, qty) -> items.put(id, qty) }
            rows.put(JSONObject()
                .put("id", draft.id)
                .put("saved_at", draft.savedAt)
                .put("sale_id", draft.saleId)
                .put("items", items)
                .put("discount_kind", draft.discountKind)
                .put("discount_input", draft.discountInput))
        }
        check(prefs.edit().putString(key(s), rows.toString()).commit()) {
            "held_bills_persist_failed"
        }
    }
}
