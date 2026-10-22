package com.cpipos.pos.next.core.webpos

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.cpipos.pos.next.core.offline.DemoReceiptLine
import com.cpipos.pos.next.core.pos.Satang
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Durable journal for an online cash checkout.
 *
 * Version 2 stores the immutable cart/tender snapshot as well as the canonical
 * server order identity. This makes a lost HTTP response recoverable with the
 * SAME idempotency key instead of creating a second bill.
 */
data class LiveSaleAttempt(
    val id: String,
    val orderId: String?,
    val orderNo: String?,
    val stage: String,
    val payloadJson: String?,
    val totalSatang: Long?,
    val receivedSatang: Long?,
    val createdAtMs: Long,
    val paymentMethod: String = "cash"
) {
    fun linesOrNull(): List<DemoReceiptLine>? {
        val raw = payloadJson?.takeIf { it.isNotBlank() } ?: return null
        return runCatching {
            val rows = JSONObject(raw).getJSONArray("lines")
            (0 until rows.length()).map { index ->
                val row = rows.getJSONObject(index)
                DemoReceiptLine(
                    productId = row.getString("product_id"),
                    name = row.getString("name"),
                    quantity = row.getInt("quantity"),
                    unitPrice = Satang.of(row.getLong("unit_price_satang"))
                )
            }
        }.getOrNull()
    }

    fun receivedOrNull(): Satang? = receivedSatang?.let(Satang::of)
    fun totalOrNull(): Satang? = totalSatang?.let(Satang::of)
}

