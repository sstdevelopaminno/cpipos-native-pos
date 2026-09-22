package com.cpipos.pos.next.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cpipos.pos.next.R
import com.cpipos.pos.next.auth.AuthAttemptResult
import com.cpipos.pos.next.auth.AuthCredentials
import com.cpipos.pos.next.auth.NativeAuthGateway
import kotlinx.coroutines.launch

private enum class PosPreviewStep(val labelRes: Int) {
    Login(R.string.pos_step_login),
    Branch(R.string.pos_step_branch),
    EmployeePin(R.string.pos_step_employee_pin),
    Counter(R.string.pos_step_counter),
    Sale(R.string.pos_step_sale),
    SaleFlow(R.string.pos_step_sale),
    Checkout(R.string.pos_step_checkout)
}

private data class PreviewBranch(
    val id: String,
    val nameRes: Int,
    val code: String
)

private data class PreviewCounter(
    val id: String,
    val titleRes: Int,
    val code: String,
    val operatorName: String,
    val isReady: Boolean
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
    PreviewBranch("phetchaburi", R.string.pos_branch_phetchaburi, "NDL-PHET-02"),
    PreviewBranch("onnut", R.string.pos_branch_onnut, "NDL-ONNUT-01")
)

private val previewCounters = listOf(
    PreviewCounter("counter-01", R.string.pos_counter_phetchaburi_1, "POS-COUNTER-01", "-", true),
    PreviewCounter("counter-02", R.string.pos_counter_phetchaburi_2, "002", "POR", true)
)

private val previewProducts = listOf(
    PreviewProduct("cut-basic", R.string.pos_product_mens_haircut, R.string.pos_category_service, 180, Color(0xFF1F7A8C), "C"),
    PreviewProduct("cut-style", R.string.pos_product_cut_style, R.string.pos_category_service, 250, Color(0xFFBF5B45), "S"),
    PreviewProduct("wash", R.string.pos_product_wash_dry, R.string.pos_category_service, 120, Color(0xFF5B6C5D), "W"),
    PreviewProduct("wax", R.string.pos_product_hair_wax, R.string.pos_category_product, 220, Color(0xFF7C6A46), "W"),
    PreviewProduct("pomade", R.string.pos_product_pomade, R.string.pos_category_product, 320, Color(0xFF6E557D), "P"),
    PreviewProduct("voucher", R.string.pos_product_service_voucher, R.string.pos_category_promotion, 500, Color(0xFF386641), "V")
)

private enum class LoginLanguage {
    Thai,
    English
}

