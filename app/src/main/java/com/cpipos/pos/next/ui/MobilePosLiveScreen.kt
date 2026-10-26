package com.cpipos.pos.next.ui

import android.util.Log
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.cpipos.pos.next.core.offline.DemoSaleReceipt
import com.cpipos.pos.next.core.webpos.LiveSaleJournal
import com.cpipos.pos.next.core.webpos.LiveSaleReconciler
import com.cpipos.pos.next.core.webpos.NativeHeldBillStore
import java.util.UUID
import com.cpipos.pos.next.core.webpos.WebBranch
import com.cpipos.pos.next.core.webpos.WebPosClient
import com.cpipos.pos.next.core.webpos.WebPosProduct
import com.cpipos.pos.next.core.webpos.WebPosSession
import com.cpipos.pos.next.core.webpos.WebPosEntitlements
import com.cpipos.pos.next.core.webpos.WebStore
import com.cpipos.pos.next.core.webpos.WebPosApiException
import com.cpipos.pos.next.printing.DemoReceiptPrinter
import com.cpipos.pos.next.core.push.NativeFcmRegistration
import com.cpipos.pos.next.core.push.NativeQrBackgroundService
import com.cpipos.pos.next.core.print.NativePrintRuntime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private val liveBlue = Color(0xFF176DED)
private val liveInk = Color(0xFF152749)

