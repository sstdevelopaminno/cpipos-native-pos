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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cpipos.pos.next.core.webpos.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import java.util.UUID

private val moveBlue = Color(0xFF176DED)
private val moveInk = Color(0xFF182C4E)

internal fun canMoveToTarget(table: WebDiningTable): Boolean =
    table.status == "available" && table.sessionId == null

/**
 * Existing CpIPOS atomic move_table_bill_session_tx via POS /move-bill.
 * This UI never opens a new bill or moves an unsent/uncertain native draft.
 */
@Composable
internal fun NativeTableMoveDialog(
    client: WebPosClient,
    session: WebPosSession,
    source: WebDiningTable,
    originalBill: WebDiningBill?,
    canMoveLocalDraft: Boolean,
    onDismiss: () -> Unit,
    onMoved: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val journal = remember(context.applicationContext) {
        NativeTableMoveJournal(context.applicationContext)
    }
    val billSession = originalBill?.sessionId ?: source.sessionId
    var pending by remember(session.tenantId, session.branchId,
        session.deviceCode, source.id, billSession) {
        mutableStateOf(billSession?.let { journal.pending(session, source.id, it) })
    }
    var tables by remember(session.id, source.id) { mutableStateOf<List<WebDiningTable>>(emptyList()) }
    var target by remember(session.id, source.id, billSession) {
        mutableStateOf<String?>(pending?.targetId)
    }
    var loading by remember(session.id, source.id) { mutableStateOf(true) }
    var saving by remember(session.id, source.id) { mutableStateOf(false) }
    var uncertain by remember(session.id, source.id, billSession) {
        mutableStateOf(pending != null)
    }
    var error by remember(session.id, source.id) { mutableStateOf<String?>(null) }
    var confirmed by remember(session.id, source.id) { mutableStateOf(false) }

    suspend fun refresh() {
        val floor = client.diningTables(session)
        tables = floor.tables
    }
    LaunchedEffect(client, session.id, source.id) {
        try { refresh() } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            error = "โหลดโต๊ะจากระบบหลักไม่ได้ กรุณาตรวจสอบการเชื่อมต่อ"
        } finally { loading = false }
    }

    Dialog(
        onDismissRequest = { if (!saving) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = !saving, dismissOnClickOutside = !saving
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = Color.White, shadowElevation = 14.dp
        ) {
            Column(
                Modifier.fillMaxWidth().padding(17.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("ย้ายโต๊ะ", color = moveInk,
                    fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                Text("จากโต๊ะ " + source.name + " • " + session.branchName,
                    color = Color(0xFF7184A0), fontSize = 12.sp)
                HorizontalDivider(color = Color(0xFFE5EBF5))
                if (loading || saving) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (error != null) Text(error.orEmpty(),
                    color = Color(0xFFB53D52), fontSize = 12.sp)
                if (!canMoveLocalDraft) {
                    Text("มีรายการค้างส่งหรือสถานะไม่แน่นอนในมือถือ " +
                        "กรุณาบันทึกและตรวจบิลก่อนย้ายโต๊ะ",
                        color = Color(0xFFAD4859), fontSize = 12.sp)
                }
                if (billSession == null || originalBill?.paid == true) {
                    Text("ต้องมีบิลโต๊ะที่ยังไม่ชำระก่อนจึงจะย้ายได้",
                        color = Color(0xFFAD4859), fontSize = 12.sp)
                }
                Text("เลือกโต๊ะปลายทางที่ว่าง", fontWeight = FontWeight.Bold,
                    fontSize = 13.sp, color = moveInk)
                val destinations = tables.filter { it.id != source.id && canMoveToTarget(it) }
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 310.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(destinations, key = { it.id }) { row ->
                        val selected = target == row.id
                        Surface(
                            modifier = Modifier.fillMaxWidth()
                                .clickable(enabled = !saving && !uncertain) {
                                    target = row.id
                                    confirmed = false
                                },
                            color = if (selected) Color(0xFFE7F1FF) else Color(0xFFF4F7FC),
                            shape = RoundedCornerShape(13.dp)
                        ) {
                            Row(Modifier.fillMaxWidth().padding(13.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically) {
                                Column {
                                    Text(row.name.ifBlank { row.code }, fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp, color = moveInk)
                                    Text("โต๊ะว่าง", fontSize = 11.sp, color = Color(0xFF6686AA))
                                }
                                RadioButton(selected = selected, onClick = {
                                    if (!saving && !uncertain) {
                                        target = row.id
                                        confirmed = false
                                    }
                                }, enabled = !saving && !uncertain)
                            }
                        }
                    }
                }
                if (!loading && destinations.isEmpty()) {
                    Text("ไม่พบโต๊ะว่างในสาขานี้", color = Color(0xFF7184A0),
                        fontSize = 12.sp)
                }
                val to = destinations.firstOrNull { it.id == target }
                if (to != null && !confirmed) {
                    Text("ย้ายบิลจาก ${source.code} ไป ${to.code} " +
                        "โดยใช้บิลเดิมและคงรายการอาหาร/การชำระไว้",
                        fontSize = 12.sp, color = moveInk)
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { confirmed = true }, enabled = !saving && !uncertain
                    ) { Text("ตรวจสอบรายการและยืนยันโต๊ะปลายทาง") }
                }
                if (uncertain) {
                    Text("ส่งคำขอย้ายแล้วแต่ผลตอบกลับยังไม่แน่นอน " +
                        "ห้ามกดย้ายซ้ำ โปรดตรวจจาก POS หลัก",
                        color = Color(0xFFB53D52), fontSize = 12.sp)
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !saving,
                        onClick = {
                            saving = true
                            scope.launch {
                                try {
                                    refresh()
                                    val current = tables.firstOrNull { it.id == target }
                                    val original = tables.firstOrNull { it.id == source.id }
                                    if (current?.sessionId == billSession &&
                                        original?.sessionId != billSession) {
                                        val entry = pending
                                        if (entry != null && billSession != null &&
                                            entry.requestId.isNotBlank()) {
                                            journal.confirmed(
                                                session, source.id, billSession, entry.requestId)
                                            pending = null
                                        }
                                        onMoved()
                                    } else {
                                        error = "ยังยืนยันผลย้ายไม่ได้ กรุณาตรวจบิลบน CpIPOS หลัก"
                                    }
                                } catch (_: Exception) {
                                    error = "ตรวจสอบโต๊ะไม่สำเร็จ"
                                } finally { saving = false }
                            }
                        }
                    ) { Text("ตรวจสอบผลการย้าย") }
                } else {
                    Button(
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        enabled = !loading && !saving && confirmed &&
                            to != null && canMoveLocalDraft &&
                            originalBill != null && !originalBill.paid &&
                            billSession != null,
                        shape = RoundedCornerShape(13.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = moveBlue),
                        onClick = {
                            if (to == null || billSession == null) return@Button
                            val targetId = to.id
                            saving = true
                            error = null
                            scope.launch {
                                var submitted = false
                                try {
                                    val fresh = client.diningTables(session)
                                    val fromCurrent = fresh.tables.firstOrNull { it.id == source.id }
                                    val toCurrent = fresh.tables.firstOrNull { it.id == targetId }
                                    check(fromCurrent?.sessionId == billSession &&
                                        toCurrent != null && canMoveToTarget(toCurrent)) {
                                        "table_state_changed"
                                    }
                                    val serverBill = client.diningBill(session, source.id)
                                    check(serverBill.sessionId == billSession &&
                                        !serverBill.paid) { "bill_changed" }
                                    // Durable marker MUST commit before the first POST. Never
                                    // send a second request after an uncertain response.
                                    val requestId = UUID.randomUUID().toString()
                                    journal.prepare(
                                        session, source.id, targetId, billSession, requestId
                                    )
                                    pending = journal.pending(session, source.id, billSession)
                                    submitted = true
                                    val result = client.moveDiningBill(session, source.id, targetId)
                                    check(result.billSessionId == billSession && result.moved) {
                                        "move_result_unconfirmed"
                                    }
                                    val newFloor = client.diningTables(session)
                                    check(newFloor.tables.any {
                                        it.id == targetId && it.sessionId == billSession
                                    }) { "move_needs_reconcile" }
                                    val entry = pending
                                    check(entry != null && entry.requestId == requestId)
                                    journal.confirmed(session, source.id, billSession, requestId)
                                    pending = null
                                    onMoved()
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (failure: Exception) {
                                    if (submitted) {
                                        uncertain = true
                                        error = "ผลย้ายโต๊ะยังไม่แน่นอน ห้ามย้ายซ้ำโดยไม่ตรวจสอบ"
                                    } else {
                                        error = "สถานะโต๊ะเปลี่ยน หรือโหลดบิลล่าสุดไม่ได้ กรุณารีเฟรช"
                                        confirmed = false
                                    }
                                } finally { saving = false }
                            }
                        }
                    ) { Text("ยืนยันย้ายโต๊ะ", fontWeight = FontWeight.Bold) }
                }
                TextButton(
                    enabled = !saving,
                    modifier = Modifier.align(Alignment.End),
                    onClick = onDismiss
                ) { Text("ปิด") }
            }
        }
    }
}
