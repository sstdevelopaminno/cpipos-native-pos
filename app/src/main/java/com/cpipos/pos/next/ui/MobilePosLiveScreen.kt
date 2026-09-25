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
internal fun MobilePosLiveScreen(client: WebPosClient, onPreview: () -> Unit) {
    val ctx = LocalContext.current
    val journal = remember(ctx.applicationContext) { LiveSaleJournal(ctx.applicationContext) }
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf("store") }
    var storeCode by remember { mutableStateOf("") }
    var selectedLanguage by remember { mutableStateOf(LoginLanguage.Thai) }
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
                        step = "ready"
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
            errorText = error,
            onPreview = onPreview
        )
        return
    }

    if (step == "sale" && session != null) {
        MobilePosSalePreview(
            mode = MockSaleMode.Takeaway,
            branchName = session!!.branchName,
            counterCode = session!!.deviceCode,
            onBack = { step = "ready" },
            liveClient = client,
            liveSession = session,
            liveProducts = catalog,
            liveJournal = journal
        )
        return
    }

    androidx.compose.material3.Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFFF4F8FF)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 23.dp, vertical = 43.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            Image(
                painter = painterResource(R.drawable.ic_cpipos_receipt_logo),
                contentDescription = "CpIPOS",
                modifier = Modifier.height(68.dp).fillMaxWidth()
            )
            Text("CpIPOS", color = liveInk, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp)
            Text("ระบบขายหน้าร้าน • เชื่อมต่อระบบจริง", color = Color(0xFF667E9C), fontSize = 12.sp)
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(19.dp),
                    verticalArrangement = Arrangement.spacedBy(11.dp)
                ) {
                    when(step) {
                        "branch" -> {
                            Text("เลือกสาขา • ${store?.name.orEmpty()}", color = liveInk,
                                fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            store?.branches?.forEach { option ->
                                OutlinedButton(
                                    onClick = {
                                        branch = option
                                        deviceCode = storePrefs.getString("device_${option.id}", "") ?: ""
                                        pin = "";error=null;step="pin"
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text(option.name.ifBlank { option.code }) }
                            }
                            OutlinedButton(onClick = { step="store" }, modifier = Modifier.fillMaxWidth()) {
                                Text("ย้อนกลับ")
                            }
                        }
                        "pin" -> {
                            Text("ยืนยันพนักงาน • ${branch?.name.orEmpty()}", color = liveInk,
                                fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text("รหัสเครื่องต้องตรงกับที่ลงทะเบียนใน POS หลังบ้าน",
                                color = Color(0xFF647994), fontSize = 12.sp)
                            OutlinedTextField(
                                value = deviceCode,
                                onValueChange = { deviceCode = it.uppercase().take(64); error=null },
                                label = { Text("รหัสเครื่อง POS ที่ลงทะเบียน") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = pin,
                                onValueChange = { pin = it.filter(Char::isDigit).take(12);error=null },
                                label = { Text("PIN พนักงาน") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Button(
                                enabled = pin.length in 4..12 && deviceCode.length >= 3 && !busy,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = liveBlue),
                                onClick = {
                                    val selected = branch ?: return@Button
                                    val secret = pin
                                    pin = ""
                                    scope.launch {
                                        busy = true; error = null
                                        try {
                                            val authenticated = client.verifyPin(storeCode,selected.id,deviceCode,secret)
                                            storePrefs.edit().putString("device_${selected.id}", deviceCode).apply()
                                            session = authenticated
                                            step = "shift"
                                            refreshCatalog(authenticated)
                                        } catch(e:Exception) {
                                            error = "เข้าสู่ระบบไม่สำเร็จ: ${e.message?.take(85)}"
                                        } finally { busy = false }
                                    }
                                }
                            ) { Text(if(busy) "กำลังยืนยัน..." else "เข้าสู่ระบบจริง") }
                            OutlinedButton(onClick={step="branch"}, modifier=Modifier.fillMaxWidth()) {
                                Text("ย้อนกลับเลือกสาขา")
                            }
                        }
                        "shift" -> {
                            Text("กะขาย • ${session?.branchName.orEmpty()}", color=liveInk,
                                fontSize=18.sp,fontWeight=FontWeight.Bold)
                            Text("ต้องมีกะเปิดและเครื่องที่ลงทะเบียนจึงจะขายสินค้าได้",
                                color=Color(0xFF607594),fontSize=12.sp)
                            if(session?.canOpenShift==true && session?.shiftId==null) {
                                Button(
                                    enabled=!busy,
                                    modifier=Modifier.fillMaxWidth(),
                                    colors=ButtonDefaults.buttonColors(containerColor=liveBlue),
                                    onClick={
                                        scope.launch {
                                            busy=true;error=null
                                            try {
                                                val opened=client.openShift()
                                                session=opened
                                                refreshCatalog(opened)
                                            }catch(e:Exception){
                                                error="เปิดกะไม่สำเร็จ: ${e.message?.take(85)}"
                                            } finally {busy=false}
                                        }
                                    }
                                ){Text("เปิดกะขาย (เงินเปิดกะ ฿0)")}
                            }
                            OutlinedButton(
                                onClick={ session?.let { refreshCatalog(it) } },
                                modifier=Modifier.fillMaxWidth()
                            ){Text("ตรวจสอบกะและโหลดสินค้าอีกครั้ง")}
                            OutlinedButton(
                                onClick={step="store";session=null},
                                modifier=Modifier.fillMaxWidth()
                            ){Text("กลับหน้าเข้าสู่ระบบ")}
                        }
                        "review" -> {
                            Text("มีบิลจริงรอตรวจสอบ", color=Color(0xFFBB453F),
                                fontWeight=FontWeight.Bold,fontSize=20.sp)
                            Text(unresolved,color=liveInk)
                            Text(
                                "ห้ามกดขายซ้ำด้วยบิลใหม่ก่อนตรวจสอบสถานะการชำระเงินจริงใน CpIPOS Web",
                                color=Color(0xFFAF5345),fontSize=12.sp
                            )
                            OutlinedButton(
                                onClick={ session?.let { refreshCatalog(it) } },
                                modifier=Modifier.fillMaxWidth()
                            ){Text("ตรวจสอบสถานะและลองโหลดใหม่")}
                        }
                        "ready" -> {
                            Text(session?.storeName.orEmpty(), color=liveInk,
                                fontSize=19.sp,fontWeight=FontWeight.ExtraBold)
                            Text("สาขา ${session?.branchName.orEmpty()} • เครื่อง ${session?.deviceCode.orEmpty()}",
                                color=Color(0xFF607594),fontSize=12.sp)
                            Text("กะเปิด: ${session?.shiftId?.take(8).orEmpty()} • สินค้า ${catalog.size} รายการ",
                                color=Color(0xFF288850),fontSize=12.sp)
                            Button(
                                modifier=Modifier.fillMaxWidth().height(50.dp),
                                colors=ButtonDefaults.buttonColors(containerColor=liveBlue),
                                enabled=catalog.isNotEmpty()&&!busy,
                                onClick={step="sale"}
                            ){Text("เข้าสู่หน้าขาย • กลับบ้าน",fontWeight=FontWeight.Bold)}
                            OutlinedButton(
                                modifier=Modifier.fillMaxWidth(),
                                onClick={ session?.let {refreshCatalog(it)} }
                            ){Text("โหลดสินค้าและตรวจสอบกะล่าสุด")}
                            Text(
                                "โหมดนั่งโต๊ะจะเปิดเมื่อเชื่อม Table Bill ของระบบจริงแล้ว",
                                color=Color(0xFF7D8CA5),fontSize=11.sp
                            )
                        }
                    }
                    if(error!=null) Text(error.orEmpty(),color=Color(0xFFBA353D),fontSize=12.sp)
                }
            }
            Text(
                "ไม่มีการใช้ PIN จำลอง • ระบบตรวจสิทธิ์ผ่าน POS Web",
                color=Color(0xFF7085A0),fontSize=10.sp
            )
        }
    }
}
