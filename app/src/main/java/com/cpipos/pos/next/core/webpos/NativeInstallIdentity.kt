package com.cpipos.pos.next.core.webpos

import android.content.Context
import java.util.UUID

/**
 * Stable per-installation identifier for opt-in QA device pairing.
 * AndroidManifest allows no backups, and no PIN, token or bank data is stored.
 * Reinstalling the development app creates a new identifier.
 */
internal object NativeInstallIdentity {
    private const val PREFERENCES = "cpipos_native_install_identity_v1"
    private const val KEY = "install_uuid"

    @Synchronized
    fun getOrCreate(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences(
            PREFERENCES, Context.MODE_PRIVATE
        )
        prefs.getString(KEY, null)?.let { existing ->
            if (runCatching {
                    UUID.fromString(existing).toString() == existing
                }.getOrDefault(false)
            ) return existing
        }
        val id = UUID.randomUUID().toString()
        check(prefs.edit().putString(KEY, id).commit()) {
            "Unable to persist Native device identity; pairing refused"
        }
        return id
    }
}
