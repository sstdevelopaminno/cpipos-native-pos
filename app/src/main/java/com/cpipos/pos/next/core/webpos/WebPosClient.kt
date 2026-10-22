package com.cpipos.pos.next.core.webpos

import com.cpipos.pos.next.BuildConfig
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
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
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
    val id: String,
    val name: String,
    val sku: String,
    val category: String,
    val price: Satang,
    val imageUrl: String? = null
)
/**
 * POS Web keeps product photos in /api/pos/product-media, not in /api/pos/products.
 * Use only the product IDs from the authenticated, branch-scoped catalog.
 * Do not need a Supabase key or infer public storage paths in the Android build.
 */
internal fun mergeCanonicalProductMedia(
    products: List<WebPosProduct>, images: JSONObject
): List<WebPosProduct> = products.map { product ->
    val image = images.optJSONObject(product.id)
    val bestUrl = sequenceOf(
        image?.optString("thumbnail_url"),
        image?.optString("image_url"),
        product.imageUrl
    ).mapNotNull { it?.trim()?.takeIf { url -> url.startsWith("https://") } }
        .firstOrNull()
    product.copy(imageUrl = bestUrl)
}

data class WebPosSession(
    val id: String, val tenantId: String, val branchId: String,
    val branchName: String, val storeName: String, val cashierName: String,
    val employeeId: String, val deviceCode: String,
    val shiftId: String?, val canOpenShift: Boolean, val canSell: Boolean
)
data class WebCreatedOrder(
    val id: String,
    val orderNo: String,
    val status: String,
    val total: Satang
)

data class WebOrderSnapshot(
    val id: String,
    val orderNo: String,
    val status: String,
    val shiftId: String?,
    val total: Satang,
    val paidTotal: Satang,
    val createdAtMs: Long
)

data class WebReceiptHistoryRecord(
    val orderId: String,
    val orderNo: String,
    val status: String,
    val createdAtMs: Long,
    val paymentMethods: List<String>,
    val receipt: DemoSaleReceipt
)

data class WebReceiptHistory(
    val records: List<WebReceiptHistoryRecord>,
    val page: Int,
    val pageSize: Int,
    val total: Int,
    val totalPages: Int
)

data class WebOrderRecovery(
    val snapshot: WebOrderSnapshot,
    val receipt: DemoSaleReceipt?
)

data class WebPaidBill(val orderId: String, val receipt: DemoSaleReceipt)
class WebPosApiException(val code: String, val status: Int) : Exception("POS API: $code ($status)")

internal fun productImageUrlFrom(
    item: JSONObject,
    supabaseUrl: String = BuildConfig.SUPABASE_URL
): String? {
    directProductImageUrl(item)?.let { return it }
    return publicProductMediaUrl(productMediaObjectPath(item), supabaseUrl)
}

internal fun publicProductMediaUrl(objectPath: String?, supabaseUrl: String): String? {
    val path = objectPath?.trim()?.trimStart('/')?.takeIf { it.isNotBlank() } ?: return null
    val base = supabaseUrl.trim().trimEnd('/')
    return if (base.startsWith("https://")) {
        "$base/storage/v1/object/public/product-media/$path"
    } else null
}

private fun directProductImageUrl(item: JSONObject): String? {
    val directKeys = listOf(
        "image_url", "imageUrl", "photo_url", "photoUrl",
        "thumbnail_url", "thumbnailUrl", "display_url", "displayUrl"
    )
    directKeys.firstNotNullOfOrNull { item.optHttpsUrl(it) }?.let { return it }

    val nestedKeys = listOf("media", "media_asset", "mediaAsset", "product_media_asset", "productMediaAsset")
    nestedKeys.mapNotNull { item.optJSONObject(it) }.forEach { media ->
        directKeys.firstNotNullOfOrNull { media.optHttpsUrl(it) }?.let { return it }
    }

    val nestedArrays = listOf("media_assets", "mediaAssets", "product_media_assets", "productMediaAssets")
    nestedArrays.mapNotNull { item.optJSONArray(it) }.forEach { assets ->
        for (i in 0 until assets.length()) {
            val media = assets.optJSONObject(i) ?: continue
            directKeys.firstNotNullOfOrNull { media.optHttpsUrl(it) }?.let { return it }
        }
    }
    return null
}

