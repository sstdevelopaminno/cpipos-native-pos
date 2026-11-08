package com.cpipos.pos.next.ui

import androidx.compose.runtime.compositionLocalOf
import java.util.Locale

/**
 * This is presentation defense-in-depth. Authorization is still enforced by
 * CpIPOS server permission / tenant / branch / role checks on every mutation.
 * Never infer role from typed employee code or UI text.
 */
internal val LocalNativePosRole = compositionLocalOf { "" }

private val privilegedNativeRoles = setOf("owner", "manager", "it_admin")

/**
 * Fail closed for employee-card roles (staff, cashier, waiter, kitchen), empty
 * role claims and any unfamiliar role. New roles must be reviewed explicitly
 * rather than silently gaining owner-only screens and bill actions.
 */
internal fun isNativeStaff(role: String): Boolean =
    role.trim().lowercase(Locale.ROOT) !in privilegedNativeRoles

internal fun nativeTabVisible(role: String, tab: String): Boolean =
    !isNativeStaff(role) || tab !in setOf("settings", "report")

internal fun nativeMoreVisible(role: String, page: String): Boolean =
    !isNativeStaff(role) || page in setOf("hub", "shift")

internal fun nativeCanModifyBills(role: String): Boolean =
    role.trim().lowercase(Locale.ROOT) in setOf("owner", "manager")
