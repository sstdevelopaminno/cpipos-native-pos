package com.cpipos.pos.next.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cpipos.pos.next.core.print.NativePrintRuntime
import com.cpipos.pos.next.core.webpos.*
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private val moreBlue = Color(0xFF176DED)
private val moreInk = Color(0xFF172B4E)
private val moreMuted = Color(0xFF71849B)

private data class MoreMenu(
    val key: String,
    val icon: String,
    val title: String,
    val subtitle: String
)

@Composable
internal fun NativeMoreScreen(
    client: WebPosClient,
    session: WebPosSession,
    products: List<WebPosProduct>,
    onTab: (String) -> Unit
) {
    var page by remember(session.id) { mutableStateOf("hub") }

    when (page) {
        "sales" -> {
            NativeSalesListScreen(
                client = client,
                session = session,
                onTab = onTab,
                onBack = { page = "hub" },
                selectedBottomTab = "history"
            )
            return
        }
        "summary" -> {
            MoreSalesSummaryScreen(client, session, onBack = { page = "hub" }, onTab = onTab)
            return
        }
        "receipts" -> {
            MoreReceiptsScreen(client, session, onBack = { page = "hub" }, onTab = onTab)
            return
        }
        "tables" -> {
            MoreTablesScreen(client, session, onBack = { page = "hub" }, onTab = onTab)
            return
        }
        "best" -> {
            MoreBestSellingScreen(client, session, onBack = { page = "hub" }, onTab = onTab)
            return
        }
        "members" -> {
            MoreMembersScreen(client, session, onBack = { page = "hub" }, onTab = onTab)
            return
        }
        "tax" -> {
            MoreTaxInvoiceScreen(client, session, onBack = { page = "hub" }, onTab = onTab)
            return
        }
    }

    val menus = listOf(
        MoreMenu("summary", "📊", "สรุปยอดขาย", "ยอดขายสุทธิ จำนวนบิล เงินสด โอน / QR และส่วนลด"),
        MoreMenu("receipts", "🧾", "ใบเสร็จย้อนหลัง", "ค้นหาและตรวจสอบใบเสร็จที่ชำระสำเร็จ"),
        MoreMenu("tables", "▦", "จัดการโต๊ะ", "โต๊ะ โซน สถานะ และบิลที่เปิดอยู่"),
        MoreMenu("sales", "≣", "รายการขายสินค้า", "รายการขายรายวัน / เดือน / ปี และช่วงวันที่"),
        MoreMenu("best", "★", "สินค้าขายดี", "อันดับสินค้าขายดีจากข้อมูลสรุปยอดขาย CpIPOS"),
        MoreMenu("members", "👥", "สมาชิก", "ข้อมูลสมาชิก คะแนน และแต้มสะสม"),
        MoreMenu("tax", "🧾", "ออกใบกำกับภาษี", "บิลที่ชำระแล้วและสถานะใบกำกับภาษี")
    )

    Surface(Modifier.fillMaxSize(), color = Color(0xFFF5F9FF)) {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().statusBarsPadding()
                    .padding(horizontal = 14.dp)
                    .padding(top = 12.dp, bottom = 112.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                item {
                    Text("เพิ่มเติม", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = moreInk)
                    Text(session.branchName + " · " + session.deviceCode, fontSize = 11.sp, color = moreMuted)
                    Spacer(Modifier.height(9.dp))
                }
                items(menus, key = { it.key }) { menu ->
                    Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(17.dp),
                        shadowElevation = 1.dp,
                        modifier = Modifier.fillMaxWidth().clickable { page = menu.key }
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = Color(0xFFEAF3FF),
                                shape = RoundedCornerShape(13.dp),
                                modifier = Modifier.size(48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) { Text(menu.icon, fontSize = 22.sp) }
                            }
                            Spacer(Modifier.width(11.dp))
                            Column(Modifier.weight(1f)) {
                                Text(menu.title, color = moreInk, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                                Text(
                                    menu.subtitle,
                                    color = moreMuted,
                                    fontSize = 10.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text("›", color = moreBlue, fontSize = 25.sp)
                        }
                    }
                }
            }
            HomeBottomMenu(
                modifier = Modifier.align(Alignment.BottomCenter),
                selectedTab = "history",
                onMenuClick = onTab
            )
        }
    }
}

