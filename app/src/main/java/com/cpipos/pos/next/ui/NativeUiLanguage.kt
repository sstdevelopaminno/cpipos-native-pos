package com.cpipos.pos.next.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext

internal object NativeUiLanguage {
    private const val PREFS = "cpipos_native_ui"
    private val languageState = mutableStateOf("th")
    private var activeUserKey: String? = null

    fun state(context: Context): State<String> {
        return languageState
    }

    fun activate(context: Context, tenantId: String, employeeId: String) {
        val key = "language_${tenantId}_${employeeId}"
        if (activeUserKey == key) return
        activeUserKey = key
        val preferences = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val selected = preferences.getString(key, null)
            ?.takeIf { it in setOf("th", "en", "lo") }
            ?: languageState.value
        languageState.value = selected
        if (!preferences.contains(key)) preferences.edit().putString(key, selected).apply()
    }

    fun deactivate() {
        activeUserKey = null
        languageState.value = "th"
    }

    fun applyRemote(context: Context, tenantId: String, employeeId: String, code: String) {
        if (activeUserKey != "language_${tenantId}_${employeeId}" || code !in setOf("th", "en", "lo")) return
        set(context, code)
    }

    fun pending(context: Context, tenantId: String, employeeId: String): String? =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("pending_language_${tenantId}_${employeeId}", null)
            ?.takeIf { it in setOf("th", "en", "lo") }

    fun markPending(context: Context, tenantId: String, employeeId: String, code: String) {
        if (activeUserKey != "language_${tenantId}_${employeeId}") return
        set(context, code)
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString("pending_language_${tenantId}_${employeeId}", code).apply()
    }

    fun clearPending(context: Context, tenantId: String, employeeId: String, code: String) {
        if (pending(context, tenantId, employeeId) != code) return
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove("pending_language_${tenantId}_${employeeId}").apply()
    }

    fun set(context: Context, code: String) {
        require(code in setOf("th", "en", "lo"))
        activeUserKey?.let { key ->
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(key, code).apply()
        }
        languageState.value = code
    }

