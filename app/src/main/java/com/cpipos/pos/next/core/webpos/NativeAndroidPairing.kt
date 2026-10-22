package com.cpipos.pos.next.core.webpos

import org.json.JSONObject
import java.util.UUID

/**
 * Optional, explicitly enabled enrollment of a Native 2.0 installation with an
 * existing registered cashier terminal. The install UUID is a non-secret,
 * app-private identifier; NEVER substitute employee credentials or a device code.
 *
 * Pairing changes server-side branch_devices metadata. It must be disabled on
 * ordinary preview builds and enabled only for an approved dedicated QA terminal.
 */
internal data class NativeAndroidPairing(
    val installId: String,
    val appVersion: String
) {
    init {
        val parsed = runCatching { UUID.fromString(installId) }.getOrNull()
        require(parsed != null && parsed.toString() == installId) {
            "A canonical Android installation UUID is required"
        }
        require(appVersion.isNotBlank() && appVersion.length <= 40) {
            "An Android app version is required"
        }
    }
}

internal fun deviceSelectionBody(
    deviceCode: String,
    pairing: NativeAndroidPairing?
): JSONObject = JSONObject().put("device_code", deviceCode).apply {
    if (pairing != null) {
        put("android_install_id", pairing.installId)
        put("android_app_version", pairing.appVersion)
    }
}

internal fun androidPairingWasConfirmed(responseHeader: String?): Boolean =
    responseHeader in setOf("paired", "confirmed", "lab-roaming")
