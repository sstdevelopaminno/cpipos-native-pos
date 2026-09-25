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
    onBack: () -> Unit
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
                    .clickable { onSelect(branch) },
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
        LiveAction("ถัดไป", enabled = selected != null, onClick = onNext)
        Spacer(modifier = Modifier.height(8.dp))
        LiveBack("กลับหน้าเข้าสู่ระบบ", onClick = onBack)
    }
}

@Composable
internal fun LiveEmployeeLoginScreen(
    branchName: String,
    deviceCode: String,
    employeePin: String,
    showPin: Boolean,
    busy: Boolean,
    error: String?,
    onDeviceCode: (String) -> Unit,
    onEmployeePin: (String) -> Unit,
    onTogglePin: () -> Unit,
    onLogin: () -> Unit,
    onBack: () -> Unit
) {
    LiveAuthCard {
        Text(
            "ยืนยันพนักงาน • $branchName",
            modifier = Modifier.fillMaxWidth(),
            color = brandInk, style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(7.dp))
        Text(
            "ใช้รหัสเครื่อง POS และ PIN ที่ลงทะเบียนไว้ในระบบร้านค้า",
            modifier = Modifier.fillMaxWidth(),
            fontSize = 11.sp, lineHeight = 16.sp, color = Color(0xFF718198)
        )
        Spacer(modifier = Modifier.height(13.dp))
        Text(
            "รหัสเครื่อง POS", modifier = Modifier.fillMaxWidth(),
            color = brandInk, fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelLarge
        )
        Spacer(modifier = Modifier.height(7.dp))
        OutlinedTextField(
            value = deviceCode, onValueChange = onDeviceCode,
            placeholder = { Text("รหัสเครื่องที่ลงทะเบียน") },
            leadingIcon = {
                Image(
                    painter = painterResource(R.drawable.ic_pos_terminal),
                    contentDescription = null,
                    modifier = Modifier.size(23.dp)
                )
            },
            singleLine = true, enabled = !busy,
            shape = RoundedCornerShape(12.dp),
            colors = brandedTextFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(13.dp))
        Text(
            "PIN พนักงาน", modifier = Modifier.fillMaxWidth(),
            color = brandInk, fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelLarge
        )
        Spacer(modifier = Modifier.height(7.dp))
        OutlinedTextField(
            value = employeePin, onValueChange = onEmployeePin,
            placeholder = { Text("กรอก PIN พนักงาน") },
            singleLine = true, enabled = !busy,
            visualTransformation = if (showPin) VisualTransformation.None
                else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            trailingIcon = {
                Image(
                    painter = painterResource(
                        if (showPin) R.drawable.ic_eye_off else R.drawable.ic_eye
                    ),
                    contentDescription = "แสดงหรือซ่อน PIN",
                    modifier = Modifier.size(21.dp).clickable { onTogglePin() }
                )
            },
            shape = RoundedCornerShape(12.dp),
            colors = brandedTextFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
        LiveError(error)
        Spacer(modifier = Modifier.height(11.dp))
        LiveAction(
            if (busy) "กำลังตรวจสอบ..." else "เข้าสู่ระบบ",
            enabled = !busy && employeePin.length in 4..12 && deviceCode.length >= 3,
            onClick = onLogin
        )
        Spacer(modifier = Modifier.height(9.dp))
        LiveBack("ย้อนกลับเลือกสาขา", onClick = onBack)
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
