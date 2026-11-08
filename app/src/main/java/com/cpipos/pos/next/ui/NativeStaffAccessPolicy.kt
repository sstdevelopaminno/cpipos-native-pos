package com.cpipos.pos.next.ui

import androidx.compose.runtime.compositionLocalOf
import java.util.Locale

/**
 * This is presentation defense-in-depth. Authorization is still enforced by
 * CpIPOS server permission / tenant / branch / role checks on every mutation.
 * Never infer role from typed employee code or UI text.
 */
internal val LocalNativePosRole = compositionLocalOf { "" }

internal fun isNativeStaff(role: String): Boolean =
    role.trim().lowercase(Locale.ROOT) == "staff"

internal fun nativeTabVisible(role: String, tab: String): Boolean =
    !isNativeStaff(role) || tab !in setOf("settings", "report")

internal fun nativeMoreVisible(role: String, page: String): Boolean =
    !isNativeStaff(role) || page in setOf("hub", "shift")

internal fun nativeCanModifyBills(role: String): Boolean =
    role.trim().lowercase(Locale.ROOT) in setOf("owner", "manager")
