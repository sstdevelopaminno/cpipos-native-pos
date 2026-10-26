package com.cpipos.pos.next.ui

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val preEntryMoney = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.US))

@Composable
internal fun NativePreEntrySubscriptionLockDialog(
    denial: WebEntryAccessDenial?,
    onPayment: () -> Unit,
    onBack: () -> Unit
) {
    val value = denial ?: return
    if (value.kind != "subscription") return
    AlertDialog(
        onDismissRequest = {},
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                value.title.ifBlank { "แพ็กเกจยังไม่พร้อมใช้งาน" },
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF172B4E)
            )
        },
        text = {
            Text(
                value.message.ifBlank {
                    "กรุณาชำระหรือติดต่อ IT เพื่อตรวจสอบแพ็กเกจก่อนเปิดเครื่อง POS"
                },
                color = Color(0xFF66758D),
                fontSize = 13.sp,
                lineHeight = 20.sp
            )
        },
        confirmButton = {
            Button(onClick = onPayment, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF176DED))) {
                Text("ชำระแพ็กเกจ", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onBack) { Text("กลับ") }
        }
    )
}

@Composable
internal fun NativePreEntrySubscriptionScreen(
    client: WebPosClient,
    onBack: () -> Unit,
    onUnlocked: (WebCashierDevices) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var checkout by remember { mutableStateOf<WebPreEntryCheckout?>(null) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var slip by remember { mutableStateOf<WebSubscriptionSlip?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
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
                error = null
            } catch (failure: Exception) {
                slip = null
                error = failure.message ?: "เลือกสลิปไม่สำเร็จ"
            }
        }
    }

    suspend fun loadCheckout() {
        try {
            checkout = client.preEntrySubscriptionCheckout()
            error = null
        } catch (failure: WebPosApiException) {
            error = when (failure.code) {
                "subscription_support_required" ->
                    "รายการนี้ต้องให้ IT / Support ดำเนินการก่อน จึงยังไม่สามารถแนบสลิปจากมือถือได้"
                "internal_demo_not_billable" -> "บัญชีทดสอบภายในไม่สามารถชำระแพ็กเกจได้"
                "receiving_account_not_configured" -> "ฝ่าย IT ยังไม่ได้ตั้งค่าบัญชีรับชำระ"
                else -> "โหลดข้อมูลชำระแพ็กเกจไม่สำเร็จ: " + failure.code
            }
        } catch (failure: Exception) {
            error = "เชื่อมต่อระบบชำระแพ็กเกจไม่สำเร็จ"
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) { loadCheckout() }

    LaunchedEffect(Unit) {
        while (true) {
            delay(5_000L)
            try {
                val devices = client.listEntryDevices()
                if (devices.entryAllowed) {
                    onUnlocked(devices)
                    break
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Keep the verified denial until the next successful refresh.
            }
        }
    }

    Surface(Modifier.fillMaxSize(), color = Color(0xFFF5F9FF)) {
        Column(
            modifier = Modifier.fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TextButton(onClick = onBack, enabled = !busy) { Text("‹ กลับหน้าเลือกเครื่อง") }
            Text("ชำระแพ็กเกจ", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF172B4E))
            Text(
                "ใช้ระบบ Billing เดียวกับ CpIPOS และ IT · เมื่ออนุมัติแล้วแอปจะปลดล็อกอัตโนมัติ",
                fontSize = 11.sp, color = Color(0xFF71849B)
            )

            if (error != null) {
                Surface(color = Color(0xFFFFEEEE), shape = RoundedCornerShape(14.dp)) {
                    Text(error.orEmpty(), Modifier.fillMaxWidth().padding(12.dp), color = Color(0xFFB13F4C), fontSize = 11.sp)
                }
            }
            if (notice != null) {
                Surface(color = Color(0xFFE9F8EF), shape = RoundedCornerShape(14.dp)) {
                    Text(notice.orEmpty(), Modifier.fillMaxWidth().padding(12.dp), color = Color(0xFF197447), fontSize = 11.sp)
                }
            }

            if (loading) {
                Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF176DED))
                }
            } else checkout?.let { quote ->
                Surface(color = Color.White, shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(quote.packageName, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF172B4E))
                        Text(
                            (if (quote.currency == "THB") "฿" else quote.currency + " ") + preEntryMoney.format(quote.amount),
                            fontSize = 28.sp, fontWeight = FontWeight.Black, color = Color(0xFF176DED)
                        )
                        Text(if (quote.billingInterval == "yearly") "รอบรายปี" else "รอบรายเดือน", color = Color(0xFF71849B), fontSize = 11.sp)
                    }
                }

                Surface(color = Color.White, shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("บัญชีรับเงินบริษัท", fontWeight = FontWeight.ExtraBold, color = Color(0xFF172B4E))
                        if (quote.issuer.bankName.isNotBlank()) Text(quote.issuer.bankName)
                        if (quote.issuer.accountName.isNotBlank()) Text(quote.issuer.accountName)
                        if (quote.issuer.accountNumber.isNotBlank()) {
                            Text("เลขบัญชี " + quote.issuer.accountNumber, color = Color(0xFF176DED), fontWeight = FontWeight.Bold)
                        }
                        if (quote.issuer.promptPayId.isNotBlank()) {
                            Text("PromptPay " + quote.issuer.promptPayId, color = Color(0xFF176DED), fontWeight = FontWeight.Bold)
                        }
                    }
                }

                quote.pendingReviewStatus?.let { status ->
                    Surface(color = Color(0xFFFFF5DF), shape = RoundedCornerShape(14.dp)) {
                        Text(
                            if (quote.pendingReviewHasEvidence)
                                "ฝ่าย IT กำลังตรวจสอบหลักฐานการชำระ · สถานะ $status"
                            else "มีรายการรอตรวจสอบ · สถานะ $status",
                            Modifier.fillMaxWidth().padding(12.dp),
                            color = Color(0xFF8A5C13), fontSize = 11.sp
                        )
                    }
                }

                if (quote.token != null && quote.canAttachEvidence && !quote.pendingReviewHasEvidence) {
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !busy,
                        onClick = { picker.launch("image/*") }
                    ) {
                        Text(if (slip == null) "เลือกสลิปชำระเงิน" else "✓ " + slip!!.fileName)
                    }
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !busy && slip != null,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1AA36F)),
                        onClick = {
                            val token = quote.token
                            val selected = slip ?: return@Button
                            busy = true
                            error = null
                            scope.launch {
                                try {
                                    val result = client.submitPreEntrySubscriptionCheckout(token, selected)
                                    slip = null
                                    notice = if (result.provisionalAccessGranted)
                                        "ตรวจสลิปผ่านและเปิดสิทธิ์ชั่วคราวแล้ว กำลังตรวจสถานะเครื่อง..."
                                    else "ส่งสลิปให้ IT ตรวจสอบแล้ว แอปจะปลดล็อกอัตโนมัติเมื่อได้รับอนุมัติ"
                                    loadCheckout()
                                    val devices = client.listEntryDevices()
                                    if (devices.entryAllowed) onUnlocked(devices)
                                } catch (failure: WebPosApiException) {
                                    error = when (failure.code) {
                                        "open_request_exists" -> "มีคำขอชำระเงินที่รอตรวจสอบอยู่แล้ว"
                                        "subscription_support_required" -> "รอบนี้ต้องให้ IT / Support ดำเนินการ"
                                        "checkout_token_invalid" -> "รายการชำระหมดอายุ กรุณาโหลดข้อมูลใหม่"
                                        "slip_required", "slip_type_invalid" -> "สลิปไม่ถูกต้อง กรุณาเลือก JPG, PNG หรือ WebP ไม่เกิน 4 MB"
                                        else -> "ส่งสลิปไม่สำเร็จ: " + failure.code
                                    }
                                } catch (_: Exception) {
                                    error = "ส่งสลิปไม่สำเร็จ กรุณาตรวจสอบอินเทอร์เน็ต"
                                } finally {
                                    busy = false
                                }
                            }
                        }
                    ) { Text("ส่งสลิปให้ IT ตรวจสอบ", fontWeight = FontWeight.Bold) }
                }

                Text(
                    "ระบบจะตรวจสถานะแพ็กเกจทุก 5 วินาทีโดยไม่ต้องปิดแอป",
                    fontSize = 10.sp, color = Color(0xFF71849B)
                )
            }
        }
    }
}
