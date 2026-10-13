# Native Authentication Phase 1

## Goal

Introduce the Native POS login surface and authentication contract without changing CpiPOS-001 schema/data and without sending employee PIN material to an unsafe client-side verification path.

## Current flow

1. Collect Store Code.
2. Collect employee PIN.
3. Call the `NativeAuthGateway` abstraction.
4. The current `GuardedNativeAuthGateway` intentionally returns `BackendUnavailable` and performs no network/database request.
5. The PIN field is cleared immediately after the attempt.

## Security invariants

- Never embed a Supabase `service_role` or database credential in the APK.
- Never download `pin_hash`/equivalent verifier data to the APK.
- Never log or persist the employee PIN.
- CpiPOS-001 RLS remains the authoritative data-access boundary.
- Android 1.0.23, the existing CpIPOS web repository, and Vercel production remain isolated.
- No INSERT/UPDATE/DELETE path is enabled in this phase.

## Next backend step

Before enabling real login, define and review a trusted authentication boundary for Store Code + employee PIN. Preferred options are a tightly scoped Supabase Edge Function or RPC that validates credentials server-side and returns only the minimum session/bootstrap context required by the Native app.

The production gateway should then progress through tenant, employee, permitted branch, device, session context, package/features, and product bootstrap while preserving tenant/branch scope.
