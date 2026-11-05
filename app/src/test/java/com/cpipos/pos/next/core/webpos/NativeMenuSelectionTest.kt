package com.cpipos.pos.next.core.webpos

import com.cpipos.pos.next.core.pos.Satang
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NativeMenuSelectionTest {
    @Test fun distinctOptionsAndSpecialPriceNeverMerge() {
        fun line(option: String, special: Boolean) = NativeMenuSelection(
            productId = "coffee", quantity = 1, useSpecialPrice = special,
            selectedOptionIds = setOf(option), note = ""
        )
        val a = line("oat", false)
        val b = line("milk", false)
        val c = line("oat", true)
        assertNotEquals(a.cartLineId, b.cartLineId)
        assertNotEquals(a.cartLineId, c.cartLineId)
        val cart = consolidateNativeSelections(listOf(a, b, c, a))
        assertEquals(3, cart.size)
        assertEquals(2, cart.single { it.cartLineId == a.cartLineId }.quantity)
    }

    @Test fun missingConfiguredSpecialPriceCannotPriceLine() {
        val product = WebPosProduct("coffee", "Coffee", "", "Drinks", Satang.of(5000))
        val config = NativeProductOptionConfig("coffee", emptyList(), emptyList())
        val line = NativeMenuSelection("coffee", 1, true, emptySet(), "")
        assertNull(line.displayPrice(product, config))
    }
}
