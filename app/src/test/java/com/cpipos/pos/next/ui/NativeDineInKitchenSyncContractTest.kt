package com.cpipos.pos.next.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression guard for the 2026-11-08 test of table T02:
 * an unsaved phone cart is NOT an order and cannot appear on cashier/KDS.
 */
class NativeDineInKitchenSyncContractTest {
    private fun screen() = File("src/main/java/com/cpipos/pos/next/ui/NativeDiningOrderScreen.kt").readText()
    private fun sheet() = File("src/main/java/com/cpipos/pos/next/ui/MobilePosSalePreview.kt").readText()
    private fun client() = File("src/main/java/com/cpipos/pos/next/core/webpos/WebPosClient.kt").readText()

    @Test fun dineInCartRequiresExplicitSaveBeforeKitchenDispatch() {
        val screen = screen()
        val sheet = sheet()
        assertFalse(screen.contains("ยังไม่ส่งไป POS หลักหรือห้องครัว"))
        assertTrue(screen.contains("menu.cart_prompt"))
        assertTrue(screen.contains("pendingCartProductName = original.name"))
        assertTrue(screen.contains("onHoldBill = {"))
        assertTrue(screen.contains("if (canSubmit) save(thenCheckout = false)"))
        assertTrue(screen.contains("emphasizeHoldBill = count > 0"))
        assertTrue(screen.contains("holdEnabled = canSubmit"))
        assertTrue(sheet.contains("holdLabel: String = \"พักบิล\""))
        assertTrue(sheet.contains("if (emphasizeHoldBill && onHoldBill != null)"))
    }

    @Test fun saveConfirmsCentralBillThenKitchenWithoutResubmittingTheOrder() {
        val screen = screen()
        val client = client()
        assertTrue(screen.contains("mutationJournal.confirmed(session, table.id, requestId)"))
        assertTrue(screen.contains("client.ensureDiningKitchenDispatch(session, requireNotNull(verified.orderId))"))
        assertTrue(screen.contains("draft.pendingKitchenOrderId = if (kitchenWarning == null) null else verified.orderId"))
        assertTrue(screen.contains("บันทึกบิลโต๊ะและส่งเข้าครัวแล้ว"))
        assertTrue(client.contains("GET\", \"/api/pos/tables/\$tableId/bill?refresh=1"))
        assertTrue(client.contains("/api/pos/orders/\$orderId/kitchen-dispatch"))
        assertFalse(screen.contains("save(thenCheckout = false)\n                save("))
    }

    @Test fun imageUpdatesDoNotRefetchEveryModifierAndPollsAreRateLimitedWhileEditing() {
        val screen = screen()
        assertTrue(screen.contains("LaunchedEffect(client, session.id, optionProductIds)"))
        assertTrue(screen.contains("optionProductIds.chunked(60).chunked(3)"))
        assertTrue(screen.contains("delay(if (draft.items.isNotEmpty()) 12_000L else 6_000L)"))
        assertFalse(screen.contains("LaunchedEffect(client, session.id, products)"))
    }
}
