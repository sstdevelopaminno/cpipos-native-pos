package com.cpipos.pos.next.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cpipos.pos.next.R
import com.cpipos.pos.next.core.pos.Satang
import com.cpipos.pos.next.core.webpos.DiningMutationJournal
import com.cpipos.pos.next.core.webpos.*
import com.cpipos.pos.next.core.offline.DemoSaleReceipt
import com.cpipos.pos.next.printing.DemoReceiptPrinter
import com.cpipos.pos.next.core.print.NativePrintRuntime
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
    var paymentRequestId by mutableStateOf<String?>(null)
    var paymentMethod by mutableStateOf<PreviewPayment?>(null)
    var cashInput by mutableStateOf("")
    var discount by mutableStateOf<PosDiscount?>(null)
}
private fun billQuantities(bill: WebDiningBill): Map<String, Int> =
    bill.lines.groupBy { it.productId }.mapValues { (_, lines) -> lines.sumOf { it.quantity } }

/** Discount uses gross item prices before tax, not the tax-inclusive server total. */
internal fun diningDiscountBase(bill: WebDiningBill?, newItemsSubtotalSatang: Long): Long =
    bill?.lines.orEmpty().sumOf { it.unitPrice.value * it.quantity.toLong() } +
        newItemsSubtotalSatang