@Composable
fun MobilePosPreviewScreen(
    isSupabaseConfigured: Boolean,
    gateway: NativeAuthGateway
) {
    var step by remember { mutableStateOf(PosPreviewStep.Login) }
    var storeCode by remember { mutableStateOf("") }
    var employeePin by remember { mutableStateOf("") }
    var isPinVisible by remember { mutableStateOf(false) }
    var selectedLanguage by remember { mutableStateOf(LoginLanguage.Thai) }
    var isSubmitting by remember { mutableStateOf(false) }
    var branch by remember { mutableStateOf(previewBranches.first()) }
    var counter by remember { mutableStateOf(previewCounters.first()) }
    var saleMode by remember { mutableStateOf(MockSaleMode.Takeaway) }
    val checkingGatewayMessage = stringResource(R.string.pos_message_checking_gateway)
    val realSessionStartedMessage = stringResource(R.string.pos_message_real_session_started)
    val gatewayDisabledMessage = stringResource(R.string.pos_message_gateway_disabled)
    val selectProductsMessage = stringResource(R.string.pos_message_select_products)
    val checkoutPlaceholderMessage = stringResource(R.string.pos_message_checkout_placeholder)
    val newMockBillMessage = stringResource(R.string.pos_message_new_mock_bill)
    var message by remember { mutableStateOf(gatewayDisabledMessage) }
    val cart = remember { mutableStateMapOf<String, Int>() }
    val scope = rememberCoroutineScope()

    val totalItems = cart.values.sum()
    val totalPrice = previewProducts.sumOf { product -> product.price * (cart[product.id] ?: 0) }

    when (step) {
        PosPreviewStep.Login -> LoginLandingScreen(
            storeCode = storeCode,
            isSubmitting = isSubmitting,
            selectedLanguage = selectedLanguage,
            onLanguageSelected = { selectedLanguage = it },
            onStoreCodeChange = { value -> storeCode = value.trimStart().take(32) },
            onSubmit = {
                branch = previewBranches.first()
                step = PosPreviewStep.Branch
            }
        )

        PosPreviewStep.Branch -> BranchSelectionScreen(
            branches = previewBranches,
            selectedBranch = branch,
            onSelect = { selected -> branch = selected },
            onBack = { step = PosPreviewStep.Login },
            onNext = {
                employeePin = ""
                isPinVisible = false
                step = PosPreviewStep.EmployeePin
            }
        )

        PosPreviewStep.EmployeePin -> EmployeePinScreen(
            branch = branch,
            employeePin = employeePin,
            isPinVisible = isPinVisible,
            isSubmitting = isSubmitting,
            selectedLanguage = selectedLanguage,
            onLanguageSelected = { selectedLanguage = it },
            onPinChange = { value -> employeePin = value.filter(Char::isDigit).take(12) },
            onTogglePinVisible = { isPinVisible = !isPinVisible },
            onBack = { step = PosPreviewStep.Branch },
            onSubmit = {
                isSubmitting = true
                message = checkingGatewayMessage
                scope.launch {
                    val result = gateway.authenticate(
                        AuthCredentials(storeCode = storeCode.trim(), employeePin = employeePin)
                    )
                    employeePin = ""
                    isPinVisible = false
                    isSubmitting = false
                    message = when (result) {
                        is AuthAttemptResult.Success -> realSessionStartedMessage
                        is AuthAttemptResult.Rejected -> result.message
                        is AuthAttemptResult.BackendUnavailable -> gatewayDisabledMessage
                    }
                    counter = previewCounters.first()
                    step = PosPreviewStep.Counter
                }
            }
        )

        PosPreviewStep.Counter -> CounterSelectionScreen(
            branch = branch,
            counters = previewCounters,
            selectedCounter = counter,
            onSelect = { selected -> counter = selected },
            onBack = { step = PosPreviewStep.EmployeePin },
            onOpenCounter = {
                message = selectProductsMessage
                step = PosPreviewStep.Sale
            }
        )

        PosPreviewStep.Sale -> HomeModeScreen(
            branch = branch,
            counter = counter,
            onSelectMode = { selected ->
                saleMode = selected
                step = PosPreviewStep.SaleFlow
            }
        )

        PosPreviewStep.SaleFlow -> MobilePosSalePreview(
            mode = saleMode,
            branchName = stringResource(branch.nameRes),
            counterCode = counter.code,
            onBack = { step = PosPreviewStep.Sale }
        )

        PosPreviewStep.Checkout -> MobilePosScaffold(
            step = step,
            isSupabaseConfigured = isSupabaseConfigured,
            branchName = stringResource(branch.nameRes),
            message = message
        ) {
            CheckoutPanel(
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
private fun LoginLandingScreen(
    storeCode: String,
    isSubmitting: Boolean,
    selectedLanguage: LoginLanguage,
    onLanguageSelected: (LoginLanguage) -> Unit,
    onStoreCodeChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    val isThai = selectedLanguage == LoginLanguage.Thai
    val storeCodeLabel = if (isThai) stringResource(R.string.pos_label_store_code_full) else "Store code"
    val storeCodePlaceholder = if (isThai) stringResource(R.string.pos_placeholder_store_code) else "Enter store code"
    val checkingText = if (isThai) stringResource(R.string.pos_action_checking) else "Checking..."
    val loginText = if (isThai) stringResource(R.string.pos_action_login) else "Log in"

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF3F7FE)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp, vertical = 34.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        LanguageToggle(
                            selectedLanguage = selectedLanguage,
                            onLanguageSelected = onLanguageSelected
                        )
                    }

                    Spacer(modifier = Modifier.height(26.dp))

                    Image(
                        painter = painterResource(R.drawable.cpipos_logo_symbol),
                        contentDescription = stringResource(R.string.pos_logo_content_description),
                        modifier = Modifier.size(82.dp)
                    )

                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(color = Color(0xFF465263))) { append("Cp") }
                            withStyle(SpanStyle(color = Color(0xFF1682F5))) { append("IPOS") }
                        },
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 6.dp)
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = storeCodeLabel,
                            color = Color(0xFF113C73),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = storeCode,
                            onValueChange = onStoreCodeChange,
                            placeholder = { Text(storeCodePlaceholder) },
                            leadingIcon = {
                                Image(
                                    painter = painterResource(R.drawable.ic_store_front),
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            singleLine = true,
                            enabled = !isSubmitting,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFBFD5F4),
                                unfocusedBorderColor = Color(0xFFBFD5F4),
                                focusedContainerColor = Color(0xFFF8FBFF),
                                unfocusedContainerColor = Color(0xFFF8FBFF),
                                cursorColor = Color(0xFF1682F5)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text(
                            text = stringResource(R.string.pos_store_code_counter, storeCode.length),
                            color = Color(0xFF6C7A90),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = onSubmit,
                            enabled = storeCode.isNotBlank() && !isSubmitting,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1F75D6),
                                disabledContainerColor = Color(0xFFD8E7F7),
                                contentColor = Color.White,
                                disabledContentColor = Color.White
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Text(
                                text = if (isSubmitting) checkingText else loginText,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LanguageToggle(
    selectedLanguage: LoginLanguage,
    onLanguageSelected: (LoginLanguage) -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xFFF5F9FF))
            .border(1.dp, Color(0xFFD3E2F5), RoundedCornerShape(22.dp))
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LanguageOption(
            text = stringResource(R.string.pos_language_th),
            selected = selectedLanguage == LoginLanguage.Thai,
            onClick = { onLanguageSelected(LoginLanguage.Thai) }
        )
        LanguageOption(
            text = stringResource(R.string.pos_language_en),
            selected = selectedLanguage == LoginLanguage.English,
            onClick = { onLanguageSelected(LoginLanguage.English) }
        )
    }
}