@Composable
private fun MoreSalesSummaryScreen(
    client: WebPosClient,
    session: WebPosSession,
    onBack: () -> Unit,
    onTab: (String) -> Unit
) {
    var data by remember(session.id) { mutableStateOf<WebSalesSummarySnapshot?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    val today = remember { LocalDate.now(ZoneId.of("Asia/Bangkok")) }
    val fromDate = remember { today.minusDays(29) }

    LaunchedEffect(client, session.id, refresh) {
        loading = true
        try {
            data = client.salesSummary(session, fromDate, today)
            error = null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = "โหลดสรุปยอดขายไม่ได้: " + ((failure as? WebPosApiException)?.code ?: "network_error")
        } finally { loading = false }
    }

    MoreScaffold("สรุปยอดขาย", session, onBack, onTab) {
        item {
            Text("ช่วง 30 วันล่าสุด · " + fromDate + " – " + today, color = moreMuted, fontSize = 11.sp)
            TextButton(onClick = { refresh += 1 }, enabled = !loading) { Text("↻ รีเฟรช") }
        }
        if (loading) item { LoadingBlock() }
        else if (error != null) item { ErrorBlock(error.orEmpty()) }
        else {
            val snapshot = data
            if (snapshot != null) {
                item {
                    MoreMetricTable(
                        listOf(
                            "ยอดขายรวม" to snapshot.grossSales.bahtText(),
                            "ยอดขายสุทธิ" to snapshot.netSales.bahtText(),
                            "จำนวนบิล" to snapshot.receiptCount.toString(),
                            "เงินสด" to snapshot.cashTotal.bahtText(),
                            "โอน / QR" to snapshot.transferTotal.bahtText(),
                            "ส่วนลด" to snapshot.discountTotal.bahtText(),
                            "ภาษี" to snapshot.taxTotal.bahtText(),
                            "ยกเลิก" to snapshot.cancelledTotal.bahtText(),
                            "เฉลี่ยต่อบิล" to snapshot.averageReceipt.bahtText()
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun MoreBestSellingScreen(
    client: WebPosClient,
    session: WebPosSession,
    onBack: () -> Unit,
    onTab: (String) -> Unit
) {
    var data by remember(session.id) { mutableStateOf<WebSalesSummarySnapshot?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(client, session.id) {
        try {
            val today = LocalDate.now(ZoneId.of("Asia/Bangkok"))
            data = client.salesSummary(session, today.minusDays(29), today)
            error = null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = "โหลดสินค้าขายดีไม่ได้: " + ((failure as? WebPosApiException)?.code ?: "network_error")
        } finally { loading = false }
    }

    MoreScaffold("สินค้าขายดี", session, onBack, onTab) {
        item { Text("จัดอันดับจากยอดขายสำเร็จ 30 วันล่าสุด", color = moreMuted, fontSize = 11.sp) }
        if (loading) item { LoadingBlock() }
        else if (error != null) item { ErrorBlock(error.orEmpty()) }
        else {
            item { TableHeader(listOf("อันดับ" to .55f, "สินค้า" to 1.7f, "ขาย" to .65f, "ยอดสุทธิ" to .9f)) }
            items(data?.bestSelling.orEmpty(), key = { it.productId }) { row ->
                val rank = (data?.bestSelling?.indexOf(row) ?: 0) + 1
                TableRow(
                    listOf(
                        ("#" + rank) to .55f,
                        row.productName to 1.7f,
                        formatQty(row.quantitySold) to .65f,
                        ("฿" + row.netAmount.bahtText()) to .9f
                    )
                )
            }
            if (data?.bestSelling.isNullOrEmpty()) item { EmptyBlock("ยังไม่มีข้อมูลสินค้าขายดี") }
        }
    }
}

@Composable
private fun MoreReceiptsScreen(
    client: WebPosClient,
    session: WebPosSession,
    onBack: () -> Unit,
    onTab: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var history by remember(session.id) { mutableStateOf<WebReceiptHistory?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var reprintTarget by remember { mutableStateOf<WebReceiptHistoryRecord?>(null) }
    var reprintBusy by remember { mutableStateOf(false) }
    var reprintError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(client, session.id, refresh) {
        loading = true
        try {
            history = client.receiptHistory(session, page = 1, pageSize = 50)
            error = null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = "โหลดใบเสร็จย้อนหลังไม่ได้: " + ((failure as? WebPosApiException)?.code ?: "network_error")
        } finally { loading = false }
    }

    MoreScaffold("ใบเสร็จย้อนหลัง", session, onBack, onTab) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("รายการชำระสำเร็จย้อนหลัง 30 วัน", color = moreMuted, fontSize = 11.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { refresh += 1 }, enabled = !loading && !reprintBusy) { Text("↻ รีเฟรช") }
            }
        }
        if (loading) item { LoadingBlock() }
        else if (error != null) item { ErrorBlock(error.orEmpty()) }
        else {
            item { TableHeader(listOf("เลขบิล" to 1.4f, "โหมด" to .85f, "ชำระ" to .85f, "ยอด" to .9f)) }
            items(history?.records.orEmpty(), key = { it.orderId }) { row ->
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().clickable(enabled = !reprintBusy) {
                        reprintError = null
                        reprintTarget = row
                    }
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(row.orderNo, Modifier.weight(1.4f), fontSize = 10.sp, color = moreInk, maxLines = 2)
                        Text(row.receipt.modeLabel, Modifier.weight(.85f), fontSize = 10.sp, color = moreInk)
                        Text(if ("bank_transfer" in row.paymentMethods) "โอน/QR" else "เงินสด", Modifier.weight(.85f), fontSize = 10.sp, color = moreInk)
                        Text("฿" + row.receipt.total.bahtText(), Modifier.weight(.9f), fontSize = 10.sp, color = moreBlue, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (history?.records.isNullOrEmpty()) item { EmptyBlock("ไม่พบใบเสร็จย้อนหลัง") }
            item { Text("แตะรายการเพื่อพิมพ์ย้อนหลัง · ต้องยืนยัน PIN Owner/Manager ตาม CpIPOS", color = moreMuted, fontSize = 10.sp) }
        }
    }

    reprintTarget?.let { row ->
        NativeCancelBillPinDialog(
            title = "พิมพ์ใบเสร็จย้อนหลัง " + row.orderNo,
            busy = reprintBusy,
            error = reprintError,
            actionLabel = "ตรวจ PIN และพิมพ์",
            actionNote = "ใช้เครื่องพิมพ์ใบเสร็จ/พิมพ์ย้อนหลังที่ตั้งไว้ใน CpIPOS",
            onDismiss = {
                if (!reprintBusy) {
                    reprintTarget = null
                    reprintError = null
                }
            },
            onApprove = { pin ->
                reprintBusy = true
                reprintError = null
                scope.launch {
                    try {
                        client.reprintReceipt(session, row.orderId, pin)
                        NativePrintRuntime.wake()
                        reprintTarget = null
                    } catch (failure: Exception) {
                        val api = failure as? WebPosApiException
                        reprintError = when (api?.code) {
                            "pin_rejected", "pin_required" -> "PIN Owner/Manager ไม่ถูกต้อง"
                            "receipt_printer_not_configured" -> "ยังไม่ได้ตั้งค่าเครื่องพิมพ์ใบเสร็จย้อนหลัง"
                            else -> "พิมพ์ย้อนหลังไม่สำเร็จ: " + (api?.code ?: "printer_or_network_error")
                        }
                    } finally { reprintBusy = false }
                }
            }
        )
    }
}

@Composable
private fun MoreTablesScreen(
    client: WebPosClient,
    session: WebPosSession,
    onBack: () -> Unit,
    onTab: (String) -> Unit
) {
    var floor by remember(session.id) { mutableStateOf<WebTableFloor?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(client, session.id) {
        try {
            floor = client.diningTables(session)
            error = null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = "โหลดข้อมูลโต๊ะไม่ได้: " + ((failure as? WebPosApiException)?.code ?: "network_error")
        } finally { loading = false }
    }

    MoreScaffold("จัดการโต๊ะ", session, onBack, onTab) {
        item { Text("ข้อมูลโต๊ะ/โซนชุดเดียวกับ CpIPOS", color = moreMuted, fontSize = 11.sp) }
        if (loading) item { LoadingBlock() }
        else if (error != null) item { ErrorBlock(error.orEmpty()) }
        else {
            item { TableHeader(listOf("โต๊ะ" to 1.2f, "โซน" to 1.1f, "ที่นั่ง" to .65f, "สถานะ" to 1f)) }
            val zones = floor?.zones.orEmpty().associateBy { it.id }
            items(floor?.tables.orEmpty(), key = { it.id }) { row ->
                val status = when (row.status) {
                    "available" -> "ว่าง"
                    "occupied" -> "มีลูกค้า"
                    "reserved" -> "จอง"
                    "pending_payment" -> "รอชำระ"
                    "inactive" -> "ปิด"
                    else -> row.status
                }
                TableRow(
                    listOf(
                        row.code.ifBlank { row.name } to 1.2f,
                        (row.zoneId?.let { zones[it]?.name } ?: "-") to 1.1f,
                        row.capacity.toString() to .65f,
                        status to 1f
                    )
                )
            }
            if (floor?.tables.isNullOrEmpty()) item { EmptyBlock("ยังไม่มีโต๊ะในสาขานี้") }
        }
    }
}

@Composable
private fun MoreMembersScreen(
    client: WebPosClient,
    session: WebPosSession,
    onBack: () -> Unit,
    onTab: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var rows by remember(session.id) { mutableStateOf<List<WebMemberRow>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var editor by remember { mutableStateOf<WebMemberRow?>(null) }
    var createOpen by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }

    LaunchedEffect(client, session.id, submitted, refresh) {
        loading = true
        try {
            rows = client.members(session, submitted)
            error = null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = "โหลดสมาชิกไม่ได้: " + ((failure as? WebPosApiException)?.code ?: "network_error")
        } finally { loading = false }
    }

    MoreScaffold("สมาชิก", session, onBack, onTab) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it.take(80) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("ค้นหาชื่อ / เบอร์โทร / อีเมล") },
                singleLine = true,
                trailingIcon = { TextButton(onClick = { submitted = query.trim() }) { Text("ค้นหา") } }
            )
        }
        item {
            Button(
                onClick = { createOpen = true; editor = null },
                modifier = Modifier.fillMaxWidth(),
                enabled = !saving,
                colors = ButtonDefaults.buttonColors(containerColor = moreBlue)
            ) { Text("+ เพิ่มสมาชิก", fontWeight = FontWeight.Bold) }
        }
        if (loading) item { LoadingBlock() }
        else if (error != null) item { ErrorBlock(error.orEmpty()) }
        else {
            item { TableHeader(listOf("สมาชิก" to 1.55f, "เบอร์โทร" to 1.1f, "คะแนน" to .7f, "แต้ม" to .6f)) }
            items(rows, key = { it.id }) { row ->
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().clickable(enabled = !saving) { editor = row; createOpen = false }
                ) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 11.dp)) {
                        Text(row.name, Modifier.weight(1.55f), fontSize = 10.sp, color = moreInk, maxLines = 2)
                        Text(row.phone.ifBlank { "-" }, Modifier.weight(1.1f), fontSize = 10.sp, color = moreInk)
                        Text(row.points.toString(), Modifier.weight(.7f), fontSize = 10.sp, color = moreInk)
                        Text(row.stamps.toString(), Modifier.weight(.6f), fontSize = 10.sp, color = moreInk)
                    }
                }
            }
            if (rows.isEmpty()) item { EmptyBlock("ไม่พบสมาชิก") }
        }
    }

    if (createOpen || editor != null) {
        MemberEditorDialog(
            initial = editor,
            busy = saving,
            onDismiss = {
                if (!saving) {
                    createOpen = false
                    editor = null
                }
            },
            onSave = { name, phone, email, points, stamps ->
                saving = true
                scope.launch {
                    try {
                        client.saveMember(session, name, phone, email, points, stamps)
                        createOpen = false
                        editor = null
                        refresh += 1
                    } catch (failure: Exception) {
                        error = "บันทึกสมาชิกไม่สำเร็จ: " + ((failure as? WebPosApiException)?.code ?: "network_error")
                    } finally { saving = false }
                }
            }
        )
    }
}

@Composable
private fun MemberEditorDialog(
    initial: WebMemberRow?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, String, Int, Int) -> Unit
) {
    var name by remember(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    var phone by remember(initial?.id) { mutableStateOf(initial?.phone.orEmpty()) }
    var email by remember(initial?.id) { mutableStateOf(initial?.email.orEmpty()) }
    var points by remember(initial?.id) { mutableStateOf((initial?.points ?: 0).toString()) }
    var stamps by remember(initial?.id) { mutableStateOf((initial?.stamps ?: 0).toString()) }
    var formError by remember(initial?.id) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "เพิ่มสมาชิก" else "แก้ไขสมาชิก") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it.take(120) }, label = { Text("ชื่อสมาชิก") }, singleLine = true)
                OutlinedTextField(phone, { phone = it.filter(Char::isDigit).take(10) }, label = { Text("เบอร์โทร") }, singleLine = true)
                OutlinedTextField(email, { email = it.take(120) }, label = { Text("อีเมล") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(points, { points = it.filter(Char::isDigit).take(8) }, label = { Text("คะแนน") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(stamps, { stamps = it.filter(Char::isDigit).take(8) }, label = { Text("แต้ม") }, modifier = Modifier.weight(1f), singleLine = true)
                }
                formError?.let { Text(it, color = Color(0xFFB13F4C), fontSize = 11.sp) }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                enabled = !busy,
                onClick = {
                    val digits = phone.filter(Char::isDigit)
                    val p = points.toIntOrNull()
                    val s = stamps.toIntOrNull()
                    when {
                        name.isBlank() -> formError = "กรุณากรอกชื่อสมาชิก"
                        digits.length !in 9..10 -> formError = "กรุณากรอกเบอร์โทร 9-10 หลัก"
                        p == null || s == null -> formError = "คะแนน/แต้มไม่ถูกต้อง"
                        else -> {
                            formError = null
                            onSave(name, digits, email, p, s)
                        }
                    }
                }
            ) { Text("บันทึก") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("ยกเลิก") } }
    )
}

