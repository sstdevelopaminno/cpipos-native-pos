package com.cpipos.pos.next.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cpipos.pos.next.R
import com.cpipos.pos.next.core.pos.CashTenderInput
import com.cpipos.pos.next.core.pos.Satang
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

private val cashInk = Color(0xFF12264B)
private val cashBlue = Color(0xFF176BEE)
private val cashGreen = Color(0xFF209845)
private val cashBorder = Color(0xFFDDE7F8)
private val cashMoney = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.US))
private fun money(value: Satang): String = cashMoney.format(value.value.toBigDecimal().movePointLeft(2))

/** Full-screen Native checkout; cashInput is editable only through this custom keypad. */
@Composable
internal fun CashCheckoutScreen(
    due: Satang,
    cashInput: String,
    saving: Boolean,
    error: String?,
    isLiveSale: Boolean = false,
    onInputChange: (String) -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    val received = CashTenderInput.money(cashInput)
    val enough = CashTenderInput.isSufficient(cashInput, due)
    val change = if (enough) received - due else Satang.ZERO
    val roundedHundred = ((due.value + 9999L) / 10000L) * 10000L
    val presets = listOf(due, Satang.of(roundedHundred), Satang.of(50000L), Satang.of(100000L))
    val keypad = listOf(
        listOf("1", "2", "3", "⌫"),
        listOf("4", "5", "6", "+50"),
        listOf("7", "8", "9", "+100"),
        listOf("00", "0", ".", "+500")
    )
    BackHandler(enabled = true) { if (!saving) onCancel() }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF6F9FF)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 13.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "‹ กลับ",
                    modifier = Modifier.clickable(enabled = !saving) { onCancel() }.padding(end = 10.dp),
                    fontSize = 16.sp,
                    color = cashBlue,
                    fontWeight = FontWeight.Bold
                )
                Image(
                    painter = painterResource(R.drawable.ic_payment_cash),
                    contentDescription = null,
                    modifier = Modifier.size(36.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text("ชำระเงิน (เงินสด)", color = cashInk, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                    Text("รับเงินจากลูกค้าและคำนวณเงินทอนอัตโนมัติ", color = Color(0xFF637593), fontSize = 10.sp)
                }
                Image(
                    painter = painterResource(R.drawable.cpipos_logo_symbol),
                    contentDescription = "CpIPOS",
                    modifier = Modifier.size(34.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(19.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, cashBorder)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                        CashSummaryRow("ยอดรวมสินค้า", money(due), cashInk)
                        Spacer(modifier = Modifier.height(5.dp))
                        CashSummaryRow("ส่วนลด", "0.00", cashInk)
                        Spacer(modifier = Modifier.height(8.dp))
                        CashSummaryRow("ยอดชำระเงิน", "฿ " + money(due), cashGreen, emphasized = true)
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(19.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, cashBorder)
                ) {
                    Column(modifier = Modifier.padding(11.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "รับเงินจากลูกค้า",
                                modifier = Modifier.weight(1f),
                                color = cashInk,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp
                            )
                            Text("เงินทอน", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = cashInk)
                        }
                        Spacer(modifier = Modifier.height(9.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(9.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1.5f)
                                    .height(75.dp)
                                    .border(1.5.dp, Color(0xFFCBD8ED), RoundedCornerShape(14.dp))
                                    .background(Color.White, RoundedCornerShape(14.dp))
                                    .padding(horizontal = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("฿", fontSize = 20.sp, color = cashGreen, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    cashInput.ifBlank { "0" },
                                    modifier = Modifier.weight(1f),
                                    color = cashGreen,
                                    fontSize = if (cashInput.length > 7) 23.sp else 29.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Box(
                                    Modifier.width(2.dp).height(25.dp)
                                        .background(cashBlue, RoundedCornerShape(4.dp))
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    "×",
                                    color = Color(0xFF8294AF),
                                    fontSize = 23.sp,
                                    modifier = Modifier.clickable(enabled = !saving) { onInputChange("0") }
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(75.dp)
                                    .background(Color(0xFFE7FBEE), RoundedCornerShape(14.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "฿ " + money(change),
                                    color = cashGreen,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1
                                )
                            }
                        }
                        if (!enough) {
                            Text(
                                "รับเงินไม่ครบ: ขาด ฿ " + money(due - received.coerceAtMost(due)),
                                color = Color(0xFFB2593A),
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 7.dp)
                            )
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    presets.forEachIndexed { index, value ->
                        val selected = received == value
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .background(
                                    if (selected) cashBlue else Color(0xFFF0F5FF),
                                    RoundedCornerShape(14.dp)
                                )
                                .border(1.dp, if (selected) cashBlue else cashBorder, RoundedCornerShape(14.dp))
                                .clickable(enabled = !saving) { onInputChange(CashTenderInput.setToDue(value)) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "฿ " + value.bahtText().substringBefore('.').let {
                                    "%,d".format(Locale.US, it.toLong())
                                },
                                color = if (selected) Color.White else cashInk,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    keypad.forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { key ->
                                val special = key.startsWith("+") || key == "⌫"
                                val shape = RoundedCornerShape(14.dp)
                                Box(
                                    modifier = Modifier.weight(1f).height(57.dp)
                                        .background(
                                            if (special) Color(0xFFE4F0FF) else Color.White,
                                            shape
                                        )
                                        .border(1.dp, cashBorder, shape)
                                        .clickable(enabled = !saving) {
                                            onInputChange(
                                                if (key.startsWith("+"))
                                                    runCatching {
                                                        CashTenderInput.addAmount(
                                                            cashInput, key.drop(1).toInt()
                                                        )
                                                    }.getOrElse { cashInput }
                                                else CashTenderInput.append(cashInput, key)
                                            )
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        key,
                                        color = if (special) cashBlue else cashInk,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = if (special) 21.sp else 25.sp
                                    )
                                }
                            }
                        }
                    }
                }
                if (error != null) {
                    Text(error, color = Color(0xFFBB2B32), fontSize = 12.sp)
                }
                Text(
                    if (isLiveSale)
                        "ยอดขายจะบันทึกเข้าระบบร้านค้าเมื่อเซิร์ฟเวอร์ยืนยันการรับเงินสำเร็จ"
                    else "ตัวอย่าง UI • รายการนี้ไม่ใช่ยอดขายจริง",
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    fontSize = 10.sp,
                    color = Color(0xFF7184A2)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    enabled = !saving,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, cashBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFCB3434))
                ) {
                    Text("✕  ยกเลิก", fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                Button(
                    onClick = onConfirm,
                    enabled = enough && !saving,
                    modifier = Modifier.weight(1.3f).height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = cashBlue)
                ) {
                    Text(
                        if (saving) "กำลังบันทึก..." else "✓  ยืนยันการรับเงิน",
                        fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun CashSummaryRow(label: String, value: String, valueColor: Color, emphasized: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            color = cashInk,
            fontWeight = if (emphasized) FontWeight.ExtraBold else FontWeight.Medium,
            fontSize = if (emphasized) 17.sp else 14.sp
        )
        Text(
            value,
            color = valueColor,
            fontSize = if (emphasized) 23.sp else 16.sp,
            fontWeight = if (emphasized) FontWeight.ExtraBold else FontWeight.SemiBold
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844, locale = "th")
@Composable
private fun CashCheckoutScreenPreview() {
    MaterialTheme {
        CashCheckoutScreen(
            Satang.fromBaht("245.00"), "300", false, null,
            onInputChange = {}, onCancel = {}, onConfirm = {}
        )
    }
}
