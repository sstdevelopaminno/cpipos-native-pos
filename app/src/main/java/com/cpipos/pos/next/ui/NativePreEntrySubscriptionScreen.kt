package com.cpipos.pos.next.ui

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val preBillingInk = Color(0xFF172B4E)
private val preBillingBlue = Color(0xFF176DED)
private val preBillingMuted = Color(0xFF71849B)
private val preBillingMoney = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.US))

@Composable
internal fun NativePreEntrySubscriptionScreen(
    client: WebPosClient,
    onBack: () -> Unit,
    onRecheckAccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var quote by remember { mutableStateOf<WebPreEntrySubscriptionQuote?>(null) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var slip by remember { mutableStateOf<WebSubscriptionSlip?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            error = null
            try {
                slip = withContext(Dispatchers.IO) {
                    val resolver = context.contentResolver
                    val mime = resolver.getType(uri).orEmpty()
                    require(mime in setOf("image/jpeg", "image/png", "image/webp")) {
                        "รองรับสลิป JPG, PNG หรือ WebP เท่านั้น"
                    }
                    var name = "subscription-slip"
                    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) name = cursor.getString(0)?.take(120).orEmpty().ifBlank { name }
                    }
                    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("อ่านไฟล์สลิปไม่ได้")
                    require(bytes.isNotEmpty() && bytes.size <= 4 * 1024 * 1024) {
                        "ไฟล์สลิปต้องไม่เกิน 4 MB"
                    }
                    WebSubscriptionSlip(name, mime, bytes)
                }
            } catch (failure: Exception) {
                slip = null
                error = failure.message ?: "เลือกสลิปไม่สำเร็จ"
            }
        }
    }

    LaunchedEffect(refresh) {
        loading = true
        error = null
        try {
            quote = client.preEntrySubscriptionQuote()
        } catch (failure: Exception) {
            val api = failure as? WebPosApiException
            error = when (api?.code) {
                "subscription_support_required" -> "รอบนี้ต้องให้ฝ่าย IT / Support ตรวจสอบก่อนชำระ"
                "receiving_account_not_configured" -> "ฝ่าย IT ยังไม่ได้ตั้งค่าบัญชีรับชำระ"
                "employee_verification_required" -> "เซสชันยืนยันพนักงานหมดอายุ กรุณากลับไปยืนยันใหม่"
                else -> "โหลดข้อมูลชำระแพ็กเกจไม่สำเร็จ: " + (api?.code ?: "network_error")
            }
        } finally {
            loading = false
        }
    }

    Surface(Modifier.fillMaxSize(), color = Color(0xFFF5F9FF)) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("แพ็กเกจหมดอายุ / ถูกล็อก", fontSize = 23.sp, fontWeight = FontWeight.ExtraBold, color = preBillingInk)
            Text(
                "ระบบขายถูกล็อกตามสถานะจริงจาก CpIPOS / IT แต่ยังเปิดหน้าชำระแพ็กเกจและตรวจสอบสิทธิ์ได้",
                fontSize = 11.sp, lineHeight = 16.sp, color = preBillingMuted
            )

            if (error != null) PreBillingNotice(error.orEmpty(), true)
            if (notice != null) PreBillingNotice(notice.orEmpty(), false)

            if (loading) {
                Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = preBillingBlue)
                }
            }

            quote?.let { data ->
                Surface(color = Color.White, shape = RoundedCornerShape(18.dp), shadowElevation = 1.dp) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(data.storeName + " · " + data.storeCode, fontWeight = FontWeight.ExtraBold, color = preBillingInk)
                        PreBillingRow("แพ็กเกจ", data.packageName)
                        PreBillingRow("รอบชำระ", if (data.billingInterval == "yearly") "รายปี" else "รายเดือน")
                        Text(
                            (if (data.currency == "THB") "฿" else data.currency + " ") + preBillingMoney.format(data.amount),
                            fontSize = 28.sp, fontWeight = FontWeight.Black, color = preBillingBlue
                        )
                    }
                }

                Surface(color = Color(0xFFEAF3FF), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("บัญชีรับชำระจากฝ่าย IT", fontWeight = FontWeight.ExtraBold, color = preBillingInk)
                        if (data.bankName.isNotBlank()) Text(data.bankName, fontSize = 12.sp, color = preBillingInk)
                        if (data.accountName.isNotBlank()) Text(data.accountName, fontSize = 12.sp, color = preBillingInk)
                        if (data.accountNumber.isNotBlank()) Text("เลขบัญชี " + data.accountNumber, fontWeight = FontWeight.Bold, color = preBillingBlue)
                        if (data.promptPayId.isNotBlank()) Text("PromptPay " + data.promptPayId, fontWeight = FontWeight.Bold, color = preBillingBlue)
                    }
                }

                if (data.pendingHasEvidence) {
                    PreBillingNotice("ได้รับสลิปแล้ว · ฝ่าย IT กำลังตรวจสอบรายการชำระ", false)
                } else {
                    OutlinedButton(
                        onClick = { picker.launch("image/*") },
                        enabled = !busy && data.token != null,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(if (slip == null) "เลือกสลิปชำระเงิน" else "✓ " + slip!!.fileName)
                    }
                    Button(
                        onClick = {
                            val selected = slip ?: return@Button
                            if (busy) return@Button
                            busy = true
                            error = null
                            notice = null
                            scope.launch {
                                try {
                                    val result = client.submitPreEntrySubscriptionPayment(data, selected)
                                    slip = null
                                    notice = if (result.provisionalAccessGranted)
                                        "ตรวจสลิปผ่านและเปิดสิทธิ์ชั่วคราวแล้ว กดตรวจสอบสิทธิ์เพื่อเข้า POS"
                                    else "ส่งสลิปให้ฝ่าย IT ตรวจสอบแล้ว"
                                    refresh += 1
                                    if (result.provisionalAccessGranted) onRecheckAccess()
                                } catch (failure: Exception) {
                                    val api = failure as? WebPosApiException
                                    error = "ส่งหลักฐานไม่สำเร็จ: " + (api?.code ?: failure.message ?: "network_error")
                                } finally {
                                    busy = false
                                }
                            }
                        },
                        enabled = !busy && slip != null && data.token != null,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A9A68)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(if (busy) "กำลังตรวจสอบสลิป..." else "ส่งหลักฐานการชำระ", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Button(
                onClick = onRecheckAccess,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = preBillingBlue),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("ตรวจสอบสิทธิ์อีกครั้ง", fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = onBack, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text("กลับไปเลือกเครื่อง")
            }
        }
    }
}

@Composable
private fun PreBillingRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 11.sp, color = preBillingMuted)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = preBillingInk)
    }
}

@Composable
private fun PreBillingNotice(text: String, isError: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (isError) Color(0xFFFFEEEE) else Color(0xFFE9F8EF),
        shape = RoundedCornerShape(14.dp)
    ) {
        Text(
            text,
            modifier = Modifier.padding(13.dp),
            fontSize = 11.sp,
            lineHeight = 16.sp,
            color = if (isError) Color(0xFFB13F4C) else Color(0xFF197447)
        )
    }
}