@Composable
private fun MoreTaxInvoiceScreen(
    client: WebPosClient,
    session: WebPosSession,
    onBack: () -> Unit,
    onTab: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var rows by remember(session.id) { mutableStateOf<List<WebTaxReceiptRow>>(emptyList()) }
    var profiles by remember(session.id) { mutableStateOf<List<WebTaxProfileRow>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<WebTaxReceiptRow?>(null) }
    var printing by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }

    LaunchedEffect(client, session.id, submitted, refresh) {
        loading = true
        try {
            rows = client.taxReceipts(session, submitted)
            profiles = client.taxProfiles(session)
            error = null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = "โหลดข้อมูลใบกำกับภาษีไม่ได้: " + ((failure as? WebPosApiException)?.code ?: "network_error")
        } finally { loading = false }
    }

    MoreScaffold("ออกใบกำกับภาษี", session, onBack, onTab) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it.take(80) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("ค้นหาเลขบิล") },
                singleLine = true,
                trailingIcon = { TextButton(onClick = { submitted = query.trim() }) { Text("ค้นหา") } }
            )
        }
        if (loading) item { LoadingBlock() }
        else if (error != null) item { ErrorBlock(error.orEmpty()) }
        else {
            item {
                Text(
                    if (profiles.isEmpty())
                        "ยังไม่มีข้อมูลผู้เสียภาษีของลูกค้าในสาขานี้ · ต้องสร้างข้อมูลผู้เสียภาษีในทะเบียนก่อนออกใบกำกับ"
                    else "แตะบิลเพื่อเลือกผู้เสียภาษีและยืนยัน PIN Owner/Manager ก่อนออก/พิมพ์",
                    color = moreMuted,
                    fontSize = 10.sp
                )
            }
            item { TableHeader(listOf("เลขบิล" to 1.5f, "ลูกค้า" to 1.1f, "ยอด" to .9f, "ใบกำกับ" to 1f)) }
            items(rows, key = { it.id }) { row ->
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().clickable(enabled = !printing && profiles.isNotEmpty()) {
                        selected = row
                    }
                ) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 11.dp)) {
                        Text(row.orderNo, Modifier.weight(1.5f), fontSize = 10.sp, color = moreInk, maxLines = 2)
                        Text(row.customerName.ifBlank { "-" }, Modifier.weight(1.1f), fontSize = 10.sp, color = moreInk, maxLines = 2)
                        Text("฿" + row.total.bahtText(), Modifier.weight(.9f), fontSize = 10.sp, color = moreBlue)
                        Text(row.invoiceNo ?: "ยังไม่ออก", Modifier.weight(1f), fontSize = 10.sp, color = moreInk, maxLines = 2)
                    }
                }
            }
            if (rows.isEmpty()) item { EmptyBlock("ไม่พบบิลสำหรับออกใบกำกับภาษี") }
        }
    }

    selected?.let { row ->
        TaxInvoicePrintDialog(
            receipt = row,
            profiles = profiles,
            busy = printing,
            onDismiss = { if (!printing) selected = null },
            onPrint = { profileId, pin ->
                printing = true
                scope.launch {
                    try {
                        client.printTaxInvoice(session, profileId, row.id, pin)
                        NativePrintRuntime.wake()
                        selected = null
                        refresh += 1
                    } catch (failure: Exception) {
                        error = "ออก/พิมพ์ใบกำกับภาษีไม่สำเร็จ: " +
                            ((failure as? WebPosApiException)?.code ?: "printer_or_network_error")
                    } finally { printing = false }
                }
            }
        )
    }
}