    fun text(code: String, key: String, fallback: String): String {
        val en = mapOf(
            "nav.sale" to "Sale",
            "home.close_shift" to "Close shift",
            "home.news" to "Announcements",
            "home.logout" to "Log out",
            "home.banner.title" to "Store management (POS)",
            "home.banner.subtitle" to "Sell easily, manage everything across devices",
            "home.takeaway" to "Takeaway",
            "home.takeaway.subtitle" to "For customers taking food home",
            "home.dine" to "Dine in",
            "home.dine.subtitle" to "For customers at the store",
            "home.package_locked" to "Not in your package",
            "home.receipts" to "Past receipts",
            "home.summary" to "Sales summary",
            "home.products" to "Manage products",
            "news.unread" to "Unread",
            "news.empty" to "No announcements from CpIPOS IT yet",
            "news.load_error" to "Unable to load announcements. Try again.",
            "news.read_error" to "Could not save read status",
            "news.open_link" to "Open link",
            "news.close" to "Close",
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
            "settings.store.subtitle" to "Store name, logo, address and contact for CpIPOS and receipts",
            "settings.branches.subtitle" to "Add, edit and disable branches shared with CpIPOS",
            "settings.devices.subtitle" to "Link POS devices to branches, counters and login policies",
            "settings.users.subtitle" to "Staff, roles, employee codes, PINs and device access",
            "settings.table_qr.subtitle" to "Set QR expiry by time or when the bill closes",
            "settings.taxes.subtitle" to "VAT and withholding tax after discounts, as in CpIPOS",
            "settings.language.subtitle" to "Thai / English / Lao for this employee on every device",
            "settings.order_kitchen.subtitle" to "Order popups, automatic kitchen dispatch and printing",
            "settings.support.subtitle" to "CpIPOS Support Center, LINE and company contacts",
            "settings.notifications.subtitle" to "Popups, sound, volume and Android notifications",
            "settings.payments.subtitle" to "Bank, PromptPay and QR settings shared with CpIPOS",
            "settings.printers.subtitle" to "Connect LAN, USB or Bluetooth printers and set print routes",
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
            "language.saved" to "Saved for this employee on every device",
            "common.cancel" to "Cancel",
            "common.save" to "Save",
            "common.refresh" to "Refresh",
            "stock.title" to "Products & stock",
            "stock.products" to "Products",
            "stock.ingredients" to "Ingredients",
            "stock.add" to "+ Add product",
            "stock.receive" to "+ Receive",
            "stock.search" to "Search product / SKU",
            "stock.edit" to "Edit product",
            "stock.edit_ingredient" to "Edit ingredient",
            "stock.ingredient_search" to "Search ingredient",
            "stock.branch" to "Branch",
            "stock.ingredient_name" to "Ingredient name",
            "stock.product_name" to "Product name",
            "stock.main_unit" to "Main unit",
            "stock.category" to "Category",
            "stock.retail_price" to "Retail price",
            "stock.special_price" to "Special price (leave blank to remove)",
            "stock.featured" to "Featured product",
            "menu.options" to "Choose options",
            "menu.add_cart" to "Add to cart",
            "menu.note" to "Note (optional)",
            "menu.quantity" to "Quantity",
            "menu.per_item" to "item",
            "menu.price" to "Selling price",
            "menu.regular_price" to "Regular price",
            "menu.special_price" to "Special price",
            "menu.required" to "Required",
            "menu.optional" to "Optional",
            "menu.kitchen_ingredients" to "Kitchen ingredient instructions",
            "menu.kitchen_ingredients_hint" to "None selected by default. Tick only what the kitchen should see.",
            "menu.kitchen_ingredients_stock" to "Selections are kitchen notes only. They do not change the price, recipe or stock.",
            "menu.note_too_long" to "Ingredient notes exceed 160 characters. Shorten the selection or note.",
            "menu.choose_option" to "Choose an option",
            "menu.cart_added" to "Added to cart",
            "menu.cart_prompt" to "is in the cart. Open the cart to save and send to the kitchen? Items are sent only when you tap Save / send to kitchen.",
            "menu.open_cart" to "Open cart",
            "menu.keep_shopping" to "Choose more items",
            "menu.save_send_kitchen" to "Save / send to kitchen",
            "menu.pay" to "Payment",
            "menu.hold" to "Hold bill",
            "menu.price_delta" to "Price change",
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
            "home.close_shift" to "ປິດກະ",
            "home.news" to "ແຈ້ງຂ່າວສານ",
            "home.logout" to "ອອກຈາກລະບົບ",
            "home.banner.title" to "ລະບົບຈັດການຮ້ານ (POS)",
            "home.banner.subtitle" to "ຂາຍງ່າຍ ຈັດການຄົບ ເຊື່ອມທຸກອຸປະກອນ",
            "home.takeaway" to "ຂາຍກັບບ້ານ",
            "home.takeaway.subtitle" to "ສໍາລັບລູກຄ້າຊື້ກັບ",
            "home.dine" to "ກິນຢູ່ຮ້ານ",
            "home.dine.subtitle" to "ສໍາລັບບໍລິການໃນຮ້ານ",
            "home.package_locked" to "ບໍ່ຢູ່ໃນແພັກເກດ",
            "home.receipts" to "ໃບຮັບຍ້ອນຫຼັງ",
            "home.summary" to "ສະຫຼຸບຍອດຂາຍ",
            "home.products" to "ຈັດການສິນຄ້າ",
            "news.unread" to "ຍັງບໍ່ອ່ານ",
            "news.empty" to "ຍັງບໍ່ມີຂ່າວສານຈາກ CpIPOS IT",
            "news.load_error" to "ໂຫຼດຂ່າວສານບໍ່ໄດ້ ລອງອີກຄັ້ງ",
            "news.read_error" to "ບັນທຶກສະຖານະອ່ານບໍ່ໄດ້",
            "news.open_link" to "ເປີດລິ້ງ",
            "news.close" to "ປິດ",
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
            "settings.store.subtitle" to "ຊື່ຮ້ານ ໂລໂກ້ ທີ່ຢູ່ ແລະເບີຕິດຕໍ່ ສໍາລັບ CpIPOS ແລະໃບບິນ",
            "settings.branches.subtitle" to "ເພີ່ມ ແກ້ໄຂ ແລະປິດສາຂາທີ່ໃຊ້ຮ່ວມກັບ CpIPOS",
            "settings.devices.subtitle" to "ເຊື່ອມເຄື່ອງ POS ກັບສາຂາ ເຄົາເຕີ ແລະນະໂຍບາຍເຂົ້າລະບົບ",
            "settings.users.subtitle" to "ພະນັກງານ ບົດບາດ ລະຫັດ PIN ແລະສິດໃຊ້ເຄື່ອງ",
            "settings.table_qr.subtitle" to "ກໍານົດອາຍຸ QR ຕາມເວລາ ຫຼືຈົນປິດບິນ",
            "settings.taxes.subtitle" to "VAT ແລະພາສີຫັກຫຼັງສ່ວນຫຼຸດ ເຊັ່ນດຽວກັບ CpIPOS",
            "settings.language.subtitle" to "ໄທ / English / ລາວ ສໍາລັບພະນັກງານນີ້ໃນທຸກເຄື່ອງ",
            "settings.order_kitchen.subtitle" to "ແຈ້ງເຕືອນອໍເດີ ສົ່ງເຂົ້າຄົວ ແລະພິມອັດຕະໂນມັດ",
            "settings.support.subtitle" to "ສູນຊ່ວຍເຫຼືອ CpIPOS, LINE ແລະຊ່ອງທາງຕິດຕໍ່",
            "settings.notifications.subtitle" to "ແຈ້ງເຕືອນ ສຽງ ຄວາມດັງ ແລະ Android Notification",
            "settings.payments.subtitle" to "ບັນຊີຮັບເງິນ PromptPay ແລະ QR ໃຊ້ຮ່ວມກັບ CpIPOS",
            "settings.printers.subtitle" to "ເຊື່ອມເຄື່ອງພິມ LAN / USB / Bluetooth ແລະຕັ້ງເສັ້ນທາງພິມ",
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
            "language.saved" to "ບັນທຶກພາສາໃຫ້ພະນັກງານນີ້ໃນທຸກເຄື່ອງ",
            "common.cancel" to "ຍົກເລີກ",
            "common.save" to "ບັນທຶກ",
            "common.refresh" to "ໂຫຼດໃໝ່",
            "stock.title" to "ສິນຄ້າ ແລະ ສະຕັອກ",
            "stock.products" to "ສິນຄ້າ",
            "stock.ingredients" to "ວັດຖຸດິບ",
            "stock.add" to "+ ເພີ່ມສິນຄ້າ",
            "stock.receive" to "+ ຮັບເຂົ້າ",
            "stock.search" to "ຄົ້ນຫາສິນຄ້າ / SKU",
            "stock.edit" to "ແກ້ໄຂສິນຄ້າ",
            "stock.edit_ingredient" to "ແກ້ໄຂວັດຖຸດິບ",
            "stock.ingredient_search" to "ຄົ້ນຫາວັດຖຸດິບ",
            "stock.branch" to "ສາຂາ",
            "stock.ingredient_name" to "ຊື່ວັດຖຸດິບ",
            "stock.product_name" to "ຊື່ສິນຄ້າ",
            "stock.main_unit" to "ຫົວໜ່ວຍຫຼັກ",
            "stock.category" to "ໝວດໝູ່",
            "stock.retail_price" to "ລາຄາຂາຍ",
            "stock.special_price" to "ລາຄາພິເສດ (ປ່ອຍວ່າງເພື່ອລຶບ)",
            "stock.featured" to "ສິນຄ້າແນະນໍາ",
            "menu.options" to "ເລືອກຕົວເລືອກ",
            "menu.add_cart" to "ເພີ່ມໃສ່ກະຕ່າ",
            "menu.note" to "ໝາຍເຫດ (ຖ້າມີ)",
            "menu.quantity" to "ຈຳນວນ",
            "menu.per_item" to "ຊິ້ນ",
            "menu.price" to "ລາຄາຂາຍ",
            "menu.regular_price" to "ລາຄາປົກກະຕິ",
            "menu.special_price" to "ລາຄາພິເສດ",
            "menu.required" to "ຕ້ອງເລືອກ",
            "menu.optional" to "ບໍ່ບັງຄັບ",
            "menu.kitchen_ingredients" to "ຄຳສັ່ງວັດຖຸດິບສຳລັບຄົວ",
            "menu.kitchen_ingredients_hint" to "ເລີ່ມຕົ້ນບໍ່ເລືອກ ໝາຍສະເພາະລາຍການທີ່ຢາກໃຫ້ຄົວເຫັນ",
            "menu.kitchen_ingredients_stock" to "ລາຍການທີ່ໝາຍແມ່ນຂໍ້ຄວາມສົ່ງຄົວເທົ່ານັ້ນ ບໍ່ປ່ຽນລາຄາ ສູດ ຫຼື ສະຕັອກ",
            "menu.note_too_long" to "ບັນທຶກວັດຖຸດິບເກີນ 160 ຕົວອັກສອນ ກະລຸນາຫຼຸດລາຍການ ຫຼື ຂໍ້ຄວາມ",
            "menu.choose_option" to "ເລືອກຕົວເລືອກ",
            "menu.cart_added" to "ເພີ່ມໃສ່ກະຕ່າແລ້ວ",
            "menu.cart_prompt" to "ຢູ່ໃນກະຕ່າ ເປີດກະຕ່າເພື່ອບັນທຶກ ແລະ ສົ່ງຄົວບໍ? ລາຍການຈະສົ່ງເມື່ອກົດ ບັນທຶກ/ສົ່ງຄົວ",
            "menu.open_cart" to "ເປີດກະຕ່າ",
            "menu.keep_shopping" to "ເລືອກສິນຄ້າຕໍ່",
            "menu.save_send_kitchen" to "ບັນທຶກ/ສົ່ງຄົວ",
            "menu.pay" to "ຊຳລະເງິນ",
            "menu.hold" to "ພັກບິນ",
            "menu.price_delta" to "ລາຄາປ່ຽນ",
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
