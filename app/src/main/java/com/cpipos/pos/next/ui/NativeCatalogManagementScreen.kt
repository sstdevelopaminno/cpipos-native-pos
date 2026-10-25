package com.cpipos.pos.next.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.window.Dialog
import com.cpipos.pos.next.core.webpos.*
import java.text.DecimalFormat
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private val catalogBlue = Color(0xFF176DED)
private val catalogInk = Color(0xFF192B47)
private val catalogMuted = Color(0xFF72839C)

private data class RecipeInput(
    val ingredient: WebCatalogRow,
    val selected: Boolean = false,
    val quantity: String = "",
    val unit: String = if (ingredient.baseUnit.lowercase() in setOf("piece", "unit", "ลูก")) "piece" else "gram"
)

@Composable
internal fun NativeCatalogManagementScreen(
    client: WebPosClient,
    session: WebPosSession,
    saleProducts: List<WebPosProduct>,
    onTab: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var ingredients by remember { mutableStateOf(false) }
    var branches by remember(session.id) {
        mutableStateOf(listOf(WebCatalogBranch(session.branchId, session.branchName, role = "owner")))
    }
    var selectedBranchId by remember(session.id) { mutableStateOf(session.branchId) }
    var branchPickerOpen by remember { mutableStateOf(false) }
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
    var productCreateOpen by remember { mutableStateOf(false) }
    var ingredientCreateOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<WebCatalogRow?>(null) }
    var deleteTarget by remember { mutableStateOf<WebCatalogRow?>(null) }
    var swipedId by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var pinError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(client, session.id) {
        try {
            val serverBranches = client.catalogBranches(session)
            if (serverBranches.isNotEmpty()) {
                branches = serverBranches
                if (branches.none { it.id == selectedBranchId }) {
                    selectedBranchId = branches.first().id
                }
            }
        } catch (_: Exception) {
        }
    }

    LaunchedEffect(
        client, session.id, selectedBranchId, ingredients, page, search, filterCategory, reload
    ) {
        loading = true
        error = null
        unavailable = false
        try {
            val result = client.catalogManagement(
                session = session,
                branchId = selectedBranchId,
                ingredients = ingredients,
                page = page,
                pageSize = 20,
                search = search,
                category = filterCategory
            )
            loaded = result
            if (result.branches.isNotEmpty()) branches = result.branches
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            loaded = null
            val api = failure as? WebPosApiException
            if (api?.status == 404 || api?.code == "non_json_response" || api?.code == "http_404") {
                unavailable = true
            } else {
                error = "โหลดข้อมูลจัดการสินค้าไม่ได้: " + (api?.code ?: "network_error")
            }
        } finally {
            loading = false
        }
    }

    val safeFallback = selectedBranchId == session.branchId
    val fallback = if (!ingredients && safeFallback) {
        saleProducts
            .filter {
                (filterCategory.isEmpty() || it.category == filterCategory) &&
                    (search.isEmpty() || it.name.contains(search, true) || it.sku.contains(search, true))
            }
            .map {
                WebCatalogRow(
                    id = it.id,
                    name = it.name,
                    sku = it.sku,
                    category = it.category,
                    price = it.price
                )
            }
    } else emptyList()

    val rows = loaded?.items ?: fallback
    val editable = loaded?.editable == true && !unavailable
    val selectedBranch = branches.firstOrNull { it.id == selectedBranchId }
        ?: WebCatalogBranch(selectedBranchId, loaded?.branchName ?: session.branchName)
    val categories = (loaded?.categories.orEmpty() +
        if (safeFallback) saleProducts.map { it.category } else emptyList())
        .filter { it.isNotBlank() }.distinct().sorted()
    val pictures = if (safeFallback) saleProducts.associateBy { it.id } else emptyMap()

    fun changeBranch(branchId: String) {
        if (branchId == selectedBranchId || saving) return
        selectedBranchId = branchId
        page = 1
        filterCategory = ""
        search = ""
        searchInput = ""
        swipedId = null
        editing = null
        reload += 1
    }

    Surface(Modifier.fillMaxSize(), color = Color(0xFFF5F8FE)) {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().statusBarsPadding()
                    .padding(horizontal = 13.dp)
                    .padding(bottom = 106.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 13.dp, bottom = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("คลังสินค้า", fontSize = 23.sp, fontWeight = FontWeight.ExtraBold, color = catalogInk)
                        Text(selectedBranch.name + " · CpIPOS", fontSize = 11.sp, color = catalogMuted)
                    }
                    TextButton(onClick = { reload += 1 }, enabled = !loading && !saving) {
                        Text("↻ รีเฟรช", color = catalogBlue)
                    }
                }

                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(15.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("ดูสินค้า / สต๊อกสาขา", fontSize = 11.sp, color = catalogMuted)
                            Text(
                                selectedBranch.name +
                                    selectedBranch.code.takeIf { it.isNotBlank() }?.let { " (" + it + ")" }.orEmpty(),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = catalogInk
                            )
                        }
                        OutlinedButton(
                            onClick = { branchPickerOpen = true },
                            enabled = branches.size > 1 && !saving,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(if (branches.size > 1) "เลือกสาขา" else "สาขาปัจจุบัน", fontSize = 11.sp)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(false to "สินค้า", true to "วัตถุดิบ").forEach { choice ->
                        val active = ingredients == choice.first
                        Surface(
                            modifier = Modifier.weight(1f).clickable(enabled = !saving) {
                                ingredients = choice.first
                                page = 1
                                filterCategory = ""
                                swipedId = null
                                editing = null
                            },
                            shape = RoundedCornerShape(15.dp),
                            color = if (active) catalogBlue else Color.White
                        ) {
                            Text(
                                choice.second,
                                modifier = Modifier.padding(vertical = 12.dp),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                color = if (active) Color.White else catalogInk
                            )
                        }
                    }
                }

                Button(
                    onClick = {
                        operationError = null
                        if (ingredients) ingredientCreateOpen = true else productCreateOpen = true
                    },
                    enabled = editable && !saving,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = catalogBlue)
                ) {
                    Text(if (ingredients) "+ เพิ่มวัตถุดิบ / รับเข้า" else "+ เพิ่มสินค้า", fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = searchInput,
                    onValueChange = { searchInput = it.take(60) },
                    label = { Text(if (ingredients) "ค้นหาวัตถุดิบ" else "ค้นหาชื่อสินค้า / SKU") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    trailingIcon = {
                        TextButton(onClick = {
                            search = searchInput.trim()
                            page = 1
                        }) { Text("ค้นหา") }
                    }
                )

                if (!ingredients && categories.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier.padding(vertical = 7.dp),
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        item {
                            FilterChip(
                                selected = filterCategory.isEmpty(),
                                onClick = { filterCategory = ""; page = 1 },
                                label = { Text("ทุกหมวดหมู่") }
                            )
                        }
                        items(categories) { category ->
                            FilterChip(
                                selected = filterCategory == category,
                                onClick = { filterCategory = category; page = 1 },
                                label = { Text(category) }
                            )
                        }
                    }
                } else {
                    Spacer(Modifier.height(7.dp))
                }

                error?.let {
                    Text(it, color = Color(0xFFAF354A), fontSize = 11.sp)
                    Spacer(Modifier.height(5.dp))
                }
                operationError?.let {
                    Text(it, color = Color(0xFFB14B53), fontSize = 11.sp)
                    Spacer(Modifier.height(5.dp))
                }

                Text(
                    if (ingredients) "วัตถุดิบ • จำนวนคงเหลือ • หน่วยจัดเก็บ • ต้นทุน"
                    else "สินค้า • ราคา • ขาย 30 วัน • คงเหลือ",
                    fontSize = 12.sp,
                    color = catalogMuted,
                    modifier = Modifier.padding(bottom = 7.dp)
                )

                Surface(color = Color(0xFFE5EEFB), shape = RoundedCornerShape(9.dp)) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("รายการ", Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        if (!ingredients) Text("ราคา / ขาย", Modifier.width(72.dp), fontSize = 11.sp)
                        else Text("ต้นทุน", Modifier.width(70.dp), fontSize = 11.sp)
                        Text("คงเหลือ", Modifier.width(65.dp), fontSize = 11.sp)
                    }
                }

                if (loading) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                } else if (rows.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            if (unavailable) "ระบบจัดการสินค้ายังไม่พร้อมใช้งานบนเซิร์ฟเวอร์"
                            else if (ingredients) "ยังไม่มีวัตถุดิบในสาขานี้"
                            else "ยังไม่มีสินค้าในสาขานี้",
                            color = catalogMuted
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(top = 6.dp, bottom = 8.dp)
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
                                                    if (distance < -65f && !ingredients && item.active) swipedId = item.id
                                                    else if (distance > 65f) swipedId = null
                                                }
                                            )
                                        }
                                        .clickable(enabled = editable && !saving) {
                                            editing = item
                                            operationError = null
                                        }
                                ) {
                                    Row(
                                        Modifier.fillMaxWidth().padding(11.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (!ingredients) {
                                            Box(
                                                Modifier.size(53.dp).background(Color(0xFFF0F5FD), RoundedCornerShape(13.dp)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (image != null) {
                                                    ProductArtwork(
                                                        product = DemoMenuItem(
                                                            image.id, image.name, image.category, image.price.value,
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
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    item.name,
                                                    color = catalogInk,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f, fill = false)
                                                )
                                                if (!ingredients && item.recommended) {
                                                    Text(" ★", color = Color(0xFFE76F51), fontSize = 11.sp)
                                                }
                                            }
                                            Text(
                                                if (ingredients) item.baseUnit
                                                else item.sku.ifBlank { item.category } + if (!item.active) " • หยุดขาย" else "",
                                                color = catalogMuted,
                                                fontSize = 10.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        if (!ingredients) {
                                            Column(Modifier.width(72.dp), horizontalAlignment = Alignment.End) {
                                                Text(
                                                    "฿" + item.price.bahtText(),
                                                    color = catalogBlue,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.ExtraBold
                                                )
                                                Text("ขาย " + (item.sold30d?.toString() ?: "—"), color = catalogMuted, fontSize = 10.sp)
                                            }
                                        } else {
                                            Column(Modifier.width(70.dp), horizontalAlignment = Alignment.End) {
                                                Text(
                                                    "฿" + DecimalFormat("#,##0.####").format(item.avgUnitCost),
                                                    color = catalogInk,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text("เฉลี่ย/หน่วย", color = catalogMuted, fontSize = 8.sp)
                                            }
                                        }

                                        Column(Modifier.width(65.dp), horizontalAlignment = Alignment.End) {
                                            Text(
                                                item.stock?.let(::formatCatalogQty) ?: "—",
                                                color = if (item.stock != null && item.stock <= 0.0) Color(0xFFBE4055) else catalogInk,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
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
                                            } else operationError = "ต้องใช้สิทธิ์เจ้าของหรือผู้จัดการ"
                                        },
                                        modifier = Modifier.align(Alignment.End).padding(top = 4.dp, end = 3.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB8304B))
                                    ) { Text("หยุดขาย  •  ยืนยัน PIN", fontSize = 12.sp) }
                                }
                            }
                        }

                        item {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("หน้า $page / " + (loaded?.totalPages ?: 1), color = catalogMuted, fontSize = 12.sp)
                                Row {
                                    TextButton(enabled = page > 1, onClick = { page -= 1 }) { Text("ก่อนหน้า") }
                                    TextButton(
                                        enabled = loaded != null && page < (loaded?.totalPages ?: 1),
                                        onClick = { page += 1 }
                                    ) { Text("ถัดไป") }
                                }
                            }
                        }
                    }
                }
            }

            HomeBottomMenu(
                modifier = Modifier.align(Alignment.BottomCenter),
                selectedTab = "stock",
                onMenuClick = { if (!saving) onTab(it) }
            )
        }
    }

    if (branchPickerOpen) {
        BranchPickerDialog(
            branches = branches,
            selectedBranchId = selectedBranchId,
            onSelect = {
                branchPickerOpen = false
                changeBranch(it.id)
            },
            onDismiss = { branchPickerOpen = false }
        )
    }

    if (productCreateOpen) {
        CreateProductDialog(
            client = client,
            session = session,
            branches = branches,
            initialBranchId = selectedBranchId,
            categories = categories,
            saving = saving,
            onSaving = { saving = it },
            onSuccess = { branchId ->
                productCreateOpen = false
                if (branchId != selectedBranchId) changeBranch(branchId) else reload += 1
            },
            onError = { operationError = it },
            onDismiss = { if (!saving) productCreateOpen = false }
        )
    }

    if (ingredientCreateOpen) {
        IngredientRestockDialog(
            client = client,
            session = session,
            branches = branches,
            initialBranchId = selectedBranchId,
            saving = saving,
            onSaving = { saving = it },
            onSuccess = { branchId ->
                ingredientCreateOpen = false
                ingredients = true
                if (branchId != selectedBranchId) changeBranch(branchId) else reload += 1
            },
            onError = { operationError = it },
            onDismiss = { if (!saving) ingredientCreateOpen = false }
        )
    }

    editing?.let { item ->
        EditCatalogDialog(
            ingredient = ingredients,
            item = item,
            branchName = selectedBranch.name,
            saving = saving,
            onDismiss = { if (!saving) editing = null },
            onSave = { name, skuUnit, category, price, recommended ->
                saving = true
                operationError = null
                scope.launch {
                    try {
                        client.saveCatalogRow(
                            session = session,
                            branchId = selectedBranchId,
                            ingredient = ingredients,
                            item = item,
                            name = name,
                            skuOrUnit = skuUnit,
                            category = category,
                            priceText = price,
                            recommended = recommended
                        )
                        editing = null
                        reload += 1
                    } catch (failure: Exception) {
                        operationError = "บันทึกไม่ได้: " + ((failure as? WebPosApiException)?.code ?: "โปรดตรวจสอบเครือข่าย")
                    } finally { saving = false }
                }
            }
        )
    }

    deleteTarget?.let { target ->
        NativeCancelBillPinDialog(
            title = "หยุดขายสินค้า: " + target.name,
            busy = saving,
            error = pinError,
            actionLabel = "ตรวจ PIN และหยุดขาย",
            actionNote = "สาขา " + selectedBranch.name + " • ไม่ลบประวัติการขายหรือสต๊อก",
            onDismiss = {
                if (!saving) {
                    deleteTarget = null
                    pinError = null
                }
            },
            onApprove = { pin ->
                saving = true
                pinError = null
                operationError = null
                scope.launch {
                    var sent = false
                    try {
                        val approval = client.approveCatalogProductDeactivate(session, selectedBranchId, target.id, pin)
                        sent = true
                        client.deactivateCatalogProduct(session, selectedBranchId, target, approval)
                        deleteTarget = null
                        swipedId = null
                        reload += 1
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        if (sent) {
                            deleteTarget = null
                            swipedId = null
                            reload += 1
                            operationError = "ตรวจสอบสถานะสินค้ากับเซิร์ฟเวอร์อีกครั้งก่อนดำเนินการต่อ"
                        } else pinError = "PIN ไม่ถูกต้อง หมดอายุ หรือไม่มีสิทธิ์อนุมัติ"
                    } finally { saving = false }
                }
            }
        )
    }
}

