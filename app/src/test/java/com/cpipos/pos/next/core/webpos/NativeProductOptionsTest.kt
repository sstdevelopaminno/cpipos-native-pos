package com.cpipos.pos.next.core.webpos

import com.cpipos.pos.next.core.pos.Satang
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeProductOptionsTest {
    private val productId = "aaaaaaaa-aaaa-4aaa-aaaa-aaaaaaaaaaaa"

    private fun config() = parseNativeProductOptions(productId, JSONObject(
        """{
           "modifier_groups_by_product": {
             "$productId": [
               {
                 "id":"g1", "name":"ชนิดเส้น", "selection_type":"single",
                 "is_required":true, "min_select":1, "max_select":1,
                 "options":[
                   {"id":"o1","name":"เส้นเล็ก","price_delta":0,"is_default":true},
                   {"id":"o2","name":"เส้นหมี่","price_delta":5.00,"is_default":false}
                 ]
               },
               {
                 "id":"g2", "name":"เพิ่มท็อปปิง", "selection_type":"multiple",
                 "is_required":false, "min_select":0, "max_select":2,
                 "options":[
                   {"id":"o3","name":"ลูกชิ้น","price_delta":10.00,"is_default":false},
                   {"id":"o4","name":"ไข่","price_delta":8.50,"is_default":false}
                 ]
               }
             ]
           },
           "ingredients_by_product": {
             "$productId": [
               {"ingredient_id":"ing1","name":"เส้นหลัก","base_unit":"gram","quantity_per_item":120}
             ]
           }
        }"""
    ))

    @Test fun requiresSingleChoiceAndRejectsUnknownOrExtraSelections() {
        val config = config()
        assertFalse(config.validSelection(emptySet()))
        assertFalse(config.validSelection(setOf("o3")))
        assertFalse(config.validSelection(setOf("o1", "o2")))
        assertFalse(config.validSelection(setOf("o1", "untrusted")))
        assertTrue(config.validSelection(setOf("o2", "o3", "o4")))
        assertEquals(1, config.baseRecipe.size)
    }

    @Test fun previewUsesIntegerSatangButNeverSubstitutesForServerPrice() {
        val config = config()
        assertEquals(Satang.of(7350), config.previewPrice(Satang.of(5000), setOf("o2", "o3", "o4")))
        assertNull(config.previewPrice(Satang.of(5000), emptySet()))
    }

    @Test fun rejectsMalformedSelectionTypeOrPrice() {
        val malformed = JSONObject("""{"modifier_groups_by_product":{"$productId":[{"id":"g1","name":"A","selection_type":"invalid","options":[]}]}}""")
        assertTrue(runCatching { parseNativeProductOptions(productId, malformed) }.isFailure)
        val badAmount = JSONObject("""{"modifier_groups_by_product":{"$productId":[{"id":"g1","name":"A","selection_type":"single","options":[{"id":"x","name":"X","price_delta":"not-a-number"}]}]}}""")
        assertTrue(runCatching { parseNativeProductOptions(productId, badAmount) }.isFailure)
    }
}
