package com.cpipos.pos.next.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Image
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cpipos.pos.next.R
import com.cpipos.pos.next.core.webpos.LiveSaleJournal
import com.cpipos.pos.next.core.webpos.WebBranch
import com.cpipos.pos.next.core.webpos.WebPosClient
import com.cpipos.pos.next.core.webpos.WebPosProduct
import com.cpipos.pos.next.core.webpos.WebPosSession
import com.cpipos.pos.next.core.webpos.WebStore
import com.cpipos.pos.next.core.webpos.WebPosApiException
import kotlinx.coroutines.launch

private val liveBlue = Color(0xFF176DED)
private val liveInk = Color(0xFF152749)

@Composable
internal fun MobilePosLiveScreen(client: WebPosClient) {
    val ctx = LocalContext.current
    val journal = remember(ctx.applicationContext) { LiveSaleJournal(ctx.applicationContext) }
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf("store") }
    var storeCode by remember { mutableStateOf("") }
    var selectedLanguage by remember { mutableStateOf(LoginLanguage.Thai) }
    var showPin by remember { mutableStateOf(false) }
    var store by remember { mutableStateOf<WebStore?>(null) }
    var branch by remember { mutableStateOf<WebBranch?>(null) }
    var deviceCode by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var session by remember { mutableStateOf<WebPosSession?>(null) }
    var catalog by remember { mutableStateOf<List<WebPosProduct>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var unresolved by remember { mutableStateOf("") }
    val storePrefs = remember(ctx.applicationContext) {
        ctx.getSharedPreferences("cpipos_native_terminal_v1", android.content.Context.MODE_PRIVATE)
    }

    fun refreshCatalog(verified: WebPosSession) {
        scope.launch {
            busy = true
            error = null
            try {
                val current = client.getSession()
                check(current.id == verified.id && current.branchId == verified.branchId)
                session = current
                if (!current.canSell) {
                    error = "พนักงานไม่มีสิทธิ์ขาย กรุณาติดต่อผู้จัดการร้าน"
                    step = "shift"
                } else if (current.shiftId == null) {
                    step = "shift"
                } else {
                    val pending = journal.unresolved(current)
                    if (pending.isNotEmpty()) {
                        unresolved = "พบ ${pending.size} บิลค้างตรวจสอบ (เลขอ้างอิง: ${pending.first().id.take(8)}). ตรวจสอบสถานะใน POS Web ก่อนเปิดบิลใหม่"
                        step = "review"
                    } else {
                        catalog = client.products(current)
                        step = "counter"
                    }
                }
            } catch (e: Exception) {
                error = "โหลดข้อมูลร้าน/กะ/สินค้าไม่สำเร็จ: ${e.message?.take(85)}"
                step = "shift"
            } finally { busy = false }
        }
    }

    // Reuse the original approved CpIPOS login composable for live and preview.
    // Store Code / language toggle / logo stay identical; only behavior differs.
    if (step == "store") {
        LoginLandingScreen(
            storeCode = storeCode,
            isSubmitting = busy,
            selectedLanguage = selectedLanguage,
            onLanguageSelected = { selectedLanguage = it },
            onStoreCodeChange = { storeCode = it.trimStart().uppercase().take(32); error = null },
            onSubmit = {
                scope.launch {
                    busy = true
                    error = null
                    try {
                        val resolved = client.resolveStore(storeCode.trim())
                        store = resolved
                        if (resolved.branches.isEmpty()) {
                            error = "ร้านนี้ยังไม่มีสาขาที่เปิดใช้งานในระบบจริง"
                        } else {
                            branch = null
                            step = "branch"
                        }
                    } catch (e: WebPosApiException) {
                        error = when (e.code) {
                            "store_not_found" ->
                                "ไม่พบรหัสร้านในระบบจริง หากเป็นรหัสตัวเลขลูกค้า กรุณาตรวจสอบให้ครบ 6 หลัก"
                            "rate_limited" ->
                                "ตรวจสอบรหัสร้านบ่อยเกินไป กรุณารอสักครู่แล้วลองใหม่"
                            "non_json_response", "missing_data" ->
                                "เซิร์ฟเวอร์ยังไม่ตอบข้อมูล POS ที่ถูกต้อง กรุณาตรวจสอบการเชื่อมต่อ"
                            else -> "ตรวจสอบรหัสร้านไม่สำเร็จ: ${e.code} (${e.status})"
                        }
                    } catch (e: Exception) {
                        error = "เชื่อมต่อระบบร้านค้าไม่สำเร็จ กรุณาตรวจสอบอินเทอร์เน็ตและลองใหม่"
                    } finally {
                        busy = false
                    }
                }
            },
            errorText = error
        )
        return
    }

    when (step) {
        "branch" -> LiveBranchChoiceScreen(
            storeName = store?.name.orEmpty(),
            branches = store?.branches.orEmpty(),
            selected = branch,
            onSelect = { branch = it; error = null },
            onNext = {
                val chosen = branch
                if (chosen != null) {
                    deviceCode = storePrefs.getString("device_${chosen.id}", "") ?: ""
                    pin = ""
                    showPin = false
                    error = null
                    step = "pin"
                }
            },
            onBack = { step = "store" }
        )
        "pin" -> LiveEmployeeLoginScreen(
            branchName = branch?.name.orEmpty(),
            deviceCode = deviceCode,
            employeePin = pin,
            showPin = showPin,
            busy = busy,
            error = error,
            onDeviceCode = { deviceCode = it.uppercase().take(64); error = null },
            onEmployeePin = { pin = it.filter(Char::isDigit).take(12); error = null },
            onTogglePin = { showPin = !showPin },
            onBack = { pin = ""; step = "branch" },
            onLogin = {
                val chosen = branch
                if (chosen != null && !busy) {
                    val submittedPin = pin
                    pin = ""
                    showPin = false
                    scope.launch {
                        busy = true
                        error = null
                        try {
                            val verified = client.verifyPin(
                                storeCode.trim(), chosen.id, deviceCode.trim(), submittedPin
                            )
                            storePrefs.edit()
                                .putString("device_${chosen.id}", verified.deviceCode)
                                .apply()
                            session = verified
                            refreshCatalog(verified)
                        } catch (e: WebPosApiException) {
                            error = when (e.code) {
                                "invalid_pin", "invalid_credentials", "pin_invalid" ->
                                    "รหัส PIN ไม่ถูกต้อง กรุณาลองใหม่"
                                "login_method_not_allowed" ->
                                    "สาขานี้ยังไม่อนุญาตให้เข้าสู่ระบบด้วย PIN"
                                "device_not_found", "device_not_registered" ->
                                    "ไม่พบรหัสเครื่อง POS ที่ลงทะเบียนกับสาขานี้"
                                else -> "ยืนยันพนักงานไม่สำเร็จ: ${e.code} (${e.status})"
                            }
                        } catch (e: Exception) {
                            error = "เข้าสู่ระบบไม่สำเร็จ กรุณาตรวจสอบเครือข่ายและสิทธิ์พนักงาน"
                        } finally {
                            busy = false
                        }
                    }
                }
            }
        )
        "shift" -> session?.let { verified ->
            LiveShiftScreen(
                session = verified,
                busy = busy,
                error = error,
                onOpen = {
                    if (!busy) {
                        scope.launch {
                            busy = true
                            error = null
                            try {
                                val opened = client.openShift()
                                session = opened
                                refreshCatalog(opened)
                            } catch (e: WebPosApiException) {
                                error = "เปิดกะไม่สำเร็จ: ${e.code} (${e.status})"
                            } catch (e: Exception) {
                                error = "เปิดกะไม่สำเร็จ กรุณาตรวจสอบการเชื่อมต่อ"
                            } finally {
                                busy = false
                            }
                        }
                    }
                },
                onRefresh = { refreshCatalog(verified) },
                onBack = { session = null; catalog = emptyList(); step = "store" }
            )
        }
        "review" -> LiveReviewScreen(
            message = unresolved,
            onRefresh = { session?.let { refreshCatalog(it) } },
            onBack = { session = null; catalog = emptyList(); step = "store" }
        )
        "counter" -> session?.let { verified ->
            LiveCounterScreen(
                session = verified,
                productCount = catalog.size,
                onEnter = { step = "mode" },
                onRefresh = { refreshCatalog(verified) },
                onBack = { session = null; catalog = emptyList(); step = "store" }
            )
        }
        "mode" -> HomeModeScreen(
            onSelectMode = { mode ->
                if (mode == MockSaleMode.Takeaway) step = "sale"
            },
            allowDineIn = false,
            onBack = { step = "counter" }
        )
        "sale" -> session?.let { verified ->
            MobilePosSalePreview(
                mode = MockSaleMode.Takeaway,
                branchName = verified.branchName,
                counterCode = verified.deviceCode,
                onBack = { step = "mode" },
                liveClient = client,
                liveSession = verified,
                liveProducts = catalog,
                liveJournal = journal
            )
        }
    }
}
