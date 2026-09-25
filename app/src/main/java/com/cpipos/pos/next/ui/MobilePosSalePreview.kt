package com.cpipos.pos.next.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
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
import com.cpipos.pos.next.core.offline.DemoSaleLedger
import com.cpipos.pos.next.core.offline.DemoSaleReceipt
import com.cpipos.pos.next.core.offline.DemoReceiptLine
import com.cpipos.pos.next.core.pos.CashTenderInput
import com.cpipos.pos.next.core.pos.Satang
import com.cpipos.pos.next.core.webpos.LiveSaleJournal
import com.cpipos.pos.next.core.webpos.WebPosClient
import com.cpipos.pos.next.core.webpos.WebPosProduct
import com.cpipos.pos.next.core.webpos.WebPosSession
import com.cpipos.pos.next.printing.DemoReceiptPrinter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.Locale
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols

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
    val price: Long,
    val marker: String,
    val accent: Color
)

private val demoMenu = listOf(
    DemoMenuItem("thai-tea", "ชาไทย", "เครื่องดื่ม", 5000L, "🧋", Color(0xFFF3A35A)),
    DemoMenuItem("americano", "อเมริกาโน่", "เครื่องดื่ม", 5500L, "🥤", Color(0xFF876552)),
    DemoMenuItem("fried-rice", "ข้าวผัด", "อาหาร", 8000L, "🍛", Color(0xFFEAAE5D)),
    DemoMenuItem("basil", "ผัดกะเพรา", "อาหาร", 7500L, "🍳", Color(0xFF70A868)),
    DemoMenuItem("water", "น้ำเปล่า", "เครื่องดื่ม", 2000L, "💧", Color(0xFF80BDF9)),
    DemoMenuItem("cake", "เค้กช็อกโกแลต", "ของหวาน", 9500L, "🍰", Color(0xFF9F735F))
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
    onBack: () -> Unit,
    liveClient: WebPosClient? = null,
    liveSession: WebPosSession? = null,
    liveProducts: List<WebPosProduct>? = null,
    liveJournal: LiveSaleJournal? = null
) {
    val cart = remember { mutableStateMapOf<String, Int>() }
    var category by remember { mutableStateOf("ทั้งหมด") }
    var search by remember { mutableStateOf("") }
    var showScannerNotice by remember { mutableStateOf(false) }
    var showCart by remember { mutableStateOf(false) }
    var showMethods by remember { mutableStateOf(false) }
    var selectedMethod by remember { mutableStateOf(PreviewPayment.Cash) }
    var payment by remember { mutableStateOf<PreviewPayment?>(null) }
    var cashInput by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf("") }
    var pendingAction by remember { mutableStateOf<String?>(null) }
    var showCancelBillConfirm by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val demoLedger = remember(context.applicationContext) { DemoSaleLedger(context.applicationContext) }
    var receipt by remember { mutableStateOf<DemoSaleReceipt?>(null) }
    var dailyReceipts by remember { mutableStateOf<List<DemoSaleReceipt>>(emptyList()) }
    var showHistory by remember { mutableStateOf(false) }
    var activeBillId by remember { mutableStateOf(UUID.randomUUID().toString()) }
    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }


    val products = if (liveSession != null) {
        (liveProducts ?: emptyList()).map {
            DemoMenuItem(
                id = it.id, name = it.name, category = it.category.ifBlank { "อื่น ๆ" },
                price = it.price.value, marker = "▣", accent = Color(0xFF79B6EE)
            )
        }
    } else demoMenu
    val categories = if (liveSession == null) saleCategories
        else listOf("ทั้งหมด") + products.map { it.category }.distinct()
    val quantity = cart.values.sum()
    val total = products.sumOf { it.price * (cart[it.id] ?: 0) }
    BackHandler(enabled = !showCart && !showMethods && payment == null && !showScannerNotice && pendingAction == null && !showCancelBillConfirm) {
        onBack()
    }

    val visibleProducts = products.filter { product ->
        (category == "ทั้งหมด" || product.category == category) &&
            (search.isBlank() || product.name.contains(search.trim(), ignoreCase = true) || product.id.contains(search.trim(), ignoreCase = true))
    }

    fun add(id: String) { cart[id] = (cart[id] ?: 0) + 1; notice = "" }
    fun remove(id: String) {
        val next = (cart[id] ?: 0) - 1
        if (next <= 0) cart.remove(id) else cart[id] = next
    }
    fun openPreviewSalesHistory() {
        if (liveSession != null) {
            notice = "ยอดขายจริงบันทึกใน CpIPOS Web • เปิดหน้ารายงาน/ใบเสร็จใน POS Web"
            return
        }
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { demoLedger.today(branchName, counterCode) }
            }.onSuccess { dailyReceipts = it; showHistory = true }
                .onFailure { notice = "เปิดรายการขายทดลองไม่ได้ กรุณาลองอีกครั้ง" }
        }
    }

    if (payment == PreviewPayment.Cash) {
        CashCheckoutScreen(
            due = Satang.of(total),
            cashInput = cashInput,
            saving = saving,
            error = saveError,
            isLiveSale = liveSession != null,
            onInputChange = { cashInput = it; saveError = null },
            onCancel = { if (!saving) { payment = null; showCart = true; saveError = null } },
            onConfirm = {
                if (!saving && quantity > 0 && total > 0L &&
                    CashTenderInput.isSufficient(cashInput, Satang.of(total))
                ) {
                    saving = true
                    saveError = null
                    val snapshot = products.mapNotNull { item ->
                        val count = cart[item.id] ?: 0
                        if (count > 0) DemoReceiptLine(
                            item.id, item.name, count, Satang.of(item.price)
                        ) else null
                    }
                    val billId = activeBillId
                    val paid = CashTenderInput.money(cashInput)
                    scope.launch {
                        try {
                            val saved = if (
                                liveSession != null && liveClient != null && liveJournal != null
                            ) {
                                withContext(Dispatchers.IO) {
                                    check(liveJournal.prepare(liveSession, billId)) {
                                        "บิลนี้เคยส่งแล้ว ตรวจสอบสถานะใน POS Web ก่อน"
                                    }
                                }
                                val paidBill = liveClient.payCash(
                                    session = liveSession,
                                    lines = snapshot,
                                    received = paid,
                                    saleId = billId,
                                    onOrderCreated = { orderId ->
                                        withContext(Dispatchers.IO) {
                                            liveJournal.orderCreated(liveSession, billId, orderId)
                                        }
                                    }
                                )
                                withContext(Dispatchers.IO) {
                                    liveJournal.confirmed(liveSession, billId)
                                }
                                paidBill.receipt
                            } else withContext(Dispatchers.IO) {
                                demoLedger.saveCash(
                                    branchName = branchName, counterCode = counterCode,
                                    modeLabel = mode.title, lines = snapshot,
                                    received = paid, id = billId
                                )
                            }
                            // Clear ONLY after SQLite commits the immutable receipt.
                            cart.clear()
                            activeBillId = UUID.randomUUID().toString()
                            showCart = false
                            showMethods = false
                            payment = null
                            cashInput = ""
                            receipt = saved
                            notice = if (saved.isDemo) "บันทึกบิลทดลองในเครื่องแล้ว: ${saved.billNo}" else "บันทึกยอดขายจริงแล้ว: ${saved.billNo}"
                        } catch (error: Exception) {
                            if (liveSession != null && liveJournal != null) {
                                runCatching {
                                    withContext(Dispatchers.IO) {
                                        if (liveJournal.unresolved(liveSession).any { it.id == billId }) {
                                            liveJournal.review(liveSession, billId)
                                        }
                                    }
                                }
                            }
                            saveError = if (liveSession != null)
                                "ยังไม่ยืนยันจบบิลจริง ห้ามขายซ้ำจนกว่าจะตรวจสอบบิลใน POS Web: " +
                                (error.localizedMessage ?: "เครือข่ายขัดข้อง").take(64)
                            else "บันทึกไม่สำเร็จ ตะกร้ายังอยู่ครบ: " +
                                (error.localizedMessage ?: "กรุณาลองอีกครั้ง").take(80)
                        } finally {
                            saving = false
                        }
                    }
                }
            }
        )
        return
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF5F9FF)) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = 14.dp)
                    .padding(bottom = 112.dp)
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
                        contentDescription = "กลับหน้าเลือกโหมด",
                        modifier = Modifier.size(45.dp).clickable { onBack() }
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
                    // Badge is outside the clipped button so it cannot be cut off.
                    Box(
                        modifier = Modifier.size(56.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .shadow(2.dp, CircleShape)
                                .clip(CircleShape)
                                .background(Color.White)
                                .clickable { showCart = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(R.drawable.ic_nav_cart),
                                contentDescription = "เปิดตะกร้า",
                                modifier = Modifier.size(29.dp)
                            )
                        }
                        if (quantity > 0) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                                    .padding(1.5.dp)
                                    .clip(CircleShape)
                                    .background(saleBlue)
                                    .clickable { showCart = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    if (quantity > 99) "99+" else quantity.toString(),
                                    color = Color.White,
                                    fontSize = if (quantity > 9) 8.sp else 10.sp,
                                    lineHeight = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (liveSession == null) "พรีวิว UI • ไม่ใช่ยอดขายจริง"
                        else "เชื่อมต่อ POS จริง • ${liveSession.cashierName}",
                        color = if (liveSession == null) saleMuted else Color(0xFF16824D),
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        if (liveSession == null) "ดูยอดขายทดลอง ›" else "ยอดขายใน POS Web ›",
                        modifier = Modifier.clickable { openPreviewSalesHistory() },
                        color = saleBlue,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
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
                    items(categories) { group ->
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
                                onAdd = { add(product.id) }
                            )
                        }
                    }
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
                        .padding(bottom = 112.dp)
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
        PreviewCartBottomSheet(
            products = products,
            cart = cart,
            quantity = quantity,
            total = total,
            onDismiss = { showCart = false },
            onAdd = { add(it) },
            onRemove = { remove(it) },
            onDelete = { cart.remove(it) },
            onCancelBill = { showCancelBillConfirm = true },
            onCheckout = {
                if (quantity > 0) {
                    selectedMethod = PreviewPayment.Cash
                    showCart = false
                    showMethods = true
                }
            },
            onFeatureClick = { pendingAction = it }
        )
    }

    if (showCancelBillConfirm) {
        AlertDialog(
            onDismissRequest = { showCancelBillConfirm = false },
            title = {
                Text("ยกเลิกบิล", color = saleInk, fontWeight = FontWeight.Bold)
            },
            text = {
                Text("ต้องการยกเลิกบิลจำลองนี้และลบรายการสินค้าในตะกร้าทั้งหมดใช่หรือไม่")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        cart.clear()
                        showCart = false
                        showCancelBillConfirm = false
                        activeBillId = UUID.randomUUID().toString()
                        notice = "ยกเลิกตะกร้าทดลองแล้ว ไม่ได้บันทึกธุรกรรม"
                    }
                ) {
                    Text("ยืนยันยกเลิกบิล", color = Color(0xFFE34D58), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelBillConfirm = false }) {
                    Text("กลับไปตะกร้า")
                }
            }
        )
    }

    if (pendingAction != null) {
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            title = { Text(pendingAction ?: "") },
            text = { Text("อยู่ระหว่างพัฒนาใน POS Preview ยังไม่เชื่อมต่อระบบจริง") },
            confirmButton = {
                TextButton(onClick = { pendingAction = null }) { Text("ตกลง") }
            }
        )
    }

    if (showMethods) {
        PreviewPaymentMethodSheet(
            total = total,
            selectedMethod = selectedMethod,
            onSelectMethod = { selectedMethod = it },
            onDismiss = { showMethods = false; showCart = true },
            onConfirm = {
                if (total > 0L) {
                    showMethods = false
                    showCart = false
                    cashInput = ""
                    payment = selectedMethod
                }
            }
        )
    }


    if (payment == PreviewPayment.Transfer) {
        AlertDialog(
            onDismissRequest = { payment = null },
            title = { Text("โอนเงิน (รอตรวจสอบยอด)", color = saleInk) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("ยอดชำระ ฿" + amount(total), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1C995A))
                    Text("ยังไม่มีการตรวจสอบยอดโอนจากธนาคารใน Native POS จึงยังไม่สามารถจบบิลด้วยวิธีนี้ได้", color = saleMuted)
                    Text("เลือกกลับไปตะกร้า หรือเปลี่ยนเป็นเงินสดเพื่อทำรายการต่อ", fontSize = 12.sp, color = saleMuted)
                }
            },
            confirmButton = { TextButton(onClick = { payment = null; showCart = true }) { Text("กลับตะกร้า") } },
            dismissButton = { TextButton(onClick = { payment = null }) { Text("ยกเลิก") } }
        )
    }
    if (showHistory) {
        DemoDailySalesSheet(
            receipts = dailyReceipts,
            onOpenReceipt = { selected -> showHistory = false; receipt = selected },
            onDismiss = { showHistory = false }
        )
    }
    receipt?.let { saved ->
        SavedDemoReceiptSheet(
            receipt = saved,
            onPrint = {
                runCatching { DemoReceiptPrinter.print(context, saved) }
                    .onFailure { notice = "เปิดระบบพิมพ์ไม่ได้ กรุณาติดตั้งบริการเครื่องพิมพ์ Android" }
            },
            onDone = { receipt = null }
        )
    }

}

