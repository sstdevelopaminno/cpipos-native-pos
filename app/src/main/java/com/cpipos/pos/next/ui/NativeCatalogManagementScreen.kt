package com.cpipos.pos.next.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
                error = "API จัดการสินค้าบน CpIPOS ยังไม่เปิดใช้ — แสดงสินค้าสำหรับดูอย่างเดียว"
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
    val categories = saleProducts.map { it.category }.filter { it.isNotBlank() }.distinct().sorted()
    val pictures = saleProducts.associateBy { it.id }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF5F8FE)) {
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().statusBarsPadding()
                    .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 104.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("จัดการสินค้า", fontSize = 23.sp, fontWeight = FontWeight.ExtraBold,
                            color = catalogInk)
                        Text("สาขา: " + (loaded?.branchName ?: session.branchName) +
                            " • เครื่อง " + session.deviceCode,
                            fontSize = 11.sp, color = catalogMuted)
                    }
                    Button(
                        enabled = editable && !saving,
                        onClick = { editing = null; editorOpen = true; operationError = null },
                        contentPadding = PaddingValues(horizontal = 13.dp, vertical = 8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = catalogBlue)
                    ) { Text("+ เพิ่ม", fontSize = 13.sp) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !ingredients, onClick = {
                        ingredients = false; page = 1; filterCategory = ""; swipedId = null
                    }, label = { Text("สินค้า") })
                    FilterChip(selected = ingredients, onClick = {
                        ingredients = true; page = 1; filterCategory = ""; swipedId = null
                    }, label = { Text("วัตถุดิบ") })
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
                    Text(error.orEmpty(), color = Color(0xFFB14B53), fontSize = 12.sp)
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
                            Text("ราคา", Modifier.width(68.dp), fontSize = 11.sp)
                            Text("ขาย", Modifier.width(39.dp), fontSize = 11.sp)
                        }
                        Text("คงเหลือ", Modifier.width(58.dp), fontSize = 11.sp)
                    }
                }
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                else if (rows.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(if (ingredients && unavailable) "ข้อมูลวัตถุดิบต้องใช้ API จาก CpIPOS หลัก"
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
                                    shape = RoundedCornerShape(11.dp),
                                    modifier = Modifier.fillMaxWidth()
                                        .pointerInput(item.id) {
                                            var distance = 0f
                                            detectHorizontalDragGestures(
                                                onDragStart = { distance = 0f },
                                                onHorizontalDrag = { _, drag -> distance += drag },
                                                onDragEnd = {
                                                    if (distance < -65f && !ingredients && editable)
                                                        swipedId = item.id
                                                    else if (distance > 65f) swipedId = null
                                                }
                                            )
                                        }.clickable(enabled = !saving && editable) {
                                            editing = item; editorOpen = true; operationError = null
                                        }
                                ) {
                                    Row(Modifier.fillMaxWidth().padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically) {
                                        if (!ingredients) {
                                            Box(Modifier.size(43.dp).background(Color(0xFFF0F5FD),
                                                RoundedCornerShape(8.dp)),
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
                                            Spacer(Modifier.width(6.dp))
                                        }
                                        Column(Modifier.weight(1f)) {
                                            Text(item.name, color = catalogInk, fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold, maxLines = 2,
                                                overflow = TextOverflow.Ellipsis)
                                            Text(if (ingredients) item.baseUnit
                                                else (item.sku.ifBlank { item.category }) +
                                                    (if (!item.active) " • หยุดขาย" else ""),
                                                color = catalogMuted, fontSize = 10.sp,
                                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                        if (!ingredients) {
                                            Text("฿" + item.price.bahtText(), Modifier.width(68.dp),
                                                color = catalogBlue, fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold)
                                            Text(item.sold30d?.toString() ?: "—", Modifier.width(39.dp),
                                                color = catalogInk, fontSize = 11.sp)
                                        }
                                        Text(item.stock?.let { formatCatalogQty(it) } ?: "—",
                                            Modifier.width(58.dp), color = catalogInk, fontSize = 11.sp)
                                    }
                                }
                                if (swipedId == item.id && editable && !ingredients && item.active) {
                                    TextButton(
                                        onClick = { deleteTarget = item },
                                        modifier = Modifier.align(Alignment.End),
                                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFB92F4E))
                                    ) { Text("ลบสินค้า (หยุดขาย)") }
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

    if (editorOpen && editable) {
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
                    if (saving) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (operationError != null) Text(operationError.orEmpty(),
                        color = Color(0xFFB14B53), fontSize = 11.sp)
                }
            },
            confirmButton = {
                Button(enabled = !saving && name.isNotBlank() && skuUnit.isNotBlank() &&
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
        AlertDialog(onDismissRequest = { if (!saving) deleteTarget = null },
            title = { Text("หยุดขายสินค้า") },
            text = { Text("ยืนยันหยุดขาย “" + target.name +
                "”? ระบบจะไม่ลบประวัติยอดขายหรือข้อมูลสต๊อก") },
            confirmButton = {
                Button(enabled = !saving, onClick = {
                    saving = true; operationError = null
                    scope.launch {
                        try {
                            client.deactivateCatalogProduct(session, target)
                            deleteTarget = null; swipedId = null; reload += 1
                        } catch (e: Exception) {
                            operationError = "หยุดขายไม่ได้: " +
                                ((e as? WebPosApiException)?.code ?: "โปรดตรวจสอบเครือข่าย")
                        } finally { saving = false }
                    }
                }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB92F4E))) {
                    Text("ยืนยันหยุดขาย")
                }
            },
            dismissButton = {
                TextButton(enabled = !saving, onClick = { deleteTarget = null }) {
                    Text("ยกเลิก")
                }
            })
    }
}

private fun formatCatalogQty(value: Double): String =
    DecimalFormat("#,##0.###", java.text.DecimalFormatSymbols(Locale.US)).format(value)
