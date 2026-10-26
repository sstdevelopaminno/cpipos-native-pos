package com.cpipos.pos.next.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cpipos.pos.next.R
import com.cpipos.pos.next.core.print.NativePrintRuntime
import com.cpipos.pos.next.core.pos.Satang
import com.cpipos.pos.next.core.webpos.*
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
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
private val moreMoneyFormat = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.US))
private fun Satang.moreMoneyText(): String = moreMoneyFormat.format(value.toBigDecimal().movePointLeft(2))

private data class MoreMenu(
    val key: String,
    val iconRes: Int,
    val title: String,
    val subtitle: String,
    val featureCode: String?,
    val menuKey: String,
    val opticalRoute: String? = null
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
    onTab: (String) -> Unit,
    onSessionChanged: (WebPosSession) -> Unit = {},
    entitlements: WebPosEntitlements? = null
) {
    var page by remember(session.id) { mutableStateOf("hub") }
    var packageLockTarget by remember(session.id) { mutableStateOf<String?>(null) }

    when (page) {
        "shift" -> {
            MoreShiftManagementScreen(
                client = client,
                session = session,
                onBack = { page = "hub" },
                onTab = onTab,
                onSessionChanged = onSessionChanged
            )
            return
        }
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
        "billing" -> {
            NativeSubscriptionBillingScreen(client, session, onBack = { page = "hub" }, onTab = onTab)
            return
        }
    }

    val menus = listOf(
        MoreMenu("shift", R.drawable.ic_nav_history, nativeUiText("more.shift","เปิด / ปิดกะ"), "เปิดกะ ปิดกะ และดูประวัติกะตามสิทธิ์ของผู้ใช้งาน", null, "main.shift"),
        MoreMenu("summary", R.drawable.ic_nav_report, nativeUiText("more.summary","สรุปยอดขาย"), "ยอดขายสุทธิ กราฟแนวโน้ม ช่องทางชำระเงิน และส่วนลด", "advanced_sales_reports", "more.sales_summary", "/preview/pos/sales-summary"),
        MoreMenu("receipts", R.drawable.ic_receipt_bill, nativeUiText("more.receipts","ใบเสร็จย้อนหลัง"), "ค้นหา เปิดดูใบเสร็จจริง และพิมพ์ย้อนหลังตามสิทธิ์", "receipt_reprint_history", "more.receipts", "/preview/pos/receipts"),
        MoreMenu("tables", R.drawable.ic_nav_tables, nativeUiText("more.tables","จัดการโต๊ะ"), "โต๊ะ โซน สถานะ และบิลที่เปิดอยู่", "table_management", "more.tables"),
        MoreMenu("sales", R.drawable.ic_nav_cart, nativeUiText("more.sales","รายการขายสินค้า"), "รายการขายรายวัน / เดือน / ปี และช่วงวันที่", "advanced_sales_reports", "more.product_sales", "/preview/pos/product-sales"),
        MoreMenu("best", R.drawable.ic_nav_stock, nativeUiText("more.best","สินค้าขายดี"), "อันดับสินค้าขายดีจากข้อมูลสรุปยอดขาย CpIPOS", "advanced_sales_reports", "more.sales_summary", "/preview/pos/sales-summary"),
        MoreMenu("members", R.drawable.ic_store_front, nativeUiText("more.members","สมาชิก"), "ข้อมูลสมาชิก คะแนน และแต้มสะสม", "core_pos_sales", "more.members"),
        MoreMenu("tax", R.drawable.ic_pos_terminal, nativeUiText("more.tax","ออกใบกำกับภาษี"), "บิลที่ชำระแล้วและสถานะใบกำกับภาษี", "core_pos_sales", "more.tax_invoices"),
        MoreMenu("billing", R.drawable.ic_package_payment, nativeUiText("more.billing","ชำระแพ็กเกจ"), "ดูแพ็กเกจ ต่ออายุ แจ้งชำระด้วยสลิป และติดตามสถานะจาก CpIPOS / IT", null, "more.subscription_billing")
    )
    Surface(Modifier.fillMaxSize(), color = Color(0xFFF5F9FF)) {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().statusBarsPadding()
                    .padding(horizontal = 14.dp)
                    .padding(top = 12.dp, bottom = 156.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                item {
                    Text(nativeUiText("more.title","เพิ่มเติม"), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = moreInk)
                    Text(session.branchName + " · " + session.deviceCode, fontSize = 11.sp, color = moreMuted)
                    Spacer(Modifier.height(9.dp))
                }
                items(menus, key = { it.key }) { menu ->
                    val allowed = entitlements?.allows(
                        featureCode = menu.featureCode,
                        menuKey = menu.menuKey,
                        parentMenuKey = if (menu.menuKey.startsWith("more.")) "main.more" else null,
                        opticalRoute = menu.opticalRoute
                    ) ?: true
                    Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(17.dp),
                        shadowElevation = 1.dp,
                        modifier = Modifier.fillMaxWidth().clickable {
                            if (allowed) page = menu.key else packageLockTarget = menu.title
                        }
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
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        painter = painterResource(menu.iconRes),
                                        contentDescription = menu.title,
                                        tint = moreBlue,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
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
                            if (!allowed) {
                                Surface(
                                    color = Color(0xFFFFF3DD),
                                    shape = RoundedCornerShape(20.dp)
                                ) {
                                    Text(
                                        "แพ็กเกจ",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        color = Color(0xFF9B6518),
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(Modifier.width(6.dp))
                            }
                            Text("›", color = if (allowed) moreBlue else moreMuted, fontSize = 25.sp)
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

    NativePackageLockDialog(
        targetLabel = packageLockTarget,
        onDismiss = { packageLockTarget = null }
    )
}

@Composable
private fun MoreShiftManagementScreen(
    client: WebPosClient,
    session: WebPosSession,
    onBack: () -> Unit,
    onTab: (String) -> Unit,
    onSessionChanged: (WebPosSession) -> Unit
) {
    val scope = rememberCoroutineScope()
    var liveSession by remember(session.id) { mutableStateOf(session) }
    var days by remember(session.id) { mutableIntStateOf(30) }
    var selectedBranchId by remember(session.id) { mutableStateOf("all") }
    var snapshot by remember(session.id) { mutableStateOf<WebShiftHistorySnapshot?>(null) }
    var loading by remember(session.id) { mutableStateOf(true) }
    var refresh by remember(session.id) { mutableIntStateOf(0) }
    var error by remember(session.id) { mutableStateOf<String?>(null) }
    var message by remember(session.id) { mutableStateOf<String?>(null) }
    var branchMenuOpen by remember { mutableStateOf(false) }
    var filterDialog by remember { mutableStateOf(false) }
    var summaryDialog by remember { mutableStateOf(false) }
    var openDialog by remember { mutableStateOf(false) }
    var closeDialog by remember { mutableStateOf(false) }
    var openingCash by remember { mutableStateOf("0") }
    var closingCash by remember { mutableStateOf("") }
    var actionBusy by remember { mutableStateOf(false) }

    LaunchedEffect(session.shiftId, session.canOpenShift, session.canCloseShift) {
        liveSession = session
    }

    LaunchedEffect(client, liveSession.id, days, selectedBranchId, refresh) {
        loading = true
        try {
            snapshot = client.shiftHistory(
                session = liveSession,
                days = days,
                branchId = selectedBranchId
            )
            error = null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            val api = failure as? WebPosApiException
            error = when (api?.code) {
                "permission_denied", "forbidden_permission" ->
                    "บัญชีนี้ไม่มีสิทธิ์ดูประวัติกะ"
                else -> "โหลดข้อมูลกะไม่ได้: " + (api?.code ?: "network_error")
            }
        } finally {
            loading = false
        }
    }

    fun actionError(failure: Exception): String {
        val api = failure as? WebPosApiException
        return when (api?.code) {
            "shift_has_open_bills" ->
                "ยังมีบิลค้างอยู่ กรุณาชำระหรือยกเลิกบิลให้เรียบร้อยก่อนปิดกะ"
            "shift_close_forbidden" ->
                "พนักงานขายสามารถปิดได้เฉพาะกะของตนเอง"
            "shift_already_closed" ->
                "กะนี้ถูกปิดไปแล้ว ระบบกำลังรีเฟรชข้อมูล"
            "shift_open_failed" ->
                "เปิดกะไม่สำเร็จ กรุณาตรวจสอบสถานะกะอีกครั้ง"
            "shift_close_not_confirmed" ->
                "ยังยืนยันการปิดกะไม่ได้ กรุณารีเฟรชแล้วตรวจสอบอีกครั้ง"
            else -> "ดำเนินการไม่สำเร็จ: " + (api?.code ?: "network_error")
        }
    }

    MoreScaffold("เปิด / ปิดกะ", liveSession, onBack, onTab) {
        item {
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(18.dp),
                shadowElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (liveSession.shiftId == null) "ยังไม่เปิดกะ" else "กะปัจจุบันกำลังเปิดอยู่",
                                color = moreInk,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                liveSession.branchName + " · " + liveSession.deviceCode,
                                color = moreMuted,
                                fontSize = 10.sp
                            )
                        }
                        Surface(
                            color = if (liveSession.shiftId == null) Color(0xFFFFF3DD) else Color(0xFFE8F7EF),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Text(
                                if (liveSession.shiftId == null) "ปิด" else "เปิด",
                                modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                                color = if (liveSession.shiftId == null) Color(0xFF9B6518) else Color(0xFF198754),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { openDialog = true; message = null; error = null },
                            enabled = liveSession.shiftId == null && liveSession.canOpenShift && !actionBusy,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(13.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = moreBlue)
                        ) {
                            Text("เปิดกะ", fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = { closeDialog = true; message = null; error = null },
                            enabled = liveSession.shiftId != null && liveSession.canCloseShift && !actionBusy,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(13.dp)
                        ) {
                            Text("ปิดกะ", fontWeight = FontWeight.Bold)
                        }
                    }
                    if (!liveSession.canOpenShift || !liveSession.canCloseShift) {
                        Text(
                            "ปุ่มเปิด/ปิดกะจะแสดงตามสิทธิ์ shift:open และ shift:close ที่กำหนดจาก CpIPOS",
                            color = moreMuted,
                            fontSize = 9.sp,
                            lineHeight = 14.sp
                        )
                    }
                }
            }
        }

        if (message != null) {
            item {
                Surface(
                    color = Color(0xFFE9F8EF),
                    shape = RoundedCornerShape(13.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        message.orEmpty(),
                        Modifier.padding(12.dp),
                        color = Color(0xFF197447),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        if (error != null) {
            item { ErrorBlock(error.orEmpty()) }
        }

        item {
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(17.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("ประวัติกะ", color = moreInk, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                    Text(
                        "ตัวกรองและยอดสรุปถูกย้ายไปเป็นหน้าต่าง Popup เพื่อให้หน้าหลักดูง่ายขึ้น",
                        color = moreMuted,
                        fontSize = 10.sp
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { filterDialog = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(13.dp)
                        ) {
                            Text("ตัวกรอง", color = moreBlue, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { summaryDialog = true },
                            enabled = snapshot != null && !loading,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(13.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = moreBlue)
                        ) {
                            Text("สรุปกะ", fontWeight = FontWeight.Bold)
                        }
                    }
                    val filters = snapshot?.filters
                    val branchLabel = if (selectedBranchId == "all") {
                        "ทุกสาขา"
                    } else {
                        filters?.branchOptions?.firstOrNull { it.id == selectedBranchId }
                            ?.let { it.name ?: it.code ?: it.id }
                            ?: liveSession.branchName
                    }
                    Text(
                        "$days วัน · $branchLabel",
                        color = moreMuted,
                        fontSize = 10.sp
                    )
                }
            }
        }

        if (loading) {
            item { LoadingBlock() }
        } else {
            val data = snapshot
            if (data != null) {
                if (data.shifts.isEmpty()) {
                    item { EmptyBlock("ไม่พบประวัติกะในช่วงที่เลือก") }
                } else {
                    item {
                        Surface(
                            color = Color.White,
                            shape = RoundedCornerShape(17.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(10.dp)) {
                                Text(
                                    "ตารางเปิด / ปิดกะ",
                                    color = moreInk,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                Box(
                                    Modifier.fillMaxWidth()
                                        .horizontalScroll(rememberScrollState())
                                ) {
                                    Column(Modifier.width(980.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                        TableHeader(
                                            listOf(
                                                "สาขา" to 1.15f,
                                                "ผู้เปิด" to 1.15f,
                                                "เปิด" to 1.2f,
                                                "ปิด" to 1.2f,
                                                "สถานะ" to 0.7f,
                                                "บิล" to 0.55f,
                                                "ยอดขาย" to 1.0f,
                                                "เงินสด" to 1.0f,
                                                "โอน/QR" to 1.0f
                                            )
                                        )
                                        data.shifts.forEach { row ->
                                            TableRow(
                                                listOf(
                                                    (row.branchName ?: row.branchCode ?: "-") to 1.15f,
                                                    row.openedByName to 1.15f,
                                                    (if (row.openedAtMs > 0) moreDateTime(row.openedAtMs) else "-") to 1.2f,
                                                    (row.closedAtMs?.let(::moreDateTime) ?: "-") to 1.2f,
                                                    (if (row.status == "open") "เปิด" else "ปิด") to 0.7f,
                                                    row.metrics.orderCount.toString() to 0.55f,
                                                    ("฿" + row.metrics.salesTotal.moreMoneyText()) to 1.0f,
                                                    ("฿" + row.metrics.cashTotal.moreMoneyText()) to 1.0f,
                                                    ("฿" + row.metrics.transferTotal.moreMoneyText()) to 1.0f
                                                )
                                            )
                                        }
                                    }
                                }
                                Text(
                                    "เลื่อนตารางซ้าย–ขวาเพื่อดูข้อมูลทุกคอลัมน์",
                                    color = moreMuted,
                                    fontSize = 9.sp,
                                    modifier = Modifier.padding(top = 7.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (filterDialog) {
        AlertDialog(
            onDismissRequest = {
                branchMenuOpen = false
                filterDialog = false
            },
            title = { Text("ตัวกรองประวัติกะ", fontWeight = FontWeight.ExtraBold) },
            text = {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 430.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("ช่วงเวลา", color = moreInk, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        listOf(7, 30, 60).forEach { option ->
                            val selected = days == option
                            Surface(
                                modifier = Modifier.weight(1f).clickable {
                                    days = option
                                    message = null
                                },
                                color = if (selected) moreBlue else Color(0xFFF5F8FC),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    "$option วัน",
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    color = if (selected) Color.White else moreInk,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }

                    val filters = snapshot?.filters
                    if (filters?.canViewBranchWide == true) {
                        Text("สาขา", color = moreInk, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Box(Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { branchMenuOpen = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                val branchLabel = if (selectedBranchId == "all") {
                                    "ทุกสาขา"
                                } else {
                                    filters.branchOptions.firstOrNull { it.id == selectedBranchId }
                                        ?.let { it.name ?: it.code ?: it.id }
                                        ?: "สาขาที่เลือก"
                                }
                                Text("สาขา: $branchLabel", color = moreBlue)
                            }
                            DropdownMenu(
                                expanded = branchMenuOpen,
                                onDismissRequest = { branchMenuOpen = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("ทุกสาขา") },
                                    onClick = {
                                        selectedBranchId = "all"
                                        branchMenuOpen = false
                                    }
                                )
                                filters.branchOptions.forEach { branch ->
                                    DropdownMenuItem(
                                        text = { Text(branch.name ?: branch.code ?: branch.id) },
                                        onClick = {
                                            selectedBranchId = branch.id
                                            branchMenuOpen = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    val scopeText = when {
                        filters == null -> "กำลังตรวจสอบสิทธิ์จาก CpIPOS"
                        filters.selfOnly -> "พนักงานขาย: แสดงเฉพาะกะของตนเอง"
                        filters.canViewBranchWide -> "Owner / Manager: ดูกะทุกสาขาและเลือกกรองสาขาได้"
                        else -> "แสดงข้อมูลตามสิทธิ์ของบัญชีนี้"
                    }
                    Surface(
                        color = Color(0xFFF5F8FC),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(scopeText, Modifier.padding(11.dp), color = moreMuted, fontSize = 10.sp)
                    }
                    OutlinedButton(
                        onClick = { refresh += 1 },
                        enabled = !loading && !actionBusy,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("↻ รีเฟรชข้อมูล")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        branchMenuOpen = false
                        filterDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = moreBlue)
                ) { Text("ใช้งานตัวกรอง") }
            },
            dismissButton = {
                TextButton(onClick = {
                    branchMenuOpen = false
                    filterDialog = false
                }) { Text("ปิด") }
            }
        )
    }

    if (summaryDialog) {
        AlertDialog(
            onDismissRequest = { summaryDialog = false },
            title = { Text("สรุปกะ", fontWeight = FontWeight.ExtraBold) },
            text = {
                val data = snapshot
                if (data == null) {
                    Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = moreBlue)
                    }
                } else {
                    Column(
                        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SummaryMetricCard("จำนวนกะ", data.summary.shiftCount.toString(), Modifier.weight(1f), true)
                            SummaryMetricCard("จำนวนบิล", data.summary.orderCount.toString(), Modifier.weight(1f))
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SummaryMetricCard("ยอดขาย", "฿" + data.summary.salesTotal.moreMoneyText(), Modifier.weight(1f), true)
                            SummaryMetricCard("เงินสด", "฿" + data.summary.cashTotal.moreMoneyText(), Modifier.weight(1f))
                        }
                        SummaryMetricCard(
                            "โอน / QR",
                            "฿" + data.summary.transferTotal.moreMoneyText(),
                            Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { summaryDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = moreBlue)
                ) { Text("ปิด") }
            }
        )
    }

    if (openDialog) {
        AlertDialog(
            onDismissRequest = { if (!actionBusy) openDialog = false },
            title = { Text("เปิดกะ", fontWeight = FontWeight.ExtraBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text(
                        "ระบุเงินทอนเริ่มต้นของกะ ระบบจะใช้กะและสาขาเดียวกับ CpIPOS",
                        color = moreMuted,
                        fontSize = 11.sp
                    )
                    OutlinedTextField(
                        value = openingCash,
                        onValueChange = { openingCash = it.take(12) },
                        label = { Text("เงินทอนเริ่มต้น (บาท)") },
                        singleLine = true,
                        enabled = !actionBusy,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = !actionBusy,
                    onClick = {
                        val amount = runCatching { Satang.fromBaht(openingCash.trim().ifBlank { "0" }) }.getOrNull()
                        if (amount == null || amount < Satang.ZERO) {
                            error = "จำนวนเงินทอนเริ่มต้นไม่ถูกต้อง"
                            return@Button
                        }
                        actionBusy = true
                        scope.launch {
                            try {
                                val updated = client.openShift(amount)
                                liveSession = updated
                                onSessionChanged(updated)
                                openDialog = false
                                message = "เปิดกะสำเร็จ"
                                error = null
                                refresh += 1
                            } catch (failure: Exception) {
                                error = actionError(failure)
                            } finally {
                                actionBusy = false
                            }
                        }
                    }
                ) { Text(if (actionBusy) "กำลังเปิด..." else "ยืนยันเปิดกะ") }
            },
            dismissButton = {
                TextButton(onClick = { openDialog = false }, enabled = !actionBusy) { Text("ยกเลิก") }
            }
        )
    }

    if (closeDialog) {
        AlertDialog(
            onDismissRequest = { if (!actionBusy) closeDialog = false },
            title = { Text("ปิดกะ", fontWeight = FontWeight.ExtraBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text(
                        "กรอกเงินสดที่นับได้ก่อนปิดกะ ระบบ CpIPOS จะตรวจบิลค้างและสิทธิ์อีกครั้ง",
                        color = moreMuted,
                        fontSize = 11.sp
                    )
                    OutlinedTextField(
                        value = closingCash,
                        onValueChange = { closingCash = it.take(12) },
                        label = { Text("เงินสดที่นับได้ (บาท)") },
                        singleLine = true,
                        enabled = !actionBusy,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = !actionBusy && closingCash.isNotBlank(),
                    onClick = {
                        val amount = runCatching { Satang.fromBaht(closingCash.trim()) }.getOrNull()
                        if (amount == null || amount < Satang.ZERO) {
                            error = "จำนวนเงินสดปิดกะไม่ถูกต้อง"
                            return@Button
                        }
                        actionBusy = true
                        scope.launch {
                            try {
                                val updated = client.closeShift(liveSession, amount)
                                liveSession = updated
                                onSessionChanged(updated)
                                closeDialog = false
                                closingCash = ""
                                message = "ปิดกะสำเร็จ"
                                error = null
                                refresh += 1
                            } catch (failure: Exception) {
                                error = actionError(failure)
                            } finally {
                                actionBusy = false
                            }
                        }
                    }
                ) { Text(if (actionBusy) "กำลังปิด..." else "ยืนยันปิดกะ") }
            },
            dismissButton = {
                TextButton(onClick = { closeDialog = false }, enabled = !actionBusy) { Text("ยกเลิก") }
            }
        )
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
private fun SalesTrendLineChart(points: List<WebSalesTrendPoint>) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 1.dp
    ) {
        Column(Modifier.padding(15.dp)) {
            Text("กราฟเส้นแนวโน้มยอดขาย", color = moreInk, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                "ยอดขายสุทธิตามช่วงเวลาที่เลือก",
                color = moreMuted,
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
            )
            if (points.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(145.dp), contentAlignment = Alignment.Center) {
                    Text("ยังไม่มีข้อมูลยอดขายสำหรับสร้างกราฟ", color = moreMuted, fontSize = 11.sp)
                }
            } else {
                val maxValue = points.maxOf { it.netSales.value }.coerceAtLeast(1L).toFloat()
                Canvas(
                    modifier = Modifier.fillMaxWidth().height(165.dp)
                ) {
                    val inset = 10.dp.toPx()
                    val chartWidth = (size.width - inset * 2).coerceAtLeast(1f)
                    val chartHeight = (size.height - inset * 2).coerceAtLeast(1f)
                    repeat(4) { index ->
                        val y = inset + chartHeight * index / 3f
                        drawLine(
                            color = Color(0xFFE8EEF7),
                            start = androidx.compose.ui.geometry.Offset(inset, y),
                            end = androidx.compose.ui.geometry.Offset(size.width - inset, y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                    fun pointAt(index: Int): androidx.compose.ui.geometry.Offset {
                        val x = if (points.size == 1) size.width / 2f
                        else inset + chartWidth * index / (points.size - 1).toFloat()
                        val ratio = points[index].netSales.value.toFloat() / maxValue
                        val y = inset + chartHeight * (1f - ratio)
                        return androidx.compose.ui.geometry.Offset(x, y)
                    }
                    if (points.size == 1) {
                        drawCircle(moreBlue, radius = 5.dp.toPx(), center = pointAt(0))
                    } else {
                        val path = Path()
                        points.indices.forEach { index ->
                            val point = pointAt(index)
                            if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
                        }
                        drawPath(
                            path = path,
                            color = moreBlue,
                            style = Stroke(
                                width = 3.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                        points.indices.forEach { index ->
                            drawCircle(Color.White, radius = 4.5.dp.toPx(), center = pointAt(index))
                            drawCircle(moreBlue, radius = 3.dp.toPx(), center = pointAt(index))
                        }
                    }
                }
                val labelIndexes = listOf(0, points.size / 2, points.lastIndex).distinct()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    labelIndexes.forEach { index ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(points[index].label, color = moreMuted, fontSize = 9.sp)
                            Text(
                                "฿" + points[index].netSales.moreMoneyText(),
                                color = moreInk,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SalesPaymentBarChart(snapshot: WebSalesSummarySnapshot) {
    val bars = listOf(
        "เงินสด" to snapshot.cashTotal,
        "โอน / QR" to snapshot.transferTotal,
        "บัตร" to snapshot.cardTotal
    )
    val maxValue = bars.maxOf { it.second.value }.coerceAtLeast(1L).toFloat()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 1.dp
    ) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Text("กราฟแท่งช่องทางชำระเงิน", color = moreInk, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            Text("เปรียบเทียบยอดรับเงินจริงในช่วงที่เลือก", color = moreMuted, fontSize = 10.sp)
            bars.forEachIndexed { index, row ->
                val fraction = (row.second.value.toFloat() / maxValue).coerceIn(0f, 1f)
                val barColor = when (index) {
                    0 -> Color(0xFF176DED)
                    1 -> Color(0xFF4F8EF7)
                    else -> Color(0xFF89ACF6)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(row.first, color = moreInk, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text("฿" + row.second.moreMoneyText(), color = moreInk, fontSize = 10.sp)
                }
                Surface(
                    modifier = Modifier.fillMaxWidth().height(12.dp),
                    color = Color(0xFFEAF0F8),
                    shape = RoundedCornerShape(50)
                ) {
                    Row {
                        Surface(
                            modifier = Modifier.fillMaxWidth(fraction.coerceAtLeast(0.01f)).fillMaxHeight(),
                            color = if (row.second.value > 0) barColor else Color.Transparent,
                            shape = RoundedCornerShape(50)
                        ) {}
                    }
                }
            }
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
                                "฿" + snapshot.netSales.moreMoneyText(),
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
                item { SalesTrendLineChart(snapshot.trend) }
                item { SalesPaymentBarChart(snapshot) }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        SummaryMetricCard("ยอดขายรวม", "฿" + snapshot.grossSales.moreMoneyText(), Modifier.weight(1f))
                        SummaryMetricCard("เฉลี่ยต่อบิล", "฿" + snapshot.averageReceipt.moreMoneyText(), Modifier.weight(1f), true)
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        SummaryMetricCard("เงินสด", "฿" + snapshot.cashTotal.moreMoneyText(), Modifier.weight(1f))
                        SummaryMetricCard("โอน / QR", "฿" + snapshot.transferTotal.moreMoneyText(), Modifier.weight(1f), true)
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        SummaryMetricCard("ส่วนลด", "฿" + snapshot.discountTotal.moreMoneyText(), Modifier.weight(1f))
                        SummaryMetricCard("ภาษี", "฿" + snapshot.taxTotal.moreMoneyText(), Modifier.weight(1f))
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        SummaryMetricCard("ยกเลิก", "฿" + snapshot.cancelledTotal.moreMoneyText(), Modifier.weight(1f))
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
                    "ขาย " + formatQty(row.quantitySold) + " · ยอดสุทธิ ฿" + row.netAmount.moreMoneyText(),
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
                                        "฿" + row.netAmount.moreMoneyText(),
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
                                line.quantity.toString() + " × ฿" + line.unitPrice.moreMoneyText(),
                                color = moreMuted,
                                fontSize = 9.sp
                            )
                        }
                        Text(
                            "฿" + line.amount.moreMoneyText(),
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
                            Text("฿" + subtotal.moreMoneyText(), color = moreInk, fontSize = 10.sp)
                        }
                        if (receipt.discount.value > 0) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("ส่วนลด", color = moreMuted, fontSize = 10.sp)
                                Text("-฿" + receipt.discount.moreMoneyText(), color = moreInk, fontSize = 10.sp)
                            }
                        }
                        HorizontalDivider(color = Color(0xFFE4EAF2))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("ยอดสุทธิ", color = moreInk, fontWeight = FontWeight.ExtraBold)
                            Text("฿" + receipt.total.moreMoneyText(), color = moreBlue, fontWeight = FontWeight.ExtraBold)
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
                                "฿" + row.receipt.total.moreMoneyText(),
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
        SavedDemoReceiptSheet(
            receipt = row.receipt,
            historyMode = true,
            onDone = { detail = null },
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
private fun TaxProfileEditorDialog(
    client: WebPosClient,
    session: WebPosSession,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (
        entityType: String,
        displayName: String,
        taxId: String,
        addressLine: String,
        postalCode: String,
        subdistrict: String,
        district: String,
        province: String
    ) -> Unit
) {
    var entityType by remember { mutableStateOf("company") }
    var typeExpanded by remember { mutableStateOf(false) }
    var displayName by remember { mutableStateOf("") }
    var taxId by remember { mutableStateOf("") }
    var addressLine by remember { mutableStateOf("") }
    var postalCode by remember { mutableStateOf("") }
    var addressOptions by remember { mutableStateOf<List<WebTaxAddressOption>>(emptyList()) }
    var selectedAddress by remember { mutableStateOf<WebTaxAddressOption?>(null) }
    var addressLoading by remember { mutableStateOf(false) }
    var addressError by remember { mutableStateOf<String?>(null) }
    var formError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(postalCode) {
        addressOptions = emptyList()
        selectedAddress = null
        addressError = null
        val digits = postalCode.filter(Char::isDigit)
        if (digits.length != 5) return@LaunchedEffect
        addressLoading = true
        try {
            val result = client.taxAddressOptions(session, digits)
            addressOptions = result
            if (result.size == 1) selectedAddress = result.first()
            if (result.isEmpty()) addressError = "ไม่พบข้อมูลพื้นที่สำหรับรหัสไปรษณีย์นี้"
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            addressError = "ค้นหาที่อยู่ไม่สำเร็จ: " + ((failure as? WebPosApiException)?.code ?: "network_error")
        } finally {
            addressLoading = false
        }
    }

    val entityLabel = when (entityType) {
        "limited_partnership" -> "หจก."
        "shop" -> "ร้านค้า"
        "individual" -> "บุคคลธรรมดา"
        else -> "บริษัท"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("เพิ่มข้อมูลผู้เสียภาษี", fontWeight = FontWeight.ExtraBold)
                Text(
                    "ฟอร์มเดียวกับ CpIPOS · เลือกพื้นที่จากรหัสไปรษณีย์",
                    color = moreMuted,
                    fontSize = 10.sp
                )
            }
        },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Text("ประเภทนาม", color = moreInk, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Box {
                    OutlinedButton(
                        onClick = { typeExpanded = true },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(entityLabel) }
                    DropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                        listOf(
                            "company" to "บริษัท",
                            "limited_partnership" to "หจก.",
                            "shop" to "ร้านค้า",
                            "individual" to "บุคคลธรรมดา"
                        ).forEach { item ->
                            DropdownMenuItem(
                                text = { Text(item.second) },
                                onClick = {
                                    entityType = item.first
                                    typeExpanded = false
                                }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it.take(160) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("ชื่อสำหรับออกภาษี") }
                )
                OutlinedTextField(
                    value = taxId,
                    onValueChange = { taxId = it.filter(Char::isDigit).take(13) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("เลขผู้เสียภาษี 13 หลัก") }
                )
                if (taxId.length == 13 && !thaiTaxIdValid(taxId)) {
                    Text("เลขผู้เสียภาษีไม่ผ่านการตรวจสอบ", color = Color(0xFFB13F4C), fontSize = 10.sp)
                }
                OutlinedTextField(
                    value = postalCode,
                    onValueChange = { postalCode = it.filter(Char::isDigit).take(5) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("รหัสไปรษณีย์") }
                )
                OutlinedTextField(
                    value = addressLine,
                    onValueChange = { addressLine = it.take(240) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("ที่อยู่ผู้เสียภาษี (บ้านเลขที่ / หมู่ / ถนน / อาคาร)") }
                )
                if (addressLoading) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("กำลังค้นหาที่อยู่จากรหัสไปรษณีย์...", color = moreBlue, fontSize = 10.sp)
                }
                addressError?.let {
                    Text(it, color = Color(0xFFB13F4C), fontSize = 10.sp)
                }
                if (addressOptions.size > 1 && selectedAddress == null) {
                    Text("รหัสนี้มีหลายพื้นที่ กรุณาเลือกให้ตรงกับที่อยู่จริง", color = moreInk, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    addressOptions.forEach { option ->
                        OutlinedButton(
                            onClick = { selectedAddress = option },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                option.subdistrict + " · " + option.district + " · " + option.province,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
                selectedAddress?.let { address ->
                    Surface(color = Color(0xFFF2F5F9), shape = RoundedCornerShape(12.dp)) {
                        Column(Modifier.fillMaxWidth().padding(11.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("ตำบล / แขวง: " + address.subdistrict, fontSize = 10.sp, color = moreInk)
                            Text("อำเภอ / เขต: " + address.district, fontSize = 10.sp, color = moreInk)
                            Text("จังหวัด: " + address.province, fontSize = 10.sp, color = moreInk)
                        }
                    }
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
                    val address = selectedAddress
                    when {
                        displayName.isBlank() -> formError = "กรุณากรอกชื่อสำหรับออกภาษี"
                        !thaiTaxIdValid(taxId) -> formError = "เลขผู้เสียภาษี 13 หลักไม่ถูกต้อง"
                        postalCode.length != 5 -> formError = "กรุณากรอกรหัสไปรษณีย์ 5 หลัก"
                        addressLine.isBlank() -> formError = "กรุณากรอกที่อยู่ผู้เสียภาษี"
                        address == null -> formError = "กรุณาเลือกตำบล/อำเภอ/จังหวัดจากรหัสไปรษณีย์"
                        else -> {
                            formError = null
                            onSave(
                                entityType,
                                displayName.trim(),
                                taxId,
                                addressLine.trim(),
                                postalCode,
                                address.subdistrict,
                                address.district,
                                address.province
                            )
                        }
                    }
                }
            ) { Text("บันทึก") }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) { Text("ยกเลิก") }
        }
    )
}

@Composable
private fun TaxReceiptPickerDialog(
    client: WebPosClient,
    session: WebPosSession,
    profile: WebTaxProfileRow,
    onDismiss: () -> Unit,
    onSelect: (WebTaxReceiptRow) -> Unit
) {
    var query by remember(profile.id) { mutableStateOf("") }
    var submitted by remember(profile.id) { mutableStateOf("") }
    var rows by remember(profile.id) { mutableStateOf<List<WebTaxReceiptRow>>(emptyList()) }
    var loading by remember(profile.id) { mutableStateOf(true) }
    var error by remember(profile.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(profile.id, submitted) {
        loading = true
        try {
            rows = client.taxReceipts(session, submitted)
            error = null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = "โหลดบิลไม่สำเร็จ: " + ((failure as? WebPosApiException)?.code ?: "network_error")
        } finally {
            loading = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("เลือกบิล / ออกใบกำกับ", fontWeight = FontWeight.ExtraBold)
                Text(profile.displayName + " · " + profile.taxId, color = moreMuted, fontSize = 10.sp)
            }
        },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it.take(80) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("ค้นหาเลขบิล") },
                    trailingIcon = {
                        TextButton(onClick = { submitted = query.trim() }) { Text("ค้นหา") }
                    }
                )
                Text("ไม่กรอกเลขบิลจะแสดงบิลที่ชำระล่าสุด", color = moreMuted, fontSize = 9.sp)
                Box(Modifier.fillMaxWidth().heightIn(max = 390.dp)) {
                    when {
                        loading -> Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = moreBlue)
                        }
                        error != null -> ErrorBlock(error.orEmpty())
                        rows.isEmpty() -> EmptyBlock("ไม่พบบิล")
                        else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            items(rows, key = { it.id }) { receipt ->
                                val conflict = receipt.invoiceProfileId != null && receipt.invoiceProfileId != profile.id
                                Surface(
                                    color = if (conflict) Color(0xFFFFEEEE) else Color(0xFFF7F9FC),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth().clickable(enabled = !conflict) {
                                        onSelect(receipt)
                                    }
                                ) {
                                    Column(Modifier.fillMaxWidth().padding(10.dp)) {
                                        Row(Modifier.fillMaxWidth()) {
                                            Text(receipt.orderNo, Modifier.weight(1f), color = moreInk, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                                            Text("฿" + receipt.total.moreMoneyText(), color = moreBlue, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                                        }
                                        Spacer(Modifier.height(3.dp))
                                        Text(
                                            when {
                                                conflict -> "ออกแล้วให้ผู้เสียภาษีรายอื่น: " + (receipt.invoiceNo ?: "-")
                                                receipt.invoiceNo != null -> "เคยออกแล้ว: " + receipt.invoiceNo + " · แตะเพื่อพิมพ์ซ้ำ"
                                                else -> "ยังไม่เคยออกใบกำกับ"
                                            },
                                            color = if (conflict) Color(0xFFB13F4C) else moreMuted,
                                            fontSize = 9.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("ปิด") }
        }
    )
}

@Composable
private fun TaxInvoicePrintDialog(
    receipt: WebTaxReceiptRow,
    profile: WebTaxProfileRow,
    busy: Boolean,
    onDismiss: () -> Unit,
    onPrint: (String) -> Unit
) {
    var pin by remember(receipt.id, profile.id) { mutableStateOf("") }
    var formError by remember(receipt.id, profile.id) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                (if (receipt.invoiceNo == null) "ออกใบกำกับภาษี" else "พิมพ์ใบกำกับภาษีซ้ำ") +
                    " " + receipt.orderNo,
                fontWeight = FontWeight.ExtraBold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Surface(color = Color(0xFFF3F7FD), shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.fillMaxWidth().padding(11.dp)) {
                        Text(profile.displayName, fontWeight = FontWeight.ExtraBold, color = moreInk)
                        Text("เลขผู้เสียภาษี " + profile.taxId, fontSize = 10.sp, color = moreMuted)
                        Text(
                            profile.addressLine + " " + profile.subdistrict + " " +
                                profile.district + " " + profile.province + " " + profile.postalCode,
                            fontSize = 9.sp,
                            color = moreMuted
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("ยอดบิล", color = moreMuted, fontSize = 11.sp)
                    Text("฿" + receipt.total.moreMoneyText(), color = moreBlue, fontWeight = FontWeight.ExtraBold)
                }
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter(Char::isDigit).take(12) },
                    modifier = Modifier.fillMaxWidth(),
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
                    if (pin.length < 4) formError = "กรุณากรอก PIN อย่างน้อย 4 หลัก"
                    else {
                        formError = null
                        onPrint(pin)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = moreBlue)
            ) {
                Text(if (receipt.invoiceNo == null) "ยืนยันออกและพิมพ์" else "พิมพ์อีกครั้ง")
            }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) { Text("ยกเลิก") }
        }
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
    var profiles by remember(session.id) { mutableStateOf<List<WebTaxProfileRow>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var addOpen by remember { mutableStateOf(false) }
    var savingProfile by remember { mutableStateOf(false) }
    var issueProfile by remember { mutableStateOf<WebTaxProfileRow?>(null) }
    var selectedReceipt by remember { mutableStateOf<WebTaxReceiptRow?>(null) }
    var printing by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<WebTaxProfileRow?>(null) }
    var deleting by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(client, session.id, submitted, refresh) {
        loading = true
        try {
            profiles = client.taxProfiles(session, submitted)
            error = null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = "โหลดทะเบียนผู้เสียภาษีไม่ได้: " + ((failure as? WebPosApiException)?.code ?: "network_error")
        } finally {
            loading = false
        }
    }

    MoreScaffold("ออกใบกำกับภาษี", session, onBack, onTab) {
        item {
            Button(
                onClick = { addOpen = true },
                modifier = Modifier.fillMaxWidth(),
                enabled = !savingProfile && !deleting,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = moreBlue)
            ) {
                Text("+ เพิ่มรายการใหม่", fontWeight = FontWeight.ExtraBold)
            }
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it.take(80) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("ค้นหาชื่อ หรือเลขผู้เสียภาษี") },
                trailingIcon = {
                    TextButton(onClick = { submitted = query.trim() }) { Text("ค้นหา") }
                }
            )
        }
        item {
            Text(
                "ทะเบียนผู้เสียภาษี → เลือกบิลย้อนหลัง → ตรวจสิทธิ์/PIN → พิมพ์ตามเครื่องที่ตั้งไว้ใน CpIPOS",
                color = moreMuted,
                fontSize = 10.sp
            )
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
            item {
                TableHeader(
                    listOf(
                        "ชื่อ / เลขผู้เสียภาษี" to 1.8f,
                        "เคยออก" to .65f,
                        "ดำเนินการ" to 1.25f
                    )
                )
            }
            items(profiles, key = { it.id }) { profile ->
                Surface(color = Color.White, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1.8f)) {
                            Text(
                                profile.displayName,
                                color = moreInk,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2
                            )
                            Text(profile.taxId, color = moreMuted, fontSize = 9.sp)
                        }
                        Text(
                            profile.invoiceCount.toString(),
                            Modifier.weight(.65f),
                            color = moreInk,
                            fontSize = 10.sp
                        )
                        Row(
                            Modifier.weight(1.25f),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = {
                                    issueProfile = profile
                                    selectedReceipt = null
                                },
                                enabled = !printing && !deleting
                            ) {
                                Text("ออกใบกำกับ", fontSize = 9.sp, color = moreBlue, fontWeight = FontWeight.Bold)
                            }
                            if (session.canManageTables) {
                                TextButton(
                                    onClick = {
                                        deleteError = null
                                        deleteTarget = profile
                                    },
                                    enabled = !printing && !deleting
                                ) {
                                    Text("ลบ", fontSize = 9.sp, color = Color(0xFFB13F4C), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
            if (profiles.isEmpty()) {
                item { EmptyBlock("ยังไม่มีทะเบียนผู้เสียภาษี") }
            }
        }
    }

    if (addOpen) {
        TaxProfileEditorDialog(
            client = client,
            session = session,
            busy = savingProfile,
            onDismiss = { if (!savingProfile) addOpen = false },
            onSave = { entityType, displayName, taxId, addressLine, postalCode, subdistrict, district, province ->
                savingProfile = true
                error = null
                success = null
                scope.launch {
                    try {
                        client.saveTaxProfile(
                            session = session,
                            entityType = entityType,
                            displayName = displayName,
                            taxId = taxId,
                            addressLine = addressLine,
                            postalCode = postalCode,
                            subdistrict = subdistrict,
                            district = district,
                            province = province
                        )
                        addOpen = false
                        success = "เพิ่มทะเบียนผู้เสียภาษีสำเร็จ"
                        refresh += 1
                    } catch (failure: Exception) {
                        error = "บันทึกทะเบียนผู้เสียภาษีไม่สำเร็จ: " +
                            ((failure as? WebPosApiException)?.code ?: "network_error")
                    } finally {
                        savingProfile = false
                    }
                }
            }
        )
    }

    issueProfile?.let { profile ->
        if (selectedReceipt == null) {
            TaxReceiptPickerDialog(
                client = client,
                session = session,
                profile = profile,
                onDismiss = { issueProfile = null },
                onSelect = { selectedReceipt = it }
            )
        }
    }

    val receipt = selectedReceipt
    val profile = issueProfile
    if (receipt != null && profile != null) {
        TaxInvoicePrintDialog(
            receipt = receipt,
            profile = profile,
            busy = printing,
            onDismiss = {
                if (!printing) selectedReceipt = null
            },
            onPrint = { pin ->
                printing = true
                error = null
                success = null
                scope.launch {
                    try {
                        val invoiceNo = client.printTaxInvoice(session, profile.id, receipt.id, pin)
                        NativePrintRuntime.wake()
                        success = "ออก/ส่งพิมพ์ใบกำกับภาษี " + invoiceNo + " แล้ว"
                        selectedReceipt = null
                        issueProfile = null
                        refresh += 1
                    } catch (failure: Exception) {
                        val api = failure as? WebPosApiException
                        error = when (api?.code) {
                            "pin_rejected", "pin_required" -> "PIN Owner/Manager ไม่ถูกต้อง"
                            "receipt_printer_not_configured" -> "ยังไม่ได้ตั้งค่าเครื่องพิมพ์ใบเสร็จ/ใบกำกับภาษี"
                            else -> "ออก/พิมพ์ใบกำกับภาษีไม่สำเร็จ: " + (api?.code ?: "printer_or_network_error")
                        }
                    } finally {
                        printing = false
                    }
                }
            }
        )
    }

    deleteTarget?.let { target ->
        NativeCancelBillPinDialog(
            title = "ลบทะเบียน " + target.displayName,
            busy = deleting,
            error = deleteError,
            actionLabel = "ยืนยัน PIN และลบ",
            actionNote = "รายการจะถูกปิดใช้งาน แต่ประวัติใบกำกับที่เคยออกจะยังคงอยู่",
            onDismiss = {
                if (!deleting) {
                    deleteTarget = null
                    deleteError = null
                }
            },
            onApprove = { pin ->
                deleting = true
                deleteError = null
                scope.launch {
                    try {
                        client.deleteTaxProfile(session, target.id, pin)
                        deleteTarget = null
                        success = "ลบทะเบียนผู้เสียภาษีแล้ว"
                        refresh += 1
                    } catch (failure: Exception) {
                        val api = failure as? WebPosApiException
                        deleteError = when (api?.code) {
                            "pin_rejected", "pin_required" -> "PIN Owner/Manager ไม่ถูกต้อง"
                            "forbidden_role" -> "บัญชีนี้ไม่มีสิทธิ์ลบทะเบียนผู้เสียภาษี"
                            "http_404" -> "CpIPOS Server ยังไม่รองรับการลบรายการจาก POS มือถือรุ่นนี้"
                            else -> "ลบไม่สำเร็จ: " + (api?.code ?: "network_error")
                        }
                    } finally {
                        deleting = false
                    }
                }
            }
        )
    }
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
                    .padding(top = 8.dp, bottom = 150.dp),
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