/**
 * Payment selection follows the approved second reference: cart remains visible
 * beneath a full-width sheet, large two-column tender cards and a prominent CTA.
 * This remains UI-only; confirming a method opens the existing mock cash/transfer flow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PreviewPaymentMethodSheet(
    total: Long,
    selectedMethod: PreviewPayment,
    onSelectMethod: (PreviewPayment) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
        containerColor = Color.White,
        scrimColor = Color(0x990C1B32),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 13.dp, bottom = 7.dp)
                    .size(width = 42.dp, height = 5.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFAEBED3))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = screenHeight * 0.87f)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 17.dp)
                .padding(bottom = 20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 15.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ชำระเงิน",
                    modifier = Modifier.weight(1f),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = saleInk
                )
                Box(
                    modifier = Modifier
                        .size(43.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE8F2FF))
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Text("×", fontSize = 29.sp, lineHeight = 30.sp, color = Color(0xFF1C477F))
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(21.dp))
                    .background(Color(0xFFEEF5FF))
                    .padding(horizontal = 15.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "ยอดชำระ",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF536783),
                    modifier = Modifier.weight(1f)
                )
                Text(
                    amount(total) + " บาท",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF1260DF),
                    maxLines = 1
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                PaymentMethodTile(
                    title = "เงินสด",
                    iconRes = R.drawable.ic_payment_cash,
                    selected = selectedMethod == PreviewPayment.Cash,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelectMethod(PreviewPayment.Cash) }
                )
                PaymentMethodTile(
                    title = "โอนเงิน",
                    iconRes = R.drawable.ic_payment_transfer,
                    selected = selectedMethod == PreviewPayment.Transfer,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelectMethod(PreviewPayment.Transfer) }
                )
            }
            Spacer(modifier = Modifier.height(15.dp))
            Button(
                onClick = onConfirm,
                enabled = total > 0L,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(19.dp),
                colors = ButtonDefaults.buttonColors(containerColor = saleBlue),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_payment_card),
                    contentDescription = null,
                    modifier = Modifier.size(23.dp)
                )
                Spacer(modifier = Modifier.width(9.dp))
                Text("ยืนยันการชำระเงิน", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(19.dp),
                border = BorderStroke(1.3.dp, Color(0xFFCCDEF8)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF1F4E92))
            ) {
                Text("ยกเลิก", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "โหมดทดลอง UI เท่านั้น • ไม่มีการรับเงินจริง",
                color = saleMuted,
                fontSize = 10.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
private fun PaymentMethodTile(
    title: String,
    iconRes: Int,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(19.dp)
    Box(
        modifier = modifier
            .height(148.dp)
            .clip(shape)
            .background(if (selected) Color(0xFFEDF5FF) else Color.White)
            .border(
                if (selected) 2.dp else 1.dp,
                if (selected) saleBlue else Color(0xFFDCE8F9),
                shape
            )
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(11.dp)
                .size(26.dp)
                .clip(CircleShape)
                .background(if (selected) Color(0xFF176EF1) else Color.White)
                .border(
                    if (selected) 0.dp else 2.dp,
                    if (selected) Color.Transparent else Color(0xFFCADAF1),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Text("✓", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(top = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(69.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                title,
                fontSize = 21.sp,
                fontWeight = FontWeight.ExtraBold,
                color = saleInk
            )
        }
    }
}


/**
 * Tap anywhere on a menu card to add one unit; quantity is read-only here.
 * All decrement and delete actions are contained in the cart sheet.
 */
