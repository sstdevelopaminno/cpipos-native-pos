package com.cpipos.pos.next.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cpipos.pos.next.core.print.NativePrintRuntime
import com.cpipos.pos.next.core.webpos.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
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


private data class MoreDateWindow(val start: LocalDate, val end: LocalDate)

private fun moreDateWindow(
    mode: String,
    anchor: LocalDate,
    customFrom: LocalDate,
    customTo: LocalDate
): MoreDateWindow = when (mode) {
    "day" -> MoreDateWindow(anchor, anchor)
    "month" -> MoreDateWindow(anchor.withDayOfMonth(1), anchor.withDayOfMonth(anchor.lengthOfMonth()))
    "year" -> MoreDateWindow(anchor.withDayOfYear(1), anchor.withDayOfYear(anchor.lengthOfYear()))
    else -> MoreDateWindow(customFrom, customTo)
}

private fun shiftMoreAnchor(mode: String, value: LocalDate, amount: Long): LocalDate = when (mode) {
    "month" -> value.plusMonths(amount)
    "year" -> value.plusYears(amount)
    else -> value.plusDays(amount)
}

private val moreDateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.US)
private val moreDateTimeFormat = DateTimeFormatter.ofPattern("dd/MM/yy HH:mm", Locale.US)
private val moreBangkok = ZoneId.of("Asia/Bangkok")

private fun moreDateTime(epochMs: Long): String =
    Instant.ofEpochMilli(epochMs).atZone(moreBangkok).format(moreDateTimeFormat)

