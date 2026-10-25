package com.cpipos.pos.next.core.print

import android.Manifest
import android.app.PendingIntent
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.util.Base64
import android.os.Build
import androidx.core.content.ContextCompat
import com.cpipos.pos.next.R
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.nio.charset.Charset
import java.util.UUID

data class NativePrinterProfile(
    val id: String,
    val name: String,
    val connectionType: String,
    val ipAddress: String?,
    val port: Int?,
    val paperWidthMm: Int,
    val metadata: JSONObject
)

data class NativePrintJob(
    val id: String,
    val attemptId: String,
    val payloadText: String,
    val payloadJson: JSONObject,
    val metadata: JSONObject,
    val printer: NativePrinterProfile
)

data class NativePrintResult(
    val bytesSent: Int,
    val transport: String,
    val providerJobId: String
)

class NativePrintException(
    val code: String,
    val retryable: Boolean,
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

class NativePrinterTransport(context: Context) {
    private val appContext = context.applicationContext
    private val usbManager = appContext.getSystemService(UsbManager::class.java)
    private val bluetoothManager = appContext.getSystemService(BluetoothManager::class.java)

    fun parseJob(row: JSONObject): NativePrintJob {
        val printer = when (val value = row.opt("printer_profiles")) {
            is JSONObject -> value
            is JSONArray -> value.optJSONObject(0)
            else -> null
        } ?: throw NativePrintException("printer_profile_missing", false, "ไม่พบข้อมูลเครื่องพิมพ์")
        return NativePrintJob(
            id = row.getString("id"),
            attemptId = row.getString("agent_attempt_id"),
            payloadText = row.optString("payload_text", ""),
            payloadJson = row.optJSONObject("payload_json") ?: JSONObject(),
            metadata = row.optJSONObject("metadata") ?: JSONObject(),
            printer = NativePrinterProfile(
                id = printer.optString("id"),
                name = printer.optString("printer_name", "Printer"),
                connectionType = printer.optString("connection_type"),
                ipAddress = printer.optString("ip_address").trim().takeIf { it.isNotEmpty() },
                port = if (printer.has("port") && !printer.isNull("port"))
                    printer.optInt("port", 9100) else null,
                paperWidthMm = printer.optInt(
                    "paper_width_mm",
                    row.optJSONObject("metadata")?.optInt("paper_width_mm", 58) ?: 58
                ).let { if (it == 80) 80 else 58 },
                metadata = printer.optJSONObject("metadata") ?: JSONObject()
            )
        )
    }

    fun print(job: NativePrintJob): NativePrintResult {
        val mode = job.printer.metadata.optString("transport_mode", "").trim().lowercase()
        val payload = buildPayload(job)
        return when {
            job.printer.connectionType == "NETWORK_ESC_POS" || mode == "lan" ->
                printLan(job.printer, payload)
            job.printer.connectionType == "LOCAL_BRIDGE" || mode == "usb" ->
                printUsb(job.printer, payload)
            job.printer.connectionType == "BLUETOOTH_BRIDGE" || mode == "bluetooth" ->
                printBluetooth(job.printer, payload)
            else -> throw NativePrintException(
                "unsupported_transport", false,
                "ไม่รองรับการเชื่อมต่อ " + job.printer.connectionType
            )
        }
    }

    private fun buildPayload(job: NativePrintJob): ByteArray {
        val command = job.metadata.optString("command", "").lowercase()
        if (command == "open_cash_drawer" || job.payloadText.trim() == "OPEN_CASH_DRAWER") {
            return byteArrayOf(0x1B, 0x70, 0x00, 0x19, 0x7D)
        }
        if (job.payloadText.isBlank()) {
            throw NativePrintException(
                "print_payload_missing", false,
                "งานพิมพ์นี้ไม่มีข้อความ ESC/POS สำหรับ Android Native"
            )
        }
        val charsetName = job.printer.metadata.optString(
            "escpos_charset", "windows-874"
        ).ifBlank { "windows-874" }
        val charset = runCatching { Charset.forName(charsetName) }.getOrElse { Charsets.UTF_8 }
        val codeTable = job.printer.metadata.optInt("escpos_code_table", 26).coerceIn(0, 255)
        val feed = job.printer.metadata.optInt("feed_lines", 3).coerceIn(1, 8)
        val autoCut = job.printer.metadata.optBoolean("auto_cut", false)
        return ByteArrayOutputStream().apply {
            write(byteArrayOf(0x1B, 0x40))
            val logo = loadReceiptLogo(job)
            if (logo != null) {
                try {
                    write(byteArrayOf(0x1B, 0x61, 0x01))
                    write(escPosRaster(logo, job.printer.paperWidthMm))
                    write('\n'.code)
                    write(byteArrayOf(0x1B, 0x61, 0x00))
                } finally {
                    logo.recycle()
                }
            }
            write(byteArrayOf(0x1B, 0x74, codeTable.toByte()))
            write(job.payloadText.replace("\r\n", "\n").toByteArray(charset))
            repeat(feed) { write('\n'.code) }
            if (autoCut) write(byteArrayOf(0x1D, 0x56, 0x00))
        }.toByteArray()
    }

    private fun loadReceiptLogo(job: NativePrintJob): Bitmap? {
        val logoUrl = sequenceOf(
            job.payloadJson.optString("store_logo_url"),
            job.payloadJson.optString("logo_url"),
            job.payloadJson.optString("logo")
        ).map(String::trim).firstOrNull { it.isNotEmpty() }

        val configured = logoUrl?.let { value ->
            when {
                value.startsWith("data:image/") -> decodeDataBitmap(value)
                value.startsWith("https://") || value.startsWith("http://") -> downloadBitmap(value)
                else -> null
            }
        }
        if (configured != null) return configured
        return drawableBitmap(ContextCompat.getDrawable(appContext, R.drawable.ic_cpipos_receipt_logo))
    }

    private fun decodeDataBitmap(value: String): Bitmap? = runCatching {
        val comma = value.indexOf(',')
        if (comma <= 0 || value.length > 250_000) return@runCatching null
        val bytes = Base64.decode(value.substring(comma + 1), Base64.DEFAULT)
        if (bytes.size > 190_000) return@runCatching null
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()

    private fun downloadBitmap(value: String): Bitmap? = runCatching {
        val url = URL(value)
        if (url.protocol != "https") return@runCatching null
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 3000
            connection.readTimeout = 3500
            connection.setRequestProperty("Accept", "image/*")
            val status = connection.responseCode
            if (status !in 200..299) return@runCatching null
            val declared = connection.contentLengthLong
            if (declared > 1_000_000L) return@runCatching null
            connection.inputStream.use { BitmapFactory.decodeStream(it) }
        } finally {
            connection.disconnect()
        }
    }.getOrNull()

    private fun drawableBitmap(drawable: Drawable?): Bitmap? {
        drawable ?: return null
        val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 220
        val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 140
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            drawable.setBounds(0, 0, width, height)
            drawable.draw(canvas)
        }
    }

    private fun escPosRaster(source: Bitmap, paperWidthMm: Int): ByteArray {
        val maxWidth = if (paperWidthMm == 80) 480 else 320
        val maxHeight = if (paperWidthMm == 80) 180 else 140
        val ratio = minOf(
            maxWidth.toFloat() / source.width.coerceAtLeast(1),
            maxHeight.toFloat() / source.height.coerceAtLeast(1),
            1f
        )
        val width = (source.width * ratio).toInt().coerceAtLeast(1)
        val height = (source.height * ratio).toInt().coerceAtLeast(1)
        val bitmap = if (width == source.width && height == source.height) source
            else Bitmap.createScaledBitmap(source, width, height, true)
        try {
            val widthBytes = (width + 7) / 8
            val image = ByteArray(widthBytes * height)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val pixel = bitmap.getPixel(x, y)
                    val alpha = Color.alpha(pixel)
                    val luminance = (
                        Color.red(pixel) * 299 +
                            Color.green(pixel) * 587 +
                            Color.blue(pixel) * 114
                        ) / 1000
                    if (alpha >= 64 && luminance < 190) {
                        val index = y * widthBytes + (x / 8)
                        image[index] = (image[index].toInt() or (0x80 shr (x % 8))).toByte()
                    }
                }
            }
            return ByteArrayOutputStream().apply {
                write(byteArrayOf(
                    0x1D, 0x76, 0x30, 0x00,
                    (widthBytes and 0xFF).toByte(),
                    ((widthBytes shr 8) and 0xFF).toByte(),
                    (height and 0xFF).toByte(),
                    ((height shr 8) and 0xFF).toByte()
                ))
                write(image)
            }.toByteArray()
        } finally {
            if (bitmap !== source) bitmap.recycle()
        }
    }

    private fun printLan(printer: NativePrinterProfile, payload: ByteArray): NativePrintResult {
        val host = printer.ipAddress?.trim().orEmpty()
        if (host.isEmpty()) throw NativePrintException(
            "lan_ip_missing", false, "ยังไม่ได้ตั้ง IP เครื่องพิมพ์ LAN"
        )
        val port = (printer.port ?: 9100).coerceIn(1, 65535)
        try {
            Socket().use { socket ->
                socket.tcpNoDelay = true
                socket.connect(InetSocketAddress(host, port), 3500)
                socket.getOutputStream().use {
                    it.write(payload)
                    it.flush()
                }
            }
            return NativePrintResult(payload.size, "lan", "android-lan:" + host + ":" + port)
        } catch (error: Throwable) {
            throw NativePrintException(
                "lan_print_failed", true,
                error.message ?: "LAN printer unavailable", error
            )
        }
    }

    private data class UsbTarget(
        val device: UsbDevice,
        val usbInterface: UsbInterface,
        val endpoint: UsbEndpoint
    )

    private fun findUsbTarget(printer: NativePrinterProfile): UsbTarget? {
        val manager = usbManager ?: return null
        val metadata = printer.metadata
        val vendor = metadata.optInt("usb_vendor_id", -1).takeIf { it >= 0 }
        val product = metadata.optInt("usb_product_id", -1).takeIf { it >= 0 }
        val deviceName = metadata.optString("usb_device_name", "").trim()
        val devices = manager.deviceList.values.sortedBy { it.deviceName }
        for (device in devices) {
            if (vendor != null && device.vendorId != vendor) continue
            if (product != null && device.productId != product) continue
            if (deviceName.isNotEmpty() && device.deviceName != deviceName) continue
            for (i in 0 until device.interfaceCount) {
                val intf = device.getInterface(i)
                for (e in 0 until intf.endpointCount) {
                    val endpoint = intf.getEndpoint(e)
                    if (endpoint.direction == UsbConstants.USB_DIR_OUT &&
                        (endpoint.type == UsbConstants.USB_ENDPOINT_XFER_BULK ||
                            endpoint.type == UsbConstants.USB_ENDPOINT_XFER_INT)
                    ) return UsbTarget(device, intf, endpoint)
                }
            }
        }
        return null
    }

    private fun printUsb(printer: NativePrinterProfile, payload: ByteArray): NativePrintResult {
        val manager = usbManager ?: throw NativePrintException(
            "usb_not_supported", false, "เครื่องนี้ไม่รองรับ USB Host"
        )
        val target = findUsbTarget(printer) ?: throw NativePrintException(
            "usb_printer_not_found", true, "ไม่พบเครื่องพิมพ์ USB ที่ตั้งค่าไว้"
        )
        if (!manager.hasPermission(target.device)) {
            val intent = PendingIntent.getBroadcast(
                appContext,
                target.device.deviceId,
                Intent(USB_PERMISSION_ACTION).setPackage(appContext.packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            manager.requestPermission(target.device, intent)
            throw NativePrintException(
                "usb_permission_required", true,
                "กรุณาอนุญาตสิทธิ์ USB บนหน้าจอ Android"
            )
        }
        val connection = manager.openDevice(target.device) ?: throw NativePrintException(
            "usb_open_failed", true, "เปิดเครื่องพิมพ์ USB ไม่สำเร็จ"
        )
        try {
            if (!connection.claimInterface(target.usbInterface, true)) {
                throw NativePrintException("usb_claim_failed", true, "ยึด USB interface ไม่สำเร็จ")
            }
            var offset = 0
            while (offset < payload.size) {
                val size = minOf(16_384, payload.size - offset)
                val sent = connection.bulkTransfer(target.endpoint, payload, offset, size, 5000)
                if (sent <= 0) throw NativePrintException(
                    "usb_write_failed", true, "เครื่องพิมพ์ USB ไม่รับข้อมูล"
                )
                offset += sent
            }
            return NativePrintResult(
                payload.size, "usb",
                "android-usb:" + target.device.vendorId + ":" +
                    target.device.productId + ":" + target.device.deviceId
            )
        } finally {
            runCatching { connection.releaseInterface(target.usbInterface) }
            connection.close()
        }
    }

    @Suppress("DEPRECATION")
    private fun printBluetooth(printer: NativePrinterProfile, payload: ByteArray): NativePrintResult {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_CONNECT) !=
            PackageManager.PERMISSION_GRANTED
        ) throw NativePrintException(
            "bluetooth_permission_required", true, "กรุณาอนุญาตสิทธิ์ Bluetooth"
        )
        val adapter = bluetoothManager?.adapter ?: throw NativePrintException(
            "bluetooth_not_supported", false, "เครื่องนี้ไม่มี Bluetooth"
        )
        if (!adapter.isEnabled) throw NativePrintException(
            "bluetooth_disabled", true, "กรุณาเปิด Bluetooth"
        )
        val address = printer.metadata.optString("bluetooth_address", "").trim()
        val name = printer.metadata.optString("bluetooth_name", printer.name).trim()
        val bonded = adapter.bondedDevices.orEmpty()
        val device = when {
            address.isNotEmpty() -> bonded.firstOrNull { it.address.equals(address, true) }
            name.isNotEmpty() -> bonded.firstOrNull { it.name?.equals(name, true) == true }
                ?: bonded.firstOrNull { it.name?.contains(name, true) == true }
            bonded.size == 1 -> bonded.first()
            else -> null
        } ?: throw NativePrintException(
            "bluetooth_printer_not_paired", true,
            "ยังไม่ได้จับคู่เครื่องพิมพ์ Bluetooth กับ Android"
        )
        try {
            val socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            socket.use {
                it.connect()
                it.outputStream.use { output ->
                    output.write(payload)
                    output.flush()
                }
            }
            return NativePrintResult(
                payload.size, "bluetooth", "android-bt:" + device.address
            )
        } catch (error: Throwable) {
            throw NativePrintException(
                "bluetooth_print_failed", true,
                error.message ?: "Bluetooth print failed", error
            )
        }
    }

    companion object {
        private const val USB_PERMISSION_ACTION =
            "com.cpipos.pos.next.USB_PRINTER_PERMISSION"
        private val SPP_UUID: UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}
