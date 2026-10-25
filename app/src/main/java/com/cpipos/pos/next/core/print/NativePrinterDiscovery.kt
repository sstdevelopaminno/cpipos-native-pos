package com.cpipos.pos.next.core.print

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.core.content.ContextCompat
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.Collections
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import org.json.JSONObject

data class NativePrinterCandidate(
    val id: String,
    val name: String,
    val mode: String,
    val fingerprint: String,
    val ipAddress: String? = null,
    val port: Int? = null,
    val metadata: JSONObject = JSONObject()
)

object NativePrinterDiscovery {
    fun usb(context: Context): List<NativePrinterCandidate> {
        val manager = context.applicationContext.getSystemService(UsbManager::class.java)
            ?: return emptyList()
        return manager.deviceList.values
            .sortedWith(compareBy({ it.vendorId }, { it.productId }, { it.deviceName }))
            .filter { device ->
                (0 until device.interfaceCount).any { i ->
                    val intf = device.getInterface(i)
                    (0 until intf.endpointCount).any { e ->
                        val endpoint = intf.getEndpoint(e)
                        endpoint.direction == UsbConstants.USB_DIR_OUT &&
                            (endpoint.type == UsbConstants.USB_ENDPOINT_XFER_BULK ||
                                endpoint.type == UsbConstants.USB_ENDPOINT_XFER_INT)
                    }
                }
            }
            .map { device ->
                val label = buildString {
                    append("USB Printer ")
                    append(device.vendorId.toString(16).uppercase())
                    append(":")
                    append(device.productId.toString(16).uppercase())
                }
                NativePrinterCandidate(
                    id = "usb:" + device.vendorId + ":" + device.productId + ":" + device.deviceName,
                    name = label,
                    mode = "usb",
                    fingerprint = "usb:" + device.vendorId + ":" + device.productId + ":" + device.deviceName,
                    metadata = JSONObject()
                        .put("usb_vendor_id", device.vendorId)
                        .put("usb_product_id", device.productId)
                        .put("usb_device_id", device.deviceId)
                        .put("usb_device_name", device.deviceName)
                        .put("transport_mode", "usb")
                )
            }
    }

    @Suppress("DEPRECATION")
    fun bluetooth(context: Context): List<NativePrinterCandidate> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) return emptyList()

        val adapter = context.applicationContext
            .getSystemService(BluetoothManager::class.java)?.adapter ?: return emptyList()
        return adapter.bondedDevices.orEmpty()
            .sortedBy { it.name ?: it.address }
            .map { device ->
                val name = device.name?.trim().takeUnless { it.isNullOrBlank() }
                    ?: "Bluetooth " + device.address
                NativePrinterCandidate(
                    id = "bluetooth:" + device.address,
                    name = name,
                    mode = "bluetooth",
                    fingerprint = "bluetooth:" + device.address.lowercase(),
                    metadata = JSONObject()
                        .put("bluetooth_address", device.address)
                        .put("bluetooth_name", name)
                        .put("transport_mode", "bluetooth")
                )
            }
    }

    /**
     * Conservative /24 scan of the active private IPv4 interface for raw ESC/POS port 9100.
     * The cashier can always enter an IP manually when the printer lives on another subnet.
     */
    fun lan(port: Int = 9100): List<NativePrinterCandidate> {
        val local = activeIpv4() ?: return emptyList()
        val octets = local.hostAddress?.split(".")?.mapNotNull(String::toIntOrNull)
            ?: return emptyList()
        if (octets.size != 4) return emptyList()
        val prefix = octets.take(3).joinToString(".")
        val own = octets[3]
        val pool = Executors.newFixedThreadPool(24)
        return try {
            val tasks = (1..254)
                .filter { it != own }
                .map { host ->
                    Callable {
                        val ip = "$prefix.$host"
                        if (portOpen(ip, port)) {
                            NativePrinterCandidate(
                                id = "lan:$ip:$port",
                                name = "LAN Printer · $ip",
                                mode = "lan",
                                fingerprint = "lan:$ip:$port",
                                ipAddress = ip,
                                port = port,
                                metadata = JSONObject()
                                    .put("transport_mode", "lan")
                                    .put("discovery", "local_ipv4_port_9100")
                            )
                        } else null
                    }
                }
            pool.invokeAll(tasks).mapNotNull { future ->
                runCatching { future.get() }.getOrNull()
            }.sortedBy { it.ipAddress }
        } finally {
            pool.shutdownNow()
        }
    }

    private fun portOpen(host: String, port: Int): Boolean =
        runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), 160)
                true
            }
        }.getOrDefault(false)

    private fun activeIpv4(): Inet4Address? {
        val interfaces = runCatching {
            Collections.list(NetworkInterface.getNetworkInterfaces())
        }.getOrDefault(emptyList())
        return interfaces
            .asSequence()
            .filter { runCatching { it.isUp && !it.isLoopback }.getOrDefault(false) }
            .flatMap { Collections.list(it.inetAddresses).asSequence() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { it.isSiteLocalAddress && !it.isLoopbackAddress }
    }
}