private fun productMediaObjectPath(item: JSONObject): String? {
    val pathKeys = listOf(
        "thumbnail_object_path", "thumbnailObjectPath",
        "display_object_path", "displayObjectPath"
    )
    pathKeys.firstNotNullOfOrNull { item.optPath(it) }?.let { return it }

    val nestedKeys = listOf("media", "media_asset", "mediaAsset", "product_media_asset", "productMediaAsset")
    nestedKeys.mapNotNull { item.optJSONObject(it) }.forEach { media ->
        pathKeys.firstNotNullOfOrNull { media.optPath(it) }?.let { return it }
    }

    val nestedArrays = listOf("media_assets", "mediaAssets", "product_media_assets", "productMediaAssets")
    nestedArrays.mapNotNull { item.optJSONArray(it) }.forEach { assets ->
        for (i in 0 until assets.length()) {
            val media = assets.optJSONObject(i) ?: continue
            pathKeys.firstNotNullOfOrNull { media.optPath(it) }?.let { return it }
        }
    }
    return null
}

private fun JSONObject.optHttpsUrl(key: String): String? =
    optString(key).trim().takeIf { it.startsWith("https://") }

private fun JSONObject.optPath(key: String): String? =
    optString(key).trim().takeIf { value ->
        value.isNotBlank() && !value.startsWith("http://") && !value.startsWith("https://")
    }

private fun isoMillis(value: String?): Long =
    value?.takeIf { it.isNotBlank() }?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
        ?: System.currentTimeMillis()

private fun queryValue(value: String): String =
    URLEncoder.encode(value, StandardCharsets.UTF_8.toString())

private fun JSONObject.optMoney(key: String): Satang =
    opt(key)?.takeUnless { it == JSONObject.NULL }?.toString()?.let {
        runCatching { Satang.fromBaht(it) }.getOrNull()
    } ?: Satang.ZERO

private fun receiptLines(rows: JSONArray): List<DemoReceiptLine> =
    (0 until rows.length()).mapNotNull { index ->
        val row = rows.optJSONObject(index) ?: return@mapNotNull null
        val productId = row.optString("product_id").ifBlank {
            row.optString("id").ifBlank { "unknown-$index" }
        }
        val name = row.optString("name").ifBlank { productId }
        val quantity = row.optInt("quantity", 0)
        if (quantity <= 0) return@mapNotNull null
        DemoReceiptLine(
            productId = productId,
            name = name,
            quantity = quantity,
            unitPrice = row.optMoney("unit_price")
        )
    }

