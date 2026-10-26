package com.cpipos.pos.next.ui

import org.junit.Assert.assertEquals
import com.cpipos.pos.next.core.pos.Satang
import com.cpipos.pos.next.core.webpos.WebDiningBill
import com.cpipos.pos.next.core.webpos.WebDiningLine
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeDiscountTest {
    @Test fun dineInDiscountBaseUsesProductSubtotalNotTaxInclusiveExistingTotal() {
        val existing = WebDiningBill(
            sessionId = "table-session", sessionStatus = "open", orderId = "order-1",
            orderNo = "DINE-001", orderStatus = "queued", paid = false,
            lines = listOf(WebDiningLine("p1", "อาหาร", 2, Satang.of(5_000L), null, false)),
            total = Satang.of(9_630L), discount = Satang.of(1_000L)
        )
        assertEquals(15_000L, diningDiscountBase(existing, 5_000L))
        assertEquals(1_500L, PosDiscount("percent", "10").amount(diningDiscountBase(existing, 5_000L)))
    }
    @Test fun percentFromOneThousandBahtIsRoundedToExactSatang() {
        val discount = PosDiscount("percent", "12.5")
        assertTrue(discount.valid(100_000L))
        assertEquals(12_500L, discount.amount(100_000L))
    }
    @Test fun amountCannotExceedSaleSubtotal() {
        val discount = PosDiscount("amount", "450")
        assertFalse(discount.valid(20_000L))
        assertEquals(20_000L, discount.amount(20_000L))
    }
    @Test fun percentMoreThan100IsRejected() {
        val discount = PosDiscount("percent", "101")
        assertFalse(discount.valid(10_000L))
        assertEquals(0L, discount.amount(10_000L))
    }
    @Test fun fractionalBahtAndRounding() {
        val discount = PosDiscount("amount", "15.25")
        assertTrue(discount.valid(2_000L))
        assertEquals(1_525L, discount.amount(2_000L))
    }
    @Test fun fullDiscountMustNotCreateZeroValueSale() {
        assertFalse(PosDiscount("percent", "100").valid(12_345L))
        assertFalse(PosDiscount("amount", "123.45").valid(12_345L))
        assertTrue(PosDiscount("amount", "123.44").valid(12_345L))
    }
    @Test fun unsupportedDiscountKindsAreRejected() {
        assertFalse(PosDiscount("free", "20").valid(20_000L))
        assertEquals(0L, PosDiscount("free", "20").amount(20_000L))
    }
    @Test fun percentageHalfUpRounding() {
        val discount = PosDiscount("percent", "12.5")
        assertEquals(1L, discount.amount(4L))
    }
}
