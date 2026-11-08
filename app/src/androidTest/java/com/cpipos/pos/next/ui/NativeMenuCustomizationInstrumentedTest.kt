package com.cpipos.pos.next.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cpipos.pos.next.core.pos.Satang
import com.cpipos.pos.next.core.webpos.NativeMenuSelection
import com.cpipos.pos.next.core.webpos.NativeModifierGroup
import com.cpipos.pos.next.core.webpos.NativeModifierOption
import com.cpipos.pos.next.core.webpos.NativeProductOptionConfig
import com.cpipos.pos.next.core.webpos.WebPosProduct
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Emulator UI gate for the same option popup used by takeaway and dine-in. */
@RunWith(AndroidJUnit4::class)
class NativeMenuCustomizationInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    private val product = WebPosProduct(
        id = "product", name = "ก๋วยเตี๋ยว", sku = "N01", category = "food",
        price = Satang.of(5500), specialPrice = Satang.of(4500)
    )
    private val config = NativeProductOptionConfig(
        productId = "product",
        groups = listOf(
            NativeModifierGroup(
                id = "g1", name = "เลือกเส้น", selectionType = "single",
                required = true, minSelect = 1, maxSelect = 1,
                options = listOf(
                    NativeModifierOption("o1", "เส้นเล็ก", Satang.ZERO, false),
                    NativeModifierOption("o2", "เส้นหมี่", Satang.of(500), false)
                )
            )
        ), baseRecipe = emptyList()
    )

    @Test
    fun requiresConfiguredIngredientBeforeAddingAndPreservesSpecialPrice() {
        var selected: NativeMenuSelection? = null
        compose.setContent {
            NativeMenuCustomizationDialog(
                product = product, config = config, onDismiss = {},
                onConfirm = { selected = it }
            )
        }
        compose.onNodeWithText("เพิ่มลงตะกร้า").assertIsNotEnabled()
        compose.onNodeWithText("เส้นเล็ก").performClick()
        compose.onNodeWithText("เพิ่มลงตะกร้า").assertIsEnabled()
        compose.onNodeWithText("ราคาพิเศษ ฿45.00").performClick()
        compose.onNodeWithText("เพิ่มลงตะกร้า").performClick()
        compose.runOnIdle {
            val choice = requireNotNull(selected)
            assertEquals(setOf("o1"), choice.selectedOptionIds)
            assertTrue(choice.useSpecialPrice)
            assertEquals(Satang.of(4500), choice.displayPrice(product, config))
        }
    }

    @Test
    fun ordinaryProductCanEnterFreeFormNoteWithoutModifierGroups() {
        var selected: NativeMenuSelection? = null
        compose.setContent {
            NativeMenuCustomizationDialog(
                product = product.copy(specialPrice = null),
                config = NativeProductOptionConfig(product.id, emptyList(), emptyList()),
                onDismiss = {}, onConfirm = { selected = it }
            )
        }
        compose.onNodeWithTag("native-item-details-note").performTextInput("ไม่ใส่น้ำตาล")
        compose.onNodeWithText("เพิ่มลงตะกร้า").performClick()
        compose.runOnIdle {
            assertEquals("ไม่ใส่น้ำตาล", requireNotNull(selected).note)
            assertTrue(requireNotNull(selected).selectedOptionIds.isEmpty())
        }
    }

    @Test
    fun tappingProductCardUsesSingleProductEditorActionWithoutOverlaidDetails() {
        var details = 0
        var adds = 0
        compose.setContent {
            DemoProductCard(
                product = DemoMenuItem("p1", "ชาเย็น", "drink", 3500, "ชา", Color.Blue),
                quantity = 0,
                onAdd = { adds++ },
                onDetails = { details++ }
            )
        }
        compose.onNodeWithText("รายละเอียด").assertDoesNotExist()
        compose.onNodeWithText("ชาเย็น").performClick()
        compose.runOnIdle {
            assertEquals(0, details)
            assertEquals(1, adds)
        }
    }

    @Test
    fun tappingRecipeCheckboxCreatesExplicitKitchenExclusionNote() {
        var selected: NativeMenuSelection? = null
        compose.setContent {
            NativeMenuCustomizationDialog(
                product = product.copy(specialPrice = null),
                config = NativeProductOptionConfig(
                    product.id, emptyList(),
                    listOf(com.cpipos.pos.next.core.webpos.NativeRecipeIngredient(
                        "egg", "ไข่", "gram", 1.0
                    ))
                ),
                onDismiss = {},
                onConfirm = { selected = it }
            )
        }
        compose.onNodeWithText("ไข่ 1.0 gram").performClick()
        compose.onNodeWithText("เพิ่มลงตะกร้า").performClick()
        compose.runOnIdle {
            assertEquals("ไม่ใส่ไข่", requireNotNull(selected).note)
        }
    }

    @Test
    fun regularAndSpecialPricesCreateDistinctLineIdentities() {
        val regular = NativeMenuSelection("product", 1, false, setOf("o2"), "")
        val special = regular.copy(useSpecialPrice = true)
        assertFalse(regular.cartLineId == special.cartLineId)
    }
}
