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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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

    LaunchedEffect(client, session.id, due.value) {
        loading = true
        account = null
        qrImage = null
        qrError = null
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

    BackHandler(enabled = !saving) { onCancel() }
    val ink = Color(0xFF152544)
    val muted = Color(0xFF71819B)
    val blue = Color(0xFF176DED)
    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF5F9FF)) {
        Column(
            modifier = Modifier.fillMaxSize()
                .statusBarsPadding().navigationBarsPadding()
                .padding(horizontal = 17.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("สแกน QR เพื่อชำระเงิน", fontSize = 21.sp,
                        fontWeight = FontWeight.ExtraBold, color = ink)
                    Text("ให้ลูกค้าสแกนด้วยแอปธนาคาร",
                        color = muted, fontSize = 12.sp)
                }
                Surface(shape = RoundedCornerShape(50), color = Color(0xFFE7F0FF)) {
                    Text("QR Pay", color = blue, fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp))
                }
            }
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(15.dp)
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(), color = Color.White,
                    shape = RoundedCornerShape(21.dp), shadowElevation = 2.dp
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("ยอดที่ต้องชำระ", fontSize = 13.sp, color = muted)
                        Text("฿${due.bahtText()}", fontSize = 32.sp,
                            fontWeight = FontWeight.ExtraBold, color = blue)
                    }
                }
                Surface(
                    modifier = Modifier.fillMaxWidth(), color = Color.White,
                    shape = RoundedCornerShape(24.dp), shadowElevation = 3.dp
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .padding(horizontal = 15.dp, vertical = 22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color(0xFFEAF3FF)
                        ) {
                            Text("พร้อมสแกนชำระเงิน", color = blue,
                                fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp))
                        }
                        Box(
                            modifier = Modifier.size(264.dp)
                                .background(Color(0xFFF8FBFF), RoundedCornerShape(20.dp))
                                .padding(13.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            when {
                                loading -> {
                                    androidx.compose.material3.CircularProgressIndicator(color = blue)
                                }
                                qrImage != null -> {
                                    qrImage?.let { bitmap ->
                                        Image(
                                            bitmap = bitmap.asImageBitmap(),
                                            contentDescription = "QR สำหรับโอนเงินเข้าร้าน",
                                            modifier = Modifier.fillMaxSize()
                                                .background(Color.White, RoundedCornerShape(12.dp))
                                                .padding(5.dp),
                                            contentScale = ContentScale.Fit
                                        )
                                    }
                                }
                                else -> Text(
                                    "ไม่สามารถแสดง QR ได้", fontSize = 13.sp,
                                    color = Color(0xFFB42336)
                                )
                            }
                        }
                        Text("สแกน QR เพื่อชำระเงิน",
                            fontSize = 12.sp, color = muted,
                            textAlign = TextAlign.Center)
                    }
                }
                qrError?.let { Text(it, color = Color(0xFFB42336), fontSize = 12.sp) }
                error?.let { Text(it, color = Color(0xFFB42336), fontSize = 12.sp) }
            }
            Text(
                "กรุณาตรวจยอดเข้าบัญชีร้านครบแล้วก่อนกดยืนยัน",
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp),
                fontSize = 11.sp, color = muted, textAlign = TextAlign.Center
            )
            Button(
                onClick = onConfirmReceived,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = blue),
                enabled = !saving && !loading && qrImage != null
            ) {
                Text(if (saving) "กำลังบันทึก..." else "ยืนยันรับเงินโอน",
                    fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            androidx.compose.material3.TextButton(
                onClick = onCancel,
                enabled = !saving,
                modifier = Modifier.fillMaxWidth().padding(bottom = 7.dp)
            ) {
                Text("กลับตะกร้า", color = muted, fontSize = 13.sp)
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
