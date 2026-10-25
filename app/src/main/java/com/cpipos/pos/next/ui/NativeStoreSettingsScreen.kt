package com.cpipos.pos.next.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.cpipos.pos.next.core.webpos.WebPosApiException
import com.cpipos.pos.next.core.webpos.WebPosClient
import com.cpipos.pos.next.core.webpos.WebPosSession
import com.cpipos.pos.next.core.webpos.WebStoreSettings
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val storeInk = Color(0xFF172B4E)
private val storeMuted = Color(0xFF71849B)
private val storeBlue = Color(0xFF176DED)

@Composable
internal fun NativeStoreSettingsScreen(
    client: WebPosClient,
    session: WebPosSession,
    onBack: () -> Unit,
    onTab: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var store by remember(session.id) { mutableStateOf<WebStoreSettings?>(null) }
    var displayName by remember(session.id) { mutableStateOf("") }
    var companyAddress by remember(session.id) { mutableStateOf("") }
    var contactPhone by remember(session.id) { mutableStateOf("") }
    var logoUrl by remember(session.id) { mutableStateOf("") }
    var logoPreview by remember { mutableStateOf<Bitmap?>(null) }
    var loading by remember(session.id) { mutableStateOf(true) }
    var saving by remember(session.id) { mutableStateOf(false) }
    var logoBusy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    fun applyStore(value: WebStoreSettings) {
        store = value
        displayName = value.displayName
        companyAddress = value.companyAddress
        contactPhone = value.contactPhone
        logoUrl = value.logoUrl
        logoPreview = decodeDataImage(value.logoUrl)
    }

    val logoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        logoBusy = true
        error = null
        scope.launch {
            try {
                val encoded = resizeStoreLogo(context, uri)
                logoUrl = encoded
                logoPreview = decodeDataImage(encoded)
                notice = "เตรียมโลโก้แล้ว กดบันทึกเพื่อใช้กับ CpIPOS และใบเสร็จ"
            } catch (failure: Exception) {
                error = failure.message ?: "อ่านรูปโลโก้ไม่สำเร็จ"
            } finally {
                logoBusy = false
            }
        }
    }

    LaunchedEffect(client, session.id) {
        try {
            applyStore(client.storeSettings(session))
            error = null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = when (failure) {
                is WebPosApiException -> when (failure.status) {
                    401, 403 -> "บัญชีนี้ไม่มีสิทธิ์ดูข้อมูลร้าน/บริษัท"
                    else -> "โหลดข้อมูลร้านไม่สำเร็จ (" + failure.code + ")"
                }
                else -> "โหลดข้อมูลร้านไม่สำเร็จ กรุณาตรวจสอบเครือข่าย"
            }
        } finally {
            loading = false
        }
    }

    fun save() {
        if (saving || loading) return
        if (displayName.trim().isEmpty()) {
            error = "กรุณาระบุชื่อร้านค้า / บริษัท"
            return
        }
        saving = true
        error = null
        scope.launch {
            try {
                val saved = client.updateStoreSettings(
                    session = session,
                    displayName = displayName,
                    logoUrl = logoUrl,
                    companyAddress = companyAddress,
                    contactPhone = contactPhone
                )
                applyStore(saved)
                notice = "บันทึกแล้ว · CpIPOS และใบเสร็จจะใช้ข้อมูลชุดเดียวกัน"
            } catch (failure: Exception) {
                error = when (failure) {
                    is WebPosApiException -> when (failure.status) {
                        403 -> "การแก้ข้อมูลร้าน/บริษัทอนุญาตเฉพาะ Owner"
                        422 -> "ข้อมูลร้านหรือโลโก้ไม่ถูกต้อง กรุณาตรวจสอบอีกครั้ง"
                        else -> "บันทึกไม่สำเร็จ (" + failure.code + ")"
                    }
                    else -> "บันทึกไม่สำเร็จ: " + (failure.message ?: "network error")
                }
            } finally {
                saving = false
            }
        }
    }

    Surface(Modifier.fillMaxSize(), color = Color(0xFFF5F9FF)) {
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().statusBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 112.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onBack) { Text("‹ ตั้งค่า") }
                    Column(Modifier.weight(1f)) {
                        Text(
                            "ตั้งชื่อร้านค้า / บริษัท",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = storeInk
                        )
                        Text(
                            "ข้อมูลเดียวกับ CpIPOS และใบเสร็จ",
                            fontSize = 11.sp,
                            color = storeMuted
                        )
                    }
                }

                if (loading) {
                    Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = storeBlue)
                    }
                } else {
                    Surface(color = Color.White, shape = RoundedCornerShape(18.dp)) {
                        Column(
                            Modifier.fillMaxWidth().padding(15.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = store?.code.orEmpty(),
                                onValueChange = {},
                                enabled = false,
                                label = { Text("รหัสร้าน") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = displayName,
                                onValueChange = { displayName = it.take(120) },
                                label = { Text("ชื่อร้านค้า / บริษัท") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                enabled = !saving
                            )
                            OutlinedTextField(
                                value = contactPhone,
                                onValueChange = { contactPhone = it.take(40) },
                                label = { Text("เบอร์ติดต่อ") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                enabled = !saving
                            )
                            OutlinedTextField(
                                value = companyAddress,
                                onValueChange = { companyAddress = it.take(500) },
                                label = { Text("ที่อยู่") },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 92.dp),
                                minLines = 3,
                                enabled = !saving
                            )

                            Text("โลโก้ร้าน / บริษัท", fontWeight = FontWeight.Bold, color = storeInk)
                            Surface(
                                color = Color(0xFFF7FAFE),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth().height(122.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    val preview = logoPreview
                                    if (preview != null) {
                                        Image(
                                            bitmap = preview.asImageBitmap(),
                                            contentDescription = "โลโก้ร้าน",
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier.fillMaxSize().padding(12.dp)
                                        )
                                    } else {
                                        Text(
                                            if (logoUrl.isNotBlank()) "มีโลโก้จาก CpIPOS แล้ว"
                                            else "ยังไม่ได้ตั้งโลโก้",
                                            color = storeMuted,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { logoPicker.launch("image/*") },
                                    enabled = !saving && !logoBusy,
                                    colors = ButtonDefaults.buttonColors(containerColor = storeBlue),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(if (logoBusy) "กำลังย่อรูป..." else "เลือกรูปโลโก้")
                                }
                                OutlinedButton(
                                    onClick = {
                                        logoUrl = ""
                                        logoPreview = null
                                        notice = "ตั้งให้ไม่ใช้โลโก้แล้ว กดบันทึกเพื่อยืนยัน"
                                    },
                                    enabled = !saving && logoUrl.isNotBlank(),
                                    modifier = Modifier.weight(1f)
                                ) { Text("เอาโลโก้ออก") }
                            }

                            Text(
                                "โลโก้จะถูกย่อไม่เกิน 360×180 px แล้วบันทึกชุดเดียวกับ CpIPOS " +
                                    "ใบเสร็จ HTML/ระบบพิมพ์จะใช้โลโก้นี้อัตโนมัติเมื่อมีการตั้งค่า",
                                fontSize = 11.sp,
                                color = storeMuted
                            )

                            Button(
                                onClick = { save() },
                                enabled = !saving && !logoBusy,
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = storeBlue)
                            ) {
                                if (saving) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(19.dp),
                                        strokeWidth = 2.dp,
                                        color = Color.White
                                    )
                                } else {
                                    Text("บันทึกข้อมูลร้าน / บริษัท", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    if (notice.isNotBlank()) {
                        Text(notice, color = Color(0xFF178A50), fontSize = 12.sp)
                    }
                    error?.let {
                        Text(it, color = Color(0xFFB33A49), fontSize = 12.sp)
                    }
                }
            }

            HomeBottomMenu(
                modifier = Modifier.align(Alignment.BottomCenter),
                selectedTab = "settings",
                onMenuClick = onTab
            )
        }
    }
}

private fun decodeDataImage(value: String): Bitmap? {
    if (!value.startsWith("data:image/")) return null
    val comma = value.indexOf(',')
    if (comma <= 0) return null
    return runCatching {
        val bytes = Base64.decode(value.substring(comma + 1), Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()
}

private suspend fun resizeStoreLogo(
    context: android.content.Context,
    uri: Uri
): String = withContext(Dispatchers.IO) {
    val source = context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it)
    } ?: throw IllegalArgumentException("ไม่สามารถอ่านรูปโลโก้ได้")

    var current = source
    try {
        val ratio = minOf(360f / source.width.coerceAtLeast(1), 180f / source.height.coerceAtLeast(1), 1f)
        val width = (source.width * ratio).toInt().coerceAtLeast(1)
        val height = (source.height * ratio).toInt().coerceAtLeast(1)
        if (width != source.width || height != source.height) {
            current = Bitmap.createScaledBitmap(source, width, height, true)
        }

        var quality = 82
        var bytes: ByteArray
        do {
            val output = ByteArrayOutputStream()
            current.compress(Bitmap.CompressFormat.WEBP, quality, output)
            bytes = output.toByteArray()
            quality -= 10
        } while (bytes.size > 155_000 && quality >= 42)

        if (bytes.size > 160_000) {
            throw IllegalArgumentException("รูปโลโก้ยังมีขนาดใหญ่เกินไป กรุณาเลือกรูปที่เล็กลง")
        }
        "data:image/webp;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
    } finally {
        if (current !== source) current.recycle()
        source.recycle()
    }
}
