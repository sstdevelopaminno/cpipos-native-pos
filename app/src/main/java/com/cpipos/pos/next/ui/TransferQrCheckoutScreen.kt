package com.cpipos.pos.next.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cpipos.pos.next.core.pos.Satang
import com.cpipos.pos.next.core.webpos.WebPaymentAccount
import com.cpipos.pos.next.core.webpos.WebPosClient
import com.cpipos.pos.next.core.webpos.WebPosSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Uses the same payment account configured by Tablet POS. Generating/displaying
 * a QR NEVER marks an order as paid. A cashier must verify funds outside the
 * app before the manual transfer confirmation becomes actionable.
 */
@Composable
internal fun TransferQrCheckoutScreen(
    client: WebPosClient,
    session: WebPosSession,
    due: Satang,
    saving: Boolean,
    error: String?,
    onCancel: () -> Unit,
    onConfirmReceived: () -> Unit
) {
    var account by remember(session.id) { mutableStateOf<WebPaymentAccount?>(null) }
    var qrImage by remember(session.id, due.value) { mutableStateOf<Bitmap?>(null) }
    var loading by remember(session.id, due.value) { mutableStateOf(true) }
    var qrError by remember(session.id, due.value) { mutableStateOf<String?>(null) }
    var verified by remember(session.id, due.value) { mutableStateOf(false) }

    LaunchedEffect(client, session.id, due.value) {
        loading = true
        account = null
        qrImage = null
        qrError = null
        verified = false
        try {
            val selected = client.activePaymentAccounts(session).firstOrNull()
                ?: throw IllegalStateException("ร้านนี้ยังไม่ได้ตั้งค่าบัญชีรับโอนที่ใช้งานอยู่ใน POS")
            account = selected
            val bitmap = if (selected.qrMode == "qr_image" && selected.qrImageUrl.isNotBlank()) {
                withContext(Dispatchers.IO) { fetchConfiguredQr(selected.qrImageUrl) }
            } else if (selected.promptPayPhone.filter(Char::isDigit).length in 9..15) {
                val image = client.promptPayQrImage(selected.promptPayPhone, due)
                withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(image, 0, image.size) }
            } else if (selected.qrImageUrl.isNotBlank()) {
                withContext(Dispatchers.IO) { fetchConfiguredQr(selected.qrImageUrl) }
            } else null
            qrImage = bitmap ?: throw IllegalStateException("บัญชีนี้ยังไม่มี PromptPay หรือรูป QR ที่ใช้ได้")
        } catch (failure: Exception) {
            qrError = "ไม่สามารถแสดง QR ได้: " + (failure.message ?: "โปรดตรวจสอบการตั้งค่ารับชำระ").take(100)
        } finally {
            loading = false
        }
    }

    BackHandler(enabled = true) { if (!saving) onCancel() }
    val ink = Color(0xFF152544)
    val muted = Color(0xFF71819B)
    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF3F7FE)) {
        Column(
            modifier = Modifier.fillMaxSize()
                .statusBarsPadding().navigationBarsPadding()
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("สแกน QR เพื่อชำระเงิน", color = ink,
                        fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
                    Text("บัญชีรับชำระจากการตั้งค่า CpIPOS ของร้าน",
                        color = muted, fontSize = 12.sp)
                }
                Surface(shape = RoundedCornerShape(50), color = Color(0xFFE7F0FF)) {
                    Text("โอนเงิน", modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                        color = Color(0xFF176DEE), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(modifier = Modifier.fillMaxWidth(), color = Color.White,
                    shape = RoundedCornerShape(20.dp), shadowElevation = 2.dp) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 17.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("ยอดที่ต้องชำระ", color = muted, fontSize = 13.sp)
                        Text("฿${due.bahtText()}", fontSize = 31.sp,
                            fontWeight = FontWeight.ExtraBold, color = Color(0xFF16824F))
                        Text("กรุณาตรวจสอบยอดก่อนทำรายการ", color = muted, fontSize = 11.sp)
                    }
                }
                Surface(modifier = Modifier.fillMaxWidth(), color = Color.White,
                    shape = RoundedCornerShape(20.dp), shadowElevation = 2.dp) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        val selected = account
                        if (selected != null) {
                            Text("บัญชีปลายทาง", color = muted, fontSize = 12.sp)
                            Text(selected.bankName, color = ink, fontSize = 17.sp,
                                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                            Text(selected.accountName, color = ink, fontSize = 14.sp,
                                textAlign = TextAlign.Center)
                            Text(selected.accountNumber.ifBlank { selected.promptPayPhone },
                                color = muted, fontSize = 13.sp)
                            HorizontalDivider(color = Color(0xFFEDF1F7))
                        }
                        when {
                            loading -> Box(Modifier.fillMaxWidth().height(224.dp),
                                contentAlignment = Alignment.Center) {
                                Text("กำลังโหลด QR จากระบบร้านค้า...", color = muted, fontSize = 13.sp)
                            }
                            qrImage != null -> {
                                qrImage?.let { image ->
                                    Image(bitmap = image.asImageBitmap(),
                                        contentDescription = "QR สำหรับโอนเงินเข้าบัญชีร้าน",
                                        modifier = Modifier.size(232.dp)
                                            .background(Color.White).padding(6.dp),
                                        contentScale = ContentScale.Fit)
                                }
                                Text("สแกนด้วยแอปธนาคารเพื่อชำระเงิน",
                                    color = muted, fontSize = 12.sp, textAlign = TextAlign.Center)
                            }
                            else -> Box(Modifier.fillMaxWidth().height(95.dp),
                                contentAlignment = Alignment.Center) {
                                Text("ไม่มี QR สำหรับบัญชีนี้", color = Color(0xFFB42336))
                            }
                        }
                    }
                }
                if (account?.qrMode == "qr_image") {
                    Surface(color = Color(0xFFFFF3E3), shape = RoundedCornerShape(14.dp)) {
                        Text("QR รูปภาพจากร้านอาจไม่มีจำนวนเงินฝังไว้ โปรดตรวจยอดในแอปธนาคาร",
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            color = Color(0xFF91601D), fontSize = 12.sp,
                            textAlign = TextAlign.Center)
                    }
                }
                Surface(color = Color(0xFFFFF7EC), shape = RoundedCornerShape(14.dp)) {
                    Text("การแสดง QR ไม่ได้ยืนยันว่าได้รับเงินจริง ต้องตรวจสอบยอดเข้าบัญชีร้านก่อน",
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        color = Color(0xFF8F5E1C), fontSize = 12.sp,
                        textAlign = TextAlign.Center)
                }
                qrError?.let { Text(it, color = Color(0xFFB42336), fontSize = 12.sp) }
                error?.let { Text(it, color = Color(0xFFB42336), fontSize = 12.sp) }
                Surface(modifier = Modifier.fillMaxWidth(), color = Color.White,
                    shape = RoundedCornerShape(16.dp)) {
                    Row(
                        modifier = Modifier.padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = verified,
                            enabled = !saving && !loading && qrImage != null,
                            onCheckedChange = { verified = it })
                        Text("ตรวจสอบบัญชีร้านแล้ว ยืนยันว่าได้รับเงินจริงครบ",
                            modifier = Modifier.weight(1f), color = ink,
                            fontSize = 13.sp, lineHeight = 19.sp,
                            fontWeight = FontWeight.Medium)
                    }
                }
                Spacer(Modifier.height(3.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(onClick = onCancel, enabled = !saving,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(16.dp)) {
                    Text("กลับตะกร้า", color = ink, fontSize = 13.sp)
                }
                Button(onClick = onConfirmReceived,
                    modifier = Modifier.weight(1.45f).height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    enabled = !saving && !loading && qrImage != null && verified) {
                    Text(if (saving) "กำลังบันทึก..." else "ยืนยันรับเงินโอน",
                        fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}

private fun fetchConfiguredQr(rawUrl: String): Bitmap? {
    if (rawUrl.startsWith("data:image/")) {
        require(rawUrl.length <= 1_250_000 && Regex(
            "^data:image/(png|jpeg|jpg|webp);base64,", RegexOption.IGNORE_CASE
        ).containsMatchIn(rawUrl))
        val bytes = Base64.decode(rawUrl.substringAfter(','), Base64.DEFAULT)
        require(bytes.isNotEmpty() && bytes.size <= 900_000)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }
    val url = URL(rawUrl)
    require(url.protocol == "https" && url.userInfo == null)
    val conn = (url.openConnection() as HttpURLConnection).apply {
        instanceFollowRedirects = false
        connectTimeout = 8000
        readTimeout = 12000
        setRequestProperty("Accept", "image/png,image/webp,image/jpeg")
    }
    return try {
        require(conn.responseCode == 200 && conn.contentType.orEmpty().startsWith("image/"))
        val bytes = conn.inputStream.use { stream ->
            val out = java.io.ByteArrayOutputStream()
            val chunk = ByteArray(8192)
            while (out.size() <= 900_000) {
                val count = stream.read(chunk)
                if (count < 0) break
                out.write(chunk, 0, count)
            }
            out.toByteArray()
        }
        require(bytes.isNotEmpty() && bytes.size <= 900_000)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } finally {
        conn.disconnect()
    }
}
