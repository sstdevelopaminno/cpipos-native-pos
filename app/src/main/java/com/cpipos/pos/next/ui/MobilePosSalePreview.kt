package com.cpipos.pos.next.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.cpipos.pos.next.R
import java.util.Locale

/**
 * Reference-inspired Takeaway sales UI. Demo products and all transactions
 * stay entirely in Compose memory; no real sale, banking or stock operations.
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
    DemoMenuItem("thai-tea", "ชาไทย", "เครื่องดื่ม", 50, "🧋", Color(0xFFF3A35A)),
    DemoMenuItem("americano", "อเมริกาโน่", "เครื่องดื่ม", 55, "🥤", Color(0xFF876552)),
    DemoMenuItem("fried-rice", "ข้าวผัด", "อาหาร", 80, "🍛", Color(0xFFEAAE5D)),
    DemoMenuItem("basil", "ผัดกะเพรา", "อาหาร", 75, "🍳", Color(0xFF70A868)),
    DemoMenuItem("water", "น้ำเปล่า", "เครื่องดื่ม", 20, "💧", Color(0xFF80BDF9)),
    DemoMenuItem("cake", "เค้กช็อกโกแลต", "ของหวาน", 95, "🍰", Color(0xFF9F735F))
)
private val saleBlue = Color(0xFF1879F3)
private val saleInk = Color(0xFF152544)
private val saleMuted = Color(0xFF7A8BA3)
private val saleCategories = listOf("ทั้งหมด", "เครื่องดื่ม", "อาหาร", "ของหวาน", "โปรโมชัน")

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
    var search by remember { mutableStateOf("") }
    var showScannerNotice by remember { mutableStateOf(false) }
    var showCart by remember { mutableStateOf(false) }
    var showMethods by remember { mutableStateOf(false) }
    var payment by remember { mutableStateOf<PreviewPayment?>(null) }
    var cashInput by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf("") }

    val quantity = cart.values.sum()
    val total = demoMenu.sumOf { it.price * (cart[it.id] ?: 0) }
    val visibleProducts = demoMenu.filter { product ->
        (category == "ทั้งหมด" || product.category == category) &&
            (search.isBlank() || product.name.contains(search.trim(), ignoreCase = true) || product.id.contains(search.trim(), ignoreCase = true))
    }

    fun add(id: String) { cart[id] = (cart[id] ?: 0) + 1; notice = "" }
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

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF5F9FF)) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = 14.dp)
                    .padding(bottom = 179.dp)
            ) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(76.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(R.drawable.cpipos_logo_symbol),
                        contentDescription = "CpIPOS",
                        modifier = Modifier.size(45.dp)
                    )
                    Text("CpIPOS", fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, color = saleInk)
                    Spacer(modifier = Modifier.width(9.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(mode.title, fontSize = 21.sp, fontWeight = FontWeight.Bold, color = saleInk, maxLines = 1)
                        Text(
                            if (mode == MockSaleMode.Takeaway) "แคชเชียร์ขายกลับบ้าน" else "แคชเชียร์ขายนั่งโต๊ะ",
                            fontSize = 12.sp,
                            color = saleMuted,
                            maxLines = 1
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(51.dp)
                            .shadow(2.dp, CircleShape)
                            .clip(CircleShape)
                            .background(Color.White)
                            .clickable { showCart = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_nav_cart),
                            contentDescription = "เปิดตะกร้า",
                            modifier = Modifier.size(31.dp)
                        )
                        if (quantity > 0) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(19.dp)
                                    .clip(CircleShape)
                                    .background(saleBlue),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    quantity.toString(),
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    lineHeight = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    modifier = Modifier.fillMaxWidth().height(57.dp),
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, color = saleInk),
                    placeholder = {
                        Text("ค้นหาสินค้า หรือสแกนบาร์โค้ด", color = saleMuted, fontSize = 14.sp, maxLines = 1)
                    },
                    leadingIcon = {
                        Text("⌕", fontSize = 33.sp, color = Color(0xFF587392))
                    },
                    trailingIcon = {
                        Text(
                            "▥",
                            fontSize = 28.sp,
                            color = saleBlue,
                            modifier = Modifier
                                .clip(RoundedCornerShape(9.dp))
                                .clickable { showScannerNotice = true }
                                .padding(horizontal = 7.dp)
                        )
                    },
                    shape = RoundedCornerShape(19.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = Color(0xFFBFD9FA),
                        unfocusedBorderColor = Color(0xFFE3ECFA)
                    )
                )
                Spacer(modifier = Modifier.height(9.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    contentPadding = PaddingValues(vertical = 2.dp)
                ) {
                    items(saleCategories) { group ->
                        val selected = category == group
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (selected) Brush.horizontalGradient(
                                        listOf(Color(0xFF3394FB), Color(0xFF146DED))
                                    ) else Brush.horizontalGradient(
                                        listOf(Color(0xFFF6F9FF), Color(0xFFF0F5FE))
                                    )
                                )
                                .clickable { category = group }
                                .padding(horizontal = 15.dp, vertical = 11.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = group,
                                color = if (selected) Color.White else Color(0xFF5D728F),
                                fontSize = 13.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))

                if (visibleProducts.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text("ไม่พบสินค้าที่ค้นหา", color = saleMuted)
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                        verticalArrangement = Arrangement.spacedBy(9.dp),
                        contentPadding = PaddingValues(bottom = 8.dp)
                    ) {
                        items(visibleProducts, key = { it.id }) { product ->
                            DemoProductCard(
                                product = product,
                                quantity = cart[product.id] ?: 0,
                                onAdd = { add(product.id) },
                                onRemove = { remove(product.id) }
                            )
                        }
                    }
                }
            }

            // Blue cart total remains immediately above the shared raised nav.
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 112.dp)
                    .fillMaxWidth()
                    .height(60.dp)
                    .clip(RoundedCornerShape(42.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFF3295FD), Color(0xFF0C6BEC))
                        )
                    )
                    .clickable { showCart = true }
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🛒", fontSize = 22.sp, color = Color.White)
                Spacer(modifier = Modifier.width(7.dp))
                Text(quantity.toString() + " รายการ", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.width(12.dp))
                Box(modifier = Modifier.width(1.dp).height(24.dp).background(Color(0x99FFFFFF)))
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "ยอดรวม ฿" + amount(total),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(40.dp))
                        .background(Color(0xFF0868E9))
                        .clickable { showCart = true }
                        .padding(horizontal = 11.dp, vertical = 12.dp)
                ) {
                    Text("ดูตะกร้า ›", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
            HomeBottomMenu(modifier = Modifier.align(Alignment.BottomCenter))
            if (notice.isNotEmpty()) {
                Text(
                    notice,
                    color = Color(0xFF25834C),
                    fontSize = 10.sp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 178.dp)
                )
            }
        }
    }
    if (showScannerNotice) {
        AlertDialog(
            onDismissRequest = { showScannerNotice = false },
            title = { Text("สแกนบาร์โค้ด") },
            text = { Text("ฟังก์ชันกล้องสแกนบาร์โค้ดยังไม่ได้เชื่อมในโหมดทดสอบนี้ คุณสามารถค้นหาชื่อสินค้าได้") },
            confirmButton = { TextButton(onClick = { showScannerNotice = false }) { Text("ตกลง") } }
        )
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
private fun DemoProductCard(
    product: DemoMenuItem,
    quantity: Int,
    onAdd: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().height(166.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(5.dp)) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f)
                    .clip(RoundedCornerShape(15.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.White, product.accent.copy(alpha = 0.17f))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Illustrative demo thumbnail; replace with product image from catalog.
                Text(product.marker, fontSize = 57.sp)
                if (quantity > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(5.dp)
                            .size(23.dp)
                            .clip(CircleShape)
                            .background(saleBlue),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(quantity.toString(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 4.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        product.name,
                        color = saleInk,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text("฿" + amount(product.price), color = saleInk, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
                if (quantity > 0) {
                    TextButton(
                        onClick = onRemove,
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.size(25.dp)
                    ) {
                        Text("−", color = saleBlue, fontSize = 22.sp)
                    }
                }
                Box(
                    modifier = Modifier
                        .padding(end = 2.dp, bottom = 1.dp)
                        .size(35.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE4F0FF))
                        .clickable { onAdd() },
                    contentAlignment = Alignment.Center
                ) { Text("+", color = saleBlue, fontSize = 28.sp, lineHeight = 29.sp) }
            }
        }
    }
}

private fun amount(value: Int): String = String.format(Locale.US, "%,d", value)

@Preview(name = "CpIPOS Takeaway Grid", showBackground = true, widthDp = 390, heightDp = 844, locale = "th")
@Composable
private fun TakeawaySaleUiPreview() {
    MaterialTheme {
        MobilePosSalePreview(MockSaleMode.Takeaway, "ถนนเพชรบุรี", "POS-COUNTER-01", onBack = {})
    }
}
