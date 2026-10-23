package com.cpipos.pos.next.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import java.util.UUID
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.cpipos.pos.next.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cpipos.pos.next.core.webpos.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val diningInk = Color(0xFF172B4E)
private val diningBlue = Color(0xFF176DED)

private fun diningStatus(status: String): Pair<String, Color> = when (status) {
    "available" -> "ว่าง" to Color(0xFF189A64)
    "occupied" -> "มีลูกค้า" to Color(0xFFDD8433)
    "ordering" -> "กำลังสั่ง" to Color(0xFF2567DE)
    "pending_payment" -> "รอชำระ" to Color(0xFFBB5A25)
    "reserved" -> "จองแล้ว" to Color(0xFF8C58B7)
    else -> "ไม่พร้อม" to Color(0xFF8B96A6)
}

/** Read actual shared floor data and active table sessions; no local table mocks. */
@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun NativeDiningTableScreen(
    client: WebPosClient, session: WebPosSession,
    onBack: () -> Unit, onMainMenu: (String) -> Unit = {},
    onEnterTable: (WebDiningTable) -> Unit = {},
    canManageTable: (String) -> Boolean = { true }
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val journal = remember(context.applicationContext) {
        DiningMutationJournal(context.applicationContext)
    }
    var floor by remember(session.id) { mutableStateOf<WebTableFloor?>(null) }
    var zone by remember(session.id) { mutableStateOf<String?>(null) }
    var loading by remember(session.id) { mutableStateOf(true) }
    var error by remember(session.id) { mutableStateOf<String?>(null) }
    var working by remember(session.id) { mutableStateOf(false) }
    var selected by remember(session.id) { mutableStateOf<WebDiningTable?>(null) }
    var detail by remember(session.id) { mutableStateOf<WebTableBillSummary?>(null) }
    var managing by remember(session.id) { mutableStateOf<WebDiningTable?>(null) }
    var managementBill by remember(session.id) { mutableStateOf<WebDiningBill?>(null) }
    var managementError by remember(session.id) { mutableStateOf<String?>(null) }
    var managementLoading by remember(session.id) { mutableStateOf(false) }
    // A timed-out open-bill request may have reached the server: block re-open
    // until a fresh server snapshot confirms this table is no longer available.
    var uncertainOpens by remember(session.id) { mutableStateOf(setOf<String>()) }

    suspend fun refresh() {
        try {
            val current = client.diningTables(session)
            floor = current
            uncertainOpens = uncertainOpens.filterNot { id ->
                current.tables.any { it.id == id && (it.sessionId != null || it.status != "available") }
            }.toSet()
            error = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = if (e is WebPosApiException && e.status == 403)
                "ไม่มีสิทธิ์ดูโต๊ะ หรือแพ็กเกจนี้ยังไม่ได้เปิดฟีเจอร์โต๊ะ"
            else "โหลดโต๊ะไม่ได้ กรุณาตรวจสอบการเชื่อมต่อ"
        } finally { loading = false }
    }
    LaunchedEffect(client, session.id) {
        while (true) {
            if (!working && selected == null) refresh()
            delay(8000)
        }
    }
    BackHandler(enabled = !working) { if (selected == null) onBack() else selected = null }

    Surface(Modifier.fillMaxSize(), color = Color(0xFFF5F9FF)) {
        Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()
            .padding(horizontal = 16.dp).padding(bottom = 104.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 18.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("‹ กลับ", color = diningBlue, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable(enabled = !working) { onBack() })
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("โหมดนั่งโต๊ะ", color = diningInk, fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold)
                    Text(session.branchName, fontSize = 12.sp, color = Color(0xFF72829A))
                }
                Text("↻", color = diningBlue, fontSize = 29.sp,
                    modifier = Modifier.clickable { scope.launch { loading = true; refresh() } })
            }
            val zones = floor?.zones.orEmpty()
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { DiningZone("ทุกโซน", zone == null) { zone = null } }
                items(zones.size) { n ->
                    DiningZone(zones[n].name, zone == zones[n].id) { zone = zones[n].id }
                }
            }
            Spacer(Modifier.height(18.dp))
            if (error != null && floor != null) {
                Text(error.orEmpty(), color = Color(0xFFA83F4A), fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 10.dp))
            }
            val visible = floor?.tables.orEmpty()
                .filter { zone == null || it.zoneId == zone }
            Text("รายการโต๊ะ " + visible.size, color = diningInk,
                fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
            if (loading && floor == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = diningBlue)
                }
            } else if (floor == null) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error ?: "ยังไม่มีข้อมูลโต๊ะ", color = Color(0xFFA83F4A))
                    OutlinedButton(onClick = { scope.launch { loading = true; refresh() } }) {
                        Text("ลองใหม่")
                    }
                }
            } else if (visible.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("ไม่มีโต๊ะในโซนนี้ กรุณาตั้งค่าใน CpIPOS Web")
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize().padding(bottom = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(visible, key = { it.id }) { table ->
                        val (label, accent) = diningStatus(table.status)
                        Surface(
                            modifier = Modifier.fillMaxWidth().combinedClickable(
                                enabled = !working,
                                onClick = {
                                    if (table.sessionId != null &&
                                        table.status in setOf("occupied", "ordering", "pending_payment")) {
                                        onEnterTable(table)
                                    } else {
                                        selected = table
                                        detail = null
                                        if (table.status != "available") {
                                            scope.launch {
                                                runCatching { client.tableBill(session, table.id) }
                                                    .onSuccess { detail = it }
                                            }
                                        }
                                    }
                                },
                                onLongClick = {
                                    managing = table
                                    managementBill = null
                                    managementError = null
                                    managementLoading = true
                                    scope.launch {
                                        try {
                                            managementBill = client.diningBill(session, table.id)
                                        } catch (e: Exception) {
                                            managementError = "ตรวจสถานะบิลไม่ได้ ห้ามยกเลิกโต๊ะจนกว่าเชื่อมต่อเซิร์ฟเวอร์ได้"
                                        } finally {
                                            managementLoading = false
                                        }
                                    }
                                }
                            ),
                            shape = RoundedCornerShape(22.dp),
                            color = Color.White, shadowElevation = 2.dp
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                Box(
                                    modifier = Modifier.size(73.dp)
                                        .background(accent.copy(alpha = 0.12f), RoundedCornerShape(19.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        painter = painterResource(R.drawable.ic_mode_table),
                                        contentDescription = "รูปโต๊ะ",
                                        modifier = Modifier.size(60.dp)
                                    )
                                }
                                Text(table.name, color = diningInk,
                                    fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                                Text(table.capacity.toString() + " ที่นั่ง",
                                    color = Color(0xFF7C8AA1), fontSize = 12.sp)
                                Surface(shape = RoundedCornerShape(50),
                                    color = accent.copy(alpha = 0.12f)) {
                                    Text(label, color = accent, fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
        HomeBottomMenu(
            modifier = Modifier.align(Alignment.BottomCenter),
            selectedTab = "sale",
            onMenuClick = { tab -> if (!working) onMainMenu(tab) }
        )
        }
    }

    managing?.let { table ->
        val bill = managementBill
        val pending = journal.pending(session, table.id)
        val eligible = bill != null && bill.editable && !bill.paid &&
            session.canManageTables && (!hasItems || session.canSell) &&
            !managementLoading && !working && canManageTable(table.id) && pending == null
        val hasItems = bill?.lines?.isNotEmpty() == true
        AlertDialog(
            onDismissRequest = { if (!working) managing = null },
            title = { Text("จัดการโต๊ะ " + table.name + " (" + table.code + ")",
                color = diningInk, fontWeight = FontWeight.ExtraBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    if (managementLoading) CircularProgressIndicator(color = diningBlue)
                    Text(if (hasItems) "บิลนี้มีรายการสินค้า ต้องยกเลิกบิลก่อนคืนสถานะโต๊ะ"
                        else "ยกเลิกบิลว่างเพื่อให้โต๊ะกลับเป็นสถานะว่าง",
                        color = Color(0xFF657994), fontSize = 13.sp)
                    if (pending != null) Text(
                        "มีคำขอยกเลิกที่ไม่แน่นอน กรุณากดตรวจสอบสถานะ " +
                            "ห้ามส่งคำขอยกเลิกซ้ำ",
                        color = Color(0xFFB33D4D), fontSize = 12.sp
                    )
                    managementError?.let { Text(it, color = Color(0xFFB33D4D), fontSize = 12.sp) }
                    if (!session.canManageTables || (hasItems && !session.canSell)) Text(
                        "บัญชีพนักงานไม่มีสิทธิ์จัดการโต๊ะหรือยกเลิกรายการ " +
                            "โปรดใช้ผู้มีสิทธิ์ที่ CpIPOS Web",
                        color = Color(0xFFB33D4D), fontSize = 12.sp
                    )
                    if (!canManageTable(table.id)) Text(
                        "มีตะกร้าหรือบิลค้างบนมือถือเครื่องนี้ " +
                            "กรุณาตรวจสอบก่อนยกเลิก",
                        color = Color(0xFFB33D4D), fontSize = 12.sp
                    )
                    if (bill?.paid == true) Text(
                        "บิลนี้ได้รับชำระแล้ว ไม่สามารถยกเลิกยอดขายโดยการเคลียร์โต๊ะ",
                        color = Color(0xFFB33D4D), fontSize = 12.sp
                    )
                }
            },
            confirmButton = {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp),
                    modifier = Modifier.fillMaxWidth()) {
                    if (pending != null) {
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !working,
                            onClick = {
                                working = true
                                scope.launch {
                                    try {
                                        val updated = client.diningTables(session)
                                        val vacant = updated.tables.any {
                                            it.id == table.id && it.status == "available" &&
                                                it.sessionId == null
                                        }
                                        if (vacant && pending.requestId.isNotBlank()) {
                                            journal.confirmed(session, table.id, pending.requestId)
                                            managing = null
                                            floor = updated
                                        } else managementError =
                                            "โต๊ะยังไม่ว่าง กรุณาตรวจบิลเดิมจาก POS Web ก่อนทำรายการอีกครั้ง"
                                    } catch (e: Exception) {
                                        managementError = "ยังตรวจสอบสถานะจากเซิร์ฟเวอร์ไม่ได้"
                                    } finally { working = false }
                                }
                            }
                        ) { Text("ตรวจสอบสถานะโต๊ะ") }
                    } else {
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = eligible && hasItems,
                            onClick = {
                                if (bill != null) {
                                    working = true
                                    managementError = null
                                    scope.launch {
                                        try {
                                            val requestId = UUID.randomUUID().toString()
                                            journal.prepare(session, table.id, "cancel",
                                                bill.orderId, requestId, emptyMap())
                                            val vacant = client.cancelDiningBill(session, table.id, bill)
                                            if (!vacant) error("table_not_yet_vacant")
                                            journal.confirmed(session, table.id, requestId)
                                            managing = null
                                            refresh()
                                        } catch (e: Exception) {
                                            managementError = "ยกเลิกบิลยังไม่ยืนยัน " +
                                                "กรุณาตรวจสถานะโต๊ะ ห้ามส่งคำขอซ้ำ"
                                        } finally { working = false }
                                    }
                                }
                            }
                        ) { Text("ยกเลิกบิลและรายการสินค้า") }
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = eligible && !hasItems,
                            onClick = {
                                if (bill != null) {
                                    working = true
                                    managementError = null
                                    scope.launch {
                                        try {
                                            val requestId = UUID.randomUUID().toString()
                                            journal.prepare(session, table.id, "cancel",
                                                bill.orderId, requestId, emptyMap())
                                            val vacant = client.releaseEmptyDiningTable(session, table.id, bill)
                                            if (!vacant) error("table_not_yet_vacant")
                                            journal.confirmed(session, table.id, requestId)
                                            managing = null
                                            refresh()
                                        } catch (e: Exception) {
                                            managementError = "เคลียร์โต๊ะยังไม่ยืนยัน " +
                                                "กรุณาตรวจสถานะโต๊ะ ห้ามส่งคำขอซ้ำ"
                                        } finally { working = false }
                                    }
                                }
                            }
                        ) { Text("เคลียร์โต๊ะให้ว่าง") }
                    }
                }
            },
            dismissButton = { TextButton(enabled = !working,
                onClick = { managing = null }) { Text("กลับ") } }
        )
    }

    selected?.let { table ->
        val canOpen = table.status == "available" && table.sessionId == null &&
            !uncertainOpens.contains(table.id)
        AlertDialog(
            onDismissRequest = { if (!working) { selected = null; detail = null } },
            title = { Text(table.name, color = diningInk, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("สถานะ: " + diningStatus(table.status).first)
                    detail?.orderNo?.let { Text("เลขบิล: " + it, color = diningBlue) }
                    if (!canOpen) Text(
                        if (uncertainOpens.contains(table.id))
                            "สถานะเปิดโต๊ะยังไม่แน่นอน กรุณารีเฟรชตรวจสอบเซิร์ฟเวอร์ก่อนเปิดอีกครั้ง"
                        else "โต๊ะนี้มีสถานะที่บันทึกบน POS Web แล้ว กรุณาตรวจสอบบิลเดิม",
                        fontSize = 12.sp, color = Color(0xFF72829A)
                    )
                }
            },
            confirmButton = {
                if (canOpen) {
                    Button(enabled = !working, onClick = {
                        working = true
                        uncertainOpens = uncertainOpens + table.id
                        scope.launch {
                            try {
                                client.openDiningTable(session, table.id)
                                val opened = client.diningBill(session, table.id)
                                check(!opened.sessionId.isNullOrBlank()) { "table_open_needs_review" }
                                selected = null
                                detail = null
                                refresh()
                                onEnterTable(table)
                            } catch (e: Exception) {
                                // Another cashier may have won a simultaneous
                                // open. If their session exists, JOIN that exact
                                // server-owned bill instead of opening another.
                                val existing = runCatching { client.diningBill(session, table.id) }.getOrNull()
                                if (existing?.sessionId != null && existing.editable) {
                                    selected = null
                                    detail = null
                                    uncertainOpens = uncertainOpens - table.id
                                    onEnterTable(table)
                                } else {
                                    if (e is WebPosApiException && e.status in 400..499)
                                        uncertainOpens = uncertainOpens - table.id
                                    error = "สถานะเปิดโต๊ะไม่แน่นอน โปรดรีเฟรชบิลเดิม ห้ามกดเปิดซ้ำ"
                                    selected = null
                                }
                            } finally { working = false }
                        }
                    }) { Text(if (working) "กำลังเปิดโต๊ะ" else "เปิดโต๊ะ") }
                } else if (detail?.sessionId != null &&
                    table.status in setOf("occupied", "ordering", "pending_payment")) {
                    TextButton(onClick = { selected = null; onEnterTable(table) }) {
                        Text("เข้าบิลโต๊ะนี้")
                    }
                } else TextButton(onClick = { selected = null }) { Text("ปิด") }
            },
            dismissButton = {
                if (canOpen) TextButton(onClick = { if (!working) selected = null }) {
                    Text("กลับ")
                }
            }
        )
    }
}

@Composable
private fun DiningZone(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(50),
        color = if (selected) diningBlue else Color.White
    ) {
        Text(text, modifier = Modifier.padding(horizontal = 17.dp, vertical = 11.dp),
            color = if (selected) Color.White else Color(0xFF566B87),
            fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}
