package com.cpipos.pos.next.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.cpipos.pos.next.auth.AuthAttemptResult
import com.cpipos.pos.next.auth.AuthCredentials
import com.cpipos.pos.next.auth.NativeAuthGateway
import kotlinx.coroutines.launch

@Composable
fun AuthPhase1Screen(
    isSupabaseConfigured: Boolean,
    gateway: NativeAuthGateway
) {
    var storeCode by remember { mutableStateOf("") }
    var employeePin by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var statusMessage by remember {
        mutableStateOf("กรอกรหัสร้านและ PIN เพื่อทดสอบหน้า Login โดยยังไม่ส่งข้อมูลไป Production")
    }
    val scope = rememberCoroutineScope()

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "CpIPOS Native 2.0",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Authentication Phase 1",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 6.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "โหมดปลอดภัย",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (isSupabaseConfigured) {
                                "Supabase configuration พร้อม แต่ Secure Auth Gateway ยังถูกปิดไว้"
                            } else {
                                "ยังไม่พบ Supabase local configuration"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                        Text(
                            text = "ไม่มีการอ่าน PIN hash และไม่มีการเขียนข้อมูล Production ในขั้นนี้",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                OutlinedTextField(
                    value = storeCode,
                    onValueChange = { storeCode = it.trimStart() },
                    label = { Text("รหัสร้าน") },
                    singleLine = true,
                    enabled = !isSubmitting,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = employeePin,
                    onValueChange = { value ->
                        employeePin = value.filter(Char::isDigit).take(12)
                    },
                    label = { Text("PIN พนักงาน") },
                    singleLine = true,
                    enabled = !isSubmitting,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        isSubmitting = true
                        statusMessage = "กำลังตรวจสอบ Secure Auth Gateway..."

                        scope.launch {
                            val result = gateway.authenticate(
                                AuthCredentials(
                                    storeCode = storeCode.trim(),
                                    employeePin = employeePin
                                )
                            )

                            employeePin = ""
                            isSubmitting = false
                            statusMessage = when (result) {
                                is AuthAttemptResult.Success ->
                                    "Authentication สำเร็จ และพร้อมเข้าสู่ Session Context"

                                is AuthAttemptResult.Rejected -> result.message
                                is AuthAttemptResult.BackendUnavailable -> result.message
                            }
                        }
                    },
                    enabled = storeCode.isNotBlank() && employeePin.isNotBlank() && !isSubmitting,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isSubmitting) "กำลังตรวจสอบ..." else "ตรวจสอบสิทธิ์")
                }

                Text(
                    text = statusMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 14.dp)
                )

                Text(
                    text = "ลำดับถัดไป: ร้าน -> พนักงาน -> สาขา -> อุปกรณ์ -> Session -> Package/Feature -> Products",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 18.dp)
                )
            }
        }
    }
}
