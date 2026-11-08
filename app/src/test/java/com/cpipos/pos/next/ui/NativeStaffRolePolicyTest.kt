package com.cpipos.pos.next.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeStaffRolePolicyTest {
    @Test fun employeeSeesOnlyShiftInsideMore() {
        val pages = listOf("hub", "shift", "summary", "receipts", "tables",
            "sales", "best", "members", "tax", "billing", "updates")
        val visible = pages.filter { nativeMoreVisible("staff", it) }
        assertTrue(visible == listOf("hub", "shift"))
        assertFalse(nativeMoreVisible("STAFF", "billing"))
        assertFalse(nativeMoreVisible("staff", "updates"))
    }

    @Test fun employeeCannotEnterSettingsReportsOrModifyExistingReceipts() {
        assertFalse(nativeTabVisible("staff", "settings"))
        assertFalse(nativeTabVisible("staff", "report"))
        assertFalse(nativeCanModifyBills("staff"))
        assertTrue(nativeTabVisible("staff", "sale"))
        assertTrue(nativeTabVisible("staff", "stock"))
        assertTrue(nativeTabVisible("staff", "history"))
    }

    @Test fun unknownAndEmployeeCardRolesNeverAcquireOwnerScreens() {
        listOf("", "staff", "STAFF", "cashier", "employee", "waiter", "kitchen",
            "chef", "unrecognized", "owner_helper").forEach { role ->
            assertTrue("Must restrict $role", isNativeStaff(role))
            assertFalse(nativeTabVisible(role, "settings"))
            assertFalse(nativeTabVisible(role, "report"))
            assertFalse(nativeMoreVisible(role, "updates"))
            assertFalse(nativeMoreVisible(role, "billing"))
            assertFalse(nativeCanModifyBills(role))
            assertTrue(nativeMoreVisible(role, "shift"))
        }
    }

    @Test fun staffShiftPageHidesSalesMetricsAndExistingBillControls() {
        val more = java.io.File("src/main/java/com/cpipos/pos/next/ui/NativeMoreScreen.kt").readText()
        val dining = java.io.File("src/main/java/com/cpipos/pos/next/ui/NativeDiningOrderScreen.kt").readText()
        val sale = java.io.File("src/main/java/com/cpipos/pos/next/ui/MobilePosSalePreview.kt").readText()
        assertTrue(more.contains("if (!isNativeStaff(liveSession.role)) {"))
        assertTrue(dining.contains("allowCancelBill = nativeCanModifyBills(session.role)"))
        assertTrue(dining.contains("canChangeExistingDiscount"))
        assertTrue(sale.contains("if (allowCancelBill) CartSecondaryButton"))
    }

    @Test fun ownerAndManagerRetainAuthorizedMenusAndBillOperations() {
        listOf("owner", "manager").forEach { role ->
            assertTrue(nativeMoreVisible(role, "summary"))
            assertTrue(nativeTabVisible(role, "settings"))
            assertTrue(nativeCanModifyBills(role))
        }
    }
}