@Composable
private fun LanguageOption(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Text(
        text = text,
        color = if (selected) Color(0xFF0E3C78) else Color(0xFF254B78),
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) Color.White else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}

@Composable
private fun BranchSelectionScreen(
    branches: List<PreviewBranch>,
    selectedBranch: PreviewBranch,
    onSelect: (PreviewBranch) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    BrandedAuthSurface {
        Column(modifier = Modifier.padding(horizontal = 22.dp, vertical = 22.dp)) {
            BrandLogoBlock()
            Spacer(modifier = Modifier.height(28.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier
                        .border(1.dp, Color(0xFFD4E1F2), RoundedCornerShape(14.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.pos_title_select_branch),
                        color = Color(0xFF113C73),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    branches.forEach { branch ->
                        BranchOptionCard(
                            branch = branch,
                            selected = branch.id == selectedBranch.id,
                            onClick = { onSelect(branch) }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.pos_action_logout))
                }
                Button(
                    onClick = onNext,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F75D6)),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.pos_action_next), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun EmployeePinScreen(
    branch: PreviewBranch,
    employeePin: String,
    isPinVisible: Boolean,
    isSubmitting: Boolean,
    selectedLanguage: LoginLanguage,
    onLanguageSelected: (LoginLanguage) -> Unit,
    onPinChange: (String) -> Unit,
    onTogglePinVisible: () -> Unit,
    onBack: () -> Unit,
    onSubmit: () -> Unit
) {
    val isThai = selectedLanguage == LoginLanguage.Thai
    val pinLabel = if (isThai) stringResource(R.string.pos_label_employee_code) else "Employee code"
    val confirmText = if (isThai) stringResource(R.string.pos_action_confirm_employee) else "Confirm employee"
    val checkingText = if (isThai) stringResource(R.string.pos_action_checking) else "Checking..."

    BrandedAuthSurface {
        Column(modifier = Modifier.padding(horizontal = 22.dp, vertical = 22.dp)) {
            BrandLogoBlock()
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                LanguageToggle(
                    selectedLanguage = selectedLanguage,
                    onLanguageSelected = onLanguageSelected
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StoreIcon()
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.pos_selected_branch, stringResource(branch.nameRes)),
                    color = Color(0xFF113C73),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = pinLabel,
                color = Color(0xFF113C73),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = employeePin,
                onValueChange = onPinChange,
                trailingIcon = {
                    Image(
                        painter = painterResource(if (isPinVisible) R.drawable.ic_eye_off else R.drawable.ic_eye),
                        contentDescription = stringResource(R.string.pos_pin_visibility_content_description),
                        modifier = Modifier
                            .size(22.dp)
                            .clickable(onClick = onTogglePinVisible)
                    )
                },
                singleLine = true,
                enabled = !isSubmitting,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                visualTransformation = if (isPinVisible) VisualTransformation.None else PasswordVisualTransformation(),
                shape = RoundedCornerShape(12.dp),
                colors = cpiposTextFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            PrimaryActionButton(
                text = if (isSubmitting) checkingText else confirmText,
                enabled = employeePin.isNotBlank() && !isSubmitting,
                onClick = onSubmit
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = onBack, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.pos_action_back))
            }
        }
    }
}

@Composable
private fun CounterSelectionScreen(
    branch: PreviewBranch,
    counters: List<PreviewCounter>,
    selectedCounter: PreviewCounter,
    onSelect: (PreviewCounter) -> Unit,
    onBack: () -> Unit,
    onOpenCounter: () -> Unit
) {
    BrandedAuthSurface {
        Column(modifier = Modifier.padding(horizontal = 22.dp, vertical = 22.dp)) {
            BrandLogoBlock()
            Spacer(modifier = Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StoreIcon()
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.pos_selected_branch, stringResource(branch.nameRes)),
                    color = Color(0xFF113C73),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier
                        .border(1.dp, Color(0xFFD4E1F2), RoundedCornerShape(14.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.pos_title_select_counter),
                        color = Color(0xFF113C73),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        counters.forEach { counter ->
                            CounterOptionCard(
                                counter = counter,
                                selected = counter.id == selectedCounter.id,
                                onClick = { onSelect(counter) }
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.pos_action_back))
                }
                Button(
                    onClick = onOpenCounter,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F75D6)),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.pos_action_open_counter), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun BranchOptionCard(branch: PreviewBranch, selected: Boolean, onClick: () -> Unit) {
    val borderColor = if (selected) Color(0xFF2577FF) else Color(0xFFD4E1F2)
    val backgroundColor = if (selected) Color(0xFFEAF3FF) else Color.White
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(if (selected) 2.dp else 1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            StoreIcon()
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(stringResource(branch.nameRes), color = Color(0xFF243349), fontWeight = FontWeight.Bold)
                Text(
                    text = stringResource(R.string.pos_branch_code, branch.code),
                    color = Color(0xFF365B86),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun CounterOptionCard(
    counter: PreviewCounter,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (selected) Color(0xFF2577FF) else Color(0xFFD4E1F2)
    val backgroundColor = if (selected) Color(0xFFEAF3FF) else Color.White
    Card(
        modifier = modifier
            .border(if (selected) 2.dp else 1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            TerminalIcon()
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.pos_counter_code, counter.code),
                color = Color(0xFF243349),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
@Composable
private fun BrandedAuthSurface(content: @Composable () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF3F7FE)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp, vertical = 34.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
private fun BrandLogoBlock(topPadding: Int = 18) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = topPadding.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(R.drawable.cpipos_logo_symbol),
            contentDescription = stringResource(R.string.pos_logo_content_description),
            modifier = Modifier.size(82.dp)
        )
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = Color(0xFF465263))) { append("Cp") }
                withStyle(SpanStyle(color = Color(0xFF1682F5))) { append("IPOS") }
            },
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
private fun StoreIcon() {
    Image(
        painter = painterResource(R.drawable.ic_store_front),
        contentDescription = null,
        modifier = Modifier.size(20.dp)
    )
}


@Composable
private fun TerminalIcon() {
    Image(
        painter = painterResource(R.drawable.ic_pos_terminal),
        contentDescription = null,
        modifier = Modifier.size(26.dp)
    )
}

@Composable
private fun PrimaryActionButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF1F75D6),
            disabledContainerColor = Color(0xFFD8E7F7),
            contentColor = Color.White,
            disabledContentColor = Color.White
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
    ) {
        Text(text = text, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun cpiposTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Color(0xFFBFD5F4),
    unfocusedBorderColor = Color(0xFFBFD5F4),
    focusedContainerColor = Color(0xFFF8FBFF),
    unfocusedContainerColor = Color(0xFFF8FBFF),
    cursorColor = Color(0xFF1682F5)
)

@Composable
private fun HomeModeScreen(
    branch: PreviewBranch,
    counter: PreviewCounter,
    onSelectMode: (MockSaleMode) -> Unit
) {
    // Keep these values in scope for the later real session/navigation hand-off.
    // The design preview must not create or modify a production POS session.
    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFFAFCFF)) {
        Box(modifier = Modifier.fillMaxSize()) {
            HomeBlueWaveBackground(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(190.dp)
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp)
                    .padding(top = 32.dp, bottom = 112.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(14.dp))
                Image(
                    painter = painterResource(R.drawable.cpipos_logo_symbol),
                    contentDescription = stringResource(R.string.pos_logo_content_description),
                    modifier = Modifier.size(176.dp)
                )
                Text(
                    text = stringResource(R.string.pos_home_title),
                    fontSize = 22.sp,
                    lineHeight = 27.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF14213D)
                )
                Text(
                    text = stringResource(R.string.pos_home_subtitle),
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = Color(0xFF8894A7)
                )
                Spacer(modifier = Modifier.height(22.dp))

                ModeCard(
                    iconRes = R.drawable.ic_mode_takeaway,
                    title = stringResource(R.string.pos_mode_takeaway),
                    subtitle = stringResource(R.string.pos_mode_takeaway_subtitle),
                    onClick = { onSelectMode(MockSaleMode.Takeaway) }
                )
                Spacer(modifier = Modifier.height(10.dp))
                ModeCard(
                    iconRes = R.drawable.ic_mode_table,
                    title = stringResource(R.string.pos_mode_table),
                    subtitle = stringResource(R.string.pos_mode_table_subtitle),
                    onClick = { onSelectMode(MockSaleMode.DineIn) }
                )
            }

            HomeBottomMenu(modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}

/**
 * Three soft, translucent curved layers rather than the hard blue rectangles
 * from the initial UI prototype. Drawn behind the floating bottom navigation.
 */
@Composable
private fun HomeBlueWaveBackground(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        val baseWave = Path().apply {
            moveTo(0f, h * 0.25f)
            cubicTo(w * 0.20f, h * 0.12f, w * 0.33f, h * 0.52f, w * 0.55f, h * 0.44f)
            cubicTo(w * 0.76f, h * 0.38f, w * 0.84f, h * 0.20f, w, h * 0.17f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(baseWave, Color(0xFFEAF4FF))

        val foregroundWave = Path().apply {
            moveTo(0f, h * 0.34f)
            cubicTo(w * 0.19f, h * 0.30f, w * 0.30f, h * 0.55f, w * 0.46f, h * 0.49f)
            cubicTo(w * 0.68f, h * 0.41f, w * 0.84f, h * 0.47f, w, h * 0.34f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(foregroundWave, Color(0x99D5E9FF))

        val highlightWave = Path().apply {
            moveTo(w * 0.34f, h * 0.45f)
            cubicTo(w * 0.60f, h * 0.33f, w * 0.78f, h * 0.27f, w, h * 0.24f)
            lineTo(w, h * 0.65f)
            cubicTo(w * 0.76f, h * 0.58f, w * 0.54f, h * 0.66f, w * 0.34f, h * 0.45f)
            close()
        }
        drawPath(highlightWave, Color(0x66FFFFFF))
    }
}

@Composable
private fun ModeCard(
    iconRes: Int,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(116.dp)
            .shadow(7.dp, RoundedCornerShape(17.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFEAF3FF)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(70.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    fontSize = 17.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF152037),
                    maxLines = 1
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = Color(0xFF8795A9),
                    maxLines = 1
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFF0F5FF)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "›",
                    color = Color(0xFF347DF4),
                    fontSize = 24.sp,
                    lineHeight = 24.sp
                )
            }
        }
    }
}

