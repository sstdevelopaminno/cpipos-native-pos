package com.cpipos.pos.next.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
internal fun NativeDiningTableScreen(client: WebPosClient, session: WebPosSession, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var floor by remember(session.id) { mutableStateOf<WebTableFloor?>(null) }
    var zone by remember(session.id) { mutableStateOf<String?>(null) }
    var loading by remember(session.id) { mutableStateOf(true) }
    var error by remember(session.id) { mutableStateOf<String?>(null) }
    var working by remember(session.id) { mutableStateOf(false) }
    var selected by remember(session.id) { mutableStateOf<WebDiningTable?>(null) }
    var detail by remember(session.id) { mutableStateOf<WebTableBillSummary?>(null) }

    suspend fun refresh() {
        try {
            floor = client.diningTables(session)
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
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp)) {
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
                            modifier = Modifier.fillMaxWidth().clickable(enabled = !working) {
                                selected = table
                                detail = null
                                if (table.sessionId != null) {
                                    scope.launch {
                                        runCatching { client.tableBill(session, table.id) }
                                            .onSuccess { detail = it }
                                    }
                                }
                            },
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
                                    Text("▣", fontSize = 47.sp, color = accent)
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
    }
    selected?.let { table ->
        val canOpen = table.status == "available" && table.sessionId == null
        AlertDialog(
            onDismissRequest = { if (!working) { selected = null; detail = null } },
            title = { Text(table.name, color = diningInk, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("สถานะ: " + diningStatus(table.status).first)
                    detail?.orderNo?.let { Text("เลขบิล: " + it, color = diningBlue) }
                    if (!canOpen) Text(
                        "โต๊ะนี้มีสถานะที่บันทึกบน POS Web แล้ว กรุณาตรวจสอบบิลเดิม",
                        fontSize = 12.sp, color = Color(0xFF72829A)
                    )
                }
            },
            confirmButton = {
                if (canOpen) {
                    Button(enabled = !working, onClick = {
                        working = true
                        scope.launch {
                            try {
                                client.openDiningTable(session, table.id)
                                selected = null
                                detail = null
                                refresh()
                            } catch (e: Exception) {
                                error = "เปิดโต๊ะไม่สำเร็จ โปรดรีเฟรชและตรวจสอบสถานะโต๊ะเดิม"
                                // Do not retry an uncertain table-open automatically.
                                selected = null
                            } finally { working = false }
                        }
                    }) { Text(if (working) "กำลังเปิดโต๊ะ" else "เปิดโต๊ะ") }
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
