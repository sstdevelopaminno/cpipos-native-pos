package com.cpipos.pos.next.core.pos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.UUID

class SaleDraftTest {
    @Test fun moneyIsExactInSatang() {
        assertEquals(1030L, (Satang.fromBaht("10.10") + Satang.fromBaht("0.20")).value)
        assertEquals("10.30", Satang.of(1030).bahtText())
        assertThrows(ArithmeticException::class.java) { Satang.fromBaht("1.001") }
    }

    @Test fun changeAndTotalAreExact() {
        val sale = draft(TenderKind.CASH, Satang.fromBaht("100.00"))
        assertEquals(7050L, sale.total.value)
        assertEquals(2950L, sale.change.value)
        assertEquals("native-pos-v1:${sale.saleId}", sale.idempotencyKey)
    }

    @Test fun rejectsUnderpaymentAndUnverifiedTransferTender() {
        assertThrows(IllegalArgumentException::class.java) {
            draft(TenderKind.CASH, Satang.fromBaht("20.00"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            draft(TenderKind.BANK_TRANSFER, Satang.fromBaht("100.00"))
        }
        assertEquals(0L, draft(TenderKind.BANK_TRANSFER, Satang.ZERO).change.value)
    }

    @Test fun duplicateCartProductCannotProduceAmbiguousPayment() {
        val base = draft(TenderKind.CASH, Satang.fromBaht("100.00"))
        assertThrows(IllegalArgumentException::class.java) {
            base.copy(lines = base.lines + base.lines.first())
        }
    }

    private fun draft(tender: TenderKind, received: Satang) = SaleDraft(
        saleId = UUID.randomUUID().toString(),
        tenantId = "tenant", branchId = "branch", employeeId = "staff", deviceId = "device",
        lines = listOf(
            SaleLine("p1", "ชา", 2, Satang.fromBaht("20.25")),
            SaleLine("p2", "ข้าว", 1, Satang.fromBaht("30.00"))
        ),
        tender = tender, received = received, createdAtMs = 1L
    )
}