@Composable
private fun TaxInvoicePrintDialog(
    receipt: WebTaxReceiptRow,
    profiles: List<WebTaxProfileRow>,
    busy: Boolean,
    onDismiss: () -> Unit,
    onPrint: (String, String) -> Unit
) {
    var profileId by remember(receipt.id) {
        mutableStateOf(receipt.invoiceProfileId?.takeIf { id -> profiles.any { it.id == id } } ?: profiles.firstOrNull()?.id.orEmpty())
    }
    var expanded by remember { mutableStateOf(false) }
    var pin by remember(receipt.id) { mutableStateOf("") }
    var formError by remember(receipt.id) { mutableStateOf<String?>(null) }
    val profile = profiles.firstOrNull { it.id == profileId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text((if (receipt.invoiceNo == null) "ออกใบกำกับภาษี" else "พิมพ์ใบกำกับภาษี") + " " + receipt.orderNo) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("ผู้เสียภาษี", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Box {
                    OutlinedButton(
                        onClick = { expanded = true },
                        enabled = !busy && receipt.invoiceProfileId == null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(profile?.displayName ?: "เลือกผู้เสียภาษี")
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        profiles.forEach { row ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(row.displayName)
                                        Text(row.taxId, fontSize = 10.sp, color = moreMuted)
                                    }
                                },
                                onClick = {
                                    profileId = row.id
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                profile?.let {
                    Text("เลขผู้เสียภาษี " + it.taxId, fontSize = 10.sp, color = moreMuted)
                }
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter(Char::isDigit).take(12) },
                    label = { Text("PIN Owner/Manager") },
                    singleLine = true
                )
                formError?.let { Text(it, color = Color(0xFFB13F4C), fontSize = 11.sp) }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                enabled = !busy,
                onClick = {
                    when {
                        profileId.isBlank() -> formError = "กรุณาเลือกผู้เสียภาษี"
                        pin.length < 4 -> formError = "กรุณากรอก PIN อย่างน้อย 4 หลัก"
                        else -> {
                            formError = null
                            onPrint(profileId, pin)
                        }
                    }
                }
            ) { Text(if (receipt.invoiceNo == null) "ออกและพิมพ์" else "พิมพ์อีกครั้ง") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("ยกเลิก") } }
    )
}