@Composable
private fun BranchPickerDialog(
    branches: List<WebCatalogBranch>,
    selectedBranchId: String,
    onSelect: (WebCatalogBranch) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("เลือกสาขา") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                branches.forEach { branch ->
                    Surface(
                        color = if (branch.id == selectedBranchId) Color(0xFFE8F2FF) else Color.White,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(branch) }
                    ) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(branch.name, fontWeight = FontWeight.Bold, color = catalogInk)
                                Text(branch.code.ifBlank { branch.role }, fontSize = 10.sp, color = catalogMuted)
                            }
                            if (branch.id == selectedBranchId) Text("✓", color = catalogBlue, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("ปิด") } }
    )
}

@Composable
private fun CatalogBranchField(
    branches: List<WebCatalogBranch>,
    branchId: String,
    enabled: Boolean,
    onChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = branches.firstOrNull { it.id == branchId }
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            enabled = enabled && branches.size > 1,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("สาขา: " + (selected?.name ?: "สาขาปัจจุบัน"))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            branches.forEach { branch ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(branch.name)
                            if (branch.code.isNotBlank()) Text(branch.code, fontSize = 10.sp, color = catalogMuted)
                        }
                    },
                    onClick = {
                        expanded = false
                        onChange(branch.id)
                    }
                )
            }
        }
    }
}

