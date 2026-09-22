package com.cpipos.pos.next.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.util.Locale

/**
 * Local-only mobile sales UI preview. The menu, cart, checkout and payments
 * are mock data/state; no network calls, POS session, payment or stock writes.
 */
internal enum class MockSaleMode(val title: String) {
    Takeaway("โหมดกลับบ้าน"),
    DineIn("โหมดนั่งโต๊ะ")
}

private data class DemoMenuItem(
    val id: String,
    val name: String,
    val category: String,
    val price: Int,
    val marker: String,
    val accent: Color
)

private val demoMenu = listOf(
    DemoMenuItem("basil", "ข้าวกะเพราหมูสับ", "อาหารจานเดียว", 65, "กะ", Color(0xFF288B78)),
    DemoMenuItem("fried-rice", "ข้าวผัดกุ้ง", "อาหารจานเดียว", 75, "ผัด", Color(0xFFF5A444)),
    DemoMenuItem("pad-thai", "ผัดไทยกุ้งสด", "อาหารจานเดียว", 80, "ไทย", Color(0xFFDF876E)),
    DemoMenuItem("tom-yum", "ต้มยำกุ้ง", "อาหารจานเดียว", 120, "ต้ม", Color(0xFFDA6559)),
    DemoMenuItem("thai-tea", "ชาไทย", "เครื่องดื่ม", 45, "ชา", Color(0xFFD68B31)),
    DemoMenuItem("americano", "อเมริกาโน่", "เครื่องดื่ม", 60, "กา", Color(0xFF746354)),
    DemoMenuItem("water", "น้ำเปล่า", "เครื่องดื่ม", 15, "น้ำ", Color(0xFF3986CF)),
    DemoMenuItem("fries", "เฟรนช์ฟรายส์", "ของทานเล่น", 69, "FF", Color(0xFFE0A13F))
)
private val saleBlue = Color(0xFF1879F3)
private val saleInk = Color(0xFF152544)
private val saleMuted = Color(0xFF7A8BA3)

private enum class PreviewPayment { Cash, Transfer }

