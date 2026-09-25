package com.cpipos.pos.next.core.pos

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

/** Store money as INTEGER satang. No floating-point arithmetic in checkout or outbox. */
@JvmInline
value class Satang private constructor(val value: Long) : Comparable<Satang> {
    companion object {
        val ZERO = Satang(0)
        fun of(value: Long) = Satang(value)
        fun fromBaht(text: String): Satang {
            val decimal = BigDecimal(text.trim()).setScale(2, RoundingMode.UNNECESSARY)
            return Satang(decimal.movePointRight(2).longValueExact())
        }
    }

    operator fun plus(other: Satang) = Satang(Math.addExact(value, other.value))
    operator fun minus(other: Satang) = Satang(Math.subtractExact(value, other.value))
    operator fun times(quantity: Int) = Satang(Math.multiplyExact(value, quantity.toLong()))
    override fun compareTo(other: Satang): Int = value.compareTo(other.value)
    fun bahtText(): String = BigDecimal.valueOf(value, 2).toPlainString()
}

data class SaleLine(
    val productId: String,
    val name: String,
    val quantity: Int,
    val unitPrice: Satang
) {
    init {
        require(productId.isNotBlank() && name.isNotBlank())
        require(quantity in 1..9999)
        require(unitPrice >= Satang.ZERO)
    }
    val lineTotal: Satang get() = unitPrice * quantity
}

enum class TenderKind { CASH, BANK_TRANSFER }

/**
 * Stable ID is generated once at bill creation, retained across network retries.
 * An offline transfer is NOT verified; final settlement is server-authoritative.
 */
data class SaleDraft(
    val saleId: String,
    val tenantId: String,
    val branchId: String,
    val employeeId: String,
    val deviceId: String,
    val lines: List<SaleLine>,
    val tender: TenderKind,
    val received: Satang,
    val createdAtMs: Long
) {
    init {
        require(runCatching { UUID.fromString(saleId) }.isSuccess)
        require(tenantId.isNotBlank() && branchId.isNotBlank())
        require(employeeId.isNotBlank() && deviceId.isNotBlank())
        require(lines.isNotEmpty())
        require(lines.map { it.productId }.distinct().size == lines.size)
        require(createdAtMs > 0L)
        require(received >= Satang.ZERO)
        if (tender == TenderKind.CASH) require(received >= total)
        if (tender == TenderKind.BANK_TRANSFER) require(received == Satang.ZERO)
    }

    val total: Satang get() = lines.fold(Satang.ZERO) { sum, line -> sum + line.lineTotal }
    val change: Satang get() = if (tender == TenderKind.CASH) received - total else Satang.ZERO
    val idempotencyKey: String get() = "native-pos-v1:$saleId"
}
