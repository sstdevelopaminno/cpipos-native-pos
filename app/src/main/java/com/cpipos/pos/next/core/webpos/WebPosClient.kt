package com.cpipos.pos.next.core.webpos

import com.cpipos.pos.next.core.offline.DemoReceiptLine
import com.cpipos.pos.next.core.offline.DemoSaleReceipt
import com.cpipos.pos.next.core.pos.Satang
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

data class WebBranch(val id: String, val name: String, val code: String)
data class WebStore(val tenantId: String, val name: String, val branches: List<WebBranch>)
data class WebEmployee(val id: String, val name: String, val code: String)
data class WebCashierDevice(
    val id: String,
    val code: String,
    val name: String,
    val counterName: String,
    val status: String,
    val currentUserName: String?
)
data class WebCashierDevices(
    val branchId: String,
    val branchName: String,
    val employeeName: String,
    val canOverrideInUse: Boolean,
    val devices: List<WebCashierDevice>
)
data class WebPosProduct(
    val id: String, val name: String, val sku: String, val category: String, val price: Satang
)
data class WebPosSession(
    val id: String, val tenantId: String, val branchId: String,
    val branchName: String, val storeName: String, val cashierName: String,
    val employeeId: String, val deviceCode: String,
    val shiftId: String?, val canOpenShift: Boolean, val canSell: Boolean
)
data class WebPaidBill(val orderId: String, val receipt: DemoSaleReceipt)
class WebPosApiException(val code: String, val status: Int) : Exception("POS API: $code ($status)")

/**
 * Existing CpIPOS Web backend is the server authority for Store Code, employee code,
 * device policy, active shift, server-side product prices, sale/payment RPC,
 * RLS, stock and receipt profile. Android holds only HTTP-only opaque cookies
 * in volatile memory (re-login required after process restart). No service role
 * / PIN hash / fake JWT is embedded here.
 *
 * The Web cookie session is NOT a Supabase JWT. Never use it against PostgREST.
 */
