package com.cpipos.pos.next.core.pos

import java.math.BigDecimal

/** Immutable cash keypad input; at most 7 baht digits and two satang digits. */
object CashTenderInput {
    private const val MAX_BAHT_DIGITS = 7

    fun append(current: String, key: String): String {
        val value = current.ifBlank { "0" }
        return when (key) {
            "ลบ", "C" -> "0"
            "⌫" -> value.dropLast(1).ifBlank { "0" }
            "." -> if (value.contains(".")) value else "$value."
            "00" -> append(append(value, "0"), "0")
            in "0".."9" -> {
                val candidate = if (value == "0") key else value + key
                if (candidate.substringBefore('.').length > MAX_BAHT_DIGITS ||
                    (candidate.contains('.') && candidate.substringAfter('.').length > 2)
                ) value else candidate
            }
            else -> value
        }
    }

    fun money(input: String): Satang =
        Satang.fromBaht(input.trimEnd('.').ifBlank { "0" })

    fun setToDue(due: Satang): String = due.bahtText().trimEnd('0').trimEnd('.')

    fun addAmount(current: String, baht: Int): String {
        require(baht > 0)
        val result = money(current) + Satang.of(baht.toLong() * 100L)
        val decimal = BigDecimal(result.bahtText())
        require(decimal < BigDecimal.TEN.pow(MAX_BAHT_DIGITS))
        return decimal.stripTrailingZeros().toPlainString()
    }

    fun isSufficient(current: String, due: Satang) = money(current) >= due && due > Satang.ZERO
}
