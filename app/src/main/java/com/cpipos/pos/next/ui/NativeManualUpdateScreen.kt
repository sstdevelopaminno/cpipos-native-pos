package com.cpipos.pos.next.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cpipos.pos.next.BuildConfig
import com.cpipos.pos.next.core.update.NativeApkUpdateManager
import kotlinx.coroutines.launch

/**
 * Customer-initiated stable Android update. Never bypass Android's installer,
 * the pinned signing certificate, SHA-256 verification or open-shift safeguards.
 * The parent passes an explicit safe-to-install flag from the active sale journals.
 */
@Composable
internal fun NativeManualUpdateScreen(
    allowInstall: Boolean,
    onBack: () -> Unit,
    onTab: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val offer by NativeApkUpdateManager.state.collectAsState()
    var checking by remember { mutableStateOf(false) }
    var checked by remember { mutableStateOf(false) }

    Surface(Modifier.fillMaxSize(), color = Color(0xFFF5F9FF)) {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxWidth().statusBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                OutlinedButton(onClick = onBack) { Text("กลับไปเมนูเพิ่มเติม") }
                Text("อัปเดต CpiPOS Mobile", fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF17365B))
                Text("เวอร์ชันที่ติดตั้งอยู่: " + BuildConfig.VERSION_NAME)
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color.White,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val message = when (offer.phase) {
                            "ready" -> "ดาวน์โหลดและตรวจสอบ APK รุ่น " +
                                offer.versionName + " เรียบร้อยแล้ว"
                            "downloading" -> "กำลังดาวน์โหลด APK รุ่น " +
                                offer.versionName + " และตรวจลายเซ็น"
                            else -> if (checked)
                                "ยังไม่พบไฟล์อัปเดตที่ผ่านการตรวจสอบ " +
                                    "หรือไม่สามารถเชื่อมต่อกับเซิร์ฟเวอร์ได้"
                            else "กดตรวจสอบเพื่อค้นหา APK Stable ที่เผยแพร่ล่าสุด"
                        }
                        Text(message, color = Color(0xFF36516D))
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !checking && offer.phase != "downloading",
                            onClick = {
                                checking = true
                                scope.launch {
                                    try {
                                        NativeApkUpdateManager.checkAndStart(
                                            context.applicationContext)
                                        checked = true
                                    } finally {
                                        checking = false
                                    }
                                }
                            }
                        ) {
                            Text(if (checking) "กำลังตรวจสอบ..." else "ตรวจสอบอัปเดตล่าสุด")
                        }
                        if (offer.phase == "ready") {
                            Button(
                                modifier = Modifier.fillMaxWidth(),
                                enabled = allowInstall,
                                onClick = { NativeApkUpdateManager.openSystemInstaller(context) }
                            ) { Text("เปิดตัวติดตั้งอัปเดตของ Android") }
                        }
                        if (!allowInstall) {
                            Text(
                                "ยังติดตั้งไม่ได้: กรุณาจบบิล/ตรวจบิลค้าง " +
                                    "และออกจากหน้าขายก่อนติดตั้ง",
                                color = Color(0xFFAD6243)
                            )
                        }
                    }
                }
                Text(
                    "ระบบดาวน์โหลดเฉพาะ APK Stable จาก CpIPOS ที่ตรวจสอบ " +
                        "SHA-256, ชื่อแพ็กเกจ, เวอร์ชัน และลายเซ็นเดิมแล้ว " +
                        "Android จะขอให้เจ้าของเครื่องยืนยันการติดตั้งทับแอปเดิม " +
                        "ห้ามถอนแอปหรือล้างข้อมูลก่อนอัปเดต",
                    color = Color(0xFF64778F)
                )
                Spacer(Modifier.height(96.dp))
            }
            HomeBottomMenu(
                modifier = Modifier.align(Alignment.BottomCenter),
                selectedTab = "history",
                onMenuClick = onTab
            )
        }
    }
}
