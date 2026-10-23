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
    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF3F7FE)) {
        Column(
            modifier = Modifier.fillMaxSize()
                .statusBarsPadding().navigationBarsPadding()
                .padding(horizontal = 16.dp)
        ) {
            Text(
                "กดยืนยันหลังตรวจสอบว่าเงินเข้าบัญชีร้านครบแล้วเท่านั้น",
                modifier = Modifier.fillMaxWidth().padding(top = 9.dp, bottom = 7.dp),
                fontSize = 11.sp, color = muted, textAlign = TextAlign.Center
            )
            Button(
                onClick = onConfirmReceived,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFF176DED)),
                enabled = !saving && !loading && qrImage != null
            ) {
                Text(if (saving) "กำลังบันทึก..." else "ยืนยันรับเงินโอน",
                    fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            androidx.compose.material3.TextButton(
                onClick = onCancel, enabled = !saving,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            ) { Text("กลับตะกร้า", color = muted, fontSize = 13.sp) }
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