@Composable
private fun CreateProductDialog(
    client: WebPosClient,
    session: WebPosSession,
    branches: List<WebCatalogBranch>,
    initialBranchId: String,
    categories: List<String>,
    saving: Boolean,
    onSaving: (Boolean) -> Unit,
    onSuccess: (String) -> Unit,
    onError: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var branchId by remember { mutableStateOf(initialBranchId) }
    var category by remember { mutableStateOf(categories.firstOrNull().orEmpty()) }
    var name by remember { mutableStateOf("") }
    var stock by remember { mutableStateOf("0") }
    var storePrice by remember { mutableStateOf("0") }
    var recommended by remember { mutableStateOf(false) }
    var deliveryPrice by remember { mutableStateOf("0") }
    var useRecipe by remember { mutableStateOf(false) }
    var ingredientsLoading by remember { mutableStateOf(false) }
    var recipeInputs by remember { mutableStateOf<List<RecipeInput>>(emptyList()) }
    var formError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(branchId, useRecipe) {
        if (!useRecipe) return@LaunchedEffect
        ingredientsLoading = true
        try {
            recipeInputs = client.catalogIngredientsForRecipe(session, branchId).map { RecipeInput(it) }
        } catch (_: Exception) {
            recipeInputs = emptyList()
            formError = "โหลดวัตถุดิบของสาขานี้ไม่ได้"
        } finally { ingredientsLoading = false }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier.fillMaxWidth().heightIn(max = 720.dp)
        ) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(11.dp)
            ) {
                Text("เพิ่มสินค้า", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = catalogInk)
                Text("เชื่อมข้อมูลสินค้า/สต๊อกกับ CpIPOS หลัก", fontSize = 11.sp, color = catalogMuted)

                if (branches.size > 1) {
                    Text("สาขาที่เพิ่มสินค้า", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    CatalogBranchField(branches, branchId, !saving, onChange = {
                        branchId = it
                        recipeInputs = emptyList()
                    })
                }

                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it.take(80) },
                    label = { Text("1. หมวดหมู่") },
                    placeholder = { Text("เช่น เครื่องดื่ม") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !saving
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(120) },
                    label = { Text("2. ชื่อสินค้า") },
                    placeholder = { Text("เช่น ชาไทยพรีเมียม") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !saving
                )
                OutlinedTextField(
                    value = stock,
                    onValueChange = { stock = it.filter { ch -> ch.isDigit() || ch == '.' }.take(16) },
                    label = { Text("3. จำนวนสินค้า") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !saving
                )
                OutlinedTextField(
                    value = storePrice,
                    onValueChange = { storePrice = it.filter { ch -> ch.isDigit() || ch == '.' }.take(16) },
                    label = { Text("4. ราคาหน้าร้าน") },
                    prefix = { Text("฿") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !saving
                )

                Surface(color = Color(0xFFFFF1F0), shape = RoundedCornerShape(12.dp)) {
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = !saving) { recommended = !recommended }.padding(11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = recommended, onCheckedChange = { recommended = it }, enabled = !saving)
                        Column {
                            Text("5. สินค้าแนะนำ", fontWeight = FontWeight.Bold, color = Color(0xFFC94C4C))
                            Text("แสดงเป็นสินค้าแนะนำใน CpIPOS", fontSize = 10.sp, color = catalogMuted)
                        }
                    }
                }

                OutlinedTextField(
                    value = deliveryPrice,
                    onValueChange = { deliveryPrice = it.filter { ch -> ch.isDigit() || ch == '.' }.take(16) },
                    label = { Text("6. ราคาเดลิเวอรี่") },
                    prefix = { Text("฿") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !saving
                )

                Surface(
                    color = if (useRecipe) Color(0xFFEAF4FF) else Color(0xFFF7F9FC),
                    shape = RoundedCornerShape(13.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = !saving) { useRecipe = !useRecipe }.padding(11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = useRecipe, onCheckedChange = { useRecipe = it }, enabled = !saving)
                        Text(
                            "เปิดใส่วัตถุดิบ (ตั้งสูตรตัดสต๊อก)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = catalogInk
                        )
                    }
                }

                if (useRecipe) {
                    Text("วัตถุดิบ • คงเหลือ • จำนวนต่อ 1 ชิ้น • หน่วย", fontSize = 11.sp, color = catalogMuted)
                    if (ingredientsLoading) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    } else if (recipeInputs.isEmpty()) {
                        Text("สาขานี้ยังไม่มีวัตถุดิบ กรุณาเพิ่มวัตถุดิบก่อน", color = Color(0xFFB14B53), fontSize = 11.sp)
                    } else {
                        Column(
                            Modifier.fillMaxWidth().heightIn(max = 260.dp).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            recipeInputs.forEachIndexed { index, line ->
                                Surface(color = Color(0xFFF7F9FC), shape = RoundedCornerShape(12.dp)) {
                                    Column(Modifier.fillMaxWidth().padding(9.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(
                                                checked = line.selected,
                                                onCheckedChange = { checked ->
                                                    recipeInputs = recipeInputs.toMutableList().also {
                                                        it[index] = line.copy(selected = checked)
                                                    }
                                                }
                                            )
                                            Column(Modifier.weight(1f)) {
                                                Text(line.ingredient.name, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                Text(
                                                    "คงเหลือ " + (line.ingredient.stock?.let(::formatCatalogQty) ?: "—") +
                                                        " " + line.ingredient.baseUnit,
                                                    fontSize = 9.sp,
                                                    color = catalogMuted
                                                )
                                            }
                                        }
                                        if (line.selected) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                OutlinedTextField(
                                                    value = line.quantity,
                                                    onValueChange = { value ->
                                                        recipeInputs = recipeInputs.toMutableList().also {
                                                            it[index] = line.copy(
                                                                quantity = value.filter { ch -> ch.isDigit() || ch == '.' }.take(12)
                                                            )
                                                        }
                                                    },
                                                    label = { Text("จำนวนต่อ 1 ชิ้น") },
                                                    modifier = Modifier.weight(1f),
                                                    singleLine = true
                                                )
                                                RecipeUnitField(
                                                    unit = line.unit,
                                                    pieceOnly = line.ingredient.baseUnit.lowercase() in setOf("piece", "unit", "ลูก"),
                                                    onChange = { unit ->
                                                        recipeInputs = recipeInputs.toMutableList().also {
                                                            it[index] = line.copy(unit = unit)
                                                        }
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        "ไม่เปิดวัตถุดิบ: ระบบจะสร้างสต๊อกแบบชิ้นและเชื่อมจำนวนคงเหลือให้ CpIPOS",
                        fontSize = 10.sp,
                        color = catalogMuted
                    )
                }

                formError?.let { Text(it, color = Color(0xFFB14B53), fontSize = 11.sp) }
                if (saving) LinearProgressIndicator(Modifier.fillMaxWidth())

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    TextButton(enabled = !saving, onClick = onDismiss) { Text("ยกเลิก") }
                    Button(
                        enabled = !saving,
                        onClick = {
                            val stockValue = stock.toDoubleOrNull()
                            val selectedRecipes = recipeInputs.filter { it.selected }.mapNotNull { line ->
                                val qty = line.quantity.toDoubleOrNull()
                                if (qty != null && qty > 0) WebCatalogRecipeDraft(line.ingredient.id, qty, line.unit) else null
                            }
                            when {
                                category.isBlank() -> formError = "กรุณากรอกหมวดหมู่"
                                name.isBlank() -> formError = "กรุณากรอกชื่อสินค้า"
                                stockValue == null || stockValue < 0 -> formError = "จำนวนสินค้าต้องเป็น 0 หรือมากกว่า"
                                storePrice.toBigDecimalOrNull() == null -> formError = "กรุณากรอกราคาหน้าร้าน"
                                deliveryPrice.toBigDecimalOrNull() == null -> formError = "กรุณากรอกราคาเดลิเวอรี่"
                                useRecipe && selectedRecipes.isEmpty() -> formError = "กรุณาเลือกวัตถุดิบอย่างน้อย 1 รายการ"
                                else -> {
                                    formError = null
                                    onSaving(true)
                                    scope.launch {
                                        try {
                                            client.createCatalogProduct(
                                                session = session,
                                                branchId = branchId,
                                                name = name,
                                                category = category,
                                                stockQuantity = stockValue,
                                                storePriceText = storePrice,
                                                deliveryPriceText = deliveryPrice,
                                                recommended = recommended,
                                                useIngredientRecipe = useRecipe,
                                                ingredientLines = selectedRecipes
                                            )
                                            onSuccess(branchId)
                                        } catch (failure: Exception) {
                                            val code = (failure as? WebPosApiException)?.code ?: failure.message ?: "network_error"
                                            formError = "เพิ่มสินค้าไม่สำเร็จ: $code"
                                            onError(formError.orEmpty())
                                        } finally { onSaving(false) }
                                    }
                                }
                            }
                        }
                    ) { Text("บันทึกสินค้า") }
                }
            }
        }
    }
}

@Composable
private fun RecipeUnitField(unit: String, pieceOnly: Boolean, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val options = if (pieceOnly) listOf("piece" to "ชิ้น") else listOf(
        "gram" to "กรัม", "kg" to "กก.", "khid" to "ขีด"
    )
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.width(92.dp)) {
            Text(options.firstOrNull { it.first == unit }?.second ?: unit, fontSize = 10.sp)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.second) },
                    onClick = {
                        expanded = false
                        onChange(option.first)
                    }
                )
            }
        }
    }
}

