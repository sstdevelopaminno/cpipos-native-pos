package com.cpipos.pos.next.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.cpipos.pos.next.core.pos.Satang
import java.math.BigDecimal
import java.math.RoundingMode

/** Discount is money in satang, calculated exactly once before server checkout. */
internal data class PosDiscount(val kind: String = "amount", val input: String = "") {
    fun amount(subtotal: Long): Long {
        if (subtotal <= 0) return 0
        val number = input.toBigDecimalOrNull() ?: return 0
        if (kind !in setOf("percent", "amount") || number < BigDecimal.ZERO) return 0
        val raw = if (kind == "percent") {
            if (number > BigDecimal("100")) return 0
            BigDecimal(subtotal).multiply(number)
                .divide(BigDecimal("100"), 0, RoundingMode.HALF_UP)
        } else {
            number.multiply(BigDecimal(100)).setScale(0, RoundingMode.HALF_UP)
        }
        return raw.min(BigDecimal(subtotal)).toLong()
    }
    fun valid(subtotal: Long): Boolean {
        if (subtotal <= 0L || kind !in setOf("percent", "amount")) return false
        val number = input.toBigDecimalOrNull() ?: return false
        return number >= BigDecimal.ZERO &&
            (kind != "percent" || number <= BigDecimal("100")) &&
            (kind != "amount" || number.multiply(BigDecimal(100)) <= BigDecimal(subtotal)) &&
            // The current server does not allow zero-value checkout, even at 100% discount.
            amount(subtotal) < subtotal
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NativeDiscountDialog(
    subtotal: Long,
    current: PosDiscount,
    onDismiss: () -> Unit,
    onConfirm: (PosDiscount) -> Unit
) {
    var kind by remember { mutableStateOf(current.kind) }
    var input by remember { mutableStateOf(current.input) }
    val discount = PosDiscount(kind, input)
    val amount = discount.amount(subtotal)
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = Color(0xFFF9FBFF),
        title = { Text("ส่วนลด", fontWeight = FontWeight.ExtraBold, color = Color(0xFF172B50)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
                Text("ยอดสินค้าก่อนลด ฿" + Satang.of(subtotal).bahtText())
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    listOf("percent" to "เปอร์เซ็นต์ (%)", "amount" to "จำนวนเงิน (฿)")
                        .forEachIndexed { i, choice ->
                            SegmentedButton(
                                selected = kind == choice.first,
                                onClick = { kind = choice.first; input = "" },
                                shape = SegmentedButtonDefaults.itemShape(index = i, count = 2)
                            ) { Text(choice.second) }
                        }
                }
                OutlinedTextField(
                    value = input,
                    onValueChange = {
                        if (it.length <= 11 && it.matches(Regex("[0-9]*\\.?[0-9]{0,2}")))
                            input = it
                    },
                    singleLine = true,
                    label = { Text(if (kind == "percent") "ส่วนลด %" else "ส่วนลดบาท") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Text("ลด ฿" + Satang.of(amount).bahtText() +
                    "   •   สุทธิ ฿" + Satang.of((subtotal - amount).coerceAtLeast(0)).bahtText(),
                    fontWeight = FontWeight.Bold, color = Color(0xFF176DED))
                if (input.isNotBlank() && !discount.valid(subtotal))
                    Text("ส่วนลดต้องไม่เกินยอดสินค้า และต้องเหลือยอดชำระมากกว่า ฿0.00",
                        color = Color(0xFFAF3449))
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(discount) },
                enabled = input.isNotBlank() && discount.valid(subtotal)) { Text("ยืนยันส่วนลด") }
        },
        dismissButton = {
            TextButton(onClick = { onConfirm(PosDiscount()) }) { Text("ล้างส่วนลด") }
            TextButton(onClick = onDismiss) { Text("กลับ") }
        }
    )
}
