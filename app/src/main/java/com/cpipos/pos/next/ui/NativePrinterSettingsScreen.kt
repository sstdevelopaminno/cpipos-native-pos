package com.cpipos.pos.next.ui

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.hardware.usb.UsbManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cpipos.pos.next.core.print.NativePrinterCandidate
import com.cpipos.pos.next.core.print.NativePrinterDiscovery
import com.cpipos.pos.next.core.print.NativePrintRuntime
import com.cpipos.pos.next.core.webpos.WebPosApiException
import com.cpipos.pos.next.core.webpos.WebPosClient
import com.cpipos.pos.next.core.webpos.WebPosSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private val printerBlue = Color(0xFF176DED)
private val printerInk = Color(0xFF172B4E)
private val printerMuted = Color(0xFF71849B)

private data class ConfiguredPrinter(
    val profileId: String,
    val name: String,
    val mode: String,
    val paper: Int,
    val status: String,
    val purposes: List<String>
)

private val printerPurposes = listOf(
    "receipt" to "ใบเสร็จหน้าขาย",
    "reprint" to "ใบเสร็จย้อนหลัง",
    "kitchen" to "ครัว",
    "drink" to "เครื่องดื่ม",
    "bar" to "บาร์",
    "shift_report" to "ปิดกะ / รายงานกะ",
    "payment_slip" to "สลิปชำระเงิน",
    "cash_drawer" to "ลิ้นชักเงินสด"
)