private fun thaiTaxIdValid(value: String): Boolean {
    val digits = value.filter(Char::isDigit)
    if (digits.length != 13) return false
    var sum = 0
    for (index in 0 until 12) sum += (digits[index] - '0') * (13 - index)
    return ((11 - (sum % 11)) % 10) == (digits[12] - '0')
}

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
private fun MorePeriodControls(
    mode: String,
    anchor: LocalDate,
    customFrom: LocalDate,
    customTo: LocalDate,
    onModeChange: (String) -> Unit,
    onAnchorChange: (LocalDate) -> Unit,
    onCustomRange: (LocalDate, LocalDate) -> Unit
) {
    val context = LocalContext.current
    var rangeDialog by remember { mutableStateOf(false) }
    var draftFrom by remember(customFrom, rangeDialog) { mutableStateOf(customFrom) }
    var draftTo by remember(customTo, rangeDialog) { mutableStateOf(customTo) }
    val window = moreDateWindow(mode, anchor, customFrom, customTo)

    fun pickDate(current: LocalDate, onSelected: (LocalDate) -> Unit) {
        DatePickerDialog(
            context,
            { _, year, month, day -> onSelected(LocalDate.of(year, month + 1, day)) },
            current.year,
            current.monthValue - 1,
            current.dayOfMonth
        ).show()
    }

    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            listOf("day" to "รายวัน", "month" to "รายเดือน", "year" to "รายปี").forEach { option ->
                val selected = mode == option.first
                Surface(
                    modifier = Modifier.weight(1f).clickable { onModeChange(option.first) },
                    color = if (selected) moreBlue else Color.White,
                    shape = RoundedCornerShape(14.dp),
                    tonalElevation = if (selected) 2.dp else 0.dp
                ) {
                    Text(
                        option.second,
                        modifier = Modifier.padding(vertical = 11.dp),
                        color = if (selected) Color.White else moreInk,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(
                enabled = mode != "custom",
                onClick = { onAnchorChange(shiftMoreAnchor(mode, anchor, -1)) }
            ) { Text("‹", fontSize = 23.sp, color = moreBlue) }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (window.start == window.end)
                        window.start.format(moreDateFormat)
                    else window.start.format(moreDateFormat) + " – " + window.end.format(moreDateFormat),
                    color = moreInk,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    when (mode) {
                        "day" -> "สรุปรายวัน"
                        "month" -> "สรุปรายเดือน"
                        "year" -> "สรุปรายปี"
                        else -> "ช่วงวันที่กำหนด"
                    },
                    color = moreMuted,
                    fontSize = 10.sp
                )
            }
            TextButton(
                enabled = mode != "custom",
                onClick = { onAnchorChange(shiftMoreAnchor(mode, anchor, 1)) }
            ) { Text("›", fontSize = 23.sp, color = moreBlue) }
        }
        OutlinedButton(
            onClick = {
                draftFrom = customFrom
                draftTo = customTo
                rangeDialog = true
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("ปฏิทิน  เลือกวันที่เริ่มต้น – วันที่สิ้นสุด", color = moreBlue, fontSize = 12.sp)
        }
    }

    if (rangeDialog) {
        AlertDialog(
            onDismissRequest = { rangeDialog = false },
            title = { Text("เลือกช่วงวันที่", fontWeight = FontWeight.ExtraBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { pickDate(draftFrom) { draftFrom = it } },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("วันที่เริ่มต้น  " + draftFrom.format(moreDateFormat))
                    }
                    OutlinedButton(
                        onClick = { pickDate(draftTo) { draftTo = it } },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("วันที่สิ้นสุด  " + draftTo.format(moreDateFormat))
                    }
                    if (draftTo.isBefore(draftFrom)) {
                        Text("วันที่สิ้นสุดต้องไม่น้อยกว่าวันที่เริ่มต้น", color = Color(0xFFB13F4C), fontSize = 11.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !draftTo.isBefore(draftFrom),
                    onClick = {
                        onCustomRange(draftFrom, draftTo)
                        rangeDialog = false
                    }
                ) { Text("ใช้ช่วงวันที่") }
            },
            dismissButton = {
                TextButton(onClick = { rangeDialog = false }) { Text("ยกเลิก") }
            }
        )
    }
}

@Composable
private fun SummaryMetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false
) {
    Surface(
        modifier = modifier,
        color = if (accent) Color(0xFFEAF3FF) else Color.White,
        shape = RoundedCornerShape(17.dp),
        shadowElevation = 1.dp
    ) {
        Column(Modifier.padding(13.dp)) {
            Text(label, color = moreMuted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(5.dp))
            Text(
                value,
                color = if (accent) moreBlue else moreInk,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
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
    val today = remember { LocalDate.now(moreBangkok) }
    var mode by remember { mutableStateOf("day") }
    var anchor by remember { mutableStateOf(today) }
    var customFrom by remember { mutableStateOf(today.minusDays(6)) }
    var customTo by remember { mutableStateOf(today) }
    var data by remember(session.id) { mutableStateOf<WebSalesSummarySnapshot?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    val window = moreDateWindow(mode, anchor, customFrom, customTo)

    LaunchedEffect(client, session.id, window.start, window.end, refresh) {
        loading = true
        try {
            data = client.salesSummary(session, window.start, window.end)
            error = null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = "โหลดสรุปยอดขายไม่ได้: " + ((failure as? WebPosApiException)?.code ?: "network_error")
        } finally {
            loading = false
        }
    }

    MoreScaffold("สรุปยอดขาย", session, onBack, onTab) {
        item {
            MorePeriodControls(
                mode = mode,
                anchor = anchor,
                customFrom = customFrom,
                customTo = customTo,
                onModeChange = { mode = it },
                onAnchorChange = { anchor = it },
                onCustomRange = { from, to ->
                    customFrom = from
                    customTo = to
                    mode = "custom"
                }
            )
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { refresh += 1 }, enabled = !loading) { Text("↻ รีเฟรช") }
            }
        }
        if (loading) {
            item { LoadingBlock() }
        } else if (error != null) {
            item { ErrorBlock(error.orEmpty()) }
        } else {
            val snapshot = data
            if (snapshot != null) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF176DED),
                        shape = RoundedCornerShape(22.dp),
                        shadowElevation = 3.dp
                    ) {
                        Column(Modifier.padding(18.dp)) {
                            Text("ยอดขายสุทธิ", color = Color(0xFFDCEBFF), fontSize = 12.sp)
                            Text(
                                "฿" + snapshot.netSales.bahtText(),
                                color = Color.White,
                                fontSize = 30.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Spacer(Modifier.height(5.dp))
                            Text(
                                snapshot.receiptCount.toString() + " บิล · " +
                                    window.start.format(moreDateFormat) + " – " +
                                    window.end.format(moreDateFormat),
                                color = Color(0xFFEAF3FF),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        SummaryMetricCard("ยอดขายรวม", "฿" + snapshot.grossSales.bahtText(), Modifier.weight(1f))
                        SummaryMetricCard("เฉลี่ยต่อบิล", "฿" + snapshot.averageReceipt.bahtText(), Modifier.weight(1f), true)
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        SummaryMetricCard("เงินสด", "฿" + snapshot.cashTotal.bahtText(), Modifier.weight(1f))
                        SummaryMetricCard("โอน / QR", "฿" + snapshot.transferTotal.bahtText(), Modifier.weight(1f), true)
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        SummaryMetricCard("ส่วนลด", "฿" + snapshot.discountTotal.bahtText(), Modifier.weight(1f))
                        SummaryMetricCard("ภาษี", "฿" + snapshot.taxTotal.bahtText(), Modifier.weight(1f))
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        SummaryMetricCard("ยกเลิก", "฿" + snapshot.cancelledTotal.bahtText(), Modifier.weight(1f))
                        SummaryMetricCard("สกุลเงิน", snapshot.currencyCode, Modifier.weight(1f), true)
                    }
                }
            }
        }
    }
}

@Composable
private fun BestSellerCard(
    rank: Int,
    row: WebBestSellingProduct,
    large: Boolean = false
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (rank == 1) Color(0xFFEAF3FF) else Color.White,
        shape = RoundedCornerShape(if (large) 22.dp else 18.dp),
        shadowElevation = if (large) 3.dp else 1.dp
    ) {
        Row(
            Modifier.fillMaxWidth().padding(if (large) 17.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(if (large) 54.dp else 45.dp),
                shape = RoundedCornerShape(16.dp),
                color = if (rank == 1) moreBlue else Color(0xFFF0F4FA)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        "#" + rank,
                        color = if (rank == 1) Color.White else moreBlue,
                        fontSize = if (large) 18.sp else 15.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    row.productName,
                    color = moreInk,
                    fontSize = if (large) 16.sp else 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(row.category, color = moreMuted, fontSize = 10.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    "ขาย " + formatQty(row.quantitySold) + " · ยอดสุทธิ ฿" + row.netAmount.bahtText(),
                    color = if (rank == 1) moreBlue else moreInk,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
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
    var showMore by remember { mutableStateOf(false) }

    LaunchedEffect(client, session.id) {
        try {
            val today = LocalDate.now(moreBangkok)
            data = client.salesSummary(session, today.minusDays(29), today)
            error = null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = "โหลดสินค้าขายดีไม่ได้: " + ((failure as? WebPosApiException)?.code ?: "network_error")
        } finally {
            loading = false
        }
    }

    val rows = data?.bestSelling.orEmpty()
    val topThree = rows.take(3)
    val remaining = rows.drop(3)

    MoreScaffold("สินค้าขายดี", session, onBack, onTab) {
        item {
            Text("อันดับยอดขายสำเร็จ 30 วันล่าสุด", color = moreMuted, fontSize = 11.sp)
        }
        if (loading) {
            item { LoadingBlock() }
        } else if (error != null) {
            item { ErrorBlock(error.orEmpty()) }
        } else if (rows.isEmpty()) {
            item { EmptyBlock("ยังไม่มีข้อมูลสินค้าขายดี") }
        } else {
            topThree.forEachIndexed { index, row ->
                item(key = row.productId) {
                    BestSellerCard(rank = index + 1, row = row, large = index == 0)
                }
            }
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(17.dp),
                    color = Color.White
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            "Top 3 แสดงด้านบนเพื่อดูสินค้าหลักได้ทันที",
                            color = moreMuted,
                            fontSize = 10.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { showMore = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = moreBlue)
                        ) {
                            Text(
                                if (remaining.isEmpty()) "ดูอันดับทั้งหมด"
                                else "ดูรายการที่เหลือ (" + remaining.size + ")",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    if (showMore) {
        AlertDialog(
            onDismissRequest = { showMore = false },
            title = { Text("อันดับสินค้าขายดีทั้งหมด", fontWeight = FontWeight.ExtraBold) },
            text = {
                Box(Modifier.fillMaxWidth().heightIn(max = 460.dp)) {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        items(rows, key = { it.productId }) { row ->
                            val rank = rows.indexOf(row) + 1
                            Surface(
                                color = Color(0xFFF7F9FC),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "#" + rank,
                                        modifier = Modifier.width(34.dp),
                                        color = moreBlue,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 11.sp
                                    )
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            row.productName,
                                            color = moreInk,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 2
                                        )
                                        Text(
                                            "ขาย " + formatQty(row.quantitySold),
                                            color = moreMuted,
                                            fontSize = 9.sp
                                        )
                                    }
                                    Text(
                                        "฿" + row.netAmount.bahtText(),
                                        color = moreBlue,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMore = false }) { Text("ปิด") }
            }
        )
    }
}

@Composable
private fun ReceiptDetailDialog(
    row: WebReceiptHistoryRecord,
    onDismiss: () -> Unit,
    onPrint: () -> Unit
) {
    val receipt = row.receipt
    val subtotal = receipt.subtotal ?: receipt.lines.fold(com.cpipos.pos.next.core.pos.Satang.ZERO) { sum, line ->
        sum + line.amount
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("ใบเสร็จ " + row.orderNo, fontWeight = FontWeight.ExtraBold)
                Text(moreDateTime(row.createdAtMs), color = moreMuted, fontSize = 10.sp)
            }
        },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 470.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Surface(color = Color(0xFFF3F7FD), shape = RoundedCornerShape(13.dp)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(receipt.storeName, fontWeight = FontWeight.ExtraBold, color = moreInk)
                        Text(receipt.branchName + " · " + receipt.counterCode, fontSize = 10.sp, color = moreMuted)
                        Text(
                            receipt.modeLabel + " · " +
                                if ("bank_transfer" in row.paymentMethods) "โอน / QR" else "เงินสด",
                            fontSize = 10.sp,
                            color = moreBlue,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Text("รายการสินค้า", color = moreInk, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                receipt.lines.forEach { line ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text(line.name, color = moreInk, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text(
                                line.quantity.toString() + " × ฿" + line.unitPrice.bahtText(),
                                color = moreMuted,
                                fontSize = 9.sp
                            )
                        }
                        Text(
                            "฿" + line.amount.bahtText(),
                            color = moreInk,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    HorizontalDivider(color = Color(0xFFEDF1F7))
                }
                Surface(color = Color.White, shape = RoundedCornerShape(12.dp), tonalElevation = 1.dp) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("ยอดสินค้า", color = moreMuted, fontSize = 10.sp)
                            Text("฿" + subtotal.bahtText(), color = moreInk, fontSize = 10.sp)
                        }
                        if (receipt.discount.value > 0) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("ส่วนลด", color = moreMuted, fontSize = 10.sp)
                                Text("-฿" + receipt.discount.bahtText(), color = moreInk, fontSize = 10.sp)
                            }
                        }
                        HorizontalDivider(color = Color(0xFFE4EAF2))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("ยอดสุทธิ", color = moreInk, fontWeight = FontWeight.ExtraBold)
                            Text("฿" + receipt.total.bahtText(), color = moreBlue, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onPrint, colors = ButtonDefaults.buttonColors(containerColor = moreBlue)) {
                Text("พิมพ์ใบเสร็จ", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ปิด") } }
    )
}

@Composable
private fun MoreReceiptsScreen(
    client: WebPosClient,
    session: WebPosSession,
    onBack: () -> Unit,
    onTab: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val today = remember { LocalDate.now(moreBangkok) }
    var mode by remember { mutableStateOf("day") }
    var anchor by remember { mutableStateOf(today) }
    var customFrom by remember { mutableStateOf(today.minusDays(6)) }
    var customTo by remember { mutableStateOf(today) }
    var query by remember { mutableStateOf("") }
    var submittedQuery by remember { mutableStateOf("") }
    var page by remember { mutableIntStateOf(1) }
    var history by remember(session.id) { mutableStateOf<WebReceiptHistory?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var detail by remember { mutableStateOf<WebReceiptHistoryRecord?>(null) }
    var reprintTarget by remember { mutableStateOf<WebReceiptHistoryRecord?>(null) }
    var reprintBusy by remember { mutableStateOf(false) }
    var reprintError by remember { mutableStateOf<String?>(null) }
    val window = moreDateWindow(mode, anchor, customFrom, customTo)

    LaunchedEffect(client, session.id, window.start, window.end, submittedQuery, page, refresh) {
        loading = true
        try {
            history = client.receiptHistory(
                session = session,
                query = submittedQuery,
                from = window.start,
                to = window.end,
                page = page,
                pageSize = 30
            )
            error = null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = "โหลดใบเสร็จย้อนหลังไม่ได้: " + ((failure as? WebPosApiException)?.code ?: "network_error")
        } finally {
            loading = false
        }
    }

    fun setMode(value: String) {
        mode = value
        page = 1
    }

    MoreScaffold("ใบเสร็จย้อนหลัง", session, onBack, onTab) {
        item {
            MorePeriodControls(
                mode = mode,
                anchor = anchor,
                customFrom = customFrom,
                customTo = customTo,
                onModeChange = { setMode(it) },
                onAnchorChange = {
                    anchor = it
                    page = 1
                },
                onCustomRange = { from, to ->
                    customFrom = from
                    customTo = to
                    mode = "custom"
                    page = 1
                }
            )
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it.take(80) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("ค้นหาเลขบิล") },
                trailingIcon = {
                    TextButton(onClick = {
                        submittedQuery = query.trim()
                        page = 1
                    }) { Text("ค้นหา") }
                }
            )
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "ทั้งหมด " + (history?.total ?: 0) + " บิล",
                    modifier = Modifier.weight(1f),
                    color = moreMuted,
                    fontSize = 11.sp
                )
                TextButton(onClick = { refresh += 1 }, enabled = !loading && !reprintBusy) {
                    Text("↻ รีเฟรช")
                }
            }
        }
        if (loading) {
            item { LoadingBlock() }
        } else if (error != null) {
            item { ErrorBlock(error.orEmpty()) }
        } else {
            items(history?.records.orEmpty(), key = { it.orderId }) { row ->
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable(enabled = !reprintBusy) { detail = row },
                    color = Color.White,
                    shape = RoundedCornerShape(17.dp),
                    shadowElevation = 1.dp
                ) {
                    Column(Modifier.fillMaxWidth().padding(13.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                row.orderNo,
                                modifier = Modifier.weight(1f),
                                color = moreInk,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 2
                            )
                            Text(
                                "฿" + row.receipt.total.bahtText(),
                                color = moreBlue,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        Spacer(Modifier.height(5.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(moreDateTime(row.createdAtMs), color = moreMuted, fontSize = 9.sp)
                            Text(
                                row.receipt.modeLabel + " · " +
                                    if ("bank_transfer" in row.paymentMethods) "โอน/QR" else "เงินสด",
                                color = moreMuted,
                                fontSize = 9.sp
                            )
                        }
                        Spacer(Modifier.height(7.dp))
                        Text("แตะเพื่อดูใบเสร็จและพิมพ์ย้อนหลัง", color = moreBlue, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (history?.records.isNullOrEmpty()) {
                item { EmptyBlock("ไม่พบใบเสร็จในช่วงวันที่นี้") }
            }
            if ((history?.totalPages ?: 1) > 1) {
                item {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { page -= 1 },
                            enabled = page > 1 && !loading
                        ) { Text("ก่อนหน้า") }
                        Text(
                            "หน้า " + page + " / " + (history?.totalPages ?: 1),
                            color = moreMuted,
                            fontSize = 10.sp
                        )
                        OutlinedButton(
                            onClick = { page += 1 },
                            enabled = page < (history?.totalPages ?: 1) && !loading
                        ) { Text("ถัดไป") }
                    }
                }
            }
        }
    }

    detail?.let { row ->
        ReceiptDetailDialog(
            row = row,
            onDismiss = { detail = null },
            onPrint = {
                detail = null
                reprintError = null
                reprintTarget = row
            }
        )
    }

    reprintTarget?.let { row ->
        NativeCancelBillPinDialog(
            title = "พิมพ์ใบเสร็จย้อนหลัง " + row.orderNo,
            busy = reprintBusy,
            error = reprintError,
            actionLabel = "ตรวจ PIN และพิมพ์",
            actionNote = "ใช้เครื่องพิมพ์ Receipt/Reprint และสิทธิ์ Owner/Manager ตาม CpIPOS",
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
                            "forbidden_role" -> "บัญชีนี้ไม่มีสิทธิ์พิมพ์ย้อนหลัง"
                            "receipt_printer_not_configured" -> "ยังไม่ได้ตั้งค่าเครื่องพิมพ์ใบเสร็จย้อนหลัง"
                            else -> "พิมพ์ย้อนหลังไม่สำเร็จ: " + (api?.code ?: "printer_or_network_error")
                        }
                    } finally {
                        reprintBusy = false
                    }
                }
            }
        )
    }
}

@Composable
private fun TableCreateDialog(
    bulk: Boolean,
    busy: Boolean,
    onDismiss: () -> Unit,
    onCreate: (String, String, Int, Int) -> Unit
) {
    var zoneName by remember(bulk) { mutableStateOf("") }
    var tableName by remember(bulk) { mutableStateOf("") }
    var capacity by remember(bulk) { mutableStateOf("4") }
    var count by remember(bulk) { mutableStateOf(if (bulk) "5" else "1") }
    var formError by remember(bulk) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    if (bulk) "เพิ่มโต๊ะหลายรายการ" else "เพิ่มโต๊ะทีละรายการ",
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    "ฟอร์มเดียวกับ CpIPOS · เลขโต๊ะสร้างอัตโนมัติ",
                    color = moreMuted,
                    fontSize = 10.sp
                )
            }
        },
        text = {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                OutlinedTextField(
                    value = zoneName,
                    onValueChange = { zoneName = it.take(80) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("โซน") },
                    supportingText = { Text("ถ้าโซนยังไม่มี ระบบ CpIPOS จะสร้างให้") }
                )
                OutlinedTextField(
                    value = tableName,
                    onValueChange = { tableName = it.take(100) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(if (bulk) "ชื่อโต๊ะ / คำนำหน้าชื่อ" else "ชื่อโต๊ะ") },
                    supportingText = {
                        if (bulk) Text("เช่น โต๊ะ VIP → โต๊ะ VIP 1, โต๊ะ VIP 2 ...")
                    }
                )
                OutlinedTextField(
                    value = capacity,
                    onValueChange = { capacity = it.filter(Char::isDigit).take(3) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("จำนวนที่นั่ง") }
                )
                if (bulk) {
                    OutlinedTextField(
                        value = count,
                        onValueChange = { count = it.filter(Char::isDigit).take(2) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("จำนวนโต๊ะที่ต้องการเพิ่ม") },
                        supportingText = { Text("เพิ่มได้ครั้งละ 1–50 โต๊ะ") }
                    )
                }
                formError?.let {
                    Text(it, color = Color(0xFFB13F4C), fontSize = 11.sp)
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                enabled = !busy,
                onClick = {
                    val seats = capacity.toIntOrNull()
                    val total = if (bulk) count.toIntOrNull() else 1
                    when {
                        seats == null || seats !in 1..100 -> formError = "จำนวนที่นั่งต้องอยู่ระหว่าง 1-100"
                        total == null || total !in 1..50 -> formError = "จำนวนโต๊ะต้องอยู่ระหว่าง 1-50"
                        else -> {
                            formError = null
                            onCreate(zoneName.trim(), tableName.trim(), seats, total)
                        }
                    }
                }
            ) {
                Text(if (bulk) "เพิ่ม " + (count.ifBlank { "หลาย" }) + " โต๊ะ" else "เพิ่มโต๊ะ")
            }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) { Text("ยกเลิก") }
        }
    )
}

