package com.cpipos.pos.next.core.mdm

import android.content.Context

/**
 * Persists an IT device-disable decision across successful health heartbeats,
 * process death, and sign-in. Key is scoped to tenant, branch and physical
 * device code; it must not be cleared by transient network success.
 */
internal class NativeDeviceLockStore(
    context: Context,
    tenantId: String,
    branchId: String,
    deviceCode: String
) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "cpipos_native_mdm_lock_v1", Context.MODE_PRIVATE
    )
    private val key = "blocked:" + tenantId + ":" + branchId + ":" + deviceCode

    fun isLocked(): Boolean = prefs.getBoolean(key, false)

    /** Runs in the MDM IO worker; synchronous persistence narrows crash window. */
    fun setLocked(value: Boolean): Boolean =
        prefs.edit().putBoolean(key, value).commit()
}
