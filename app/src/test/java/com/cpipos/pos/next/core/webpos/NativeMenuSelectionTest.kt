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

    @Test fun recipeExclusionsBecomeKitchenNoteWithoutMutatingRecipeDefinitions() {
        val config = NativeProductOptionConfig(
            "noodle", emptyList(),
            listOf(
                NativeRecipeIngredient("instant", "มาม่า", "gram", 1.0),
                NativeRecipeIngredient("egg", "ไข่", "gram", 1.0)
            )
        )
        val note = composeNativeRecipeKitchenNote(config, setOf("egg"), "ไม่เผ็ด")
        assertEquals("ไม่ใส่ไข่ | ไม่เผ็ด", note)
        assertEquals(2, config.baseRecipe.size)
        val selection = NativeMenuSelection("noodle", 1, false, emptySet(), note)
        assertEquals(note, selection.note)
        assertNotEquals(
            selection.cartLineId,
            NativeMenuSelection("noodle", 1, false, emptySet(), "").cartLineId
        )
    }

    @Test fun missingConfiguredSpecialPriceCannotPriceLine() {
        val product = WebPosProduct("coffee", "Coffee", "", "Drinks", Satang.of(5000))
        val config = NativeProductOptionConfig("coffee", emptyList(), emptyList())
        val line = NativeMenuSelection("coffee", 1, true, emptySet(), "")
        assertNull(line.displayPrice(product, config))
    }

    @Test fun receiptDescriptionKeepsOptionNamesSpecialModeAndNote() {
        val config = NativeProductOptionConfig(
            productId = "coffee",
            groups = listOf(NativeModifierGroup(
                id = "milk", name = "Milk", selectionType = "single",
                required = true, minSelect = 1, maxSelect = 1,
                options = listOf(NativeModifierOption("oat", "นมโอ๊ต", Satang.ZERO, true))
            )),
            baseRecipe = emptyList()
        )
        val chosen = NativeMenuSelection("coffee", 1, true, setOf("oat"), "หวานน้อย")
        assertEquals("ราคาพิเศษ, นมโอ๊ต, หวานน้อย", chosen.receiptDescription(config))
    }
}
