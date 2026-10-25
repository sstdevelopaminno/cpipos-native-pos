package com.cpipos.pos.next.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cpipos.pos.next.core.webpos.WebDiningTable
import com.cpipos.pos.next.core.webpos.WebPosApiException
import com.cpipos.pos.next.core.webpos.WebPosClient
import com.cpipos.pos.next.core.webpos.WebPosSession
import com.cpipos.pos.next.core.webpos.WebTableFloor
import com.cpipos.pos.next.core.webpos.WebTableOrderQr
import com.cpipos.pos.next.printing.TableOrderQrPrinter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val qrBlue = Color(0xFF176DED)
private val qrInk = Color(0xFF193254)
private val qrMuted = Color(0xFF72849C)
private val thaiTime = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
    .withZone(ZoneId.of("Asia/Bangkok"))

/** Match CpIPOS Web's open-bill guard; no synthetic QR for unopened tables. */
internal fun eligibleForTableOrderQr(table: WebDiningTable): Boolean =
    table.sessionId != null &&
        table.status in setOf("occupied", "ordering", "pending_payment")

/**
 * Existing CpIPOS customer QR ordering, native mobile presentation only.
 * Reads branch-scoped floor -> issues server-signed QR -> Android PrintManager.
 */
@Composable
internal fun NativeTableOrderQrDialog(
    client: WebPosClient,
    session: WebPosSession,
    onDismiss: () -> Unit,
    selectedTableId: String? = null
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var floor by remember(session.id) { mutableStateOf<WebTableFloor?>(null) }
    var loading by remember(session.id) { mutableStateOf(true) }
    var working by remember(session.id) { mutableStateOf(false) }
    var error by remember(session.id) { mutableStateOf<String?>(null) }
    var issued by remember(session.id) { mutableStateOf<WebTableOrderQr?>(null) }
    var bitmap by remember(session.id) { mutableStateOf<Bitmap?>(null) }
    var autoRequested by remember(session.id, selectedTableId) { mutableStateOf(false) }

    LaunchedEffect(client, session.id) {
        try {
            floor = client.diningTables(session)
            error = null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = "โหลดโต๊ะจาก CpIPOS ไม่สำเร็จ กรุณาตรวจสอบการเชื่อมต่อและสิทธิ์"
        } finally {
            loading = false
        }
    }

    LaunchedEffect(issued?.qrPngBase64) {
        val image = issued?.qrPngBase64
        bitmap = null
        if (image != null) {
            bitmap = withContext(Dispatchers.Default) {
                runCatching {
                    val bytes = Base64.decode(image, Base64.DEFAULT)
                    require(bytes.size <= 1_000_000) { "qr_image_too_large" }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }.getOrNull()
            }
            if (bitmap == null) error = "รูป QR จากเซิร์ฟเวอร์ไม่ถูกต้อง กรุณาเปิดใหม่"
        }
    }

    fun getQr(table: WebDiningTable) {
        if (working) return
        working = true
        error = null
        scope.launch {
            try {
                // Fresh server floor state prevents issuing a QR for a closed,
                // replaced, or transferred table session.
                val latest = client.diningTables(session)
                floor = latest
                val current = latest.tables.firstOrNull { it.id == table.id }
                if (current == null || !eligibleForTableOrderQr(current) ||
                    current.sessionId != table.sessionId) {
                    error = "โต๊ะนี้ยังไม่ได้เปิดบิล หรือสถานะโต๊ะเปลี่ยนแล้ว"
                    return@launch
                }
                val next = client.issueTableOrderQr(session, table.id)
                check(next.tableSessionId == current.sessionId) {
                    "qr_table_session_changed"
                }
                issued = next
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = when ((failure as? WebPosApiException)?.code) {
                    "table_not_open", "table_session_not_open" ->
                        "กรุณาเปิดบิลโต๊ะในเมนูเลือกโต๊ะก่อนออก QR"
                    "permission_denied", "forbidden_role" ->
                        "บัญชีนี้ไม่มีสิทธิ์ออก QR โต๊ะ กรุณาติดต่อเจ้าของร้านหรือผู้จัดการ"
                    "feature_not_enabled", "feature_disabled", "feature_not_in_plan" ->
                        "สาขานี้ยังไม่ได้เปิดสิทธิ์ QR สั่งอาหาร"
                    "buffet_plan_not_committed" ->
                        "ต้องยืนยันแพ็กเกจบุฟเฟ่ในบิลก่อนออก QR"
                    "table_qr_issue_timeout" ->
                        "เซิร์ฟเวอร์ตอบช้า กรุณาตรวจสอบ QR เดิมก่อนลองอีกครั้ง"
                    else -> "ออก QR โต๊ะไม่สำเร็จ กรุณาตรวจสอบสิทธิ์และสถานะโต๊ะ"
                }
            } finally {
                working = false
            }
        }
    }

    LaunchedEffect(floor, selectedTableId) {
        if (selectedTableId != null && !autoRequested && floor != null) {
            autoRequested = true
            val current = floor?.tables?.firstOrNull { it.id == selectedTableId }
            if (current != null && eligibleForTableOrderQr(current)) {
                getQr(current)
            } else {
                error = "ยังไม่มีบิลที่เปิดอยู่สำหรับโต๊ะนี้ กรุณาเปิดโต๊ะก่อนออก QR"
            }
        }
    }

    Dialog(
        onDismissRequest = { if (!working) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = !working, dismissOnClickOutside = !working
        )
    ) {
        Surface(
            shape = RoundedCornerShape(25.dp), color = Color(0xFFFDFEFF),
            shadowElevation = 14.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.fillMaxWidth().padding(17.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("QR โต๊ะสั่งอาหาร", fontWeight = FontWeight.ExtraBold,
                            fontSize = 19.sp, color = qrInk)
                        Text(session.branchName, fontSize = 11.sp, color = qrMuted)
                    }
                    TextButton(onClick = onDismiss, enabled = !working) { Text("ปิด") }
                }
                HorizontalDivider(color = Color(0xFFE7ECF5))
                if (loading || working) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(),
                        color = qrBlue)
                }
                error?.let {
                    Text(it, color = Color(0xFFB04353), fontSize = 12.sp,
                        textAlign = TextAlign.Center)
                }

                val current = issued
                if (current == null) {
                    Text("เลือกโต๊ะที่เปิดบิลอยู่ เพื่อให้ลูกค้าสแกนสั่งอาหาร",
                        fontSize = 12.sp, color = qrMuted, textAlign = TextAlign.Center)
                    val tables = floor?.tables.orEmpty()
                    if (!loading && tables.isEmpty()) {
                        Text("ยังไม่พบโต๊ะ กรุณาตรวจสอบเมนูเลือกโต๊ะ",
                            fontSize = 12.sp, color = qrMuted)
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        items(tables, key = { it.id }) { table ->
                            val enabled = eligibleForTableOrderQr(table)
                            Surface(
                                modifier = Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable(enabled = enabled && !working) { getQr(table) },
                                color = if (enabled) Color(0xFFEBF4FF) else Color(0xFFF2F4F7),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Row(Modifier.fillMaxWidth().padding(13.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Text("▦", fontSize = 25.sp,
                                        color = if (enabled) qrBlue else qrMuted)
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(table.name.ifBlank { table.code },
                                            fontWeight = FontWeight.Bold, color = qrInk,
                                            fontSize = 14.sp)
                                        Text(if (enabled) "เปิดบิลแล้ว • แตะเพื่อแสดง QR"
                                            else "ต้องเปิดบิลก่อน", fontSize = 10.sp,
                                            color = qrMuted)
                                    }
                                    Text(if (enabled) "เลือก ›" else "—",
                                        fontSize = 12.sp, color = if (enabled) qrBlue else qrMuted)
                                }
                            }
                        }
                    }
                    if (!loading && floor == null) {
                        OutlinedButton(onClick = {
                            working = true
                            scope.launch {
                                try {
                                    floor = client.diningTables(session)
                                    error = null
                                } catch (e: Exception) {
                                    error = "โหลดโต๊ะไม่ได้ กรุณาตรวจสอบเครือข่าย"
                                } finally { working = false }
                            }
                        }) { Text("ลองใหม่") }
                    }
                } else {
                    Text("โต๊ะ " + current.tableName.ifBlank { current.tableCode },
                        fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = qrInk)
                    val qr = bitmap
                    if (qr != null) {
                        Surface(color = Color.White,
                            shape = RoundedCornerShape(15.dp),
                            shadowElevation = 3.dp) {
                            androidx.compose.foundation.Image(
                                bitmap = qr.asImageBitmap(),
                                contentDescription = "QR สั่งอาหารโต๊ะ " + current.tableCode,
                                modifier = Modifier.size(225.dp).padding(8.dp),
                                contentScale = ContentScale.Fit
                            )
                        }
                    } else if (error == null) {
                        CircularProgressIndicator(color = qrBlue, modifier = Modifier.size(40.dp))
                    }
                    Text(if (current.expiryMode == "bill")
                        "ใช้ได้จนกว่าจะปิดบิล (ภายใต้เวลาจำกัดของระบบ)"
                        else "QR หมดอายุ: " + thaiTime.format(Instant.parse(current.expiresAt)),
                        fontSize = 12.sp, color = qrMuted, textAlign = TextAlign.Center)
                    Button(
                        onClick = {
                            if (working || bitmap == null) return@Button
                            working = true
                            scope.launch {
                                try {
                                    val fresh = client.diningTables(session)
                                    floor = fresh
                                    val table = fresh.tables.firstOrNull { it.id == current.tableId }
                                    check(table != null && eligibleForTableOrderQr(table) &&
                                        table.sessionId == current.tableSessionId) {
                                        "table_session_closed"
                                    }
                                    check(Instant.parse(current.expiresAt).isAfter(Instant.now())) {
                                        "table_qr_expired"
                                    }
                                    TableOrderQrPrinter.print(context, current, bitmap!!, session.branchName)
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    error = "พิมพ์ไม่ได้ หรือ QR หมดอายุ กรุณาตรวจสอบโต๊ะก่อนพิมพ์"
                                } finally { working = false }
                            }
                        },
                        enabled = !working && bitmap != null,
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = RoundedCornerShape(13.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = qrBlue)
                    ) { Text("พิมพ์ QR สั่งอาหารโต๊ะ", fontWeight = FontWeight.Bold) }
                    OutlinedButton(
                        enabled = !working,
                        onClick = { issued = null; bitmap = null; error = null },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("เลือกโต๊ะอื่น") }
                }
            }
        }
    }
}