internal fun parseHistory(
    data: JSONObject,
    session: WebPosSession
): WebReceiptHistory {
    val branch = data.optJSONObject("branch")
    val store = branch?.optJSONObject("store_profile")
    val branchName = branch?.optString("name")?.takeIf { it.isNotBlank() } ?: session.branchName
    val storeName = store?.optString("display_name")?.takeIf { it.isNotBlank() } ?: session.storeName
    val address = store?.optString("company_address")?.takeIf { it.isNotBlank() }
    val phone = store?.optString("contact_phone")?.takeIf { it.isNotBlank() }
    val records = data.optJSONArray("records") ?: JSONArray()
    val parsed = (0 until records.length()).mapNotNull { index ->
        val row = records.optJSONObject(index) ?: return@mapNotNull null
        val id = row.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val orderNo = row.optString("orderNo").takeIf { it.isNotBlank() } ?: id
        val total = row.optMoney("totalAmount")
        val paid = row.optMoney("paidTotal")
        val methodsArray = row.optJSONArray("paymentMethods") ?: JSONArray()
        val methods = (0 until methodsArray.length()).map { methodsArray.optString(it) }
            .filter { it.isNotBlank() }
        val storedReceived = row.optMoney("cashReceived")
        val received = if (storedReceived > Satang.ZERO) storedReceived
            else if (methods.any { it == "cash" }) paid
            else paid
        val change = row.optMoney("changeAmount")
        val lines = receiptLines(row.optJSONArray("items") ?: JSONArray())
        val mode = when (row.optString("orderType")) {
            "dine_in" -> "นั่งโต๊ะ"
            else -> "กลับบ้าน"
        }
        val receipt = DemoSaleReceipt(
            id = id,
            billNo = orderNo,
            createdAtMs = isoMillis(row.optString("createdAt")),
            branchName = branchName,
            counterCode = session.deviceCode,
            modeLabel = mode,
            lines = lines,
            total = total,
            received = received,
            change = change,
            isDemo = false,
            storeName = storeName,
            storeAddress = address,
            storePhone = phone,
            cashierName = row.optString("cashierName").takeIf { it.isNotBlank() && it != "-" }
                ?: session.cashierName,
            shiftLabel = null
        )
        WebReceiptHistoryRecord(
            orderId = id,
            orderNo = orderNo,
            status = row.optString("status"),
            createdAtMs = receipt.createdAtMs,
            paymentMethods = methods,
            receipt = receipt
        )
    }
    val page = data.optJSONObject("pagination")
    return WebReceiptHistory(
        records = parsed,
        page = page?.optInt("page", 1) ?: 1,
        pageSize = page?.optInt("page_size", parsed.size.coerceAtLeast(1)) ?: parsed.size.coerceAtLeast(1),
        total = page?.optInt("total", parsed.size) ?: parsed.size,
        totalPages = page?.optInt("total_pages", if (parsed.isEmpty()) 0 else 1)
            ?: if (parsed.isEmpty()) 0 else 1
    )
}

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
                shift.optString("id").takeIf { it.isNotBlank() } else null,
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
            WebPosProduct(
                id = item.getString("id"),
                name = item.getString("name"),
                sku = item.optString("sku"),
                category = item.optString("category"),
                price = amount,
                imageUrl = productImageUrlFrom(item)
            )
        }
    }

    /** Retrieve the canonical image map using the SAME verified POS Web cookie session.
     * The server scopes assets to the current tenant and branch. Images are optional;
     * callers should keep the catalog usable on media service failure.
     */
    suspend fun productsWithMedia(products: List<WebPosProduct>): List<WebPosProduct> {
        if (products.isEmpty()) return products
        val data = call("GET", "/api/pos/product-media")
        val images = data.optJSONObject("images")
            ?: throw WebPosApiException("missing_product_media_map", 200)
        return mergeCanonicalProductMedia(products, images)
    }

    private suspend fun requireSameLiveSession(
        expected: WebPosSession,
        requireShift: Boolean = true
    ): WebPosSession {
        val current = getSession()
        if (current.id != expected.id ||
            current.tenantId != expected.tenantId ||
            current.branchId != expected.branchId ||
            current.deviceCode != expected.deviceCode ||
            current.employeeId != expected.employeeId ||
            !current.canSell ||
            (requireShift && (current.shiftId == null || current.shiftId != expected.shiftId))
        ) {
            throw WebPosApiException("cashier_session_or_shift_changed", 409)
        }
        return current
    }

    suspend fun currentShiftOrders(): List<WebOrderSnapshot> {
        val data = call("GET", "/api/pos/orders/current-shift")
        val rows = data.optJSONArray("orders") ?: JSONArray()
        return (0 until rows.length()).mapNotNull { index ->
            val row = rows.optJSONObject(index) ?: return@mapNotNull null
            val id = row.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            WebOrderSnapshot(
                id = id,
                orderNo = row.optString("order_no").ifBlank { id },
                status = row.optString("status"),
                shiftId = data.optJSONObject("shift")?.optString("id")?.takeIf { it.isNotBlank() },
                total = row.optMoney("grand_total").takeIf { it > Satang.ZERO }
                    ?: row.optMoney("subtotal"),
                paidTotal = row.optMoney("paid_total"),
                createdAtMs = isoMillis(row.optString("created_at"))
            )
        }
    }

    /**
     * Search canonical receipt history in the current branch. The default window
     * is the last 30 Bangkok calendar days so a cashier can recover/reprint a
     * recently completed bill without reading the database directly.
     */
    suspend fun receiptHistory(
        session: WebPosSession,
        query: String = "",
        from: LocalDate? = null,
        to: LocalDate? = null,
        status: String = "completed",
        page: Int = 1,
        pageSize: Int = 40
    ): WebReceiptHistory {
        require(session.branchId.isNotBlank())
        require(page >= 1 && pageSize in 1..100)
        val today = LocalDate.now(ZoneId.of("Asia/Bangkok"))
        val start = from ?: today.minusDays(29)
        val end = to ?: today
        require(!end.isBefore(start))
        val path = buildString {
            append("/api/pos/receipts?mode=custom")
            append("&from=").append(queryValue(start.toString()))
            append("&to=").append(queryValue(end.toString()))
            append("&status=").append(queryValue(status))
            append("&page=").append(page)
            append("&page_size=").append(pageSize)
            if (query.isNotBlank()) append("&q=").append(queryValue(query.trim()))
        }
        return parseHistory(call("GET", path), session)
    }

    /**
     * New recovery endpoint does not require the original shift to remain open.
     * Native callers should fall back to current-shift/history APIs while the
     * compatibility endpoint is rolling through Web production.
     */
    suspend fun orderRecovery(session: WebPosSession, orderId: String): WebOrderRecovery {
        require(runCatching { UUID.fromString(orderId) }.isSuccess)
        val data = call("GET", "/api/pos/orders/$orderId/status")
        val order = data.getJSONObject("order")
        val id = order.getString("id")
        if (id != orderId) throw WebPosApiException("reconcile_order_scope_mismatch", 409)
        val total = order.optMoney("total")
        val paid = order.optMoney("paid_total")
        val snapshot = WebOrderSnapshot(
            id = id,
            orderNo = order.optString("order_no").ifBlank { id },
            status = order.optString("status"),
            shiftId = order.optString("shift_id").takeIf { it.isNotBlank() },
            total = total,
            paidTotal = paid,
            createdAtMs = isoMillis(order.optString("created_at"))
        )
        val receipt = if (snapshot.status == "completed") {
            val store = data.optJSONObject("store_profile")
            val branch = data.optJSONObject("branch")
            val storedReceived = order.optMoney("cash_received")
            val received = if (storedReceived > Satang.ZERO) storedReceived else paid
            DemoSaleReceipt(
                id = id,
                billNo = snapshot.orderNo,
                createdAtMs = snapshot.createdAtMs,
                branchName = branch?.optString("name")?.takeIf { it.isNotBlank() }
                    ?: session.branchName,
                counterCode = order.optString("device_code").takeIf { it.isNotBlank() }
                    ?: session.deviceCode,
                modeLabel = "กลับบ้าน",
                lines = receiptLines(data.optJSONArray("items") ?: JSONArray()),
                total = total,
                received = received,
                change = order.optMoney("change_amount"),
                isDemo = false,
                storeName = store?.optString("display_name")?.takeIf { it.isNotBlank() }
                    ?: session.storeName,
                storeAddress = store?.optString("company_address")?.takeIf { it.isNotBlank() },
                storePhone = store?.optString("contact_phone")?.takeIf { it.isNotBlank() },
                cashierName = data.optString("cashier_name").takeIf { it.isNotBlank() && it != "-" }
                    ?: session.cashierName,
                shiftLabel = null
            )
        } else null
        return WebOrderRecovery(snapshot, receipt)
    }

    suspend fun createTakeawayOrder(
        session: WebPosSession,
        lines: List<DemoReceiptLine>,
        saleId: String
    ): WebCreatedOrder {
        require(lines.isNotEmpty() && lines.all { it.quantity in 1..9999 })
        require(lines.map { it.productId }.distinct().size == lines.size)
        require(session.canSell && session.shiftId != null)
        require(UUID.fromString(saleId).toString() == saleId)
        requireSameLiveSession(session)
        val total = lines.fold(Satang.ZERO) { sum, line -> sum + line.amount }
        require(total > Satang.ZERO)
        val items = JSONArray()
        lines.forEach { line ->
            require(runCatching { UUID.fromString(line.productId) }.isSuccess)
            items.put(
                JSONObject()
                    .put("product_id", line.productId)
                    .put("quantity", line.quantity)
            )
        }
        val order = call(
            "POST",
            "/api/pos/orders",
            JSONObject().put("items", items).put("discount_total", 0),
            key = saleId
        ).getJSONObject("order")
        val serverTotal = Satang.fromBaht(
            order.opt("grand_total")?.toString()?.takeIf { it != "null" }
                ?: order.get("total_amount").toString()
        )
        if (serverTotal != total) {
            throw WebPosApiException("server_price_changed_review_order", 409)
        }
        return WebCreatedOrder(
            id = order.getString("id"),
            orderNo = order.getString("order_no"),
            status = order.optString("status"),
            total = serverTotal
        )
    }

    suspend fun payExistingCash(
        session: WebPosSession,
        orderId: String,
        orderNo: String,
        lines: List<DemoReceiptLine>,
        received: Satang,
        saleId: String,
        expectedTotal: Satang
    ): WebPaidBill {
        require(runCatching { UUID.fromString(orderId) }.isSuccess)
        require(UUID.fromString(saleId).toString() == saleId)
        require(lines.isNotEmpty())
        require(received >= expectedTotal && expectedTotal > Satang.ZERO)
        requireSameLiveSession(session)
        val paid = call(
            "POST",
            "/api/pos/orders/$orderId/pay",
            JSONObject()
                .put("method", "cash")
                .put("amount", BigDecimal(received.bahtText())),
            key = UUID.nameUUIDFromBytes(("native-pay:$saleId").toByteArray()).toString()
        )
        val paymentOrder = paid.getJSONObject("order")
        if (paymentOrder.getString("id") != orderId ||
            paymentOrder.optString("status") != "completed"
        ) {
            throw WebPosApiException("server_payment_not_confirmed", 409)
        }
        val actual = paid.optJSONObject("receipt_preview")
        val store = actual?.optJSONObject("store_profile")
        val serverReceiptNo = paymentOrder.optString("order_no").ifBlank { orderNo }
        require(serverReceiptNo.isNotBlank())
        val snapshotTotal = Satang.fromBaht(
            actual?.opt("total")?.toString()?.takeIf { it != "null" }
                ?: expectedTotal.bahtText()
        )
        if (snapshotTotal != expectedTotal) {
            throw WebPosApiException("receipt_total_mismatch_review", 409)
        }
        val actualReceived = actual?.opt("cash_received")?.toString()
            ?.let { runCatching { Satang.fromBaht(it) }.getOrNull() }
            ?.takeIf { it > Satang.ZERO } ?: received
        val actualChange = actual?.opt("change_total")?.toString()
            ?.let { runCatching { Satang.fromBaht(it) }.getOrNull() }
            ?: (actualReceived - snapshotTotal)
        return WebPaidBill(
            orderId = orderId,
            receipt = DemoSaleReceipt(
                id = orderId,
                billNo = serverReceiptNo,
                createdAtMs = System.currentTimeMillis(),
                branchName = session.branchName,
                counterCode = session.deviceCode,
                modeLabel = "กลับบ้าน",
                lines = lines.toList(),
                total = snapshotTotal,
                received = actualReceived,
                change = actualChange,
                isDemo = false,
                storeName = store?.optString("display_name")?.takeIf { it.isNotBlank() }
                    ?: session.storeName,
                storeAddress = store?.optString("company_address")?.takeIf { it.isNotBlank() },
                storePhone = store?.optString("contact_phone")?.takeIf { it.isNotBlank() },
                cashierName = session.cashierName,
                shiftLabel = "open"
            )
        )
    }

    /**
     * Normal online checkout. Recovery code uses the two explicit operations
     * above so it can replay the same order/payment keys independently.
     */
    suspend fun payCash(
        session: WebPosSession,
        lines: List<DemoReceiptLine>,
        received: Satang,
        saleId: String,
        onOrderCreated: suspend (String, String) -> Unit
    ): WebPaidBill {
        val total = lines.fold(Satang.ZERO) { sum, line -> sum + line.amount }
        require(received >= total && total > Satang.ZERO)
        val order = createTakeawayOrder(session, lines, saleId)
        onOrderCreated(order.id, order.orderNo)
        return payExistingCash(
            session = session,
            orderId = order.id,
            orderNo = order.orderNo,
            lines = lines,
            received = received,
            saleId = saleId,
            expectedTotal = order.total
        )
    }
}
