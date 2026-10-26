package com.cpipos.pos.next.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.cpipos.pos.next.core.pos.Satang
import com.cpipos.pos.next.core.webpos.*
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val paySettingsInk = Color(0xFF172B4E)
private val paySettingsBlue = Color(0xFF176DED)
private val paySettingsMuted = Color(0xFF71849B)

@Composable
internal fun NativePaymentSettingsScreen(
    client: WebPosClient,
    session: WebPosSession,
    onBack: () -> Unit,
    onTab: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var snapshot by remember(session.id) { mutableStateOf<WebPaymentSettingsSnapshot?>(null) }
    var loading by remember(session.id) { mutableStateOf(true) }
    var saving by remember(session.id) { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<WebPaymentSettingsAccount?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<WebPaymentSettingsAccount?>(null) }
    var pinError by remember { mutableStateOf<String?>(null) }

    suspend fun reload() {
        loading = true
        try {
            snapshot = client.paymentSettings(session)
            error = null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = when (failure) {
                is WebPosApiException -> when (failure.status) {
                    401, 403 -> "บัญชีนี้ไม่มีสิทธิ์ดูการตั้งค่าชำระเงิน"
                    else -> "โหลดการตั้งค่าชำระเงินไม่สำเร็จ (" + failure.code + ")"
                }
                else -> "โหลดการตั้งค่าชำระเงินไม่สำเร็จ กรุณาตรวจสอบเครือข่าย"
            }
        } finally {
            loading = false
        }
    }

    LaunchedEffect(client, session.id) { reload() }

    val data = snapshot
    val canManage = data?.canManage == true
    val branches = data?.branches.orEmpty().filter { it.active }
    val branchNames = data?.branches.orEmpty().associateBy { it.id }

    Surface(Modifier.fillMaxSize(), color = Color(0xFFF5F9FF)) {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().statusBarsPadding()
                    .padding(start = 15.dp, end = 15.dp, top = 8.dp, bottom = 112.dp),
                verticalArrangement = Arrangement.spacedBy(11.dp)
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onBack) { Text("‹ ตั้งค่า") }
                        Column(Modifier.weight(1f)) {
                            Text(
                                "ตั้งค่าชำระเงิน",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = paySettingsInk
                            )
                            Text(
                                "บัญชีรับโอน / PromptPay / QR ชุดเดียวกับ CpIPOS",
                                fontSize = 11.sp,
                                color = paySettingsMuted
                            )
                        }
                    }
                }

                item {
                    Surface(color = Color(0xFFEAF3FF), shape = RoundedCornerShape(16.dp)) {
                        Column(
                            Modifier.fillMaxWidth().padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                "เชื่อมกับระบบชำระเงินแบบโอน",
                                fontWeight = FontWeight.ExtraBold,
                                color = paySettingsInk
                            )
                            Text(
                                "หน้าชำระเงินมือถือจะอ่านบัญชีที่ Active ของสาขานี้จาก CpIPOS โดยตรง " +
                                    "และใช้ QR ที่ตั้งไว้ เมื่อพนักงานตรวจยอดเข้าแล้วจึงกดยืนยันรับเงินโอน",
                                fontSize = 11.sp,
                                color = paySettingsMuted
                            )
                        }
                    }
                }

                if (loading) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = paySettingsBlue)
                        }
                    }
                } else {
                    if (data?.ready == false) {
                        item {
                            Text(
                                "ตารางบัญชีรับชำระยังไม่พร้อมบน Server",
                                color = Color(0xFFB33A49),
                                fontSize = 12.sp
                            )
                        }
                    }

                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "บัญชีรับชำระ",
                                    fontWeight = FontWeight.ExtraBold,
                                    color = paySettingsInk
                                )
                                Text(
                                    (data?.accounts?.size ?: 0).toString() + " รายการ",
                                    fontSize = 11.sp,
                                    color = paySettingsMuted
                                )
                            }
                            TextButton(onClick = { scope.launch { reload() } }, enabled = !saving) {
                                Text("↻ รีเฟรช")
                            }
                        }
                    }

                    if (canManage) {
                        item {
                            Button(
                                onClick = {
                                    creating = true
                                    editing = null
                                    error = null
                                },
                                enabled = !saving && branches.isNotEmpty(),
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(15.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = paySettingsBlue)
                            ) {
                                Text("+ เพิ่มบัญชีรับชำระ", fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        item {
                            Text(
                                "ดูได้อย่างเดียว · การเพิ่ม/แก้ไขบัญชีรับชำระอนุญาตเฉพาะ Owner",
                                fontSize = 11.sp,
                                color = paySettingsMuted
                            )
                        }
                    }

                    val accounts = data?.accounts.orEmpty()
                    if (accounts.isEmpty()) {
                        item {
                            Surface(color = Color.White, shape = RoundedCornerShape(16.dp)) {
                                Text(
                                    "ยังไม่ได้ตั้งบัญชีรับโอนสำหรับ POS",
                                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                                    color = paySettingsMuted,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    } else {
                        items(accounts, key = { it.id }) { account ->
                            Surface(
                                color = Color.White,
                                shape = RoundedCornerShape(17.dp),
                                shadowElevation = 1.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    Modifier.fillMaxWidth().padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(7.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                account.bankName.ifBlank { "บัญชีรับชำระ" },
                                                fontWeight = FontWeight.ExtraBold,
                                                color = paySettingsInk,
                                                fontSize = 15.sp
                                            )
                                            Text(
                                                account.accountName,
                                                color = paySettingsMuted,
                                                fontSize = 11.sp
                                            )
                                        }
                                        Surface(
                                            color = if (account.active) Color(0xFFE7F8EF) else Color(0xFFF0F2F5),
                                            shape = RoundedCornerShape(50)
                                        ) {
                                            Text(
                                                if (account.active) "ใช้งาน" else "ปิดใช้งาน",
                                                color = if (account.active) Color(0xFF168452) else Color(0xFF68778A),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        "เลขบัญชี " + account.accountNumber.ifBlank { "—" },
                                        color = paySettingsInk,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        "PromptPay " + account.promptPayPhone.ifBlank { "—" } +
                                            " · QR " + if (account.qrMode == "qr_image") "รูปภาพ" else "สร้างตามยอด",
                                        color = paySettingsMuted,
                                        fontSize = 11.sp
                                    )
                                    Text(
                                        if (account.appliesToAllBranches) "ใช้ทุกสาขา"
                                        else "สาขา " + (branchNames[account.branchId]?.name ?: account.branchId),
                                        color = paySettingsBlue,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )

                                    if (canManage) {
                                        Row(
                                            Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                                        ) {
                                            OutlinedButton(
                                                onClick = { editing = account; creating = false },
                                                enabled = !saving,
                                                modifier = Modifier.weight(1f)
                                            ) { Text("แก้ไข", fontSize = 11.sp) }
                                            OutlinedButton(
                                                onClick = {
                                                    saving = true
                                                    error = null
                                                    scope.launch {
                                                        try {
                                                            client.savePaymentSettingsAccount(
                                                                session,
                                                                account.copy(active = !account.active)
                                                            )
                                                            reload()
                                                            notice = if (account.active)
                                                                "ปิดการใช้งานบัญชีแล้ว"
                                                            else "เปิดใช้งานบัญชีแล้ว · หน้ารับโอนจะใช้บัญชีนี้"
                                                        } catch (failure: Exception) {
                                                            error = paymentSettingsError(failure)
                                                        } finally {
                                                            saving = false
                                                        }
                                                    }
                                                },
                                                enabled = !saving,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text(
                                                    if (account.active) "ปิดใช้" else "เปิดใช้",
                                                    fontSize = 11.sp
                                                )
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    pinError = null
                                                    deleteTarget = account
                                                },
                                                enabled = !saving
                                            ) { Text("ลบ", color = Color(0xFFB33A49), fontSize = 11.sp) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (notice.isNotBlank()) {
                    item {
                        Surface(color = Color(0xFFEAF8EF), shape = RoundedCornerShape(13.dp)) {
                            Text(
                                notice,
                                Modifier.fillMaxWidth().padding(12.dp),
                                color = Color(0xFF177A48),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
                error?.let { message ->
                    item {
                        Surface(color = Color(0xFFFFEEEE), shape = RoundedCornerShape(13.dp)) {
                            Text(
                                message,
                                Modifier.fillMaxWidth().padding(12.dp),
                                color = Color(0xFFB13F4C),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }

            HomeBottomMenu(
                modifier = Modifier.align(Alignment.BottomCenter),
                selectedTab = "settings",
                onMenuClick = onTab
            )
        }
    }

    if ((creating || editing != null) && branches.isNotEmpty()) {
        PaymentAccountFormDialog(
            client = client,
            session = session,
            initial = editing,
            branches = branches,
            currentBranchId = session.branchId,
            busy = saving,
            onDismiss = {
                if (!saving) {
                    creating = false
                    editing = null
                }
            },
            onSave = { account ->
                saving = true
                error = null
                scope.launch {
                    try {
                        client.savePaymentSettingsAccount(session, account)
                        creating = false
                        editing = null
                        reload()
                        notice = "บันทึกแล้ว · CpIPOS และหน้าชำระเงินแบบโอนใช้ข้อมูลชุดเดียวกัน"
                    } catch (failure: Exception) {
                        error = paymentSettingsError(failure)
                    } finally {
                        saving = false
                    }
                }
            }
        )
    }

    deleteTarget?.let { account ->
        NativeCancelBillPinDialog(
            title = "ยืนยัน PIN เพื่อลบบัญชีรับชำระ",
            busy = saving,
            error = pinError,
            actionLabel = "ตรวจ PIN และลบ",
            actionNote = account.bankName + " · " + account.accountName,
            onDismiss = {
                if (!saving) {
                    deleteTarget = null
                    pinError = null
                }
            },
            onApprove = { pin ->
                saving = true
                pinError = null
                scope.launch {
                    try {
                        client.deletePaymentSettingsAccount(session, account.id, pin)
                        deleteTarget = null
                        reload()
                        notice = "ลบบัญชีรับชำระแล้ว"
                    } catch (failure: Exception) {
                        val api = failure as? WebPosApiException
                        pinError = when (api?.code) {
                            "pin_rejected", "payment_account_approval_required" ->
                                "PIN ไม่ถูกต้องหรือไม่มีสิทธิ์อนุมัติ"
                            else -> paymentSettingsError(failure)
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
private fun PaymentAccountFormDialog(
    client: WebPosClient,
    session: WebPosSession,
    initial: WebPaymentSettingsAccount?,
    branches: List<WebPaymentSettingsBranch>,
    currentBranchId: String,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (WebPaymentSettingsAccount) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var branchId by remember(initial?.id) {
        mutableStateOf(initial?.branchId ?: currentBranchId.takeIf { id -> branches.any { it.id == id } }
            ?: branches.first().id)
    }
    var bankName by remember(initial?.id) { mutableStateOf(initial?.bankName.orEmpty()) }
    var accountName by remember(initial?.id) { mutableStateOf(initial?.accountName.orEmpty()) }
    var accountNumber by remember(initial?.id) { mutableStateOf(initial?.accountNumber.orEmpty()) }
    var promptPay by remember(initial?.id) { mutableStateOf(initial?.promptPayPhone.orEmpty()) }
    var qrMode by remember(initial?.id) { mutableStateOf(initial?.qrMode ?: "promptpay_link") }
    var qrImageUrl by remember(initial?.id) { mutableStateOf(initial?.qrImageUrl.orEmpty()) }
    var allBranches by remember(initial?.id) { mutableStateOf(initial?.appliesToAllBranches ?: false) }
    var active by remember(initial?.id) { mutableStateOf(initial?.active ?: true) }
    var branchMenu by remember { mutableStateOf(false) }
    var qrBusy by remember { mutableStateOf(false) }
    var formError by remember { mutableStateOf<String?>(null) }
    var qrPreview by remember(initial?.id) { mutableStateOf(decodePaymentQrData(qrImageUrl)) }
    var promptPayPreview by remember(initial?.id) { mutableStateOf<Bitmap?>(null) }
    var promptPayPreviewBusy by remember(initial?.id) { mutableStateOf(false) }
    var promptPayPreviewError by remember(initial?.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(client, session.id, qrMode, promptPay) {
        promptPayPreview = null
        promptPayPreviewError = null
        if (qrMode != "promptpay_link") {
            promptPayPreviewBusy = false
            return@LaunchedEffect
        }
        val digits = promptPay.filter(Char::isDigit)
        if (digits.length !in 9..15) {
            promptPayPreviewBusy = false
            if (digits.isNotEmpty()) promptPayPreviewError = "กรอกเบอร์ PromptPay ให้ครบก่อนสร้าง QR"
            return@LaunchedEffect
        }
        promptPayPreviewBusy = true
        try {
            kotlinx.coroutines.delay(350L)
            val bytes = client.promptPayQrImage(digits, Satang.of(10_000L))
            promptPayPreview = withContext(Dispatchers.Default) {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } ?: throw IllegalStateException("promptpay_preview_decode_failed")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            val api = failure as? WebPosApiException
            promptPayPreviewError = when (api?.code) {
                "payment_provider_currency_unsupported" ->
                    "PromptPay ตามยอดรองรับเฉพาะร้านที่ใช้สกุลเงินบาท (THB)"
                "promptpay_qr_upstream_failed",
                "promptpay_qr_upstream_not_image",
                "promptpay_qr_image_size_invalid",
                "promptpay_qr_proxy_failed",
                "promptpay_qr_unavailable" ->
                    "สร้าง QR PromptPay ไม่สำเร็จ กรุณาตรวจสอบอินเทอร์เน็ตแล้วลองใหม่"
                else -> "สร้าง QR PromptPay ตัวอย่างไม่สำเร็จ"
            }
        } finally {
            promptPayPreviewBusy = false
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            qrBusy = true
            formError = null
            scope.launch {
                try {
                    val encoded = resizePaymentQr(context, uri)
                    qrImageUrl = encoded
                    qrPreview = decodePaymentQrData(encoded)
                    qrMode = "qr_image"
                } catch (failure: Exception) {
                    formError = failure.message ?: "อ่านรูป QR ไม่สำเร็จ"
                } finally {
                    qrBusy = false
                }
            }
        }
    }

    Dialog(onDismissRequest = { if (!busy && !qrBusy) onDismiss() }) {
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier.fillMaxWidth().heightIn(max = 730.dp)
        ) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(11.dp)
            ) {
                Text(
                    if (initial == null) "เพิ่มบัญชีรับชำระ" else "แก้ไขบัญชีรับชำระ",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = paySettingsInk
                )

                Text("สาขา", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Box {
                    OutlinedButton(
                        onClick = { branchMenu = true },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(branches.firstOrNull { it.id == branchId }?.name ?: "เลือกสาขา")
                    }
                    DropdownMenu(expanded = branchMenu, onDismissRequest = { branchMenu = false }) {
                        branches.forEach { branch ->
                            DropdownMenuItem(
                                text = { Text(branch.name) },
                                onClick = {
                                    branchId = branch.id
                                    branchMenu = false
                                }
                            )
                        }
                    }
                }

                Row(
                    Modifier.fillMaxWidth().clickable(enabled = !busy) { allBranches = !allBranches },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = allBranches,
                        onCheckedChange = { allBranches = it },
                        enabled = !busy
                    )
                    Column {
                        Text("ใช้บัญชีนี้ทุกสาขา", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text("ถ้าเปิด ระบบรับโอนทุกสาขาจะใช้บัญชี Active นี้", fontSize = 10.sp, color = paySettingsMuted)
                    }
                }

                OutlinedTextField(
                    value = bankName,
                    onValueChange = { bankName = it.take(100) },
                    label = { Text("ธนาคาร") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !busy
                )
                OutlinedTextField(
                    value = accountName,
                    onValueChange = { accountName = it.take(120) },
                    label = { Text("ชื่อบัญชี") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !busy
                )
                OutlinedTextField(
                    value = accountNumber,
                    onValueChange = { accountNumber = it.take(60) },
                    label = { Text("เลขบัญชี") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !busy
                )

                Text("รูปแบบ QR", fontWeight = FontWeight.Bold, color = paySettingsInk)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = qrMode == "promptpay_link",
                        onClick = { qrMode = "promptpay_link" },
                        label = { Text("PromptPay ตามยอด") }
                    )
                    FilterChip(
                        selected = qrMode == "qr_image",
                        onClick = { qrMode = "qr_image" },
                        label = { Text("รูป QR") }
                    )
                }

                OutlinedTextField(
                    value = promptPay,
                    onValueChange = { promptPay = it.filter(Char::isDigit).take(15) },
                    label = { Text(if (qrMode == "promptpay_link") "เบอร์ PromptPay *" else "เบอร์ PromptPay") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !busy
                )

                if (qrMode == "promptpay_link") {
                    Surface(
                        color = Color(0xFFF7FAFE),
                        shape = RoundedCornerShape(15.dp),
                        modifier = Modifier.fillMaxWidth().height(220.dp)
                    ) {
                        Column(
                            Modifier.fillMaxSize().padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterVertically)
                        ) {
                            Text(
                                "ตัวอย่าง PromptPay QR · 100.00 บาท",
                                fontWeight = FontWeight.Bold,
                                color = paySettingsInk,
                                fontSize = 12.sp
                            )
                            Box(
                                modifier = Modifier.size(160.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                when {
                                    promptPayPreviewBusy -> CircularProgressIndicator(color = paySettingsBlue)
                                    promptPayPreview != null -> Image(
                                        bitmap = requireNotNull(promptPayPreview).asImageBitmap(),
                                        contentDescription = "ตัวอย่าง PromptPay QR 100 บาท",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Fit
                                    )
                                    else -> Text(
                                        promptPayPreviewError ?: "กรอกเบอร์ PromptPay เพื่อสร้าง QR ตัวอย่าง",
                                        color = if (promptPayPreviewError != null) Color(0xFFB13F4C) else paySettingsMuted,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                    Text(
                        "ตัวอย่างนี้ใช้ยอด 100 บาทเหมือน CpIPOS Web · ตอนขายจริงระบบจะสร้าง QR ใหม่ตามยอดบิลจริง",
                        fontSize = 10.sp,
                        color = paySettingsMuted
                    )
                } else {
                    Surface(
                        color = Color(0xFFF7FAFE),
                        shape = RoundedCornerShape(15.dp),
                        modifier = Modifier.fillMaxWidth().height(180.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            val preview = qrPreview
                            if (preview != null) {
                                Image(
                                    bitmap = preview.asImageBitmap(),
                                    contentDescription = "QR รับชำระ",
                                    modifier = Modifier.fillMaxSize().padding(12.dp),
                                    contentScale = ContentScale.Fit
                                )
                            } else {
                                Text(
                                    if (qrImageUrl.isNotBlank()) "มีรูป QR จาก CpIPOS แล้ว"
                                    else "ยังไม่ได้เลือกรูป QR",
                                    color = paySettingsMuted,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { picker.launch("image/*") },
                            enabled = !busy && !qrBusy && !promptPayPreviewBusy,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = paySettingsBlue)
                        ) { Text(if (qrBusy) "กำลังย่อรูป..." else "เลือกรูป QR") }
                        OutlinedButton(
                            onClick = {
                                qrImageUrl = ""
                                qrPreview = null
                            },
                            enabled = !busy && !qrBusy && qrImageUrl.isNotBlank(),
                            modifier = Modifier.weight(1f)
                        ) { Text("เอารูปออก") }
                    }
                }

                Row(
                    Modifier.fillMaxWidth().clickable(enabled = !busy) { active = !active },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = active, onCheckedChange = { active = it }, enabled = !busy)
                    Text("เปิดใช้งานบัญชีนี้", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                Text(
                    if (qrMode == "promptpay_link")
                        "PromptPay จะสร้าง QR ใหม่ตามยอดบิลจริงทุกครั้ง"
                    else "รูป QR นี้จะแสดงในหน้าชำระเงินแบบโอนตามที่ตั้งใน CpIPOS",
                    fontSize = 10.sp,
                    color = paySettingsMuted
                )

                formError?.let { Text(it, color = Color(0xFFB13F4C), fontSize = 11.sp) }
                if (busy || qrBusy || promptPayPreviewBusy) LinearProgressIndicator(Modifier.fillMaxWidth())

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    TextButton(enabled = !busy && !qrBusy, onClick = onDismiss) { Text("ยกเลิก") }
                    Button(
                        enabled = !busy && !qrBusy,
                        onClick = {
                            val digits = promptPay.filter(Char::isDigit)
                            when {
                                bankName.trim().isBlank() -> formError = "กรุณาระบุธนาคาร"
                                accountName.trim().isBlank() -> formError = "กรุณาระบุชื่อบัญชี"
                                qrMode == "promptpay_link" && digits.length !in 9..15 ->
                                    formError = "กรุณาระบุเบอร์ PromptPay ให้ถูกต้อง"
                                qrMode == "qr_image" && qrImageUrl.isBlank() ->
                                    formError = "กรุณาเลือกรูป QR"
                                else -> {
                                    formError = null
                                    onSave(
                                        WebPaymentSettingsAccount(
                                            id = initial?.id.orEmpty(),
                                            branchId = branchId,
                                            bankName = bankName.trim(),
                                            accountName = accountName.trim(),
                                            accountNumber = accountNumber.trim(),
                                            promptPayPhone = digits,
                                            qrImageUrl = qrImageUrl,
                                            qrMode = qrMode,
                                            appliesToAllBranches = allBranches,
                                            active = active
                                        )
                                    )
                                }
                            }
                        }
                    ) { Text("บันทึก") }
                }
            }
        }
    }
}

private fun paymentSettingsError(failure: Exception): String {
    val api = failure as? WebPosApiException
    return when {
        api?.status == 403 -> "การแก้การตั้งค่าชำระเงินอนุญาตเฉพาะ Owner"
        api?.code == "payment_account_active_duplicate" ->
            "มีบัญชี Active ในขอบเขตนี้อยู่แล้ว กรุณารีเฟรชและตรวจสอบอีกครั้ง"
        api?.status == 422 -> "ข้อมูลบัญชีหรือ QR ไม่ถูกต้อง กรุณาตรวจสอบอีกครั้ง"
        api != null -> "บันทึกการตั้งค่าชำระเงินไม่สำเร็จ (" + api.code + ")"
        else -> "บันทึกการตั้งค่าชำระเงินไม่สำเร็จ: " + (failure.message ?: "network error")
    }
}

private fun decodePaymentQrData(value: String): Bitmap? {
    if (!value.startsWith("data:image/")) return null
    val comma = value.indexOf(',')
    if (comma <= 0) return null
    return runCatching {
        val bytes = Base64.decode(value.substring(comma + 1), Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()
}

private suspend fun resizePaymentQr(
    context: android.content.Context,
    uri: Uri
): String = withContext(Dispatchers.IO) {
    val source = context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it)
    } ?: throw IllegalArgumentException("ไม่สามารถอ่านรูป QR ได้")

    var current = source
    try {
        val maxSize = 520f
        val ratio = minOf(maxSize / source.width.coerceAtLeast(1), maxSize / source.height.coerceAtLeast(1), 1f)
        val width = (source.width * ratio).toInt().coerceAtLeast(1)
        val height = (source.height * ratio).toInt().coerceAtLeast(1)
        if (width != source.width || height != source.height) {
            current = Bitmap.createScaledBitmap(source, width, height, true)
        }

        var quality = 90
        var bytes: ByteArray
        do {
            val output = ByteArrayOutputStream()
            current.compress(Bitmap.CompressFormat.WEBP, quality, output)
            bytes = output.toByteArray()
            quality -= 8
        } while (bytes.size > 220_000 && quality >= 50)

        if (bytes.size > 240_000) {
            throw IllegalArgumentException("รูป QR มีขนาดใหญ่เกินไป กรุณาเลือกรูปที่เล็กลง")
        }
        "data:image/webp;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
    } finally {
        if (current !== source) current.recycle()
        source.recycle()
    }
}
