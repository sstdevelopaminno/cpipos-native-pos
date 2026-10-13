package com.cpipos.pos.next.printing

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import com.cpipos.pos.next.R
import com.cpipos.pos.next.core.offline.DemoSaleReceipt
import com.cpipos.pos.next.core.pos.Satang
import java.io.FileOutputStream
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.ceil

/**
 * Printable 58 / 80 mm thermal-slip PDF via Android PrintManager.
 * Owner-provided CpIPOS logo is a vector resource, so it stays sharp when printed.
 * A real bill is labelled as paid ONLY after verified POS Web payment response.
 * Requires a configured Android print service for a physical printer.
 */
object DemoReceiptPrinter {
    private val numberFormat = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.US))
    private fun baht(value: Satang): String =
        numberFormat.format(value.value.toBigDecimal().movePointLeft(2))

    fun print(context: Context, receipt: DemoSaleReceipt, paperWidthMm: Int = 80) {
        require(paperWidthMm == 58 || paperWidthMm == 80)
        val manager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        val scale = paperWidthMm.toFloat() / 80f
        val pageHeight = (410 + receipt.lines.sumOf {
            22 + ((it.name.length / 18) + 1) * 13
        } + (if (receipt.storeAddress.isNullOrBlank()) 0 else 30) +
            (if (receipt.storePhone.isNullOrBlank()) 0 else 15) +
            (if (receipt.isDemo) 20 else 0)).coerceAtLeast(490)
        val pageWidth = ceil(227f * scale).toInt()
        val actualHeight = ceil(pageHeight * scale).toInt()
        val mediaWidth = (paperWidthMm * 1000f / 25.4f).toInt()
        val mediaHeight = (actualHeight * 1000f / 72f).toInt().coerceAtLeast(2000)
        val attributes = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize(
                "CPIPOS_${paperWidthMm}_MM", "${paperWidthMm} mm receipt", mediaWidth, mediaHeight
            ))
            .setResolution(PrintAttributes.Resolution("CPIPOS_203", "203 dpi", 203, 203))
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .setColorMode(PrintAttributes.COLOR_MODE_MONOCHROME)
            .build()
        manager.print(
            "CpIPOS-${receipt.billNo}",
            object : PrintDocumentAdapter() {
                override fun onLayout(
                    oldAttributes: PrintAttributes?,
                    newAttributes: PrintAttributes?,
                    cancellationSignal: CancellationSignal,
                    callback: LayoutResultCallback,
                    extras: Bundle?
                ) {
                    if (cancellationSignal.isCanceled) {
                        callback.onLayoutCancelled()
                        return
                    }
                    callback.onLayoutFinished(
                        PrintDocumentInfo.Builder("${receipt.billNo}.pdf")
                            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                            .setPageCount(1)
                            .build(),
                        oldAttributes != newAttributes
                    )
                }

                override fun onWrite(
                    pages: Array<out PageRange>,
                    destination: ParcelFileDescriptor,
                    cancellationSignal: CancellationSignal,
                    callback: WriteResultCallback
                ) {
                    if (cancellationSignal.isCanceled) {
                        callback.onWriteCancelled()
                        return
                    }
                    val pdf = PdfDocument()
                    try {
                        val page = pdf.startPage(
                            PdfDocument.PageInfo.Builder(pageWidth, actualHeight, 1).create()
                        )
                        page.canvas.save()
                        page.canvas.scale(scale, scale)
                        render(context, page.canvas, receipt)
                        page.canvas.restore()
                        pdf.finishPage(page)
                        FileOutputStream(destination.fileDescriptor).use(pdf::writeTo)
                        callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                    } catch (e: Exception) {
                        callback.onWriteFailed(e.localizedMessage ?: "Cannot render receipt")
                    } finally {
                        pdf.close()
                    }
                }
            },
            attributes
        )
    }

    private fun render(context: Context, c: Canvas, receipt: DemoSaleReceipt) {
        val black = android.graphics.Color.BLACK
        val regular = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = black
            textSize = 9.5f
            typeface = Typeface.DEFAULT
        }
        val bold = Paint(regular).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 10f
        }
        val small = Paint(regular).apply { textSize = 8.2f }
        val title = Paint(bold).apply { textSize = 13.7f }
        val grand = Paint(bold).apply { textSize = 12.7f }
        val dash = Paint(regular).apply {
            strokeWidth = 0.8f
            style = Paint.Style.STROKE
            pathEffect = DashPathEffect(floatArrayOf(4f, 3f), 0f)
        }
        fun txt(value: String, x: Float, y: Float, font: Paint = regular) {
            c.drawText(value, x, y, font)
        }
        fun right(value: String, y: Float, font: Paint = regular) {
            txt(value, 216f - font.measureText(value), y, font)
        }
        fun center(value: String, y: Float, font: Paint = bold) {
            txt(value, (227f - font.measureText(value)) / 2, y, font)
        }
        fun hr(y: Float) { c.drawLine(10f, y, 217f, y, dash) }
        fun lines(value: String, maxWidth: Float, font: Paint): List<String> {
            if (value.isBlank()) return emptyList()
            val output = mutableListOf<String>()
            var rest = value.trim()
            while (rest.isNotEmpty()) {
                val n = font.breakText(rest, true, maxWidth, null).coerceAtLeast(1)
                output.add(rest.take(n))
                rest = rest.drop(n)
            }
            return output
        }
        fun meta(label: String, value: String, top: Float): Float {
            val v = value.ifBlank { "-" }
            txt(label, 10f, top, bold)
            return if (small.measureText(v) < 164f) {
                right(v, top, small)
                top + 13f
            } else {
                lines(v, 207f, small).forEachIndexed { index, segment ->
                    txt(segment, 10f, top + 12f + index * 11f, small)
                }
                top + 12f + lines(v, 207f, small).size * 11f
            }
        }

        val logo = context.getDrawable(R.drawable.ic_cpipos_receipt_logo)
        logo?.setBounds(95, 7, 132, 48)
        logo?.draw(c)
        center("CpIPOS", 63f, title)
        var y = 79f
        receipt.storeName.takeIf { it.isNotBlank() }?.let {
            lines(it, 205f, title).forEach { segment ->
                center(segment, y, title)
                y += 15f
            }
        }
        receipt.storeAddress?.takeIf { it.isNotBlank() }?.let {
            lines(it, 200f, small).forEach { line ->
                center(line, y, small)
                y += 12f
            }
        }
        receipt.storePhone?.takeIf { it.isNotBlank() }?.let {
            center(it, y, small)
            y += 12f
        }
        if (receipt.branchName.isNotBlank() && receipt.branchName != receipt.storeName) {
            center(receipt.branchName.take(42), y, bold)
            y += 15f
        }
        if (receipt.isDemo) {
            center("PREVIEW ONLY / บิลทดลอง", y, bold)
            y += 15f
        }
        hr(y)
        y += 14f
        y = meta("ชื่อผู้ขาย", receipt.cashierName, y)
        y = meta("กะ", receipt.shiftLabel ?: receipt.counterCode, y)
        y = meta("โหมด", receipt.modeLabel, y)
        y = meta("เลขที่บิล", receipt.billNo, y)
        val tz = TimeZone.getTimeZone("Asia/Bangkok")
        val buddhistYear = java.util.GregorianCalendar(tz, Locale.US).apply {
            timeInMillis = receipt.createdAtMs
        }.get(java.util.Calendar.YEAR) + 543
        val thaiDay = SimpleDateFormat("d MMM", Locale("th", "TH")).apply {
            timeZone = tz
        }.format(Date(receipt.createdAtMs))
        val clock = SimpleDateFormat("HH:mm", Locale.US).apply {
            timeZone = tz
        }.format(Date(receipt.createdAtMs))
        val date = "$thaiDay $buddhistYear $clock"
        y = meta("วันที่", date, y)
        hr(y)
        y += 14f

        receipt.lines.forEach { item ->
            val nameRows = lines(item.name, 126f, bold)
            nameRows.forEachIndexed { index, line ->
                txt(line, 10f, y, bold)
                if (index == 0) {
                    txt(item.quantity.toString(), 151f, y, bold)
                    right(baht(item.amount), y, bold)
                }
                y += 12f
            }
            txt("× " + baht(item.unitPrice), 10f, y, small)
            y += 19f
        }
        hr(y)
        y += 15f
        txt("การชำระเงิน", 10f, y, bold)
        right("ชำระเงินสด", y, bold)
        y += 15f
        hr(y)
        y += 13f
        txt("ส่วนลด", 10f, y)
        right("฿0.00", y)
        y += 13f
        c.drawLine(10f, y, 217f, y, regular)
        y += 15f
        txt("ยอดที่ต้องชำระ", 10f, y, grand)
        right("฿" + baht(receipt.total), y, grand)
        y += 6f
        c.drawLine(10f, y, 217f, y, regular)
        y += 16f
        txt("รับเงินจากลูกค้า", 10f, y, bold)
        right("฿" + baht(receipt.received), y, bold)
        y += 16f
        txt("เงินทอน", 10f, y, bold)
        right("฿" + baht(receipt.change), y, bold)
        y += 9f
        hr(y)
        y += 16f
        center(
            if (receipt.isDemo) "ใบเสร็จทดสอบ • ไม่ใช่หลักฐานรับเงินจริง"
            else "ขอบคุณที่ใช้บริการ",
            y, small
        )
        center("CpIPOS", y + 14f, bold)
    }
}
