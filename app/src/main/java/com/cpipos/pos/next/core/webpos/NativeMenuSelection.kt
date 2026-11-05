package com.cpipos.pos.next.core.webpos

import com.cpipos.pos.next.core.pos.Satang
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/** Structured immutable draft; never replace server-side price, tax or stock validation. */
internal data class NativeMenuSelection(
    val productId: String,
    val quantity: Int,
    val useSpecialPrice: Boolean,
    val selectedOptionIds: Set<String>,
    val note: String
) {
    init {
        require(productId.isNotBlank() && quantity in 1..999)
        require(note.length <= 160 && selectedOptionIds.size <= 24)
    }

    /** Stable identity for variants of the same product, including price and options. */
    val cartLineId: String get() {
        val material = listOf(
            productId, if (useSpecialPrice) "special" else "regular",
            selectedOptionIds.sorted().joinToString(","), note.trim()
        ).joinToString("\u001f")
        val sha = MessageDigest.getInstance("SHA-256").digest(
            material.toByteArray(StandardCharsets.UTF_8)
        ).take(16).joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return "$productId:$sha"
    }

    fun displayPrice(product: WebPosProduct, config: NativeProductOptionConfig): Satang? {
        if (product.id != productId) return null
        val base = if (useSpecialPrice) product.specialPrice ?: return null else product.price
        return config.previewPrice(base, selectedOptionIds)
    }
}

/** Prevents merging different options or special and normal prices into one POS line. */
internal fun consolidateNativeSelections(lines: List<NativeMenuSelection>): List<NativeMenuSelection> {
    val byKey = linkedMapOf<String, NativeMenuSelection>()
    lines.forEach { line ->
        val old = byKey[line.cartLineId]
        val next = if (old != null) old.copy(quantity = Math.addExact(old.quantity, line.quantity)) else line
        require(next.quantity in 1..999)
        byKey[line.cartLineId] = next
    }
    return byKey.values.toList()
}
