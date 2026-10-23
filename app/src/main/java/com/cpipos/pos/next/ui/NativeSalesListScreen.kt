package com.cpipos.pos.next.ui

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cpipos.pos.next.core.webpos.*
import com.cpipos.pos.next.printing.DemoReceiptPrinter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

private val salesBlue = Color(0xFF176DED)
private val salesInk = Color(0xFF172C50)
private val salesMuted = Color(0xFF70839C)
private val bangkok = ZoneId.of("Asia/Bangkok")
private val receiptDateFormat = DateTimeFormatter.ofPattern("dd/MM/yy HH:mm", Locale.US)
private val dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.US)

internal data class NativeSalesWindow(val start: LocalDate, val end: LocalDate)
internal fun nativeSalesWindow(mode: String, anchor: LocalDate,
                               customFrom: LocalDate, customTo: LocalDate): NativeSalesWindow =
    when (mode) {
        "day" -> NativeSalesWindow(anchor, anchor)
        "month" -> NativeSalesWindow(anchor.withDayOfMonth(1), anchor.withDayOfMonth(anchor.lengthOfMonth()))
        "year" -> NativeSalesWindow(anchor.withDayOfYear(1), anchor.withDayOfYear(anchor.lengthOfYear()))
        else -> NativeSalesWindow(customFrom, customTo)
    }

/**
 * Server-backed sales list: same completed POS receipts for both takeaway and
 * dine-in. Deletion is audited soft-delete + stock restoration on CpIPOS Web,
 * NEVER a local-only row removal or direct Android database mutation.
 */
