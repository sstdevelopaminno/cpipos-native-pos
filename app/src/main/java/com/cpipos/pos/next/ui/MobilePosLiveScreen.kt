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
    var language by remember { mutableStateOf(LoginLanguage.Thai) }
    var store by remember { mutableStateOf<WebStore?>(null) }
    var branch by remember { mutableStateOf<WebBranch?>(null) }
    var employeeCode by remember { mutableStateOf("") }
    var showEmployeeCode by remember { mutableStateOf(false) }
    var employee by remember {
        mutableStateOf<com.cpipos.pos.next.core.webpos.WebEmployee?>(null)
    }
    var availableDevices by remember {
        mutableStateOf<com.cpipos.pos.next.core.webpos.WebCashierDevices?>(null)
    }
    var selectedDevice by remember {
        mutableStateOf<com.cpipos.pos.next.core.webpos.WebCashierDevice?>(null)
    }
    var session by remember { mutableStateOf<WebPosSession?>(null) }
    var catalog by remember { mutableStateOf<List<WebPosProduct>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var unresolved by remember { mutableStateOf("") }

    fun goToStore() {
        employeeCode = ""
        employee = null
        availableDevices = null
        selectedDevice = null
        session = null
        catalog = emptyList()
        error = null
        step = "store"
    }

    suspend fun refreshCatalog(verified: WebPosSession) {
            busy = true
            error = null
            try {
                val current = client.getSession()
                check(
                    current.id == verified.id &&
                        current.branchId == verified.branchId &&
                        current.deviceCode == verified.deviceCode
                ) { "Session changed" }
                session = current
                when {
                    !current.canSell -> {
                        error = "พนักงานไม่มีสิทธิ์ขาย กรุณาติดต่อผู้จัดการร้าน"
                        step = "shift"
                    }
                    current.shiftId == null -> step = "shift"
                    else -> {
                        val pending = journal.unresolved(current)
                        if (pending.isNotEmpty()) {
                            unresolved = "พบ ${pending.size} บิลค้างตรวจสอบ " +
                                "(อ้างอิง ${pending.first().id.take(8)}) " +
                                "ตรวจสอบการชำระเงินที่ CpIPOS Web ก่อนขายอีกครั้ง"
                            step = "review"
                        } else {
                            catalog = client.products(current)
                            step = "mode"
                        }
                    }
                }
            } catch (e: Exception) {
                error = "ตรวจสอบกะหรือสินค้าไม่สำเร็จ: ${e.message?.take(80)}"
                step = "shift"
            } finally {
                busy = false
            }
    }

    when (step) {
        "store" -> LoginLandingScreen(
            storeCode = storeCode,
            isSubmitting = busy,
            selectedLanguage = language,
            onLanguageSelected = { language = it },
            onStoreCodeChange = {
                storeCode = it.trimStart().uppercase().take(32)
                error = null
            },
            onSubmit = {
                if (!busy) scope.launch {
                    busy = true
                    error = null
                    try {
                        val resolved = client.startStoreEntry(storeCode.trim())
                        store = resolved
                        branch = null
                        employeeCode = ""
                        employee = null
                        availableDevices = null
                        selectedDevice = null
                        session = null
                        if (resolved.branches.isEmpty()) {
                            error = "ร้านนี้ยังไม่มีสาขาที่เปิดใช้งานในระบบจริง"
                        } else {
                            step = "branch"
                        }
                    } catch (e: WebPosApiException) {
                        error = when (e.code) {
                            "store_not_found" ->
                                "ไม่พบรหัสร้านในระบบจริง กรุณาตรวจสอบรหัสจากหลังบ้าน"
                            "rate_limited" ->
                                "ตรวจสอบบ่อยเกินไป กรุณารอสักครู่แล้วลองใหม่"
                            else ->
                                "ตรวจสอบรหัสร้านไม่สำเร็จ: ${e.code} (${e.status})"
                        }
                    } catch (_: Exception) {
                        error = "เชื่อมต่อระบบร้านค้าไม่สำเร็จ กรุณาตรวจสอบอินเทอร์เน็ต"
                    } finally {
                        busy = false
                    }
                }
            },
            errorText = error
        )
        "branch" -> LiveBranchChoiceScreen(
            storeName = store?.name.orEmpty(),
            branches = store?.branches.orEmpty(),
            selected = branch,
            onSelect = { branch = it; error = null },
            onNext = {
                val selected = branch
                if (selected != null && !busy) scope.launch {
                    busy = true
                    error = null
                    try {
                        client.selectEntryBranch(selected.id)
                        employeeCode = ""
                        showEmployeeCode = false
                        employee = null
                        step = "employee"
                    } catch (e: WebPosApiException) {
                        error = "เลือกสาขาไม่สำเร็จ: ${e.code} (${e.status})"
                    } catch (_: Exception) {
                        error = "เลือกสาขาไม่สำเร็จ กรุณาตรวจสอบอินเทอร์เน็ต"
                    } finally {
                        busy = false
                    }
                }
            },
            onBack = { goToStore() },
            busy = busy,
            error = error
        )
        "employee" -> LiveEmployeeLoginScreen(
            branchName = branch?.name.orEmpty(),
            employeeCode = employeeCode,
            showCode = showEmployeeCode,
            selectedLanguage = language,
            busy = busy,
            error = error,
            onLanguageSelected = { language = it },
            onEmployeeCode = {
                employeeCode = it.filter(Char::isDigit).take(32)
                error = null
            },
            onToggleCode = { showEmployeeCode = !showEmployeeCode },
            onBack = {
                employeeCode = ""
                showEmployeeCode = false
                employee = null
                step = "branch"
            },
            onVerify = {
                if (!busy && employeeCode.isNotBlank()) {
                    val submittedCode = employeeCode
                    employeeCode = ""
                    showEmployeeCode = false
                    scope.launch {
                        busy = true
                        error = null
                        try {
                            val verified = client.verifyEmployeeCode(submittedCode)
                            val devices = client.listEntryDevices()
                            check(devices.branchId == branch?.id) {
                                "Device branch did not match the selected branch"
                            }
                            employee = verified
                            availableDevices = devices
                            selectedDevice = null
                            step = "device"
                        } catch (e: WebPosApiException) {
                            error = when (e.code) {
                                "employee_not_found", "invalid_employee_code" ->
                                    "ไม่พบรหัสพนักงานในสาขานี้ กรุณาตรวจสอบจากระบบหลังบ้าน"
                                "rate_limited" ->
                                    "ยืนยันรหัสหลายครั้งเกินไป กรุณารอสักครู่"
                                "missing_employee_context" ->
                                    "เซสชันยืนยันพนักงานหมดอายุ กรุณาย้อนกลับเลือกสาขาใหม่"
                                else ->
                                    "ยืนยันพนักงานไม่สำเร็จ: ${e.code} (${e.status})"
                            }
                        } catch (_: Exception) {
                            error = "ยืนยันพนักงานหรือโหลดเครื่องไม่สำเร็จ กรุณาลองอีกครั้ง"
                        } finally {
                            busy = false
                        }
                    }
                }
            }
        )
        "device" -> LiveDeviceChoiceScreen(
            branchName = availableDevices?.branchName
                ?.takeIf { it.isNotBlank() } ?: branch?.name.orEmpty(),
            employeeName = employee?.name.orEmpty(),
            devices = availableDevices?.devices.orEmpty(),
            canOverrideInUse = availableDevices?.canOverrideInUse == true,
            selected = selectedDevice,
            busy = busy,
            error = error,
            onSelect = { selectedDevice = it; error = null },
            onOpen = {
                val chosen = selectedDevice
                val who = employee
                val where = branch
                if (!busy && chosen != null && who != null && where != null) {
                    scope.launch {
                        busy = true
                        error = null
                        try {
                            val verified = client.selectEntryDevice(
                                branchId = where.id,
                                employeeId = who.id,
                                selected = chosen
                            )
                            session = verified
                            refreshCatalog(verified)
                        } catch (e: WebPosApiException) {
                            error = when (e.code) {
                                "device_in_use" ->
                                    "เครื่องนี้กำลังใช้งานอยู่ กรุณาเลือกเครื่องอื่นหรือแจ้งผู้จัดการ"
                                "device_scope_denied" ->
                                    "พนักงานไม่ได้รับสิทธิ์ใช้งานเครื่องนี้"
                                "device_disabled", "device_offline" ->
                                    "เครื่องแคชเชียร์ไม่พร้อมใช้งาน"
                                else ->
                                    "เปิดเครื่องไม่สำเร็จ: ${e.code} (${e.status})"
                            }
                        } catch (_: Exception) {
                            error = "เปิดเครื่องไม่สำเร็จ กรุณาตรวจสอบเครือข่าย"
                        } finally {
                            busy = false
                        }
                    }
                }
            },
            onBack = {
                // Re-selecting a branch resets the server-side employee stage.
                employee = null
                availableDevices = null
                selectedDevice = null
                step = "branch"
            }
        )
        "shift" -> session?.let { verified ->
            LiveShiftScreen(
                session = verified,
                busy = busy,
                error = error,
                onOpen = {
                    if (!busy) scope.launch {
                        busy = true
                        error = null
                        try {
                            val opened = client.openShift()
                            session = opened
                            refreshCatalog(opened)
                        } catch (e: WebPosApiException) {
                            error = "เปิดกะไม่สำเร็จ: ${e.code} (${e.status})"
                        } catch (_: Exception) {
                            error = "เปิดกะไม่สำเร็จ กรุณาตรวจสอบการเชื่อมต่อ"
                        } finally {
                            busy = false
                        }
                    }
                },
                onRefresh = {
                    if (!busy) scope.launch { refreshCatalog(verified) }
                },
                onBack = { goToStore() }
            )
        }
        "review" -> LiveReviewScreen(
            message = unresolved,
            onRefresh = {
                val verified = session
                if (verified != null && !busy) scope.launch { refreshCatalog(verified) }
            },
            onBack = { goToStore() }
        )
        "mode" -> HomeModeScreen(
            onSelectMode = { mode ->
                if (mode == MockSaleMode.Takeaway) step = "sale"
            },
            allowDineIn = false,
            onBack = { step = "shift" }
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
