package com.cpipos.pos.next.core.offline

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.cpipos.pos.next.core.pos.SaleDraft
import com.cpipos.pos.next.core.pos.TenderKind
import org.json.JSONArray
import org.json.JSONObject

enum class OutboxState { PENDING, IN_FLIGHT, RETRY, REQUIRES_REVIEW, CONFIRMED }

data class OutboxSale(
    val id: String,
    val tenantId: String,
    val branchId: String,
    val employeeId: String,
    val deviceId: String,
    val state: OutboxState,
    val payload: String,
    val totalSatang: Long,
    val remoteOrderId: String?
)

/**
 * A durable scoped local outbox, not an alternative source of truth for cloud receipts.
 * Never store a PIN, Supabase access token, service-role key, or bank credentials here.
 */
class NativeSaleOutbox(context: Context) : SQLiteOpenHelper(
    context.applicationContext, "cpipos_native_outbox_v1.db", null, 1
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE native_sales (
              id TEXT PRIMARY KEY NOT NULL,
              tenant_id TEXT NOT NULL, branch_id TEXT NOT NULL,
              employee_id TEXT NOT NULL, device_id TEXT NOT NULL,
              idempotency_key TEXT NOT NULL UNIQUE,
              payload TEXT NOT NULL, total_satang INTEGER NOT NULL,
              state TEXT NOT NULL, remote_order_id TEXT,
              attempts INTEGER NOT NULL DEFAULT 0, last_error TEXT,
              created_at_ms INTEGER NOT NULL, updated_at_ms INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX native_sales_sync_idx ON native_sales(tenant_id, branch_id, state, created_at_ms)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Never silently drop sales. A future schema version requires a reviewed migration.
        throw IllegalStateException("Native sale outbox migration required: $oldVersion -> $newVersion")
    }

    /** Duplicate saleId/idempotencyKey throws rather than creating a second bill. */
    fun enqueue(draft: SaleDraft) {
        val detail = JSONArray()
        draft.lines.forEach { line ->
            detail.put(JSONObject()
                .put("product_id", line.productId)
                .put("name", line.name)
                .put("quantity", line.quantity)
                .put("unit_price_satang", line.unitPrice.value)
                .put("line_total_satang", line.lineTotal.value))
        }
        val payload = JSONObject()
            .put("schema_version", 1)
            .put("sale_id", draft.saleId)
            .put("idempotency_key", draft.idempotencyKey)
            .put("tenant_id", draft.tenantId)
            .put("branch_id", draft.branchId)
            .put("employee_id", draft.employeeId)
            .put("device_id", draft.deviceId)
            .put("tender", draft.tender.name)
            .put("total_satang", draft.total.value)
            .put("received_satang", draft.received.value)
            .put("change_satang", draft.change.value)
            .put("transfer_verified", false)
            .put("created_at_ms", draft.createdAtMs)
            .put("lines", detail)
        val values = ContentValues().apply {
            put("id", draft.saleId)
            put("tenant_id", draft.tenantId)
            put("branch_id", draft.branchId)
            put("employee_id", draft.employeeId)
            put("device_id", draft.deviceId)
            put("idempotency_key", draft.idempotencyKey)
            put("payload", payload.toString())
            put("total_satang", draft.total.value)
            put("state", OutboxState.PENDING.name)
            put("created_at_ms", draft.createdAtMs)
            put("updated_at_ms", System.currentTimeMillis())
        }
        writableDatabase.insertOrThrow("native_sales", null, values)
    }

    /** No cross-store queries: both IDs are mandatory. */
    fun pending(tenantId: String, branchId: String, limit: Int = 50): List<OutboxSale> {
        require(tenantId.isNotBlank() && branchId.isNotBlank())
        require(limit in 1..200)
        readableDatabase.query(
            "native_sales", null,
            "tenant_id=? AND branch_id=? AND state IN (?,?)",
            arrayOf(tenantId, branchId, OutboxState.PENDING.name, OutboxState.RETRY.name),
            null, null, "created_at_ms ASC", limit.toString()
        ).use { cursor ->
            return buildList {
                while (cursor.moveToNext()) add(cursor.asSale())
            }
        }
    }

    /** Claim once before sending; a crashed IN_FLIGHT bill needs explicit recovery/reconciliation. */
    fun claim(tenantId: String, branchId: String, saleId: String): Boolean {
        require(tenantId.isNotBlank() && branchId.isNotBlank())
        val values = ContentValues().apply {
            put("state", OutboxState.IN_FLIGHT.name)
            put("updated_at_ms", System.currentTimeMillis())
        }
        val changed = writableDatabase.update("native_sales", values,
            "id=? AND tenant_id=? AND branch_id=? AND state IN (?,?)",
            arrayOf(saleId, tenantId, branchId, OutboxState.PENDING.name, OutboxState.RETRY.name))
        return changed == 1
    }

    fun confirm(tenantId: String, branchId: String, saleId: String, orderId: String): Boolean {
        require(orderId.isNotBlank())
        val values = ContentValues().apply {
            put("state", OutboxState.CONFIRMED.name)
            put("remote_order_id", orderId)
            putNull("last_error")
            put("updated_at_ms", System.currentTimeMillis())
        }
        return writableDatabase.update("native_sales", values,
            "id=? AND tenant_id=? AND branch_id=? AND state=?",
            arrayOf(saleId, tenantId, branchId, OutboxState.IN_FLIGHT.name)) == 1
    }

    fun markFailure(tenantId: String, branchId: String, saleId: String, retry: Boolean, reason: String): Boolean {
        val values = ContentValues().apply {
            put("state", if (retry) OutboxState.RETRY.name else OutboxState.REQUIRES_REVIEW.name)
            put("last_error", reason.take(200)) // Never put tokens/PINs or raw server responses in reason.
            put("updated_at_ms", System.currentTimeMillis())
        }
        val db = writableDatabase
        db.execSQL("""UPDATE native_sales SET
             state=?, last_error=?, updated_at_ms=?, attempts=attempts+1
             WHERE id=? AND tenant_id=? AND branch_id=? AND state=?""",
            arrayOf(values.getAsString("state"), values.getAsString("last_error"),
                values.getAsLong("updated_at_ms"), saleId, tenantId, branchId, OutboxState.IN_FLIGHT.name))
        return android.database.DatabaseUtils.longForQuery(db,
            "SELECT changes()", null) == 1L
    }

    private fun Cursor.asSale(): OutboxSale = OutboxSale(
        id = getString(getColumnIndexOrThrow("id")),
        tenantId = getString(getColumnIndexOrThrow("tenant_id")),
        branchId = getString(getColumnIndexOrThrow("branch_id")),
        employeeId = getString(getColumnIndexOrThrow("employee_id")),
        deviceId = getString(getColumnIndexOrThrow("device_id")),
        state = OutboxState.valueOf(getString(getColumnIndexOrThrow("state"))),
        payload = getString(getColumnIndexOrThrow("payload")),
        totalSatang = getLong(getColumnIndexOrThrow("total_satang")),
        remoteOrderId = getColumnIndexOrThrow("remote_order_id").let {
            if (isNull(it)) null else getString(it)
        }
    )
}