@Composable
private fun IngredientRestockDialog(
    client: WebPosClient,
    session: WebPosSession,
    branches: List<WebCatalogBranch>,
    initialBranchId: String,
    saving: Boolean,
    onSaving: (Boolean) -> Unit,
    onSuccess: (String) -> Unit,
    onError: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var branchId by remember { mutableStateOf(initialBranchId) }
    var existing by remember { mutableStateOf<List<WebCatalogRow>>(emptyList()) }
    var selectedIngredientId by remember { mutableStateOf<String?>(null) }
    var ingredientName by remember { mutableStateOf("") }
    var purchaseQuantity by remember { mutableStateOf("0") }
    var purchaseUnit by remember { mutableStateOf("gram") }
    var bagWeight by remember { mutableStateOf("0") }
    var totalCost by remember { mutableStateOf("0") }
    var reason by remember { mutableStateOf("เติมสต๊อกจากการรับเข้า") }
    var formError by remember { mutableStateOf<String?>(null) }
    var ingredientPicker by remember { mutableStateOf(false) }

    LaunchedEffect(branchId) {
        selectedIngredientId = null
        ingredientName = ""
        try {
            existing = client.catalogIngredientsForRecipe(session, branchId)
        } catch (_: Exception) {
            existing = emptyList()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier.fillMaxWidth().heightIn(max = 680.dp)
        ) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(11.dp)
            ) {
                Text("เพิ่มวัตถุดิบ / รับเข้า", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = catalogInk)
                Text("หัวข้อเดียวกับ CpIPOS หลัก", fontSize = 11.sp, color = catalogMuted)

                if (branches.size > 1) {
                    Text("สาขาที่รับเข้า", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    CatalogBranchField(branches, branchId, !saving, onChange = { branchId = it })
                }

                OutlinedTextField(
                    value = ingredientName,
                    onValueChange = {
                        ingredientName = it.take(120)
                        selectedIngredientId = existing.firstOrNull { row -> row.name.equals(ingredientName.trim(), true) }?.id
                    },
                    label = { Text("วัตถุดิบ") },
                    placeholder = { Text("เช่น กะหล่ำปลี / ถ้วยน้ำจิ้ม") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !saving,
                    trailingIcon = {
                        if (existing.isNotEmpty()) {
                            TextButton(onClick = { ingredientPicker = true }) { Text("เลือก") }
                        }
                    }
                )
                OutlinedTextField(
                    value = purchaseQuantity,
                    onValueChange = { purchaseQuantity = it.filter { ch -> ch.isDigit() || ch == '.' }.take(16) },
                    label = { Text("จำนวนรับเข้า") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !saving
                )

                IngredientPurchaseUnitField(unit = purchaseUnit, enabled = !saving, onChange = { purchaseUnit = it })

                if (purchaseUnit == "bag") {
                    OutlinedTextField(
                        value = bagWeight,
                        onValueChange = { bagWeight = it.filter { ch -> ch.isDigit() || ch == '.' }.take(16) },
                        label = { Text("น้ำหนักต่อถุง (กรัม)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !saving
                    )
                }

                OutlinedTextField(
                    value = totalCost,
                    onValueChange = { totalCost = it.filter { ch -> ch.isDigit() || ch == '.' }.take(16) },
                    label = { Text("ต้นทุนรวม (บาท)") },
                    prefix = { Text("฿") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !saving
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it.take(300) },
                    label = { Text("หมายเหตุ") },
                    placeholder = { Text("เช่น รับเข้าจากซัพพลายเออร์") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !saving
                )

                formError?.let { Text(it, color = Color(0xFFB14B53), fontSize = 11.sp) }
                if (saving) LinearProgressIndicator(Modifier.fillMaxWidth())

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    TextButton(enabled = !saving, onClick = onDismiss) { Text("ยกเลิก") }
                    Button(
                        enabled = !saving,
                        onClick = {
                            val qty = purchaseQuantity.toDoubleOrNull()
                            val cost = totalCost.toBigDecimalOrNull()
                            val weight = bagWeight.toDoubleOrNull()
                            when {
                                ingredientName.isBlank() -> formError = "กรุณากรอกชื่อวัตถุดิบ"
                                qty == null || qty <= 0 -> formError = "จำนวนรับเข้าต้องมากกว่า 0"
                                purchaseUnit == "bag" && (weight == null || weight <= 0) -> formError = "กรุณากรอกน้ำหนักต่อถุง"
                                cost == null || cost < java.math.BigDecimal.ZERO -> formError = "ต้นทุนรวมต้องเป็น 0 หรือมากกว่า"
                                else -> {
                                    formError = null
                                    onSaving(true)
                                    scope.launch {
                                        try {
                                            client.restockCatalogIngredient(
                                                session = session,
                                                branchId = branchId,
                                                ingredientId = selectedIngredientId,
                                                ingredientName = ingredientName,
                                                purchaseQuantity = qty,
                                                purchaseUnit = purchaseUnit,
                                                weightPerBagGrams = if (purchaseUnit == "bag") weight else null,
                                                receivedTotalCostText = totalCost,
                                                reason = reason
                                            )
                                            onSuccess(branchId)
                                        } catch (failure: Exception) {
                                            val code = (failure as? WebPosApiException)?.code ?: failure.message ?: "network_error"
                                            formError = "บันทึกวัตถุดิบไม่สำเร็จ: $code"
                                            onError(formError.orEmpty())
                                        } finally { onSaving(false) }
                                    }
                                }
                            }
                        }
                    ) { Text("บันทึกการรับเข้า") }
                }
            }
        }
    }

    if (ingredientPicker) {
        AlertDialog(
            onDismissRequest = { ingredientPicker = false },
            title = { Text("เลือกวัตถุดิบเดิม") },
            text = {
                Column(
                    Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    existing.forEach { row ->
                        Surface(
                            color = if (row.id == selectedIngredientId) Color(0xFFE8F2FF) else Color.White,
                            shape = RoundedCornerShape(11.dp),
                            modifier = Modifier.fillMaxWidth().clickable {
                                selectedIngredientId = row.id
                                ingredientName = row.name
                                purchaseUnit = if (row.baseUnit.lowercase() in setOf("piece", "unit", "ลูก")) "piece" else "gram"
                                ingredientPicker = false
                            }
                        ) {
                            Row(Modifier.fillMaxWidth().padding(10.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text(row.name, fontWeight = FontWeight.Bold)
                                    Text(
                                        "คงเหลือ " + (row.stock?.let(::formatCatalogQty) ?: "—") + " " + row.baseUnit,
                                        fontSize = 9.sp,
                                        color = catalogMuted
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { ingredientPicker = false }) { Text("ปิด") } }
        )
    }
}

@Composable
private fun IngredientPurchaseUnitField(unit: String, enabled: Boolean, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf(
        "gram" to "กรัม", "kg" to "กิโลกรัม", "khid" to "ขีด", "bag" to "ถุง", "piece" to "ลูก/ชิ้น"
    )
    Column {
        Text("หน่วยรับเข้า", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = catalogInk)
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth()
            ) { Text(options.firstOrNull { it.first == unit }?.second ?: unit) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.second) },
                        onClick = {
                            expanded = false
                            onChange(option.first)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun EditCatalogDialog(
    ingredient: Boolean,
    item: WebCatalogRow,
    branchName: String,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, Boolean) -> Unit
) {
    var name by remember(item.id) { mutableStateOf(item.name) }
    var skuUnit by remember(item.id) { mutableStateOf(if (ingredient) item.baseUnit else item.sku) }
    var category by remember(item.id) { mutableStateOf(item.category) }
    var price by remember(item.id) { mutableStateOf(if (ingredient) "" else item.price.bahtText()) }
    var recommended by remember(item.id) { mutableStateOf(item.recommended) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (ingredient) "แก้ไขวัตถุดิบ" else "แก้ไขสินค้า") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("สาขา $branchName", fontSize = 10.sp, color = catalogMuted)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(120) },
                    label = { Text(if (ingredient) "ชื่อวัตถุดิบ" else "ชื่อสินค้า") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = skuUnit,
                    onValueChange = { skuUnit = it.take(60) },
                    label = { Text(if (ingredient) "หน่วยหลัก" else "SKU") },
                    readOnly = ingredient,
                    singleLine = true
                )
                if (!ingredient) {
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it.take(80) },
                        label = { Text("หมวดหมู่") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = price,
                        onValueChange = { price = it.take(20) },
                        label = { Text("ราคาหน้าร้าน") },
                        prefix = { Text("฿") },
                        singleLine = true
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = recommended, onCheckedChange = { recommended = it })
                        Text("สินค้าแนะนำ", fontSize = 12.sp)
                    }
                }
                Text(
                    "คงเหลือ: " + (item.stock?.let(::formatCatalogQty) ?: "—") +
                        if (ingredient) " " + item.baseUnit else " ชิ้น",
                    fontSize = 11.sp,
                    color = catalogMuted
                )
                Text(
                    if (ingredient)
                        "การเพิ่มจำนวนคงเหลือต้องทำผ่าน “เพิ่มวัตถุดิบ / รับเข้า” เพื่อบันทึก movement"
                    else
                        "จำนวนคงเหลือใช้สูตร/สต๊อกเดียวกับ CpIPOS และไม่เขียนทับจากหน้าปรับชื่อสินค้า",
                    fontSize = 10.sp,
                    color = catalogMuted
                )
                if (saving) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                enabled = !saving && name.isNotBlank() && skuUnit.isNotBlank() &&
                    (ingredient || (category.isNotBlank() && price.toBigDecimalOrNull() != null)),
                onClick = { onSave(name, skuUnit, category, price, recommended) }
            ) { Text("บันทึก") }
        },
        dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text("ยกเลิก") } }
    )
}

private fun formatCatalogQty(value: Double): String =
    DecimalFormat("#,##0.###", java.text.DecimalFormatSymbols(Locale.US)).format(value)
