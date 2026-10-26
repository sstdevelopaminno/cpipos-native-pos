package com.cpipos.pos.next.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext

internal object NativeUiLanguage {
    private const val PREFS = "cpipos_native_ui"
    private const val KEY = "language"
    private val languageState = mutableStateOf("th")
    @Volatile private var initialized = false

    fun state(context: Context): State<String> {
        if (!initialized) {
            synchronized(this) {
                if (!initialized) {
                    languageState.value = context.applicationContext
                        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                        .getString(KEY, "th")
                        ?.takeIf { it in setOf("th", "en", "lo") } ?: "th"
                    initialized = true
                }
            }
        }
        return languageState
    }

    fun set(context: Context, code: String) {
        require(code in setOf("th", "en", "lo"))
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, code).apply()
        initialized = true
        languageState.value = code
    }

    fun text(code: String, key: String, fallback: String): String {
        val en = mapOf(
            "nav.sale" to "Sale",
            "nav.report" to "Sales",
            "nav.stock" to "Products",
            "nav.more" to "More",
            "nav.settings" to "Settings",
            "settings.title" to "Settings",
            "settings.store" to "Store / Company",
            "settings.branches" to "Add branch",
            "settings.devices" to "Add cashier",
            "settings.users" to "POS users",
            "settings.table_qr" to "Table QR",
            "settings.taxes" to "Tax settings",
            "settings.language" to "Language",
            "settings.order_kitchen" to "Order & kitchen alerts",
            "settings.support" to "Help center",
            "settings.notifications" to "Notifications",
            "settings.payments" to "Payment settings",
            "settings.printers" to "Receipt printer",
            "more.title" to "More",
            "more.shift" to "Open / close shift",
            "more.summary" to "Sales summary",
            "more.receipts" to "Receipt history",
            "more.tables" to "Table management",
            "more.sales" to "Product sales",
            "more.best" to "Best sellers",
            "more.members" to "Members",
            "more.tax" to "Tax invoice",
            "more.billing" to "Package payment",
            "language.title" to "Language",
            "language.subtitle" to "Choose the language for Native POS menus",
            "language.saved" to "Language changed immediately",
            "billing.title" to "Package payment",
            "billing.subtitle" to "Same billing system as CpIPOS and IT",
            "billing.current" to "Current package",
            "billing.status" to "Service status",
            "billing.due" to "Amount due",
            "billing.it_connected" to "IT connected",
            "billing.monthly" to "Monthly",
            "billing.yearly" to "Yearly",
            "billing.renew" to "Renew / change package",
            "billing.pay" to "Upload payment slip",
            "billing.owner_only" to "Only the store owner can submit billing requests",
            "billing.history" to "Request history",
            "billing.receipts" to "Receipts",
            "billing.bank" to "Company receiving account",
            "billing.refresh" to "Refresh",
            "billing.select_slip" to "Choose payment slip",
            "billing.no_requests" to "No billing requests yet",
            "billing.no_receipts" to "No receipts yet"
        )
        val lo = mapOf(
            "nav.sale" to "ຂາຍ",
            "nav.report" to "ລາຍການຂາຍ",
            "nav.stock" to "ສິນຄ້າ",
            "nav.more" to "ເພີ່ມເຕີມ",
            "nav.settings" to "ຕັ້ງຄ່າ",
            "settings.title" to "ຕັ້ງຄ່າ",
            "settings.store" to "ຮ້ານ / ບໍລິສັດ",
            "settings.branches" to "ເພີ່ມສາຂາ",
            "settings.devices" to "ເພີ່ມເຄື່ອງແຄຊເຊຍ",
            "settings.users" to "ຜູ້ໃຊ້ POS",
            "settings.table_qr" to "QR ໂຕະ",
            "settings.taxes" to "ຕັ້ງຄ່າພາສີ",
            "settings.language" to "ປ່ຽນພາສາ",
            "settings.order_kitchen" to "ແຈ້ງເຕືອນອໍເດີ/ຄົວ",
            "settings.support" to "ສູນຊ່ວຍເຫຼືອ",
            "settings.notifications" to "ການແຈ້ງເຕືອນ",
            "settings.payments" to "ຕັ້ງຄ່າຊຳລະເງິນ",
            "settings.printers" to "ເຄື່ອງພິມໃບຮັບ",
            "more.title" to "ເພີ່ມເຕີມ",
            "more.shift" to "ເປີດ / ປິດກະ",
            "more.summary" to "ສະຫຼຸບຍອດຂາຍ",
            "more.receipts" to "ໃບຮັບຍ້ອນຫຼັງ",
            "more.tables" to "ຈັດການໂຕະ",
            "more.sales" to "ລາຍການຂາຍສິນຄ້າ",
            "more.best" to "ສິນຄ້າຂາຍດີ",
            "more.members" to "ສະມາຊິກ",
            "more.tax" to "ໃບກຳກັບພາສີ",
            "more.billing" to "ຊຳລະແພັກເກດ",
            "language.title" to "ປ່ຽນພາສາ",
            "language.subtitle" to "ເລືອກພາສາສຳລັບເມນູ Native POS",
            "language.saved" to "ປ່ຽນພາສາແລ້ວ",
            "billing.title" to "ຊຳລະແພັກເກດ",
            "billing.subtitle" to "ໃຊ້ລະບົບ Billing ດຽວກັບ CpIPOS ແລະ IT",
            "billing.current" to "ແພັກເກດປັດຈຸບັນ",
            "billing.status" to "ສະຖານະບໍລິການ",
            "billing.due" to "ຍອດທີ່ຕ້ອງຊຳລະ",
            "billing.it_connected" to "ເຊື່ອມຕໍ່ IT",
            "billing.monthly" to "ລາຍເດືອນ",
            "billing.yearly" to "ລາຍປີ",
            "billing.renew" to "ຕໍ່ອາຍຸ / ປ່ຽນແພັກເກດ",
            "billing.pay" to "ແນບສະລິບຊຳລະເງິນ",
            "billing.owner_only" to "ສະເພາະ Owner ສາມາດສົ່ງຄຳຂໍຊຳລະ",
            "billing.history" to "ປະຫວັດຄຳຂໍ",
            "billing.receipts" to "ໃບຮັບ",
            "billing.bank" to "ບັນຊີຮັບເງິນບໍລິສັດ",
            "billing.refresh" to "ໂຫຼດໃໝ່",
            "billing.select_slip" to "ເລືອກສະລິບ",
            "billing.no_requests" to "ຍັງບໍ່ມີຄຳຂໍ",
            "billing.no_receipts" to "ຍັງບໍ່ມີໃບຮັບ"
        )
        return when (code) {
            "en" -> en[key] ?: fallback
            "lo" -> lo[key] ?: fallback
            else -> fallback
        }
    }
}

@Composable
internal fun nativeUiText(key: String, fallback: String): String {
    val context = LocalContext.current
    val code = NativeUiLanguage.state(context).value
    return NativeUiLanguage.text(code, key, fallback)
}