@Composable
private fun MoreScaffold(
    title: String,
    session: WebPosSession,
    onBack: () -> Unit,
    onTab: (String) -> Unit,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    Surface(Modifier.fillMaxSize(), color = Color(0xFFF5F9FF)) {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().statusBarsPadding()
                    .padding(horizontal = 13.dp)
                    .padding(top = 8.dp, bottom = 108.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
                content = {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = onBack) { Text("‹ เพิ่มเติม") }
                            Column(Modifier.weight(1f)) {
                                Text(title, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = moreInk)
                                Text(session.branchName + " · CpIPOS", fontSize = 11.sp, color = moreMuted)
                            }
                        }
                    }
                    content()
                    item { Spacer(Modifier.height(10.dp)) }
                }
            )
            HomeBottomMenu(
                modifier = Modifier.align(Alignment.BottomCenter),
                selectedTab = "history",
                onMenuClick = onTab
            )
        }
    }
}

@Composable
private fun MoreMetricTable(rows: List<Pair<String, String>>) {
    Surface(color = Color.White, shape = RoundedCornerShape(17.dp), modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(Modifier.fillMaxWidth().padding(12.dp)) {
                Text("หัวข้อ", Modifier.weight(1f), fontWeight = FontWeight.Bold, color = moreInk)
                Text("ยอด", Modifier.weight(1f), fontWeight = FontWeight.Bold, color = moreInk)
            }
            HorizontalDivider(color = Color(0xFFE7EEF8))
            rows.forEach { row ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Text(row.first, Modifier.weight(1f), color = moreInk, fontSize = 12.sp)
                    Text(row.second, Modifier.weight(1f), color = moreBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                HorizontalDivider(color = Color(0xFFF0F3F8))
            }
        }
    }
}

