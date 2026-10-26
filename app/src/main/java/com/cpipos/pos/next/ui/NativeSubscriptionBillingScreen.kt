package com.cpipos.pos.next.ui

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cpipos.pos.next.core.webpos.*
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val billingBlue = Color(0xFF176DED)
private val billingInk = Color(0xFF172B4E)
private val billingMuted = Color(0xFF71849B)
private val billingMoney = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.US))

@Composable
internal fun NativeSubscriptionBillingScreen(
    client: WebPosClient,
    session: WebPosSession,
    onBack: () -> Unit,
    onTab: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var snapshot by remember(session.id) { mutableStateOf<WebSubscriptionOverview?>(null) }
    var loading by remember(session.id) { mutableStateOf(true) }
    var busy by remember(session.id) { mutableStateOf(false) }
    var refresh by remember(session.id) { mutableIntStateOf(0) }
    var error by remember(session.id) { mutableStateOf<String?>(null) }
    var notice by remember(session.id) { mutableStateOf<String?>(null) }
    var selectedPackageId by remember(session.id) { mutableStateOf("") }
    var interval by remember(session.id) { mutableStateOf("monthly") }
    var slip by remember(session.id) { mutableStateOf<WebSubscriptionSlip?>(null) }

    val slipPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            error = null
            try {
                val picked = withContext(Dispatchers.IO) {
                    val resolver = context.contentResolver
                    val mime = resolver.getType(uri).orEmpty()
                    require(mime in setOf("image/jpeg", "image/png", "image/webp")) {
                        "รองรับเฉพาะ JPG, PNG หรือ WebP"
                    }
                    var name = "subscription-slip"
                    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            name = cursor.getString(0)?.take(120).orEmpty().ifBlank { name }
                        }
                    }
                    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("อ่านไฟล์สลิปไม่ได้")
                    require(bytes.isNotEmpty() && bytes.size <= 4 * 1024 * 1024) {
                        "สลิปต้องมีขนาดไม่เกิน 4 MB"
                    }
                    WebSubscriptionSlip(name, mime, bytes)
                }
                slip = picked
                notice = nativeBillingText("เลือกสลิปแล้ว: " + picked.fileName, "Selected slip: " + picked.fileName, "ເລືອກສະລິບແລ້ວ: " + picked.fileName)
            } catch (failure: Exception) {
                slip = null
                error = failure.message ?: "เลือกสลิปไม่สำเร็จ"
            }
        }
    }

    LaunchedEffect(client, session.id, refresh) {
        loading = true
        try {
            val loaded = client.subscriptionOverview(session)
            snapshot = loaded
            val pending = loaded.requests.firstOrNull { it.status in setOf("pending", "under_review") }
            if (selectedPackageId.isBlank()) {
                selectedPackageId = pending?.packageId ?: loaded.packageId ?: loaded.packages.firstOrNull()?.id.orEmpty()
            }
            if (pending != null) interval = pending.billingInterval
            else if (loaded.billingInterval in setOf("monthly", "yearly")) interval = loaded.billingInterval
            error = null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            val api = failure as? WebPosApiException
            error = when (api?.status) {
                403 -> nativeBillingText(
                    "เมนูชำระแพ็กเกจสำหรับ Owner / Manager เท่านั้น",
                    "Package billing is available to Owner / Manager only",
                    "ເມນູຊຳລະແພັກເກດສຳລັບ Owner / Manager ເທົ່ານັ້ນ"
                )
                else -> "โหลดข้อมูลแพ็กเกจไม่สำเร็จ: " + (api?.code ?: "network_error")
            }
        } finally {
            loading = false
        }
    }

    fun submit(kind: String, requestKey: String, packageId: String, billing: String, pickedSlip: WebSubscriptionSlip?) {
        if (busy) return
        busy = true
        error = null
        notice = null
        scope.launch {
            try {
                val result = client.submitSubscriptionRequest(
                    session = session,
                    requestKey = requestKey,
                    kind = kind,
                    packageId = packageId,
                    billingInterval = billing,
                    slip = pickedSlip
                )
                notice = when {
                    result.provisionalAccessGranted -> nativeBillingText(
                        "ตรวจสลิปผ่านและเปิดสิทธิ์ชั่วคราวแล้ว · รอ IT ยืนยันยอดเงินจริง",
                        "Slip verified; provisional access granted while IT confirms settlement",
                        "ກວດສະລິບຜ່ານ ແລະເປີດສິດຊົ່ວຄາວ · ລໍຖ້າ IT ຢືນຢັນ"
                    )
                    kind == "payment_notice" -> nativeBillingText(
                        "ส่งสลิปให้ IT ตรวจสอบแล้ว",
                        "Payment slip sent to IT for review",
                        "ສົ່ງສະລິບໃຫ້ IT ກວດສອບແລ້ວ"
                    )
                    else -> nativeBillingText(
                        "ส่งคำขอแพ็กเกจแล้ว",
                        "Package request submitted",
                        "ສົ່ງຄຳຂໍແພັກເກດແລ້ວ"
                    )
                }
                slip = null
                refresh += 1
            } catch (failure: Exception) {
                val api = failure as? WebPosApiException
                error = billingError(api)
            } finally {
                busy = false
            }
        }
    }

    Surface(Modifier.fillMaxSize(), color = Color(0xFFF5F9FF)) {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().statusBarsPadding()
                    .padding(horizontal = 14.dp)
                    .padding(top = 8.dp, bottom = 150.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onBack) { Text("‹ " + nativeUiText("more.title", "เพิ่มเติม")) }
                        Column(Modifier.weight(1f)) {
                            Text(nativeUiText("billing.title", "ชำระแพ็กเกจ"), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = billingInk)
                            Text(nativeUiText("billing.subtitle", "ระบบ Billing เดียวกับ CpIPOS และ IT"), fontSize = 10.sp, color = billingMuted)
                        }
                    }
                }

                if (error != null) item { BillingMessage(error.orEmpty(), true) }
                if (notice != null) item { BillingMessage(notice.orEmpty(), false) }

                if (loading) {
                    item {
                        Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = billingBlue)
                        }
                    }
                } else snapshot?.let { data ->
                    val pending = data.requests.firstOrNull { it.status in setOf("pending", "under_review") }
                    val selected = data.packages.firstOrNull { it.id == selectedPackageId }
                    val owner = session.role == "owner"

                    item {
                        Surface(color = Color.White, shape = RoundedCornerShape(18.dp), shadowElevation = 1.dp) {
                            Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column(Modifier.weight(1f)) {
                                        Text(nativeUiText("billing.current", "แพ็กเกจปัจจุบัน"), fontSize = 10.sp, color = billingMuted)
                                        Text(data.packageName, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = billingInk)
                                    }
                                    Surface(
                                        color = if (data.itConnected) Color(0xFFE8F7EF) else Color(0xFFFFF3DD),
                                        shape = RoundedCornerShape(20.dp)
                                    ) {
                                        Text(
                                            if (data.itConnected) nativeUiText("billing.it_connected", "เชื่อมต่อ IT") else "IT offline",
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            color = if (data.itConnected) Color(0xFF198754) else Color(0xFF9B6518),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                BillingMetric(nativeUiText("billing.status", "สถานะการใช้งาน"), data.contractStatus)
                                BillingMetric(
                                    nativeBillingText("ค่าบริการต่อรอบ", "Price per cycle", "ຄ່າບໍລິການຕໍ່ຮອບ"),
                                    data.amountPerCycle?.let { moneyText(it, data.currency) } ?: "—"
                                )
                                BillingMetric(
                                    nativeBillingText("วันคงเหลือ", "Days remaining", "ວັນຄົງເຫຼືອ"),
                                    data.daysRemaining?.toString() ?: "—"
                                )
                            }
                        }
                    }

                    data.due?.let { due ->
                        item {
                            Surface(
                                color = if (due.accessLocked) Color(0xFFFFEEEE) else Color(0xFFEAF3FF),
                                shape = RoundedCornerShape(18.dp)
                            ) {
                                Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(nativeUiText("billing.due", "ยอดที่ต้องชำระ"), fontWeight = FontWeight.ExtraBold, color = billingInk)
                                    Text(
                                        moneyText(if (due.outstanding > 0) due.outstanding else due.amountDue, due.currency),
                                        fontSize = 25.sp,
                                        fontWeight = FontWeight.Black,
                                        color = billingBlue
                                    )
                                    Text("สถานะ: " + due.status, fontSize = 10.sp, color = billingMuted)
                                    if (due.supportRequired) {
                                        Text("รอบนี้ต้องให้ IT / Support ตรวจสอบก่อนชำระ", fontSize = 10.sp, color = Color(0xFF9B6518))
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Text(
                            nativeBillingText("เลือกแพ็กเกจ", "Choose package", "ເລືອກແພັກເກດ"),
                            fontWeight = FontWeight.ExtraBold,
                            color = billingInk,
                            fontSize = 15.sp
                        )
                    }
                    items(data.packages, key = { it.id }) { pkg ->
                        Surface(
                            color = if (pkg.id == selectedPackageId) Color(0xFFEAF3FF) else Color.White,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth().clickable(enabled = pending == null && owner && !busy) {
                                selectedPackageId = pkg.id
                            }
                        ) {
                            Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = pkg.id == selectedPackageId,
                                    onClick = { if (pending == null && owner && !busy) selectedPackageId = pkg.id },
                                    enabled = pending == null && owner && !busy
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(pkg.name, fontWeight = FontWeight.Bold, color = billingInk)
                                    Text(
                                        if (pkg.contactSales) "CUSTOM · ติดต่อ IT"
                                        else "เดือน " + (pkg.monthlyPrice?.let { moneyText(it, "THB") } ?: "—") +
                                            " · ปี " + (pkg.yearlyPrice?.let { moneyText(it, "THB") } ?: "—"),
                                        fontSize = 9.sp,
                                        color = billingMuted
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = interval == "monthly",
                                onClick = { if (pending == null) interval = "monthly" },
                                label = { Text(nativeUiText("billing.monthly", "รายเดือน")) },
                                enabled = pending == null && owner
                            )
                            FilterChip(
                                selected = interval == "yearly",
                                onClick = { if (pending == null) interval = "yearly" },
                                label = { Text(nativeUiText("billing.yearly", "รายปี")) },
                                enabled = pending == null && owner && selected?.yearlyPrice != null
                            )
                        }
                    }

                    if (!owner) {
                        item { BillingMessage(nativeUiText("billing.owner_only", "เฉพาะ Owner เท่านั้นที่ส่งคำขอชำระแพ็กเกจได้"), false) }
                    } else if (!data.internalDemo) {
                        item {
                            Button(
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !busy && pending == null && selectedPackageId.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(containerColor = billingBlue),
                                onClick = {
                                    val pkg = selected ?: return@Button
                                    val kind = if (pkg.contactSales) "custom_quote_request" else "renewal_intent"
                                    submit(kind, UUID.randomUUID().toString(), pkg.id, if (pkg.contactSales) "monthly" else interval, null)
                                }
                            ) {
                                Text(nativeUiText("billing.renew", "ต่ออายุ / เปลี่ยนแพ็กเกจ"), fontWeight = FontWeight.Bold)
                            }
                        }

                        item {
                            OutlinedButton(
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !busy,
                                onClick = { slipPicker.launch("image/*") }
                            ) {
                                Text(
                                    if (slip == null) nativeUiText("billing.select_slip", "เลือกสลิปชำระเงิน")
                                    else "✓ " + slip!!.fileName
                                )
                            }
                        }

                        item {
                            val paymentPackage = pending?.packageId ?: selectedPackageId
                            val paymentInterval = pending?.billingInterval ?: interval
                            val due = data.due
                            val canUsePending = pending != null && !pending.hasEvidence
                            val selfServiceAllowed = due?.selfServicePaymentAllowed != false || canUsePending
                            Button(
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !busy && slip != null && paymentPackage.isNotBlank() &&
                                    due?.supportRequired != true && selfServiceAllowed,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1AA36F)),
                                onClick = {
                                    val selectedSlip = slip ?: return@Button
                                    val key = pending?.id ?: UUID.randomUUID().toString()
                                    submit("payment_notice", key, paymentPackage, paymentInterval, selectedSlip)
                                }
                            ) {
                                Text(nativeUiText("billing.pay", "แจ้งชำระเงินด้วยสลิป"), fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    item {
                        Surface(color = Color.White, shape = RoundedCornerShape(16.dp)) {
                            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(nativeUiText("billing.bank", "บัญชีรับเงินบริษัท"), fontWeight = FontWeight.ExtraBold, color = billingInk)
                                Text(data.issuer.name, fontSize = 11.sp, color = billingInk)
                                if (data.issuer.bankName.isNotBlank()) Text(data.issuer.bankName, fontSize = 10.sp, color = billingMuted)
                                if (data.issuer.accountName.isNotBlank()) Text(data.issuer.accountName, fontSize = 10.sp, color = billingMuted)
                                if (data.issuer.accountNumber.isNotBlank()) Text("เลขบัญชี " + data.issuer.accountNumber, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = billingBlue)
                                if (data.issuer.promptPayId.isNotBlank()) Text("PromptPay " + data.issuer.promptPayId, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = billingBlue)
                            }
                        }
                    }

                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(nativeUiText("billing.history", "ประวัติคำขอ"), fontWeight = FontWeight.ExtraBold, color = billingInk)
                            TextButton(onClick = { refresh += 1 }, enabled = !busy) {
                                Text(nativeUiText("billing.refresh", "รีเฟรช"))
                            }
                        }
                    }
                    if (data.requests.isEmpty()) {
                        item { BillingEmpty(nativeUiText("billing.no_requests", "ยังไม่มีคำขอแพ็กเกจ")) }
                    } else {
                        items(data.requests.take(10), key = { "req-" + it.id }) { row ->
                            Surface(color = Color.White, shape = RoundedCornerShape(14.dp)) {
                                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(row.packageName.ifBlank { data.packageName }, fontWeight = FontWeight.Bold, color = billingInk)
                                    Text(row.kind + " · " + row.billingInterval + " · " + row.status, fontSize = 9.sp, color = billingMuted)
                                    val value = row.amount ?: row.expectedAmount
                                    if (value != null) Text(moneyText(value, "THB"), fontSize = 11.sp, color = billingBlue, fontWeight = FontWeight.Bold)
                                    if (row.hasEvidence) Text("✓ มีสลิปแล้ว", fontSize = 9.sp, color = Color(0xFF198754))
                                }
                            }
                        }
                    }

                    item { Text(nativeUiText("billing.receipts", "ใบเสร็จ"), fontWeight = FontWeight.ExtraBold, color = billingInk) }
                    if (data.receipts.isEmpty()) {
                        item { BillingEmpty(nativeUiText("billing.no_receipts", "ยังไม่มีใบเสร็จ")) }
                    } else {
                        items(data.receipts.take(10), key = { "receipt-" + it.id }) { receipt ->
                            Surface(color = Color.White, shape = RoundedCornerShape(14.dp)) {
                                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column(Modifier.weight(1f)) {
                                        Text(receipt.number, fontWeight = FontWeight.Bold, color = billingInk)
                                        Text(receipt.packageName + " · " + receipt.billingInterval, fontSize = 9.sp, color = billingMuted)
                                    }
                                    Text(moneyText(receipt.amount, receipt.currency), fontWeight = FontWeight.Bold, color = billingBlue)
                                }
                            }
                        }
                    }
                }
            }

            HomeBottomMenu(
                modifier = Modifier.align(Alignment.BottomCenter),
                selectedTab = "history",
                onMenuClick = onTab
            )
        }
    }
}

