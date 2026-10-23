package com.cpipos.pos.next.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeDiscountTest {
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
    @Test fun percentageHalfUpRounding() {
        val discount = PosDiscount("percent", "12.5")
        assertEquals(1L, discount.amount(4L))
    }
}
