# CpIPOS Native POS 2.0

Native Android POS application for CUTTING POINT TECH CO., LTD.

## Project status

This repository is the clean development line for **CpIPOS Native POS 2.x**.

- Production Android `1.0.23` remains in the existing `sstdevelopaminno/CpIPOS` repository and must not be modified by work in this repository.
- The current CpIPOS Web application and Vercel deployment remain in service during the migration period.
- `CpiPOS-001` remains the authoritative production database. Native 2.0 must not perform destructive schema changes or production data migrations without a separately reviewed migration plan.
- `CpiPOS-002` is not a dependency of this project.

## Target architecture

- Kotlin
- Jetpack Compose
- Native Android UI (no WebView runtime)
- Room / SQLite local persistence for offline-first operation
- Supabase `CpiPOS-001` for cloud data, authentication, realtime and controlled RPC/Edge Function operations
- Multi-tenant: multiple business owners, each with one or more branches
- Existing subscription/package entitlements remain authoritative
- Existing CpIPOS IT / device-management concepts remain supported

## Application identity

Development builds use a separate application id so they can coexist with the production 1.0.23 app:

`com.cpipos.pos.next`

The production application id `com.cpipos.pos` is reserved for the controlled 2.0 production migration and must not be used by development builds.

## VS Code + Android handset preview (Windows)

Start at [Windows VS Code / USB Android setup](docs/WINDOWS_VSCODE_ANDROID_DEVICE.md).
The checked-in `scripts/android-device-preview.ps1` builds a debug APK with Gradle,
installs the separate `com.cpipos.pos.next.dev` app through ADB and optionally
opens scrcpy. Use `feature/native-auth-phase1`, **not** the default `main`
handoff branch. The **live** entry flow now invokes the existing server-verified CpIPOS Web POS
Store Code → branch → verified employee code → cashier selection → active shift, scoped products, order and
cash-payment APIs. The live flow now uses the previously approved unified CpIPOS UI
for branch, employee PIN, counter, sale mode and payment. No demo entry is shown.
Vercel is not used to build APKs. Live writes occur only after a real backend
session/shift and explicit cashier confirmation; no demo tenant or products
can be submitted as real sales.

## Next implementation checkpoint

See [online POS cash/receipt integration, limitations and device prerequisites](docs/NATIVE_ONLINE_CASH_RECEIPTS_2026-09-25.md)
and [earlier offline/outbox foundation](docs/NATIVE_PHASE2_IMPLEMENTATION_2026-09-25.md).
Live checkout uses the existing Web pre-entry employee and device-selection session; a Supabase publishable key
alone is not a POS login. Merchant and printer smoke tests are still required
before declaring the native path field-ready.

## Safety rules

1. Do not copy the legacy WebView POS runtime into this repository.
2. Do not store Supabase service-role keys, database passwords, Android signing keys or other secrets in Git.
3. Do not modify the production `CpIPOS` repository as part of Native 2.0 work unless explicitly planned as a compatibility/release change.
4. Do not modify production `CpiPOS-001` schema or data from ordinary application development work.
5. Security boundaries must be enforced by Supabase Auth, RLS and transactional server-side database operations, not only by UI state.

See `docs/ARCHITECTURE.md`, `docs/SECURITY.md`, `docs/ROADMAP.md`, and `docs/DEVELOPMENT_STATUS.md` on the development branch for the detailed foundation.
