package com.cpipos.pos.next.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cpipos.pos.next.core.webpos.WebSubscriptionRuntime
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

private val lifecycleMoney = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.US))

@Composable
internal fun NativeSubscriptionLifecycleDialog(
    runtime: WebSubscriptionRuntime?,
    nowMs: Long,
    warningDismissed: Boolean,
    onDismissWarning: () -> Unit,
    onOpenBilling: () -> Unit
) {
    val value = runtime ?: return
    if (value.exempt) return

    val locked = value.lockedAt(nowMs)
    val days = value.expiresAt?.let {
        runCatching {
            val expiry = java.time.Instant.parse(it).toEpochMilli()
            kotlin.math.ceil((expiry - nowMs).toDouble() / 86_400_000.0).toInt()
        }.getOrNull()
    } ?: value.daysRemaining
    val warning = !locked && !warningDismissed && days != null && days in 1..7
    if (!locked && !warning) return

    val context = LocalContext.current
    val language = NativeUiLanguage.state(context).value
    fun tr(th: String, en: String, lo: String): String = when (language) {
        "en" -> en
        "lo" -> lo
        else -> th
    }
    val amount = value.amountDue?.let {
        (if (value.currency == "THB") "฿" else value.currency + " ") + lifecycleMoney.format(it)
    } ?: "—"

    AlertDialog(
        onDismissRequest = { if (!locked) onDismissWarning() },
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                if (locked) tr(
                    "แพ็กเกจครบกำหนดชำระแล้ว",
                    "Package payment is required",
                    "ແພັກເກດຄົບກຳນົດຊຳລະແລ້ວ"
                ) else tr(
                    "แพ็กเกจใกล้ครบกำหนด",
                    "Package renewal reminder",
                    "ແພັກເກດໃກ້ຄົບກຳນົດ"
                ),
                fontWeight = FontWeight.ExtraBold,
                color = if (locked) Color(0xFFB42336) else Color(0xFF9B6518)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (locked) tr(
                        "ระบบงานถูกล็อกชั่วคราวจนกว่าฝ่าย IT จะตรวจสอบการชำระเงิน เมื่ออนุมัติแล้วแอปจะปลดล็อกอัตโนมัติ",
                        "The POS is temporarily locked until IT confirms payment. It will unlock automatically after approval.",
                        "ລະບົບຖືກລັອກຊົ່ວຄາວຈົນກວ່າ IT ຈະຢືນຢັນການຊຳລະ ແລະຈະປົດລັອກອັດຕະໂນມັດ"
                    ) else tr(
                        "เหลือ ${days ?: "—"} วันก่อนครบกำหนด สามารถชำระแพ็กเกจล่วงหน้าได้จากแอป",
                        "${days ?: "—"} day(s) remaining. You can renew from the app now.",
                        "ເຫຼືອ ${days ?: "—"} ມື້ ສາມາດຕໍ່ອາຍຸຈາກແອັບໄດ້"
                    ),
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = Color(0xFF66758D)
                )
                Surface(
                    color = Color(0xFFF5F9FF),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        Modifier.padding(13.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        LifecycleRow(tr("แพ็กเกจ", "Package", "ແພັກເກດ"), value.packageName)
                        LifecycleRow(tr("ยอดชำระ", "Amount due", "ຍອດຊຳລະ"), amount)
                        LifecycleRow(
                            tr("รอบชำระ", "Billing cycle", "ຮອບຊຳລະ"),
                            if (value.billingInterval == "yearly")
                                tr("รายปี", "Yearly", "ລາຍປີ")
                            else tr("รายเดือน", "Monthly", "ລາຍເດືອນ")
                        )
                    }
                }
                val bank = listOf(value.bankName, value.accountName, value.accountNumber)
                    .filter { it.isNotBlank() }.joinToString(" · ")
                if (bank.isNotBlank()) {
                    Text(
                        tr("บัญชีบริษัท: ", "Company account: ", "ບັນຊີບໍລິສັດ: ") + bank,
                        fontSize = 10.sp,
                        color = Color(0xFF176DED)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onOpenBilling,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (locked) Color(0xFFD43B4E) else Color(0xFF176DED)
                )
            ) {
                Text(
                    tr("ชำระแพ็กเกจ", "Package payment", "ຊຳລະແພັກເກດ"),
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = if (!locked) {
            {
                OutlinedButton(onClick = onDismissWarning, shape = RoundedCornerShape(14.dp)) {
                    Text(tr("ภายหลัง", "Later", "ພາຍຫຼັງ"))
                }
            }
        } else null
    )
}

@Composable
private fun LifecycleRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 10.sp, color = Color(0xFF71849B))
        Text(value.ifBlank { "—" }, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF172B4E))
    }
}
