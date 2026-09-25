package com.cpipos.pos.next.printing

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
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

/**
 * System print / Save to PDF: PDF sized for 80 mm paper. Requires an installed
 * Android print service for physical Bluetooth/Wi-Fi printing. No Web/Vercel calls.
 * "PREVIEW" is deliberately printed because this is not a verified cloud sale.
 */
object DemoReceiptPrinter {
    private val money = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.US))
    private fun baht(value: Satang): String = money.format(value.value.toBigDecimal().movePointLeft(2))

    fun print(context: Context, receipt: DemoSaleReceipt) {
        val manager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        val pageHeight = 305 + receipt.lines.size * 43
        val mediaHeight = ((pageHeight / 72.0) * 1000.0).toInt().coerceAtLeast(5000)
        val attributes = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize("CPIPOS_80_MM", "80 mm thermal receipt", 3150, mediaHeight))
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
                        true
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
                        val page = pdf.startPage(PdfDocument.PageInfo.Builder(227, pageHeight, 1).create())
                        render(context, page.canvas, receipt)
                        pdf.finishPage(page)
                        FileOutputStream(destination.fileDescriptor).use(pdf::writeTo)
                        callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                    } catch (error: Exception) {
                        callback.onWriteFailed(error.localizedMessage ?: "Cannot create PDF receipt")
                    } finally {
                        pdf.close()
                    }
                }
            },
            attributes
        )
    }

    private fun render(context: Context, c: Canvas, receipt: DemoSaleReceipt) {
        val normal = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK
            textSize = 9.0f
            typeface = Typeface.DEFAULT
        }
        val bold = Paint(normal).apply { typeface = Typeface.DEFAULT_BOLD }
        val small = Paint(normal).apply { textSize = 8f }
        val rule = Paint(normal).apply { strokeWidth = 0.65f }
        fun text(s: String, x: Float, y: Float, p: Paint = normal) = c.drawText(s, x, y, p)
        fun right(s: String, y: Float, p: Paint = normal) =
            text(s, 216f - p.measureText(s), y, p)
        fun center(s: String, y: Float, p: Paint = bold) =
            text(s, (227f - p.measureText(s)) / 2f, y, p)
        fun hr(y: Float) = c.drawLine(10f, y, 217f, y, rule)

        runCatching {
            val logo = BitmapFactory.decodeResource(context.resources, R.drawable.cpipos_logo_symbol)
            if (logo != null) {
                c.drawBitmap(logo, null, Rect(100, 10, 127, 37), Paint(Paint.FILTER_BITMAP_FLAG))
                logo.recycle()
            }
        }
        center("CpIPOS", 51f, bold)
        center("ตัวอย่าง / PREVIEW ONLY", 64f, bold)
        center(receipt.branchName.take(33), 77f, bold)
        hr(83f)
        text("ผู้ขาย", 10f, 96f)
        right("ทดลอง UI", 96f)
        text("กะ", 10f, 109f)
        right(receipt.counterCode.takeLast(23), 109f, small)
        text("โหมด", 10f, 122f)
        right(receipt.modeLabel.take(25), 122f, small)
        text("เลขที่บิล", 10f, 135f)
        right(receipt.billNo, 135f, small)
        val date = SimpleDateFormat("dd MMM yyyy HH:mm", Locale("th", "TH"))
            .apply { timeZone = TimeZone.getTimeZone("Asia/Bangkok") }
            .format(Date(receipt.createdAtMs))
        text("วันที่", 10f, 148f)
        right(date, 148f, small)
        hr(156f)
        var y = 169f
        receipt.lines.forEach { item ->
            val price = "฿" + baht(item.amount)
            val nameWidth = 130f
            var name = item.name
            while (name.isNotEmpty()) {
                var take = name.length
                while (take > 1 && bold.measureText(name.take(take)) > nameWidth) take--
                text(name.take(take), 10f, y, bold)
                name = name.drop(take)
                y += 12f
            }
            right("${item.quantity} × ${baht(item.unitPrice)}", y, small)
            right(price, y + 12f, bold)
            y += 22f
        }
        hr(y)
        y += 14f
        text("ชำระเงิน", 10f, y, bold)
        right("เงินสด (ทดลอง)", y, bold)
        y += 13f
        text("ส่วนลด", 10f, y)
        right("฿0.00", y)
        y += 12f
        hr(y)
        y += 16f
        text("ยอดที่ต้องชำระ", 10f, y, bold)
        right("฿" + baht(receipt.total), y, bold)
        y += 17f
        text("รับเงินจากลูกค้า", 10f, y, bold)
        right("฿" + baht(receipt.received), y, bold)
        y += 15f
        text("เงินทอน", 10f, y, bold)
        right("฿" + baht(receipt.change), y, bold)
        y += 9f
        hr(y)
        center("ใบเสร็จทดสอบ • ไม่ใช่หลักฐานชำระเงินจริง", y + 13f, small)
        center("ขอบคุณที่ใช้บริการ", y + 26f, normal)
    }
}
