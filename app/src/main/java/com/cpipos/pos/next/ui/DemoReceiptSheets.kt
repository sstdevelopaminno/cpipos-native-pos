package com.cpipos.pos.next.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import com.cpipos.pos.next.R
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cpipos.pos.next.core.offline.DemoSaleReceipt
import com.cpipos.pos.next.core.offline.DemoReceiptLine
import com.cpipos.pos.next.core.pos.Satang
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private val receiptBlue = Color(0xFF176DED)
private val receiptInk = Color(0xFF142545)
private val receiptGreen = Color(0xFF208A49)
private val receiptMoney = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.US))
private fun baht(s: Satang): String =
    receiptMoney.format(s.value.toBigDecimal().movePointLeft(2))
private fun paidDate(time: Long): String =
    SimpleDateFormat("dd MMM yyyy HH:mm", Locale("th", "TH"))
        .apply { timeZone = TimeZone.getTimeZone("Asia/Bangkok") }.format(Date(time))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SavedDemoReceiptSheet(
    receipt: DemoSaleReceipt,
    onPrint: () -> Unit,
    onDone: () -> Unit
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val h = LocalConfiguration.current.screenHeightDp.dp
    ModalBottomSheet(
        onDismissRequest = onDone,
        sheetState = state,
        containerColor = Color(0xFFF7FAFF),
        shape = RoundedCornerShape(topStart = 27.dp, topEnd = 27.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .heightIn(max = h * .92f)
                .navigationBarsPadding()
                .padding(horizontal = 11.dp, vertical = 4.dp)
        ) {
            Text(
                if (receipt.isDemo) "บันทึกบิลทดสอบเรียบร้อย" else "บันทึกยอดขายเรียบร้อย",
                modifier = Modifier.padding(horizontal = 7.dp),
                fontSize = 18.sp, fontWeight = FontWeight.ExtraBold,
                color = receiptGreen
            )
            Text(
                if (receipt.isDemo) "ตะกร้าเคลียร์แล้ว • บันทึกอยู่ในเครื่องนี้เท่านั้น"
                else "ระบบยืนยันรับเงินแล้ว • บันทึกบิลเข้าระบบร้านค้า",
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                fontSize = 10.sp, color = Color(0xFF71829B)
            )
            Surface(
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                shape = RoundedCornerShape(13.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Color(0xFFDDE5F2))
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 11.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Image(
                                painter = painterResource(R.drawable.ic_cpipos_receipt_logo),
                                contentDescription = "โลโก้ CpIPOS",
                                modifier = Modifier.size(40.dp)
                            )
                            Text("CpIPOS", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = receiptInk)
                            Text(
                                receipt.storeName.ifBlank { receipt.branchName },
                                fontSize = 15.sp, lineHeight = 18.sp, color = receiptInk,
                                fontWeight = FontWeight.ExtraBold,
                                textAlign = TextAlign.Center
                            )
                            if (!receipt.storeAddress.isNullOrBlank()) {
                                Text(receipt.storeAddress, color = receiptInk, fontSize = 10.sp,
                                    lineHeight = 13.sp, textAlign = TextAlign.Center)
                            }
                            if (!receipt.storePhone.isNullOrBlank()) {
                                Text(receipt.storePhone, color = receiptInk, fontSize = 10.sp)
                            }
                            if (receipt.branchName != receipt.storeName) {
                                Text(receipt.branchName, color = receiptInk, fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold)
                            }
                            if (receipt.isDemo) Text(
                                "ตัวอย่าง / PREVIEW ONLY", color = Color(0xFFBD4C41),
                                fontWeight = FontWeight.Bold, fontSize = 11.sp
                            )
                        }
                        ReceiptDashedDivider()
                        ReceiptTotalRow("ผู้ขาย", receipt.cashierName)
                        ReceiptTotalRow("กะ", receipt.shiftLabel ?: receipt.counterCode)
                        ReceiptTotalRow("โหมด", receipt.modeLabel)
                        ReceiptTotalRow("เลขที่บิล", receipt.billNo)
                        ReceiptTotalRow("สมาชิก", "0 คะแนน / 0 แต้ม")
                        ReceiptTotalRow("วันที่", paidDate(receipt.createdAtMs))
                        ReceiptDashedDivider()
                    }
                    items(receipt.lines, key = { it.productId }) { item ->
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    item.name,
                                    fontSize = 12.sp, lineHeight = 15.sp, color = receiptInk,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "× " + baht(item.unitPrice),
                                    fontSize = 10.sp,
                                    color = Color(0xFF506078)
                                )
                            }
                            Text(
                                item.quantity.toString(),
                                modifier = Modifier.width(21.dp),
                                color = receiptInk, fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                baht(item.amount),
                                modifier = Modifier.width(67.dp),
                                color = receiptInk, fontSize = 12.sp,
                                textAlign = TextAlign.End, fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                    }
                    item {
                        ReceiptDashedDivider()
                        ReceiptTotalRow("การชำระเงิน", "ชำระเงินสด")
                        ReceiptDashedDivider()
                        ReceiptTotalRow("ส่วนลด", "฿0.00")
                        androidx.compose.material3.HorizontalDivider(color = receiptInk)
                        ReceiptTotalRow("ยอดที่ต้องชำระ", "฿" + baht(receipt.total), bold = true)
                        androidx.compose.material3.HorizontalDivider(color = receiptInk)
                        ReceiptTotalRow("รับเงินจากลูกค้า", "฿" + baht(receipt.received), bold = true)
                        ReceiptTotalRow("เงินทอน", "฿" + baht(receipt.change), bold = true)
                        ReceiptDashedDivider()
                        Text(
                            if (receipt.isDemo) "ไม่ใช่ใบเสร็จรับเงินจริง • ไม่ได้บันทึกเข้าระบบร้านค้า"
                            else "ขอบคุณที่ใช้บริการ",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            color = if (receipt.isDemo) Color(0xFFB04740) else receiptInk,
                            fontSize = 10.sp
                        )
                        Text(
                            "CpIPOS",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            color = receiptInk, fontSize = 12.sp
                        )
                    }
                }
            }
            Spacer(Modifier.height(9.dp))
            Button(
                onClick = onPrint,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = receiptBlue)
            ) {
                Text("▣  พิมพ์ใบเสร็จ / บันทึก PDF", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Spacer(Modifier.height(5.dp))
            OutlinedButton(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth().height(45.dp),
                shape = RoundedCornerShape(14.dp)
            ) { Text("เสร็จสิ้น • กลับหน้าขาย", color = receiptInk) }
        }
    }
}

