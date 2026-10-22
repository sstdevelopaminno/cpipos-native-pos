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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF5F9FF)) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 26.dp)) {
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("ชำระเงินด้วย QR / โอนเงิน", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF152544))
                Text("ยอดที่ต้องชำระ ฿${due.bahtText()}", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color(0xFF177643))
                val selected = account
                if (selected != null) {
                    Text(selected.bankName + " • " + selected.accountName,
                        textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
                    Text(selected.accountNumber.ifBlank { selected.promptPayPhone },
                        fontSize = 14.sp, color = Color(0xFF57667C))
                }
                if (loading) {
                    Box(Modifier.fillMaxWidth().height(265.dp), contentAlignment = Alignment.Center) {
                        Text("กำลังโหลดบัญชีชำระเงินจาก CpIPOS...")
                    }
                } else if (qrImage != null) {
                    Image(
                        bitmap = qrImage!!.asImageBitmap(),
                        contentDescription = "QR สำหรับชำระเงินให้ร้าน",
                        modifier = Modifier.size(270.dp).background(Color.White).padding(8.dp),
                        contentScale = ContentScale.Fit
                    )
                }
                if (account?.qrMode == "qr_image") {
                    Text("QR ภาพจากร้าน อาจไม่มีจำนวนเงินฝังไว้ โปรดตรวจสอบยอดก่อนโอน",
                        fontSize = 12.sp, textAlign = TextAlign.Center, color = Color(0xFF8B5B20))
                }
                qrError?.let { Text(it, color = Color(0xFFB52836), fontSize = 13.sp) }
                Text(
                    "การแสดงหรือสแกน QR ไม่ใช่หลักฐานว่าเงินเข้าบัญชีแล้ว",
                    textAlign = TextAlign.Center, color = Color(0xFF9B572B),
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                )
                if (qrImage != null && !loading) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = verified && !saving, enabled = !saving, onCheckedChange = { verified = it })
                        Text("ตรวจสอบจากบัญชีร้านแล้วว่าได้รับเงินจริงครบ", fontSize = 13.sp)
                    }
                }
                if (error != null) Text(error, color = Color(0xFFBA2836), fontSize = 13.sp)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onCancel, enabled = !saving, modifier = Modifier.weight(1f)) {
                    Text("กลับตะกร้า")
                }
                Button(
                    onClick = onConfirmReceived,
                    modifier = Modifier.weight(1.5f),
                    enabled = !saving && !loading && qrImage != null && verified
                ) {
                    Text(if (saving) "กำลังบันทึก..." else "ยืนยันรับเงินโอน")
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