class LiveSaleJournal(context: Context) : SQLiteOpenHelper(
    context.applicationContext, "cpipos_live_sale_attempts_v1.db", null, 3
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE live_attempts(
              id TEXT PRIMARY KEY NOT NULL,
              tenant_id TEXT NOT NULL,
              branch_id TEXT NOT NULL,
              device_code TEXT NOT NULL,
              order_id TEXT,
              order_no TEXT,
              stage TEXT NOT NULL,
              payload_json TEXT,
              total_satang INTEGER,
              received_satang INTEGER,
              payment_method TEXT NOT NULL DEFAULT 'cash',
              created_at_ms INTEGER NOT NULL,
              updated_at_ms INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX live_attempt_scope ON live_attempts(tenant_id, branch_id, device_code, stage)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion == 1 && newVersion >= 2) {
            // Non-destructive migration: old uncertain bills remain visible for
            // manual/server reconciliation; they simply lack a replay payload.
            db.execSQL("ALTER TABLE live_attempts ADD COLUMN order_no TEXT")
            db.execSQL("ALTER TABLE live_attempts ADD COLUMN payload_json TEXT")
            db.execSQL("ALTER TABLE live_attempts ADD COLUMN total_satang INTEGER")
            db.execSQL("ALTER TABLE live_attempts ADD COLUMN received_satang INTEGER")
        }
        if (oldVersion <= 2 && newVersion >= 3) {
            db.execSQL("ALTER TABLE live_attempts ADD COLUMN payment_method TEXT NOT NULL DEFAULT 'cash'")
            return
        }
        error("Live sale journal migration required: $oldVersion -> $newVersion")
    }

    fun unresolved(session: WebPosSession): List<LiveSaleAttempt> {
        readableDatabase.query(
            "live_attempts",
            arrayOf(
                "id", "order_id", "order_no", "stage", "payload_json",
                "total_satang", "received_satang", "created_at_ms", "payment_method"
            ),
            "tenant_id=? AND branch_id=? AND device_code=? AND stage NOT IN (?,?)",
            arrayOf(
                session.tenantId, session.branchId, session.deviceCode,
                "confirmed", "resolved_cancelled"
            ),
            null,
            null,
            "created_at_ms ASC"
        ).use { cursor ->
            return buildList {
                while (cursor.moveToNext()) {
                    add(
                        LiveSaleAttempt(
                            id = cursor.getString(0),
                            orderId = if (cursor.isNull(1)) null else cursor.getString(1),
                            orderNo = if (cursor.isNull(2)) null else cursor.getString(2),
                            stage = cursor.getString(3),
                            payloadJson = if (cursor.isNull(4)) null else cursor.getString(4),
                            totalSatang = if (cursor.isNull(5)) null else cursor.getLong(5),
                            receivedSatang = if (cursor.isNull(6)) null else cursor.getLong(6),
                            createdAtMs = cursor.getLong(7),
                            paymentMethod = cursor.getString(8)
                        )
                    )
                }
            }
        }
    }

    /**
     * Returns false when the exact bill UUID already exists.
     * A different unresolved bill on the same terminal blocks a new checkout.
     */
    fun prepare(
        session: WebPosSession,
        billId: String,
        lines: List<DemoReceiptLine>,
        total: Satang,
        received: Satang,
        paymentMethod: String = "cash"
    ): Boolean {
        require(UUID.fromString(billId).toString() == billId)
        require(lines.isNotEmpty())
        require(lines.all { it.quantity > 0 && it.unitPrice >= Satang.ZERO })
        require(total > Satang.ZERO && received >= total)
        require(paymentMethod == "cash" || paymentMethod == "bank_transfer")
        if (paymentMethod == "bank_transfer") require(received == total)
        require(unresolved(session).all { it.id == billId }) {
            "Unresolved previous sale: reconcile it before opening a new bill"
        }
        val payload = JSONObject().apply {
            put(
                "lines",
                JSONArray().apply {
                    lines.forEach { line ->
                        put(
                            JSONObject()
                                .put("product_id", line.productId)
                                .put("name", line.name)
                                .put("quantity", line.quantity)
                                .put("unit_price_satang", line.unitPrice.value)
                        )
                    }
                }
            )
        }.toString()
        val now = System.currentTimeMillis()
        val row = ContentValues().apply {
            put("id", billId)
            put("tenant_id", session.tenantId)
            put("branch_id", session.branchId)
            put("device_code", session.deviceCode)
            put("stage", "prepared")
            put("payload_json", payload)
            put("total_satang", total.value)
            put("received_satang", received.value)
            put("payment_method", paymentMethod)
            put("created_at_ms", now)
            put("updated_at_ms", now)
        }
        return writableDatabase.insertWithOnConflict(
            "live_attempts", null, row, SQLiteDatabase.CONFLICT_IGNORE
        ) != -1L
    }

    fun orderCreated(
        session: WebPosSession,
        billId: String,
        orderId: String,
        orderNo: String
    ) {
        require(orderId.isNotBlank() && orderNo.isNotBlank())
        val values = ContentValues().apply {
            put("order_id", orderId)
            put("order_no", orderNo)
            put("stage", "order_created")
            put("updated_at_ms", System.currentTimeMillis())
        }
        check(
            writableDatabase.update(
                "live_attempts",
                values,
                "id=? AND tenant_id=? AND branch_id=? AND device_code=? AND stage NOT IN (?,?)",
                arrayOf(
                    billId, session.tenantId, session.branchId, session.deviceCode,
                    "confirmed", "resolved_cancelled"
                )
            ) == 1
        )
    }

    fun confirmed(session: WebPosSession, billId: String) {
        transition(session, billId, "confirmed")
    }

    fun resolvedCancelled(session: WebPosSession, billId: String) {
        transition(session, billId, "resolved_cancelled")
    }

    fun review(session: WebPosSession, billId: String) {
        transition(session, billId, "requires_review")
    }

    private fun transition(session: WebPosSession, billId: String, stage: String) {
        val values = ContentValues().apply {
            put("stage", stage)
            put("updated_at_ms", System.currentTimeMillis())
        }
        check(
            writableDatabase.update(
                "live_attempts",
                values,
                "id=? AND tenant_id=? AND branch_id=? AND device_code=? AND stage NOT IN (?,?)",
                arrayOf(
                    billId, session.tenantId, session.branchId, session.deviceCode,
                    "confirmed", "resolved_cancelled"
                )
            ) == 1
        )
    }
}
