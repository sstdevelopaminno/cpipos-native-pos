package com.cpipos.pos.next.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.cpipos.pos.next.core.webpos.NativeMenuSelection
import com.cpipos.pos.next.core.webpos.NativeModifierGroup
import com.cpipos.pos.next.core.webpos.NativeProductOptionConfig
import com.cpipos.pos.next.core.webpos.WebPosProduct

/**
 * Shared takeaway and dine-in menu editor. The caller must persist this structured choice
 * through the canonical sale transaction; it must NOT drop option IDs or replace the
 * total with a locally calculated amount.
 */
@Composable
internal fun NativeMenuCustomizationDialog(
    product: WebPosProduct,
    config: NativeProductOptionConfig,
    onDismiss: () -> Unit,
    onConfirm: (NativeMenuSelection) -> Unit
) {
    var quantity by remember(product.id) { mutableIntStateOf(1) }
    var useSpecialPrice by remember(product.id) { mutableStateOf(false) }
    var selected by remember(product.id) {
        mutableStateOf(config.groups.flatMap { group ->
            group.options.filter { it.isDefault }.map { it.id }
        }.toSet())
    }
    var note by remember(product.id) { mutableStateOf("") }
    // Recipe exclusions are kitchen instructions. They must not be confused
    // with stock-changing modifier options, which are server-authoritative.
    var excludedRecipeIds by remember(product.id) { mutableStateOf(emptySet<String>()) }

    fun toggle(group: NativeModifierGroup, optionId: String) {
        val sameGroup = group.options.map { it.id }.toSet()
        val next = selected.toMutableSet()
        if (group.selectionType == "single") {
            next.removeAll(sameGroup)
            if (optionId !in selected || group.required) next.add(optionId)
        } else {
            if (optionId in next) next.remove(optionId)
            else if (next.count { it in sameGroup } < group.maxSelect) next.add(optionId)
        }
        selected = next
    }

    val recipeInstructions = config.baseRecipe.filter { it.id in excludedRecipeIds }
        .map { "ไม่ใส่" + it.name }
    val fullNote = (recipeInstructions + listOf(note.trim()).filter { it.isNotBlank() })
        .joinToString(" | ")
    val noteTooLong = fullNote.length > 160
    val draft = remember(product.id, quantity, useSpecialPrice, selected, fullNote) {
        NativeMenuSelection(product.id, quantity, useSpecialPrice, selected, fullNote.take(160))
    }
    val price = draft.displayPrice(product, config)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(product.name + " · เลือกตัวเลือกสินค้า") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 540.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (product.specialPrice != null) {
                    Text("เลือกราคาขาย")
                    Row(modifier = Modifier.fillMaxWidth().clickable { useSpecialPrice = false },
                        verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = !useSpecialPrice, onClick = { useSpecialPrice = false })
                        Text("ราคาปกติ ฿" + product.price.bahtText())
                    }
                    Row(modifier = Modifier.fillMaxWidth().clickable { useSpecialPrice = true },
                        verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = useSpecialPrice, onClick = { useSpecialPrice = true })
                        Text("ราคาพิเศษ ฿" + product.specialPrice.bahtText())
                    }
                }
                config.groups.forEach { group ->
                    Text(group.name + if (group.required) " (ต้องเลือก)" else " (ไม่บังคับ)")
                    group.options.forEach { option ->
                        val active = option.id in selected
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { toggle(group, option.id) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (group.selectionType == "single") {
                                RadioButton(selected = active, onClick = { toggle(group, option.id) })
                            } else {
                                Checkbox(checked = active, onCheckedChange = {
                                    toggle(group, option.id)
                                })
                            }
                            Text(option.name + if (option.priceDelta.value != 0L)
                                " (เพิ่ม/ลด ฿" + option.priceDelta.bahtText() + ")" else "")
                        }
                    }
                }
                if (config.baseRecipe.isNotEmpty()) {
                    Text("วัตถุดิบตามสูตร · แตะเพื่อระบุสิ่งที่ไม่ใส่")
                    config.baseRecipe.forEach { ingredient ->
                        val include = ingredient.id !in excludedRecipeIds
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                excludedRecipeIds = if (include)
                                    excludedRecipeIds + ingredient.id else excludedRecipeIds - ingredient.id
                            },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = include,
                                onCheckedChange = { checked ->
                                    excludedRecipeIds = if (checked)
                                        excludedRecipeIds - ingredient.id else excludedRecipeIds + ingredient.id
                                }
                            )
                            Text(ingredient.name + " " + ingredient.quantityPerItem.toString() +
                                " " + ingredient.unit + if (include) "" else " · ไม่ใส่")
                        }
                    }
                    Text(
                        "การไม่ใส่วัตถุดิบเป็นหมายเหตุส่งครัว ไม่เปลี่ยนราคาหรือยอดตัดสต็อก " +
                            "หากต้องการปรับสต็อก ให้ตั้งตัวเลือกวัตถุดิบใน POS หลัก",
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                    )
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(160) },
                    label = { Text("หมายเหตุ (ถ้ามี)") },
                    modifier = Modifier.fillMaxWidth().testTag("native-item-details-note")
                )
                if (noteTooLong) {
                    Text("หมายเหตุรวมวัตถุดิบยาวเกิน 160 ตัวอักษร กรุณาลดรายการหรือข้อความ",
                        color = androidx.compose.material3.MaterialTheme.colorScheme.error)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { if (quantity > 1) quantity -= 1 }) { Text("−") }
                    Text("จำนวน " + quantity)
                    TextButton(onClick = { if (quantity < 999) quantity += 1 }) { Text("+") }
                    Spacer(modifier = Modifier.weight(1f))
                    Text(price?.let { "฿" + it.bahtText() + " / ชิ้น" } ?: "กรุณาเลือกตัวเลือก")
                }
            }
        },
        confirmButton = {
            Button(enabled = price != null && !noteTooLong, onClick = { onConfirm(draft) }) {
                Text("เพิ่มลงตะกร้า")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ยกเลิก") } }
    )
}
