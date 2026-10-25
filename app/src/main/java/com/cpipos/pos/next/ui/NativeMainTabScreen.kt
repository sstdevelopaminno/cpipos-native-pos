package com.cpipos.pos.next.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cpipos.pos.next.core.webpos.*
import kotlinx.coroutines.CancellationException

/**
 * Native read-only home sections. Selecting any tab again remounts its root
 * via tabRevision in LiveScreen. It never restarts authentication or discards
 * the pending takeaway workspace.
 */
@Composable
internal fun NativeMainTabScreen(
    tab: String,
    client: WebPosClient,
    session: WebPosSession,
    products: List<WebPosProduct>,
    onTab: (String) -> Unit
) {
    if (tab == "settings") {
        NativeSettingsHubScreen(client = client, session = session, onTab = onTab)
        return
    }
    if (tab == "report") {
        NativeSalesListScreen(client = client, session = session, onTab = onTab)
        return
    }
    if (tab == "stock") {
        NativeCatalogManagementScreen(client, session, products, onTab)
        return
    }
    var history by remember(session.id, tab) { mutableStateOf<WebReceiptHistory?>(null) }
    var page by remember(session.id, tab) { mutableStateOf(1) }
    var loading by remember(session.id, tab) { mutableStateOf(tab == "history" || tab == "report") }
    var error by remember(session.id, tab) { mutableStateOf<String?>(null) }

    LaunchedEffect(client, session.id, tab, page) {
        if (tab == "history" || tab == "report") {
            try {
                loading = true
                history = client.receiptHistory(
                    session = session, query = "", page = page, pageSize = 40
                )
                error = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = "ไม่สามารถโหลดข้อมูลจาก POS Web ได้ กรุณาตรวจสอบสิทธิ์และเครือข่าย"
            } finally { loading = false }
        }
    }
    val title = when (tab) {
        "report" -> "รายการขาย"
        "history" -> "รายการ"
        "stock" -> "ข้อมูลสินค้า"
        else -> "ข้อมูลระบบ"
    }
    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF5F9FF)) {
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().statusBarsPadding()
                    .padding(start = 17.dp, end = 17.dp, top = 15.dp, bottom = 112.dp)
            ) {
                Text(title, fontSize = 24.sp, color = Color(0xFF172B4E),
                    fontWeight = FontWeight.ExtraBold)
                Text(session.branchName + " · " + session.deviceCode,
                    color = Color(0xFF71849B), fontSize = 12.sp)
                if (tab == "history") {
                    Text("รายการขายสำเร็จทั้งโหมดกลับบ้านและนั่งโต๊ะ • ย้อนหลัง 30 วัน",
                        color = Color(0xFF71849B), fontSize = 11.sp)
                }
                Spacer(Modifier.height(20.dp))
                when {
                    loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFF176DED))
                    }
                    error != null -> Text(error.orEmpty(), color = Color(0xFFAD4450))
                    tab == "history" || tab == "report" -> {
                        val records = history?.records.orEmpty()
                        if (records.isEmpty()) {
                            Text("ไม่มีรายการในช่วงประวัติที่โหลด", color = Color(0xFF71849B))
                        } else {
                            Text("ทั้งหมด " + (history?.total ?: records.size) + " บิล",
                                color = Color(0xFF657A96), fontSize = 12.sp,
                                modifier = Modifier.padding(bottom = 10.dp))
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(records, key = { it.orderId }) { record ->
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = Color.White,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            Modifier.fillMaxWidth().padding(15.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column {
                                                Text(record.orderNo, fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF193251))
                                                Text(
                                                    record.receipt.modeLabel + " · " +
                                                        (if (record.receipt.paymentMethod == "bank_transfer")
                                                            "เงินโอน" else "เงินสด"),
                                                    fontSize = 11.sp,
                                                    color = Color(0xFF8796AC)
                                                )
                                            }
                                            Text("฿" + record.receipt.total.bahtText(),
                                                fontWeight = FontWeight.Bold, color = Color(0xFF176DED))
                                        }
                                    }
                                }
                                if ((history?.totalPages ?: 1) > 1) {
                                    item {
                                        Row(modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically) {
                                            OutlinedButton(
                                                onClick = { if (page > 1) page -= 1 },
                                                enabled = page > 1
                                            ) { Text("ก่อนหน้า") }
                                            Text(page.toString() + "/" +
                                                (history?.totalPages ?: page),
                                                fontSize = 12.sp, color = Color(0xFF677A95))
                                            OutlinedButton(
                                                onClick = { page += 1 },
                                                enabled = page < (history?.totalPages ?: page)
                                            ) { Text("ถัดไป") }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    tab == "stock" -> {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                            items(products, key = { it.id }) { product ->
                                Surface(
                                    shape = RoundedCornerShape(15.dp), color = Color.White,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        Modifier.fillMaxWidth().padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(product.name, color = Color(0xFF172B4E),
                                            modifier = Modifier.weight(1f))
                                        Text("฿" + product.price.bahtText(),
                                            color = Color(0xFF176DED), fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                    else -> {
                        Surface(color = Color.White, shape = RoundedCornerShape(18.dp)) {
                            Column(Modifier.fillMaxWidth().padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("ร้าน: " + session.storeName)
                                Text("สาขา: " + session.branchName)
                                Text("เครื่องขาย: " + session.deviceCode)
                                Text("พนักงาน: " + session.cashierName)
                                Text("การตั้งค่าร้านและการชำระเงินใช้ฐานข้อมูลร่วมกับ CpIPOS Web",
                                    fontSize = 12.sp, color = Color(0xFF71849B))
                            }
                        }
                    }
                }
            }
            HomeBottomMenu(
                modifier = Modifier.align(Alignment.BottomCenter),
                selectedTab = tab,
                onMenuClick = onTab
            )
        }
    }
}
