package com.cpipos.pos.next.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.cpipos.pos.next.auth.AuthAttemptResult
import com.cpipos.pos.next.auth.AuthCredentials
import com.cpipos.pos.next.auth.NativeAuthGateway
import kotlinx.coroutines.launch

private enum class PosPreviewStep(val label: String) {
    Login("Login"), Branch("Branch"), Sale("Sale"), Checkout("Checkout")
}

private data class PreviewBranch(
    val id: String,
    val name: String,
    val status: String
)

private data class PreviewProduct(
    val id: String,
    val name: String,
    val category: String,
    val price: Int,
    val color: Color
)

private val previewBranches = listOf(
    PreviewBranch("bkk-main", "สาขาหลัก", "พร้อมขาย"),
    PreviewBranch("counter-2", "เคาน์เตอร์ 2", "จำลองข้อมูล")
)

private val previewProducts = listOf(
    PreviewProduct("cut-basic", "ตัดผมชาย", "บริการ", 180, Color(0xFF1F7A8C)),
    PreviewProduct("cut-style", "ตัด+เซ็ต", "บริการ", 250, Color(0xFFBF5B45)),
    PreviewProduct("wash", "สระไดร์", "บริการ", 120, Color(0xFF5B6C5D)),
    PreviewProduct("wax", "แว็กซ์ผม", "สินค้า", 220, Color(0xFF7C6A46)),
    PreviewProduct("pomade", "โพเมด", "สินค้า", 320, Color(0xFF6E557D)),
    PreviewProduct("voucher", "คูปองบริการ", "โปรโมชั่น", 500, Color(0xFF386641))
)

@Composable
fun MobilePosPreviewScreen(
    isSupabaseConfigured: Boolean,
    gateway: NativeAuthGateway
) {
    var step by remember { mutableStateOf(PosPreviewStep.Login) }
    var storeCode by remember { mutableStateOf("") }
    var employeePin by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var branch by remember { mutableStateOf(previewBranches.first()) }
    var message by remember {
        mutableStateOf("โหมด APK Preview: ทดสอบ UI มือถือด้วย mock data ยังไม่เขียนข้อมูล Production")
    }
    val cart = remember { mutableStateMapOf<String, Int>() }
    val scope = rememberCoroutineScope()

    val totalItems = cart.values.sum()
    val totalPrice = previewProducts.sumOf { product -> product.price * (cart[product.id] ?: 0) }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF6F7F2)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 18.dp)
        ) {
            AppHeader(
                step = step,
                isSupabaseConfigured = isSupabaseConfigured,
                branchName = branch.name
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF4C5A4F)
            )

            Spacer(modifier = Modifier.height(12.dp))

            when (step) {
                PosPreviewStep.Login -> LoginPanel(
                    storeCode = storeCode,
                    employeePin = employeePin,
                    isSubmitting = isSubmitting,
                    onStoreCodeChange = { storeCode = it.trimStart() },
                    onPinChange = { value -> employeePin = value.filter(Char::isDigit).take(12) },
                    onSubmit = {
                        isSubmitting = true
                        message = "กำลังตรวจสอบ Secure Auth Gateway..."
                        scope.launch {
                            val result = gateway.authenticate(
                                AuthCredentials(storeCode = storeCode.trim(), employeePin = employeePin)
                            )
                            employeePin = ""
                            isSubmitting = false
                            message = when (result) {
                                is AuthAttemptResult.Success -> "เข้าสู่ session จริงสำเร็จ"
                                is AuthAttemptResult.Rejected -> result.message
                                is AuthAttemptResult.BackendUnavailable ->
                                    "Secure gateway ยังปิดไว้ จึงเปิดโหมดจำลองสำหรับทดสอบ UI มือถือ"
                            }
                            step = PosPreviewStep.Branch
                        }
                    }
                )

                PosPreviewStep.Branch -> BranchPanel(
                    selectedBranch = branch,
                    onSelect = { selected -> branch = selected },
                    onContinue = {
                        message = "เลือกสินค้าและทดสอบตะกร้าแบบ local mock"
                        step = PosPreviewStep.Sale
                    }
                )

                PosPreviewStep.Sale -> SalePanel(
                    cart = cart,
                    totalItems = totalItems,
                    totalPrice = totalPrice,
                    onAdd = { product -> cart[product.id] = (cart[product.id] ?: 0) + 1 },
                    onRemove = { product ->
                        val next = (cart[product.id] ?: 0) - 1
                        if (next <= 0) cart.remove(product.id) else cart[product.id] = next
                    },
                    onCheckout = {
                        message = "ชำระเงินเป็น placeholder ยังไม่สร้างออเดอร์/ชำระเงินจริง"
                        step = PosPreviewStep.Checkout
                    }
                )

                PosPreviewStep.Checkout -> CheckoutPanel(
                    totalItems = totalItems,
                    totalPrice = totalPrice,
                    onBack = { step = PosPreviewStep.Sale },
                    onReset = {
                        cart.clear()
                        message = "เริ่มบิลจำลองใหม่แล้ว"
                        step = PosPreviewStep.Sale
                    }
                )
            }
        }
    }
}

