package com.cpipos.pos.next.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cpipos.pos.next.core.webpos.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.util.Locale

private val catalogBlue = Color(0xFF176DED)
private val catalogInk = Color(0xFF192B47)
private val catalogMuted = Color(0xFF72839C)
private val catalogSurface = Color(0xFFF1F5FB)

/**
 * Native counterpart to Backoffice ProductManagementPanel.
 * All reads/writes use POS session scoped /api/pos/catalog-management.
 * Deletion deactivates a product; no stock overwrite or hard delete.
 */
@Composable
internal fun NativeCatalogManagementScreen(
    client: WebPosClient,
    session: WebPosSession,
    saleProducts: List<WebPosProduct>,
    onTab: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var ingredients by remember { mutableStateOf(false) }
    var filterCategory by remember { mutableStateOf("") }
    var searchInput by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var page by remember { mutableIntStateOf(1) }
    var reload by remember { mutableIntStateOf(0) }
    var loaded by remember { mutableStateOf<WebCatalogPage?>(null) }
    var loading by remember { mutableStateOf(true) }
    var unavailable by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var operationError by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<WebCatalogRow?>(null) }
    var editorOpen by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<WebCatalogRow?>(null) }
    var swipedId by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var pinError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(client, session.id, ingredients, page, search, filterCategory, reload) {
        loading = true
        error = null
        unavailable = false
        try {
            loaded = client.catalogManagement(session, ingredients, page, search, filterCategory)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            loaded = null
            val api = failure as? WebPosApiException
            if (api?.status == 404 || api?.code == "non_json_response" || api?.code == "http_404") {
                unavailable = true
                // A missing, not-yet-deployed endpoint is a capability state,
                // not a red error banner. Never pretend remote writes work.
            } else {
                error = "โหลดข้อมูลจัดการสินค้าไม่ได้: " +
                    (api?.code ?: "network_error")
            }
        } finally {
            loading = false
        }
    }

    val fallback = if (!ingredients) saleProducts
        .filter { (filterCategory.isEmpty() || it.category == filterCategory) &&
            (search.isEmpty() || it.name.contains(search, true) || it.sku.contains(search, true)) }
        .map { WebCatalogRow(
            id = it.id, name = it.name, sku = it.sku, category = it.category, price = it.price
        ) } else emptyList()
    val rows = loaded?.items ?: fallback
    val editable = loaded?.editable == true && !unavailable
    val canOpenEditor = !saving
    val categories = saleProducts.map { it.category }.filter { it.isNotBlank() }.distinct().sorted()
    val pictures = saleProducts.associateBy { it.id }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF5F8FE)) {
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().statusBarsPadding()
                    .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 104.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF19365E),
                    shape = RoundedCornerShape(23.dp),
                    shadowElevation = 3.dp
                ) {
                    Column(Modifier.padding(horizontal = 17.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text("คลังสินค้า", color = Color.White, fontSize = 24.sp,
                                    fontWeight = FontWeight.ExtraBold)
                                Text("สาขา " + (loaded?.branchName ?: session.branchName),
                                    color = Color(0xFFC7D9F4), fontSize = 12.sp)
                            }
                            Surface(color = Color(0xFF2F507C),
                                shape = RoundedCornerShape(13.dp)) {
                                Text(if (ingredients) "วัตถุดิบ" else "สินค้า " +
                                    (loaded?.total?.toString() ?: saleProducts.size.toString()),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }
                        Button(
                            enabled = canOpenEditor,
                            onClick = { editing = null; editorOpen = true; operationError = null },
                            modifier = Modifier.fillMaxWidth().height(45.dp),
                            shape = RoundedCornerShape(13.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White, contentColor = Color(0xFF17385E))
                        ) { Text(if (ingredients) "+ เพิ่มวัตถุดิบ" else "+ เพิ่มสินค้า",
                            fontWeight = FontWeight.Bold) }
                    }
                }
                Surface(color = Color.White, shape = RoundedCornerShape(16.dp)) {
                    Row(Modifier.fillMaxWidth().padding(5.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        listOf(false to "สินค้า", true to "วัตถุดิบ").forEach { choice ->
                            val active = ingredients == choice.first
                            Surface(
                                color = if (active) catalogBlue else Color.White,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f).clickable {
                                    ingredients = choice.first
                                    page = 1; filterCategory = ""; swipedId = null
                                    editorOpen = false
                                }
                            ) {
                                Text(choice.second, modifier = Modifier.padding(vertical = 11.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    color = if (active) Color.White else catalogMuted,
                                    fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = searchInput,
                    onValueChange = { searchInput = it.take(60) },
                    label = { Text("ค้นหาชื่อสินค้า / SKU / วัตถุดิบ") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    trailingIcon = {
                        TextButton(onClick = { search = searchInput.trim(); page = 1 }) {
                            Text("ค้นหา")
                        }
                    }
                )
                if (!ingredients) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        item {
                            FilterChip(selected = filterCategory.isEmpty(),
                                onClick = { filterCategory = ""; page = 1 },
                                label = { Text("ทุกหมวดหมู่") })
                        }
                        items(categories) { cat ->
                            FilterChip(selected = filterCategory == cat,
                                onClick = { filterCategory = cat; page = 1 },
                                label = { Text(cat) })
                        }
                    }
                }
                if (error != null) {
                    Text(error.orEmpty(), color = catalogMuted, fontSize = 11.sp)
                }
                if (operationError != null) {
                    Text(operationError.orEmpty(), color = Color(0xFFB14B53), fontSize = 12.sp)
                }
                Text(
                    if (ingredients) "รายการวัตถุดิบ • คงเหลือตามหน่วยจัดเก็บ"
                    else "สินค้า • ราคาขาย • จำนวนขาย 30 วัน • คงเหลือ",
                    fontSize = 12.sp, color = catalogMuted
                )
                Surface(color = Color(0xFFE5EEFB), shape = RoundedCornerShape(9.dp)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("รายการ", Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        if (!ingredients) {
                            Text("ราคา / ขาย", Modifier.width(68.dp), fontSize = 11.sp)
                        }
                        Text("คงเหลือ", Modifier.width(65.dp), fontSize = 11.sp)
                    }
                }
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                else if (rows.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(if (unavailable && ingredients) "ยังไม่มีข้อมูลวัตถุดิบที่โหลดได้"
                            else "ไม่พบรายการ", color = catalogMuted)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(rows, key = { it.id }) { item ->
                            val image = pictures[item.id]
                            Column {
                                Surface(
                                    color = if (item.active) Color.White else Color(0xFFEEF0F4),
                                    shape = RoundedCornerShape(17.dp),
                                    shadowElevation = 1.dp,
                                    modifier = Modifier.fillMaxWidth()
                                        .pointerInput(item.id) {
                                            var distance = 0f
                                            detectHorizontalDragGestures(
                                                onDragStart = { distance = 0f },
                                                onHorizontalDrag = { _, drag -> distance += drag },
                                                onDragEnd = {
                                                    if (distance < -65f && !ingredients && item.active)
                                                        swipedId = item.id
                                                    else if (distance > 65f) swipedId = null
                                                }
                                            )
                                        }.clickable(enabled = canOpenEditor) {
                                            editing = item; editorOpen = true; operationError = null
                                        }
                                ) {
                                    Row(Modifier.fillMaxWidth().padding(11.dp),
                                        verticalAlignment = Alignment.CenterVertically) {
                                        if (!ingredients) {
                                            Box(Modifier.size(53.dp).background(Color(0xFFF0F5FD),
                                                RoundedCornerShape(13.dp)),
                                                contentAlignment = Alignment.Center) {
                                                if (image != null) {
                                                    ProductArtwork(
                                                        product = DemoMenuItem(
                                                            image.id, image.name, image.category,
                                                            image.price.value,
                                                            image.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                                                            catalogBlue, image.imageUrl
                                                        ),
                                                        markerFontSize = 20.sp,
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                } else Text("▣", color = catalogMuted)
                                            }
                                            Spacer(Modifier.width(9.dp))
                                        }
                                        Column(Modifier.weight(1f)) {
                                            Text(item.name, color = catalogInk, fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold, maxLines = 2,
                                                overflow = TextOverflow.Ellipsis)
                                            Text(if (ingredients) item.baseUnit
                                                else (item.sku.ifBlank { item.category }) +
                                                    (if (!item.active) " • หยุดขาย" else ""),
                                                color = catalogMuted, fontSize = 10.sp,
                                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                        if (!ingredients) {
                                            Column(Modifier.width(68.dp),
                                                horizontalAlignment = Alignment.End) {
                                                Text("฿" + item.price.bahtText(),
                                                    color = catalogBlue, fontSize = 12.sp,
                                                    fontWeight = FontWeight.ExtraBold)
                                                Text("ขาย " + (item.sold30d?.toString() ?: "—"),
                                                    color = catalogMuted, fontSize = 10.sp)
                                            }
                                        }
                                        Column(Modifier.width(65.dp),
                                            horizontalAlignment = Alignment.End) {
                                            Text(item.stock?.let { formatCatalogQty(it) } ?: "—",
                                                color = if (item.stock != null && item.stock <= 0.0)
                                                    Color(0xFFBE4055) else catalogInk,
                                                fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            Text("คงเหลือ", color = catalogMuted, fontSize = 9.sp)
                                        }
                                    }
                                }
                                if (swipedId == item.id && !ingredients && item.active) {
                                    Button(
                                        onClick = {
                                            if (editable) {
                                                pinError = null
                                                deleteTarget = item
                                            } else {
                                                operationError = "การลบสินค้าต้องใช้สิทธิ์เจ้าของหรือผู้จัดการ"
                                            }
                                        },
                                        modifier = Modifier.align(Alignment.End)
                                            .padding(top = 4.dp, end = 3.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFFB8304B))
                                    ) { Text("ลบสินค้า  •  ยืนยัน PIN", fontSize = 12.sp) }
                                }
                            }
                        }
                        item {
                            Row(Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically) {
                                Text("หน้า $page / " + (loaded?.totalPages ?: 1),
                                    color = catalogMuted, fontSize = 12.sp)
                                Row {
                                    TextButton(enabled = page > 1, onClick = { page -= 1 }) {
                                        Text("ก่อนหน้า")
                                    }
                                    TextButton(enabled = loaded != null &&
                                        page < (loaded?.totalPages ?: 1),
                                        onClick = { page += 1 }) { Text("ถัดไป") }
                                }
                            }
                        }
                    }
                }
            }
            HomeBottomMenu(modifier = Modifier.align(Alignment.BottomCenter),
                selectedTab = "stock", onMenuClick = { if (!saving) onTab(it) })
        }
    }

    if (editorOpen) {
        var name by remember(editorOpen, editing?.id) { mutableStateOf(editing?.name.orEmpty()) }
        var skuUnit by remember(editorOpen, editing?.id) {
            mutableStateOf(if (ingredients) editing?.baseUnit ?: "piece" else editing?.sku.orEmpty())
        }
        var category by remember(editorOpen, editing?.id) {
            mutableStateOf(editing?.category ?: categories.firstOrNull().orEmpty())
        }
        var price by remember(editorOpen, editing?.id) {
            mutableStateOf(if (ingredients) "" else editing?.price?.bahtText().orEmpty())
        }
        AlertDialog(
            onDismissRequest = { if (!saving) editorOpen = false },
            title = { Text(if (editing == null) "เพิ่ม" + if (ingredients) "วัตถุดิบ" else "สินค้า"
                else "แก้ไข" + if (ingredients) "วัตถุดิบ" else "สินค้า") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it.take(120) },
                        label = { Text("ชื่อ") }, singleLine = true)
                    OutlinedTextField(value = skuUnit,
                        onValueChange = { skuUnit = it.take(60) },
                        label = { Text(if (ingredients) "หน่วยจัดเก็บ" else "SKU") },
                        readOnly = ingredients && editing != null,
                        singleLine = true)
                    if (!ingredients) {
                        OutlinedTextField(value = category,
                            onValueChange = { category = it.take(80) },
                            label = { Text("หมวดหมู่") }, singleLine = true)
                        OutlinedTextField(value = price,
                            onValueChange = { price = it.take(20) },
                            label = { Text("ราคาขาย (บาท)") }, singleLine = true)
                    }
                    editing?.let {
                        Text("คงเหลือ: " + (it.stock?.let(::formatCatalogQty) ?: "—") +
                            " • ต้องปรับสต๊อกผ่านรายการเคลื่อนไหวที่ตรวจสอบได้",
                            fontSize = 11.sp, color = catalogMuted)
                    }
                    if (!editable) {
                        Text("สามารถกรอกข้อมูลได้ แต่การบันทึกต้องเชื่อมต่อบัญชีที่มีสิทธิ์จัดการสินค้า",
                            fontSize = 11.sp, color = catalogMuted)
                    }
                    if (saving) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (operationError != null) Text(operationError.orEmpty(),
                        color = Color(0xFFB14B53), fontSize = 11.sp)
                }
            },
            confirmButton = {
                Button(enabled = editable && !saving && name.isNotBlank() && skuUnit.isNotBlank() &&
                    (ingredients || (category.isNotBlank() &&
                        price.toBigDecimalOrNull()?.let { it > java.math.BigDecimal.ZERO } == true)),
                    onClick = {
                        saving = true; operationError = null
                        scope.launch {
                            try {
                                client.saveCatalogRow(session, ingredients, editing,
                                    name, skuUnit, category, price)
                                editorOpen = false
                                reload += 1
                            } catch (e: Exception) {
                                operationError = "บันทึกไม่ได้: " +
                                    ((e as? WebPosApiException)?.code ?: "โปรดตรวจสอบเครือข่าย")
                            } finally { saving = false }
                        }
                    }) { Text("บันทึก") }
            },
            dismissButton = {
                TextButton(enabled = !saving, onClick = { editorOpen = false }) {
                    Text("ยกเลิก")
                }
            }
        )
    }
    deleteTarget?.let { target ->
        NativeCancelBillPinDialog(
            title = "ลบสินค้า: " + target.name,
            busy = saving,
            error = pinError,
            actionLabel = "ตรวจ PIN และหยุดขาย",
            actionNote = "ผู้จัดการหรือเจ้าของร้านต้องอนุมัติ • ไม่ลบประวัติการขายหรือสต๊อก",
            onDismiss = { if (!saving) { deleteTarget = null; pinError = null } },
            onApprove = { pin ->
                saving = true; pinError = null; operationError = null
                scope.launch {
                    var sent = false
                    try {
                        val approval = client.approveCatalogProductDeactivate(session, target.id, pin)
                        sent = true
                        client.deactivateCatalogProduct(session, target, approval)
                        deleteTarget = null; swipedId = null; reload += 1
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        if (sent) {
                            // A lost response might mean the product was already deactivated.
                            // Refresh instead of reissuing an approval or blind mutation.
                            deleteTarget = null; swipedId = null; reload += 1
                            operationError = "ตรวจสอบสถานะสินค้ากับเซิร์ฟเวอร์อีกครั้งก่อนดำเนินการต่อ"
                        } else {
                            pinError = "PIN ไม่ถูกต้อง หมดอายุ หรือไม่มีสิทธิ์อนุมัติ"
                        }
                    } finally { saving = false }
                }
            }
        )
    }
}

private fun formatCatalogQty(value: Double): String =
    DecimalFormat("#,##0.###", java.text.DecimalFormatSymbols(Locale.US)).format(value)
