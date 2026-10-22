package com.cpipos.pos.next.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cpipos.pos.next.core.webpos.WebReceiptHistoryRecord
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private val historyBlue = Color(0xFF176DED)
private val historyInk = Color(0xFF142545)
private val historyMoney = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.US))

private fun historyBaht(value: Long): String =
    historyMoney.format(value.toBigDecimal().movePointLeft(2))

private fun historyTime(epochMs: Long): String =
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("Asia/Bangkok")
    }.format(Date(epochMs))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LiveReceiptHistorySheet(
    records: List<WebReceiptHistoryRecord>,
    query: String,
    loading: Boolean,
    error: String?,
    totalRecords: Int,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onOpen: (WebReceiptHistoryRecord) -> Unit,
    onDismiss: () -> Unit
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * .88f
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = Color(0xFFF7FAFF),
        shape = RoundedCornerShape(topStart = 27.dp, topEnd = 27.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Text(
                "ประวัติใบเสร็จจริง",
                color = historyInk,
                fontSize = 21.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                "ค้นหาเลขที่บิล/ลูกค้า ย้อนหลัง 30 วัน จากระบบ CpIPOS",
                color = Color(0xFF6D7F99),
                fontSize = 11.sp
            )
            Spacer(Modifier.height(9.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("ค้นหาเลขที่บิล") }
                )
                Button(
                    onClick = onSearch,
                    enabled = !loading,
                    shape = RoundedCornerShape(13.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = historyBlue),
                    modifier = Modifier.height(56.dp)
                ) {
                    Text(if (loading) "..." else "ค้นหา")
                }
            }
            Spacer(Modifier.height(8.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFFEAF3FF),
                border = BorderStroke(1.dp, Color(0xFFD6E5FA))
            ) {
                Text(
                    if (loading) "กำลังโหลดประวัติ..."
                    else "พบ $totalRecords บิลในช่วงข้อมูลที่อนุญาต",
                    modifier = Modifier.padding(12.dp),
                    color = historyInk,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (!error.isNullOrBlank()) {
                Text(
                    error,
                    color = Color(0xFFB33E43),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            Spacer(Modifier.height(5.dp))
            if (!loading && records.isEmpty() && error.isNullOrBlank()) {
                Text(
                    "ไม่พบบิลที่ตรงกับเงื่อนไข",
                    color = Color(0xFF6C7F9A),
                    modifier = Modifier.padding(vertical = 20.dp)
                )
            } else {
                LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    items(records, key = { it.orderId }) { record ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { onOpen(record) },
                            shape = RoundedCornerShape(14.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, Color(0xFFE0E8F5))
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        record.orderNo,
                                        color = historyInk,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        historyTime(record.createdAtMs),
                                        color = Color(0xFF74849C),
                                        fontSize = 10.sp
                                    )
                                    Text(
                                        buildString {
                                            append(
                                                record.paymentMethods.joinToString(" + ")
                                                    .ifBlank { "-" }
                                            )
                                            append(" • ")
                                            append(record.status)
                                        },
                                        color = Color(0xFF657A96),
                                        fontSize = 10.sp
                                    )
                                }
                                Column {
                                    Text(
                                        "฿" + historyBaht(record.receipt.total.value),
                                        color = Color(0xFF18884A),
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        "เปิดดู ›",
                                        color = historyBlue,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().height(45.dp),
                shape = RoundedCornerShape(13.dp)
            ) {
                Text("ปิด", color = historyInk)
            }
        }
    }
}
