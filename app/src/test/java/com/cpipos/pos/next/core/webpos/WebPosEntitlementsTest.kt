package com.cpipos.pos.next.core.webpos

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebPosEntitlementsTest {
    private fun snapshot(
        features: Map<String, Boolean> = emptyMap(),
        menu: Map<String, Boolean> = emptyMap(),
        custom: Boolean = false,
        optical: Map<String, Boolean> = emptyMap(),
        takeaway: Boolean = true,
        dineIn: Boolean = true
    ) = WebPosEntitlements(
        tenantId = "tenant",
        branchId = "branch",
        features = features,
        menuPolicy = menu,
        isCustom = custom,
        isInternalTestFull = false,
        salesModes = WebPosSalesModes(
            takeaway = takeaway,
            dineIn = dineIn,
            buffetTable = false,
            delivery = false,
            generalSale = false,
            opticalRetail = false
        ),
        opticalMoreAccess = optical
    )

    @Test
    fun fixedPackageUsesFeatureAndIndependentMenuLock() {
        val allowed = snapshot(features = mapOf("advanced_sales_reports" to true))
        assertTrue(allowed.allows("advanced_sales_reports", "more.sales_summary", "main.more"))

        val locked = snapshot(
            features = mapOf("advanced_sales_reports" to true),
            menu = mapOf("more.sales_summary" to false)
        )
        assertFalse(locked.allows("advanced_sales_reports", "more.sales_summary", "main.more"))
    }

    @Test
    fun customPackageRequiresExplicitParentAndChildMenuOptIn() {
        val missingParent = snapshot(
            custom = true,
            features = mapOf("advanced_sales_reports" to true),
            menu = mapOf("more.sales_summary" to true)
        )
        assertFalse(missingParent.allows("advanced_sales_reports", "more.sales_summary", "main.more"))

        val allowed = snapshot(
            custom = true,
            features = mapOf("advanced_sales_reports" to true),
            menu = mapOf("main.more" to true, "more.sales_summary" to true)
        )
        assertTrue(allowed.allows("advanced_sales_reports", "more.sales_summary", "main.more"))
    }

    @Test
    fun shiftStaysReachableEvenForCustomPackage() {
        val custom = snapshot(custom = true)
        assertTrue(custom.menuEnabled("main.shift", "main.more"))
    }

    @Test
    fun opticalRouteGrantCanAuthorizeMorePageWithoutGenericFeature() {
        val route = "/preview/pos/receipts"
        val custom = snapshot(
            custom = true,
            features = mapOf("receipt_reprint_history" to false),
            menu = mapOf("main.more" to true, "more.receipts" to true),
            optical = mapOf(route to true)
        )
        assertTrue(custom.allows("receipt_reprint_history", "more.receipts", "main.more", route))
    }

    @Test
    fun saleModesRemainSeparateFromFeatureFlags() {
        val core = mapOf("core_pos_sales" to true, "table_management" to true)
        assertFalse(snapshot(features = core, takeaway = false).takeawayAllowed())
        assertFalse(snapshot(features = core, dineIn = false).dineInAllowed())
        assertTrue(snapshot(features = core, takeaway = true, dineIn = true).takeawayAllowed())
        assertTrue(snapshot(features = core, takeaway = true, dineIn = true).dineInAllowed())
    }
}