@Composable
internal fun NativeDiningOrderScreen(
    client: WebPosClient, session: WebPosSession, table: WebDiningTable,
    products: List<WebPosProduct>, draft: DiningDraft,
    onTables: () -> Unit, onMainMenu: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val mutationJournal = remember(context.applicationContext) {
        DiningMutationJournal(context.applicationContext)
    }
    var bill by remember(session.id, table.id) { mutableStateOf<WebDiningBill?>(null) }
    var loading by remember(session.id, table.id) { mutableStateOf(true) }
    var error by remember(session.id, table.id) { mutableStateOf<String?>(null) }
    var stale by remember(session.id, table.id) { mutableStateOf(false) }
    var cartOpen by remember(session.id, table.id) { mutableStateOf(false) }
    var showCancelPin by remember(session.id, table.id) { mutableStateOf(false) }
    var cancelPinError by remember(session.id, table.id) { mutableStateOf<String?>(null) }
    var showMethods by remember(session.id, table.id) { mutableStateOf(false) }
    var showDiscount by remember(session.id, table.id) { mutableStateOf(false) }
    var showTableQr by remember(session.id, table.id) { mutableStateOf(false) }
    var showMoveTable by remember(session.id, table.id) { mutableStateOf(false) }
    var selectedPaymentMethod by remember(session.id, table.id) { mutableStateOf(PreviewPayment.Cash) }
    var category by remember(session.id, table.id) { mutableStateOf("ทั้งหมด") }
    var search by remember(session.id, table.id) { mutableStateOf("") }
    var feedback by remember(session.id, table.id) { mutableStateOf("idle") }
    var paidReceipt by remember(session.id, table.id) { mutableStateOf<DemoSaleReceipt?>(null) }
    var drawerAvailable by remember(session.id) { mutableStateOf(false) }
    var drawerOpening by remember(session.id) { mutableStateOf(false) }

    LaunchedEffect(client, session.id) {
        while (true) {
            drawerAvailable = try {
                client.cashDrawerStatus(session).visible
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                false
            }
            delay(10_000L)
        }
    }

    suspend fun refresh(accept: Boolean) {
        try {
            val remote = client.diningBill(session, table.id)
            if (bill != null && remote.fingerprint() != bill?.fingerprint() &&
                (draft.items.isNotEmpty() || draft.discount != null) && !accept) {
                stale = true
                error = "บิลบน POS หลักเปลี่ยนแล้ว ต้องรีเฟรชก่อนบันทึกรายการ"
            } else {
                bill = remote
                stale = false
                error = null
            }
            if (!remote.editable) {
                if (draft.items.isNotEmpty() || draft.pendingQuantities.isNotEmpty()) draft.uncertain = true
                if (remote.paid || remote.sessionStatus in listOf("closed", "cancelled")) {
                    if (draft.items.isEmpty() && draft.pendingQuantities.isEmpty() &&
                        mutationJournal.pending(session, table.id) == null) {
                        onTables()
                    } else {
                        bill = remote
                        stale = true
                        error = "โต๊ะถูกชำระหรือปิดจาก POS อีกเครื่องแล้ว " +
                            "มีรายการในมือถือที่ยังไม่ได้ยืนยัน ห้ามส่งซ้ำและต้องตรวจบิลเดิม"
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = "โหลดบิลโต๊ะไม่ได้ โปรดตรวจสอบการเชื่อมต่อ"
        } finally { loading = false }
    }
    LaunchedEffect(client, session.id, table.id) {
        mutationJournal.pending(session, table.id)?.let { pending ->
            draft.uncertain = true
            if (pending.kind == "items") {
                draft.requestId = pending.requestId
                draft.pendingQuantities = pending.quantities
            } else if (pending.kind == "payment") {
                draft.paymentRequestId = pending.requestId
            }
            error = "มีรายการโต๊ะที่ผลตอบกลับไม่แน่นอน กรุณาตรวจบิลเดิมก่อน"
        }
        refresh(true)
        while (true) {
            delay(6000L)
            if (!draft.busy) refresh(false)
        }
    }
    BackHandler(enabled = !draft.busy && !showTableQr && !showMoveTable) {
        when {
            draft.paymentMethod != null -> draft.paymentMethod = null
            cartOpen -> cartOpen = false
            showCancelPin -> showCancelPin = false
            else -> onTables()
        }
    }

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
    val existing = bill
    val storedQuantities = existing?.let(::billQuantities).orEmpty()
    val combinedCart = storedQuantities.toMutableMap().apply {
        draft.items.forEach { (id, quantity) -> put(id, (get(id) ?: 0) + quantity) }
    }
    val combinedCount = combinedCart.values.sum()
    val gross = diningDiscountBase(existing, subtotal)
    val targetDiscount = draft.discount?.amount(gross)
        ?: (existing?.discount?.value ?: 0L)
    val discountChanged = draft.discount != null &&
        targetDiscount != (existing?.discount?.value ?: 0L)
    val discountValid = targetDiscount >= 0L && targetDiscount < gross
    val combinedTotal = (gross - targetDiscount).coerceAtLeast(0L)
    val custom = existing?.lines.orEmpty().any { item ->
        val product = products.find { it.id == item.productId }
        item.quantity <= 0 || item.hasCustomMetadata || !item.notes.isNullOrBlank() ||
            product == null || product.price != item.unitPrice
    }
    val canSubmit = existing?.editable == true && !custom && !stale &&
        !draft.busy && !draft.uncertain && discountValid &&
        (count > 0 || discountChanged)
    val canPayExisting = existing?.editable == true && existing.orderId != null &&
        existing.total > Satang.ZERO && !custom && !stale &&
        !draft.busy && !draft.uncertain && !discountChanged && count == 0
    val canCheckout = canSubmit || canPayExisting

    fun save(thenCheckout: Boolean = true) {
        val initial = bill ?: return
        if (!canSubmit) return
        val desired = billQuantities(initial).toMutableMap()
        draft.items.forEach { (id, qty) -> desired[id] = (desired[id] ?: 0) + qty }
        val requestedDiscount = targetDiscount
        val requestId = UUID.randomUUID().toString()
        try {
            mutationJournal.prepare(session, table.id, "items", initial.orderId, requestId,
                desired, requestedDiscount)
        } catch (failure: Exception) {
            draft.uncertain = true
            error = "มีรายการที่ต้องตรวจสอบในเครื่อง ห้ามบันทึกซ้ำ"
            return
        }
        draft.pendingQuantities = desired
        draft.requestId = requestId
        draft.busy = true
        cartOpen = false
        feedback = "saving"
        error = null
        scope.launch {
            try {
                client.saveDiningOrder(session, table.id, initial, desired, products,
                    requireNotNull(draft.requestId), Satang.of(requestedDiscount))
                val verified = client.diningBill(session, table.id)
                if (billQuantities(verified) != desired ||
                    verified.discount.value != requestedDiscount ||
                    verified.orderId.isNullOrBlank())
                    throw IllegalStateException("table_order_acknowledgement_not_confirmed")
                mutationJournal.confirmed(session, table.id, requestId)
                bill = verified
                // Items are saved, but the table has NOT been paid.
                // Show the green success check only after actual settlement.
                draft.items.clear()
                draft.discount = null
                draft.pendingQuantities = emptyMap()
                draft.requestId = null
                draft.uncertain = false
                stale = false
                feedback = "idle"
                if (thenCheckout) showMethods = true else onTables()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                val api = failure as? WebPosApiException
                if (api != null && api.status in 400..499 &&
                    runCatching { mutationJournal.confirmed(session, table.id, requestId) }.isSuccess) {
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
                val pending = mutationJournal.pending(session, table.id)
                if (pending?.kind == "cancel") {
                    val snapshot = client.diningTables(session)
                    val vacant = snapshot.tables.any {
                        it.id == table.id && it.status == "available" && it.sessionId == null
                    }
                    if (vacant && pending.requestId.isNotBlank()) {
                        mutationJournal.confirmed(session, table.id, pending.requestId)
                        draft.items.clear()
                        draft.pendingQuantities = emptyMap()
                        draft.uncertain = false
                        error = null
                        onTables()
                    } else {
                        draft.uncertain = true
                        error = "ยังไม่ยืนยันการยกเลิกบิล โปรดตรวจใน POS Web " +
                            "ห้ามส่งคำขอซ้ำ"
                    }
                    return@launch
                }
                val remote = client.diningBill(session, table.id)
                bill = remote
                val resolved = pending != null && when (pending.kind) {
                    "items" -> pending.quantities.isNotEmpty() &&
                        billQuantities(remote) == pending.quantities &&
                        (pending.discountSatang == null ||
                            remote.discount.value == pending.discountSatang) &&
                        !remote.orderId.isNullOrBlank()
                    "payment" -> remote.paid && remote.sessionStatus == "closed" &&
                        remote.orderId == pending.orderId
                    else -> false
                }
                if (resolved && pending != null) {
                    mutationJournal.confirmed(session, table.id, pending.requestId)
                    draft.items.clear()
                    draft.discount = null
                    draft.pendingQuantities = emptyMap()
                    draft.requestId = null
                    draft.paymentRequestId = null
                    draft.paymentMethod = null
                    draft.uncertain = false
                    stale = false
                    error = null
                    if (pending.kind == "payment") onTables()
                } else {
                    draft.uncertain = true
                    error = "จำนวนสินค้าไม่ตรงกับที่ส่ง ห้ามส่งซ้ำ โปรดตรวจใน POS Web"
                }
            } catch (failure: Exception) {
                error = "ยังตรวจสอบบิลไม่ได้ ห้ามบันทึกซ้ำ"
            } finally { draft.busy = false }
        }
    }

    fun cancelBillWithPin(pin: String) {
        val base = bill ?: return
        if (draft.busy || draft.uncertain || !base.editable || base.paid ||
            base.sessionId == null ||
            !(if (base.orderId != null) session.canSell else session.canManageTables)) return
        if (mutationJournal.pending(session, table.id) != null) {
            draft.uncertain = true
            cancelPinError = "มีคำขอค้างอยู่ ห้ามยกเลิกซ้ำ"
            return
        }
        draft.busy = true
        cancelPinError = null
        scope.launch {
            var mutationPrepared = false
            try {
                val approvalId = client.approveDiningCancellation(session, base, pin)
                val requestId = UUID.randomUUID().toString()
                mutationJournal.prepare(session, table.id, "cancel", base.orderId,
                    requestId, emptyMap())
                mutationPrepared = true
                val vacant = client.cancelDiningBill(session, table.id, base, approvalId)
                if (!vacant) throw IllegalStateException("table_release_not_confirmed")
                mutationJournal.confirmed(session, table.id, requestId)
                draft.items.clear()
                draft.pendingQuantities = emptyMap()
                draft.requestId = null
                draft.paymentRequestId = null
                draft.uncertain = false
                showCancelPin = false
                onTables()
            } catch (failure: Exception) {
                if (mutationPrepared) {
                    draft.uncertain = true
                    showCancelPin = false
                    error = "ผลยกเลิกบิลยังไม่ยืนยัน โปรดตรวจบิลเดิมใน POS Web ห้ามส่งซ้ำ"
                } else {
                    cancelPinError = "PIN ไม่ถูกต้องหรือไม่มีสิทธิ์อนุมัติ " +
                        "หรือบิลเปลี่ยนแล้ว กรุณาตรวจสอบและลองใหม่"
                }
            } finally { draft.busy = false }
        }
    }

    fun settle() {
        val reference = bill ?: return
        val method = draft.paymentMethod ?: return
        if (!reference.editable || reference.orderId == null ||
            reference.total <= Satang.ZERO || draft.items.isNotEmpty() ||
            stale || draft.uncertain || draft.busy) return
        val received = if (method == PreviewPayment.Cash) {
            val parsed = runCatching {
                com.cpipos.pos.next.core.pos.CashTenderInput.money(draft.cashInput)
            }.getOrNull() ?: return
            if (parsed < reference.total) return
            parsed
        } else Satang.ZERO
        val requestId = draft.paymentRequestId ?: UUID.randomUUID().toString()
        try {
            mutationJournal.prepare(session, table.id, "payment", reference.orderId,
                requestId, emptyMap())
        } catch (failure: Exception) {
            draft.uncertain = true
            error = "มีรายการชำระเงินที่ต้องตรวจสอบ ห้ามชำระซ้ำ"
            return
        }
        draft.paymentRequestId = requestId
        draft.busy = true
        feedback = "saving"
        error = null
        scope.launch {
            try {
                val paid = client.settleDiningBill(
                    session = session,
                    tableId = table.id,
                    expected = reference,
                    method = if (method == PreviewPayment.Cash) "cash" else "bank_transfer",
                    cashReceived = received,
                    requestId = requireNotNull(draft.paymentRequestId),
                    tableLabel = table.code.ifBlank { table.name }
                )
                val verification = client.diningBill(session, table.id)
                if (!verification.paid || verification.sessionStatus != "closed")
                    throw IllegalStateException("payment_or_table_cleanup_unconfirmed")
                mutationJournal.confirmed(session, table.id, requestId)
                bill = verification
                paidReceipt = paid.receipt
                NativePrintRuntime.wake()
                feedback = "success"
                delay(650L)
                draft.paymentRequestId = null
                draft.paymentMethod = null
                draft.cashInput = ""
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                // The payment may already have settled at the server. NEVER
                // send again with a different payment UUID or unlock the table.
                draft.uncertain = true
                error = "ผลชำระเงินโต๊ะยังไม่แน่นอน กรุณาตรวจบิลเดิมใน POS Web ก่อน: " +
                    ((failure as? WebPosApiException)?.code ?: "network_or_response_lost")
            } finally {
                feedback = "idle"
                draft.busy = false
            }
        }
    }

    Surface(Modifier.fillMaxSize(), color = Color(0xFFF5F9FF)) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().statusBarsPadding()
                .padding(horizontal = 14.dp).padding(bottom = 108.dp)) {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 5.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Image(painter = painterResource(R.drawable.cpipos_logo_symbol),
                        contentDescription = null, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("CpIPOS", color = dineInk, fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) {
                        Text("โต๊ะ " + table.name, color = dineBlue,
                            fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                        Text("เลขที่โต๊ะ " + table.code, color = Color(0xFF6B80A0),
                            fontSize = 11.sp, maxLines = 1)
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DiningTopAction(R.drawable.ic_nav_qr_table, "QR โต๊ะ", !draft.busy) {
                        showTableQr = true
                    }
                    DiningTopAction(R.drawable.ic_nav_move_table, "ย้ายโต๊ะ",
                        !draft.busy && session.canManageTables) {
                        showMoveTable = true
                    }
                    DiningTopAction(R.drawable.ic_nav_tables, "เลือกโต๊ะ", !draft.busy) { onTables() }
                    if (drawerAvailable) {
                        DiningTopAction(
                            R.drawable.ic_nav_cash_drawer,
                            "เปิดลิ้นชัก",
                            !draft.busy && !drawerOpening
                        ) {
                            drawerOpening = true
                            scope.launch {
                                try {
                                    val accepted = client.openCashDrawer(session)
                                    if (!accepted) throw IllegalStateException("drawer_command_not_accepted")
                                    NativePrintRuntime.wake()
                                    Toast.makeText(context, "ส่งคำสั่งเปิดลิ้นชักแล้ว", Toast.LENGTH_SHORT).show()
                                } catch (failure: Exception) {
                                    val api = failure as? WebPosApiException
                                    drawerAvailable = runCatching {
                                        client.cashDrawerStatus(session).visible
                                    }.getOrDefault(false)
                                    val message = when (api?.code) {
                                        "permission_denied" -> "บัญชีนี้ไม่มีสิทธิ์เปิดลิ้นชัก"
                                        "drawer_route_not_ready",
                                        "print_agent_unavailable" -> "ลิ้นชักหรือเครื่องพิมพ์ยังไม่พร้อม"
                                        "drawer_cooldown" -> "กรุณารอสักครู่ก่อนเปิดลิ้นชักอีกครั้ง"
                                        else -> "เปิดลิ้นชักไม่สำเร็จ"
                                    }
                                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                } finally {
                                    drawerOpening = false
                                }
                            }
                        }
                    }
                    DiningTopAction(
                        R.drawable.ic_nav_cart, "ตะกร้า", !draft.busy,
                        count = combinedCount
                    ) { cartOpen = true }
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

    if (showMethods) PreviewPaymentMethodSheet(
        total = bill?.total?.value ?: 0L,
        isLiveSale = true,
        selectedMethod = selectedPaymentMethod,
        onSelectMethod = { selectedPaymentMethod = it },
        onDismiss = { showMethods = false },
        onConfirm = {
            if (!draft.busy && !draft.uncertain && draft.items.isEmpty()) {
                showMethods = false
                draft.paymentMethod = selectedPaymentMethod
                if (selectedPaymentMethod == PreviewPayment.Cash)
                    draft.cashInput = ""
            }
        }
    )
    if (draft.paymentMethod == PreviewPayment.Cash) {
        CashCheckoutScreen(
            due = bill?.total ?: Satang.ZERO,
            cashInput = draft.cashInput,
            saving = draft.busy,
            error = error,
            isLiveSale = true,
            onInputChange = { if (!draft.busy) draft.cashInput = it },
            onCancel = { if (!draft.busy) draft.paymentMethod = null },
            onConfirm = { settle() }
        )
        PaymentFeedbackOverlay(feedback)
        return
    }
    if (draft.paymentMethod == PreviewPayment.Transfer) {
        TransferQrCheckoutScreen(
            client = client, session = session,
            due = bill?.total ?: Satang.ZERO, saving = draft.busy,
            error = error,
            onCancel = { if (!draft.busy) draft.paymentMethod = null },
            onConfirmReceived = { settle() }
        )
        PaymentFeedbackOverlay(feedback)
        return
    }
    if (showDiscount) NativeDiscountDialog(
        subtotal = gross,
        current = draft.discount ?: PosDiscount(
            "amount", existing?.discount?.bahtText().orEmpty()
        ),
        onDismiss = { showDiscount = false },
        onConfirm = { selected ->
            draft.discount = selected
            showDiscount = false
        }
    )
    if (cartOpen) PreviewCartBottomSheet(
        products = entries, cart = combinedCart,
        quantity = combinedCount, total = combinedTotal,
        onDismiss = { cartOpen = false },
        onAdd = {
            if (existing?.editable == true && !draft.uncertain && !draft.busy &&
                !stale && !custom) {
                draft.items[it] = (draft.items[it] ?: 0) + 1
            }
        },
        onRemove = {
            if (!draft.uncertain && !draft.busy && !stale && !custom) {
                val value = (draft.items[it] ?: 0) - 1
                if (value <= 0) draft.items.remove(it) else draft.items[it] = value
            }
        },
        onDelete = { if (!draft.uncertain && !draft.busy && !stale && !custom) draft.items.remove(it) },
        onCancelBill = {
            cartOpen = false
            if (bill?.sessionId != null && bill?.editable == true &&
                !draft.busy && !draft.uncertain &&
                (if (bill?.orderId != null) session.canSell else session.canManageTables)) {
                cancelPinError = null
                showCancelPin = true
            } else {
                error = "ยกเลิกไม่ได้: ตรวจสถานะบิล/สิทธิ์พนักงานใน CpIPOS หลัก"
            }
        },
        onCheckout = {
            if (canCheckout) {
                cartOpen = false
                if (canSubmit) save()
                else showMethods = true
            }
        },
        onFeatureClick = { if (it == "ส่วนลด") showDiscount = true },
        onHoldBill = {
            cartOpen = false
            if (canSubmit) save(thenCheckout = false)
            else if (canPayExisting) onTables()
            else error = "ไม่สามารถพักบิลที่ยังตรวจสอบสถานะไม่สำเร็จ"
        },
        discountAmount = targetDiscount,
        checkoutLabel = "ชำระเงิน", tableMode = true,
        checkoutEnabled = canCheckout,
        lockedQuantities = storedQuantities
    )
    if (showCancelPin) NativeCancelBillPinDialog(
        title = "ยกเลิกบิลโต๊ะ " + table.name,
        busy = draft.busy,
        error = cancelPinError,
        onDismiss = {
            if (!draft.busy) {
                showCancelPin = false
                cancelPinError = null
            }
        },
        onApprove = { pin -> cancelBillWithPin(pin) }
    )
    if (showMoveTable) {
        NativeTableMoveDialog(
            client = client,
            session = session,
            source = table,
            originalBill = bill,
            canMoveLocalDraft = !draft.busy && !draft.uncertain &&
                !stale && draft.items.isEmpty() && draft.discount == null &&
                draft.pendingQuantities.isEmpty() &&
                draft.paymentMethod == null && draft.paymentRequestId == null &&
                mutationJournal.pending(session, table.id) == null,
            onDismiss = { showMoveTable = false },
            onMoved = {
                showMoveTable = false
                onTables()
            }
        )
    }
    if (showTableQr) {
        NativeTableOrderQrDialog(
            client = client,
            session = session,
            selectedTableId = table.id,
            onDismiss = { showTableQr = false }
        )
    }
    paidReceipt?.let { receipt ->
        SavedDemoReceiptSheet(
            receipt = receipt,
            onPrint = { runCatching { DemoReceiptPrinter.print(context, receipt) } },
            onDone = {
                paidReceipt = null
                onTables()
            }
        )
    }
    PaymentFeedbackOverlay(feedback)
}

@Composable
private fun DiningTopAction(icon: Int, description: String, enabled: Boolean,
    count: Int = 0, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Box(
            modifier = Modifier.size(45.dp).shadow(2.dp, CircleShape).clip(CircleShape)
                .background(Color.White)
                .clickable(enabled = enabled, onClickLabel = description, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Image(painter = painterResource(icon), contentDescription = description,
                modifier = Modifier.size(25.dp))
            if (count > 0) {
                Surface(
                    modifier = Modifier.align(Alignment.TopEnd),
                    shape = CircleShape, color = dineBlue
                ) {
                    Text(
                        if (count > 99) "99+" else count.toString(),
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                        color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp
                    )
                }
            }
        }
        Text(description, fontSize = 10.sp, color = Color(0xFF667894),
            maxLines = 1)
    }
}
