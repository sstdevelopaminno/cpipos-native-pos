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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cpipos.pos.next.R
import com.cpipos.pos.next.auth.AuthAttemptResult
import com.cpipos.pos.next.auth.AuthCredentials
import com.cpipos.pos.next.auth.NativeAuthGateway
import kotlinx.coroutines.launch

private enum class PosPreviewStep(val labelRes: Int) {
    Login(R.string.pos_step_login),
    Branch(R.string.pos_step_branch),
    Sale(R.string.pos_step_sale),
    Checkout(R.string.pos_step_checkout)
}

private data class PreviewBranch(
    val id: String,
    val nameRes: Int,
    val statusRes: Int
)

private data class PreviewProduct(
    val id: String,
    val nameRes: Int,
    val categoryRes: Int,
    val price: Int,
    val color: Color,
    val marker: String
)

private val previewBranches = listOf(
    PreviewBranch("bkk-main", R.string.pos_branch_main, R.string.pos_branch_main_status),
    PreviewBranch("counter-2", R.string.pos_branch_counter2, R.string.pos_branch_counter2_status)
)

private val previewProducts = listOf(
    PreviewProduct("cut-basic", R.string.pos_product_mens_haircut, R.string.pos_category_service, 180, Color(0xFF1F7A8C), "C"),
    PreviewProduct("cut-style", R.string.pos_product_cut_style, R.string.pos_category_service, 250, Color(0xFFBF5B45), "S"),
    PreviewProduct("wash", R.string.pos_product_wash_dry, R.string.pos_category_service, 120, Color(0xFF5B6C5D), "W"),
    PreviewProduct("wax", R.string.pos_product_hair_wax, R.string.pos_category_product, 220, Color(0xFF7C6A46), "W"),
    PreviewProduct("pomade", R.string.pos_product_pomade, R.string.pos_category_product, 320, Color(0xFF6E557D), "P"),
    PreviewProduct("voucher", R.string.pos_product_service_voucher, R.string.pos_category_promotion, 500, Color(0xFF386641), "V")
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
    val previewModeMessage = stringResource(R.string.pos_message_preview_mode)
    val checkingGatewayMessage = stringResource(R.string.pos_message_checking_gateway)
    val realSessionStartedMessage = stringResource(R.string.pos_message_real_session_started)
    val gatewayDisabledMessage = stringResource(R.string.pos_message_gateway_disabled)
    val selectProductsMessage = stringResource(R.string.pos_message_select_products)
    val checkoutPlaceholderMessage = stringResource(R.string.pos_message_checkout_placeholder)
    val newMockBillMessage = stringResource(R.string.pos_message_new_mock_bill)
    var message by remember(previewModeMessage) { mutableStateOf(previewModeMessage) }
    val cart = remember { mutableStateMapOf<String, Int>() }
    val scope = rememberCoroutineScope()

    val totalItems = cart.values.sum()
    val totalPrice = previewProducts.sumOf { product -> product.price * (cart[product.id] ?: 0) }

    MobilePosScaffold(
        step = step,
        isSupabaseConfigured = isSupabaseConfigured,
        branchName = stringResource(branch.nameRes),
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
                    message = checkingGatewayMessage
                    scope.launch {
                        val result = gateway.authenticate(
                            AuthCredentials(storeCode = storeCode.trim(), employeePin = employeePin)
                        )
                        employeePin = ""
                        isSubmitting = false
                        message = when (result) {
                            is AuthAttemptResult.Success -> realSessionStartedMessage
                            is AuthAttemptResult.Rejected -> result.message
                            is AuthAttemptResult.BackendUnavailable -> gatewayDisabledMessage
                        }
                        step = PosPreviewStep.Branch
                    }
                }
            )

            PosPreviewStep.Branch -> BranchPanel(
                selectedBranch = branch,
                onSelect = { selected -> branch = selected },
                onContinue = {
                    message = selectProductsMessage
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
                    message = checkoutPlaceholderMessage
                    step = PosPreviewStep.Checkout
                }
            )

            PosPreviewStep.Checkout -> CheckoutPanel(
                totalItems = totalItems,
                totalPrice = totalPrice,
                onBack = { step = PosPreviewStep.Sale },
                onReset = {
                    cart.clear()
                    message = newMockBillMessage
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
                stepLabel = stringResource(step.labelRes),
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
    stepLabel: String,
    isSupabaseConfigured: Boolean,
    branchName: String
) {
    Column {
        Text(
            text = stringResource(R.string.pos_title_app),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF18221D)
        )
        Text(
            text = stringResource(R.string.pos_subtitle_app),
            style = MaterialTheme.typography.titleSmall,
            color = Color(0xFF617064)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            StatusPill(text = stepLabel, color = Color(0xFF1F7A8C), modifier = Modifier.weight(1f))
            StatusPill(
                text = if (isSupabaseConfigured) {
                    stringResource(R.string.pos_status_supabase_ready)
                } else {
                    stringResource(R.string.pos_status_mock_mode)
                },
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
            Text(stringResource(R.string.pos_title_sign_in), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = storeCode,
                onValueChange = onStoreCodeChange,
                label = { Text(stringResource(R.string.pos_label_store_code)) },
                enabled = !isSubmitting,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = employeePin,
                onValueChange = onPinChange,
                label = { Text(stringResource(R.string.pos_label_employee_pin)) },
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
                Text(
                    if (isSubmitting) {
                        stringResource(R.string.pos_action_checking)
                    } else {
                        stringResource(R.string.pos_action_open_preview)
                    }
                )
            }
            Text(
                text = stringResource(R.string.pos_hint_pin_cleared),
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
            Text(stringResource(R.string.pos_title_select_branch), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        items(previewBranches) { branch ->
            SelectableCard(
                title = stringResource(branch.nameRes),
                subtitle = stringResource(branch.statusRes),
                selected = branch.id == selectedBranch.id,
                onClick = { onSelect(branch) }
            )
        }
        item {
            Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.pos_action_go_to_sale))
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
    val totalText = formatBaht(totalPrice)
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.pos_title_sale), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.pos_items_summary, totalItems, totalText), style = MaterialTheme.typography.bodyMedium)
                }
                Button(onClick = onCheckout, enabled = totalItems > 0) { Text(stringResource(R.string.pos_action_pay)) }
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
    val categoryText = stringResource(product.categoryRes)
    val priceText = formatBaht(product.price)
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(8.dp)) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(product.color),
                contentAlignment = Alignment.Center
            ) {
                Text(product.marker, color = Color.White, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(product.nameRes), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.pos_product_detail, categoryText, priceText), style = MaterialTheme.typography.bodySmall)
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
            Text(stringResource(R.string.pos_title_checkout), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            SummaryLine(stringResource(R.string.pos_label_items), "$totalItems")
            SummaryLine(stringResource(R.string.pos_label_total), formatBaht(totalPrice))
            HorizontalDivider()
            Text(
                text = stringResource(R.string.pos_checkout_next_phase),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF66736A)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.pos_action_back)) }
                Button(onClick = onReset, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.pos_action_finish_mock_bill)) }
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