@Composable
private fun BillingMetric(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 10.sp, color = billingMuted)
        Text(value, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = billingInk)
    }
}

@Composable
private fun BillingMessage(message: String, error: Boolean) {
    Surface(
        color = if (error) Color(0xFFFFEEEE) else Color(0xFFE9F8EF),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            message,
            Modifier.padding(12.dp),
            fontSize = 10.sp,
            color = if (error) Color(0xFFB13F4C) else Color(0xFF197447)
        )
    }
}

@Composable
private fun BillingEmpty(message: String) {
    Box(Modifier.fillMaxWidth().height(72.dp), contentAlignment = Alignment.Center) {
        Text(message, color = billingMuted, fontSize = 10.sp)
    }
}

@Composable
private fun nativeBillingText(th: String, en: String, lo: String): String {
    val context = LocalContext.current
    return when (NativeUiLanguage.state(context).value) {
        "en" -> en
        "lo" -> lo
        else -> th
    }
}

private fun moneyText(value: Double, currency: String): String =
    (if (currency == "THB") "฿" else currency + " ") + billingMoney.format(value)

private fun billingError(api: WebPosApiException?): String = when (api?.code) {
    "owner_required", "forbidden" -> "เฉพาะ Owner เท่านั้นที่ส่งคำขอแพ็กเกจได้"
    "open_request_exists" -> "มีคำขอเดิมรอตรวจสอบอยู่ กรุณาใช้รายการเดิม"
    "renewal_selection_locked" -> "คำขอเดิมล็อกแพ็กเกจ/รอบบิลไว้แล้ว"
    "subscription_support_required", "subscription_self_service_locked" ->
        "รอบนี้ต้องติดต่อ Support / IT เพื่อดำเนินการ"
    "slip_required" -> "กรุณาแนบสลิป JPG, PNG หรือ WebP ขนาดไม่เกิน 4 MB"
    "slip_upload_failed" -> "อัปโหลดสลิปไม่สำเร็จ กรุณาลองอีกครั้ง"
    "package_unavailable" -> "แพ็กเกจนี้ไม่พร้อมใช้งาน"
    "custom_requires_it_agreement" -> "แพ็กเกจ CUSTOM ต้องให้ IT ตกลงเงื่อนไขก่อน"
    else -> "ดำเนินการแพ็กเกจไม่สำเร็จ: " + (api?.code ?: "network_error")
}
