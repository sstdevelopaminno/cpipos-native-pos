package com.cpipos.pos.next.printing

import android.content.Context
import android.graphics.Bitmap
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
import com.cpipos.pos.next.core.webpos.WebTableOrderQr
import java.io.FileOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.ceil

/**
 * Print exactly the server-issued ordering QR for the selected opened table.
 * Android print-service mediation prevents silently claiming a print completed.
 * Never generate a local URL/token or submit a customer order.
 */
object TableOrderQrPrinter {
    private val bangkok = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
        .withZone(ZoneId.of("Asia/Bangkok"))

    fun print(context: Context, data: WebTableOrderQr, bitmap: Bitmap, branch: String, paperMm: Int = 80) {
        require(paperMm == 80 || paperMm == 58)
        require(bitmap.width > 0 && bitmap.height > 0)
        require(Instant.parse(data.expiresAt).isAfter(Instant.now())) { "table_qr_expired" }
        val printer = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        val factor = paperMm / 80f
        val width = ceil(227 * factor).toInt()
        val height = ceil(345 * factor).toInt()
        val widthMils = (paperMm * 1000 / 25.4f).toInt()
        val heightMils = (height * 1000 / 72f).toInt()
        val attributes = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize(
                "CPIPOS_QR_$paperMm", "Table QR $paperMm mm", widthMils, heightMils))
            .setResolution(PrintAttributes.Resolution("CPIPOS_QR_203", "203 dpi", 203, 203))
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .setColorMode(PrintAttributes.COLOR_MODE_MONOCHROME)
            .build()

        printer.print("CpIPOS-QR-${data.tableCode}",
            object : PrintDocumentAdapter() {
                override fun onLayout(
                    oldAttributes: PrintAttributes?, newAttributes: PrintAttributes?,
                    cancellationSignal: CancellationSignal, callback: LayoutResultCallback,
                    extras: Bundle?
                ) {
                    if (cancellationSignal.isCanceled) {
                        callback.onLayoutCancelled()
                        return
                    }
                    callback.onLayoutFinished(
                        PrintDocumentInfo.Builder("table-order-qr.pdf")
                            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                            .setPageCount(1).build(),
                        oldAttributes != newAttributes
                    )
                }

                override fun onWrite(
                    pages: Array<out PageRange>, destination: ParcelFileDescriptor,
                    cancellationSignal: CancellationSignal, callback: WriteResultCallback
                ) {
                    if (cancellationSignal.isCanceled) {
                        callback.onWriteCancelled()
                        return
                    }
                    val pdf = PdfDocument()
                    try {
                        val page = pdf.startPage(
                            PdfDocument.PageInfo.Builder(width, height, 1).create())
                        val canvas = page.canvas
                        canvas.save()
                        canvas.scale(factor, factor)
                        val bold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.BLACK
                            typeface = Typeface.create("sans-serif", Typeface.BOLD)
                            textSize = 13f
                        }
                        val regular = Paint(bold).apply {
                            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                            textSize = 9f
                        }
                        fun centered(text: String, y: Float, paint: Paint) {
                            canvas.drawText(text, (227f - paint.measureText(text)) / 2f, y, paint)
                        }
                        centered("CpIPOS", 26f, bold)
                        centered(branch.take(28), 46f, regular)
                        centered("โต๊ะ ${data.tableName.ifBlank { data.tableCode }}", 71f, bold)
                        centered("สแกน QR เพื่อสั่งอาหาร", 91f, regular)
                        // QR stays square; nearest-neighbor keeps module edges sharp.
                        val qr = android.graphics.Rect(21, 102, 206, 287)
                        val qrPaint = Paint().apply { isFilterBitmap = false }
                        canvas.drawBitmap(bitmap, null, qr, qrPaint)
                        centered("หมดอายุ: ${bangkok.format(Instant.parse(data.expiresAt))}", 310f, regular)
                        centered("เมื่อชำระเงิน/ปิดบิล QR จะหยุดทำงาน", 327f, regular)
                        canvas.restore()
                        pdf.finishPage(page)
                        FileOutputStream(destination.fileDescriptor).use(pdf::writeTo)
                        callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                    } catch (e: Exception) {
                        callback.onWriteFailed(e.message ?: "Unable to print table QR")
                    } finally {
                        pdf.close()
                    }
                }
            }, attributes)
    }
}
