package com.cpipos.pos.next.core.offline

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.cpipos.pos.next.core.pos.Satang
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.time.LocalDate
import java.time.ZoneId

/**
 * ISOLATED preview-only paid-cash ledger. Never synchronise with CpiPOS-001, do not
 * count in business reports or disguise these as actual customer receipts.
 */
data class DemoReceiptLine(
    val productId: String, val name: String, val quantity: Int, val unitPrice: Satang
) {
    val amount: Satang get() = unitPrice * quantity
}

data class DemoSaleReceipt(
    val id: String, val billNo: String, val createdAtMs: Long,
    val branchName: String, val counterCode: String, val modeLabel: String,
    val lines: List<DemoReceiptLine>,
    val total: Satang, val received: Satang, val change: Satang
)

data class DemoDailyTotal(val billCount: Int, val sales: Satang)

class DemoSaleLedger(context: Context) : SQLiteOpenHelper(
    context.applicationContext, "cpipos_preview_cash_sales_v1.db", null, 1
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE preview_cash_receipts (
              id TEXT PRIMARY KEY NOT NULL, bill_no TEXT NOT NULL UNIQUE,
              branch_name TEXT NOT NULL, counter_code TEXT NOT NULL,
              mode_label TEXT NOT NULL, created_at_ms INTEGER NOT NULL,
              lines_json TEXT NOT NULL,
              total_satang INTEGER NOT NULL, received_satang INTEGER NOT NULL,
              change_satang INTEGER NOT NULL, method TEXT NOT NULL CHECK(method = 'cash'),
              environment TEXT NOT NULL CHECK(environment = 'PREVIEW_ONLY')
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX preview_receipts_scope ON preview_cash_receipts(branch_name, counter_code, created_at_ms)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        throw IllegalStateException("Explicit preview ledger migration required")
    }

    /**
     * Commit receipt and its immutable item snapshot in ONE SQLite transaction.
     * Caller must NOT clear the cart if this throws; never retry with a new UUID.
     */
    fun saveCash(
        branchName: String, counterCode: String, modeLabel: String,
        lines: List<DemoReceiptLine>, received: Satang,
        id: String = UUID.randomUUID().toString(), now: Long = System.currentTimeMillis()
    ): DemoSaleReceipt {
        require(branchName.isNotBlank() && counterCode.isNotBlank() && modeLabel.isNotBlank())
        require(lines.isNotEmpty() && lines.all { it.quantity > 0 && it.unitPrice >= Satang.ZERO })
        require(lines.map { it.productId }.distinct().size == lines.size)
        val total = lines.fold(Satang.ZERO) { sum, item -> sum + item.amount }
        require(total > Satang.ZERO && received >= total)
        val date = SimpleDateFormat("yyyyMMdd", Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("Asia/Bangkok")
        }.format(Date(now))
        val prefix = if (modeLabel.contains("นั่งโต๊ะ")) "DIN" else "TKO"
        val billNo = "DEMO-$prefix-$date-${id.take(8).uppercase(Locale.US)}"
        val receipt = DemoSaleReceipt(
            id, billNo, now, branchName, counterCode, modeLabel,
            lines.toList(), total, received, received - total
        )
        val json = JSONArray()
        receipt.lines.forEach { item ->
            json.put(JSONObject().put("id", item.productId)
                .put("name", item.name).put("quantity", item.quantity)
                .put("unit_price_satang", item.unitPrice.value))
        }
        val values = ContentValues().apply {
            put("id", receipt.id)
            put("bill_no", receipt.billNo)
            put("branch_name", receipt.branchName)
            put("counter_code", receipt.counterCode)
            put("mode_label", receipt.modeLabel)
            put("created_at_ms", receipt.createdAtMs)
            put("lines_json", json.toString())
            put("total_satang", receipt.total.value)
            put("received_satang", receipt.received.value)
            put("change_satang", receipt.change.value)
            put("method", "cash")
            put("environment", "PREVIEW_ONLY")
        }
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.insertOrThrow("preview_cash_receipts", null, values)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return receipt
    }

    fun today(branchName: String, counterCode: String): List<DemoSaleReceipt> {
        require(branchName.isNotBlank() && counterCode.isNotBlank())
        val zone = ZoneId.of("Asia/Bangkok")
        val start = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        readableDatabase.query(
            "preview_cash_receipts", null,
            "branch_name=? AND counter_code=? AND created_at_ms>=? AND created_at_ms<?",
            arrayOf(branchName, counterCode, start.toString(), end.toString()),
            null, null, "created_at_ms DESC"
        ).use { cur ->
            return buildList {
                while (cur.moveToNext()) add(cur.asReceipt())
            }
        }
    }

    fun todayTotal(branchName: String, counterCode: String): DemoDailyTotal {
        val receipts = today(branchName, counterCode)
        return DemoDailyTotal(receipts.size, receipts.fold(Satang.ZERO) { sum, bill -> sum + bill.total })
    }

    fun find(id: String): DemoSaleReceipt? {
        readableDatabase.query(
            "preview_cash_receipts", null, "id=?", arrayOf(id), null, null, null, "1"
        ).use { cur -> return if (cur.moveToFirst()) cur.asReceipt() else null }
    }

    private fun Cursor.asReceipt(): DemoSaleReceipt {
        val json = JSONArray(getString(getColumnIndexOrThrow("lines_json")))
        val items = (0 until json.length()).map { index ->
            val row = json.getJSONObject(index)
            DemoReceiptLine(
                row.getString("id"), row.getString("name"), row.getInt("quantity"),
                Satang.of(row.getLong("unit_price_satang"))
            )
        }
        return DemoSaleReceipt(
            getString(getColumnIndexOrThrow("id")),
            getString(getColumnIndexOrThrow("bill_no")),
            getLong(getColumnIndexOrThrow("created_at_ms")),
            getString(getColumnIndexOrThrow("branch_name")),
            getString(getColumnIndexOrThrow("counter_code")),
            getString(getColumnIndexOrThrow("mode_label")),
            items,
            Satang.of(getLong(getColumnIndexOrThrow("total_satang"))),
            Satang.of(getLong(getColumnIndexOrThrow("received_satang"))),
            Satang.of(getLong(getColumnIndexOrThrow("change_satang")))
        )
    }
}