@Composable
internal fun NativeSalesListScreen(
    client: WebPosClient,
    session: WebPosSession,
    onTab: (String) -> Unit
) {
    val context = LocalContext.current
    val deleteJournal = remember(context.applicationContext) {
        SalesDeleteJournal(context.applicationContext)
    }
    val scope = rememberCoroutineScope()
    val today = remember { LocalDate.now(bangkok) }
    var mode by remember { mutableStateOf("day") }
    var anchor by remember { mutableStateOf(today) }
    var customFrom by remember { mutableStateOf(today.minusDays(29)) }
    var customTo by remember { mutableStateOf(today) }
    var rangeDialog by remember { mutableStateOf(false) }
    var draftFrom by remember { mutableStateOf(customFrom) }
    var draftTo by remember { mutableStateOf(customTo) }
    var page by remember { mutableIntStateOf(1) }
    var refresh by remember { mutableIntStateOf(0) }
    var history by remember { mutableStateOf<WebReceiptHistory?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var visibleDeleteId by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<WebReceiptHistoryRecord?>(null) }
    var deleteBusy by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }
    var uncertainIds by remember(session.id) {
        mutableStateOf(deleteJournal.pending(session))
    }
    var selectedReceipt by remember { mutableStateOf<WebReceiptHistoryRecord?>(null) }
    var printError by remember { mutableStateOf<String?>(null) }

    val window = nativeSalesWindow(mode, anchor, customFrom, customTo)
    LaunchedEffect(client, session.id, mode, window.start, window.end, page, refresh) {
        loading = true
        error = null
        try {
            history = client.receiptHistory(
                session = session,
                from = window.start,
                to = window.end,
                status = "completed",
                page = page,
                pageSize = 40
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = "โหลดรายการขายไม่ได้ กรุณาตรวจสอบเครือข่ายและสิทธิ์ของสาขา"
        } finally { loading = false }
    }
    fun changeMode(value: String) {
        mode = value
        page = 1
        visibleDeleteId = null
    }
    fun shiftPeriod(direction: Long) {
        anchor = when (mode) {
            "month" -> anchor.plusMonths(direction)
            "year" -> anchor.plusYears(direction)
            else -> anchor.plusDays(direction)
        }
        page = 1
    }
    fun openPicker(current: LocalDate, isFrom: Boolean) {
        DatePickerDialog(context, { _, y, m, d ->
            val selected = LocalDate.of(y, m + 1, d)
            if (isFrom) draftFrom = selected else draftTo = selected
        }, current.year, current.monthValue - 1, current.dayOfMonth).show()
    }

    Surface(Modifier.fillMaxSize(), color = Color(0xFFF5F8FE)) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().statusBarsPadding()
                .padding(horizontal = 13.dp).padding(bottom = 106.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 13.dp, bottom = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("รายการขาย", fontSize = 23.sp,
                            fontWeight = FontWeight.ExtraBold, color = salesInk)
                        Text(session.branchName + " · CpIPOS", fontSize = 11.sp,
                            color = salesMuted)
                    }
                    TextButton(onClick = { refresh += 1 }, enabled = !loading && !deleteBusy) {
                        Text("↻ รีเฟรช", color = salesBlue)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("day" to "รายวัน", "month" to "รายเดือน", "year" to "รายปี").forEach { (key, name) ->
                        Surface(
                            modifier = Modifier.weight(1f)
                                .clickable { changeMode(key) },
                            shape = RoundedCornerShape(15.dp),
                            color = if (mode == key) salesBlue else Color.White
                        ) {
                            Text(name,
                                modifier = Modifier.padding(vertical = 12.dp),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                color = if (mode == key) Color.White else salesInk)
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(enabled = mode != "custom", onClick = { shiftPeriod(-1) }) {
                        Text("‹", fontSize = 22.sp, color = salesBlue)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(window.start.format(dateFormat) + " – " +
                            window.end.format(dateFormat),
                            fontSize = 12.sp, color = salesInk,
                            fontWeight = FontWeight.SemiBold)
                        Text("ช่วงวันที่", fontSize = 10.sp, color = salesMuted)
                    }
                    TextButton(enabled = mode != "custom", onClick = { shiftPeriod(1) }) {
                        Text("›", fontSize = 22.sp, color = salesBlue)
                    }
                }
                OutlinedButton(
                    onClick = {
                        draftFrom = customFrom
                        draftTo = customTo
                        rangeDialog = true
                    },
                    modifier = Modifier.fillMaxWidth().height(43.dp),
                    shape = RoundedCornerShape(14.dp)
                ) { Text("ปฏิทิน  เลือกวันที่เริ่มต้น – วันที่สิ้นสุด", color = salesBlue) }
                Spacer(Modifier.height(13.dp))
                if (error != null) {
                    Text(error.orEmpty(), color = Color(0xFFAF354A),
                        fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
                }
                if (uncertainIds.isNotEmpty()) {
                    Surface(color = Color(0xFFFFF0EA), shape = RoundedCornerShape(14.dp)) {
                        Column(Modifier.fillMaxWidth().padding(11.dp)) {
                            Text("การลบบิลยังไม่ยืนยัน ห้ามส่งคำขอซ้ำ",
                                color = Color(0xFFAB4342), fontSize = 12.sp)
                            Text("โปรดตรวจสอบใน CpIPOS Web ก่อนทำรายการลบใหม่",
                                color = Color(0xFFAB4342), fontSize = 11.sp)
                        }
                    }
                    Spacer(Modifier.height(7.dp))
                }
                val records = history?.records.orEmpty()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 9.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("บิลที่ชำระแล้ว", fontWeight = FontWeight.Bold,
                        color = salesInk, fontSize = 14.sp)
                    Text("รวม ${history?.total ?: 0} บิล", fontSize = 12.sp, color = salesMuted)
                }
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(18.dp),
                    color = Color.White
                ) {
                    Column(Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().background(Color(0xFFE7F0FE))
                                .padding(horizontal = 8.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SalesCell("เลขบิล", 1.35f, true)
                            SalesCell("วันที่", 1.1f, true)
                            SalesCell("โหมด / ชำระ", 1.15f, true)
                            SalesCell("ยอด", .95f, true, true)
                        }
                        when {
                            loading && history == null -> Box(
                                modifier = Modifier.fillMaxWidth().height(190.dp),
                                contentAlignment = Alignment.Center
                            ) { CircularProgressIndicator(color = salesBlue) }
                            records.isEmpty() -> Box(
                                modifier = Modifier.fillMaxWidth().height(125.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(if (loading) "กำลังโหลดรายการ..." else "ไม่มีรายการขายในช่วงวันที่นี้",
                                    color = salesMuted, fontSize = 13.sp)
                            }
                            else -> LazyColumn(
                                modifier = Modifier.weight(1f),
                            ) {
                                items(records, key = { it.orderId }) { record ->
                                    var drag by remember(record.orderId) { mutableFloatStateOf(0f) }
                                    val isOpen = visibleDeleteId == record.orderId
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .weight(1f)
                                                .pointerInput(record.orderId) {
                                                    detectHorizontalDragGestures(
                                                        onDragEnd = {
                                                            if (drag < -50f) visibleDeleteId = record.orderId
                                                            if (drag > 50f) visibleDeleteId = null
                                                            drag = 0f
                                                        },
                                                        onDragCancel = { drag = 0f },
                                                        onHorizontalDrag = { change, amount ->
                                                            drag += amount
                                                            if (abs(drag) > 6f) change.consume()
                                                        }
                                                    )
                                                }
                                                .clickable {
                                                    if (!deleteBusy) selectedReceipt = record
                                                }
                                                .padding(horizontal = 8.dp, vertical = 13.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            SalesCell(record.orderNo, 1.35f, false)
                                            SalesCell(
                                                Instant.ofEpochMilli(record.createdAtMs)
                                                    .atZone(bangkok).format(receiptDateFormat),
                                                1.1f, false
                                            )
                                            SalesCell(
                                                record.receipt.modeLabel + "\n" +
                                                    if ("bank_transfer" in record.paymentMethods)
                                                        "เงินโอน" else if ("cash" in record.paymentMethods)
                                                        "เงินสด" else record.paymentMethods.joinToString(","),
                                                1.15f, false
                                            )
                                            SalesCell("฿" + record.receipt.total.bahtText(),
                                                .95f, false, true)
                                        }
                                        if (isOpen) {
                                            TextButton(
                                                onClick = {
                                                    visibleDeleteId = null
                                                    deleteTarget = record
                                                    deleteError = null
                                                },
                                                enabled = !deleteBusy && record.orderId !in uncertainIds
                                            ) { Text("ลบบิล", color = Color(0xFFCE354C), fontSize = 12.sp) }
                                        }
                                    }
                                    HorizontalDivider(color = Color(0xFFEAF0FA))
                                }
                                item {
                                    Row(
                                        Modifier.fillMaxWidth().padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        TextButton(
                                            enabled = !loading && page > 1,
                                            onClick = { page -= 1 }
                                        ) { Text("ก่อนหน้า") }
                                        Text("หน้า $page / ${history?.totalPages ?: 1}",
                                            color = salesMuted, fontSize = 11.sp)
                                        TextButton(
                                            enabled = !loading &&
                                                page < (history?.totalPages ?: 1),
                                            onClick = { page += 1 }
                                        ) { Text("ถัดไป") }
                                    }
                                }
                            }
                        }
                    }
                }
                Text("แตะรายการเพื่อดูใบเสร็จ • ปัดซ้ายเพื่อขออนุมัติลบบิล",
                    fontSize = 11.sp, color = salesMuted,
                    modifier = Modifier.padding(top = 9.dp))
            }
            HomeBottomMenu(
                modifier = Modifier.align(Alignment.BottomCenter),
                selectedTab = "report", onMenuClick = onTab
            )
        }
    }

    if (rangeDialog) AlertDialog(
        onDismissRequest = { rangeDialog = false },
        shape = RoundedCornerShape(23.dp),
        title = { Text("เลือกช่วงวันที่ขาย", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(modifier = Modifier.fillMaxWidth(),
                    onClick = { openPicker(draftFrom, true) }) {
                    Text("วันที่เริ่มต้น  ${draftFrom.format(dateFormat)}")
                }
                OutlinedButton(modifier = Modifier.fillMaxWidth(),
                    onClick = { openPicker(draftTo, false) }) {
                    Text("วันที่สิ้นสุด  ${draftTo.format(dateFormat)}")
                }
                if (draftTo.isBefore(draftFrom)) {
                    Text("วันสิ้นสุดต้องไม่น้อยกว่าวันเริ่มต้น",
                        fontSize = 12.sp, color = Color(0xFFB0334A))
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !draftTo.isBefore(draftFrom),
                onClick = {
                    customFrom = draftFrom
                    customTo = draftTo
                    rangeDialog = false
                    changeMode("custom")
                }
            ) { Text("ตกลง") }
        },
        dismissButton = {
            TextButton(onClick = { rangeDialog = false }) { Text("ยกเลิก") }
        }
    )
    deleteTarget?.let { row ->
        NativeCancelBillPinDialog(
            title = "ลบบิล ${row.orderNo}",
            busy = deleteBusy,
            error = deleteError,
            actionLabel = "อนุมัติลบบิล",
            onDismiss = { if (!deleteBusy) deleteTarget = null },
            onApprove = { pin ->
                deleteBusy = true
                deleteError = null
                scope.launch {
                    var deletionSent = false
                    try {
                        if (row.orderId in deleteJournal.pending(session)) {
                            deleteError = "บิลนี้มีคำขอลบที่ไม่แน่นอน " +
                                "กรุณาตรวจสอบ CpIPOS Web ก่อน ห้ามส่งซ้ำ"
                            return@launch
                        }
                        val approvalId = client.approveSalesRecordDelete(session, row.orderId, pin)
                        deleteJournal.prepare(session, row.orderId)
                        uncertainIds = deleteJournal.pending(session)
                        deletionSent = true
                        client.deleteSalesRecord(session, row.orderId, approvalId)
                        deleteJournal.confirmed(session, row.orderId)
                        uncertainIds = deleteJournal.pending(session)
                        deleteTarget = null
                        visibleDeleteId = null
                        page = 1
                        refresh += 1
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        if (deletionSent) {
                            // No network retries: server may have restored stock.
                            uncertainIds = deleteJournal.pending(session)
                            deleteTarget = null
                            deleteError = null
                        } else {
                            deleteError = "PIN ไม่ผ่านหรือไม่มีสิทธิ์ลบบิล " +
                                "ต้องได้รับสิทธิ์เจ้าของ/ผู้จัดการและเปิดฟีเจอร์รายการขาย"
                        }
                    } finally { deleteBusy = false }
                }
            }
        )
    }
    selectedReceipt?.let { row ->
        SavedDemoReceiptSheet(
            receipt = row.receipt,
            onPrint = {
                runCatching { DemoReceiptPrinter.print(context, row.receipt) }
                    .onFailure { printError = "เปิดระบบพิมพ์หรือบันทึก PDF ไม่สำเร็จ" }
            },
            onDone = { selectedReceipt = null }
        )
    }
    if (printError != null) AlertDialog(
        onDismissRequest = { printError = null },
        title = { Text("พิมพ์ใบเสร็จ") },
        text = { Text(printError.orEmpty()) },
        confirmButton = { TextButton(onClick = { printError = null }) { Text("ตกลง") } }
    )
}

@Composable
private fun RowScope.SalesCell(value: String, size: Float, heading: Boolean,
                               alignEnd: Boolean = false) {
    Text(
        value,
        modifier = Modifier.weight(size),
        fontSize = if (heading) 10.sp else 11.sp,
        color = if (heading) Color(0xFF48637F) else salesInk,
        fontWeight = if (heading) FontWeight.Bold else FontWeight.Medium,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
        lineHeight = 14.sp,
        textAlign = if (alignEnd) androidx.compose.ui.text.style.TextAlign.End
            else androidx.compose.ui.text.style.TextAlign.Start
    )
}
