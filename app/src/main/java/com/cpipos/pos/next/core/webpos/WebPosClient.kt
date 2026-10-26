package com.cpipos.pos.next.core.webpos

import android.content.Context
import android.os.SystemClock
import android.util.Log
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
import java.time.temporal.ChronoUnit
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
data class WebEntryAccess(
    val allowed: Boolean = true,
    val code: String? = null,
    val title: String? = null,
    val message: String? = null,
    val expiresAt: String? = null,
    val kind: String? = null
)
data class WebPaymentReview(
    val status: String,
    val submittedAt: String?,
    val reviewDueAt: String?
)
data class WebCashierDevices(
    val branchId: String,
    val branchName: String,
    val employeeName: String,
    val canOverrideInUse: Boolean,
    val devices: List<WebCashierDevice>,
    val entryAccess: WebEntryAccess = WebEntryAccess(),
    val paymentReview: WebPaymentReview? = null
)
data class WebTableZone(val id: String, val name: String, val color: String)
data class WebDiningTable(
    val id: String, val zoneId: String?, val code: String, val name: String,
    val capacity: Int, val status: String, val sessionId: String?, val orderId: String?
)
data class WebTableFloor(val zones: List<WebTableZone>, val tables: List<WebDiningTable>)
data class WebTableMoveResult(
    val sourceId: String, val targetId: String, val billSessionId: String,
    val orderId: String?, val moved: Boolean
)

data class WebQrActivityEvent(
    val id: String, val tableId: String, val tableCode: String,
    val type: String, val itemCount: Int, val subtotal: Satang?,
    val createdAt: String
)
data class WebQrActivityPage(
    val events: List<WebQrActivityEvent>, val cursor: String, val serverTime: String
)

data class WebSettingsBranch(
    val id: String,
    val code: String,
    val name: String,
    val address: String,
    val active: Boolean
)
data class WebSettingsBranchesSnapshot(
    val branches: List<WebSettingsBranch>,
    val canManage: Boolean
)
data class WebSettingsDevice(
    val id: String,
    val branchId: String,
    val deviceCode: String,
    val deviceName: String,
    val deviceType: String,
    val status: String,
    val locked: Boolean,
    val counterName: String,
    val location: String,
    val lastSeenAt: String?
)
data class WebPosUserRow(
    val userId: String,
    val branchId: String,
    val branchName: String,
    val branchCode: String,
    val role: String,
    val permissionRole: String,
    val positionTitle: String,
    val employeeCode: String,
    val fullName: String,
    val email: String,
    val active: Boolean,
    val canEdit: Boolean,
    val canDelete: Boolean,
    val protectedOwner: Boolean,
    val canApproveCancelBill: Boolean,
    val scopeMode: String,
    val deviceId: String?
)
data class WebPosUsersSnapshot(
    val items: List<WebPosUserRow>,
    val devices: List<WebSettingsDevice>,
    val canAdd: Boolean,
    val canDelete: Boolean
)
data class WebTaxLineSettings(
    val id: String,
    val label: String,
    val ratePct: Double,
    val mode: String,
    val active: Boolean
)
data class WebTaxSettings(
    val enabled: Boolean,
    val calculationBase: String,
    val lines: List<WebTaxLineSettings>
)
data class WebOrderKitchenPolicy(
    val branchId: String,
    val popupEnabled: Boolean,
    val kitchenAutoSendEnabled: Boolean,
    val kitchenAutoPrintEnabled: Boolean,
    val effectivePopupEnabled: Boolean,
    val effectiveKitchenAutoSendEnabled: Boolean,
    val effectiveKitchenAutoPrintEnabled: Boolean,
    val popupForcedByIt: Boolean,
    val sendForcedByIt: Boolean,
    val printForcedByIt: Boolean
)
data class WebTableQrPolicy(
    val tableId: String,
    val tableCode: String,
    val tableName: String,
    val mode: String,
    val ttlMinutes: Int?
)

data class WebSupportConversationHead(
    val id: String,
    val subject: String,
    val contactName: String,
    val status: String,
    val assignedUserName: String?,
    val latestMessageAt: String?,
    val latestMessagePreview: String?,
    val latestSenderType: String?,
    val unreadStoreCount: Int
)
data class WebSupportMessage(
    val id: String,
    val senderType: String,
    val senderName: String,
    val senderRole: String?,
    val body: String,
    val createdAt: String
)
data class WebSupportConversationSnapshot(
    val id: String,
    val subject: String,
    val status: String,
    val assignedUserName: String?,
    val messages: List<WebSupportMessage>
)

data class WebPosNotificationSettings(
    val popupEnabled: Boolean = true,
    val soundEnabled: Boolean = true,
    val soundVolume: Float = 0.8f
)

data class WebStoreSettings(
    val id: String,
    val code: String,
    val displayName: String,
    val logoUrl: String,
    val companyAddress: String,
    val contactPhone: String
)

internal fun parseStoreSettings(data: JSONObject): WebStoreSettings {
    val row = data.optJSONObject("store") ?: data
    return WebStoreSettings(
        id = row.optString("id"),
        code = row.optString("code"),
        displayName = row.optString("display_name").ifBlank { row.optString("name") },
        logoUrl = row.optString("logo_url"),
        companyAddress = row.optString("company_address"),
        contactPhone = row.optString("contact_phone")
    )
}

internal fun parsePosNotificationSettings(data: JSONObject): WebPosNotificationSettings {
    val row = data.optJSONObject("notification_settings") ?: data
    return WebPosNotificationSettings(
        popupEnabled = row.optBoolean("table_qr_popup_enabled", true),
        soundEnabled = row.optBoolean("table_qr_sound_enabled", true),
        soundVolume = row.optDouble("table_qr_sound_volume", 0.8)
            .toFloat().coerceIn(0f, 1f)
    )
}

internal fun parseQrActivityPage(data: JSONObject, fallbackCursor: String): WebQrActivityPage {
    val array = data.optJSONArray("events") ?: JSONArray()
    val events = (0 until array.length()).mapNotNull { index ->
        val row = array.optJSONObject(index) ?: return@mapNotNull null
        val eventType = row.optString("event_type")
        if (eventType !in setOf("order", "call_staff", "request_checkout")) return@mapNotNull null
        val id = row.optString("id")
        val tableId = row.optString("table_id")
        if (runCatching { UUID.fromString(id) }.isFailure ||
            runCatching { UUID.fromString(tableId) }.isFailure) return@mapNotNull null
        val table = row.optJSONObject("table")
        val total = row.opt("subtotal")?.takeUnless { it == JSONObject.NULL }
            ?.toString()?.let { runCatching { Satang.fromBaht(it) }.getOrNull() }
        WebQrActivityEvent(
            id = id, tableId = tableId, tableCode = table?.optString("table_code") ?: "—",
            type = eventType, itemCount = row.optInt("item_count", 0).coerceAtLeast(0),
            subtotal = total, createdAt = row.optString("created_at")
        )
    }
    val cursor = data.optString("cursor").takeUnless { it.isBlank() } ?: fallbackCursor
    val time = data.optString("server_time").takeUnless { it.isBlank() } ?: fallbackCursor
    return WebQrActivityPage(events, cursor, time)
}

data class WebTableOrderQr(
    val tableId: String,
    val tableCode: String,
    val tableName: String,
    val tableSessionId: String,
    val qrSessionId: String,
    val orderUrl: String,
    val qrPngBase64: String,
    val expiresAt: String,
    val expiryMode: String
)

/** Reject incorrect table/session tokens and untrusted QR redirects before display or printing. */
internal fun parseTableOrderQr(data: JSONObject, expectedTableId: String, trustedOrigin: String): WebTableOrderQr {
    require(data.optString("table_id") == expectedTableId) { "qr_table_scope_mismatch" }
    val source = URL(trustedOrigin)
    val issuedUrl = data.optString("order_url")
    val target = URL(issuedUrl)
    require(target.protocol == "https" && target.host.equals(source.host, ignoreCase = true) &&
        target.port == source.port && target.path.startsWith("/table-order/")) {
        "qr_order_url_untrusted"
    }
    val image = data.optString("qr_data_url")
    require(image.startsWith("data:image/png;base64,") && image.length <= 1_500_000) {
        "qr_image_invalid"
    }
    val sessionId = data.optString("qr_session_id")
    val billId = data.optString("table_session_id")
    require(runCatching { UUID.fromString(sessionId) }.isSuccess &&
        runCatching { UUID.fromString(billId) }.isSuccess) {
        "qr_server_session_invalid"
    }
    val expiresAt = data.optString("expires_at")
    require(runCatching { java.time.Instant.parse(expiresAt) }.isSuccess) {
        "qr_expiry_invalid"
    }
    return WebTableOrderQr(
        tableId = expectedTableId,
        tableCode = data.optString("table_code"),
        tableName = data.optString("table_name"),
        tableSessionId = billId,
        qrSessionId = sessionId,
        orderUrl = issuedUrl,
        qrPngBase64 = image.removePrefix("data:image/png;base64,"),
        expiresAt = expiresAt,
        expiryMode = data.optString("expiry_mode")
    )
}

data class WebTableBillSummary(val sessionId: String?, val orderNo: String?, val status: String?, val itemCount: Int)
data class WebDiningLine(
    val productId: String, val name: String, val quantity: Int,
    val unitPrice: Satang, val notes: String?, val hasCustomMetadata: Boolean
)
data class WebDiningBill(
    val sessionId: String?, val sessionStatus: String?,
    val orderId: String?, val orderNo: String?, val orderStatus: String?,
    val lines: List<WebDiningLine>, val paid: Boolean, val total: Satang,
    val discount: Satang = Satang.ZERO
) {
    val editable: Boolean get() =
        sessionId != null && sessionStatus in setOf("open", "ordering") &&
            (orderId == null || orderStatus == "queued") && !paid
    fun fingerprint(): String = listOf(sessionId, sessionStatus, orderId, orderStatus,
        paid, total.value, discount.value,
        lines.joinToString("|") { "${it.productId}:${it.quantity}:${it.unitPrice.value}:${it.notes.orEmpty()}:${it.hasCustomMetadata}" }
    ).joinToString("#")
}


data class WebPaymentAccount(
    val id: String,
    val bankName: String,
    val accountName: String,
    val accountNumber: String,
    val promptPayPhone: String,
    val qrImageUrl: String,
    val qrMode: String
)
data class WebPaymentSettingsBranch(
    val id: String,
    val name: String,
    val code: String = "",
    val active: Boolean = true
)
data class WebPaymentSettingsAccount(
    val id: String = "",
    val branchId: String,
    val bankName: String,
    val accountName: String,
    val accountNumber: String,
    val promptPayPhone: String,
    val qrImageUrl: String,
    val qrMode: String,
    val appliesToAllBranches: Boolean,
    val active: Boolean
)
data class WebPaymentSettingsSnapshot(
    val accounts: List<WebPaymentSettingsAccount>,
    val branches: List<WebPaymentSettingsBranch>,
    val canManage: Boolean,
    val ready: Boolean
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
    val shiftId: String?, val canOpenShift: Boolean, val canSell: Boolean,
    val canManageTables: Boolean = false,
    val canCloseShift: Boolean = false,
    val role: String = "staff"
)

data class WebSubscriptionPackage(
    val id: String,
    val code: String,
    val name: String,
    val monthlyPrice: Double?,
    val yearlyPrice: Double?,
    val contactSales: Boolean,
    val maxBranches: Int?,
    val maxDevices: Int?,
    val maxUsers: Int?
)
data class WebSubscriptionRequest(
    val id: String,
    val status: String,
    val packageId: String?,
    val packageName: String,
    val billingInterval: String,
    val expectedAmount: Double?,
    val amount: Double?,
    val kind: String,
    val hasEvidence: Boolean,
    val submittedAt: String
)
data class WebSubscriptionReceipt(
    val id: String,
    val number: String,
    val issuedAt: String,
    val amount: Double,
    val currency: String,
    val packageName: String,
    val billingInterval: String
)
data class WebSubscriptionDue(
    val status: String,
    val payableNow: Boolean,
    val selfServicePaymentAllowed: Boolean,
    val supportRequired: Boolean,
    val dueAt: String?,
    val amountDue: Double,
    val amountPaid: Double,
    val outstanding: Double,
    val currency: String,
    val billingInterval: String,
    val packageName: String?,
    val openRequestStatus: String?,
    val openRequestId: String?,
    val accessLocked: Boolean
)
data class WebSubscriptionIssuer(
    val name: String,
    val bankName: String,
    val accountName: String,
    val accountNumber: String,
    val promptPayId: String
)
data class WebSubscriptionOverview(
    val storeCode: String,
    val storeName: String,
    val packageId: String?,
    val packageCode: String?,
    val packageName: String,
    val contractStatus: String,
    val billingInterval: String,
    val expiresAt: String?,
    val daysRemaining: Int?,
    val amountPerCycle: Double?,
    val currency: String,
    val internalDemo: Boolean,
    val packages: List<WebSubscriptionPackage>,
    val requests: List<WebSubscriptionRequest>,
    val due: WebSubscriptionDue?,
    val issuer: WebSubscriptionIssuer,
    val receipts: List<WebSubscriptionReceipt>,
    val itConnected: Boolean
)
data class WebSubscriptionSlip(
    val fileName: String,
    val mimeType: String,
    val bytes: ByteArray
)
data class WebSubscriptionSubmitResult(
    val id: String,
    val status: String,
    val alreadySubmitted: Boolean,
    val provisionalAccessGranted: Boolean
)
data class WebSubscriptionRuntime(
    val locked: Boolean,
    val lockReason: String?,
    val lifecycleStatus: String,
    val expiresAt: String?,
    val daysRemaining: Int?,
    val packageName: String,
    val billingInterval: String,
    val amountDue: Double?,
    val currency: String
)
data class WebPreEntrySubscriptionQuote(
    val token: String?,
    val storeCode: String,
    val storeName: String,
    val packageName: String,
    val amount: Double,
    val currency: String,
    val billingInterval: String,
    val bankName: String,
    val accountName: String,
    val accountNumber: String,
    val promptPayId: String,
    val pendingStatus: String?,
    val pendingHasEvidence: Boolean,
    val pendingCanAttachEvidence: Boolean
)
data class WebPreEntrySubscriptionSubmitResult(
    val id: String,
    val status: String,
    val provisionalAccessGranted: Boolean,
    val provisionalAccessExpiresAt: String?
)

data class WebPosSalesModes(
    val takeaway: Boolean,
    val dineIn: Boolean,
    val buffetTable: Boolean,
    val delivery: Boolean,
    val generalSale: Boolean,
    val opticalRetail: Boolean
) {
    fun enabled(mode: String): Boolean = when (mode) {
        "takeaway", "home" -> takeaway
        "dine_in" -> dineIn
        "buffet_table" -> buffetTable
        "delivery" -> delivery
        "general_sale" -> generalSale
        "optical_retail" -> opticalRetail
        else -> false
    }
}

/**
 * Effective package/menu entitlement snapshot resolved by CpIPOS Server from
 * the same CpiPOS-001 control-plane tables managed by CpIPOS-IT.
 *
 * Android never receives a service-role key and never chooses a Supabase
 * project. UI locks are advisory UX; every protected API remains authoritative.
 */
data class WebPosEntitlements(
    val tenantId: String,
    val branchId: String,
    val features: Map<String, Boolean>,
    val menuPolicy: Map<String, Boolean>,
    val isCustom: Boolean,
    val isInternalTestFull: Boolean,
    val salesModes: WebPosSalesModes,
    val opticalMoreAccess: Map<String, Boolean>
) {
    fun featureEnabled(code: String): Boolean = features[code] == true

    fun menuEnabled(key: String, parentKey: String? = null): Boolean {
        // CpIPOS deliberately keeps shift operationally reachable regardless of
        // package navigation switches.
        if (key == "main.shift") return true
        if (isCustom) {
            if (menuPolicy[key] != true) return false
            if (parentKey != null && menuPolicy[parentKey] != true) return false
            return true
        }
        // Fixed packages use menu switches as independent navigation locks.
        return menuPolicy[key] != false
    }

    fun allows(
        featureCode: String? = null,
        menuKey: String? = null,
        parentMenuKey: String? = null,
        opticalRoute: String? = null
    ): Boolean {
        if (menuKey != null && !menuEnabled(menuKey, parentMenuKey)) return false
        if (featureCode == null) return true
        if (featureEnabled(featureCode)) return true
        return opticalRoute != null && opticalMoreAccess[opticalRoute] == true
    }

    fun takeawayAllowed(): Boolean =
        allows(featureCode = "core_pos_sales", menuKey = "main.sales") &&
            salesModes.takeaway

    fun dineInAllowed(): Boolean =
        allows(featureCode = "core_pos_sales", menuKey = "main.sales") &&
            featureEnabled("table_management") &&
            salesModes.dineIn
}