@Composable
private fun DemoProductCard(
    product: DemoMenuItem,
    quantity: Int,
    onAdd: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(166.dp)
            .clickable(onClick = onAdd),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(5.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(15.dp))
                    .background(Brush.verticalGradient(
                        listOf(Color.White, product.accent.copy(alpha = 0.17f))
                    )),
                contentAlignment = Alignment.Center
            ) {
                // Demo illustration until real catalog product photos are connected.
                Text(product.marker, fontSize = 57.sp)
                if (quantity > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(27.dp)
                            .clip(CircleShape)
                            .background(saleBlue),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            quantity.toString(),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 5.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
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
                    Text(
                        "฿" + amount(product.price),
                        color = saleInk,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                }
                if (quantity > 0) {
                    Text(
                        "×" + quantity,
                        color = saleBlue,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PreviewCartBottomSheet(
    products: List<DemoMenuItem>,
    cart: Map<String, Int>,
    quantity: Int,
    total: Long,
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    onDelete: (String) -> Unit,
    onCancelBill: () -> Unit,
    onCheckout: () -> Unit,
    onFeatureClick: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val cartProducts = products.filter { (cart[it.id] ?: 0) > 0 }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
        containerColor = Color(0xFFFAFCFF),
        scrimColor = Color(0x990C1B32),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 7.dp)
                    .size(width = 42.dp, height = 5.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFB8C8DD))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = screenHeight * 0.86f)
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "ตะกร้าสินค้า",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = saleInk
                    )
                    Text(
                        quantity.toString() + " รายการ",
                        color = saleMuted,
                        fontSize = 16.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .size(39.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE8F2FF))
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("×", color = saleInk, fontSize = 27.sp, lineHeight = 30.sp)
                }
            }

            // Scroll only the product rows so checkout remains reachable.
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = screenHeight * 0.37f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 6.dp)
            ) {
                if (cartProducts.isEmpty()) {
                    item {
                        Text(
                            "ยังไม่มีสินค้าในตะกร้า",
                            color = saleMuted,
                            modifier = Modifier.padding(vertical = 26.dp)
                        )
                    }
                }
                items(cartProducts, key = { it.id }) { product ->
                    val count = cart[product.id] ?: 0
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(2.dp, RoundedCornerShape(15.dp))
                            .clip(RoundedCornerShape(15.dp))
                            .background(Color.White)
                            .padding(horizontal = 8.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(product.accent.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(product.marker, fontSize = 30.sp)
                        }
                        Spacer(modifier = Modifier.width(7.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                product.name,
                                fontSize = 13.sp,
                                lineHeight = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = saleInk,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "฿" + amount(product.price) + " / ชิ้น",
                                color = saleMuted,
                                fontSize = 10.sp,
                                maxLines = 1
                            )
                            Text(
                                "฿" + amount(product.price * count),
                                fontSize = 14.sp,
                                color = saleInk,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Color(0xFFF2F7FF))
                                .padding(horizontal = 3.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CartQuantityButton(label = "−", onClick = { onRemove(product.id) })
                            Text(
                                count.toString(),
                                modifier = Modifier.padding(horizontal = 5.dp),
                                fontWeight = FontWeight.Bold,
                                color = saleInk,
                                fontSize = 13.sp
                            )
                            CartQuantityButton(label = "+", onClick = { onAdd(product.id) })
                        }
                        Spacer(modifier = Modifier.width(5.dp))
                        Box(
                            modifier = Modifier
                                .size(35.dp)
                                .clip(CircleShape)
                                .clickable { onDelete(product.id) },
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(R.drawable.ic_cart_delete_red),
                                contentDescription = "ลบรายการ " + product.name,
                                modifier = Modifier.size(21.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(9.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(15.dp))
                    .background(Color(0xFFEAF4FF))
                    .clickable { onFeatureClick("ส่วนลด") }
                    .padding(horizontal = 14.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("◇", color = saleBlue, fontSize = 24.sp)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    "ส่วนลด",
                    color = saleInk,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text("›", color = saleMuted, fontSize = 27.sp)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .padding(horizontal = 14.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "ยอดรวม",
                    color = saleInk,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "฿" + amount(total),
                    color = Color(0xFF126BE5),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 23.sp
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                CartSecondaryButton(
                    title = "พิมพ์บิล",
                    modifier = Modifier.weight(1f),
                    onClick = { onFeatureClick("พิมพ์บิล") }
                )
                CartSecondaryButton(
                    title = "ยกเลิกบิล",
                    modifier = Modifier.weight(1f),
                    onClick = onCancelBill,
                    destructive = true
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = onCheckout,
                enabled = quantity > 0,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(17.dp),
                colors = ButtonDefaults.buttonColors(containerColor = saleBlue)
            ) {
                Text("▣  ชำระเงิน", fontWeight = FontWeight.Bold, fontSize = 19.sp)
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun CartQuantityButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(27.dp)
            .clip(CircleShape)
            .background(Color(0xFFE1EEFF))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = Color(0xFF176DEE),
            fontSize = 19.sp,
            lineHeight = 21.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun CartSecondaryButton(
    title: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    destructive: Boolean = false
) {
    Box(
        modifier = modifier
            .height(47.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (destructive) Color(0xFFFFEDF0) else Color(0xFFEAF4FF))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            title,
            color = if (destructive) Color(0xFFD43D4C) else saleInk,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            maxLines = 1
        )
    }
}

private fun amount(value: Long): String = DecimalFormat("#,##0.##",
    DecimalFormatSymbols(Locale.US)
).format(value.toBigDecimal().movePointLeft(2))

@Preview(name = "CpIPOS Takeaway Grid", showBackground = true, widthDp = 390, heightDp = 844, locale = "th")
@Composable
private fun TakeawaySaleUiPreview() {
    MaterialTheme {
        MobilePosSalePreview(MockSaleMode.Takeaway, "ถนนเพชรบุรี", "POS-COUNTER-01", onBack = {})
    }
}