@Composable
private fun ReceiptDashedDivider() {
    androidx.compose.foundation.Canvas(
        modifier = Modifier.fillMaxWidth().height(9.dp)
    ) {
        drawLine(
            color = Color(0xFF1E2C44),
            start = androidx.compose.ui.geometry.Offset(0f, size.height / 2),
            end = androidx.compose.ui.geometry.Offset(size.width, size.height / 2),
            strokeWidth = 1f,
            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(9f, 6f))
        )
    }
}

@Composable
private fun ReceiptTotalRow(label: String, value: String, bold: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, modifier = Modifier.weight(1f),
            fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.Medium,
            color = receiptInk, fontSize = 12.sp)
        Text(value, color = receiptInk,
            fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.SemiBold,
            fontSize = if (bold) 15.sp else 12.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DemoDailySalesSheet(
    receipts: List<DemoSaleReceipt>,
    onOpenReceipt: (DemoSaleReceipt) -> Unit,
    onDismiss: () -> Unit
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sum = receipts.fold(Satang.ZERO) { total, bill -> total + bill.total }
    val h = LocalConfiguration.current.screenHeightDp.dp
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = Color(0xFFF7FAFF),
        shape = RoundedCornerShape(topStart = 27.dp, topEnd = 27.dp)
    ) {
        Column(modifier = Modifier
            .fillMaxWidth().heightIn(max = h * .82f)
            .navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text("ยอดขายทดสอบวันนี้", fontSize = 21.sp,
                fontWeight = FontWeight.Bold, color = receiptInk)
            Text("เฉพาะสาขาและเคาน์เตอร์ที่เลือก • ไม่รวมยอดจริง",
                fontSize = 11.sp, color = Color(0xFF70829C))
            Spacer(Modifier.height(8.dp))
            Surface(color = Color(0xFFE8F2FF), shape = RoundedCornerShape(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${receipts.size} บิล", color = receiptInk,
                        modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    Text("฿${baht(sum)}", color = receiptBlue,
                        fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
            Spacer(Modifier.height(8.dp))
            if (receipts.isEmpty()) {
                Text("ยังไม่มีบิลทดสอบที่บันทึกวันนี้",
                    color = Color(0xFF647B99), modifier = Modifier.padding(20.dp))
            } else {
                LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    items(receipts, key = { it.id }) { receipt ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { onOpenReceipt(receipt) }
                                .padding(vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(receipt.billNo, color = receiptInk,
                                    fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(paidDate(receipt.createdAtMs),
                                    color = Color(0xFF7A8AA2), fontSize = 11.sp)
                            }
                            Text("฿${baht(receipt.total)}", color = receiptGreen,
                                fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text(" ›", fontSize = 19.sp, color = receiptBlue)
                        }
                    }
                }
            }
            OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("ปิด", color = receiptInk)
            }
        }
    }
}