@Composable
private fun formatBaht(value: Int): String = stringResource(R.string.pos_currency_amount, value)

private object PreviewNativeAuthGateway : NativeAuthGateway {
    override suspend fun authenticate(credentials: AuthCredentials): AuthAttemptResult {
        return AuthAttemptResult.BackendUnavailable(
            message = "Preview mock gateway only. No production auth request is sent."
        )
    }
}

@Preview(name = "Mobile POS - Login", showBackground = true, widthDp = 390, heightDp = 844, locale = "th")
@Composable
private fun MobilePosLoginPreview() {
    MaterialTheme {
        MobilePosPreviewScreen(
            isSupabaseConfigured = false,
            gateway = PreviewNativeAuthGateway
        )
    }
}

@Preview(name = "Mobile POS - Branch", showBackground = true, widthDp = 390, heightDp = 844, locale = "th")
@Composable
private fun MobilePosBranchPreview() {
    MaterialTheme {
        MobilePosScaffold(
            step = PosPreviewStep.Branch,
            isSupabaseConfigured = true,
            branchName = stringResource(previewBranches.first().nameRes),
            message = stringResource(R.string.pos_preview_message_branch)
        ) {
            BranchPanel(
                selectedBranch = previewBranches.first(),
                onSelect = {},
                onContinue = {}
            )
        }
    }
}

@Preview(name = "Mobile POS - Sale", showBackground = true, widthDp = 390, heightDp = 844, locale = "th")
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
            branchName = stringResource(previewBranches.first().nameRes),
            message = stringResource(R.string.pos_preview_message_sale)
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

@Preview(name = "Mobile POS - Checkout", showBackground = true, widthDp = 390, heightDp = 844, locale = "th")
@Composable
private fun MobilePosCheckoutPreview() {
    MaterialTheme {
        MobilePosScaffold(
            step = PosPreviewStep.Checkout,
            isSupabaseConfigured = true,
            branchName = stringResource(previewBranches.first().nameRes),
            message = stringResource(R.string.pos_preview_message_checkout)
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