@Composable
private fun TableHeader(columns: List<Pair<String, Float>>) {
    Surface(color = Color(0xFFE5EEFB), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp)) {
            columns.forEach { column ->
                Text(column.first, Modifier.weight(column.second), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = moreInk)
            }
        }
    }
}

@Composable
private fun TableRow(columns: List<Pair<String, Float>>) {
    Surface(color = Color.White, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            columns.forEach { column ->
                Text(
                    column.first,
                    Modifier.weight(column.second),
                    fontSize = 10.sp,
                    color = moreInk,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun LoadingBlock() {
    Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = moreBlue)
    }
}

@Composable
private fun ErrorBlock(message: String) {
    Surface(color = Color(0xFFFFEEEE), shape = RoundedCornerShape(13.dp), modifier = Modifier.fillMaxWidth()) {
        Text(message, Modifier.padding(12.dp), color = Color(0xFFB13F4C), fontSize = 11.sp)
    }
}

@Composable
private fun EmptyBlock(message: String) {
    Box(Modifier.fillMaxWidth().height(110.dp), contentAlignment = Alignment.Center) {
        Text(message, color = moreMuted, fontSize = 12.sp)
    }
}

private fun formatQty(value: Double): String =
    if (kotlin.math.abs(value - kotlin.math.round(value)) < 0.0001)
        kotlin.math.round(value).toLong().toString()
    else String.format(java.util.Locale.US, "%.3f", value).trimEnd('0').trimEnd('.')