data class WebShiftBranchOption(
    val id: String,
    val code: String?,
    val name: String?
)
data class WebShiftMetrics(
    val orderCount: Int,
    val cancelledOrderCount: Int,
    val salesTotal: Satang,
    val cashTotal: Satang,
    val transferTotal: Satang
)
data class WebShiftHistoryRow(
    val id: String,
    val branchId: String,
    val branchCode: String?,
    val branchName: String?,
    val openedByName: String,
    val closedByName: String?,
    val openedAtMs: Long,
    val closedAtMs: Long?,
    val status: String,
    val openingCash: Satang,
    val expectedCash: Satang?,
    val actualCash: Satang?,
    val metrics: WebShiftMetrics
)
data class WebShiftHistoryFilters(
    val selfOnly: Boolean,
    val canViewBranchWide: Boolean,
    val selectedBranchId: String,
    val branchOptions: List<WebShiftBranchOption>
)
data class WebShiftHistorySummary(
    val shiftCount: Int,
    val orderCount: Int,
    val cancelledOrderCount: Int,
    val salesTotal: Satang,
    val cashTotal: Satang,
    val transferTotal: Satang
)
data class WebShiftHistorySnapshot(
    val filters: WebShiftHistoryFilters,
    val summary: WebShiftHistorySummary,
    val shifts: List<WebShiftHistoryRow>
)
data class WebCatalogBranch(
    val id: String,
    val name: String,
    val code: String = "",
    val role: String = ""
)
data class WebCatalogRow(
    val id: String, val name: String, val sku: String = "",
    val category: String = "", val price: Satang = Satang.ZERO,
    val stock: Double? = null, val sold30d: Int? = null,
    val baseUnit: String = "", val reorderLevel: Double = 0.0,
    val avgUnitCost: Double = 0.0, val lastPurchaseUnitCost: Double = 0.0,
    val mode: String = "unit_only", val active: Boolean = true,
    val recommended: Boolean = false,
    val updatedAt: String = ""
)
data class WebCatalogPage(
    val items: List<WebCatalogRow>, val page: Int, val totalPages: Int,
    val total: Int, val editable: Boolean, val branchId: String,
    val branchName: String, val categories: List<String> = emptyList(),
    val branches: List<WebCatalogBranch> = emptyList()
)
data class WebCatalogRecipeDraft(
    val ingredientId: String,
    val quantity: Double,
    val unit: String
)
data class WebSalesTrendPoint(
    val key: String,
    val label: String,
    val netSales: Satang,
    val receiptCount: Int
)
data class WebSalesSummarySnapshot(
    val currencyCode: String,
    val grossSales: Satang,
    val netSales: Satang,
    val receiptCount: Int,
    val cashTotal: Satang,
    val transferTotal: Satang,
    val cardTotal: Satang = Satang.ZERO,
    val discountTotal: Satang,
    val taxTotal: Satang,
    val cancelledTotal: Satang,
    val averageReceipt: Satang,
    val bestSelling: List<WebBestSellingProduct>,
    val trend: List<WebSalesTrendPoint> = emptyList()
)
data class WebBestSellingProduct(
    val productId: String,
    val productName: String,
    val category: String,
    val quantitySold: Double,
    val grossAmount: Satang,
    val netAmount: Satang
)
data class WebMemberRow(
    val id: String,
    val name: String,
    val phone: String,
    val email: String,
    val points: Int,
    val stamps: Int,
    val updatedAt: String
)
data class WebTaxReceiptRow(
    val id: String,
    val orderNo: String,
    val customerName: String,
    val total: Satang,
    val taxTotal: Satang,
    val createdAt: String,
    val invoiceNo: String?,
    val invoiceProfileId: String?,
    val issuedAt: String?
)
data class WebTaxProfileRow(
    val id: String,
    val displayName: String,
    val taxId: String,
    val entityType: String,
    val addressLine: String,
    val subdistrict: String,
    val district: String,
    val province: String,
    val postalCode: String,
    val invoiceCount: Int,
    val lastIssuedAt: String?
)
data class WebTaxAddressOption(
    val postalCode: String,
    val subdistrict: String,
    val district: String,
    val province: String
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
data class WebCashDrawerStatus(
    val configured: Boolean,
    val ready: Boolean,
    val reason: String,
    val selectedMode: String?,
    val printerName: String?
) {
    val visible: Boolean get() = configured && ready
}
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
            shiftLabel = null,
            paymentMethod = if (methods.contains("bank_transfer")) "bank_transfer" else "cash",
            discount = row.optMoney("discountAmount"),
            subtotal = row.optMoney("subtotal").takeIf { it > Satang.ZERO }
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
class WebPosClient(baseUrl: String, context: Context? = null) {
    private val base = baseUrl.trimEnd('/').also {
        require(it.startsWith("https://") && !it.contains('@') && !it.contains('?')) {
            "CpIPOS Web endpoint must be HTTPS"
        }
    }
    private val cookieManager = CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER)
    private val secureSessionStore = context?.let(::SecurePosSessionCookieStore)

    init {
        secureSessionStore?.restore()?.let { restored ->
            cookieManager.put(URL(base).toURI(), mapOf("Set-Cookie" to listOf(restored)))
        }
    }

    fun hasStoredPosSession(): Boolean = secureSessionStore?.restore() != null
    fun clearStoredPosSession() {
        secureSessionStore?.clear()
        cookieManager.cookieStore.removeAll()
    }

    /**
     * Revoke only THIS authenticated cashier-device session on CpIPOS Web.
     * Always discard local SSO state, including when the server is unreachable.
     * Returns false if remote revocation could not be confirmed.
     */
    suspend fun logoutCurrentPosSession(): Boolean {
        val confirmed = try {
            call("POST", "/api/auth/session/logout", JSONObject().put("mode", "full"))
            true
        } catch (_: Exception) {
            false
        } finally {
            clearStoredPosSession()
        }
        return confirmed
    }

    /**
     * Use the same server-owned POS logout/switch flow as tablet POS.
     * This ends the old device session and creates a short-lived, scoped
     * pre-entry cookie for /api/auth/branches -> employee -> device.
     * It does not use a new authorization mechanism or copy a PIN.
     */
    suspend fun switchToBranchChooser(): WebStore {
        try {
            val switched = call(
                "POST", "/api/auth/session/logout",
                JSONObject().put("mode", "switch_branch")
            )
            if (switched.optString("mode") != "switch_branch") {
                throw WebPosApiException("switch_branch_not_confirmed", 409)
            }
            // Clear ONLY the persisted POS SSO secret. The server-provided
            // pre-entry cookie must stay in CookieManager for branch selection.
            secureSessionStore?.clear()
            val listing = call("GET", "/api/auth/branches")
            val tenant = listing.getJSONObject("tenant")
            val branches = listing.optJSONArray("branches") ?: JSONArray()
            return WebStore(
                tenantId = "",
                name = tenant.optString("name"),
                branches = (0 until branches.length()).map { index ->
                    val item = branches.getJSONObject(index)
                    WebBranch(
                        id = item.getString("id"),
                        name = item.optString("name"),
                        code = item.optString("code")
                    )
                }
            )
        } catch (failure: Exception) {
            // If response was lost after server revocation, fail closed and
            // start normal store verification, never silently reuse old SSO.
            clearStoredPosSession()
            throw failure
        }
    }

    private suspend fun call(
        method: String, path: String, json: JSONObject? = null, key: String? = null,
        readTimeoutMs: Int = 15000,
        connectTimeoutMs: Int = 8000,
        headers: Map<String, String> = emptyMap()
    ): JSONObject = withContext(Dispatchers.IO) {
        require(path.startsWith("/") && !path.startsWith("//"))
        val url = URL(base + path)
        val conn = url.openConnection() as HttpURLConnection
        val startedAt = SystemClock.elapsedRealtime()
        var responseStatus = -1
        try {
            conn.instanceFollowRedirects = false
            conn.connectTimeout = connectTimeoutMs.coerceIn(1000, 30000)
            conn.readTimeout = readTimeoutMs.coerceIn(1000, 60000)
            conn.requestMethod = method
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("User-Agent", "CpIPOS-Native-Android/2")
            headers.forEach { (name, value) ->
                require(name.matches(Regex("[A-Za-z0-9-]{1,64}")))
                conn.setRequestProperty(name, value.take(4096))
            }
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
            responseStatus = status
            val cookies = conn.headerFields.filterKeys { it?.equals("Set-Cookie", true) == true }
            if (cookies.isNotEmpty()) {
                cookieManager.put(url.toURI(), cookies)
                // Persistence is best-effort: a locked/unavailable Keystore
                // must never turn an otherwise successful real payment ambiguous.
                runCatching { secureSessionStore?.save(cookies.values.flatten()) }
            }
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
            val elapsedMs = SystemClock.elapsedRealtime() - startedAt
            if (elapsedMs >= 2_500L) {
                // Route-only diagnostic: never log query values, request bodies,
                // cookies, PINs, customer data or payment payloads.
                Log.w(
                    "CpIPOSHttp",
                    "Slow POS API " + method + " " + path.substringBefore("?") +
                        " " + elapsedMs + "ms status=" + responseStatus
                )
            }
            conn.disconnect()
        }
    }

    private suspend fun callMultipart(
        path: String,
        fields: Map<String, String>,
        slip: WebSubscriptionSlip? = null,
        readTimeoutMs: Int = 45_000,
        connectTimeoutMs: Int = 8_000
    ): JSONObject = withContext(Dispatchers.IO) {
        require(path.startsWith("/") && !path.startsWith("//"))
        if (slip != null) {
            require(slip.bytes.isNotEmpty() && slip.bytes.size <= 4 * 1024 * 1024)
            require(slip.mimeType in setOf("image/jpeg", "image/png", "image/webp"))
        }
        val url = URL(base + path)
        val conn = url.openConnection() as HttpURLConnection
        val boundary = "CpIPOSNative" + UUID.randomUUID().toString().replace("-", "")
        val startedAt = SystemClock.elapsedRealtime()
        var responseStatus = -1
        try {
            conn.instanceFollowRedirects = false
            conn.connectTimeout = connectTimeoutMs.coerceIn(1000, 30000)
            conn.readTimeout = readTimeoutMs.coerceIn(1000, 60000)
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setChunkedStreamingMode(8192)
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("User-Agent", "CpIPOS-Native-Android/2")
            conn.setRequestProperty("X-CpIPOS-Client", "native-android")
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary)
            cookieManager.get(url.toURI(), emptyMap()).forEach { (header, values) ->
                if (header.equals("Cookie", ignoreCase = true)) {
                    conn.setRequestProperty("Cookie", values.joinToString("; "))
                }
            }
            conn.outputStream.buffered().use { output ->
                fun write(value: String) = output.write(value.toByteArray(Charsets.UTF_8))
                fields.forEach { (name, value) ->
                    require(name.matches(Regex("[A-Za-z0-9_]{1,64}")))
                    write("--" + boundary + "\r\n")
                    write("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n")
                    write(value)
                    write("\r\n")
                }
                if (slip != null) {
                    val safeName = slip.fileName.replace(Regex("[^A-Za-z0-9._-]"), "_").take(120)
                    write("--" + boundary + "\r\n")
                    write("Content-Disposition: form-data; name=\"slip\"; filename=\"" + safeName + "\"\r\n")
                    write("Content-Type: " + slip.mimeType + "\r\n\r\n")
                    output.write(slip.bytes)
                    write("\r\n")
                }
                write("--" + boundary + "--\r\n")
                output.flush()
            }
            val status = conn.responseCode
            responseStatus = status
            val cookies = conn.headerFields.filterKeys { it?.equals("Set-Cookie", true) == true }
            if (cookies.isNotEmpty()) {
                cookieManager.put(url.toURI(), cookies)
                runCatching { secureSessionStore?.save(cookies.values.flatten()) }
            }
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
            val elapsedMs = SystemClock.elapsedRealtime() - startedAt
            if (elapsedMs >= 2_500L) {
                Log.w(
                    "CpIPOSHttp",
                    "Slow POS API POST " + path.substringBefore("?") +
                        " " + elapsedMs + "ms status=" + responseStatus
                )
            }
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
        val entry = data.optJSONObject("entry_access") ?: JSONObject().put("allowed", true)
        val denial = entry.optJSONObject("error")
        val review = data.optJSONObject("payment_review")
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
            },
            entryAccess = WebEntryAccess(
                allowed = entry.optBoolean("allowed", true),
                code = denial?.optString("code")?.takeIf { it.isNotBlank() },
                title = denial?.optString("title")?.takeIf { it.isNotBlank() },
                message = denial?.optString("message")?.takeIf { it.isNotBlank() },
                expiresAt = denial?.optString("expires_at")?.takeIf { it.isNotBlank() && it != "null" },
                kind = denial?.optString("kind")?.takeIf { it.isNotBlank() }
            ),
            paymentReview = review?.let {
                WebPaymentReview(
                    status = it.optString("status"),
                    submittedAt = it.optString("submitted_at").takeIf { value -> value.isNotBlank() && value != "null" },
                    reviewDueAt = it.optString("review_due_at").takeIf { value -> value.isNotBlank() && value != "null" }
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

    suspend fun posEntitlements(
        session: WebPosSession,
        forceFresh: Boolean = false
    ): WebPosEntitlements {
        val path = if (forceFresh) "/api/pos/features?refresh=1" else "/api/pos/features"
        // Entitlement checks are UX/control-plane reads, never payment writes.
        // Keep a short bounded timeout so a slow network cannot make the POS
        // appear frozen while IT settings are being refreshed.
        val data = call(
            method = "GET",
            path = path,
            readTimeoutMs = 5000,
            connectTimeoutMs = 3000
        )
        val tenantId = data.optString("tenant_id")
        val branchId = data.optString("branch_id")
        if (tenantId != session.tenantId || branchId != session.branchId) {
            throw WebPosApiException("feature_scope_mismatch", 409)
        }

        fun booleanMap(source: JSONObject?): Map<String, Boolean> {
            if (source == null) return emptyMap()
            val result = linkedMapOf<String, Boolean>()
            val keys = source.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                if (key.isNotBlank()) result[key] = source.optBoolean(key, false)
            }
            return result
        }

        val isCustom = data.optBoolean("is_custom", false)
        val sales = data.optJSONObject("sales_modes")
        fun mode(key: String, fixedDefault: Boolean): Boolean =
            sales?.optBoolean(key, if (isCustom) false else fixedDefault)
                ?: if (isCustom) false else fixedDefault

        return WebPosEntitlements(
            tenantId = tenantId,
            branchId = branchId,
            features = booleanMap(data.optJSONObject("features")),
            menuPolicy = booleanMap(data.optJSONObject("menu_policy")),
            isCustom = isCustom,
            isInternalTestFull = data.optBoolean("is_internal_test_full", false),
            salesModes = WebPosSalesModes(
                takeaway = mode("takeaway", true),
                dineIn = mode("dine_in", true),
                buffetTable = mode("buffet_table", true),
                delivery = mode("delivery", true),
                generalSale = mode("general_sale", true),
                opticalRetail = mode("optical_retail", false)
            ),
            opticalMoreAccess = booleanMap(data.optJSONObject("optical_more_access"))
        )
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
            canSell = "sale:create" in values && "sales:enter" in values,
            canManageTables = "tables:manage" in values,
            canCloseShift = "shift:close" in values,
            role = d.optString("role").ifBlank { "staff" }
        )
    }

    /**
     * An older Android SSO session can remain bound to a shift that was
     * closed in tablet POS. /session/current then correctly reports closed,
     * but does not automatically rebind to a *new* shift. Join the same
     * registered device's current shift via the existing audited server API.
     * Never create a shift while merely refreshing status.
     */
    suspend fun synchronizedShiftSession(): WebPosSession {
        val current = getSession()
        if (current.shiftId != null) return current
        val snapshot = call("GET", "/api/pos/shifts/current")
        val candidate = snapshot.optJSONObject("current_shift")
            ?.optString("id")?.takeIf { it.isNotBlank() } ?: return current
        require(UUID.fromString(candidate).toString() == candidate)
        call("POST", "/api/pos/shifts/join", JSONObject().put("shift_id", candidate))
        val rebound = getSession()
        if (rebound.id != current.id || rebound.branchId != current.branchId ||
            rebound.deviceCode != current.deviceCode || rebound.shiftId != candidate) {
            throw WebPosApiException("shift_rebind_not_confirmed", 409)
        }
        return rebound
    }

    /**
     * Opens or reuses the POS Web shift bound to the same branch/device code.
     * Opening cash is cashier-supplied baht, never the old hardcoded zero.
     * Backend owns shift truth; an existing shift retains its original float.
     */
    suspend fun openShift(openingCash: Satang): WebPosSession {
        require(openingCash >= Satang.ZERO)
        require(openingCash.value <= 999999999L)
        call(
            "POST", "/api/pos/shifts/open",
            JSONObject().put("opening_cash", BigDecimal(openingCash.bahtText()))
        )
        val current = getSession()
        if (current.shiftId == null) throw WebPosApiException("shift_open_not_confirmed", 409)
        return current
    }

    /**
     * Reads the canonical CpIPOS shift table. The server owns role scoping:
     * staff are forced to their own shifts/current branch, while Owner/Manager
     * can use the returned branch options when reports:view is granted.
     */
    suspend fun shiftHistory(
        session: WebPosSession,
        days: Int = 30,
        branchId: String = "all",
        startDate: LocalDate? = null,
        endDate: LocalDate? = null
    ): WebShiftHistorySnapshot {
        require(days in 1..90)
        require(startDate == null || endDate == null || !endDate.isBefore(startDate))
        val current = getSession()
        if (current.id != session.id ||
            current.tenantId != session.tenantId ||
            current.branchId != session.branchId ||
            current.deviceCode != session.deviceCode ||
            current.employeeId != session.employeeId
        ) {
            throw WebPosApiException("cashier_session_scope_changed", 409)
        }
        val path = buildString {
            append("/api/pos/shifts/history?days=").append(days)
            append("&view=all&branch_id=").append(queryValue(branchId.ifBlank { "all" }))
            if (startDate != null) append("&start_date=").append(queryValue(startDate.toString()))
            if (endDate != null) append("&end_date=").append(queryValue(endDate.toString()))
        }
        val data = call("GET", path)
        val filtersJson = data.optJSONObject("filters") ?: JSONObject()
        val summaryJson = data.optJSONObject("summary") ?: JSONObject()
        val shiftRows = data.optJSONArray("shifts") ?: JSONArray()
        val branchRows = filtersJson.optJSONArray("branch_options") ?: JSONArray()

        fun money(source: JSONObject, key: String): Satang =
            Satang.fromBaht(source.opt(key)?.toString()?.takeIf { it != "null" } ?: "0")
        fun nullableMoney(source: JSONObject, key: String): Satang? =
            if (!source.has(key) || source.isNull(key)) null else
                runCatching { Satang.fromBaht(source.get(key).toString()) }.getOrNull()
        fun millis(value: String): Long =
            runCatching { Instant.parse(value).toEpochMilli() }.getOrDefault(0L)

        return WebShiftHistorySnapshot(
            filters = WebShiftHistoryFilters(
                selfOnly = filtersJson.optBoolean("self_only", true),
                canViewBranchWide = filtersJson.optBoolean("can_view_branch_wide", false),
                selectedBranchId = filtersJson.optString("selected_branch_id").ifBlank { current.branchId },
                branchOptions = (0 until branchRows.length()).mapNotNull { index ->
                    val row = branchRows.optJSONObject(index) ?: return@mapNotNull null
                    val id = row.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    WebShiftBranchOption(
                        id = id,
                        code = row.optString("code").takeIf { it.isNotBlank() && it != "null" },
                        name = row.optString("name").takeIf { it.isNotBlank() && it != "null" }
                    )
                }
            ),
            summary = WebShiftHistorySummary(
                shiftCount = summaryJson.optInt("shift_count", 0),
                orderCount = summaryJson.optInt("order_count", 0),
                cancelledOrderCount = summaryJson.optInt("cancelled_order_count", 0),
                salesTotal = money(summaryJson, "sales_total"),
                cashTotal = money(summaryJson, "cash_total"),
                transferTotal = money(summaryJson, "transfer_total")
            ),
            shifts = (0 until shiftRows.length()).mapNotNull { index ->
                val row = shiftRows.optJSONObject(index) ?: return@mapNotNull null
                val id = row.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val metrics = row.optJSONObject("metrics") ?: JSONObject()
                WebShiftHistoryRow(
                    id = id,
                    branchId = row.optString("branch_id"),
                    branchCode = row.optString("branch_code").takeIf { it.isNotBlank() && it != "null" },
                    branchName = row.optString("branch_name").takeIf { it.isNotBlank() && it != "null" },
                    openedByName = row.optString("opened_by_name").ifBlank { "-" },
                    closedByName = row.optString("closed_by_name").takeIf { it.isNotBlank() && it != "null" },
                    openedAtMs = millis(row.optString("opened_at")),
                    closedAtMs = row.optString("closed_at")
                        .takeIf { it.isNotBlank() && it != "null" }?.let(::millis),
                    status = row.optString("status"),
                    openingCash = money(row, "opening_cash"),
                    expectedCash = nullableMoney(row, "expected_cash"),
                    actualCash = nullableMoney(row, "actual_cash"),
                    metrics = WebShiftMetrics(
                        orderCount = metrics.optInt("order_count", 0),
                        cancelledOrderCount = metrics.optInt("cancelled_order_count", 0),
                        salesTotal = money(metrics, "sales_total"),
                        cashTotal = money(metrics, "cash_total"),
                        transferTotal = money(metrics, "transfer_total")
                    )
                )
            }
        )
    }

    /**
     * Close only the active shift bound to this authenticated POS session.
     * CpIPOS server re-checks shift:close, own-shift rules, open bills and all
     * reconciliation constraints before clearing pos_sessions.shift_id.
     */
    suspend fun closeShift(session: WebPosSession, closingCash: Satang): WebPosSession {
        require(session.shiftId != null)
        require(closingCash >= Satang.ZERO)
        require(closingCash.value <= 999999999L)
        call(
            "POST", "/api/pos/shifts/close",
            JSONObject().put("closing_cash", BigDecimal(closingCash.bahtText()))
        )
        val current = getSession()
        if (current.id != session.id ||
            current.tenantId != session.tenantId ||
            current.branchId != session.branchId ||
            current.deviceCode != session.deviceCode ||
            current.employeeId != session.employeeId
        ) {
            throw WebPosApiException("cashier_session_scope_changed", 409)
        }
        if (current.shiftId != null) {
            throw WebPosApiException("shift_close_not_confirmed", 409)
        }
        return current
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


    suspend fun salesSummary(
        session: WebPosSession,
        dateFrom: LocalDate = LocalDate.now(ZoneId.of("Asia/Bangkok")).minusDays(29),
        dateTo: LocalDate = LocalDate.now(ZoneId.of("Asia/Bangkok"))
    ): WebSalesSummarySnapshot {
        requireSameLiveSession(session, requireShift = false, requireSell = false)
        require(!dateTo.isBefore(dateFrom))

        val totalDays = ChronoUnit.DAYS.between(dateFrom, dateTo) + 1
        val trendGrouping = when {
            totalDays <= 1 -> "hour"
            totalDays <= 62 -> "day"
            else -> "month"
        }
        val bangkok = ZoneId.of("Asia/Bangkok")

        suspend fun loadChunk(from: LocalDate, to: LocalDate): WebSalesSummarySnapshot {
            val data = call(
                "GET",
                "/api/pos/sales-summary?dateFrom=" + queryValue(from.toString()) +
                    "&dateTo=" + queryValue(to.toString()) +
                    "&branchId=" + queryValue(session.branchId) +
                    "&paymentMethod=all&status=all"
            )
            val summary = data.optJSONObject("summary") ?: JSONObject()
            val bestRows = data.optJSONArray("bestSellingProducts") ?: JSONArray()
            val salesRows = data.optJSONArray("salesRows") ?: JSONArray()
            fun money(key: String): Satang = Satang.fromBaht(summary.opt(key)?.toString() ?: "0")

            val trendBuckets = linkedMapOf<String, WebSalesTrendPoint>()
            for (index in 0 until salesRows.length()) {
                val row = salesRows.optJSONObject(index) ?: continue
                if (row.optString("status") != "completed") continue
                val created = runCatching {
                    Instant.parse(row.optString("createdAt")).atZone(bangkok)
                }.getOrNull() ?: continue
                val key: String
                val label: String
                when (trendGrouping) {
                    "hour" -> {
                        val hour = created.hour.toString().padStart(2, '0')
                        key = created.toLocalDate().toString() + "T" + hour
                        label = "$hour:00"
                    }
                    "month" -> {
                        val month = created.monthValue.toString().padStart(2, '0')
                        key = created.year.toString() + "-" + month
                        label = month + "/" + created.year.toString().takeLast(2)
                    }
                    else -> {
                        val day = created.dayOfMonth.toString().padStart(2, '0')
                        val month = created.monthValue.toString().padStart(2, '0')
                        key = created.toLocalDate().toString()
                        label = "$day/$month"
                    }
                }
                val amount = runCatching {
                    Satang.fromBaht(row.opt("netTotal")?.toString() ?: "0")
                }.getOrDefault(Satang.ZERO)
                val current = trendBuckets[key]
                trendBuckets[key] = WebSalesTrendPoint(
                    key = key,
                    label = label,
                    netSales = if (current == null) amount else current.netSales + amount,
                    receiptCount = (current?.receiptCount ?: 0) + 1
                )
            }

            return WebSalesSummarySnapshot(
                currencyCode = data.optString("currencyCode").ifBlank { "THB" },
                grossSales = money("grossSales"),
                netSales = money("netSales"),
                receiptCount = summary.optInt("receiptCount", 0),
                cashTotal = money("cashTotal"),
                transferTotal = money("qrTransferTotal"),
                cardTotal = money("cardTotal"),
                discountTotal = money("discountTotal"),
                taxTotal = money("taxTotal"),
                cancelledTotal = money("cancelledTotal"),
                averageReceipt = money("averageReceiptValue"),
                bestSelling = (0 until bestRows.length()).mapNotNull { index ->
                    val row = bestRows.optJSONObject(index) ?: return@mapNotNull null
                    WebBestSellingProduct(
                        productId = row.optString("productId"),
                        productName = row.optString("productName").ifBlank { "-" },
                        category = row.optString("category").ifBlank { "-" },
                        quantitySold = row.optDouble("quantitySold", 0.0),
                        grossAmount = Satang.fromBaht(row.opt("grossAmount")?.toString() ?: "0"),
                        netAmount = Satang.fromBaht(row.opt("netAmount")?.toString() ?: "0")
                    )
                },
                trend = trendBuckets.values.sortedBy { it.key }
            )
        }

        if (totalDays <= 120) return loadChunk(dateFrom, dateTo)

        val chunks = mutableListOf<WebSalesSummarySnapshot>()
        var cursor = dateFrom
        while (!cursor.isAfter(dateTo)) {
            val chunkEnd = minOf(cursor.plusDays(89), dateTo)
            chunks += loadChunk(cursor, chunkEnd)
            cursor = chunkEnd.plusDays(1)
        }

        val productBuckets = linkedMapOf<String, WebBestSellingProduct>()
        val trendBuckets = linkedMapOf<String, WebSalesTrendPoint>()
        for (snapshot in chunks) {
            for (row in snapshot.bestSelling) {
                val current = productBuckets[row.productId]
                productBuckets[row.productId] = if (current == null) row else current.copy(
                    quantitySold = current.quantitySold + row.quantitySold,
                    grossAmount = current.grossAmount + row.grossAmount,
                    netAmount = current.netAmount + row.netAmount
                )
            }
            for (point in snapshot.trend) {
                val current = trendBuckets[point.key]
                trendBuckets[point.key] = if (current == null) point else current.copy(
                    netSales = current.netSales + point.netSales,
                    receiptCount = current.receiptCount + point.receiptCount
                )
            }
        }
        val receiptCount = chunks.sumOf { it.receiptCount }
        val netSales = chunks.fold(Satang.ZERO) { sum, row -> sum + row.netSales }
        return WebSalesSummarySnapshot(
            currencyCode = chunks.firstOrNull()?.currencyCode ?: "THB",
            grossSales = chunks.fold(Satang.ZERO) { sum, row -> sum + row.grossSales },
            netSales = netSales,
            receiptCount = receiptCount,
            cashTotal = chunks.fold(Satang.ZERO) { sum, row -> sum + row.cashTotal },
            transferTotal = chunks.fold(Satang.ZERO) { sum, row -> sum + row.transferTotal },
            cardTotal = chunks.fold(Satang.ZERO) { sum, row -> sum + row.cardTotal },
            discountTotal = chunks.fold(Satang.ZERO) { sum, row -> sum + row.discountTotal },
            taxTotal = chunks.fold(Satang.ZERO) { sum, row -> sum + row.taxTotal },
            cancelledTotal = chunks.fold(Satang.ZERO) { sum, row -> sum + row.cancelledTotal },
            averageReceipt = if (receiptCount > 0) Satang.of(netSales.value / receiptCount) else Satang.ZERO,
            bestSelling = productBuckets.values
                .sortedByDescending { it.quantitySold }
                .take(20),
            trend = trendBuckets.values.sortedBy { it.key }
        )
    }

    suspend fun members(session: WebPosSession, query: String = ""): List<WebMemberRow> {
        requireSameLiveSession(session, requireShift = false, requireSell = false)
        require(query.length <= 80)
        val data = call(
            "GET",
            "/api/pos/members?q=" + queryValue(query.trim())
        )
        val rows = data.optJSONArray("members") ?: JSONArray()
        return (0 until rows.length()).mapNotNull { index ->
            val row = rows.optJSONObject(index) ?: return@mapNotNull null
            val id = row.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            WebMemberRow(
                id = id,
                name = row.optString("name").ifBlank { "-" },
                phone = row.optString("phone"),
                email = row.optString("email"),
                points = row.optInt("points_balance", row.optInt("points", 0)),
                stamps = row.optInt("stamp_balance", row.optInt("stamps", 0)),
                updatedAt = row.optString("updated_at")
            )
        }
    }

    suspend fun taxReceipts(session: WebPosSession, query: String = ""): List<WebTaxReceiptRow> {
        requireSameLiveSession(session, requireShift = false, requireSell = false)
        require(query.length <= 80)
        val data = call(
            "GET",
            "/api/pos/tax-invoices?mode=receipts&q=" + queryValue(query.trim())
        )
        val rows = data.optJSONArray("records") ?: JSONArray()
        return (0 until rows.length()).mapNotNull { index ->
            val row = rows.optJSONObject(index) ?: return@mapNotNull null
            val id = row.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val invoice = row.optJSONObject("invoice")
            WebTaxReceiptRow(
                id = id,
                orderNo = row.optString("order_no").ifBlank { id },
                customerName = row.optString("customer_name"),
                total = Satang.fromBaht(row.opt("total")?.toString() ?: "0"),
                taxTotal = Satang.fromBaht(row.opt("tax_total")?.toString() ?: "0"),
                createdAt = row.optString("created_at"),
                invoiceNo = invoice?.optString("invoice_no")?.takeIf { it.isNotBlank() },
                invoiceProfileId = invoice?.optString("profile_id")?.takeIf { it.isNotBlank() },
                issuedAt = invoice?.optString("issued_at")?.takeIf { it.isNotBlank() }
            )
        }
    }

    suspend fun reprintReceipt(
        session: WebPosSession,
        orderId: String,
        managerPin: String
    ): Int {
        requireSameLiveSession(session, requireShift = false, requireSell = false)
        require(runCatching { UUID.fromString(orderId) }.isSuccess)
        require(managerPin.length in 4..12 && managerPin.all(Char::isDigit))
        val data = call(
            "POST",
            "/api/pos/receipts/" + orderId + "/reprint",
            JSONObject()
                .put("manager_pin", managerPin)
                .put("note", "พิมพ์ย้อนหลังจาก POS มือถือ")
        )
        return data.optJSONArray("jobs")?.length()
            ?: data.optInt("print_jobs_queued", 0)
    }

    suspend fun saveMember(
        session: WebPosSession,
        name: String,
        phone: String,
        email: String = "",
        points: Int = 0,
        stamps: Int = 0
    ): WebMemberRow {
        requireSameLiveSession(session, requireShift = false, requireSell = false)
        val digits = phone.filter(Char::isDigit)
        require(name.trim().isNotBlank() && digits.length in 9..10)
        require(points >= 0 && stamps >= 0)
        val data = call(
            "POST",
            "/api/pos/members",
            JSONObject()
                .put("name", name.trim())
                .put("phone", digits)
                .put("email", email.trim())
                .put("points", points)
                .put("stamps", stamps)
        ).getJSONObject("member")
        return WebMemberRow(
            id = data.getString("id"),
            name = data.optString("name").ifBlank { name.trim() },
            phone = data.optString("phone").ifBlank { digits },
            email = data.optString("email"),
            points = data.optInt("points_balance", data.optInt("points", points)),
            stamps = data.optInt("stamp_balance", data.optInt("stamps", stamps)),
            updatedAt = data.optString("updated_at")
        )
    }

    suspend fun deleteMember(session: WebPosSession, memberId: String) {
        requireSameLiveSession(session, requireShift = false, requireSell = false)
        require(runCatching { UUID.fromString(memberId) }.isSuccess)
        val data = call("DELETE", "/api/pos/members?id=" + queryValue(memberId))
        if (!data.optBoolean("deleted", false)) {
            throw WebPosApiException("member_delete_unconfirmed", 503)
        }
    }

    suspend fun taxProfiles(session: WebPosSession, query: String = ""): List<WebTaxProfileRow> {
        requireSameLiveSession(session, requireShift = false, requireSell = false)
        require(query.length <= 80)
        val data = call(
            "GET",
            "/api/pos/tax-invoices?q=" + queryValue(query.trim())
        )
        val rows = data.optJSONArray("profiles") ?: JSONArray()
        return (0 until rows.length()).mapNotNull { index ->
            val row = rows.optJSONObject(index) ?: return@mapNotNull null
            val id = row.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            WebTaxProfileRow(
                id = id,
                displayName = row.optString("display_name").ifBlank { "-" },
                taxId = row.optString("tax_id"),
                entityType = row.optString("entity_type"),
                addressLine = row.optString("address_line"),
                subdistrict = row.optString("subdistrict"),
                district = row.optString("district"),
                province = row.optString("province"),
                postalCode = row.optString("postal_code"),
                invoiceCount = row.optInt("invoice_count", 0),
                lastIssuedAt = row.optString("last_issued_at").takeIf { it.isNotBlank() }
            )
        }
    }

    suspend fun createDiningTables(
        session: WebPosSession,
        zoneName: String,
        tableName: String,
        capacity: Int,
        count: Int = 1
    ): Int {
        requireSameLiveSession(session, requireShift = false, requireSell = false)
        require(session.canManageTables)
        require(capacity in 1..100 && count in 1..50)
        val data = call(
            "POST",
            "/api/pos/native-table-management",
            JSONObject()
                .put("action", if (count == 1) "create" else "bulk_create")
                .put("zone_name", zoneName.trim())
                .put("table_name", tableName.trim())
                .put("capacity", capacity)
                .put("count", count)
        )
        val created = data.optInt("created_count", 0)
        if (created != count) throw WebPosApiException("native_table_create_unconfirmed", 503)
        return created
    }

    suspend fun taxAddressOptions(
        session: WebPosSession,
        postalCode: String
    ): List<WebTaxAddressOption> {
        requireSameLiveSession(session, requireShift = false, requireSell = false)
        val digits = postalCode.filter(Char::isDigit)
        require(digits.length == 5)
        val data = call(
            "GET",
            "/api/pos/tax-invoices?mode=address&postal_code=" + queryValue(digits)
        )
        val rows = data.optJSONArray("options") ?: JSONArray()
        return (0 until rows.length()).mapNotNull { index ->
            val row = rows.optJSONObject(index) ?: return@mapNotNull null
            WebTaxAddressOption(
                postalCode = row.optString("postal_code").ifBlank { digits },
                subdistrict = row.optString("subdistrict"),
                district = row.optString("district"),
                province = row.optString("province")
            )
        }
    }

    suspend fun saveTaxProfile(
        session: WebPosSession,
        entityType: String,
        displayName: String,
        taxId: String,
        addressLine: String,
        postalCode: String,
        subdistrict: String,
        district: String,
        province: String
    ): WebTaxProfileRow {
        requireSameLiveSession(session, requireShift = false, requireSell = false)
        require(entityType in setOf("company", "limited_partnership", "shop", "individual"))
        val digits = taxId.filter(Char::isDigit)
        require(digits.length == 13 && postalCode.filter(Char::isDigit).length == 5)
        val data = call(
            "POST",
            "/api/pos/tax-invoices",
            JSONObject()
                .put("action", "save_profile")
                .put("entity_type", entityType)
                .put("display_name", displayName.trim())
                .put("tax_id", digits)
                .put("address_line", addressLine.trim())
                .put("postal_code", postalCode.filter(Char::isDigit))
                .put("subdistrict", subdistrict.trim())
                .put("district", district.trim())
                .put("province", province.trim())
        ).getJSONObject("profile")
        return WebTaxProfileRow(
            id = data.getString("id"),
            displayName = data.optString("display_name"),
            taxId = data.optString("tax_id"),
            entityType = data.optString("entity_type"),
            addressLine = data.optString("address_line"),
            subdistrict = data.optString("subdistrict"),
            district = data.optString("district"),
            province = data.optString("province"),
            postalCode = data.optString("postal_code"),
            invoiceCount = 0,
            lastIssuedAt = null
        )
    }

    suspend fun deleteTaxProfile(
        session: WebPosSession,
        profileId: String,
        managerPin: String
    ) {
        requireSameLiveSession(session, requireShift = false, requireSell = false)
        require(runCatching { UUID.fromString(profileId) }.isSuccess)
        require(managerPin.length in 4..12 && managerPin.all(Char::isDigit))
        val data = call(
            "POST",
            "/api/pos/tax-invoices",
            JSONObject()
                .put("action", "delete_profile")
                .put("profile_id", profileId)
                .put("manager_pin", managerPin)
        )
        if (!data.optBoolean("deleted", false)) {
            throw WebPosApiException("tax_profile_delete_unconfirmed", 503)
        }
    }

    suspend fun printTaxInvoice(
        session: WebPosSession,
        profileId: String,
        orderId: String,
        managerPin: String
    ): String {
        requireSameLiveSession(session, requireShift = false, requireSell = false)
        require(runCatching { UUID.fromString(profileId) }.isSuccess)
        require(runCatching { UUID.fromString(orderId) }.isSuccess)
        require(managerPin.length in 4..12 && managerPin.all(Char::isDigit))
        val data = call(
            "POST",
            "/api/pos/tax-invoices",
            JSONObject()
                .put("action", "print_invoice")
                .put("profile_id", profileId)
                .put("order_id", orderId)
                .put("manager_pin", managerPin)
        )
        val invoiceNo = data.optString("invoice_no")
        if (invoiceNo.isBlank()) throw WebPosApiException("tax_invoice_print_unconfirmed", 503)
        return invoiceNo
    }

    /** POS-cookie catalog bridge backed by the same CpIPOS products/ingredients tables. */
    suspend fun catalogBranches(session: WebPosSession): List<WebCatalogBranch> {
        requireSameLiveSession(session, requireShift = false, requireSell = false)
        val result = call("GET", "/api/pos/catalog-management?view=branches")
        val rows = result.optJSONArray("branches") ?: JSONArray()
        return (0 until rows.length()).mapNotNull { index ->
            val row = rows.optJSONObject(index) ?: return@mapNotNull null
            val id = row.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            WebCatalogBranch(
                id = id,
                name = row.optString("name").ifBlank { id },
                code = row.optString("code"),
                role = row.optString("role")
            )
        }
    }

    suspend fun catalogManagement(
        session: WebPosSession,
        branchId: String,
        ingredients: Boolean,
        page: Int = 1,
        pageSize: Int = 20,
        search: String = "",
        category: String = ""
    ): WebCatalogPage {
        requireSameLiveSession(session, requireShift = false, requireSell = false)
        require(page >= 1 && pageSize in 1..50 && search.length <= 60)
        require(runCatching { UUID.fromString(branchId) }.isSuccess)
        val view = if (ingredients) "ingredients" else "products"
        val path = "/api/pos/catalog-management?view=$view&page=$page&page_size=$pageSize" +
            "&branch_id=" + queryValue(branchId) +
            "&search=" + queryValue(search.trim()) +
            "&category=" + queryValue(if (ingredients) "" else category)
        val result = call("GET", path)
        check(result.optString("branch_id") == branchId) { "catalog_branch_scope_mismatch" }

        fun parseBranches(): List<WebCatalogBranch> {
            val rows = result.optJSONArray("branches") ?: JSONArray()
            return (0 until rows.length()).mapNotNull { index ->
                val row = rows.optJSONObject(index) ?: return@mapNotNull null
                val id = row.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                WebCatalogBranch(
                    id = id,
                    name = row.optString("name").ifBlank { id },
                    code = row.optString("code"),
                    role = row.optString("role")
                )
            }
        }

        val rows = result.optJSONArray("items") ?: JSONArray()
        val items = (0 until rows.length()).map { index ->
            val item = rows.getJSONObject(index)
            val isIngredient = ingredients
            WebCatalogRow(
                id = item.getString("id"),
                name = item.getString("name"),
                sku = item.optString("sku"),
                category = item.optString("category"),
                price = if (isIngredient) Satang.ZERO
                    else Satang.fromBaht(item.get("price").toString()),
                stock = (if (isIngredient) item.opt("quantity_on_hand")
                    else item.opt("stock_remaining"))?.takeUnless { it == JSONObject.NULL }
                    ?.toString()?.toDoubleOrNull(),
                sold30d = item.opt("sold_30d")?.takeUnless { it == JSONObject.NULL }
                    ?.toString()?.toIntOrNull(),
                baseUnit = item.optString("base_unit"),
                reorderLevel = item.optDouble("reorder_level", 0.0),
                avgUnitCost = item.optDouble("avg_unit_cost", 0.0),
                lastPurchaseUnitCost = item.optDouble("last_purchase_unit_cost", 0.0),
                mode = item.optString("stock_deduction_mode", "unit_only"),
                active = item.optBoolean("is_active", true),
                recommended = item.optBoolean("is_recommended", false),
                updatedAt = item.optString("updated_at")
            )
        }
        val categoriesArray = result.optJSONArray("categories") ?: JSONArray()
        val categories = (0 until categoriesArray.length())
            .mapNotNull { categoriesArray.optString(it).takeIf(String::isNotBlank) }
        val pagination = result.optJSONObject("pagination")
        return WebCatalogPage(
            items = items,
            page = pagination?.optInt("page", page) ?: page,
            totalPages = pagination?.optInt("total_pages", 1) ?: 1,
            total = pagination?.optInt("total", items.size) ?: items.size,
            editable = result.optBoolean("can_edit", false),
            branchId = result.optString("branch_id").ifBlank { branchId },
            branchName = result.optString("branch_name").ifBlank {
                if (branchId == session.branchId) session.branchName else branchId
            },
            categories = categories,
            branches = parseBranches()
        )
    }

    suspend fun catalogIngredientsForRecipe(
        session: WebPosSession,
        branchId: String
    ): List<WebCatalogRow> {
        val first = catalogManagement(session, branchId, ingredients = true, page = 1, pageSize = 50)
        if (first.totalPages <= 1) return first.items
        val all = first.items.toMutableList()
        for (page in 2..minOf(first.totalPages, 20)) {
            all += catalogManagement(session, branchId, ingredients = true, page = page, pageSize = 50).items
        }
        return all.distinctBy { it.id }
    }

    suspend fun createCatalogProduct(
        session: WebPosSession,
        branchId: String,
        name: String,
        category: String,
        stockQuantity: Double,
        storePriceText: String,
        deliveryPriceText: String,
        recommended: Boolean,
        useIngredientRecipe: Boolean,
        ingredientLines: List<WebCatalogRecipeDraft>
    ) {
        requireSameLiveSession(session, requireShift = false, requireSell = false)
        require(runCatching { UUID.fromString(branchId) }.isSuccess)
        require(name.isNotBlank() && category.isNotBlank())
        require(stockQuantity >= 0.0)
        val storePrice = Satang.fromBaht(storePriceText)
        val deliveryPrice = Satang.fromBaht(deliveryPriceText)
        require(storePrice >= Satang.ZERO && deliveryPrice >= Satang.ZERO)
        if (useIngredientRecipe) require(ingredientLines.isNotEmpty())

        val recipe = JSONArray()
        ingredientLines.forEach { line ->
            require(runCatching { UUID.fromString(line.ingredientId) }.isSuccess)
            require(line.quantity > 0.0)
            recipe.put(
                JSONObject()
                    .put("ingredient_id", line.ingredientId)
                    .put("quantity", line.quantity)
                    .put("quantity_unit", line.unit)
            )
        }
        val payload = JSONObject()
            .put("action", "create_product_with_stock_setup")
            .put("branch_id", branchId)
            .put("name", name.trim())
            .put("category", category.trim())
            .put("stock_quantity", stockQuantity)
            .put("store_price", BigDecimal(storePrice.bahtText()))
            .put("delivery_price", BigDecimal(deliveryPrice.bahtText()))
            .put("is_recommended", recommended)
            .put("use_ingredient_recipe", useIngredientRecipe)
            .put("ingredient_lines", recipe)
        call("POST", "/api/pos/catalog-management", payload)
    }

    suspend fun restockCatalogIngredient(
        session: WebPosSession,
        branchId: String,
        ingredientId: String?,
        ingredientName: String,
        purchaseQuantity: Double,
        purchaseUnit: String,
        weightPerBagGrams: Double?,
        receivedTotalCostText: String,
        reason: String
    ) {
        requireSameLiveSession(session, requireShift = false, requireSell = false)
        require(runCatching { UUID.fromString(branchId) }.isSuccess)
        require(ingredientName.isNotBlank() && purchaseQuantity > 0.0)
        val totalCost = receivedTotalCostText.trim().ifBlank { "0" }.toBigDecimal()
        require(totalCost >= BigDecimal.ZERO)
        val payload = JSONObject()
            .put("action", "add_ingredient_stock")
            .put("branch_id", branchId)
            .put("ingredient_name", ingredientName.trim())
            .put("purchase_quantity", purchaseQuantity)
            .put("purchase_unit", purchaseUnit)
            .put("received_total_cost", totalCost)
            .put("reason", reason.trim().ifBlank { "เติมสต๊อกจากการรับเข้า" })
        ingredientId?.takeIf { it.isNotBlank() }?.let { payload.put("ingredient_id", it) }
        weightPerBagGrams?.takeIf { it > 0.0 }?.let { payload.put("weight_per_bag_in_grams", it) }
        call("POST", "/api/pos/catalog-management", payload)
    }

    suspend fun saveCatalogRow(
        session: WebPosSession,
        branchId: String,
        ingredient: Boolean,
        item: WebCatalogRow,
        name: String,
        skuOrUnit: String,
        category: String,
        priceText: String,
        recommended: Boolean = item.recommended
    ) {
        requireSameLiveSession(session, requireShift = false, requireSell = false)
        require(runCatching { UUID.fromString(branchId) }.isSuccess)
        require(name.isNotBlank() && skuOrUnit.isNotBlank())
        val payload = JSONObject()
            .put("action", if (ingredient) "save_ingredient" else "save_product")
            .put("branch_id", branchId)
            .put("id", item.id)
            .put("updated_at", item.updatedAt)
            .put("name", name.trim())
        if (ingredient) {
            payload.put("base_unit", skuOrUnit.trim())
        } else {
            require(category.isNotBlank())
            val price = Satang.fromBaht(priceText)
            require(price >= Satang.ZERO)
            payload.put("sku", skuOrUnit.trim())
                .put("category", category.trim())
                .put("price", BigDecimal(price.bahtText()))
                .put("is_recommended", recommended)
        }
        call("POST", "/api/pos/catalog-management", payload)
    }

    suspend fun approveCatalogProductDeactivate(
        session: WebPosSession,
        branchId: String,
        productId: String,
        pin: String
    ): String {
        requireSameLiveSession(session, requireShift = false, requireSell = false)
        require(runCatching { UUID.fromString(branchId) }.isSuccess)
        require(UUID.fromString(productId).toString() == productId)
        require(pin.length in 4..12 && pin.all(Char::isDigit))
        val data = call("POST", "/api/backoffice/approvals/pin", JSONObject()
            .put("tenant_id", session.tenantId)
            .put("branch_id", branchId)
            .put("action", "product_deactivate")
            .put("target_table", "products")
            .put("target_id", productId)
            .put("manager_pin", pin))
        val approvalId = data.optString("approval_id")
        require(runCatching { UUID.fromString(approvalId).toString() == approvalId }.getOrDefault(false)) {
            "catalog_deactivation_approval_missing"
        }
        return approvalId
    }

    suspend fun deactivateCatalogProduct(
        session: WebPosSession,
        branchId: String,
        item: WebCatalogRow,
        approvalId: String
    ) {
        requireSameLiveSession(session, requireShift = false, requireSell = false)
        require(item.active)
        require(UUID.fromString(approvalId).toString() == approvalId)
        val response = call("POST", "/api/pos/catalog-management", JSONObject()
            .put("action", "deactivate_product")
            .put("branch_id", branchId)
            .put("id", item.id)
            .put("approval_id", approvalId))
        check(response.optBoolean("deactivated", false)) {
            "catalog_deactivation_unconfirmed"
        }
    }

    /** Printer Settings V3 uses the same POS-session auth as CpIPOS Web. */
    suspend fun printerRegistry(session: WebPosSession): JSONObject {
        requireSameLiveSession(session, requireShift = false, requireSell = false)
        return call("GET", "/api/backoffice/printers/devices")
    }

    suspend fun createPrinter(session: WebPosSession, payload: JSONObject): JSONObject {
        requireSameLiveSession(session, requireShift = false, requireSell = false)
        return call("POST", "/api/backoffice/printers/devices", payload)
    }

    suspend fun updatePrinter(session: WebPosSession, payload: JSONObject): JSONObject {
        requireSameLiveSession(session, requireShift = false, requireSell = false)
        return call("PATCH", "/api/backoffice/printers/devices", payload)
    }

    suspend fun testPrinter(session: WebPosSession, printerId: String): JSONObject {
        requireSameLiveSession(session, requireShift = false, requireSell = false)
        require(UUID.fromString(printerId).toString() == printerId)
        return call("POST", "/api/backoffice/printers/test",
            JSONObject().put("printer_id", printerId))
    }

    suspend fun bootstrapNativePrintAgent(
        session: WebPosSession, installationId: String
    ): JSONObject {
        requireSameLiveSession(session, requireShift = false, requireSell = false)
        require(UUID.fromString(installationId).toString() == installationId)
        return call(
            method = "POST",
            path = "/api/android-pos/print-agent/bootstrap",
            json = JSONObject().put("runtime", "android_native_print_agent_v2"),
            headers = mapOf(
                "X-CpIPOS-Android-POS" to "true",
                "X-CpIPOS-Install-Id" to installationId,
                "X-CpIPOS-App-Version" to BuildConfig.VERSION_NAME
            )
        )
    }

    suspend fun claimNativePrintJobs(agentKey: String): JSONArray {
        require(agentKey.startsWith("cpi_pa_") && agentKey.length in 40..160)
        val data = call(
            method = "POST",
            path = "/api/print-agent/v1/jobs/claim",
            json = JSONObject().put("limit", 3).put("lease_seconds", 60)
                .put("app_version", BuildConfig.VERSION_NAME),
            readTimeoutMs = 15000,
            headers = mapOf("X-Print-Agent-Key" to agentKey)
        )
        return data.optJSONArray("jobs") ?: JSONArray()
    }

    suspend fun heartbeatNativePrintAgent(agentKey: String) {
        require(agentKey.startsWith("cpi_pa_"))
        call(
            method = "POST",
            path = "/api/print-agent/v1/heartbeat",
            json = JSONObject().put("app_version", BuildConfig.VERSION_NAME)
                .put("metadata", JSONObject()
                    .put("runtime", "android_native_print_agent_v2")
                    .put("transports", JSONArray(listOf("lan", "usb", "bluetooth")))),
            headers = mapOf("X-Print-Agent-Key" to agentKey)
        )
    }

    suspend fun ackNativePrintJob(
        agentKey: String, jobId: String, attemptId: String,
        providerJobId: String, bytesSent: Int, transport: String
    ) {
        require(runCatching { UUID.fromString(jobId) }.isSuccess)
        call(
            method = "POST",
            path = "/api/print-agent/v1/jobs/$jobId/ack",
            json = JSONObject()
                .put("agent_attempt_id", attemptId)
                .put("provider_job_id", providerJobId)
                .put("bytes_sent", bytesSent)
                .put("metadata", JSONObject()
                    .put("runtime", "android_native_print_agent_v2")
                    .put("transport", transport)
                    .put("app_version", BuildConfig.VERSION_NAME)),
            headers = mapOf("X-Print-Agent-Key" to agentKey)
        )
    }

    suspend fun failNativePrintJob(
        agentKey: String, jobId: String, attemptId: String,
        errorCode: String, message: String, retryable: Boolean
    ) {
        require(runCatching { UUID.fromString(jobId) }.isSuccess)
        call(
            method = "POST",
            path = "/api/print-agent/v1/jobs/$jobId/fail",
            json = JSONObject()
                .put("agent_attempt_id", attemptId)
                .put("error_code", errorCode.take(120))
                .put("error_message", message.take(500))
                .put("retryable", retryable)
                .put("metadata", JSONObject()
                    .put("runtime", "android_native_print_agent_v2")
                    .put("app_version", BuildConfig.VERSION_NAME)),
            headers = mapOf("X-Print-Agent-Key" to agentKey)
        )
    }

    suspend fun settingsBranchesSnapshot(
        session: WebPosSession
    ): WebSettingsBranchesSnapshot {
        requireSameLiveSession(session, requireShift = false, requireSell = false)
        val data = call("GET", "/api/pos/settings/branches")
        val rows = data.optJSONArray("branches") ?: JSONArray()
        val metadata = data.optJSONObject("metadata")
        return WebSettingsBranchesSnapshot(
            branches = (0 until rows.length()).mapNotNull { index ->
                val row = rows.optJSONObject(index) ?: return@mapNotNull null
                val id = row.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                WebSettingsBranch(
                    id = id,
                    code = row.optString("code"),
                    name = row.optString("name").ifBlank { row.optString("code") },
                    address = row.optString("address"),
                    active = row.optBoolean("is_active", true)
                )
            },
            canManage = metadata?.optBoolean("can_manage", false) ?: false
        )
    }

    suspend fun settingsBranches(session: WebPosSession): List<WebSettingsBranch> =
        settingsBranchesSnapshot(session).branches

    suspend fun saveSettingsBranch(
        session: WebPosSession,
        branch: WebSettingsBranch
    ): WebSettingsBranch {
        requireSameLiveSession(session, requireShift = false, requireSell = false)
        require(branch.code.trim().isNotBlank() && branch.name.trim().isNotBlank())
        val payload = JSONObject()
            .put("code", branch.code.trim().uppercase())
            .put("name", branch.name.trim())
            .put("address", branch.address.trim())
            .put("is_active", branch.active)
        if (branch.id.isNotBlank()) payload.put("id", branch.id)
        val row = call(
            if (branch.id.isBlank()) "POST" else "PATCH",
            "/api/pos/settings/branches",
            payload
        ).getJSONObject("branch")
        return WebSettingsBranch(
            id = row.getString("id"),
            code = row.optString("code"),
            name = row.optString("name"),
            address = row.optString("address"),
            active = row.optBoolean("is_active", true)
        )
    }

    suspend fun deactivateSettingsBranch(session: WebPosSession, branchId: String) {
        requireSameLiveSession(session, requireShift = false, requireSell = false)
        require(UUID.fromString(branchId).toString() == branchId)
        call("DELETE", "/api/pos/settings/branches?branch_id=" + queryValue(branchId))
    }

    suspend fun settingsDevices(session: WebPosSession): List<WebSettingsDevice> {
        requireSameLiveSession(session, requireShift = false, requireSell = false)
        val rows = call("GET", "/api/pos/settings/devices").optJSONArray("devices") ?: JSONArray()
        return (0 until rows.length()).mapNotNull { index ->
            val row = rows.optJSONObject(index) ?: return@mapNotNull null
            val id = row.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            WebSettingsDevice(
                id = id,
                branchId = row.optString("branch_id"),
                deviceCode = row.optString("device_code"),
                deviceName = row.optString("device_name"),
                deviceType = row.optString("device_type").ifBlank { "pos_terminal" },
                status = row.optString("status").ifBlank { "active" },
                locked = row.optBoolean("is_locked", true),
                counterName = row.optString("counter_name"),
                location = row.optString("location"),
                lastSeenAt = row.optString("last_seen_at").takeIf { it.isNotBlank() && it != "null" }
            )
        }
    }

    suspend fun saveSettingsDevice(
        session: WebPosSession,
        device: WebSettingsDevice
    ): WebSettingsDevice {
        requireSameLiveSession(session, requireShift = false, requireSell = false)
        require(device.branchId.isNotBlank())
        require(device.deviceCode.trim().isNotBlank() && device.deviceName.trim().isNotBlank())
        val payload = JSONObject()
            .put("branch_id", device.branchId)
            .put("device_code", device.deviceCode.trim())
            .put("device_name", device.deviceName.trim())
            .put("device_type", device.deviceType)
            .put("status", device.status)
            .put("is_locked", device.locked)
            .put("counter_name", device.counterName.trim())
            .put("location", device.location.trim())
        if (device.id.isNotBlank()) payload.put("id", device.id)
        val row = call(
            if (device.id.isBlank()) "POST" else "PATCH",
            "/api/pos/settings/devices",
            payload
        ).getJSONObject("device")
        return WebSettingsDevice(
            id = row.getString("id"),
            branchId = row.optString("branch_id"),
            deviceCode = row.optString("device_code"),
            deviceName = row.optString("device_name"),
            deviceType = row.optString("device_type").ifBlank { "pos_terminal" },
            status = row.optString("status").ifBlank { "active" },
            locked = row.optBoolean("is_locked", true),
            counterName = row.optString("counter_name"),
            location = row.optString("location"),
            lastSeenAt = row.optString("last_seen_at").takeIf { it.isNotBlank() && it != "null" }
        )
    }

    suspend fun deleteSettingsDevice(session: WebPosSession, deviceId: String) {
        requireSameLiveSession(session, requireShift = false, requireSell = false)
        require(UUID.fromString(deviceId).toString() == deviceId)
        call("DELETE", "/api/pos/settings/devices?device_id=" + queryValue(deviceId))
    }

    suspend fun posUsers(session: WebPosSession): WebPosUsersSnapshot {
        requireSameLiveSession(session, requireShift = false, requireSell = false)
        val data = call("GET", "/api/pos/users?branch_id=" + queryValue(session.branchId))
        val items = data.optJSONArray("items") ?: JSONArray()
        val devices = data.optJSONArray("devices") ?: JSONArray()
        val metadata = data.optJSONObject("metadata")
        return WebPosUsersSnapshot(
            items = (0 until items.length()).mapNotNull { index ->
                val row = items.optJSONObject(index) ?: return@mapNotNull null
                val id = row.optString("user_id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val scope = row.optJSONObject("device_scope")
                WebPosUserRow(
                    userId = id,
                    branchId = row.optString("branch_id"),
                    branchName = row.optString("branch_name"),
                    branchCode = row.optString("branch_code"),
                    role = row.optString("role").ifBlank { "staff" },
                    permissionRole = row.optString("permission_role"),
                    positionTitle = row.optString("position_title"),
                    employeeCode = row.optString("employee_code"),
                    fullName = row.optString("full_name"),
                    email = row.optString("email"),
                    active = row.optBoolean("is_active", false),
                    canEdit = row.optBoolean("can_edit", false),
                    canDelete = row.optBoolean("can_delete", false),
                    protectedOwner = row.optBoolean("is_protected_primary_owner", false),
                    canApproveCancelBill = row.optBoolean("can_approve_cancel_bill", false),
                    scopeMode = scope?.optString("scope_mode") ?: "all_devices",
                    deviceId = scope?.optString("device_id")?.takeIf { it.isNotBlank() && it != "null" }
                )
            },
            devices = (0 until devices.length()).mapNotNull { index ->
                val row = devices.optJSONObject(index) ?: return@mapNotNull null
                val id = row.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                WebSettingsDevice(
                    id = id,
                    branchId = row.optString("branch_id"),
                    deviceCode = row.optString("device_code"),
                    deviceName = row.optString("device_name"),
                    deviceType = "pos_terminal",
                    status = row.optString("status").ifBlank { "active" },
                    locked = true,
                    counterName = "",
                    location = "",
                    lastSeenAt = null
                )
            },
            canAdd = metadata?.optBoolean("can_add", false) ?: false,
            canDelete = metadata?.optBoolean("can_delete", false) ?: false
        )
    }

    suspend fun createPosUser(
        session: WebPosSession,
        fullName: String,
        email: String,
        role: String,
        employeeCode: String,
        positionTitle: String,
        permissionRole: String,
        pin: String,
        scopeMode: String,
        deviceId: String?,
        canApproveCancelBill: Boolean,
        approvalPin: String
    ) {
        requireSameLiveSession(session, requireShift = false, requireSell = false)
        require(role in setOf("manager", "staff", "accountant", "kitchen"))
        require(employeeCode.isNotBlank() && employeeCode.all(Char::isDigit))
        require(scopeMode in setOf("all_devices", "single_device"))
        val payload = JSONObject()
            .put("full_name", fullName.trim())
            .put("email", email.trim())
            .put("role", role)
            .put("branch_id", session.branchId)
            .put("employee_code", employeeCode)
            .put("position_title", positionTitle.trim())
            .put("permission_role", permissionRole.trim().ifBlank { role })
            .put("pin", pin.trim())
            .put("scope_mode", scopeMode)
            .put("device_id", deviceId ?: JSONObject.NULL)
            .put("can_approve_cancel_bill", canApproveCancelBill)
            .put("approval_pin", approvalPin.trim())
        call("POST", "/api/pos/users", payload)
    }

    suspend fun setPosUserActive(
        session: WebPosSession,
        userId: String,
        active: Boolean
    ) {
        requireSameLiveSession(session, requireShift = false, requireSell = false)
        call(
            "PATCH",
            "/api/pos/users",
            JSONObject()
                .put("action", "set_active")
                .put("user_id", userId)
                .put("branch_id", session.branchId)
                .put("is_active", active)
        )
    }

    suspend fun updatePosUserProfile(
        session: WebPosSession,
        user: WebPosUserRow,
        fullName: String,
        email: String,
        role: String,
        employeeCode: String,
        positionTitle: String,
        permissionRole: String,
        approvalPin: String
    ) {
        requireSameLiveSession(session, requireShift = false, requireSell = false)
        require(role in setOf("manager", "staff", "accountant", "kitchen", "owner"))
        require(employeeCode.all(Char::isDigit) && employeeCode.isNotBlank())
        call(
            "PATCH",
            "/api/pos/users",
            JSONObject()
                .put("action", "update_profile")
                .put("user_id", user.userId)
                .put("branch_id", session.branchId)
                .put("full_name", fullName.trim())
                .put("email", email.trim())
                .put("role", role)
                .put("employee_code", employeeCode)
                .put("position_title", positionTitle.trim())
                .put("permission_role", permissionRole.trim().ifBlank { role })
                .put("approval_pin", approvalPin.trim())
        )
    }

    suspend fun setPosUserPin(
        session: WebPosSession,
        userId: String,
        pin: String,
        approvalPin: String
    ) {
        requireSameLiveSession(session, requireShift = false, requireSell = false)
        require(pin.length in 4..12 && pin.all(Char::isDigit))
        call(
            "PATCH",
            "/api/pos/users",
            JSONObject()
                .put("action", "set_pin")
                .put("user_id", userId)
                .put("branch_id", session.branchId)
                .put("pin", pin)
                .put("approval_pin", approvalPin.trim())
        )
    }

    suspend fun setPosUserDeviceScope(
        session: WebPosSession,
        userId: String,
        scopeMode: String,
        deviceId: String?
    ) {
        requireSameLiveSession(session, requireShift = false, requireSell = false)
        require(scopeMode in setOf("all_devices", "single_device"))
        if (scopeMode == "single_device") require(!deviceId.isNullOrBlank())
        call(
            "PATCH",
            "/api/pos/users",
            JSONObject()
                .put("action", "set_device_scope")
                .put("user_id", userId)
                .put("branch_id", session.branchId)
                .put("scope_mode", scopeMode)
                .put("device_id", deviceId ?: JSONObject.NULL)
        )
    }

    suspend fun setPosUserCancelBillApproval(
        session: WebPosSession,
        userId: String,
        enabled: Boolean,
        staffPin: String,
        ownerPin: String
    ) {
        requireSameLiveSession(session, requireShift = false, requireSell = false)
        if (enabled) require(staffPin.length in 4..12 && staffPin.all(Char::isDigit))
        require(ownerPin.length in 4..12 && ownerPin.all(Char::isDigit))
        call(
            "PATCH",
            "/api/pos/users",
            JSONObject()
                .put("action", "set_cancel_bill_approval")
                .put("user_id", userId)
                .put("branch_id", session.branchId)
                .put("is_enabled", enabled)
                .put("pin", staffPin)
                .put("approval_pin", ownerPin)
        )
    }

    suspend fun deletePosUser(
        session: WebPosSession,
        userId: String
    ) {
        requireSameLiveSession(session, requireShift = false, requireSell = false)
        require(UUID.fromString(userId).toString() == userId)
        call(
            "DELETE",
            "/api/pos/users?user_id=" + queryValue(userId) +
                "&branch_id=" + queryValue(session.branchId)
        )
    }

    suspend fun taxSettings(session: WebPosSession): WebTaxSettings {
        requireSameLiveSession(session, requireShift = false, requireSell = false)
        val data = call(
            "GET",
            "/api/pos/settings/tax?branch_id=" + queryValue(session.branchId)
        )
        val row = data.optJSONObject("tax_settings") ?: JSONObject()
        val lines = row.optJSONArray("lines") ?: JSONArray()
        return WebTaxSettings(
            enabled = row.optBoolean("is_enabled", false),
            calculationBase = row.optString("calculation_base").ifBlank { "net_after_discount" },
            lines = (0 until lines.length()).mapNotNull { index ->
                val line = lines.optJSONObject(index) ?: return@mapNotNull null
                WebTaxLineSettings(
                    id = line.optString("id"),
                    label = line.optString("label"),
                    ratePct = line.optDouble("rate_pct", 0.0),
                    mode = line.optString("mode").ifBlank { "add_to_bill" },
                    active = line.optBoolean("is_active", false)
                )
            }
        )
    }

    suspend fun saveTaxSettings(
        session: WebPosSession,
        settings: WebTaxSettings
    ): WebTaxSettings {
        requireSameLiveSession(session, requireShift = false, requireSell = false)
        val lines = JSONArray()
        settings.lines.forEach { line ->
            lines.put(JSONObject()
                .put("id", line.id)
                .put("label", line.label)
                .put("rate_pct", line.ratePct)
                .put("mode", line.mode)
                .put("is_active", line.active))
        }
        val data = call(
            "PATCH",
            "/api/pos/settings/tax",
            JSONObject()
                .put("branch_id", session.branchId)
                .put("is_enabled", settings.enabled)
                .put("calculation_base", settings.calculationBase)
                .put("lines", lines)
        )
        val row = data.optJSONObject("tax_settings") ?: JSONObject()
        val savedLines = row.optJSONArray("lines") ?: JSONArray()
        return WebTaxSettings(
            enabled = row.optBoolean("is_enabled", false),
            calculationBase = row.optString("calculation_base").ifBlank { "net_after_discount" },
            lines = (0 until savedLines.length()).mapNotNull { index ->
                val line = savedLines.optJSONObject(index) ?: return@mapNotNull null
                WebTaxLineSettings(
                    id = line.optString("id"),
                    label = line.optString("label"),
                    ratePct = line.optDouble("rate_pct", 0.0),
                    mode = line.optString("mode").ifBlank { "add_to_bill" },
                    active = line.optBoolean("is_active", false)
                )
            }
        )
    }

    suspend fun orderKitchenPolicy(session: WebPosSession): WebOrderKitchenPolicy {
        requireSameLiveSession(session, requireShift = false, requireSell = false)
        val data = call(
            "GET",
            "/api/pos/settings/order-kitchen?branch_id=" + queryValue(session.branchId)
        )
        return parseOrderKitchenPolicy(data.getJSONObject("policy"))
    }

    suspend fun saveOrderKitchenPolicy(
        session: WebPosSession,
        policy: WebOrderKitchenPolicy,
        canQrOrdering: Boolean = true,
        canKitchenPrinting: Boolean = true
    ): WebOrderKitchenPolicy {
        requireSameLiveSession(session, requireShift = false, requireSell = false)
        require(canQrOrdering || canKitchenPrinting)
        val payload = JSONObject().put("branch_id", session.branchId)
        if (canQrOrdering) {
            payload.put("popup_enabled", policy.popupEnabled)
            payload.put("kitchen_auto_send_enabled", policy.kitchenAutoSendEnabled)
        }
        if (canKitchenPrinting) {
            payload.put("kitchen_auto_print_enabled", policy.kitchenAutoPrintEnabled)
        }
        val data = call("PATCH", "/api/pos/settings/order-kitchen", payload)
        return parseOrderKitchenPolicy(data.getJSONObject("policy"))
    }

    suspend fun tableQrPolicy(
        session: WebPosSession,
        tableId: String
    ): WebTableQrPolicy {
        requireSameLiveSession(session, requireShift = false, requireSell = false)
        val data = call(
            "GET",
            "/api/backoffice/tables/" + tableId + "/qr-policy?branch_id=" +
                queryValue(session.branchId)
        )
        val policy = data.optJSONObject("policy") ?: JSONObject()
        return WebTableQrPolicy(
            tableId = data.optString("table_id").ifBlank { tableId },
            tableCode = data.optString("table_code"),
            tableName = data.optString("table_name"),
            mode = policy.optString("mode").ifBlank { "time" },
            ttlMinutes = if (policy.isNull("ttl_minutes")) null else policy.optInt("ttl_minutes")
        )
    }

    suspend fun saveTableQrPolicy(
        session: WebPosSession,
        policy: WebTableQrPolicy
    ): WebTableQrPolicy {
        requireSameLiveSession(session, requireShift = false, requireSell = false)
        require(policy.mode in setOf("time", "bill"))
        val payload = JSONObject()
            .put("branch_id", session.branchId)
            .put("mode", policy.mode)
        if (policy.mode == "time") payload.put("ttl_minutes", policy.ttlMinutes ?: 1080)
        val data = call(
            "PATCH",
            "/api/backoffice/tables/" + policy.tableId + "/qr-policy",
            payload
        )
        val row = data.optJSONObject("policy") ?: JSONObject()
        return WebTableQrPolicy(
            tableId = data.optString("table_id").ifBlank { policy.tableId },
            tableCode = data.optString("table_code"),
            tableName = data.optString("table_name"),
            mode = row.optString("mode").ifBlank { policy.mode },
            ttlMinutes = if (row.isNull("ttl_minutes")) null else row.optInt("ttl_minutes")
        )
    }

    suspend fun subscriptionOverview(session: WebPosSession): WebSubscriptionOverview {
        require(session.id.isNotBlank() && session.tenantId.isNotBlank())
        val data = call(
            "GET",
            "/api/pos/billing/overview",
            readTimeoutMs = 8_000,
            connectTimeoutMs = 4_000
        )
        val store = data.optJSONObject("store") ?: JSONObject()
        val contract = data.optJSONObject("contract") ?: JSONObject()
        val packageRows = data.optJSONArray("packages") ?: JSONArray()
        val requestRows = data.optJSONArray("requests") ?: JSONArray()
        val issuer = data.optJSONObject("issuer") ?: JSONObject()
        val dueRow = data.optJSONObject("billing_due")
        val documentRows = data.optJSONArray("documents") ?: JSONArray()
        val control = data.optJSONObject("control_plane")
        fun nullableDouble(row: JSONObject, key: String): Double? =
            if (row.isNull(key)) null else row.optDouble(key).takeIf { it.isFinite() }
        fun nullableInt(row: JSONObject, key: String): Int? =
            if (row.isNull(key)) null else row.optInt(key).takeIf { it > 0 }
        return WebSubscriptionOverview(
            storeCode = store.optString("code"),
            storeName = store.optString("name"),
            packageId = contract.optString("package_id").takeIf { it.isNotBlank() && it != "null" },
            packageCode = contract.optString("package_code").takeIf { it.isNotBlank() && it != "null" },
            packageName = contract.optString("package_name"),
            contractStatus = contract.optString("status"),
            billingInterval = contract.optString("billing_interval").ifBlank { "monthly" },
            expiresAt = contract.optString("expires_at").takeIf { it.isNotBlank() && it != "null" },
            daysRemaining = if (contract.isNull("days_remaining")) null else contract.optInt("days_remaining"),
            amountPerCycle = nullableDouble(contract, "amount_per_cycle"),
            currency = contract.optString("currency").ifBlank { "THB" },
            internalDemo = contract.optBoolean("is_internal_demo", false),
            packages = (0 until packageRows.length()).mapNotNull { index ->
                val row = packageRows.optJSONObject(index) ?: return@mapNotNull null
                val id = row.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                WebSubscriptionPackage(
                    id = id,
                    code = row.optString("code"),
                    name = row.optString("name"),
                    monthlyPrice = nullableDouble(row, "monthly_price"),
                    yearlyPrice = nullableDouble(row, "yearly_price"),
                    contactSales = row.optBoolean("contact_sales", false),
                    maxBranches = nullableInt(row, "max_branches"),
                    maxDevices = nullableInt(row, "max_devices"),
                    maxUsers = nullableInt(row, "max_users")
                )
            },
            requests = (0 until requestRows.length()).mapNotNull { index ->
                val row = requestRows.optJSONObject(index) ?: return@mapNotNull null
                val id = row.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                WebSubscriptionRequest(
                    id = id,
                    status = row.optString("status"),
                    packageId = row.optString("package_id").takeIf { it.isNotBlank() && it != "null" },
                    packageName = row.optString("package_name"),
                    billingInterval = row.optString("billing_interval").ifBlank { "monthly" },
                    expectedAmount = nullableDouble(row, "expected_amount"),
                    amount = nullableDouble(row, "amount"),
                    kind = row.optString("kind").ifBlank { "renewal_intent" },
                    hasEvidence = row.optBoolean("has_evidence", false),
                    submittedAt = row.optString("submitted_at")
                )
            },
            due = dueRow?.let { row ->
                WebSubscriptionDue(
                    status = row.optString("status"),
                    payableNow = row.optBoolean("payable_now", false),
                    selfServicePaymentAllowed = row.optBoolean("self_service_payment_allowed", false),
                    supportRequired = row.optBoolean("support_required", false),
                    dueAt = row.optString("due_at").takeIf { it.isNotBlank() && it != "null" },
                    amountDue = row.optDouble("amount_due", 0.0),
                    amountPaid = row.optDouble("amount_paid", 0.0),
                    outstanding = row.optDouble("outstanding", 0.0),
                    currency = row.optString("currency").ifBlank { "THB" },
                    billingInterval = row.optString("billing_interval").ifBlank { "monthly" },
                    packageName = row.optString("package_name").takeIf { it.isNotBlank() && it != "null" },
                    openRequestStatus = row.optString("open_request_status").takeIf { it.isNotBlank() && it != "null" },
                    openRequestId = row.optString("open_request_id").takeIf { it.isNotBlank() && it != "null" },
                    accessLocked = row.optBoolean("access_locked", false)
                )
            },
            issuer = WebSubscriptionIssuer(
                name = issuer.optString("name"),
                bankName = issuer.optString("bank_name"),
                accountName = issuer.optString("account_name"),
                accountNumber = issuer.optString("account_number"),
                promptPayId = issuer.optString("promptpay_id")
            ),
            receipts = (0 until documentRows.length()).mapNotNull { index ->
                val row = documentRows.optJSONObject(index) ?: return@mapNotNull null
                val id = row.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                WebSubscriptionReceipt(
                    id = id,
                    number = row.optString("number"),
                    issuedAt = row.optString("issued_at"),
                    amount = row.optDouble("amount", 0.0),
                    currency = row.optString("currency").ifBlank { "THB" },
                    packageName = row.optString("package_name"),
                    billingInterval = row.optString("billing_interval").ifBlank { "monthly" }
                )
            },
            itConnected = control?.optBoolean("connected", false) ?: false
        )
    }

    suspend fun submitSubscriptionRequest(
        session: WebPosSession,
        requestKey: String,
        kind: String,
        packageId: String,
        billingInterval: String,
        note: String = "",
        slip: WebSubscriptionSlip? = null
    ): WebSubscriptionSubmitResult {
        require(session.id.isNotBlank() && session.tenantId.isNotBlank())
        require(session.role == "owner")
        require(UUID.fromString(requestKey).toString() == requestKey)
        require(kind in setOf("renewal_intent", "payment_notice", "custom_quote_request"))
        require(billingInterval in setOf("monthly", "yearly"))
        require(packageId.isNotBlank())
        val data = callMultipart(
            path = "/api/pos/billing/requests",
            fields = linkedMapOf(
                "request_key" to requestKey,
                "kind" to kind,
                "package_id" to packageId,
                "billing_interval" to billingInterval,
                "note" to note.take(500)
            ),
            slip = slip
        )
        return WebSubscriptionSubmitResult(
            id = data.optString("id"),
            status = data.optString("status"),
            alreadySubmitted = data.optBoolean("already_submitted", false),
            provisionalAccessGranted = data.optBoolean("provisional_access_granted", false)
        )
    }

    suspend fun subscriptionRuntime(
        session: WebPosSession,
        reason: String = "fallback"
    ): WebSubscriptionRuntime {
        require(session.id.isNotBlank() && session.tenantId.isNotBlank())
        val safeReason = if (reason in setOf("focus", "fallback")) reason else "native"
        val data = call(
            "GET",
            "/api/pos/billing/runtime?reason=" + queryValue(safeReason),
            readTimeoutMs = 6_000,
            connectTimeoutMs = 3_500
        )
        val row = data.optJSONObject("runtime") ?: JSONObject()
        return WebSubscriptionRuntime(
            locked = row.optBoolean("locked", false),
            lockReason = row.optString("lock_reason").takeIf { it.isNotBlank() && it != "null" },
            lifecycleStatus = row.optString("lifecycle_status").ifBlank { "unknown" },
            expiresAt = row.optString("expires_at").takeIf { it.isNotBlank() && it != "null" },
            daysRemaining = if (row.isNull("days_remaining")) null else row.optInt("days_remaining"),
            packageName = row.optString("package_name").ifBlank { "แพ็กเกจ" },
            billingInterval = row.optString("billing_interval").ifBlank { "monthly" },
            amountDue = if (row.isNull("amount_due")) null else row.optDouble("amount_due").takeIf { it.isFinite() },
            currency = row.optString("currency").ifBlank { "THB" }
        )
    }

    suspend fun preEntrySubscriptionQuote(): WebPreEntrySubscriptionQuote {
        val data = call(
            "GET",
            "/api/auth/subscription-checkout",
            readTimeoutMs = 8_000,
            connectTimeoutMs = 4_000
        )
        val store = data.optJSONObject("store") ?: JSONObject()
        val pkg = data.optJSONObject("package") ?: JSONObject()
        val issuer = data.optJSONObject("issuer") ?: JSONObject()
        val pending = data.optJSONObject("pending_review")
        return WebPreEntrySubscriptionQuote(
            token = data.optString("token").takeIf { it.isNotBlank() && it != "null" },
            storeCode = store.optString("code"),
            storeName = store.optString("name"),
            packageName = pkg.optString("name"),
            amount = pkg.optDouble("amount", 0.0),
            currency = pkg.optString("currency").ifBlank { "THB" },
            billingInterval = pkg.optString("billing_interval").ifBlank { "monthly" },
            bankName = issuer.optString("bank_name"),
            accountName = issuer.optString("account_name"),
            accountNumber = issuer.optString("account_number"),
            promptPayId = issuer.optString("promptpay_id"),
            pendingStatus = pending?.optString("status")?.takeIf { it.isNotBlank() },
            pendingHasEvidence = pending?.optBoolean("has_evidence", false) ?: false,
            pendingCanAttachEvidence = pending?.optBoolean("can_attach_evidence", false) ?: false
        )
    }

    suspend fun submitPreEntrySubscriptionPayment(
        quote: WebPreEntrySubscriptionQuote,
        slip: WebSubscriptionSlip,
        note: String = ""
    ): WebPreEntrySubscriptionSubmitResult {
        val token = quote.token ?: throw WebPosApiException("checkout_token_invalid", 401)
        val data = callMultipart(
            path = "/api/auth/subscription-checkout",
            fields = linkedMapOf(
                "checkout_token" to token,
                "note" to note.take(500)
            ),
            slip = slip
        )
        return WebPreEntrySubscriptionSubmitResult(
            id = data.optString("id"),
            status = data.optString("status"),
            provisionalAccessGranted = data.optBoolean("provisional_access_granted", false),
            provisionalAccessExpiresAt = data.optString("provisional_access_expires_at")
                .takeIf { it.isNotBlank() && it != "null" }
        )
    }

    suspend fun nativeDeviceHeartbeat(payload: JSONObject): JSONObject =
        call(
            method = "POST",
            path = "/api/pos/device-heartbeat",
            json = payload,
            readTimeoutMs = 8_000,
            connectTimeoutMs = 4_000,
            headers = mapOf("X-CpIPOS-Client" to "native-android")
        )

    suspend fun ackNativeDeviceCommands(results: JSONArray) {
        if (results.length() == 0) return
        require(results.length() <= 10)
        call(
            method = "POST",
            path = "/api/pos/device-commands/ack",
            json = JSONObject()
                .put("results", results)
                .put("agent_surface", "native-android")
                .put("agent_version", BuildConfig.VERSION_NAME),
            readTimeoutMs = 8_000,
            connectTimeoutMs = 4_000,
            headers = mapOf("X-CpIPOS-Client" to "native-android")
        )
    }

    suspend fun supportConversations(
        session: WebPosSession
    ): List<WebSupportConversationHead> {
        requireSameLiveSession(session, requireShift = false, requireSell = false)
        val rows = call("GET", "/api/pos/support-chat/conversations")
            .optJSONArray("conversations") ?: JSONArray()
        return (0 until rows.length()).mapNotNull { index ->
            val row = rows.optJSONObject(index) ?: return@mapNotNull null
            val id = row.optString("conversation_id").takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            WebSupportConversationHead(
                id = id,
                subject = row.optString("subject"),
                contactName = row.optString("contact_name"),
                status = row.optString("status"),
                assignedUserName = row.optString("assigned_user_name")
                    .takeIf { it.isNotBlank() && it != "null" },
                latestMessageAt = row.optString("latest_message_at")
                    .takeIf { it.isNotBlank() && it != "null" },
                latestMessagePreview = row.optString("latest_message_preview")
                    .takeIf { it.isNotBlank() && it != "null" },
                latestSenderType = row.optString("latest_sender_type")
                    .takeIf { it.isNotBlank() && it != "null" },
                unreadStoreCount = row.optInt("unread_store_count", 0)
            )
        }
    }

    suspend fun createSupportConversation(
        session: WebPosSession,
        subject: String,
        contactName: String
    ): String {
        requireSameLiveSession(session, requireShift = false, requireSell = false)
        require(subject.trim().length >= 2 && contactName.trim().length >= 2)
        val data = call(
            "POST",
            "/api/pos/support-chat/conversations",
            JSONObject()
                .put("subject", subject.trim().take(180))
                .put("contact_name", contactName.trim().take(120))
        )
        val head = data.optJSONObject("head")
        val conversation = data.optJSONObject("conversation")
        return head?.optString("conversation_id")?.takeIf { it.isNotBlank() }
            ?: conversation?.optString("id")?.takeIf { it.isNotBlank() }
            ?: throw WebPosApiException("support_chat_create_unconfirmed", 502)
    }

    suspend fun supportConversation(
        session: WebPosSession,
        conversationId: String
    ): WebSupportConversationSnapshot {
        requireSameLiveSession(session, requireShift = false, requireSell = false)
        require(conversationId.isNotBlank())
        val data = call(
            "GET",
            "/api/pos/support-chat/conversations/" + queryValue(conversationId) + "/messages"
        )
        val conversation = data.optJSONObject("conversation") ?: JSONObject()
        val messages = data.optJSONArray("messages") ?: JSONArray()
        return WebSupportConversationSnapshot(
            id = conversation.optString("id").ifBlank { conversationId },
            subject = conversation.optString("subject"),
            status = conversation.optString("status"),
            assignedUserName = conversation.optString("assigned_user_name")
                .takeIf { it.isNotBlank() && it != "null" },
            messages = (0 until messages.length()).mapNotNull { index ->
                val row = messages.optJSONObject(index) ?: return@mapNotNull null
                val id = row.optString("id").takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                WebSupportMessage(
                    id = id,
                    senderType = row.optString("sender_type"),
                    senderName = row.optString("sender_name"),
                    senderRole = row.optString("sender_role")
                        .takeIf { it.isNotBlank() && it != "null" },
                    body = row.optString("message_body"),
                    createdAt = row.optString("created_at")
                )
            }
        )
    }

    suspend fun sendSupportMessage(
        session: WebPosSession,
        conversationId: String,
        message: String
    ) {
        requireSameLiveSession(session, requireShift = false, requireSell = false)
        require(conversationId.isNotBlank() && message.trim().isNotBlank())
        call(
            "POST",
            "/api/pos/support-chat/conversations/" + queryValue(conversationId) + "/messages",
            JSONObject().put("message", message.trim().take(4000))
        )
    }

    suspend fun closeSupportConversation(
        session: WebPosSession,
        conversationId: String
    ) {
        requireSameLiveSession(session, requireShift = false, requireSell = false)
        require(conversationId.isNotBlank())
        call(
            "POST",
            "/api/pos/support-chat/conversations/" + queryValue(conversationId),
            JSONObject().put("action", "close")
        )
    }

    fun trustedWebUrl(path: String): String {
        require(path.startsWith("/") && !path.startsWith("//"))
        return base + path
    }

    private fun parseOrderKitchenPolicy(row: JSONObject): WebOrderKitchenPolicy {
        val store = row.optJSONObject("store") ?: JSONObject()
        val effective = row.optJSONObject("effective") ?: JSONObject()
        val forced = row.optJSONObject("forced_by_it") ?: JSONObject()
        return WebOrderKitchenPolicy(
            branchId = row.optString("branch_id"),
            popupEnabled = store.optBoolean("popup_enabled", true),
            kitchenAutoSendEnabled = store.optBoolean("kitchen_auto_send_enabled", true),
            kitchenAutoPrintEnabled = store.optBoolean("kitchen_auto_print_enabled", true),
            effectivePopupEnabled = effective.optBoolean("popup_enabled", true),
            effectiveKitchenAutoSendEnabled = effective.optBoolean("kitchen_auto_send_enabled", true),
            effectiveKitchenAutoPrintEnabled = effective.optBoolean("kitchen_auto_print_enabled", true),
            popupForcedByIt = forced.optBoolean("popup", false),
            sendForcedByIt = forced.optBoolean("kitchen_auto_send", false),
            printForcedByIt = forced.optBoolean("kitchen_auto_print", false)
        )
    }

    suspend fun storeSettings(session: WebPosSession): WebStoreSettings {
        requireSameLiveSession(session, requireShift = false, requireSell = false)
        return parseStoreSettings(call("GET", "/api/pos/settings/store"))
    }

    suspend fun updateStoreSettings(
        session: WebPosSession,
        displayName: String,
        logoUrl: String,
        companyAddress: String,
        contactPhone: String
    ): WebStoreSettings {
        requireSameLiveSession(session, requireShift = false, requireSell = false)
        require(displayName.trim().isNotEmpty() && displayName.length <= 120)
        require(logoUrl.length <= 220_000)
        val data = call(
            "PATCH",
            "/api/pos/settings/store",
            JSONObject()
                .put("display_name", displayName.trim())
                .put("logo_url", logoUrl.trim())
                .put("company_address", companyAddress.trim())
                .put("contact_phone", contactPhone.trim())
        )
        return parseStoreSettings(data)
    }

    /** Same payment-account settings used by CpIPOS Web. */
    suspend fun paymentSettings(session: WebPosSession): WebPaymentSettingsSnapshot {
        requireSameLiveSession(session, requireShift = false, requireSell = false)
        val data = call("GET", "/api/pos/settings/payment-accounts")
        val accountRows = data.optJSONArray("payment_accounts") ?: JSONArray()
        val branchRows = data.optJSONArray("branches") ?: JSONArray()
        val metadata = data.optJSONObject("metadata")
        return WebPaymentSettingsSnapshot(
            accounts = (0 until accountRows.length()).mapNotNull { index ->
                val row = accountRows.optJSONObject(index) ?: return@mapNotNull null
                val branchId = row.optString("branch_id").takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                WebPaymentSettingsAccount(
                    id = row.optString("id"),
                    branchId = branchId,
                    bankName = row.optString("bank_name"),
                    accountName = row.optString("account_name"),
                    accountNumber = row.optString("account_number"),
                    promptPayPhone = row.optString("promptpay_phone"),
                    qrImageUrl = row.optString("qr_image_url"),
                    qrMode = row.optString("qr_mode").ifBlank { "promptpay_link" },
                    appliesToAllBranches = row.optBoolean("applies_to_all_branches", false),
                    active = row.optBoolean("is_active", true)
                )
            },
            branches = (0 until branchRows.length()).mapNotNull { index ->
                val row = branchRows.optJSONObject(index) ?: return@mapNotNull null
                val id = row.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                WebPaymentSettingsBranch(
                    id = id,
                    name = row.optString("name").ifBlank { id },
                    code = row.optString("code"),
                    active = row.optBoolean("is_active", true)
                )
            },
            canManage = metadata?.optBoolean("can_manage", false) ?: false,
            ready = metadata?.optBoolean("payment_accounts_ready", false) ?: false
        )
    }

    suspend fun savePaymentSettingsAccount(
        session: WebPosSession,
        account: WebPaymentSettingsAccount
    ): WebPaymentSettingsAccount {
        requireSameLiveSession(session, requireShift = false, requireSell = false)
        require(runCatching { UUID.fromString(account.branchId) }.isSuccess)
        require(account.bankName.trim().isNotBlank())
        require(account.accountName.trim().isNotBlank())
        require(account.qrMode in setOf("promptpay_link", "qr_image"))
        if (account.qrMode == "promptpay_link") {
            require(account.promptPayPhone.filter(Char::isDigit).length in 9..15)
        } else {
            require(account.qrImageUrl.startsWith("data:image/") ||
                account.qrImageUrl.startsWith("https://"))
            require(account.qrImageUrl.length <= 350_000)
        }
        val payload = JSONObject()
            .put("branch_id", account.branchId)
            .put("bank_name", account.bankName.trim())
            .put("account_name", account.accountName.trim())
            .put("account_number", account.accountNumber.trim())
            .put("promptpay_phone", account.promptPayPhone.filter(Char::isDigit))
            .put("qr_image_url", account.qrImageUrl.trim())
            .put("qr_mode", account.qrMode)
            .put("applies_to_all_branches", account.appliesToAllBranches)
            .put("is_active", account.active)
        if (account.id.isNotBlank()) payload.put("id", account.id)
        val data = call(
            if (account.id.isBlank()) "POST" else "PATCH",
            "/api/pos/settings/payment-accounts",
            payload
        ).getJSONObject("account")
        return WebPaymentSettingsAccount(
            id = data.getString("id"),
            branchId = data.getString("branch_id"),
            bankName = data.optString("bank_name"),
            accountName = data.optString("account_name"),
            accountNumber = data.optString("account_number"),
            promptPayPhone = data.optString("promptpay_phone"),
            qrImageUrl = data.optString("qr_image_url"),
            qrMode = data.optString("qr_mode").ifBlank { "promptpay_link" },
            appliesToAllBranches = data.optBoolean("applies_to_all_branches", false),
            active = data.optBoolean("is_active", true)
        )
    }

    suspend fun deletePaymentSettingsAccount(
        session: WebPosSession,
        accountId: String,
        managerPin: String
    ) {
        requireSameLiveSession(session, requireShift = false, requireSell = false)
        require(runCatching { UUID.fromString(accountId) }.isSuccess)
        require(managerPin.length in 4..12 && managerPin.all(Char::isDigit))
        val data = call(
            "DELETE",
            "/api/pos/settings/payment-accounts?account_id=" + queryValue(accountId),
            JSONObject().put("manager_pin", managerPin)
        )
        check(data.optBoolean("deleted", false) && data.optString("id") == accountId) {
            "payment_account_delete_unconfirmed"
        }
    }

    /** Shared branch policy used by CpIPOS Web and Native notification UI. */
    suspend fun notificationSettings(session: WebPosSession): WebPosNotificationSettings {
        requireSameLiveSession(session, requireShift = false, requireSell = false)
        return parsePosNotificationSettings(call("GET", "/api/pos/settings/notifications"))
    }

    suspend fun updateNotificationSettings(
        session: WebPosSession,
        settings: WebPosNotificationSettings
    ): WebPosNotificationSettings {
        requireSameLiveSession(session, requireShift = false, requireSell = false)
        val data = call("PATCH", "/api/pos/settings/notifications", JSONObject()
            .put("branch_id", session.branchId)
            .put("table_qr_popup_enabled", settings.popupEnabled)
            .put("table_qr_sound_enabled", settings.soundEnabled)
            .put("table_qr_sound_volume", settings.soundVolume.coerceIn(0f, 1f).toDouble()))
        return parsePosNotificationSettings(data)
    }

    /** Register Firebase token for THIS verified tenant/branch/register only. */
    suspend fun registerNativeQrPush(
        session: WebPosSession, installationId: String, fcmToken: String, appId: String
    ) {
        requireSameLiveSession(session, requireShift = false, requireSell = false)
        require(UUID.fromString(installationId).toString() == installationId)
        require(fcmToken.length in 50..4096)
        val data = call("PUT", "/api/pos/native-qr-push-registration", JSONObject()
            .put("installation_id", installationId)
            .put("fcm_token", fcmToken)
            .put("app_id", appId))
        check(data.optBoolean("registered", false) &&
            data.optString("branch_id") == session.branchId) { "fcm_registration_unconfirmed" }
    }

    suspend fun unregisterNativeQrPush(session: WebPosSession, installationId: String) {
        requireSameLiveSession(session, requireShift = false, requireSell = false)
        require(UUID.fromString(installationId).toString() == installationId)
        val data = call("DELETE", "/api/pos/native-qr-push-registration", JSONObject()
            .put("installation_id", installationId))
        check(data.optBoolean("unregistered", false)) { "fcm_unregistration_unconfirmed" }
    }

    /** Canonical POS Web mobile/tablet global QR activity, scoped to the signed-in POS branch. */
    suspend fun qrActivity(session: WebPosSession, since: String): WebQrActivityPage {
        requireSameLiveSession(session, requireShift = false, requireSell = false)
        require(runCatching { java.time.Instant.parse(since) }.isSuccess)
        val data = call("GET", "/api/pos/table-qr-activity?since=" + queryValue(since))
        return parseQrActivityPage(data, since)
    }

    /** Service events are acknowledged on the server; a food order is not. */
    suspend fun acknowledgeQrService(
        session: WebPosSession, eventId: String, goToTable: Boolean
    ) {
        requireSameLiveSession(session, requireShift = false, requireSell = false)
        require(UUID.fromString(eventId).toString() == eventId)
        val data = call("POST", "/api/pos/table-qr-activity", JSONObject()
            .put("event_id", eventId)
            .put("action", if (goToTable) "go_to_table" else "acknowledge"))
        check(data.optBoolean("acknowledged", false)) { "qr_service_ack_unconfirmed" }
    }

    /**
     * Existing transaction-locked move-bill endpoint. Never retries automatically:
     * an HTTP timeout might mean that the bill already moved at the server.
     */
    suspend fun moveDiningBill(
        session: WebPosSession, sourceTableId: String, targetTableId: String
    ): WebTableMoveResult {
        requireSameLiveSession(session)
        require(UUID.fromString(sourceTableId).toString() == sourceTableId)
        require(UUID.fromString(targetTableId).toString() == targetTableId)
        require(sourceTableId != targetTableId)
        val result = call(
            "POST", "/api/pos/tables/$sourceTableId/move-bill",
            JSONObject().put("target_table_id", targetTableId)
        )
        check(result.optString("from_table_id") == sourceTableId &&
            result.optString("to_table_id") == targetTableId) {
            "table_move_scope_mismatch"
        }
        val sessionId = result.optString("table_session_id")
        check(runCatching { UUID.fromString(sessionId) }.isSuccess) {
            "table_move_session_unconfirmed"
        }
        return WebTableMoveResult(
            sourceId = sourceTableId, targetId = targetTableId, billSessionId = sessionId,
            orderId = result.optString("order_id").takeUnless { it.isBlank() || it == "null" },
            moved = result.optBoolean("moved", false)
        )
    }

    suspend fun diningTables(session: WebPosSession): WebTableFloor {
        requireSameLiveSession(session, requireShift = false, requireSell = false)
        val data = call("GET", "/api/pos/tables")
        val zones = data.optJSONArray("zones") ?: JSONArray()
        val tables = data.optJSONArray("tables") ?: JSONArray()
        return WebTableFloor(
            zones = (0 until zones.length()).map { index ->
                val row = zones.getJSONObject(index)
                WebTableZone(row.getString("id"), row.optString("zone_name"), row.optString("color"))
            },
            tables = (0 until tables.length()).map { index ->
                val row = tables.getJSONObject(index)
                WebDiningTable(
                    id = row.getString("id"),
                    zoneId = row.optString("zone_id").takeUnless { it.isBlank() || it == "null" },
                    code = row.optString("table_code"),
                    name = row.optString("table_name").takeUnless { it.isBlank() || it == "null" }
                        ?: row.optString("table_code"),
                    capacity = row.optInt("capacity", 0),
                    status = row.optString("status", "available"),
                    sessionId = row.optString("active_session_id").takeUnless { it.isBlank() || it == "null" },
                    orderId = row.optString("active_order_id").takeUnless { it.isBlank() || it == "null" }
                )
            }
        )
    }

    /** Server validates tables:manage, active shift and restaurant feature flag. */
    /**
     * Existing CpIPOS restaurant QR issuing API. Server checks branch,
     * active bill, QR policy, feature access and tables:manage.
     * This never creates a bill or a second payment.
     */
    suspend fun issueTableOrderQr(session: WebPosSession, tableId: String): WebTableOrderQr {
        require(UUID.fromString(tableId).toString() == tableId)
        requireSameLiveSession(session)
        val result = call(
            "POST", "/api/pos/tables/$tableId/qr-order", JSONObject(),
            readTimeoutMs = 50000
        )
        return parseTableOrderQr(result, tableId, base)
    }

    suspend fun openDiningTable(session: WebPosSession, tableId: String) {
        require(UUID.fromString(tableId).toString() == tableId)
        requireSameLiveSession(session)
        call("POST", "/api/pos/tables/$tableId/open-bill", JSONObject())
    }

    suspend fun tableBill(session: WebPosSession, tableId: String): WebTableBillSummary {
        require(UUID.fromString(tableId).toString() == tableId)
        requireSameLiveSession(session)
        val data = call("GET", "/api/pos/tables/$tableId/bill?lite=1&refresh=1")
        val entry = data.optJSONObject("session")
        val order = data.optJSONObject("order")
        return WebTableBillSummary(
            sessionId = entry?.optString("id"),
            orderNo = order?.optString("order_no"),
            status = entry?.optString("status"),
            itemCount = data.optJSONArray("items")?.length() ?: 0
        )
    }


    /**
     * Canonical table bill: server is sole source of order, items, payments.
     * Refresh bypasses the small POS Web cache; no database credentials in APK.
     */
    suspend fun diningBill(session: WebPosSession, tableId: String): WebDiningBill {
        require(UUID.fromString(tableId).toString() == tableId)
        requireSameLiveSession(session)
        val data = call("GET", "/api/pos/tables/$tableId/bill?refresh=1")
        val bill = data.optJSONObject("session")
        val order = data.optJSONObject("order")
        val rows = data.optJSONArray("items") ?: JSONArray()
        val payments = data.optJSONArray("payments") ?: JSONArray()
        val lines = (0 until rows.length()).map { i ->
            val row = rows.getJSONObject(i)
            val details = row.optJSONObject("metadata")
            val product = row.optJSONObject("products")
            WebDiningLine(
                productId = row.optString("product_id"),
                name = product?.optString("name").orEmpty().ifBlank { row.optString("product_id") },
                quantity = row.optInt("quantity"),
                unitPrice = Satang.fromBaht(row.get("unit_price").toString()),
                notes = row.optString("notes").takeIf { it.isNotBlank() && it != "null" },
                hasCustomMetadata = details != null && details.length() > 0
            )
        }
        return WebDiningBill(
            sessionId = bill?.optString("id")?.takeIf { it.isNotBlank() },
            sessionStatus = bill?.optString("status"),
            orderId = order?.optString("id")?.takeIf { it.isNotBlank() },
            orderNo = order?.optString("order_no"),
            orderStatus = order?.optString("status"),
            lines = lines,
            paid = (0 until payments.length()).any { i ->
                payments.optJSONObject(i)?.optDouble("amount", 0.0)?.let { it > 0.0 } == true
            } || order?.optString("status") == "completed",
            total = Satang.fromBaht(order?.optString("total_amount", "0") ?: "0"),
            discount = Satang.fromBaht(order?.optString("discount_amount", "0") ?: "0")
        )
    }

    /**
     * Existing authenticated POS Web dine-in sale endpoint only. Always send
     * the full desired bill state with ORIGINAL order_id when editing, so
     * Takeaway UUIDs are never used for a table. Caller verifies a fresh
     * preflight snapshot and blocks uncertain responses rather than retrying.
     */
    suspend fun saveDiningOrder(
        session: WebPosSession, tableId: String, base: WebDiningBill,
        quantities: Map<String, Int>, products: List<WebPosProduct>,
        requestId: String, discount: Satang = base.discount
    ): String {
        require(UUID.fromString(tableId).toString() == tableId)
        require(UUID.fromString(requestId).toString() == requestId)
        requireSameLiveSession(session)
        check(session.shiftId != null && session.canSell && base.editable)
        check(quantities.isNotEmpty() && quantities.values.all { it > 0 && it <= 999 })
        val priceById = products.associateBy { it.id }
        val payloadLines = JSONArray()
        var total = Satang.ZERO
        quantities.toSortedMap().forEach { (id, quantity) ->
            val product = priceById[id] ?: error("product_removed_refresh_table")
            require(product.price >= Satang.ZERO)
            total += Satang.of(product.price.value * quantity.toLong())
            payloadLines.put(JSONObject()
                .put("product_id", id)
                .put("quantity", quantity)
                .put("unit_price", BigDecimal(product.price.bahtText())))
        }
        require(discount >= Satang.ZERO && discount < total)
        // A current server snapshot guards against applying a stale full-cart
        // overwrite. The DB's replace_queued_dine_in_order_tx serializes writes,
        // but does not offer an atomic If-Match revision yet.
        val current = diningBill(session, tableId)
        if (!current.editable || current.fingerprint() != base.fingerprint()) {
            throw WebPosApiException("table_bill_changed_refresh_required", 409)
        }
        val input = JSONObject()
            .put("shift_id", session.shiftId)
            .put("order_type", "dine_in")
            .put("channel", "pos")
            .put("table_id", tableId)
            .put("app_total_amount", BigDecimal(total.bahtText()))
            .put("discount_amount", BigDecimal(discount.bahtText()))
            .put("items", payloadLines)
        base.orderId?.let { input.put("order_id", it) }
        val result = call("POST", "/api/pos/sales", input, requestId)
        return result.optString("id").ifBlank { result.optString("order_id") }
            .takeIf { it.isNotBlank() } ?: throw WebPosApiException("dine_order_response_ambiguous", 503)
    }


    /**
     * Settle the ORIGINAL dine-in order through the tablet's payment API.
     * Its atomic payment transaction protects idempotency, and backend closes
     * the table bill session and releases the dining table.
     */
    suspend fun settleDiningBill(
        session: WebPosSession, tableId: String, expected: WebDiningBill,
        method: String, cashReceived: Satang, requestId: String,
        tableLabel: String = ""
    ): WebPaidBill {
        require(method == "cash" || method == "bank_transfer")
        require(UUID.fromString(requestId).toString() == requestId)
        requireSameLiveSession(session)
        val orderId = expected.orderId ?: error("table_order_missing")
        check(expected.total > Satang.ZERO && !expected.paid &&
            expected.orderStatus == "queued" &&
            expected.sessionStatus in setOf("open", "ordering"))
        if (method == "cash") check(cashReceived >= expected.total)
        else check(cashReceived == Satang.ZERO)
        val latest = diningBill(session, tableId)
        if (latest.fingerprint() != expected.fingerprint() || latest.paid ||
            latest.total != expected.total || !latest.editable) {
            throw WebPosApiException("table_bill_changed_refresh_required", 409)
        }
        val locked = call(
            "POST", "/api/pos/tables/$tableId/payment-lock",
            JSONObject().put("order_id", orderId).put("locked", true)
        )
        if (locked.optString("order_id") != orderId ||
            locked.optString("status") != "pending_payment")
            throw WebPosApiException("table_payment_lock_uncertain", 503)
        val body = JSONObject()
            .put("order_id", orderId)
            .put("payment_lines", JSONArray().put(JSONObject()
                .put("method", method)
                .put("amount", BigDecimal(expected.total.bahtText()))))
        if (method == "cash") {
            body.put("cash_received", BigDecimal(cashReceived.bahtText()))
        } else {
            // Cashier has explicitly attested that bank funds arrived.
            // Displaying the QR alone never calls this function.
            body.put("skip_transfer_verification", true)
        }
        val result = call("POST", "/api/pos/payments", body, requestId)
        if (result.optBoolean("table_cleanup_ok", true) != true) {
            throw WebPosApiException("table_payment_cleanup_needs_review", 503)
        }
        val receiptLines = expected.lines.map { line ->
            DemoReceiptLine(
                productId = line.productId,
                name = line.name,
                quantity = line.quantity,
                unitPrice = line.unitPrice
            )
        }
        val subtotal = Satang.of(receiptLines.sumOf { it.amount.value })
        val received = if (method == "cash") cashReceived else expected.total
        return WebPaidBill(
            orderId = orderId,
            receipt = DemoSaleReceipt(
                id = orderId,
                billNo = expected.orderNo?.takeIf { it.isNotBlank() } ?: orderId,
                createdAtMs = System.currentTimeMillis(),
                branchName = session.branchName,
                counterCode = session.deviceCode,
                modeLabel = if (tableLabel.isBlank()) "นั่งโต๊ะ" else "นั่งโต๊ะ · โต๊ะ $tableLabel",
                lines = receiptLines,
                total = expected.total,
                received = received,
                change = (received - expected.total).takeIf { it >= Satang.ZERO } ?: Satang.ZERO,
                isDemo = false,
                storeName = session.storeName,
                cashierName = session.cashierName,
                shiftLabel = "open",
                paymentMethod = method,
                discount = expected.discount,
                subtotal = subtotal
            )
        )
    }



    /**
     * The same branch-scoped PIN approval flow as CpIPOS tablet POS.
     * The backend validates manager/owner or explicitly granted staff PINs;
     * the PIN itself is sent once over the authenticated HTTPS session and is
     * never persisted, logged or embedded in the APK.
     */
    suspend fun approveDiningCancellation(
        session: WebPosSession, expected: WebDiningBill, pin: String
    ): String {
        requireSameLiveSession(session)
        require(pin.length in 4..12 && pin.all { it.isDigit() })
        val targetId = expected.orderId ?: expected.sessionId
            ?: throw WebPosApiException("table_bill_missing", 409)
        require(UUID.fromString(targetId).toString() == targetId)
        val result = call("POST", "/api/backoffice/approvals/pin", JSONObject()
            .put("tenant_id", session.tenantId)
            .put("branch_id", session.branchId)
            .put("action", "cancel_bill")
            .put("target_table", if (expected.orderId != null) "orders" else "table_bill_sessions")
            .put("target_id", targetId)
            .put("manager_pin", pin))
        val approvalId = result.optString("approval_id")
        require(runCatching { UUID.fromString(approvalId) }.isSuccess) {
            "pin_approval_id_missing"
        }
        return approvalId
    }

    /**
     * Cancel the original queued dine-in order via exactly the same
     * server-enforced cancellation_approval_id endpoint used by POS Web.
     * This is a cancellation, not a paid receipt deletion or item reset.
     *
     * An empty table with no order uses the existing table-management API,
     * after a server-validated PIN approval and permission check. That older
     * endpoint still enforces tables:manage rather than the approval ID.
     * Responses are checked against the authoritative table floor; callers
     * preserve their journal entry if the result is uncertain.
     */
    suspend fun cancelDiningBill(
        session: WebPosSession, tableId: String, expected: WebDiningBill,
        approvalId: String
    ): Boolean {
        requireSameLiveSession(session)
        require(UUID.fromString(tableId).toString() == tableId)
        require(UUID.fromString(approvalId).toString() == approvalId)
        val current = diningBill(session, tableId)
        if (!current.editable || current.paid || current.sessionId != expected.sessionId ||
            current.fingerprint() != expected.fingerprint()) {
            throw WebPosApiException("table_changed_cancel_requires_refresh", 409)
        }
        val orderId = current.orderId
        if (orderId != null) {
            check(session.canSell) { "sale_cancel_permission_required" }
            call("POST", "/api/pos/orders/$orderId/cancel", JSONObject()
                .put("reason", "ยกเลิกบิลโต๊ะผ่าน CpIPOS Native POS")
                .put("cancellation_approval_id", approvalId))
        } else {
            check(current.lines.isEmpty() && session.canManageTables) {
                "empty_table_management_permission_required"
            }
            call("POST", "/api/pos/tables/$tableId/cancel-empty-bill", JSONObject())
        }
        return diningTables(session).tables.any { it.id == tableId &&
            it.status == "available" && it.sessionId == null }
    }

    /**
     * Read-only cashier view of the SAME active payment account configured in
     * tablet POS settings. The server scopes tenant/branch and role.
     */
    suspend fun activePaymentAccounts(session: WebPosSession): List<WebPaymentAccount> {
        requireSameLiveSession(session)
        val rows = call("GET", "/api/pos/payments/native-accounts")
            .optJSONArray("accounts") ?: JSONArray()
        return (0 until rows.length()).map { i ->
            val row = rows.getJSONObject(i)
            WebPaymentAccount(
                id = row.getString("id"),
                bankName = row.optString("bank_name"),
                accountName = row.optString("account_name"),
                accountNumber = row.optString("account_number"),
                promptPayPhone = row.optString("promptpay_phone"),
                qrImageUrl = row.optString("qr_image_url"),
                qrMode = row.optString("qr_mode")
            )
        }
    }

    /**
     * Use the server's authenticated PromptPay QR proxy, NOT untrusted web
     * content or a third-party direct request containing merchant data.
     * The QR is display-only until cashier explicitly confirms funds received.
     */
    suspend fun promptPayQrImage(phone: String, amount: Satang): ByteArray =
        withContext(Dispatchers.IO) {
            val digits = phone.filter(Char::isDigit)
            require(digits.length in 9..15 && amount > Satang.ZERO)
            val url = URL(base + "/api/pos/promptpay-qr-proxy")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 8000
                readTimeout = 12000
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Accept", "image/png,image/webp,image/jpeg")
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
            try {
                cookieManager.get(url.toURI(), emptyMap()).forEach { (header, values) ->
                    if (header.equals("Cookie", ignoreCase = true)) {
                        connection.setRequestProperty("Cookie", values.joinToString("; "))
                    }
                }
                val path = "/$digits/${amount.bahtText()}"
                connection.outputStream.use {
                    it.write(JSONObject().put("path", path).toString().toByteArray(Charsets.UTF_8))
                }
                val status = connection.responseCode
                val contentType = connection.contentType.orEmpty().lowercase()
                if (status !in 200..299) {
                    val serverCode = runCatching {
                        val raw = connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                            .orEmpty()
                        JSONObject(raw).optJSONObject("error")?.optString("code").orEmpty()
                    }.getOrDefault("")
                    throw WebPosApiException(
                        serverCode.ifBlank { "promptpay_qr_unavailable" },
                        status
                    )
                }
                if (!contentType.startsWith("image/")) {
                    throw WebPosApiException("promptpay_qr_upstream_not_image", status)
                }
                val bytes = connection.inputStream.use { stream ->
                    val out = java.io.ByteArrayOutputStream()
                    val chunk = ByteArray(8192)
                    while (out.size() <= 900_000) {
                        val count = stream.read(chunk)
                        if (count < 0) break
                        out.write(chunk, 0, count)
                    }
                    out.toByteArray()
                }
                if (bytes.isEmpty() || bytes.size > 900_000) {
                    throw WebPosApiException("promptpay_qr_invalid_image", 502)
                }
                bytes
            } finally {
                connection.disconnect()
            }
        }

    /** Queue the same routed PAYMENT NOTICE used by CpIPOS Web for dine-in transfer checkout. */
    suspend fun printDiningPaymentNotice(
        session: WebPosSession,
        bill: WebDiningBill,
        tableLabel: String,
        qrDataUri: String,
        account: WebPaymentAccount
    ): Int {
        requireSameLiveSession(session)
        val orderId = bill.orderId?.takeIf { it.isNotBlank() }
            ?: throw WebPosApiException("payment_notice_order_required", 422)
        val orderNo = bill.orderNo?.takeIf { it.isNotBlank() } ?: orderId
        require(bill.total > Satang.ZERO && bill.lines.isNotEmpty())
        require(qrDataUri.startsWith("data:image/") && qrDataUri.length <= 700_000)

        val items = JSONArray()
        bill.lines.forEach { line ->
            items.put(
                JSONObject()
                    .put("name", line.name)
                    .put("quantity", line.quantity)
                    .put("unit_price", BigDecimal(line.unitPrice.bahtText()))
                    .put("line_total", BigDecimal(Satang.of(line.unitPrice.value * line.quantity.toLong()).bahtText()))
                    .put("note", line.notes ?: JSONObject.NULL)
            )
        }
        val accountLabel = listOf(account.bankName, account.accountName, account.accountNumber)
            .filter { it.isNotBlank() }.joinToString(" · ")
        val promptPayLabel = account.promptPayPhone.takeIf { it.isNotBlank() }?.let { "PromptPay $it" }

        val data = call(
            "POST",
            "/api/pos/payment-notice",
            JSONObject()
                .put("order_id", orderId)
                .put("order_no", orderNo)
                .put("table_label", tableLabel)
                .put("total_amount", BigDecimal(bill.total.bahtText()))
                .put("discount_amount", BigDecimal(bill.discount.bahtText()))
                .put("tax_amount", BigDecimal.ZERO)
                .put("created_at", java.time.Instant.now().toString())
                .put("seller_name", session.cashierName)
                .put("qr_data_uri", qrDataUri)
                .put("account_label", accountLabel.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
                .put("promptpay_label", promptPayLabel ?: JSONObject.NULL)
                .put("items", items)
        )
        if (!data.optBoolean("queued", false)) {
            throw WebPosApiException("payment_notice_queue_unconfirmed", 503)
        }
        return data.optInt("print_jobs_queued", 0)
    }

    /** Cash drawer readiness is owned by CpIPOS printer/drawer routing. */
    suspend fun cashDrawerStatus(session: WebPosSession): WebCashDrawerStatus {
        requireSameLiveSession(session, requireShift = false, requireSell = false)
        val data = call("GET", "/api/pos/cash-drawer/open")
        val printer = data.optJSONObject("printer")
        return WebCashDrawerStatus(
            configured = data.optBoolean("configured", false),
            ready = data.optBoolean("ready", false),
            reason = data.optString("reason", "drawer_not_configured"),
            selectedMode = data.optString("selected_mode").takeIf { it.isNotBlank() },
            printerName = printer?.optString("printer_name")?.takeIf { it.isNotBlank() }
        )
    }

    /**
     * Manual drawer opening stays server-audited and uses the same printer route
     * as CpIPOS Web. The fixed reason keeps the button one-tap while satisfying
     * the existing manual-open audit requirement.
     */
    suspend fun openCashDrawer(
        session: WebPosSession,
        reason: String = "เปิดลิ้นชักด้วยตนเองจาก POS มือถือ"
    ): Boolean {
        requireSameLiveSession(session, requireShift = false, requireSell = false)
        require(reason.trim().isNotBlank())
        val data = call(
            "POST",
            "/api/pos/cash-drawer/open",
            JSONObject().put("reason", reason.trim().take(160))
        )
        return data.optBoolean("command_sent", false) ||
            data.optBoolean("command_queued", false)
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
        requireShift: Boolean = true,
        requireSell: Boolean = true
    ): WebPosSession {
        val current = getSession()
        if (current.id != expected.id ||
            current.tenantId != expected.tenantId ||
            current.branchId != expected.branchId ||
            current.deviceCode != expected.deviceCode ||
            current.employeeId != expected.employeeId ||
            (requireSell && !current.canSell) ||
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
     * The existing sales-list deletion is an auditable SOFT DELETE + stock
     * restoration, not a hard database delete. Approval is scoped to the exact
     * order with action=sales_record_delete and enforced again server-side.
     * This endpoint is subject to the advanced_sales_reports feature gate
     * and owner / manager / IT admin role (verified by POS Web).
     */
    /**
     * PIN-authorize clearing an UNSENT takeaway cart. No server order exists
     * yet, so this approval is audited but no cancel-order API is called.
     * An already-submitted/uncertain sale must be reconciled instead.
     */
    suspend fun approveTakeawayDraftCancellation(
        session: WebPosSession, draftSaleId: String, pin: String
    ) {
        requireSameLiveSession(session)
        require(UUID.fromString(draftSaleId).toString() == draftSaleId)
        require(pin.length in 4..12 && pin.all(Char::isDigit))
        val result = call("POST", "/api/backoffice/approvals/pin", JSONObject()
            .put("tenant_id", session.tenantId)
            .put("branch_id", session.branchId)
            .put("action", "cancel_bill")
            .put("target_table", "orders")
            .put("target_id", draftSaleId)
            .put("manager_pin", pin))
        if (!runCatching { UUID.fromString(result.optString("approval_id")) }.isSuccess)
            throw WebPosApiException("draft_cancel_approval_missing", 409)
    }

    suspend fun approveSalesRecordDelete(
        session: WebPosSession, orderId: String, pin: String
    ): String {
        requireSameLiveSession(session, requireShift = false, requireSell = false)
        require(UUID.fromString(orderId).toString() == orderId)
        require(pin.length in 4..12 && pin.all(Char::isDigit))
        val data = call("POST", "/api/backoffice/approvals/pin", JSONObject()
            .put("tenant_id", session.tenantId)
            .put("branch_id", session.branchId)
            .put("action", "sales_record_delete")
            .put("target_table", "orders")
            .put("target_id", orderId)
            .put("manager_pin", pin))
        val approvalId = data.optString("approval_id")
        require(runCatching { UUID.fromString(approvalId) }.isSuccess) {
            "delete_approval_not_confirmed"
        }
        return approvalId
    }

    suspend fun deleteSalesRecord(
        session: WebPosSession, orderId: String, approvalId: String
    ) {
        requireSameLiveSession(session, requireShift = false, requireSell = false)
        require(UUID.fromString(orderId).toString() == orderId)
        require(UUID.fromString(approvalId).toString() == approvalId)
        val result = call("DELETE", "/api/pos/sales-list",
            JSONObject().put("order_id", orderId).put("approval_id", approvalId))
        if (!result.optBoolean("deleted", false)) {
            throw WebPosApiException("sales_record_delete_unconfirmed", 503)
        }
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
                shiftLabel = null,
                paymentMethod = if ((data.optJSONArray("payments") ?: JSONArray()).let { rows ->
                    (0 until rows.length()).any { i ->
                        rows.optJSONObject(i)?.optString("method") == "bank_transfer"
                    }
                }) "bank_transfer" else "cash"
            )
        } else null
        return WebOrderRecovery(snapshot, receipt)
    }

    suspend fun createTakeawayOrder(
        session: WebPosSession,
        lines: List<DemoReceiptLine>,
        saleId: String,
        expectedTotal: Satang = lines.fold(Satang.ZERO) { sum, line -> sum + line.amount }
    ): WebCreatedOrder {
        require(lines.isNotEmpty() && lines.all { it.quantity in 1..9999 })
        require(lines.map { it.productId }.distinct().size == lines.size)
        require(session.canSell && session.shiftId != null)
        require(UUID.fromString(saleId).toString() == saleId)
        requireSameLiveSession(session)
        val subtotal = lines.fold(Satang.ZERO) { sum, line -> sum + line.amount }
        require(expectedTotal > Satang.ZERO && expectedTotal <= subtotal)
        val discountTotal = subtotal - expectedTotal
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
            JSONObject().put("items", items)
                .put("discount_total", BigDecimal(discountTotal.bahtText())),
            key = saleId
        ).getJSONObject("order")
        val serverTotal = Satang.fromBaht(
            order.opt("grand_total")?.toString()?.takeIf { it != "null" }
                ?: order.get("total_amount").toString()
        )
        if (serverTotal != expectedTotal) {
            throw WebPosApiException("server_price_or_discount_changed_review_order", 409)
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
                shiftLabel = "open",
                discount = Satang.of((lines.sumOf { it.amount.value } - expectedTotal.value).coerceAtLeast(0L)),
                subtotal = Satang.of(lines.sumOf { it.amount.value })
            )
        )
    }

    /**
     * Confirm a manually verified bank transfer against the pre-created order.
     * Never call this merely because a QR was displayed or scanned.
     */
    suspend fun payExistingTransfer(
        session: WebPosSession,
        orderId: String,
        orderNo: String,
        lines: List<DemoReceiptLine>,
        saleId: String,
        expectedTotal: Satang
    ): WebPaidBill {
        require(UUID.fromString(orderId).toString() == orderId)
        require(UUID.fromString(saleId).toString() == saleId)
        require(expectedTotal > Satang.ZERO && lines.isNotEmpty())
        requireSameLiveSession(session)
        val paid = call(
            "POST",
            "/api/pos/orders/$orderId/pay",
            JSONObject().put("method", "bank_transfer")
                .put("amount", BigDecimal(expectedTotal.bahtText())),
            key = UUID.nameUUIDFromBytes(("native-pay:$saleId").toByteArray()).toString()
        )
        val order = paid.getJSONObject("order")
        if (order.getString("id") != orderId || order.optString("status") != "completed") {
            throw WebPosApiException("server_transfer_not_confirmed", 409)
        }
        val preview = paid.optJSONObject("receipt_preview")
        val actualTotal = Satang.fromBaht(
            preview?.opt("total")?.toString()?.takeIf { it != "null" }
                ?: expectedTotal.bahtText()
        )
        if (actualTotal != expectedTotal) {
            throw WebPosApiException("transfer_total_mismatch_review", 409)
        }
        val store = preview?.optJSONObject("store_profile")
        val receipt = DemoSaleReceipt(
            id = orderId,
            billNo = order.optString("order_no").ifBlank { orderNo },
            createdAtMs = System.currentTimeMillis(),
            branchName = session.branchName,
            counterCode = session.deviceCode,
            modeLabel = "กลับบ้าน",
            lines = lines.toList(),
            total = expectedTotal,
            received = expectedTotal,
            change = Satang.ZERO,
            isDemo = false,
            storeName = store?.optString("display_name")?.takeIf { it.isNotBlank() }
                ?: session.storeName,
            storeAddress = store?.optString("company_address")?.takeIf { it.isNotBlank() },
            storePhone = store?.optString("contact_phone")?.takeIf { it.isNotBlank() },
            cashierName = session.cashierName,
            shiftLabel = "open",
            paymentMethod = "bank_transfer",
            discount = Satang.of((lines.sumOf { it.amount.value } - expectedTotal.value).coerceAtLeast(0L)),
            subtotal = Satang.of(lines.sumOf { it.amount.value })
        )
        return WebPaidBill(orderId, receipt)
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
        onOrderCreated: suspend (String, String) -> Unit,
        expectedTotal: Satang = lines.fold(Satang.ZERO) { sum, line -> sum + line.amount }
    ): WebPaidBill {
        require(received >= expectedTotal && expectedTotal > Satang.ZERO)
        val order = createTakeawayOrder(session, lines, saleId, expectedTotal)
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