@Composable
private fun AppHeader(
    step: PosPreviewStep,
    isSupabaseConfigured: Boolean,
    branchName: String
) {
    Column {
        Text(
            text = "CpIPOS Native",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF18221D)
        )
        Text(
            text = "Mobile POS Preview APK",
            style = MaterialTheme.typography.titleSmall,
            color = Color(0xFF617064)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            StatusPill(text = step.label, color = Color(0xFF1F7A8C), modifier = Modifier.weight(1f))
            StatusPill(
                text = if (isSupabaseConfigured) "Supabase ready" else "Mock mode",
                color = if (isSupabaseConfigured) Color(0xFF386641) else Color(0xFF7C6A46),
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        StatusPill(text = branchName, color = Color(0xFF4C5A4F), modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun StatusPill(text: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .padding(horizontal = 10.dp, vertical = 9.dp)
    ) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = text, style = MaterialTheme.typography.labelMedium, color = Color(0xFF29322C))
    }
}

@Composable
private fun LoginPanel(
    storeCode: String,
    employeePin: String,
    isSubmitting: Boolean,
    onStoreCodeChange: (String) -> Unit,
    onPinChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(8.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("เข้าใช้งาน", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = storeCode,
                onValueChange = onStoreCodeChange,
                label = { Text("รหัสร้าน") },
                enabled = !isSubmitting,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = employeePin,
                onValueChange = onPinChange,
                label = { Text("PIN พนักงาน") },
                enabled = !isSubmitting,
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = onSubmit,
                enabled = storeCode.isNotBlank() && employeePin.isNotBlank() && !isSubmitting,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isSubmitting) "กำลังตรวจสอบ..." else "เปิด POS Preview")
            }
            Text(
                text = "PIN จะถูกล้างหลัง attempt และยังไม่มีการส่งข้อมูลไปสร้าง session production",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF66736A)
            )
        }
    }
}

@Composable
private fun BranchPanel(
    selectedBranch: PreviewBranch,
    onSelect: (PreviewBranch) -> Unit,
    onContinue: () -> Unit
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("เลือกสาขา", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        items(previewBranches) { branch ->
            SelectableCard(
                title = branch.name,
                subtitle = branch.status,
                selected = branch.id == selectedBranch.id,
                onClick = { onSelect(branch) }
            )
        }
        item {
            Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
                Text("ไปหน้าขาย")
            }
        }
    }
}

@Composable
private fun SalePanel(
    cart: Map<String, Int>,
    totalItems: Int,
    totalPrice: Int,
    onAdd: (PreviewProduct) -> Unit,
    onRemove: (PreviewProduct) -> Unit,
    onCheckout: () -> Unit
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("ขายสินค้า", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("$totalItems รายการ / ${formatBaht(totalPrice)}", style = MaterialTheme.typography.bodyMedium)
                }
                Button(onClick = onCheckout, enabled = totalItems > 0) { Text("ชำระ") }
            }
        }
        items(previewProducts) { product ->
            ProductRow(
                product = product,
                quantity = cart[product.id] ?: 0,
                onAdd = { onAdd(product) },
                onRemove = { onRemove(product) }
            )
        }
    }
}

@Composable
private fun ProductRow(
    product: PreviewProduct,
    quantity: Int,
    onAdd: () -> Unit,
    onRemove: () -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(8.dp)) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(product.color),
                contentAlignment = Alignment.Center
            ) {
                Text(product.name.first().toString(), color = Color.White, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(product.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("${product.category} • ${formatBaht(product.price)}", style = MaterialTheme.typography.bodySmall)
            }
            if (quantity > 0) {
                OutlinedButton(onClick = onRemove) { Text("-") }
                Text("$quantity", modifier = Modifier.padding(horizontal = 8.dp), fontWeight = FontWeight.Bold)
            }
            Button(onClick = onAdd) { Text("+") }
        }
    }
}

@Composable
private fun CheckoutPanel(
    totalItems: Int,
    totalPrice: Int,
    onBack: () -> Unit,
    onReset: () -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(8.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("สรุปชำระเงิน", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            SummaryLine("จำนวนรายการ", "$totalItems")
            SummaryLine("ยอดรวม", formatBaht(totalPrice))
            HorizontalDivider()
            Text(
                text = "ขั้นนี้เป็น placeholder สำหรับต่อ Sale RPC, Payment, Receipt และ Offline queue ใน phase ถัดไป",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF66736A)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("ย้อนกลับ") }
                Button(onClick = onReset, modifier = Modifier.weight(1f)) { Text("จบบิลจำลอง") }
            }
        }
    }
}

@Composable
private fun SummaryLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color(0xFF66736A))
        Text(value, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SelectableCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (selected) Color(0xFF1F7A8C) else Color.Transparent
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = if (selected) Color(0xFFE8F2F0) else Color.White),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(borderColor))
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color(0xFF66736A))
            }
        }
    }
}

private fun formatBaht(value: Int): String = "฿%,d".format(value)
