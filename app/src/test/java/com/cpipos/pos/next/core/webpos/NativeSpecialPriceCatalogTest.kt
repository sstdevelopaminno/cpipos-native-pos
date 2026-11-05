package com.cpipos.pos.next.core.webpos

import com.cpipos.pos.next.core.pos.Satang
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Catalog prices are optional metadata; missing/invalid values must never replace the base price. */
class NativeSpecialPriceCatalogTest {
    @Test fun parsesConfiguredPriceInSatangWithoutFloatingPoint() {
        assertEquals(Satang.of(3950L), parseOptionalSpecialPrice("39.50"))
        assertEquals(Satang.ZERO, parseOptionalSpecialPrice("0"))
    }

    @Test fun rejectsAbsentOrMalformedSpecialPrice() {
        assertNull(parseOptionalSpecialPrice(null))
        assertNull(parseOptionalSpecialPrice(""))
        assertNull(parseOptionalSpecialPrice("null"))
        assertNull(parseOptionalSpecialPrice("-1"))
        assertNull(parseOptionalSpecialPrice("not-a-number"))
    }

    @Test fun productKeepsNormalAndSpecialPricesSeparate() {
        val item = WebPosProduct(
            id = "test", name = "Tea", sku = "T", category = "Drinks",
            price = Satang.of(5500), specialPrice = Satang.of(4500)
        )
        assertEquals(Satang.of(5500), item.price)
        assertEquals(Satang.of(4500), item.specialPrice)
    }
}
