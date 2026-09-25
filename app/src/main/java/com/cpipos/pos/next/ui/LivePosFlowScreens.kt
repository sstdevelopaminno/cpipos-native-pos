package com.cpipos.pos.next.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cpipos.pos.next.R
import com.cpipos.pos.next.core.webpos.WebBranch
import com.cpipos.pos.next.core.webpos.WebPosSession

private val brandBlue = Color(0xFF1F75D6)
private val brandInk = Color(0xFF113C73)
private val brandBorder = Color(0xFFD4E1F2)

@Composable
private fun LiveAuthCard(content: @Composable () -> Unit) {
    BrandedAuthSurface {
        Column(
            modifier = Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BrandLogoBlock(topPadding = 0)
            Spacer(modifier = Modifier.height(22.dp))
            content()
        }
    }
}

@Composable
private fun LiveAction(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(47.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = brandBlue,
            disabledContainerColor = Color(0xFFD8E7F7)
        )
    ) { Text(text, fontWeight = FontWeight.Bold) }
}

@Composable
private fun LiveBack(text: String = "ย้อนกลับ", onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick, modifier = Modifier.fillMaxWidth().height(43.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, brandBorder)
    ) { Text(text, color = brandInk) }
}

@Composable
private fun LiveError(message: String?) {
    if (!message.isNullOrBlank()) {
        Text(
            text = message, color = Color(0xFFB73F43),
            fontSize = 11.sp, lineHeight = 16.sp,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
        )
    }
}

@Composable
internal fun LiveBranchChoiceScreen(
    storeName: String,
    branches: List<WebBranch>,
    selected: WebBranch?,
    onSelect: (WebBranch) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    busy: Boolean = false,
    error: String? = null
) {
    LiveAuthCard {
        Text(
            "เลือกสาขา • $storeName",
            color = brandInk, fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        branches.forEach { branch ->
            val isSelected = selected?.id == branch.id
            Card(
                modifier = Modifier.fillMaxWidth()
                    .padding(bottom = 9.dp)
                    .border(
                        if (isSelected) 2.dp else 1.dp,
                        if (isSelected) Color(0xFF2577FF) else brandBorder,
                        RoundedCornerShape(12.dp)
                    )
                    .clickable(enabled = !busy) { onSelect(branch) },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) Color(0xFFEAF3FF) else Color.White
                )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_store_front),
                        contentDescription = null,
                        modifier = Modifier.size(23.dp)
                    )
                    Spacer(modifier = Modifier.width(11.dp))
                    Column {
                        Text(branch.name.ifBlank { branch.code },
                            fontWeight = FontWeight.Bold, color = Color(0xFF243349))
                        if (branch.code.isNotBlank()) {
                            Text(branch.code, color = Color(0xFF365B86), fontSize = 11.sp)
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(7.dp))
        LiveError(error)
        LiveAction(
            if (busy) "กำลังเลือกสาขา..." else "ถัดไป",
            enabled = selected != null && !busy,
            onClick = onNext
        )
        Spacer(modifier = Modifier.height(8.dp))
        LiveBack("กลับหน้าเข้าสู่ระบบ", onClick = onBack)
    }
}

@Composable
internal fun LiveEmployeeLoginScreen(
    branchName: String,
    employeeCode: String,
    showCode: Boolean,
    selectedLanguage: LoginLanguage,
    busy: Boolean,
    error: String?,
    onLanguageSelected: (LoginLanguage) -> Unit,
    onEmployeeCode: (String) -> Unit,
    onToggleCode: () -> Unit,
    onVerify: () -> Unit,
    onBack: () -> Unit
) {
    LiveAuthCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            LanguageToggle(
                selectedLanguage = selectedLanguage,
                onLanguageSelected = onLanguageSelected
            )
        }
        Spacer(modifier = Modifier.height(13.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(R.drawable.ic_store_front),
                contentDescription = null, modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(7.dp))
            Text(
                text = if (selectedLanguage == LoginLanguage.Thai)
                    "สาขา: $branchName" else "Branch: $branchName",
                color = brandInk, fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = if (selectedLanguage == LoginLanguage.Thai)
                "รหัสพนักงาน" else "Employee code",
            modifier = Modifier.fillMaxWidth(),
            color = brandInk,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelLarge
        )
        Spacer(modifier = Modifier.height(7.dp))
        OutlinedTextField(
            value = employeeCode, onValueChange = onEmployeeCode,
            placeholder = {
                Text(
                    if (selectedLanguage == LoginLanguage.Thai)
                        "รหัสพนักงานที่กำหนดจากระบบหลังบ้าน"
                    else "Employee code from back office",
                    fontSize = 13.sp
                )
            },
            singleLine = true,
            enabled = !busy,
            visualTransformation = if (showCode)
                VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            trailingIcon = {
                Image(
                    painter = painterResource(
                        if (showCode) R.drawable.ic_eye_off else R.drawable.ic_eye
                    ),
                    contentDescription = "แสดงหรือซ่อนรหัสพนักงาน",
                    modifier = Modifier.size(21.dp).clickable(enabled = !busy) { onToggleCode() }
                )
            },
            shape = RoundedCornerShape(12.dp),
            colors = brandedTextFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
        LiveError(error)
        Spacer(modifier = Modifier.height(10.dp))
        LiveAction(
            if (busy) "กำลังตรวจสอบ..."
            else if (selectedLanguage == LoginLanguage.Thai) "ยืนยันพนักงาน"
            else "Verify employee",
            enabled = !busy && employeeCode.isNotBlank(),
            onClick = onVerify
        )
        Spacer(modifier = Modifier.height(9.dp))
        LiveBack(
            if (selectedLanguage == LoginLanguage.Thai) "ย้อนกลับ" else "Back",
            onClick = onBack
        )
    }
}

