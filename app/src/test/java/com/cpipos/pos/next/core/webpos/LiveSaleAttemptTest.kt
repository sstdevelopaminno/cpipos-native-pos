package com.cpipos.pos.next.core.webpos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class LiveSaleAttemptTest {
    @Test
    fun decodesImmutableCartAndTenderSnapshot() {
        val attempt = LiveSaleAttempt(
            id = "11111111-1111-1111-1111-111111111111",
            orderId = null,
            orderNo = null,
            stage = "prepared",
            payloadJson = """{"lines":[{"product_id":"22222222-2222-2222-2222-222222222222","name":"ชาไทย","quantity":2,"unit_price_satang":5000}]}""",
            totalSatang = 10000,
            receivedSatang = 12000,
            createdAtMs = 1L
        )
        val lines = attempt.linesOrNull()
        assertNotNull(lines)
        assertEquals(1, lines!!.size)
        assertEquals("ชาไทย", lines.single().name)
        assertEquals(2, lines.single().quantity)
        assertEquals(5000L, lines.single().unitPrice.value)
        assertEquals(12000L, attempt.receivedOrNull()!!.value)
        assertEquals(10000L, attempt.totalOrNull()!!.value)
    }
}
