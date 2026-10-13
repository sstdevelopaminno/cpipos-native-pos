package com.cpipos.pos.next.core.supabase.model

import com.cpipos.pos.next.core.pos.Satang
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class PostgresNumericTest {
    @Test fun numericIsNotRoundedThroughDouble() {
        val product = Json.decodeFromString<ProductReadModel>(
            """{"id":"p","tenant_id":"t","branch_id":"b","sku":"S","name":"Tea","category":"Drink","price":0.10,"is_active":true}"""
        )
        assertEquals("0.10", product.price)
        assertEquals(10L, Satang.fromBaht(product.price).value)
    }

    @Test fun largeExactAmountStaysAsDecimalText() {
        val product = Json.decodeFromString<ProductReadModel>(
            """{"id":"p","tenant_id":"t","branch_id":"b","sku":"S","name":"Tea","category":"Drink","price":123456789.99,"is_active":true}"""
        )
        assertEquals("123456789.99", product.price)
        assertEquals(12345678999L, Satang.fromBaht(product.price).value)
    }
}
