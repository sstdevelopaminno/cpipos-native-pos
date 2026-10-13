package com.cpipos.pos.next.core.webpos

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.UUID

/**
 * Durable online-sale request IDs. An uncertain payment is never silently
 * retried with a fresh key or shown as paid. Reconcile with Web POS before
 * completing the same bill / commencing a different sale on this terminal.
 */
data class LiveSaleAttempt(val id: String, val orderId: String?, val stage: String)

class LiveSaleJournal(context: Context): SQLiteOpenHelper(
    context.applicationContext, "cpipos_live_sale_attempts_v1.db", null, 1
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE live_attempts(
              id TEXT PRIMARY KEY NOT NULL,
              tenant_id TEXT NOT NULL, branch_id TEXT NOT NULL,
              device_code TEXT NOT NULL,
              order_id TEXT, stage TEXT NOT NULL,
              created_at_ms INTEGER NOT NULL, updated_at_ms INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX live_attempt_scope ON live_attempts(tenant_id, branch_id, device_code, stage)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion:Int, newVersion:Int) {
        error("Review live sale journal schema migration")
    }

    fun unresolved(session: WebPosSession): List<LiveSaleAttempt> {
        readableDatabase.query(
            "live_attempts",
            arrayOf("id", "order_id", "stage"),
            "tenant_id=? AND branch_id=? AND device_code=? AND stage !=?",
            arrayOf(session.tenantId, session.branchId, session.deviceCode, "confirmed"),
            null, null, "created_at_ms DESC"
        ).use { c ->
            return buildList {
                while(c.moveToNext()) add(
                    LiveSaleAttempt(
                        c.getString(0),
                        if(c.isNull(1)) null else c.getString(1),
                        c.getString(2)
                    )
                )
            }
        }
    }

    /** Returns false if the same stable bill ID was already journaled. */
    fun prepare(session: WebPosSession, billId: String): Boolean {
        require(UUID.fromString(billId).toString() == billId)
        require(unresolved(session).all { it.id == billId }) {
            "Unresolved previous sale: reconcile in Web POS first"
        }
        val row = ContentValues().apply {
            put("id", billId)
            put("tenant_id", session.tenantId)
            put("branch_id", session.branchId)
            put("device_code", session.deviceCode)
            put("stage", "prepared")
            put("created_at_ms", System.currentTimeMillis())
            put("updated_at_ms", System.currentTimeMillis())
        }
        return writableDatabase.insertWithOnConflict(
            "live_attempts", null, row, SQLiteDatabase.CONFLICT_IGNORE
        ) != -1L
    }

    fun orderCreated(session: WebPosSession, billId: String, orderId: String) {
        require(orderId.isNotBlank())
        val values = ContentValues().apply {
            put("order_id", orderId)
            put("stage", "order_created")
            put("updated_at_ms", System.currentTimeMillis())
        }
        check(writableDatabase.update("live_attempts", values,
            "id=? AND tenant_id=? AND branch_id=? AND device_code=? AND stage !=?",
            arrayOf(billId,session.tenantId,session.branchId,session.deviceCode,"confirmed")) == 1)
    }

    fun confirmed(session: WebPosSession, billId: String) {
        transition(session,billId,"confirmed")
    }
    fun review(session: WebPosSession, billId: String) {
        transition(session,billId,"requires_review")
    }

    private fun transition(session: WebPosSession, billId: String, stage: String) {
        val values = ContentValues().apply {
            put("stage", stage)
            put("updated_at_ms", System.currentTimeMillis())
        }
        check(writableDatabase.update("live_attempts", values,
            "id=? AND tenant_id=? AND branch_id=? AND device_code=? AND stage !=?",
            arrayOf(billId,session.tenantId,session.branchId,session.deviceCode,"confirmed")) == 1)
    }
}
