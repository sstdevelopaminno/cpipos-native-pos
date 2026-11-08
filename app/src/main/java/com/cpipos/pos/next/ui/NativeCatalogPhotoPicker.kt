package com.cpipos.pos.next.ui

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

/**
 * Android camera/gallery picker for the single CpIPOS product-media image.
 * No camera permission and no direct Supabase storage access are needed.
 */
@Composable
internal fun NativeCatalogPhotoPicker(
    selected: Uri?,
    enabled: Boolean,
    onSelected: (Uri?) -> Unit
) {
    val context = LocalContext.current
    var cameraTarget by remember { mutableStateOf<Uri?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            error = null
            onSelected(uri)
        }
    }
    val cameraPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { accepted ->
        if (accepted) {
            cameraTarget?.let(onSelected)
            error = null
        } else {
            error = "ไม่ได้บันทึกรูปถ่าย"
        }
    }
    val preview = remember(selected) {
        selected?.let { uri ->
            runCatching {
                val opts = BitmapFactory.Options().apply { inSampleSize = 16 }
                context.contentResolver.openInputStream(uri)?.use { input ->
                    BitmapFactory.decodeStream(input, null, opts)
                }
            }.getOrNull()
        }
    }

    Surface(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("รูปสินค้า • ใช้ภาพเดียวกันกับ CpIPOS Web และ POS มือถือ")
            if (preview != null) {
                Image(
                    bitmap = preview.asImageBitmap(),
                    contentDescription = "ตัวอย่างรูปสินค้าที่เลือก",
                    modifier = Modifier.fillMaxWidth().height(132.dp),
                    contentScale = ContentScale.Fit
                )
            } else if (selected != null) {
                Text("เลือกรูปสินค้าแล้ว พร้อมบันทึก")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    enabled = enabled,
                    onClick = {
                        try {
                            val folder = File(context.cacheDir, "native-product-capture")
                            check(folder.exists() || folder.mkdirs())
                            val file = File(folder, UUID.randomUUID().toString() + ".jpg")
                            check(file.createNewFile())
                            val uri = FileProvider.getUriForFile(
                                context, context.packageName + ".nativefileprovider", file
                            )
                            cameraTarget = uri
                            cameraPicker.launch(uri)
                        } catch (_: Exception) {
                            error = "เปิดกล้องไม่ได้ กรุณาตรวจสอบแอปกล้อง"
                        }
                    }
                ) { Text("ถ่ายรูป") }
                OutlinedButton(
                    enabled = enabled,
                    onClick = {
                        try { photoPicker.launch("image/*") }
                        catch (_: Exception) { error = "เปิดเลือกรูปไม่ได้" }
                    }
                ) { Text("เลือกจากเครื่อง") }
            }
            if (selected != null) {
                TextButton(enabled = enabled, onClick = { onSelected(null) }) {
                    Text("ไม่เปลี่ยนรูปในรอบนี้")
                }
            }
            error?.let { Text(it) }
            Text("ระบบจะย่อรูปและอัปโหลดผ่านสิทธิ์ผู้จัดการ/เจ้าของร้านของ CpIPOS")
        }
    }
}
