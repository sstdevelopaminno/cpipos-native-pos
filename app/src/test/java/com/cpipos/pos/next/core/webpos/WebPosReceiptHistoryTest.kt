package com.cpipos.pos.next.core.webpos

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class WebPosReceiptHistoryTest {
    private val session = WebPosSession(
        id = "session-1",
        tenantId = "tenant-1",
        branchId = "branch-1",
        branchName = "ถนนเพชรบุรี",
        storeName = "ร้านทดสอบ",
        cashierName = "พนักงานหนึ่ง",
        employeeId = "employee-1",
        deviceCode = "POS-01",
        shiftId = "shift-1",
        canOpenShift = true,
        canSell = true
    )

    @Test
    fun parsesCanonicalCashReceiptWithRealTenderAndChange() {
        val data = JSONObject(
            """{
              "branch":{"id":"branch-1","name":"ถนนเพชรบุรี","store_profile":{"display_name":"ร้านจริง","company_address":"กรุงเทพฯ","contact_phone":"0800000000"}},
              "records":[{
                "id":"11111111-1111-1111-1111-111111111111",
                "orderNo":"TKO-001",
                "orderType":"takeaway",
                "status":"completed",
                "createdAt":"2026-10-22T10:00:00.000Z",
                "cashierName":"แคชเชียร์จริง",
                "totalAmount":260,
                "paidTotal":260,
                "cashReceived":300,
                "changeAmount":40,
                "paymentMethods":["cash"],
                "items":[{"product_id":"22222222-2222-2222-2222-222222222222","name":"ชาไทย","quantity":2,"unit_price":50,"line_total":100}]
              }],
              "pagination":{"page":1,"page_size":40,"total":1,"total_pages":1}
            }"""
        )
        val history = parseHistory(data, session)
        val receipt = history.records.single().receipt
        assertEquals("TKO-001", receipt.billNo)
        assertEquals(26000L, receipt.total.value)
        assertEquals(30000L, receipt.received.value)
        assertEquals(4000L, receipt.change.value)
        assertEquals("ร้านจริง", receipt.storeName)
        assertEquals("แคชเชียร์จริง", receipt.cashierName)
        assertEquals(1, receipt.lines.size)
    }

    @Test
    fun discountedReceiptUsesCanonicalBackendDiscountNotEstimatedLineTotals() {
        val data = JSONObject("""{
          "records":[{"id":"11111111-1111-1111-1111-111111111111",
          "orderNo":"TKO-DISCOUNT","orderType":"takeaway","status":"completed",
          "totalAmount":85.75,"subtotal":100.00,"discountAmount":14.25,
          "paidTotal":85.75,"cashReceived":100,"changeAmount":14.25,
          "paymentMethods":["cash"],"items":[]}]
        }""")
        val receipt = parseHistory(data, session).records.single().receipt
        assertEquals(1425L, receipt.discount.value)
        assertEquals(10000L, receipt.subtotal?.value)
        assertEquals(8575L, receipt.total.value)
    }

    @Test
    fun oldCashReceiptWithoutTenderFallsBackToPaidTotalInsteadOfZero() {
        val data = JSONObject(
            """{
              "branch":{"id":"branch-1","name":"ถนนเพชรบุรี","store_profile":{}},
              "records":[{
                "id":"33333333-3333-3333-3333-333333333333",
                "orderNo":"TKO-OLD",
                "orderType":"takeaway",
                "status":"completed",
                "createdAt":"2026-10-22T10:00:00.000Z",
                "totalAmount":130,
                "paidTotal":130,
                "cashReceived":0,
                "changeAmount":0,
                "paymentMethods":["cash"],
                "items":[]
              }],
              "pagination":{"page":1,"page_size":40,"total":1,"total_pages":1}
            }"""
        )
        val receipt = parseHistory(data, session).records.single().receipt
        assertEquals(13000L, receipt.received.value)
        assertEquals(0L, receipt.change.value)
    }
    @Test
    fun bankTransferHistoryShowsTransferNotCashInReceipt() {
        val data = JSONObject(
            """{
              "branch":{"id":"branch-1","name":"ถนนเพชรบุรี","store_profile":{}},
              "records":[{
                "id":"44444444-4444-4444-4444-444444444444",
                "orderNo":"TKO-QR-001",
                "orderType":"takeaway",
                "status":"completed",
                "createdAt":"2026-10-22T10:00:00.000Z",
                "totalAmount":245,
                "paidTotal":245,
                "cashReceived":0,
                "changeAmount":0,
                "paymentMethods":["bank_transfer"],
                "items":[]
              }],
              "pagination":{"page":1,"page_size":40,"total":1,"total_pages":1}
            }"""
        )
        val receipt = parseHistory(data, session).records.single().receipt
        assertEquals("bank_transfer", receipt.paymentMethod)
        assertEquals(24500L, receipt.total.value)
        assertEquals(24500L, receipt.received.value)
        assertEquals(0L, receipt.change.value)
    }

}
