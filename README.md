# CpIPOS Native POS 2.0

Native Android POS application for CUTTING POINT TECH CO., LTD.

## Project status

This repository is the development line for **CpIPOS Native POS 2.x**.

- Production Android `1.0.23` remains in the existing `sstdevelopaminno/CpIPOS` repository.
- The current CpIPOS Web/Vercel backend remains authoritative during migration.
- `CpiPOS-001` remains the production database. Native 2.0 does not embed service-role/database credentials and must not perform destructive schema changes from the APK.
- Development app id is `com.cpipos.pos.next` (debug: `com.cpipos.pos.next.dev`) so it can coexist with production.

## Current live path

On `feature/native-auth-phase1`, MainActivity enters one real native flow:

`Store Code → branch → employee code → registered cashier device → POS session → active shift → mode → product catalog → cart → cash checkout → server order/payment → receipt`

The app uses the existing authenticated CpIPOS Web POS APIs. It does not turn a
Supabase publishable key into a cashier session and does not ship administrative
keys in the APK.

Implemented live checkpoints include:

- real Store Code/branch/employee/device session entry;
- active-shift validation/opening;
- server-scoped products plus canonical product-media images;
- native product grid/search/cart;
- server-authoritative takeaway cash order and payment with stable idempotency IDs;
- durable local uncertain-sale journal and Reconcile flow;
- real receipt history/search (last 30 Bangkok days) via authenticated Web POS;
- 58/80 mm Android PrintManager receipt/PDF rendering.

Transfer settlement, barcode camera scanning, dine-in/Table Bill, close shift,
direct ESC/POS printer transport, and fully wired offline sync remain incomplete.

## VS Code + Android handset preview (Windows)

Start at [Windows VS Code / USB Android setup](docs/WINDOWS_VSCODE_ANDROID_DEVICE.md).
The checked-in `scripts/android-device-preview.ps1` builds a debug APK, installs
`com.cpipos.pos.next.dev` through ADB and can open scrcpy.

Use `feature/native-auth-phase1`, **not** the default `main` handoff branch.

## Current implementation checkpoints

- [Real-sale reconciliation + receipt history (2026-10-22)](docs/NATIVE_RECONCILE_RECEIPT_HISTORY_2026-10-22.md)
- [Online cash/receipt integration (2026-09-25)](docs/NATIVE_ONLINE_CASH_RECEIPTS_2026-09-25.md)
- [Offline/outbox foundation](docs/NATIVE_PHASE2_IMPLEMENTATION_2026-09-25.md)

A CI build proves compile/unit-test/APK generation only. Field readiness still
requires a registered merchant, employee/device permission, active shift, a
controlled cash sale comparison against CpIPOS Web, and printer testing.

## Safety rules

1. Do not copy the legacy WebView POS runtime into this repository.
2. Do not store Supabase service-role keys, database passwords, Android signing keys or other secrets in Git.
3. Production Web compatibility changes must be isolated, reviewed and pass its own CI before deployment.
4. Do not perform destructive CpiPOS-001 schema/data changes from ordinary Native application work.
5. Server permissions, tenant/branch scope, active session/shift and idempotency remain authoritative; UI state is not a security boundary.
