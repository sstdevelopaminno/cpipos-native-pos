package com.cpipos.pos.next.core.webpos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class NativeAndroidPairingTest {
    private val qaInstallId = "bb1c5c24-64e1-4bb1-a55f-79d39a9de72b"

    @Test fun previewSelectionHasNoPairingFieldsOrHeaderRequirement() {
        val request = deviceSelectionBody("POS-QA-01", null)
        assertEquals("POS-QA-01", request.getString("device_code"))
        assertFalse(request.has("android_install_id"))
        assertFalse(request.has("android_app_version"))
    }

    @Test fun optedInSelectionMatchesTheCurrentWebPosPairingContract() {
        val pairing = NativeAndroidPairing(qaInstallId, "2.0.0-dev-local")
        val body = deviceSelectionBody("POS-QA-01", pairing)
        assertEquals("POS-QA-01", body.getString("device_code"))
        assertEquals(qaInstallId, body.getString("android_install_id"))
        assertEquals("2.0.0-dev-local", body.getString("android_app_version"))
    }

    @Test fun pairingRequiresAValidCanonicalInstallUuidAndAppVersion() {
        for (invalid in listOf("", "device123", "BB1C5C24-64E1-4BB1-A55F-79D39A9DE72B")) {
            try {
                NativeAndroidPairing(invalid, "2.0.0")
                fail("Expected invalid install UUID to be rejected: $invalid")
            } catch (_: IllegalArgumentException) {
                // Expected: the server validates a canonical installation UUID.
            }
        }
        try {
            NativeAndroidPairing(qaInstallId, "")
            fail("Expected missing app version to be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }

    @Test fun authenticatedServerMustConfirmPairingBeforeNativeSalesProceed() {
        assertTrue(androidPairingWasConfirmed("paired"))
        assertTrue(androidPairingWasConfirmed("confirmed"))
        assertTrue(androidPairingWasConfirmed("lab-roaming"))
        for (invalid in listOf(null, "", "failed", "pending", "true")) {
            assertFalse(androidPairingWasConfirmed(invalid))
        }
    }
}
