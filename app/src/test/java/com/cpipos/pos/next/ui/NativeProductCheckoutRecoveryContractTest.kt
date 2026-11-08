package com.cpipos.pos.next.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guard the exact user-reported Android release regressions. */
class NativeProductCheckoutRecoveryContractTest {
    private fun ui(name: String) =
        File("src/main/java/com/cpipos/pos/next/ui/$name").readText()
    private fun core(name: String) =
        File("src/main/java/com/cpipos/pos/next/core/webpos/$name").readText()

    @Test fun entireProductCardOpensEditorWithoutOverlayChip() {
        val cards = ui("MobilePosSalePreview.kt")
        assertFalse(cards.contains("Text(\"รายละเอียด\", fontSize = 11.sp"))
        assertTrue(cards.contains(".clickable(onClick = onAdd)"))
        assertTrue(cards.contains("editingProductId = product.id"))
        assertTrue(ui("NativeDiningOrderScreen.kt").contains("editingProductId = product.id"))
    }

    @Test fun ingredientCheckboxesSendInstructionsButCannotClaimStockChange() {
        val editor = ui("NativeMenuCustomizationDialog.kt")
        val model = core("NativeMenuSelection.kt")
        assertTrue(editor.contains("excludedRecipeIds"))
        assertTrue(editor.contains("Checkbox("))
        assertTrue(editor.contains("composeNativeRecipeKitchenNote"))
        assertTrue(editor.contains("!noteTooLong"))
        assertTrue(model.contains("ไม่ใส่"))
        assertTrue(model.contains("DO NOT change canonical product recipes"))
    }

    @Test fun rejectBeforeWriteDoesNotPermanentlyLockEitherCashierCart() {
        val dining = ui("NativeDiningOrderScreen.kt")
        val takeaway = ui("MobilePosSalePreview.kt")
        val journal = core("LiveSaleJournal.kt")
        assertTrue(dining.contains("api?.code == \"native_atomic_menu_release_gate_not_enabled\""))
        assertTrue(dining.contains("draft.uncertain = false"))
        assertTrue(takeaway.contains("resolveKnownPrewriteRejection"))
        assertTrue(takeaway.contains("activeBillId = UUID.randomUUID().toString()"))
        assertTrue(journal.contains("AND stage='prepared' AND order_id IS NULL"))
        assertTrue(journal.contains("rejected_before_write"))
    }

    @Test fun unsentItemsAreRemovableEvenWhenOriginalTableBillHasCustomOptions() {
        val dining = ui("NativeDiningOrderScreen.kt")
        assertTrue(dining.contains("if (!draft.uncertain && !draft.busy) {"))
        assertFalse(dining.contains("if (!draft.uncertain && !draft.busy && !stale && !custom) {"))
        assertTrue(dining.contains("draft.items.remove(id)"))
    }
}
