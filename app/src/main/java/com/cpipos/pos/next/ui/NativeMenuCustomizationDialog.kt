package com.cpipos.pos.next.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cpipos.pos.next.core.webpos.NativeMenuSelection
import com.cpipos.pos.next.core.webpos.NativeModifierGroup
import com.cpipos.pos.next.core.webpos.NativeProductOptionConfig
import com.cpipos.pos.next.core.webpos.composeNativeRecipeKitchenNote
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
    // These are optional kitchen instructions, never stock-changing modifiers.
    var selectedRecipeIds by remember(product.id, config.baseRecipe) {
        mutableStateOf(emptySet<String>())
    }

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

    val fullNote = composeNativeRecipeKitchenNote(config, selectedRecipeIds, note)
    val noteTooLong = fullNote.length > 160
    val draft = remember(product.id, quantity, useSpecialPrice, selected, fullNote) {
        NativeMenuSelection(product.id, quantity, useSpecialPrice, selected, fullNote.take(160))
    }
    val price = draft.displayPrice(product, config)
    val blue = Color(0xFF176DED)
    val ink = Color(0xFF192B47)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        titleContentColor = ink,
        textContentColor = ink,
        shape = RoundedCornerShape(24.dp),
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(product.name, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp,
                    color = ink)
                Text(nativeUiText("menu.options", "เลือกตัวเลือกสินค้า"),
                    color = Color(0xFF6B7D96), fontSize = 13.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (product.specialPrice != null) {
                    Text(nativeUiText("menu.price", "ราคาขาย"), fontWeight = FontWeight.Bold, color = ink)
                    MenuSelectionCard(nativeUiText("menu.regular_price", "ราคาปกติ"), "฿" + product.price.bahtText(),
                        !useSpecialPrice, single = true, onClick = { useSpecialPrice = false })
                    MenuSelectionCard(nativeUiText("menu.special_price", "ราคาพิเศษ"), "฿" + product.specialPrice.bahtText(),
                        useSpecialPrice, single = true, onClick = { useSpecialPrice = true })
                }
                config.groups.forEach { group ->
                    Text(group.name + " · " + if (group.required)
                        nativeUiText("menu.required", "ต้องเลือก") else nativeUiText("menu.optional", "ไม่บังคับ"),
                        fontWeight = FontWeight.Bold, color = ink)
                    group.options.forEach { option ->
                        val active = option.id in selected
                        MenuSelectionCard(option.name,
                            if (option.priceDelta.value != 0L)
                                nativeUiText("menu.price_delta", "เพิ่ม/ลด") + " ฿" +
                                    option.priceDelta.bahtText() else null,
                            active, single = group.selectionType == "single",
                            onClick = { toggle(group, option.id) })
                    }
                }
                if (config.baseRecipe.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(nativeUiText("menu.kitchen_ingredients", "คำสั่งวัตถุดิบสำหรับครัว"),
                            fontWeight = FontWeight.Bold, color = ink)
                        Text(nativeUiText("menu.kitchen_ingredients_hint",
                            "เริ่มต้นไม่เลือก · ติ๊กเฉพาะรายการที่ต้องการให้ครัวเห็น"),
                            color = Color(0xFF6B7D96), fontSize = 12.sp)
                    }
                    config.baseRecipe.forEach { ingredient ->
                        val active = ingredient.id in selectedRecipeIds
                        MenuSelectionCard(ingredient.name,
                            ingredient.quantityPerItem.toString() + " " + ingredient.unit,
                            active, single = false, onClick = {
                                selectedRecipeIds = if (active)
                                    selectedRecipeIds - ingredient.id else selectedRecipeIds + ingredient.id
                            })
                    }
                    Text(
                        nativeUiText("menu.kitchen_ingredients_stock",
                            "รายการที่ติ๊กเป็นข้อความส่งครัวเท่านั้น ไม่เปลี่ยนราคา สูตร หรือสต็อก"),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF6B7D96)
                    )
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(160) },
                    label = { Text(nativeUiText("menu.note", "หมายเหตุ (ถ้ามี)")) },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = blue,
                        focusedLabelColor = blue),
                    modifier = Modifier.fillMaxWidth().testTag("native-item-details-note")
                )
                if (noteTooLong) {
                    Text(nativeUiText("menu.note_too_long",
                        "หมายเหตุรวมวัตถุดิบยาวเกิน 160 ตัวอักษร กรุณาลดรายการหรือข้อความ"),
                        color = MaterialTheme.colorScheme.error)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { if (quantity > 1) quantity -= 1 },
                        colors = ButtonDefaults.textButtonColors(contentColor = blue)) { Text("−") }
                    Text(nativeUiText("menu.quantity", "จำนวน") + " " + quantity)
                    TextButton(onClick = { if (quantity < 999) quantity += 1 },
                        colors = ButtonDefaults.textButtonColors(contentColor = blue)) { Text("+") }
                    Spacer(modifier = Modifier.weight(1f))
                    Text(price?.let { "฿" + it.bahtText() + " / " +
                        nativeUiText("menu.per_item", "ชิ้น") } ?:
                        nativeUiText("menu.choose_option", "กรุณาเลือกตัวเลือก"))
                }
            }
        },
        confirmButton = {
            Button(enabled = price != null && !noteTooLong, onClick = { onConfirm(draft) },
                colors = ButtonDefaults.buttonColors(containerColor = blue)) {
                Text(nativeUiText("menu.add_cart", "เพิ่มลงตะกร้า"))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) {
            Text(nativeUiText("common.cancel", "ยกเลิก"), color = blue)
        } }
    )
}

@Composable
private fun MenuSelectionCard(
    title: String,
    detail: String?,
    active: Boolean,
    single: Boolean,
    onClick: () -> Unit
) {
    val blue = Color(0xFF176DED)
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (active) Color(0xFFEAF3FF) else Color(0xFFF8FAFD))
            .border(1.dp, if (active) Color(0xFF82B7FA) else Color(0xFFE2E9F3),
                RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (single) {
            RadioButton(selected = active, onClick = null,
                colors = RadioButtonDefaults.colors(selectedColor = blue))
        } else {
            Checkbox(checked = active, onCheckedChange = null,
                colors = CheckboxDefaults.colors(checkedColor = blue))
        }
        Column(modifier = Modifier.weight(1f).padding(start = 6.dp)) {
            Text(title, color = Color(0xFF192B47), fontWeight = FontWeight.SemiBold)
            detail?.let {
                Text(it, color = Color(0xFF697D98), fontSize = 12.sp)
            }
        }
    }
}
