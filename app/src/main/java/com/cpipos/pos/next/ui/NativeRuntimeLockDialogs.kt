package com.cpipos.pos.next.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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

private val runtimeMoney = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.US))

@Composable
internal fun NativeSubscriptionLockDialog(
    runtime: WebSubscriptionRuntime?,
    onBilling: () -> Unit
) {
    val state = runtime ?: return
    if (!state.locked || state.exempt) return
    val language = NativeUiLanguage.state(LocalContext.current).value
    val title = when (language) {
        "en" -> "Package expired / system suspended"
        "lo" -> "ແພັກເກດໝົດອາຍຸ / ລະບົບຖືກລະງັບ"
        else -> "แพ็กเกจหมดอายุ / ระบบถูกระงับ"
    }
    val message = when (language) {
        "en" -> "Package " + state.packageName +
            " cannot use POS operations right now. Renew or pay the package; " +
            "the app will unlock automatically after CpIPOS / IT confirms the status."
        "lo" -> "ແພັກເກດ " + state.packageName +
            " ບໍ່ສາມາດໃຊ້ງານ POS ໄດ້ໃນຂະນະນີ້. ກະລຸນາຕໍ່ອາຍຸ ຫຼື ຊຳລະແພັກເກດ; " +
            "ລະບົບຈະປົດລັອກອັດຕະໂນມັດຫຼັງ CpIPOS / IT ຢືນຢັນ."
        else -> "แพ็กเกจ " + state.packageName +
            " ไม่สามารถใช้งานระบบ POS ต่อได้ในขณะนี้ กรุณาชำระหรือต่ออายุแพ็กเกจ " +
            "ระบบจะปลดล็อกอัตโนมัติหลัง CpIPOS / IT ยืนยันสถานะ"
    }
    val amountLabel = when (language) {
        "en" -> "Reference amount: "
        "lo" -> "ຍອດອ້າງອີງ: "
        else -> "ยอดอ้างอิง: "
    }
    val billingLabel = when (language) {
        "en" -> "Pay package"
        "lo" -> "ໄປຊຳລະແພັກເກດ"
        else -> "ไปชำระแพ็กเกจ"
    }

    AlertDialog(
        onDismissRequest = {},
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                title,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF172B4E)
            )
        },
        text = {
            Column {
                Text(
                    message,
                    color = Color(0xFF66758D),
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )
                state.amountDue?.takeIf { it > 0.0 }?.let { amount ->
                    Text(
                        amountLabel +
                            (if (state.currency == "THB") "฿" else state.currency + " ") +
                            runtimeMoney.format(amount),
                        modifier = Modifier.padding(top = 10.dp),
                        color = Color(0xFF176DED),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onBilling,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF176DED))
            ) {
                Text(billingLabel, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
internal fun NativeMdmDeviceLockDialog() {
    val language = NativeUiLanguage.state(LocalContext.current).value
    val title = when (language) {
        "en" -> "Device disabled by IT"
        "lo" -> "ອຸປະກອນຖືກປິດໃຊ້ງານໂດຍ IT"
        else -> "อุปกรณ์ถูกปิดใช้งานโดย IT"
    }
    val message = when (language) {
        "en" -> "This POS device is locked by MDM. Contact IT. The app will keep checking CpIPOS and unlock automatically after IT enables the device."
        "lo" -> "ເຄື່ອງ POS ນີ້ຖືກລັອກໂດຍ MDM. ກະລຸນາຕິດຕໍ່ IT. ແອັບຈະກວດສອບ CpIPOS ຕໍ່ເນື່ອງ ແລະປົດລັອກເມື່ອ IT ເປີດໃຊ້ງານ."
        else -> "เครื่อง POS นี้ถูกล็อกจากระบบ MDM กรุณาติดต่อผู้ดูแล IT แอปจะตรวจสอบสถานะกับ CpIPOS อัตโนมัติและปลดล็อกเมื่อได้รับคำสั่งเปิดใช้งาน"
    }
    AlertDialog(
        onDismissRequest = {},
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                title,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF172B4E)
            )
        },
        text = {
            Text(
                message,
                color = Color(0xFF66758D),
                fontSize = 13.sp,
                lineHeight = 20.sp
            )
        },
        confirmButton = {}
    )
}
