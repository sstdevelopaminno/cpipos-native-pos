# CpIPOS Native 2.0 Architecture

## Scope

CpIPOS Native 2.0 is a new native Android POS runtime. It is developed independently from the existing production Web POS / Android 1.0.23 line.

## Non-negotiable boundaries

- Production Android 1.0.23 remains untouched during Native 2.0 development.
- The existing `CpIPOS` repository remains the production/reference line.
- Vercel remains available for the current web application and company websites during the transition.
- `CpiPOS-001` remains the authoritative production database.
- `CpiPOS-002` is not part of Native 2.0.
- Native 2.0 must not depend on the existing WebView POS runtime.

## Target runtime

```text
CpIPOS Native Android
        |
        +-- Native UI: Kotlin + Jetpack Compose
        +-- Local persistence: Room / SQLite
        +-- Sync engine
        +-- Hardware adapters
        |     +-- Barcode scanner
        |     +-- Receipt printer
        |     +-- Cash drawer
        |     +-- Customer display
        |
        +-- Supabase CpiPOS-001
              +-- Auth
              +-- Postgres / Data API
              +-- RLS
              +-- Realtime where appropriate
              +-- RPC / transactional operations
              +-- Edge Functions only where server-side secret handling is required
```

## Multi-tenant model

The business hierarchy is:

```text
Tenant / Business owner
  +-- Branch 1
  +-- Branch 2
  +-- Branch N
```

Every cloud data access path must resolve tenant and branch authorization from authenticated server-side/RLS context. A tenant identifier supplied by the UI alone is never sufficient authorization.

## Transaction boundary

Critical POS writes such as completing a sale, payment, stock deduction, refund, void, shift close and tax-document issuance must be implemented as controlled transactional operations. The Android app must not emulate one business transaction by issuing unrelated table writes that can partially succeed.

## Offline-first direction

Room / SQLite is the local runtime store. The app should remain usable for supported selling operations during temporary connectivity loss. Cloud synchronization must use idempotency keys, deterministic conflict rules and an auditable sync queue.

## Migration strategy

1. Keep Android 1.0.23 stable.
2. Build Native 2.0 under a separate development application id.
3. Validate read-only access contracts against CpiPOS-001.
4. Add reviewed RLS/RPC contracts only when required.
5. Pilot Native 2.0 on test stores/devices.
6. Validate upgrade/signing compatibility before production 2.0 uses `com.cpipos.pos`.
7. Retire Web POS dependencies only after production migration is proven.
