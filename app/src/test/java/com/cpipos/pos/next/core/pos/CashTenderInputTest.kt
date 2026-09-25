package com.cpipos.pos.next.core.pos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CashTenderInputTest {
    @Test fun decimalAndBackspace() {
        val entered = listOf("3", "0", "0", ".", "5", "0")
            .fold("0") { current, key -> CashTenderInput.append(current, key) }
        assertEquals("300.50", entered)
        assertEquals(30050L, CashTenderInput.money(entered).value)
        assertEquals("300.5", CashTenderInput.append(entered, "⌫"))
        assertEquals("0", CashTenderInput.append(entered, "ลบ"))
    }

    @Test fun presetsAndQuickAddsAreExact() {
        val total = Satang.fromBaht("245.00")
        assertEquals("245", CashTenderInput.setToDue(total))
        assertEquals("300", CashTenderInput.addAmount("250", 50))
        assertEquals("300.25", CashTenderInput.addAmount("200.25", 100))
        assertEquals("300", CashTenderInput.append("3", "00"))
    }

    @Test fun preventsUnderpaymentAndExcessDecimalDigits() {
        val total = Satang.fromBaht("245.00")
        assertFalse(CashTenderInput.isSufficient("244.99", total))
        assertTrue(CashTenderInput.isSufficient("300", total))
        assertEquals("1.23", CashTenderInput.append("1.23", "4"))
        assertEquals("9999999", CashTenderInput.append("9999999", "1"))
    }
}
