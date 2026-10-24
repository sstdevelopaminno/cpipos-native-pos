package com.cpipos.pos.next.core.webpos

import com.cpipos.pos.next.core.pos.Satang
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiningTableSyncContractTest {
    private fun bill(
        status: String = "ordering",
        orderStatus: String = "queued",
        paid: Boolean = false,
        total: Long = 15000L,
        count: Int = 2,
        orderId: String? = "11111111-1111-4111-8111-111111111111"
    ) = WebDiningBill(
        sessionId = "22222222-2222-4222-8222-222222222222",
        sessionStatus = status,
        orderId = orderId,
        orderNo = "DIN-005",
        orderStatus = orderStatus,
        paid = paid,
        total = Satang.of(total),
        lines = listOf(WebDiningLine(
            productId = "33333333-3333-4333-8333-333333333333",
            name = "ลาเต้ร้อน",
            quantity = count,
            unitPrice = Satang.of(7500L),
            notes = null,
            hasCustomMetadata = false
        ))
    )

    @Test
    fun activeOpenOrQueuedTableIsEditableUnderSameRealBill() {
        assertTrue(bill().editable)
        assertTrue(bill(status = "open", orderId = null).editable)
    }

    @Test
    fun paidClosedOrPaymentLockedTableCannotAcceptNewItems() {
        assertFalse(bill(status = "pending_payment").editable)
        assertFalse(bill(status = "closed", orderStatus = "completed", paid = true).editable)
        assertFalse(bill(orderStatus = "completed").editable)
        assertFalse(bill(paid = true).editable)
    }

    @Test
    fun changingTotalsItemsOrStatusInvalidatesClientPreflightFingerprint() {
        val original = bill()
        assertNotEquals(original.fingerprint(), bill(total = 14000).fingerprint())
        assertNotEquals(original.fingerprint(), bill(count = 3).fingerprint())
        assertNotEquals(original.fingerprint(), bill(status = "pending_payment").fingerprint())
        assertNotEquals(original.fingerprint(), bill(paid = true).fingerprint())
        assertNotEquals(original.fingerprint(), bill(orderId = null).fingerprint())
    }
}
