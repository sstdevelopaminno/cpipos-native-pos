package com.cpipos.pos.next.auth

/**
 * Phase 1 safety implementation.
 *
 * This gateway intentionally performs no network request and no database read.
 * It exists so the Native login UI and state flow can be implemented/tested
 * without accidentally exposing PIN material or bypassing CpiPOS-001 RLS.
 */
class GuardedNativeAuthGateway : NativeAuthGateway {
    override suspend fun authenticate(credentials: AuthCredentials): AuthAttemptResult {
        return AuthAttemptResult.BackendUnavailable(
            message = "Secure Auth Gateway ยังไม่ได้เปิดใช้งาน จึงยังไม่มีการส่งรหัสร้านหรือ PIN ไปยัง Production"
        )
    }
}