class WebPosClient(baseUrl: String) {
    private val base = baseUrl.trimEnd('/').also {
        require(it.startsWith("https://") && !it.contains('@') && !it.contains('?')) {
            "CpIPOS Web endpoint must be HTTPS"
        }
    }
    private val cookieManager = CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER)

    private suspend fun call(
        method: String, path: String, json: JSONObject? = null, key: String? = null
    ): JSONObject = withContext(Dispatchers.IO) {
        require(path.startsWith("/") && !path.startsWith("//"))
        val url = URL(base + path)
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 8000
            conn.readTimeout = 15000
            conn.requestMethod = method
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("User-Agent", "CpIPOS-Native-Android/2")
            cookieManager.get(url.toURI(), emptyMap()).forEach { (header, values) ->
                if (header.equals("Cookie", ignoreCase = true)) {
                    conn.setRequestProperty("Cookie", values.joinToString("; "))
                }
            }
            if (key != null) {
                require(UUID.fromString(key).toString() == key)
                conn.setRequestProperty("X-Idempotency-Key", key)
            }
            if (json != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(json.toString().toByteArray(Charsets.UTF_8)) }
            }
            val status = conn.responseCode
            val cookies = conn.headerFields.filterKeys { it?.equals("Set-Cookie", true) == true }
            if (cookies.isNotEmpty()) cookieManager.put(url.toURI(), cookies)
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            val document = runCatching { JSONObject(response) }.getOrElse {
                throw WebPosApiException("non_json_response", status)
            }
            if (status !in 200..299 || !document.isNull("error")) {
                val code = document.optJSONObject("error")?.optString("code")
                    ?.takeIf { it.isNotBlank() } ?: "http_$status"
                throw WebPosApiException(code, status)
            }
            document.optJSONObject("data") ?: throw WebPosApiException("missing_data", status)
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Canonical browser/POS pre-entry flow. Employee code is verified BEFORE
     * fetching scoped cashier devices; only device selection issues a POS session.
     * No native pretend PIN session, no unscoped public device directory.
     */
    suspend fun startStoreEntry(storeCode: String): WebStore {
        require(storeCode.trim().matches(Regex("[A-Za-z0-9._:-]{3,64}")))
        val data = call("POST", "/api/auth/store-code/verify",
            JSONObject().put("store_code", storeCode.trim().uppercase()))
        val tenant = data.getJSONObject("tenant")
        val branches = data.optJSONArray("branches") ?: JSONArray()
        return WebStore(
            tenantId = tenant.optString("id"),
            name = tenant.optString("name"),
            branches = (0 until branches.length()).map { i ->
                val row = branches.getJSONObject(i)
                WebBranch(
                    id = row.getString("id"),
                    name = row.optString("name"),
                    code = row.optString("code")
                )
            }
        )
    }

    suspend fun selectEntryBranch(branchId: String) {
        require(runCatching { UUID.fromString(branchId) }.isSuccess)
        val data = call("POST", "/api/auth/branches/select",
            JSONObject().put("branch_id", branchId))
        require(data.getJSONObject("selected_branch").getString("id") == branchId) {
            "Backend branch did not match the requested branch"
        }
    }

    suspend fun verifyEmployeeCode(employeeCode: String): WebEmployee {
        // This is the back-office employee code, not a cashier device code.
        require(employeeCode.matches(Regex("[0-9]{1,32}")))
        val data = call("POST", "/api/auth/employee/verify-code",
            JSONObject().put("employee_code", employeeCode))
        val employee = data.getJSONObject("employee")
        val id = employee.getString("id")
        require(runCatching { UUID.fromString(id) }.isSuccess)
        return WebEmployee(id, employee.optString("name"), employee.optString("code"))
    }

    suspend fun listEntryDevices(): WebCashierDevices {
        val data = call("GET", "/api/auth/devices")
        val branch = data.getJSONObject("branch")
        val employee = data.getJSONObject("employee")
        val rows = data.optJSONArray("devices") ?: JSONArray()
        return WebCashierDevices(
            branchId = branch.getString("id"),
            branchName = branch.optString("name"),
            employeeName = employee.optString("name"),
            canOverrideInUse = data.optBoolean("can_override_in_use", false),
            devices = (0 until rows.length()).map { i ->
                val row = rows.getJSONObject(i)
                WebCashierDevice(
                    id = row.getString("deviceId"),
                    code = row.getString("deviceCode"),
                    name = row.optString("deviceName"),
                    counterName = row.optString("counterName"),
                    status = row.optString("status"),
                    currentUserName = row.optJSONObject("currentUser")
                        ?.optString("name")?.takeIf { it.isNotBlank() }
                )
            }
        )
    }

    suspend fun selectEntryDevice(
        branchId: String, employeeId: String, selected: WebCashierDevice
    ): WebPosSession {
        require(runCatching { UUID.fromString(branchId) }.isSuccess)
        require(runCatching { UUID.fromString(employeeId) }.isSuccess)
        require(selected.status == "ready" || selected.status == "in_use") {
            "Device is not selectable"
        }
        val data = call("POST", "/api/auth/devices/select",
            JSONObject().put("device_code", selected.code))
        val sessionId = data.getString("session_id")
        val session = getSession()
        if (session.id != sessionId || session.branchId != branchId ||
            session.employeeId != employeeId || session.deviceCode != selected.code) {
            throw WebPosApiException("selected_device_session_scope_mismatch", 403)
        }
        return session
    }

    suspend fun getSession(): WebPosSession {
        val d = call("GET", "/api/pos/session/current")
        val user = d.getJSONObject("user")
        val tenant = d.getJSONObject("tenant")
        val branch = d.getJSONObject("branch")
        val device = d.getJSONObject("device")
        val session = d.getJSONObject("session")
        if (session.optString("status") != "active" ||
            device.optBoolean("block_sales", false) ||
            device.optString("status") != "active") {
            throw WebPosApiException("device_or_session_blocked", 403)
        }
        val shift = d.optJSONObject("shift")
        val permissions = d.optJSONArray("permissions") ?: JSONArray()
        val values = (0 until permissions.length()).map { permissions.optString(it) }.toSet()
        return WebPosSession(
            id = session.getString("id"),
            tenantId = tenant.getString("id"),
            branchId = branch.getString("id"),
            branchName = branch.optString("name"),
            storeName = tenant.optString("name"),
            cashierName = user.optString("full_name"),
            employeeId = user.getString("id"),
            deviceCode = device.getString("code"),
            shiftId = if (shift?.optString("status") == "open")
                shift.optString("id").takeIf { it?.isNotBlank() == true } else null,
            canOpenShift = "shift:open" in values,
            canSell = "sale:create" in values && "sales:enter" in values
        )
    }

    suspend fun openShift(): WebPosSession {
        call("POST", "/api/pos/shifts/open", JSONObject().put("opening_cash", 0))
        return getSession()
    }

    suspend fun products(session: WebPosSession): List<WebPosProduct> {
        check(session.shiftId != null && session.canSell) { "An authorized open shift is required" }
        val d = call("GET", "/api/pos/products")
        val shift = d.optJSONObject("shift") ?: throw WebPosApiException("shift_missing", 409)
        if (shift.optString("id") != session.shiftId || shift.optString("status") != "open") {
            throw WebPosApiException("shift_changed", 409)
        }
        val rows = d.getJSONArray("products")
        return (0 until rows.length()).map { i ->
            val item = rows.getJSONObject(i)
            val amount = Satang.fromBaht(item.get("price").toString())
            require(amount >= Satang.ZERO)
            WebPosProduct(item.getString("id"), item.getString("name"),
                item.optString("sku"), item.optString("category"), amount)
        }
    }

    /** Online only: server creates scoped idempotent order and separately settles payment.
     * On uncertain network outcome caller MUST NOT discard bill ID or retry with a new key.
     * Payment result must state completed and return canonical server order number.
     */
    suspend fun payCash(
        session: WebPosSession, lines: List<DemoReceiptLine>,
        received: Satang, saleId: String,
        onOrderCreated: suspend (String) -> Unit
    ): WebPaidBill {
        require(lines.isNotEmpty() && lines.all { it.quantity in 1..9999 })
        require(lines.map { it.productId }.distinct().size == lines.size)
        require(session.canSell && session.shiftId != null)
        val total = lines.fold(Satang.ZERO) { sum, line -> sum + line.amount }
        require(total > Satang.ZERO && received >= total)
        val current = getSession()
        if (current.id != session.id || current.tenantId != session.tenantId ||
            current.branchId != session.branchId || current.shiftId != session.shiftId ||
            current.deviceCode != session.deviceCode || !current.canSell) {
            throw WebPosApiException("cashier_session_or_shift_changed", 409)
        }
        val items = JSONArray()
        lines.forEach { line ->
            require(runCatching { UUID.fromString(line.productId) }.isSuccess)
            items.put(JSONObject().put("product_id", line.productId).put("quantity", line.quantity))
        }
        val order = call("POST", "/api/pos/orders",
            JSONObject().put("items", items).put("discount_total", 0),
            key = saleId).getJSONObject("order")
        val serverTotal = Satang.fromBaht(
            order.opt("grand_total")?.toString()?.takeIf { it != "null" }
                ?: order.get("total_amount").toString()
        )
        if (serverTotal != total) throw WebPosApiException("server_price_changed_review_order", 409)
        val orderId = order.getString("id")
        onOrderCreated(orderId)
        val paid = call("POST", "/api/pos/orders/$orderId/pay",
            JSONObject().put("method", "cash").put("amount", BigDecimal(received.bahtText())),
            key = UUID.nameUUIDFromBytes(("native-pay:$saleId").toByteArray()).toString())
        val paymentOrder = paid.getJSONObject("order")
        if (paymentOrder.getString("id") != orderId ||
            paymentOrder.optString("status") != "completed") {
            throw WebPosApiException("server_payment_not_confirmed", 409)
        }
        val actual = paid.optJSONObject("receipt_preview")
        val store = actual?.optJSONObject("store_profile")
        val serverReceiptNo = paymentOrder.getString("order_no")
        require(serverReceiptNo.isNotBlank())
        val snapshotTotal = Satang.fromBaht(actual?.get("total")?.toString() ?: serverTotal.bahtText())
        if (snapshotTotal != serverTotal) throw WebPosApiException("receipt_total_mismatch_review", 409)
        return WebPaidBill(
            orderId,
            DemoSaleReceipt(
                id = orderId,
                billNo = serverReceiptNo,
                createdAtMs = System.currentTimeMillis(),
                branchName = session.branchName,
                counterCode = session.deviceCode,
                modeLabel = "กลับบ้าน",
                lines = lines.toList(),
                total = serverTotal,
                received = received,
                change = received - serverTotal,
                isDemo = false,
                storeName = store?.optString("display_name")?.takeIf { it.isNotBlank() }
                    ?: session.storeName,
                storeAddress = store?.optString("company_address"),
                storePhone = store?.optString("contact_phone"),
                cashierName = session.cashierName,
                shiftLabel = "open"
            )
        )
    }
}
