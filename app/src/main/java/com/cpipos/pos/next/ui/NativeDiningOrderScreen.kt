package com.cpipos.pos.next.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cpipos.pos.next.R
import com.cpipos.pos.next.core.pos.Satang
import com.cpipos.pos.next.core.webpos.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

private val dineBlue = Color(0xFF176DED)
private val dineInk = Color(0xFF152544)

/** Unsaved items belong to one table; separate from the Takeaway cart. */
internal class DiningWorkspace {
    val drafts = mutableStateMapOf<String, DiningDraft>()
}
internal class DiningDraft {
    val items = mutableStateMapOf<String, Int>()
    var uncertain by mutableStateOf(false)
    var busy by mutableStateOf(false)
    var pendingQuantities by mutableStateOf<Map<String, Int>>(emptyMap())
    var requestId by mutableStateOf<String?>(null)
}
private fun billQuantities(bill: WebDiningBill): Map<String, Int> =
    bill.lines.groupBy { it.productId }.mapValues { (_, lines) -> lines.sumOf { it.quantity } }

@Composable
internal fun NativeDiningOrderScreen(
    client: WebPosClient, session: WebPosSession, table: WebDiningTable,
    products: List<WebPosProduct>, draft: DiningDraft,
    onTables: () -> Unit, onMode: () -> Unit, onMainMenu: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var bill by remember(session.id, table.id) { mutableStateOf<WebDiningBill?>(null) }
    var loading by remember(session.id, table.id) { mutableStateOf(true) }
    var error by remember(session.id, table.id) { mutableStateOf<String?>(null) }
    var stale by remember(session.id, table.id) { mutableStateOf(false) }
    var cartOpen by remember(session.id, table.id) { mutableStateOf(false) }
    var showExisting by remember(session.id, table.id) { mutableStateOf(false) }
    var showClear by remember(session.id, table.id) { mutableStateOf(false) }
    var category by remember(session.id, table.id) { mutableStateOf("ทั้งหมด") }
    var search by remember(session.id, table.id) { mutableStateOf("") }
    var feedback by remember(session.id, table.id) { mutableStateOf("idle") }

    suspend fun refresh(accept: Boolean) {
        try {
            val remote = client.diningBill(session, table.id)
            if (bill != null && remote.fingerprint() != bill?.fingerprint() &&
                draft.items.isNotEmpty() && !accept) {
                stale = true
                error = "บิลบน POS หลักเปลี่ยนแล้ว ต้องรีเฟรชก่อนบันทึกรายการ"
            } else {
                bill = remote
                stale = false
                error = null
            }
            if (!remote.editable) {
                draft.uncertain = true
                if (remote.paid || remote.sessionStatus in listOf("closed", "cancelled")) {
                    draft.items.clear()
                    draft.requestId = null
                    onTables()
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = "โหลดบิลโต๊ะไม่ได้ โปรดตรวจสอบการเชื่อมต่อ"
        } finally { loading = false }
    }
    LaunchedEffect(client, session.id, table.id) {
        refresh(true)
        while (true) {
            delay(6000L)
            if (!draft.busy) refresh(false)
        }
    }
    BackHandler(enabled = !draft.busy) { onTables() }

    val entries = products.map {
        DemoMenuItem(it.id, it.name, it.category.ifBlank { "อื่น ๆ" },
            it.price.value, it.name.firstOrNull()?.toString()
                ?.uppercase(Locale.forLanguageTag("th-TH")) ?: "?",
            Color(0xFF79B6EE), it.imageUrl)
    }
    val categories = listOf("ทั้งหมด") + entries.map { it.category }.distinct()
    val shown = entries.filter {
        (category == "ทั้งหมด" || it.category == category) &&
            (search.isBlank() || it.name.contains(search.trim(), ignoreCase = true))
    }
    val count = draft.items.values.sum()
    val subtotal = entries.sumOf { it.price * (draft.items[it.id] ?: 0) }
    val savedTotal = bill?.lines.orEmpty().sumOf { it.unitPrice.value * it.quantity }
    val existing = bill
    val custom = existing?.lines.orEmpty().any { item ->
        val product = products.find { it.id == item.productId }
        item.quantity <= 0 || item.hasCustomMetadata || !item.notes.isNullOrBlank() ||
            product == null || product.price != item.unitPrice
    }
    val canSubmit = existing?.editable == true && !custom && !stale &&
        !draft.busy && !draft.uncertain && count > 0

    fun save() {
        val initial = bill ?: return
        if (!canSubmit) return
        val desired = billQuantities(initial).toMutableMap()
        draft.items.forEach { (id, qty) -> desired[id] = (desired[id] ?: 0) + qty }
        draft.pendingQuantities = desired
        draft.requestId = UUID.randomUUID().toString()
        draft.busy = true
        cartOpen = false
        feedback = "saving"
        error = null
        scope.launch {
            try {
                client.saveDiningOrder(session, table.id, initial, desired, products,
                    requireNotNull(draft.requestId))
                val verified = client.diningBill(session, table.id)
                if (billQuantities(verified) != desired || verified.orderId.isNullOrBlank())
                    throw IllegalStateException("table_order_acknowledgement_not_confirmed")
                bill = verified
                feedback = "success"
                delay(850L)
                draft.items.clear()
                draft.pendingQuantities = emptyMap()
                draft.requestId = null
                draft.uncertain = false
                stale = false
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                val api = failure as? WebPosApiException
                if (api != null && api.status in 400..499) {
                    draft.requestId = null
                    draft.pendingQuantities = emptyMap()
                    stale = true
                } else {
                    // Network may have dropped after server committed. Never
                    // resend a different request or change the original order.
                    draft.uncertain = true
                }
                error = "ผลการบันทึกบิลยังไม่แน่นอน โปรดตรวจบิลเดิม: " +
                    (api?.code ?: "network_or_response_lost")
            } finally {
                feedback = "idle"
                draft.busy = false
            }
        }
    }

    fun reconcile() {
        if (draft.busy) return
        draft.busy = true
        scope.launch {
            try {
                val remote = client.diningBill(session, table.id)
                bill = remote
                if (draft.pendingQuantities.isNotEmpty() &&
                    billQuantities(remote) == draft.pendingQuantities &&
                    !remote.orderId.isNullOrBlank()) {
                    draft.items.clear()
                    draft.pendingQuantities = emptyMap()
                    draft.requestId = null
                    draft.uncertain = false
                    stale = false
                    error = null
                } else {
                    draft.uncertain = true
                    error = "จำนวนสินค้าไม่ตรงกับที่ส่ง ห้ามส่งซ้ำ โปรดตรวจใน POS Web"
                }
            } catch (failure: Exception) {
                error = "ยังตรวจสอบบิลไม่ได้ ห้ามบันทึกซ้ำ"
            } finally { draft.busy = false }
        }
    }

    Surface(Modifier.fillMaxSize(), color = Color(0xFFF5F9FF)) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().statusBarsPadding()
                .padding(horizontal = 14.dp).padding(bottom = 108.dp)) {
                Row(Modifier.fillMaxWidth().padding(top = 7.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("‹ กลับ", fontSize = 13.sp, color = dineBlue,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable(enabled = !draft.busy) { onTables() }
                            .padding(vertical = 12.dp))
                    Spacer(Modifier.width(11.dp))
                    Text("โต๊ะ " + table.name, color = dineInk, fontSize = 19.sp,
                        fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                    Text("เลือกโต๊ะ", fontSize = 13.sp, color = dineBlue,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable(enabled = !draft.busy) { onTables() }
                            .padding(8.dp))
                }
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("บิลโต๊ะเชื่อมกับ CpIPOS Web", fontSize = 11.sp,
                        color = Color(0xFF74849C), modifier = Modifier.weight(1f))
                    DiningTopIcon(R.drawable.ic_nav_select_mode, "เลือกโหมด", !draft.busy, onMode)
                    Spacer(Modifier.width(8.dp))
                    DiningTopIcon(R.drawable.ic_nav_cart, "ตะกร้า", !draft.busy) { cartOpen = true }
                    if (count > 0) Text(" " + count, color = dineBlue,
                        fontWeight = FontWeight.ExtraBold)
                }
                Surface(shape = RoundedCornerShape(17.dp), color = Color.White) {
                    Row(Modifier.fillMaxWidth().padding(13.dp),
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("บิล: " + (existing?.orderNo ?: "ยังไม่ส่งรายการ"),
                                fontSize = 12.sp, fontWeight = FontWeight.Bold, color = dineInk)
                            Text("บิลเดิม " + existing?.lines.orEmpty().sumOf { it.quantity } +
                                " ชิ้น · ตะกร้าใหม่ " + count + " ชิ้น",
                                fontSize = 11.sp, color = Color(0xFF74849C))
                        }
                        Text("฿" + Satang.of(savedTotal + subtotal).bahtText(),
                            color = dineBlue, fontWeight = FontWeight.ExtraBold)
                    }
                }
                if (existing?.lines?.isNotEmpty() == true) {
                    TextButton(onClick = { showExisting = true }) {
                        Text("ดูรายการในบิลจาก POS หลัก ›", fontSize = 12.sp)
                    }
                }
                if (error != null || custom || draft.uncertain || stale) {
                    Text(error ?: if (custom)
                        "รายการพิเศษ/ราคาไม่ตรง กรุณาแก้บิลนี้ใน POS Web"
                    else "บิลถูกเปลี่ยนแปลง ต้องรีเฟรช", color = Color(0xFFAE454A),
                        fontSize = 12.sp)
                    OutlinedButton(
                        enabled = !draft.busy,
                        onClick = {
                            if (draft.uncertain) reconcile()
                            else scope.launch { loading = true; refresh(true) }
                        }
                    ) { Text(if (draft.uncertain) "ตรวจบิลเดิม" else "รีเฟรชบิล", fontSize = 12.sp) }
                }
                OutlinedTextField(
                    value = search, onValueChange = { search = it }, singleLine = true,
                    placeholder = { Text("ค้นหาสินค้า หรือสแกนบาร์โค้ด", fontSize = 13.sp) },
                    leadingIcon = { Text("⌕", fontSize = 28.sp, color = dineBlue) },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = Color(0xFFBFD9FA),
                        unfocusedBorderColor = Color(0xFFE3ECFA))
                )
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(categories) { group ->
                        Surface(
                            modifier = Modifier.clickable { category = group },
                            shape = RoundedCornerShape(50),
                            color = if (category == group) dineBlue else Color(0xFFEDF3FB)
                        ) {
                            Text(group, color = if (category == group) Color.White
                                else Color(0xFF657790), fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                if (loading && bill == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = dineBlue)
                    }
                } else LazyVerticalGrid(
                    columns = GridCells.Fixed(2), modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    items(shown, key = { it.id }) { product ->
                        DemoProductCard(product, draft.items[product.id] ?: 0,
                            onAdd = {
                                if (existing?.editable == true && !draft.uncertain &&
                                    !stale && !draft.busy && !custom) {
                                    draft.items[product.id] = (draft.items[product.id] ?: 0) + 1
                                }
                            })
                    }
                }
            }
            HomeBottomMenu(modifier = Modifier.align(Alignment.BottomCenter),
                selectedTab = "sale", onMenuClick = {
                    if (!draft.busy) onMainMenu(it)
                })
        }
    }

    if (cartOpen) PreviewCartBottomSheet(
        products = entries, cart = draft.items, quantity = count, total = subtotal,
        onDismiss = { cartOpen = false },
        onAdd = { if (!draft.uncertain && !stale) draft.items[it] = (draft.items[it] ?: 0) + 1 },
        onRemove = {
            if (!draft.uncertain) {
                val value = (draft.items[it] ?: 0) - 1
                if (value <= 0) draft.items.remove(it) else draft.items[it] = value
            }
        },
        onDelete = { if (!draft.uncertain) draft.items.remove(it) },
        onCancelBill = { showClear = true },
        onCheckout = { save() }, onFeatureClick = {},
        checkoutLabel = "บันทึกรายการลงโต๊ะ", tableMode = true
    )
    if (showClear) AlertDialog(
        onDismissRequest = { showClear = false },
        title = { Text("ล้างตะกร้ารายการใหม่") },
        text = { Text("ล้างเฉพาะรายการยังไม่ส่ง ไม่ลบรายการใน POS หลัก") },
        confirmButton = { TextButton(onClick = {
            if (!draft.uncertain && !draft.busy) { draft.items.clear(); cartOpen = false }
            showClear = false
        }) { Text("ล้างตะกร้า") } },
        dismissButton = { TextButton(onClick = { showClear = false }) { Text("กลับ") } }
    )
    if (showExisting) AlertDialog(
        onDismissRequest = { showExisting = false },
        title = { Text("บิลโต๊ะ " + table.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                existing?.lines.orEmpty().forEach { item ->
                    Text(item.name + " ×" + item.quantity + "  ฿" +
                        Satang.of(item.unitPrice.value * item.quantity).bahtText(), fontSize = 12.sp)
                }
                Text("ระบบใช้บิลเดียวกันกับ CpIPOS Web", fontSize = 11.sp,
                    color = Color(0xFF74849C))
            }
        },
        confirmButton = { TextButton(onClick = { showExisting = false }) { Text("ปิด") } }
    )
    PaymentFeedbackOverlay(feedback)
}

@Composable
private fun DiningTopIcon(icon: Int, description: String, enabled: Boolean,
    onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(44.dp).shadow(2.dp, CircleShape).clip(CircleShape)
            .background(Color.White)
            .clickable(enabled = enabled, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Image(painter = painterResource(icon), contentDescription = description,
            modifier = Modifier.size(25.dp))
    }
}
