package com.cpipos.pos.next.core.webpos

import com.cpipos.pos.next.core.pos.Satang
import org.json.JSONArray
import org.json.JSONObject

/** Read-only, branch-scoped option definitions supplied by CpIPOS Web.
 * Price previews are NOT authority to charge: checkout must be verified server-side.
 */
internal data class NativeModifierOption(
    val id: String, val name: String, val priceDelta: Satang, val isDefault: Boolean
)

internal data class NativeModifierGroup(
    val id: String, val name: String, val selectionType: String,
    val required: Boolean, val minSelect: Int, val maxSelect: Int,
    val options: List<NativeModifierOption>
)

internal data class NativeRecipeIngredient(
    val id: String, val name: String, val unit: String, val quantityPerItem: Double
)

internal data class NativeProductOptionConfig(
    val productId: String,
    val groups: List<NativeModifierGroup>,
    val baseRecipe: List<NativeRecipeIngredient>
) {
    /**
     * Reject stale/unknown options, duplicates, missing required selection, and
     * over-selected groups before a UI can submit its chosen option IDs.
     */
    fun validSelection(optionIds: Set<String>): Boolean {
        val allIds = groups.flatMap { group -> group.options.map { it.id } }
        if (allIds.distinct().size != allIds.size) return false
        if (!allIds.toSet().containsAll(optionIds)) return false
        return groups.all { group ->
            val count = group.options.count { it.id in optionIds }
            val minimum = if (group.required) maxOf(1, group.minSelect) else group.minSelect
            (count == 0 && !group.required || count >= minimum) &&
                count <= group.maxSelect && (group.selectionType != "single" || count <= 1)
        }
    }

    /** UI-only amount; server must reload options and calculate final price. */
    fun previewPrice(base: Satang, optionIds: Set<String>): Satang? {
        if (!validSelection(optionIds)) return null
        val delta = groups.flatMap { it.options }
            .filter { it.id in optionIds }
            .fold(Satang.ZERO) { sum, option -> sum + option.priceDelta }
        val result = base + delta
        return result.takeIf { it >= Satang.ZERO }
    }
}

private fun JSONArray.jsonObjects(): List<JSONObject> =
    (0 until length()).mapNotNull { optJSONObject(it) }

/** Fail closed if the selected branch returns an invalid modifier contract. */
internal fun parseNativeProductOptions(productId: String, data: JSONObject): NativeProductOptionConfig {
    val groups = data.optJSONObject("modifier_groups_by_product")
        ?.optJSONArray(productId)?.jsonObjects().orEmpty().map { group ->
            val options = group.optJSONArray("options")?.jsonObjects().orEmpty().map { option ->
                NativeModifierOption(
                    id = option.getString("id"), name = option.getString("name"),
                    priceDelta = Satang.fromBaht(option.opt("price_delta")?.toString() ?: "0"),
                    isDefault = option.optBoolean("is_default", false)
                )
            }
            val selectionType = group.optString("selection_type")
            require(selectionType == "single" || selectionType == "multiple")
            val maxSelect = group.optInt("max_select", if (selectionType == "single") 1 else options.size)
            val minSelect = group.optInt("min_select", if (group.optBoolean("is_required")) 1 else 0)
            require(maxSelect >= 0 && minSelect in 0..maxSelect)
            NativeModifierGroup(
                id = group.getString("id"), name = group.getString("name"),
                selectionType = selectionType, required = group.optBoolean("is_required"),
                minSelect = minSelect, maxSelect = maxSelect, options = options
            )
        }
    val ingredients = data.optJSONObject("ingredients_by_product")
        ?.optJSONArray(productId)?.jsonObjects().orEmpty().map { ingredient ->
            NativeRecipeIngredient(
                id = ingredient.getString("ingredient_id"),
                name = ingredient.getString("name"),
                unit = ingredient.optString("base_unit"),
                quantityPerItem = ingredient.getDouble("quantity_per_item")
            )
        }
    require(groups.map { it.id }.distinct().size == groups.size)
    return NativeProductOptionConfig(productId, groups, ingredients)
}