@Composable
internal fun MobilePosSalePreview(
    mode: MockSaleMode,
    branchName: String,
    counterCode: String,
    onBack: () -> Unit
) {
    val cart = remember { mutableStateMapOf<String, Int>() }
    var category by remember { mutableStateOf("ทั้งหมด") }
    var showCart by remember { mutableStateOf(false) }
    var showMethods by remember { mutableStateOf(false) }
    var payment by remember { mutableStateOf<PreviewPayment?>(null) }
    var cashInput by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf("") }

    val quantity = cart.values.sum()
    val total = demoMenu.sumOf { it.price * (cart[it.id] ?: 0) }

    fun add(id: String) { cart[id] = (cart[id] ?: 0) + 1 }
    fun remove(id: String) {
        val next = (cart[id] ?: 0) - 1
        if (next <= 0) cart.remove(id) else cart[id] = next
    }
    fun clearDemo() {
        cart.clear()
        showCart = false
        showMethods = false
        payment = null
        cashInput = ""
        notice = "จบบิลจำลองแล้ว ไม่มีการรับเงินจริงหรือบันทึกยอดขาย"
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF6F9FF)) {
        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) {
                    Text("‹ กลับ", color = saleBlue, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.weight(1f))
                Text("POS PREVIEW", color = saleBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Text(mode.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = saleInk)
            Text(branchName + " · " + counterCode, fontSize = 12.sp, color = saleMuted)
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFFE5F1FF)).clickable { showCart = true }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🛒", fontSize = 22.sp)
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("รายการที่เลือก " + quantity + " ชิ้น", fontWeight = FontWeight.Bold, color = saleInk)
                    Text("แตะเพื่อดูตะกร้าและปรับจำนวน", fontSize = 11.sp, color = saleMuted)
                }
                Text("฿" + amount(total) + "  ›", fontWeight = FontWeight.Bold, color = saleBlue)
            }
            if (notice.isNotEmpty()) {
                Text(notice, color = Color(0xFF25834C), fontSize = 11.sp, modifier = Modifier.padding(top = 7.dp))
            }
            Spacer(modifier = Modifier.height(15.dp))
            Text("เมนูอาหารและสินค้า", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = saleInk)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf("ทั้งหมด", "อาหารจานเดียว", "เครื่องดื่ม", "ของทานเล่น")) { group ->
                    FilterChip(selected = category == group, onClick = { category = group }, label = { Text(group) })
                }
            }
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                items(demoMenu.filter { category == "ทั้งหมด" || it.category == category }, key = { it.id }) { product ->
                    DemoProductCard(
                        product = product,
                        quantity = cart[product.id] ?: 0,
                        onAdd = { add(product.id) },
                        onRemove = { remove(product.id) }
                    )
                }
                item { Spacer(modifier = Modifier.height(8.dp)) }
            }
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 5.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(quantity.toString() + " รายการ", fontSize = 12.sp, color = saleMuted)
                        Text("฿" + amount(total), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1C995A))
                    }
                    Button(
                        enabled = quantity > 0,
                        onClick = { showMethods = true },
                        colors = ButtonDefaults.buttonColors(containerColor = saleBlue)
                    ) { Text("ชำระเงิน ›") }
                }
            }
        }
    }

    if (showCart) {
        AlertDialog(
            onDismissRequest = { showCart = false },
            title = { Text("ตะกร้าสินค้า (" + quantity + ")", color = saleInk) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    if (quantity == 0) Text("ยังไม่มีสินค้าในตะกร้า", color = saleMuted)
                    demoMenu.filter { (cart[it.id] ?: 0) > 0 }.forEach { product ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(product.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text("฿" + amount(product.price * (cart[product.id] ?: 0)), fontSize = 12.sp, color = saleMuted)
                            }
                            TextButton(onClick = { remove(product.id) }) { Text("−") }
                            Text((cart[product.id] ?: 0).toString(), fontWeight = FontWeight.Bold)
                            TextButton(onClick = { add(product.id) }) { Text("+") }
                        }
                    }
                    HorizontalDivider()
                    Text("รวม ฿" + amount(total), fontWeight = FontWeight.Bold, color = Color(0xFF1C995A))
                }
            },
            confirmButton = {
                TextButton(
                    enabled = quantity > 0,
                    onClick = { showCart = false; showMethods = true }
                ) { Text("ชำระเงิน") }
            },
            dismissButton = { TextButton(onClick = { showCart = false }) { Text("ปิด") } }
        )
    }

    if (showMethods) {
        AlertDialog(
            onDismissRequest = { showMethods = false },
            title = { Text("เลือกวิธีชำระเงิน", color = saleInk) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("ยอดชำระ ฿" + amount(total), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1C995A))
                    Text("ทดสอบ UI เท่านั้น ไม่ได้สร้างธุรกรรม", fontSize = 12.sp, color = saleMuted)
                    Button(
                        onClick = { showMethods = false; cashInput = ""; payment = PreviewPayment.Cash },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = saleBlue)
                    ) { Text("เงินสด") }
                    OutlinedButton(
                        onClick = { showMethods = false; payment = PreviewPayment.Transfer },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("โอนเงิน") }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showMethods = false }) { Text("ยกเลิก") } }
        )
    }

    if (payment == PreviewPayment.Cash) {
        val received = cashInput.toIntOrNull() ?: 0
        Dialog(onDismissRequest = { payment = null }) {
            Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = Color.White) {
                Column(modifier = Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("รับชำระเงินสด", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = saleInk)
                    Text("ยอดที่ต้องชำระ", fontSize = 12.sp, color = saleMuted)
                    Text("฿" + amount(total), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1C995A))
                    Spacer(modifier = Modifier.height(9.dp))
                    Text("รับเงิน ฿" + amount(received), fontSize = 19.sp, color = saleInk)
                    Text(
                        if (received >= total) "เงินทอน ฿" + amount(received - total)
                        else "เงินไม่พอ ฿" + amount(total - received),
                        color = if (received >= total) Color(0xFF1C995A) else Color(0xFFA8753C),
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    listOf(
                        listOf("1", "2", "3"), listOf("4", "5", "6"),
                        listOf("7", "8", "9"), listOf("ลบ", "0", "⌫")
                    ).forEach { digitRow ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            digitRow.forEach { key ->
                                OutlinedButton(
                                    onClick = {
                                        cashInput = when (key) {
                                            "ลบ" -> ""
                                            "⌫" -> cashInput.dropLast(1)
                                            else -> (cashInput + key).trimStart('0').take(7)
                                        }
                                    },
                                    modifier = Modifier.weight(1f).height(43.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                                ) { Text(key, fontSize = 17.sp, fontWeight = FontWeight.Bold) }
                            }
                        }
                        Spacer(modifier = Modifier.height(7.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        OutlinedButton(onClick = { payment = null }, modifier = Modifier.weight(1f)) {
                            Text("ยกเลิก")
                        }
                        Button(
                            onClick = { clearDemo() },
                            enabled = received >= total && total > 0,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1C995A))
                        ) { Text("จบบิลจำลอง", fontSize = 12.sp) }
                    }
                }
            }
        }
    }

    if (payment == PreviewPayment.Transfer) {
        AlertDialog(
            onDismissRequest = { payment = null },
            title = { Text("โอนเงิน (พรีวิว)", color = saleInk) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("ยอดชำระ ฿" + amount(total), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1C995A))
                    Text("ยังไม่มี QR รับเงินจริงหรือการตรวจสอบยอดโอนจากธนาคาร", color = saleMuted)
                    Text("ปุ่มด้านล่างจำลองการจบบิลบนหน้าจอเท่านั้น", fontSize = 12.sp, color = saleMuted)
                }
            },
            confirmButton = { TextButton(onClick = { clearDemo() }) { Text("จบบิลจำลอง") } },
            dismissButton = { TextButton(onClick = { payment = null }) { Text("ยกเลิก") } }
        )
    }
}

@Composable
private fun DemoProductCard(product: DemoMenuItem, quantity: Int, onAdd: () -> Unit, onRemove: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(58.dp).clip(RoundedCornerShape(14.dp))
                    .background(product.accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(product.marker, color = product.accent, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(product.name, fontWeight = FontWeight.Bold, color = saleInk, fontSize = 14.sp, maxLines = 1)
                Text(product.category, color = saleMuted, fontSize = 11.sp)
                Text("฿" + amount(product.price), color = Color(0xFF168C56), fontWeight = FontWeight.Bold)
            }
            if (quantity > 0) {
                TextButton(onClick = onRemove) { Text("−", fontSize = 19.sp) }
                Text(quantity.toString(), fontWeight = FontWeight.Bold, color = saleInk)
            }
            TextButton(onClick = onAdd) { Text("+", fontSize = 23.sp, color = saleBlue) }
        }
    }
}

private fun amount(value: Int): String = String.format(Locale.US, "%,d", value)

@Preview(name = "CpIPOS Takeaway Sale Mock", showBackground = true, widthDp = 390, heightDp = 844, locale = "th")
@Composable
private fun TakeawaySaleUiPreview() {
    MaterialTheme {
        MobilePosSalePreview(MockSaleMode.Takeaway, "ถนนเพชรบุรี", "POS-COUNTER-01", onBack = {})
    }
}
