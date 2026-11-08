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

    @Test fun ownerAndManagerRetainAuthorizedMenusAndBillOperations() {
        listOf("owner", "manager").forEach { role ->
            assertTrue(nativeMoreVisible(role, "summary"))
            assertTrue(nativeTabVisible(role, "settings"))
            assertTrue(nativeCanModifyBills(role))
        }
    }
}
