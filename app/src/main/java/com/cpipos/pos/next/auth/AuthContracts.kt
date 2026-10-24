package com.cpipos.pos.next.auth

/**
 * Credentials collected by the Native POS login screen.
 *
 * The PIN must never be logged, persisted, returned in analytics, or copied into
 * a long-lived session object.
 */
class AuthCredentials(
    val storeCode: String,
    val employeePin: String
) {
    override fun toString(): String = "AuthCredentials(storeCode=[redacted], employeePin=[redacted])"
}

data class AuthSessionContext(
    val tenantId: String,
    val branchId: String,
    val employeeId: String,
    val deviceId: String,
    val sessionId: String
)

sealed interface AuthAttemptResult {
    data class Success(
        val session: AuthSessionContext
    ) : AuthAttemptResult

    data class Rejected(
        val message: String
    ) : AuthAttemptResult

    data class BackendUnavailable(
        val message: String
    ) : AuthAttemptResult
}

/**
 * Security boundary for Native authentication.
 *
 * A production implementation must validate Store Code + employee PIN on a
 * trusted Supabase RPC/Edge Function (or an equivalent trusted server boundary).
 * It must not download PIN hashes to the APK for local verification.
 */
interface NativeAuthGateway {
    suspend fun authenticate(credentials: AuthCredentials): AuthAttemptResult
}