@Composable
private fun MoreTablesScreen(
    client: WebPosClient,
    session: WebPosSession,
    onBack: () -> Unit,
    onTab: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var floor by remember(session.id) { mutableStateOf<WebTableFloor?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var createMode by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var success by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(client, session.id, refresh) {
        loading = true
        try {
            floor = client.diningTables(session)
            error = null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = "โหลดข้อมูลโต๊ะไม่ได้: " + ((failure as? WebPosApiException)?.code ?: "network_error")
        } finally {
            loading = false
        }
    }

    MoreScaffold("จัดการโต๊ะ", session, onBack, onTab) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("ข้อมูลโต๊ะ/โซนชุดเดียวกับ CpIPOS", modifier = Modifier.weight(1f), color = moreMuted, fontSize = 11.sp)
                TextButton(onClick = { refresh += 1 }, enabled = !loading && !saving) { Text("↻ รีเฟรช") }
            }
        }
        if (session.canManageTables) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Button(
                        onClick = { createMode = "single" },
                        modifier = Modifier.weight(1f),
                        enabled = !saving,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = moreBlue)
                    ) {
                        Text("+ เพิ่มทีละรายการ", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = { createMode = "bulk" },
                        modifier = Modifier.weight(1f),
                        enabled = !saving,
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("+ เพิ่มหลายรายการ", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            item {
                Surface(color = Color(0xFFFFF7E7), shape = RoundedCornerShape(13.dp)) {
                    Text(
                        "บัญชีนี้ดูสถานะโต๊ะได้ แต่ไม่มีสิทธิ์ tables:manage สำหรับเพิ่มโต๊ะ",
                        Modifier.padding(12.dp),
                        color = Color(0xFF8A5A00),
                        fontSize = 10.sp
                    )
                }
            }
        }
        success?.let { message ->
            item {
                Surface(color = Color(0xFFEAF8EF), shape = RoundedCornerShape(13.dp)) {
                    Text(message, Modifier.padding(12.dp), color = Color(0xFF237A45), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (loading) {
            item { LoadingBlock() }
        } else if (error != null) {
            item { ErrorBlock(error.orEmpty()) }
        } else {
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
            if (floor?.tables.isNullOrEmpty()) {
                item { EmptyBlock("ยังไม่มีโต๊ะในสาขานี้") }
            }
        }
    }

    createMode?.let { mode ->
        TableCreateDialog(
            bulk = mode == "bulk",
            busy = saving,
            onDismiss = { if (!saving) createMode = null },
            onCreate = { zoneName, tableName, capacity, count ->
                saving = true
                error = null
                success = null
                scope.launch {
                    try {
                        val created = client.createDiningTables(
                            session = session,
                            zoneName = zoneName,
                            tableName = tableName,
                            capacity = capacity,
                            count = count
                        )
                        success = "เพิ่มโต๊ะสำเร็จ " + created + " รายการ"
                        createMode = null
                        refresh += 1
                    } catch (failure: Exception) {
                        val api = failure as? WebPosApiException
                        error = when (api?.code) {
                            "forbidden_role", "permission_denied" -> "บัญชีนี้ไม่มีสิทธิ์เพิ่มโต๊ะ"
                            "feature_not_enabled" -> "แพ็กเกจนี้ยังไม่เปิดระบบจัดการโต๊ะ"
                            "http_404" -> "CpIPOS Server ยังไม่รองรับการเพิ่มโต๊ะจาก POS มือถือรุ่นนี้"
                            else -> "เพิ่มโต๊ะไม่สำเร็จ: " + (api?.code ?: "network_error")
                        }
                    } finally {
                        saving = false
                    }
                }
            }
        )
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