@Composable
internal fun MobilePosLiveScreen(client: WebPosClient) {
    val ctx = LocalContext.current
    val journal = remember(ctx.applicationContext) { LiveSaleJournal(ctx.applicationContext) }
    val heldBillStore = remember(ctx.applicationContext) { NativeHeldBillStore(ctx.applicationContext) }
    val reconciler = remember(client, journal) { LiveSaleReconciler(client, journal) }
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
    var entitlements by remember { mutableStateOf<WebPosEntitlements?>(null) }
    var entitlementCheckBusy by remember { mutableStateOf(false) }
    var packageLockTarget by remember { mutableStateOf<String?>(null) }
    var catalog by remember { mutableStateOf<List<WebPosProduct>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var unresolved by remember { mutableStateOf("") }
    var recoveredReceipt by remember { mutableStateOf<DemoSaleReceipt?>(null) }
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var browsingWithoutShift by remember { mutableStateOf(false) }
    var showShiftRequiredDialog by remember { mutableStateOf(false) }
    var showSalePermissionDialog by remember { mutableStateOf(false) }
    // Single workspace for the authenticated register. Switching to the mode
    // chooser never disposes its cart, entered cash or checkout step.
    val takeaway = remember(session?.id) { TakeawayWorkspace() }
    val dineWorkspace = remember(session?.id) { DiningWorkspace() }
    var selectedDiningTable by remember(session?.id) { mutableStateOf<com.cpipos.pos.next.core.webpos.WebDiningTable?>(null) }
    var mainTab by remember { mutableStateOf("history") }
    var tabRevision by remember { mutableStateOf(0) }
    val fcmNavigationRevision = NativeFcmRegistration.navigationRevision.intValue

    fun tabPackageAllowed(tab: String, access: WebPosEntitlements? = entitlements): Boolean {
        val snapshot = access ?: return true
        return when (tab) {
            "sale" -> snapshot.allows("core_pos_sales", "main.sales")
            "report" -> snapshot.allows("advanced_sales_reports", "main.sales_list")
            "stock" -> snapshot.allows("stock_management", "more.stock")
            // Native keeps Open/Close Shift inside More, so the More container
            // itself stays reachable and each child is gated independently.
            "history" -> true
            "settings" -> snapshot.allows("core_pos_sales", "main.settings")
            else -> true
        }
    }

    fun tabPackageLabel(tab: String): String = when (tab) {
        "sale" -> "ขาย"
        "report" -> "รายงาน / รายการขาย"
        "stock" -> "สินค้า / สต๊อก"
        "settings" -> "ตั้งค่า"
        else -> "เมนูนี้"
    }

    fun openTab(tab: String) {
        if (tab == "sale") {
            step = "mode"
        } else {
            mainTab = tab
            tabRevision += 1
            step = "tab"
        }
    }

    fun navigateTab(tab: String) {
        if (takeaway.saving.value || dineWorkspace.drafts.values.any { it.busy }) return
        if (tabPackageAllowed(tab)) {
            openTab(tab)
            return
        }

        val current = session
        if (current == null || entitlementCheckBusy) {
            if (current == null) packageLockTarget = tabPackageLabel(tab)
            return
        }

        // A cached denial is rechecked once against the no-store server path.
        // This lets a just-changed IT entitlement take effect immediately
        // without aggressive polling or blocking the Compose UI thread.
        scope.launch {
            entitlementCheckBusy = true
            try {
                val latest = client.posEntitlements(current, forceFresh = true)
                if (session?.id != current.id || session?.branchId != current.branchId) return@launch
                entitlements = latest
                if (tabPackageAllowed(tab, latest)) {
                    openTab(tab)
                } else {
                    packageLockTarget = tabPackageLabel(tab)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Log.w("CpIPOSEntitlements", "Fresh navigation entitlement check failed: " +
                    failure.javaClass.simpleName)
                packageLockTarget = tabPackageLabel(tab)
            } finally {
                entitlementCheckBusy = false
            }
        }
    }

    fun goToStore() {
        NativePrintRuntime.stop()
        NativeQrBackgroundService.stop(ctx)
        NativeFcmRegistration.clearScope(ctx)
        employeeCode = ""
        employee = null
        availableDevices = null
        selectedDevice = null
        session = null
        entitlements = null
        entitlementCheckBusy = false
        packageLockTarget = null
        catalog = emptyList()
        error = null
        recoveredReceipt = null
        browsingWithoutShift = false
        showShiftRequiredDialog = false
        showSalePermissionDialog = false
        step = "store"
    }

    suspend fun refreshCatalog(verified: WebPosSession) {
            busy = true
            error = null
            try {
                val current = client.synchronizedShiftSession()
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
                            val loadedProducts = client.products(current)
                            catalog = loadedProducts
                            step = "mode"
                            // Images come from the same authenticated POS Web media API.
                            // Optional media must never delay cashier entry or a sale.
                            scope.launch {
                                try {
                                    val withImages = client.productsWithMedia(loadedProducts)
                                    if (session?.id == current.id &&
                                        session?.branchId == current.branchId &&
                                        (step == "mode" || step == "sale")
                                    ) {
                                        catalog = withImages
                                    }
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (failure: Exception) {
                                    Log.w(
                                        "CpIPOSMedia",
                                        "Product media unavailable (" + failure.javaClass.simpleName + "); keeping sale catalog"
                                    )
                                }
                            }
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

    // IT Admin controls packages and menu policies through the shared CpiPOS-001
    // control plane. Native reads only the authenticated CpIPOS feature endpoint;
    // it never receives privileged IT/Supabase credentials.
    LaunchedEffect(client, session?.id, session?.branchId) {
        val active = session
        if (active == null) {
            entitlements = null
            return@LaunchedEffect
        }
        var firstLoad = true
        while (session?.id == active.id && session?.branchId == active.branchId) {
            try {
                val latest = client.posEntitlements(active, forceFresh = firstLoad)
                if (session?.id == active.id && session?.branchId == active.branchId) {
                    entitlements = latest
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                // UI snapshot failure must not invent access. Existing server-side
                // feature gates remain authoritative for every protected API.
                Log.w("CpIPOSEntitlements", "Feature snapshot unavailable: " +
                    failure.javaClass.simpleName)
            }
            firstLoad = false
            kotlinx.coroutines.delay(60_000L)
        }
    }

    // Keep QR operations reachable when the cashier leaves/swipes away the UI.
    // FCM remains preferred when configured; this foreground-service poller is
    // a zero-cost fallback and stops automatically on POS-session revocation.
    LaunchedEffect(session?.id) {
        val active = session
        if (active != null) {
            NativeQrBackgroundService.start(ctx)
            NativePrintRuntime.start(ctx, client, active)
        } else {
            NativePrintRuntime.stop()
            NativeQrBackgroundService.stop(ctx)
        }
    }

    // Firebase token registration is strictly POS-session scoped. Firebase may
    // be absent in CI or until the owner supplies app/google-services.json.
    // Re-register periodically to recover token rotation / offline windows.
    LaunchedEffect(client, session?.id) {
        val active = session ?: return@LaunchedEffect
        while (session?.id == active.id) {
            try {
                NativeFcmRegistration.register(ctx, client, active)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Log.w("CpIPOSFCM", "Registration unavailable: " +
                    failure.javaClass.simpleName)
            }
            kotlinx.coroutines.delay(60 * 60 * 1000L)
        }
    }

    // OS notification opens the existing POS with a scoped, verified table ID.
    // Never discard an unresolved sale or draft while navigating from FCM.
    LaunchedEffect(client, session?.id, fcmNavigationRevision, step) {
        val active = session ?: return@LaunchedEffect
        val target = NativeFcmRegistration.pendingTable(ctx, active) ?: return@LaunchedEffect
        if (step !in setOf("mode", "sale", "dine", "dine_order", "tab")) return@LaunchedEffect
        if (busy || takeaway.saving.value || dineWorkspace.drafts.values.any {
            it.busy || it.uncertain || it.items.isNotEmpty() || it.pendingQuantities.isNotEmpty()
        }) return@LaunchedEffect
        if (runCatching { journal.unresolved(active).isEmpty() }.getOrDefault(false).not())
            return@LaunchedEffect
        val floor = try {
            client.diningTables(active)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        if (floor != null && session?.id == active.id) {
            val table = floor.tables.firstOrNull {
                it.id == target && it.sessionId != null &&
                    it.status in setOf("occupied", "ordering", "pending_payment")
            }
            NativeFcmRegistration.clearPendingTable(ctx)
            if (table != null) {
                selectedDiningTable = table
                step = "dine_order"
            } else {
                step = "dine"
            }
        }
    }

    // Resume the same backend-issued POS identity after process restart.
    // Server still validates tenant, branch, device, employee and session lease.
    LaunchedEffect(client) {
        if (client.hasStoredPosSession() && step == "store") {
            step = "restoring"
            try {
                val restored = client.synchronizedShiftSession()
                session = restored
                refreshCatalog(restored)
            } catch (failure: WebPosApiException) {
                if (failure.status == 401 || failure.status == 403) client.clearStoredPosSession()
                session = null
                error = null
                step = "store"
            } catch (_: Exception) {
                session = null
                step = "store"
            }
        }
    }

    // CpIPOS Web owns open/closed shift state. Read it while cashier is
    // browsing; never re-issue an open/close request and never interrupt a
    // payment currently being committed to its original UUID.
    LaunchedEffect(client, session?.id, step) {
        while (step in setOf("shift", "mode", "sale", "dine", "dine_order", "tab")) {
            kotlinx.coroutines.delay(6000L)
            val previous = session ?: continue
            if (busy || takeaway.saving.value || dineWorkspace.drafts.values.any { it.busy }) continue
            val remote = try { client.synchronizedShiftSession() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { continue }
            if (remote.id != previous.id ||
                remote.branchId != previous.branchId ||
                remote.deviceCode != previous.deviceCode) continue
            if (remote.shiftId != previous.shiftId) {
                val pending = runCatching { journal.unresolved(previous) }.getOrNull()
                session = remote
                if (pending == null || pending.isNotEmpty()) {
                    unresolved = "กะขายเปลี่ยนจาก POS หลัก และมีรายการที่ต้องตรวจสอบ " +
                        "กรุณา Reconcile บิลเดิมก่อนทำรายการต่อ"
                    step = "review"
                } else if (remote.shiftId == null) {
                    error = null
                    if (!browsingWithoutShift) {
                        step = "shift"
                    } else if (step in setOf("sale", "dine", "dine_order")) {
                        // Defensive fail-closed guard: browse mode may use all
                        // non-sale menus, but a closed shift may never retain a
                        // transaction screen.
                        step = "mode"
                        showShiftRequiredDialog = true
                    }
                } else {
                    // A tablet/POS on the same device scope opened or
                    // replaced the shift: reload catalog then select mode.
                    browsingWithoutShift = false
                    refreshCatalog(remote)
                }
            }
        }
    }

    when (step) {
        "restoring" -> CpiposSessionRestoringScreen()
        "switching" -> CpiposSessionRestoringScreen(
            headline = "กำลังเปลี่ยนเซสชัน",
            subtitle = "เตรียมรายการสาขาและเครื่องขายจาก CpIPOS..."
        )
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
                onOpen = { openingCash ->
                    if (!busy) scope.launch {
                        busy = true
                        error = null
                        try {
                            // Reuses an existing shared shift on the same
                            // tenant/branch/device instead of creating another.
                            val opened = client.openShift(openingCash)
                            session = opened
                            browsingWithoutShift = false
                            refreshCatalog(opened)
                        } catch (failure: Exception) {
                            // An uncertain POST response may have created the shift.
                            // Read actual server state, never send a duplicate POST.
                            val observed = runCatching { client.synchronizedShiftSession() }.getOrNull()
                            if (observed != null && observed.id == verified.id &&
                                observed.branchId == verified.branchId &&
                                observed.deviceCode == verified.deviceCode &&
                                observed.shiftId != null) {
                                session = observed
                                browsingWithoutShift = false
                                refreshCatalog(observed)
                            } else {
                                error = if (failure is WebPosApiException)
                                    "เปิดกะยังไม่สำเร็จ: ${failure.code} (${failure.status})"
                                else "ยังยืนยันการเปิดกะไม่ได้ โปรดตรวจสอบสถานะกะบน CpIPOS"
                            }
                        } finally {
                            busy = false
                        }
                    }
                },
                onBrowseWithoutShift = {
                    if (!busy) {
                        browsingWithoutShift = true
                        error = null
                        step = "mode"
                    }
                },
                onBack = { goToStore() }
            )
        }
        "review" -> LiveReviewScreen(
            message = unresolved,
            busy = busy,
            onReconcile = {
                val verified = session
                if (verified != null && !busy) {
                    scope.launch {
                        busy = true
                        error = null
                        try {
                            val report = reconciler.reconcile(verified)
                            // Clear only drafts whose original sale UUID is confirmed
                            // completed or cancelled in the durable journal. Uncertain
                            // attempts keep their parked snapshots for safe recovery.
                            runCatching {
                                val resolved = heldBillStore.all(verified).filter {
                                    journal.stage(verified, it.saleId) in setOf("confirmed", "resolved_cancelled")
                                }
                                resolved.forEach { heldBillStore.remove(verified, it.id) }
                                if (resolved.any { it.id == takeaway.activeHeldId.value }) {
                                    takeaway.cart.clear()
                                    takeaway.discount.value = PosDiscount()
                                    takeaway.activeHeldId.value = null
                                    takeaway.activeBillId.value = UUID.randomUUID().toString()
                                    takeaway.showCart.value = false
                                    takeaway.payment.value = null
                                }
                            }.onFailure {
                                // A paid sale must still remain blocked by journal.stage.
                                Log.e("CpIPOS", "Could not clean up resolved local held bill", it)
                            }
                            recoveredReceipt = report.recoveredReceipts.lastOrNull()
                            unresolved = report.notes.joinToString("\n")
                                .ifBlank { "ตรวจสอบบิลค้างกับ Server แล้ว" }
                            if (report.remaining.isEmpty()) {
                                refreshCatalog(verified)
                            } else {
                                step = "review"
                            }
                        } catch (failure: Exception) {
                            unresolved = "Reconcile ไม่สำเร็จ: " +
                                (failure.message ?: "กรุณาตรวจสอบเครือข่าย").take(100)
                            step = "review"
                        } finally {
                            busy = false
                        }
                    }
                }
            },
            onBack = { goToStore() }
        )
        "mode" -> HomeModeScreen(
            onSelectMode = { mode ->
                val current = session
                if (current == null || current.shiftId == null) {
                    showShiftRequiredDialog = true
                } else if (!current.canSell) {
                    showSalePermissionDialog = true
                } else {
                    fun modeAllowed(access: WebPosEntitlements?): Boolean =
                        access == null ||
                            if (mode == MockSaleMode.Takeaway) access.takeawayAllowed()
                            else access.dineInAllowed()

                    fun enterMode() {
                        if (mode == MockSaleMode.Takeaway) {
                            scope.launch {
                                val pending = runCatching { journal.unresolved(current) }.getOrNull()
                                when {
                                    pending == null -> {
                                        unresolved = "อ่านสถานะบิลค้างไม่ได้ กรุณาตรวจสอบก่อนขาย"
                                        step = "review"
                                    }
                                    pending.isNotEmpty() -> {
                                        unresolved = "พบบิลค้าง ${pending.size} รายการ โปรด Reconcile ก่อนขายต่อ"
                                        step = "review"
                                    }
                                    else -> step = "sale"
                                }
                            }
                        } else {
                            step = "dine"
                        }
                    }

                    if (modeAllowed(entitlements)) {
                        enterMode()
                    } else if (!entitlementCheckBusy) {
                        scope.launch {
                            entitlementCheckBusy = true
                            try {
                                val latest = client.posEntitlements(current, forceFresh = true)
                                if (session?.id != current.id ||
                                    session?.branchId != current.branchId
                                ) return@launch
                                entitlements = latest
                                if (modeAllowed(latest)) {
                                    enterMode()
                                } else {
                                    packageLockTarget =
                                        if (mode == MockSaleMode.Takeaway) "โหมดขายกลับบ้าน"
                                        else "โหมดขายนั่งโต๊ะ"
                                }
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (failure: Exception) {
                                Log.w(
                                    "CpIPOSEntitlements",
                                    "Fresh sales-mode entitlement check failed: " +
                                        failure.javaClass.simpleName
                                )
                                packageLockTarget =
                                    if (mode == MockSaleMode.Takeaway) "โหมดขายกลับบ้าน"
                                    else "โหมดขายนั่งโต๊ะ"
                            } finally {
                                entitlementCheckBusy = false
                            }
                        }
                    }
                }
            },
            allowDineIn = true,
            onLogout = { if (!busy) showLogoutConfirm = true },
            onMainMenu = { tab -> navigateTab(tab) }
        )
        "dine" -> session?.let { verified ->
            NativeDiningTableScreen(
                client = client,
                session = verified,
                onBack = { step = "mode" },
                onSelectMode = { step = "mode" },
                onMainMenu = { tab -> navigateTab(tab) },
                onEnterTable = { table ->
                    selectedDiningTable = table
                    step = "dine_order"
                },
                canManageTable = { tableId ->
                    val draft = dineWorkspace.drafts[tableId]
                    draft == null || (!draft.busy && !draft.uncertain &&
                        draft.items.isEmpty() && draft.pendingQuantities.isEmpty() &&
                        draft.paymentMethod == null && draft.paymentRequestId == null)
                }
            )
        }
        "dine_order" -> session?.let { verified ->
            selectedDiningTable?.let { table ->
                val draft = dineWorkspace.drafts.getOrPut(table.id) { DiningDraft() }
                NativeDiningOrderScreen(
                    client = client,
                    session = verified,
                    table = table,
                    products = catalog,
                    draft = draft,
                    onTables = { step = "dine" },
                    onMainMenu = { tab -> navigateTab(tab) }
                )
            }
        }
        "tab" -> session?.let { verified ->
            androidx.compose.runtime.key(verified.id, mainTab, tabRevision) {
                NativeMainTabScreen(
                    tab = mainTab,
                    client = client,
                    session = verified,
                    products = catalog,
                    entitlements = entitlements,
                    onSessionChanged = { updated ->
                        val previousShiftId = session?.shiftId
                        session = updated
                        browsingWithoutShift = updated.shiftId == null
                        if (previousShiftId == null && updated.shiftId != null && updated.canSell) {
                            // Opening a shift from More must not force navigation away from
                            // the shift table, but preload the canonical catalog so Sale is
                            // ready when the cashier later chooses it.
                            scope.launch {
                                runCatching { client.products(updated) }
                                    .onSuccess { latest ->
                                        if (session?.id == updated.id && session?.shiftId == updated.shiftId) {
                                            catalog = latest
                                            runCatching { client.productsWithMedia(latest) }
                                                .onSuccess { withMedia ->
                                                    if (session?.id == updated.id &&
                                                        session?.shiftId == updated.shiftId
                                                    ) catalog = withMedia
                                                }
                                        }
                                    }
                            }
                        }
                    },
                    onTab = { destination ->
                        if (destination == "sale" && verified.shiftId == null && !busy) {
                            // Browse-without-shift mode can return to the sale-mode chooser,
                            // but actual transaction modes remain guarded there.
                            step = "mode"
                        } else if (mainTab == "stock" && destination == "sale" && !busy) {
                            // Product editors may change the canonical server price. Refresh
                            // before reopening the sale screen; never sell from stale memory.
                            scope.launch {
                                busy = true
                                try {
                                    val latest = client.products(verified)
                                    catalog = latest
                                    runCatching { client.productsWithMedia(latest) }
                                        .onSuccess { catalog = it }
                                    step = "mode"
                                } catch (failure: Exception) {
                                    error = "รีเฟรชราคาสินค้าไม่สำเร็จ กรุณาตรวจสอบเครือข่ายก่อนขาย"
                                    Log.e("CpIPOS", "Sale catalog refresh failed", failure)
                                } finally { busy = false }
                            }
                        } else if (!busy) {
                            navigateTab(destination)
                        }
                    }
                )
            }
        }
        "sale" -> session?.let { verified ->
            MobilePosSalePreview(
                mode = MockSaleMode.Takeaway,
                branchName = verified.branchName,
                counterCode = verified.deviceCode,
                onBack = { step = "mode" },
                // Return to the existing mode chooser; keep the authenticated POS session.
                onSelectMode = { step = "mode" },
                liveClient = client,
                liveSession = verified,
                liveProducts = catalog,
                liveJournal = journal,
                workspace = takeaway,
                onMainMenu = { tab -> navigateTab(tab) }
            )
        }
    }

    NativePackageLockDialog(
        targetLabel = packageLockTarget,
        onDismiss = { packageLockTarget = null }
    )

    if (showSalePermissionDialog) {
        AlertDialog(
            onDismissRequest = { if (!busy) showSalePermissionDialog = false },
            shape = RoundedCornerShape(24.dp),
            title = {
                Text(
                    "ไม่มีสิทธิ์ขาย",
                    fontWeight = FontWeight.ExtraBold,
                    color = liveInk
                )
            },
            text = {
                Text(
                    "บัญชีพนักงานนี้สามารถเข้าเมนูที่ได้รับสิทธิ์ได้ แต่ไม่สามารถเข้าโหมดขายได้ " +
                        "กรุณาติดต่อ Owner หรือ Manager เพื่อกำหนดสิทธิ์ขาย",
                    color = Color(0xFF66758D),
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = { showSalePermissionDialog = false },
                    enabled = !busy,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = liveBlue)
                ) {
                    Text("รับทราบ", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (showShiftRequiredDialog) {
        AlertDialog(
            onDismissRequest = { if (!busy) showShiftRequiredDialog = false },
            shape = RoundedCornerShape(24.dp),
            title = {
                Text(
                    "กรุณาเปิดกะก่อน",
                    fontWeight = FontWeight.ExtraBold,
                    color = liveInk
                )
            },
            text = {
                Text(
                    "ขณะนี้คุณเข้าแอปในโหมดไม่เปิดกะ จึงดูเมนูหลัก สินค้า รายงาน เพิ่มเติม และตั้งค่าได้ " +
                        "แต่ยังไม่สามารถเข้าโหมดขาย “กลับบ้าน” หรือ “นั่งโต๊ะ” ได้",
                    color = Color(0xFF66758D),
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showShiftRequiredDialog = false
                        step = "shift"
                    },
                    enabled = !busy,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = liveBlue)
                ) {
                    Text("ไปเปิดกะ", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showShiftRequiredDialog = false },
                    enabled = !busy
                ) {
                    Text("อยู่ในเมนูต่อ")
                }
            }
        )
    }

    if (showLogoutConfirm) {
        val verified = session
        // Fail closed: an unresolved live sale must NEVER be lost when
        // selecting a different branch/device or logging out.
        val pendingCount = if (verified == null) -1 else
            runCatching { journal.unresolved(verified).size }.getOrDefault(-1)
        AlertDialog(
            onDismissRequest = { if (!busy) showLogoutConfirm = false },
            shape = RoundedCornerShape(24.dp),
            title = {
                Text(
                    if (pendingCount > 0) "พบบิลที่รอตรวจสอบ"
                    else "จัดการการเข้าสู่ระบบ",
                    fontWeight = FontWeight.ExtraBold,
                    color = liveInk
                )
            },
            text = {
                Text(
                    when {
                        pendingCount > 0 ->
                            "มี $pendingCount บิลที่สถานะการชำระเงินยังไม่แน่นอน " +
                                "ต้อง Reconcile บิลเดิมก่อนเปลี่ยนเครื่องหรือออกจากระบบ"
                        pendingCount < 0 ->
                            "ไม่สามารถตรวจสอบรายการขายที่ค้างอยู่ในเครื่องได้ " +
                                "กรุณาตรวจสอบบิลก่อนดำเนินการ"
                        else ->
                            "เลือกกลับไปเลือกสาขาและเครื่องขายใหม่ หรือออกจากระบบทั้งหมด " +
                                "กะขายเดิมจะไม่ถูกปิดโดยอัตโนมัติ"
                    },
                    color = Color(0xFF66758D),
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                when {
                    pendingCount > 0 -> Button(
                        onClick = {
                            showLogoutConfirm = false
                            unresolved = "พบ $pendingCount บิลค้าง กรุณา Reconcile บิลเดิมก่อนทำรายการต่อ"
                            step = "review"
                        },
                        enabled = !busy
                    ) { Text("ไปตรวจสอบบิล") }
                    pendingCount == 0 -> Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (!busy) {
                                    showLogoutConfirm = false
                                    scope.launch {
                                        busy = true
                                        step = "switching"
                                        try {
                                            // The POS server revokes the old device
                                            // lease and issues a scoped pre-entry cookie.
                                            verified?.let {
                                                runCatching { NativeFcmRegistration.unregister(ctx, client, it) }
                                            }
                                            val nextStore = client.switchToBranchChooser()
                                            check(nextStore.branches.isNotEmpty()) {
                                                "ไม่มีสาขาที่เปิดใช้งาน"
                                            }
                                            goToStore()
                                            store = nextStore
                                            branch = null
                                            unresolved = ""
                                            step = "branch"
                                        } catch (failure: Exception) {
                                            // The old remote session might already
                                            // be revoked: force store verification.
                                            goToStore()
                                            store = null
                                            branch = null
                                            unresolved = ""
                                            error = "เปลี่ยนสาขาไม่สำเร็จ กรุณาเข้าด้วยรหัสร้านอีกครั้ง: " +
                                                (failure.message ?: "ไม่สามารถเชื่อมต่อได้").take(70)
                                        } finally {
                                            busy = false
                                        }
                                    }
                                }
                            },
                            enabled = !busy,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = liveBlue)
                        ) { Text("เลือกสาขา / เครื่องขายใหม่", fontWeight = FontWeight.Bold) }
                        OutlinedButton(
                            onClick = {
                                if (!busy) {
                                    showLogoutConfirm = false
                                    scope.launch {
                                        busy = true
                                        step = "switching"
                                        try {
                                            verified?.let {
                                                runCatching { NativeFcmRegistration.unregister(ctx, client, it) }
                                            }
                                            val remoteRevoked = client.logoutCurrentPosSession()
                                            goToStore()
                                            storeCode = ""
                                            store = null
                                            branch = null
                                            unresolved = ""
                                            error = if (remoteRevoked) null else
                                                "ออกจากมือถือแล้ว แต่ยังไม่ยืนยันการเพิกถอนเซสชันจาก Server"
                                        } finally {
                                            busy = false
                                        }
                                    }
                                }
                            },
                            enabled = !busy,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) { Text("ออกจากระบบทั้งหมด", color = Color(0xFFB84245)) }
                    }
                }
            },
            // The dialog can still be closed by tapping outside or Android Back;
            // remove the redundant visible Cancel action as requested.
        )
    }

    recoveredReceipt?.let { saved ->
        SavedDemoReceiptSheet(
            receipt = saved,
            onPrint = {
                runCatching { DemoReceiptPrinter.print(ctx, saved) }
            },
            onDone = { recoveredReceipt = null }
        )
    }

    // One app-wide listener survives switching tabs, sale screens and table modes.
    // The server owns QR order state; dismissing a food notification NEVER accepts an order.
    val verifiedForAlerts = session
    if (verifiedForAlerts != null &&
        step in setOf("shift", "review", "mode", "sale", "dine", "dine_order", "tab")) {
        NativeQrGlobalAlerts(
            client = client,
            session = verifiedForAlerts,
            canNavigate = step in setOf("mode", "sale", "dine", "dine_order", "tab") &&
                verifiedForAlerts.shiftId != null &&
                runCatching { journal.unresolved(verifiedForAlerts).isEmpty() }
                    .getOrDefault(false) &&
                !busy && !takeaway.saving.value &&
                dineWorkspace.drafts.values.none { it.busy || it.uncertain },
            onGoToTable = { event ->
                if (step in setOf("mode", "sale", "dine", "dine_order", "tab") &&
                    verifiedForAlerts.shiftId != null &&
                    runCatching { journal.unresolved(verifiedForAlerts).isEmpty() }
                        .getOrDefault(false) &&
                    !busy && !takeaway.saving.value &&
                    dineWorkspace.drafts.values.none { it.busy || it.uncertain }) {
                    scope.launch {
                        val floor = try {
                            client.diningTables(verifiedForAlerts)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            null
                        }
                        val destination = floor?.tables?.firstOrNull {
                            it.id == event.tableId && it.sessionId != null &&
                                it.status in setOf("occupied", "ordering", "pending_payment")
                        }
                        if (session?.id == verifiedForAlerts.id) {
                            if (destination != null) {
                                selectedDiningTable = destination
                                step = "dine_order"
                            } else {
                                step = "dine"
                            }
                        }
                    }
                }
            }
        )
    }
}