@Composable
internal fun LiveDeviceChoiceScreen(
    branchName: String,
    employeeName: String,
    devices: List<com.cpipos.pos.next.core.webpos.WebCashierDevice>,
    canOverrideInUse: Boolean,
    selected: com.cpipos.pos.next.core.webpos.WebCashierDevice?,
    busy: Boolean,
    error: String?,
    onSelect: (com.cpipos.pos.next.core.webpos.WebCashierDevice) -> Unit,
    onOpen: () -> Unit,
    onBack: () -> Unit
) {
    val twoColumns = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 530
    LiveAuthCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(R.drawable.ic_store_front),
                contentDescription = null, modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(7.dp))
            Text("สาขา: $branchName", color = brandInk, fontSize = 13.sp)
        }
        Spacer(modifier = Modifier.height(13.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, brandBorder),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "เลือกเครื่องแคชเชียร์",
                    color = brandInk, fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "พนักงาน: $employeeName",
                    color = Color(0xFF667991), fontSize = 11.sp
                )
                if (devices.isEmpty()) {
                    Text(
                        "สาขานี้ยังไม่มีเครื่องที่ลงทะเบียน กรุณาเพิ่มเครื่องจากระบบหลังบ้าน",
                        color = Color(0xFFB13F43), fontSize = 12.sp
                    )
                }
                if (twoColumns) {
                    devices.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            pair.forEach { device ->
                                LiveDeviceOption(
                                    device = device,
                                    selected = device.id == selected?.id,
                                    enabled = !busy &&
                                        (device.status == "ready" ||
                                            (device.status == "in_use" && canOverrideInUse)),
                                    onClick = { onSelect(device) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                } else {
                    devices.forEach { device ->
                        LiveDeviceOption(
                            device = device,
                            selected = device.id == selected?.id,
                            enabled = !busy &&
                                (device.status == "ready" ||
                                    (device.status == "in_use" && canOverrideInUse)),
                            onClick = { onSelect(device) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
        LiveError(error)
        Spacer(modifier = Modifier.height(9.dp))
        LiveAction(
            if (busy) "กำลังเปิดเครื่อง..." else "เปิดเคาน์เตอร์",
            enabled = !busy && selected != null,
            onClick = onOpen
        )
        Spacer(modifier = Modifier.height(9.dp))
        LiveBack(onClick = onBack)
    }
}

@Composable
private fun LiveDeviceOption(
    device: com.cpipos.pos.next.core.webpos.WebCashierDevice,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(12.dp)
    val statusText = when (device.status) {
        "ready" -> "พร้อมใช้งาน"
        "in_use" -> "กำลังใช้งาน"
        "offline" -> "ออฟไลน์"
        "disabled" -> "ปิดใช้งาน"
        else -> "ไม่พร้อมใช้งาน"
    }
    Card(
        modifier = modifier
            .border(
                if (selected) 2.dp else 1.dp,
                if (selected) Color(0xFF2577FF) else brandBorder,
                shape
            )
            .clickable(enabled = enabled, onClick = onClick),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) Color(0xFFF0F7FF)
            else if (enabled) Color.White else Color(0xFFF6F7FA)
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                device.name.ifBlank { device.code },
                fontWeight = FontWeight.SemiBold, color = brandInk,
                fontSize = 13.sp, lineHeight = 18.sp
            )
            Text(
                "รหัสเครื่อง " + device.code,
                fontWeight = FontWeight.Bold, color = brandInk,
                fontSize = 12.sp
            )
            if (device.counterName.isNotBlank() && device.counterName != "-") {
                Text(device.counterName, fontSize = 11.sp, color = Color(0xFF66809F))
            }
            if (!device.currentUserName.isNullOrBlank()) {
                Text(
                    "ใช้งานโดย " + device.currentUserName,
                    fontSize = 11.sp, color = Color(0xFF9E6543)
                )
            }
            Text(
                statusText,
                fontSize = 11.sp,
                color = if (enabled) Color(0xFF16814B) else Color(0xFF986653),
                modifier = Modifier
                    .background(
                        if (enabled) Color(0xFFE8F9EF) else Color(0xFFF8EDEB),
                        RoundedCornerShape(25.dp)
                    )
                    .padding(horizontal = 9.dp, vertical = 5.dp)
            )
        }
    }
}

@Composable
internal fun LiveShiftScreen(
    session: WebPosSession,
    busy: Boolean,
    error: String?,
    onOpen: () -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit
) {
    LiveAuthCard {
        Text(
            "เปิดกะขาย • ${session.branchName}",
            modifier = Modifier.fillMaxWidth(),
            color = brandInk, style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            "ผู้ขาย: ${session.cashierName}  •  เครื่อง: ${session.deviceCode}",
            color = Color(0xFF627791), fontSize = 12.sp
        )
        Spacer(modifier = Modifier.height(13.dp))
        Text(
            "กะขายต้องเปิดอยู่ และระบบต้องตรวจสอบสิทธิ์เครื่องและพนักงานสำเร็จ",
            color = Color(0xFF627791),
            fontSize = 11.sp,
            modifier = Modifier.fillMaxWidth()
        )
        LiveError(error)
        Spacer(modifier = Modifier.height(10.dp))
        if (session.canOpenShift && session.shiftId == null && session.canSell) {
            LiveAction(
                if (busy) "กำลังเปิดกะ..." else "เปิดกะขาย (เงินเปิดกะ ฿0)",
                enabled = !busy, onClick = onOpen
            )
            Spacer(modifier = Modifier.height(9.dp))
        }
        LiveAction("ตรวจสอบกะและโหลดสินค้า", enabled = !busy, onClick = onRefresh)
        Spacer(modifier = Modifier.height(8.dp))
        LiveBack("กลับหน้าเข้าสู่ระบบ", onClick = onBack)
    }
}

@Composable
internal fun LiveCounterScreen(
    session: WebPosSession,
    productCount: Int,
    onEnter: () -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit
) {
    LiveAuthCard {
        Text(
            "เลือกเคาน์เตอร์ • ${session.branchName}",
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold, color = brandInk
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "พนักงาน ${session.cashierName} • สินค้า $productCount รายการ",
            color = Color(0xFF617994), fontSize = 11.sp,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        Card(
            modifier = Modifier.fillMaxWidth().border(
                2.dp, Color(0xFF2577FF), RoundedCornerShape(13.dp)
            ),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF3FF)),
            shape = RoundedCornerShape(13.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(15.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_pos_terminal),
                    contentDescription = null,
                    modifier = Modifier.size(31.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        session.deviceCode, color = brandInk,
                        fontWeight = FontWeight.Bold, fontSize = 17.sp
                    )
                    Text("กะเปิดอยู่ • เครื่องที่ยืนยันสิทธิ์แล้ว",
                        color = Color(0xFF23814D), fontSize = 11.sp)
                }
            }
        }
        Spacer(modifier = Modifier.height(13.dp))
        LiveAction("เปิดหน้าขาย", enabled = productCount > 0, onClick = onEnter)
        Spacer(modifier = Modifier.height(8.dp))
        LiveBack("โหลดสินค้าและตรวจสอบกะ", onClick = onRefresh)
        Spacer(modifier = Modifier.height(4.dp))
        LiveBack("กลับหน้าเข้าสู่ระบบ", onClick = onBack)
    }
}

@Composable
internal fun LiveReviewScreen(
    message: String, onRefresh: () -> Unit, onBack: () -> Unit
) {
    LiveAuthCard {
        Text("มีบิลค้างตรวจสอบ", color = Color(0xFFB13F43),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(9.dp))
        Text(message, color = Color(0xFF1B3151),
            fontSize = 12.sp, lineHeight = 18.sp)
        Text(
            "ตรวจสอบเลขที่บิลและการชำระเงินใน POS Web ก่อนเปิดบิลใหม่ เพื่อป้องกันยอดซ้ำ",
            color = Color(0xFFB13F43), fontSize = 11.sp, lineHeight = 17.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        LiveAction("ตรวจสอบสิทธิ์และสถานะอีกครั้ง", onClick = onRefresh)
        Spacer(modifier = Modifier.height(8.dp))
        LiveBack("กลับหน้าเข้าสู่ระบบ", onClick = onBack)
    }
}

@Composable
private fun brandedTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Color(0xFFBFD5F4),
    unfocusedBorderColor = Color(0xFFBFD5F4),
    focusedContainerColor = Color(0xFFF8FBFF),
    unfocusedContainerColor = Color(0xFFF8FBFF),
    cursorColor = Color(0xFF1682F5)
)