/**
 * Five equal columns prevent labels from clipping on narrow Android phones.
 * The centre stock control has its own raised white ring and blue gradient.
 */
/**
 * The reference design has a raised, convex centre shoulder behind the product
 * button, not a concave cut-out. Keep the shoulder as part of the same white
 * surface so the bar and the floating button read as one sculpted component.
 */
@Composable
private fun HomeBottomMenu(modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val flatTopPx = with(density) { 17.dp.toPx() }
    val cornerPx = with(density) { 23.dp.toPx() }
    val shoulderPx = with(density) { 78.dp.toPx() }
    val crestHalfPx = with(density) { 28.dp.toPx() }

    val raisedBarShape = remember(flatTopPx, cornerPx, shoulderPx, crestHalfPx) {
        GenericShape { size, _ ->
            val center = size.width / 2f
            val flatTop = flatTopPx.coerceAtMost(size.height * 0.3f)
            val crestTop = with(density) { 1.dp.toPx() }
            val corner = cornerPx.coerceAtMost(size.width * 0.12f)
            val shoulder = shoulderPx.coerceAtMost(size.width * 0.23f)
            val crestHalf = crestHalfPx.coerceAtMost(shoulder * 0.45f)

            moveTo(0f, size.height)
            lineTo(0f, flatTop + corner)
            quadraticBezierTo(0f, flatTop, corner, flatTop)
            lineTo(center - shoulder, flatTop)

            // Gentle upward shoulders join the raised centre without a notch.
            cubicTo(
                center - shoulder * 0.70f, flatTop,
                center - crestHalf * 1.75f, crestTop,
                center - crestHalf, crestTop
            )
            lineTo(center + crestHalf, crestTop)
            cubicTo(
                center + crestHalf * 1.75f, crestTop,
                center + shoulder * 0.70f, flatTop,
                center + shoulder, flatTop
            )

            lineTo(size.width - corner, flatTop)
            quadraticBezierTo(size.width, flatTop, size.width, flatTop + corner)
            lineTo(size.width, size.height)
            close()
        }
    }

    Box(
        modifier = modifier
            .navigationBarsPadding()
            .padding(start = 10.dp, end = 10.dp, bottom = 5.dp)
            .fillMaxWidth()
            .height(104.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        // The background and its shadow share one shape: the raised centre
        // remains visible either side of the circular product button.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(90.dp)
                .shadow(elevation = 11.dp, shape = raisedBarShape, clip = false)
                .clip(raisedBarShape)
                .background(Color.White)
        )

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(77.dp)
                .padding(start = 6.dp, end = 6.dp, top = 15.dp),
            verticalAlignment = Alignment.Top
        ) {
            BottomMenuItem(
                iconRes = R.drawable.ic_nav_cart,
                label = stringResource(R.string.pos_nav_sale),
                selected = true,
                modifier = Modifier.weight(1f)
            )
            BottomMenuItem(
                iconRes = R.drawable.ic_nav_report,
                label = stringResource(R.string.pos_nav_report),
                selected = false,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.weight(1f))
            BottomMenuItem(
                iconRes = R.drawable.ic_nav_history,
                label = stringResource(R.string.pos_nav_history),
                selected = false,
                modifier = Modifier.weight(1f)
            )
            BottomMenuItem(
                iconRes = R.drawable.ic_nav_setting,
                label = stringResource(R.string.pos_nav_setting),
                selected = false,
                modifier = Modifier.weight(1f)
            )
        }

        // White halo + gentle elevation creates the raised central button.
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .size(76.dp)
                .shadow(15.dp, CircleShape, clip = false)
                .clip(CircleShape)
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF69B6FF), Color(0xFF1764ED))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_nav_stock),
                    contentDescription = stringResource(R.string.pos_nav_stock),
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        Text(
            text = stringResource(R.string.pos_nav_stock),
            fontSize = 11.sp,
            lineHeight = 13.sp,
            color = Color(0xFF1B355E),
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 5.dp)
        )
    }
}

@Composable
private fun BottomMenuItem(
    iconRes: Int,
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier
) {
    val itemColor = if (selected) Color(0xFF2282F6) else Color(0xFF7890AD)
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(25.dp)
        )
        Text(
            text = label,
            fontSize = 10.sp,
            lineHeight = 13.sp,
            color = itemColor,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1
        )
        Box(
            modifier = Modifier
                .width(24.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(100.dp))
                .background(if (selected) itemColor else Color.Transparent)
        )
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
                subtitle = stringResource(R.string.pos_branch_code, branch.code),
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
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF66736A))
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

@Preview(name = "Mobile POS - Home Modes", showBackground = true, widthDp = 390, heightDp = 844, locale = "th")
@Composable
private fun MobilePosHomeModePreview() {
    MaterialTheme {
        HomeModeScreen(previewBranches.first(), previewCounters.first(), onSelectMode = {})
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