@Composable
internal fun NativePrinterSettingsScreen(
    client: WebPosClient,
    session: WebPosSession,
    onBack: () -> Unit,
    onTab: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var mode by remember { mutableStateOf("lan") }
    var discovering by remember { mutableStateOf(false) }
    var candidates by remember { mutableStateOf<List<NativePrinterCandidate>>(emptyList()) }
    var selected by remember { mutableStateOf<NativePrinterCandidate?>(null) }
    var printerName by remember { mutableStateOf("") }
    var paper by remember { mutableIntStateOf(58) }
    var lanIp by remember { mutableStateOf("") }
    var lanPort by remember { mutableStateOf("9100") }
    var selectedPurposes by remember { mutableStateOf(setOf("receipt", "reprint")) }
    var configured by remember { mutableStateOf<List<ConfiguredPrinter>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var testingId by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it }) {
            candidates = NativePrinterDiscovery.bluetooth(context)
            notice = if (candidates.isEmpty())
                "ยังไม่พบเครื่อง Bluetooth ที่จับคู่ไว้ กด “เปิดหน้าจับคู่ Bluetooth” ก่อน"
            else "พบ Bluetooth " + candidates.size + " เครื่อง"
        } else {
            error = "ต้องอนุญาต Bluetooth เพื่อค้นหาและเชื่อมต่อเครื่องพิมพ์"
        }
    }

    fun parseRegistry(data: JSONObject): List<ConfiguredPrinter> {
        val rows = data.optJSONArray("devices") ?: JSONArray()
        return buildList {
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                val profileId = row.optString("printer_profile_id").trim()
                if (profileId.isEmpty()) continue
                val assignments = row.optJSONArray("printer_device_assignments") ?: JSONArray()
                val purposes = buildList {
                    for (j in 0 until assignments.length()) {
                        val assignment = assignments.optJSONObject(j) ?: continue
                        if (assignment.optBoolean("is_enabled", true)) {
                            assignment.optString("purpose").takeIf { it.isNotBlank() }?.let(::add)
                        }
                    }
                }.distinct()
                add(
                    ConfiguredPrinter(
                        profileId = profileId,
                        name = row.optString("display_name", "Printer"),
                        mode = row.optString("connection_mode", "lan"),
                        paper = row.optInt("paper_width_mm", 58),
                        status = row.optString("status", "unknown"),
                        purposes = purposes
                    )
                )
            }
        }.distinctBy { it.profileId }
    }

    fun loadRegistry() {
        scope.launch {
            loading = true
            try {
                configured = parseRegistry(client.printerRegistry(session))
                error = null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = when (failure) {
                    is WebPosApiException -> when (failure.status) {
                        401, 403 -> "เมนูเครื่องพิมพ์ต้องใช้สิทธิ์ Owner / Manager / Kitchen"
                        else -> "โหลดเครื่องพิมพ์ไม่สำเร็จ (" + failure.code + ")"
                    }
                    else -> "โหลดเครื่องพิมพ์ไม่สำเร็จ กรุณาตรวจสอบเครือข่าย"
                }
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(client, session.id) { loadRegistry() }

    fun selectCandidate(candidate: NativePrinterCandidate) {
        selected = candidate
        mode = candidate.mode
        printerName = candidate.name
        if (candidate.mode == "lan") {
            lanIp = candidate.ipAddress.orEmpty()
            lanPort = (candidate.port ?: 9100).toString()
        }
        notice = "เลือก " + candidate.name + " แล้ว ตรวจสอบขนาดกระดาษและเส้นทางพิมพ์ก่อนบันทึก"
        error = null
    }

    fun discover() {
        discovering = true
        candidates = emptyList()
        selected = null
        notice = ""
        error = null
        when (mode) {
            "bluetooth" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    bluetoothPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.BLUETOOTH_CONNECT,
                            Manifest.permission.BLUETOOTH_SCAN
                        )
                    )
                    discovering = false
                } else {
                    candidates = NativePrinterDiscovery.bluetooth(context)
                    discovering = false
                }
            }
            "usb" -> {
                candidates = NativePrinterDiscovery.usb(context)
                discovering = false
                notice = if (candidates.isEmpty()) "ยังไม่พบเครื่องพิมพ์ USB ที่ต่อกับมือถือ"
                else "พบ USB " + candidates.size + " เครื่อง"
            }
            else -> scope.launch {
                try {
                    candidates = withContext(Dispatchers.IO) {
                        NativePrinterDiscovery.lan(lanPort.toIntOrNull() ?: 9100)
                    }
                    notice = if (candidates.isEmpty())
                        "ไม่พบพอร์ตเครื่องพิมพ์ในเครือข่ายอัตโนมัติ สามารถกรอก IP เองได้"
                    else "พบ LAN " + candidates.size + " เครื่อง"
                } catch (failure: Exception) {
                    error = "ค้นหา LAN ไม่สำเร็จ: " + (failure.message ?: "network error")
                } finally {
                    discovering = false
                }
            }
        }
    }

    fun requestUsbPermission(candidate: NativePrinterCandidate) {
        val manager = context.getSystemService(UsbManager::class.java) ?: return
        val name = candidate.metadata.optString("usb_device_name")
        val device = manager.deviceList.values.firstOrNull { it.deviceName == name } ?: return
        if (manager.hasPermission(device)) return
        val pending = PendingIntent.getBroadcast(
            context,
            device.deviceId,
            Intent("com.cpipos.pos.next.USB_PRINTER_PERMISSION").setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        manager.requestPermission(device, pending)
    }

    fun savePrinter() {
        if (saving) return
        val port = lanPort.toIntOrNull()?.coerceIn(1, 65535) ?: 9100
        val candidate = selected
        val name = printerName.trim().ifBlank {
            when (mode) {
                "lan" -> "Receipt LAN " + lanIp.trim()
                "usb" -> "USB Receipt Printer"
                else -> "Bluetooth Receipt Printer"
            }
        }
        if (mode == "lan" && lanIp.trim().isEmpty()) {
            error = "กรุณาระบุ IP เครื่องพิมพ์ LAN"
            return
        }
        if (mode != "lan" && candidate == null) {
            error = "กรุณาค้นหาและเลือกเครื่องพิมพ์ก่อน"
            return
        }
        if (selectedPurposes.isEmpty()) {
            error = "กรุณาติ๊กเส้นทางการสั่งพิมพ์อย่างน้อย 1 รายการ"
            return
        }
        val fingerprint = when (mode) {
            "lan" -> "lan:" + lanIp.trim() + ":" + port
            else -> candidate?.fingerprint ?: return
        }
        val metadata = JSONObject(candidate?.metadata?.toString() ?: "{}")
            .put("native_pos_v2", true)
            .put("transport_mode", mode)

        val purposes = JSONArray()
        val assignments = JSONArray()
        selectedPurposes.forEach { purpose ->
            purposes.put(purpose)
            assignments.put(
                JSONObject()
                    .put("purpose", purpose)
                    .put("zone_key", "")
                    .put("is_default", purpose == "receipt")
                    .put("copies", 1)
            )
        }
        val payload = JSONObject()
            .put("printer_name", name)
            .put("connection_mode", mode)
            .put("paper_width_mm", paper)
            .put("purposes", purposes)
            .put("assignments", assignments)
            .put("runtime_device_code", session.deviceCode)
            .put("device_fingerprint", fingerprint)
            .put("enabled", true)
            .put("metadata", metadata)
        if (mode == "lan") {
            payload.put("ip_address", lanIp.trim()).put("port", port)
        }

        saving = true
        scope.launch {
            try {
                if (mode == "usb" && candidate != null) requestUsbPermission(candidate)
                val result = client.createPrinter(session, payload)
                val profileId = result.optJSONObject("profile")?.optString("id").orEmpty()
                notice = "บันทึกเครื่องพิมพ์แล้ว" +
                    if (profileId.isNotBlank()) " · พร้อมทดสอบพิมพ์" else ""
                error = null
                NativePrintRuntime.wake()
                loadRegistry()
            } catch (failure: Exception) {
                error = when (failure) {
                    is WebPosApiException -> when {
                        failure.status == 403 -> "ไม่มีสิทธิ์ตั้งค่าเครื่องพิมพ์ ต้องใช้ Owner / Manager / Kitchen"
                        failure.code == "printer_conflict" -> "เครื่องพิมพ์นี้ถูกบันทึกไว้แล้ว"
                        else -> "บันทึกเครื่องพิมพ์ไม่สำเร็จ (" + failure.code + ")"
                    }
                    else -> "บันทึกเครื่องพิมพ์ไม่สำเร็จ: " + (failure.message ?: "unknown")
                }
            } finally {
                saving = false
            }
        }
    }

    fun testPrinter(profileId: String) {
        testingId = profileId
        scope.launch {
            try {
                val result = client.testPrinter(session, profileId)
                notice = result.optString("message").ifBlank { "ส่งงานพิมพ์ทดสอบแล้ว" }
                error = null
                NativePrintRuntime.wake()
            } catch (failure: Exception) {
                error = when (failure) {
                    is WebPosApiException -> "พิมพ์ทดสอบไม่สำเร็จ (" + failure.code + ")"
                    else -> "พิมพ์ทดสอบไม่สำเร็จ: " + (failure.message ?: "unknown")
                }
            } finally {
                testingId = null
            }
        }
    }

    Surface(Modifier.fillMaxSize(), color = Color(0xFFF5F9FF)) {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().statusBarsPadding()
                    .padding(start = 15.dp, end = 15.dp, top = 8.dp, bottom = 112.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onBack) { Text("‹ ตั้งค่า") }
                        Column(Modifier.weight(1f)) {
                            Text("ตั้งค่า เครื่องปริ้นใบเสร็จ", fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold, color = printerInk)
                            Text(session.branchName + " · เครื่อง " + session.deviceCode,
                                fontSize = 11.sp, color = printerMuted)
                        }
                    }
                }

                item {
                    Surface(color = Color.White, shape = RoundedCornerShape(18.dp)) {
                        Column(
                            Modifier.fillMaxWidth().padding(15.dp),
                            verticalArrangement = Arrangement.spacedBy(11.dp)
                        ) {
                            Text("1. เลือกการเชื่อมต่อ", fontWeight = FontWeight.ExtraBold,
                                color = printerInk)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("lan" to "สายแลน", "usb" to "USB", "bluetooth" to "Bluetooth")
                                    .forEach { pair ->
                                        FilterChip(
                                            selected = mode == pair.first,
                                            onClick = {
                                                mode = pair.first
                                                candidates = emptyList()
                                                selected = null
                                                error = null
                                            },
                                            label = { Text(pair.second) }
                                        )
                                    }
                            }
                            if (mode == "lan") {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = lanIp,
                                        onValueChange = { lanIp = it.take(45) },
                                        label = { Text("IP เครื่องพิมพ์") },
                                        placeholder = { Text("192.168.1.100") },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = lanPort,
                                        onValueChange = { lanPort = it.filter(Char::isDigit).take(5) },
                                        label = { Text("Port") },
                                        modifier = Modifier.width(100.dp),
                                        singleLine = true
                                    )
                                }
                            }
                            Button(
                                onClick = { discover() },
                                enabled = !discovering,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = printerBlue)
                            ) {
                                if (discovering) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp, color = Color.White
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text(if (discovering) "กำลังค้นหา..." else "ค้นหาเครื่องพิมพ์")
                            }
                            if (mode == "bluetooth") {
                                OutlinedButton(
                                    onClick = {
                                        context.startActivity(
                                            Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("เปิดหน้าจับคู่ Bluetooth") }
                            }
                        }
                    }
                }

                if (candidates.isNotEmpty()) {
                    item {
                        Text("2. เลือกเครื่องที่พบ", fontWeight = FontWeight.ExtraBold,
                            color = printerInk)
                    }
                    items(candidates, key = { it.id }) { candidate ->
                        Surface(
                            color = if (selected?.id == candidate.id) Color(0xFFE8F2FF) else Color.White,
                            shape = RoundedCornerShape(15.dp),
                            modifier = Modifier.fillMaxWidth().clickable { selectCandidate(candidate) }
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(candidate.name, fontWeight = FontWeight.Bold,
                                        color = printerInk)
                                    val target = candidate.ipAddress?.let {
                                        " · " + it + ":" + (candidate.port ?: 9100)
                                    } ?: ""
                                    Text(candidate.mode.uppercase() + target,
                                        fontSize = 11.sp, color = printerMuted)
                                }
                                RadioButton(
                                    selected = selected?.id == candidate.id,
                                    onClick = { selectCandidate(candidate) }
                                )
                            }
                        }
                    }
                }

                item {
                    Surface(color = Color.White, shape = RoundedCornerShape(18.dp)) {
                        Column(
                            Modifier.fillMaxWidth().padding(15.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text("3. ตั้งค่าและเลือกเส้นทางการสั่งพิมพ์",
                                fontWeight = FontWeight.ExtraBold, color = printerInk)
                            OutlinedTextField(
                                value = printerName,
                                onValueChange = { printerName = it.take(80) },
                                label = { Text("ชื่อเครื่องพิมพ์") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Text("ขนาดกระดาษ", fontWeight = FontWeight.Bold, color = printerInk)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = paper == 58,
                                    onClick = { paper = 58 },
                                    label = { Text("58 mm") }
                                )
                                FilterChip(
                                    selected = paper == 80,
                                    onClick = { paper = 80 },
                                    label = { Text("80 mm") }
                                )
                            }
                            HorizontalDivider(color = Color(0xFFE7EEF7))
                            Text("ติ๊กเส้นทางการสั่งพิมพ์", fontWeight = FontWeight.Bold,
                                color = printerInk)
                            printerPurposes.forEach { purposePair ->
                                val purpose = purposePair.first
                                val label = purposePair.second
                                Row(
                                    Modifier.fillMaxWidth()
                                        .clickable {
                                            selectedPurposes =
                                                if (purpose in selectedPurposes)
                                                    selectedPurposes - purpose
                                                else selectedPurposes + purpose
                                        },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = purpose in selectedPurposes,
                                        onCheckedChange = {
                                            selectedPurposes =
                                                if (it) selectedPurposes + purpose
                                                else selectedPurposes - purpose
                                        }
                                    )
                                    Text(label, fontSize = 13.sp, color = printerInk)
                                }
                            }
                            Text(
                                "ใบเสร็จหลังชำระจะถูกคิวอัตโนมัติจาก CpIPOS ตามเส้นทาง receipt " +
                                    "และ Print Agent ของเครื่องนี้จะรับงานโดยไม่ทำให้การชำระเงินล้มเหลว",
                                fontSize = 11.sp, color = printerMuted
                            )
                            Button(
                                onClick = { savePrinter() },
                                enabled = !saving,
                                modifier = Modifier.fillMaxWidth().height(49.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = printerBlue)
                            ) {
                                Text(if (saving) "กำลังบันทึก..." else "บันทึกและเชื่อมต่อ")
                            }
                        }
                    }
                }

                if (notice.isNotBlank()) {
                    item {
                        Surface(color = Color(0xFFEAF8EF), shape = RoundedCornerShape(13.dp)) {
                            Text(notice, Modifier.fillMaxWidth().padding(12.dp),
                                color = Color(0xFF177A48), fontSize = 12.sp)
                        }
                    }
                }
                error?.let { message ->
                    item {
                        Surface(color = Color(0xFFFFEEEE), shape = RoundedCornerShape(13.dp)) {
                            Text(message, Modifier.fillMaxWidth().padding(12.dp),
                                color = Color(0xFFB13F4C), fontSize = 12.sp)
                        }
                    }
                }

                item {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("เครื่องที่ตั้งค่าแล้ว", fontWeight = FontWeight.ExtraBold,
                            color = printerInk, modifier = Modifier.weight(1f))
                        TextButton(onClick = { loadRegistry() }) { Text("รีเฟรช") }
                    }
                }

                if (loading) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = printerBlue)
                        }
                    }
                } else if (configured.isEmpty()) {
                    item {
                        Text("ยังไม่มีเครื่องพิมพ์ที่ผูกกับสาขานี้",
                            color = printerMuted, fontSize = 12.sp)
                    }
                } else {
                    items(configured, key = { it.profileId }) { printer ->
                        Surface(color = Color.White, shape = RoundedCornerShape(16.dp)) {
                            Column(
                                Modifier.fillMaxWidth().padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(printer.name, fontWeight = FontWeight.Bold,
                                            color = printerInk)
                                        Text(
                                            printer.mode.uppercase() + " · " + printer.paper + " mm · " +
                                                printer.status,
                                            fontSize = 11.sp, color = printerMuted
                                        )
                                        Text(
                                            printer.purposes.map { purpose ->
                                                printerPurposes.firstOrNull {
                                                    it.first == purpose
                                                }?.second ?: purpose
                                            }.joinToString(" • "),
                                            fontSize = 10.sp, color = Color(0xFF526A88)
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = { testPrinter(printer.profileId) },
                                        enabled = testingId == null
                                    ) {
                                        Text(
                                            if (testingId == printer.profileId) "กำลังส่ง..."
                                            else "พิมพ์ทดสอบ",
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(18.dp)) }
            }

            HomeBottomMenu(
                modifier = Modifier.align(Alignment.BottomCenter),
                selectedTab = "settings",
                onMenuClick = onTab
            )
        }
    }
}
