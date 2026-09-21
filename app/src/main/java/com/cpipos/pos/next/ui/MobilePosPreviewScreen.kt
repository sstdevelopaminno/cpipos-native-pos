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
import androidx.compose.ui.tooling.preview.Preview
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
    PreviewBranch("bkk-main", "Main Branch", "Ready for sales"),
    PreviewBranch("counter-2", "Counter 2", "Mock data")
)

private val previewProducts = listOf(
    PreviewProduct("cut-basic", "Men's Haircut", "Service", 180, Color(0xFF1F7A8C)),
    PreviewProduct("cut-style", "Cut + Style", "Service", 250, Color(0xFFBF5B45)),
    PreviewProduct("wash", "Wash + Blow Dry", "Service", 120, Color(0xFF5B6C5D)),
    PreviewProduct("wax", "Hair Wax", "Product", 220, Color(0xFF7C6A46)),
    PreviewProduct("pomade", "Pomade", "Product", 320, Color(0xFF6E557D)),
    PreviewProduct("voucher", "Service Voucher", "Promotion", 500, Color(0xFF386641))
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
        mutableStateOf("APK Preview mode: test mobile UI with mock data. No production writes.")
    }
    val cart = remember { mutableStateMapOf<String, Int>() }
    val scope = rememberCoroutineScope()

    val totalItems = cart.values.sum()
    val totalPrice = previewProducts.sumOf { product -> product.price * (cart[product.id] ?: 0) }

    MobilePosScaffold(
        step = step,
        isSupabaseConfigured = isSupabaseConfigured,
        branchName = branch.name,
        message = message
    ) {
        when (step) {
            PosPreviewStep.Login -> LoginPanel(
                storeCode = storeCode,
                employeePin = employeePin,
                isSubmitting = isSubmitting,
                onStoreCodeChange = { storeCode = it.trimStart() },
                onPinChange = { value -> employeePin = value.filter(Char::isDigit).take(12) },
                onSubmit = {
                    isSubmitting = true
                    message = "Checking Secure Auth Gateway..."
                    scope.launch {
                        val result = gateway.authenticate(
                            AuthCredentials(storeCode = storeCode.trim(), employeePin = employeePin)
                        )
                        employeePin = ""
                        isSubmitting = false
                        message = when (result) {
                            is AuthAttemptResult.Success -> "Real session started."
                            is AuthAttemptResult.Rejected -> result.message
                            is AuthAttemptResult.BackendUnavailable ->
                                "Secure gateway is disabled, so the app continues in mobile UI mock mode."
                        }
                        step = PosPreviewStep.Branch
                    }
                }
            )

            PosPreviewStep.Branch -> BranchPanel(
                selectedBranch = branch,
                onSelect = { selected -> branch = selected },
                onContinue = {
                    message = "Select products and test the local mock cart."
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
                    message = "Checkout is a placeholder. No real order or payment is created."
                    step = PosPreviewStep.Checkout
                }
            )

            PosPreviewStep.Checkout -> CheckoutPanel(
                totalItems = totalItems,
                totalPrice = totalPrice,
                onBack = { step = PosPreviewStep.Sale },
                onReset = {
                    cart.clear()
                    message = "Started a new mock bill."
                    step = PosPreviewStep.Sale
                }
            )
        }
    }
}

@Composable
private fun MobilePosScaffold(
    step: PosPreviewStep,
    isSupabaseConfigured: Boolean,
    branchName: String,
    message: String,
    content: @Composable () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF6F7F2)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 18.dp)
        ) {
            AppHeader(
                step = step,
                isSupabaseConfigured = isSupabaseConfigured,
                branchName = branchName
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF4C5A4F)
            )

            Spacer(modifier = Modifier.height(12.dp))

            content()
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
            Text("Sign in", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = storeCode,
                onValueChange = onStoreCodeChange,
                label = { Text("Store code") },
                enabled = !isSubmitting,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = employeePin,
                onValueChange = onPinChange,
                label = { Text("Employee PIN") },
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
                Text(if (isSubmitting) "Checking..." else "Open POS Preview")
            }
            Text(
                text = "The PIN is cleared after each attempt. This preview does not create a production session.",
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
            Text("Select branch", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
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
                Text("Go to sale")
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
                    Text("Sale", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("$totalItems items / ${formatBaht(totalPrice)}", style = MaterialTheme.typography.bodyMedium)
                }
                Button(onClick = onCheckout, enabled = totalItems > 0) { Text("Pay") }
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
                Text("${product.category} - ${formatBaht(product.price)}", style = MaterialTheme.typography.bodySmall)
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
            Text("Checkout", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            SummaryLine("Items", "$totalItems")
            SummaryLine("Total", formatBaht(totalPrice))
            HorizontalDivider()
            Text(
                text = "Placeholder for the next phase: Sale RPC, payment, receipt, and offline queue.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF66736A)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Back") }
                Button(onClick = onReset, modifier = Modifier.weight(1f)) { Text("Finish mock bill") }
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
    val markerColor = if (selected) Color(0xFF1F7A8C) else Color.Transparent
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = if (selected) Color(0xFFE8F2F0) else Color.White),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(markerColor))
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color(0xFF66736A))
            }
        }
    }
}

private fun formatBaht(value: Int): String = "THB %,d".format(value)

private object PreviewNativeAuthGateway : NativeAuthGateway {
    override suspend fun authenticate(credentials: AuthCredentials): AuthAttemptResult {
        return AuthAttemptResult.BackendUnavailable(
            message = "Preview mock gateway only. No production auth request is sent."
        )
    }
}

@Preview(name = "Mobile POS - Login", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun MobilePosLoginPreview() {
    MaterialTheme {
        MobilePosPreviewScreen(
            isSupabaseConfigured = false,
            gateway = PreviewNativeAuthGateway
        )
    }
}

@Preview(name = "Mobile POS - Branch", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun MobilePosBranchPreview() {
    MaterialTheme {
        MobilePosScaffold(
            step = PosPreviewStep.Branch,
            isSupabaseConfigured = true,
            branchName = previewBranches.first().name,
            message = "Supabase configuration is present. Real auth remains guarded."
        ) {
            BranchPanel(
                selectedBranch = previewBranches.first(),
                onSelect = {},
                onContinue = {}
            )
        }
    }
}

@Preview(name = "Mobile POS - Sale", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun MobilePosSalePreview() {
    val previewCart = mapOf(
        "cut-basic" to 1,
        "pomade" to 2
    )
    val totalItems = previewCart.values.sum()
    val totalPrice = previewProducts.sumOf { product -> product.price * (previewCart[product.id] ?: 0) }

    MaterialTheme {
        MobilePosScaffold(
            step = PosPreviewStep.Sale,
            isSupabaseConfigured = true,
            branchName = previewBranches.first().name,
            message = "Mock cart preview for mobile POS layout review."
        ) {
            SalePanel(
                cart = previewCart,
                totalItems = totalItems,
                totalPrice = totalPrice,
                onAdd = {},
                onRemove = {},
                onCheckout = {}
            )
        }
    }
}

@Preview(name = "Mobile POS - Checkout", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun MobilePosCheckoutPreview() {
    MaterialTheme {
        MobilePosScaffold(
            step = PosPreviewStep.Checkout,
            isSupabaseConfigured = true,
            branchName = previewBranches.first().name,
            message = "Checkout preview only. No order or payment is created."
        ) {
            CheckoutPanel(
                totalItems = 3,
                totalPrice = 820,
                onBack = {},
                onReset = {}
            )
        }
    }
}
