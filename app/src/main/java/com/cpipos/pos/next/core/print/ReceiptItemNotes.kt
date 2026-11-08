package com.cpipos.pos.next.core.print

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

private const val ITEM_DETAIL_MARKER = "[[CPIPOS_ITEM_DETAIL]]:"

/**
 * Receipt-only rendering of the POS canonical item note envelope.
 * Keep the source data untouched; do not display the internal encoded marker.
 */
internal fun printableReceiptItemNotes(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val options = mutableListOf<String>()
    val details = mutableListOf<String>()
    raw.replace("\r\n", "\n").replace("\r", "\n").lineSequence().forEach { line ->
        val value = line.trim()
        if (value.isEmpty()) return@forEach
        if (value.startsWith(ITEM_DETAIL_MARKER)) {
            val encoded = value.removePrefix(ITEM_DETAIL_MARKER)
            val decoded = runCatching {
                URLDecoder.decode(encoded, StandardCharsets.UTF_8.name())
            }.getOrNull()?.trim()
            if (!decoded.isNullOrEmpty() && !decoded.contains(ITEM_DETAIL_MARKER)) {
                details += decoded
            }
        } else {
            options += value
        }
    }
    val printable = options + details.map { "รายละเอียด: " + it }
    return printable.joinToString("\n").ifBlank { null }
}
