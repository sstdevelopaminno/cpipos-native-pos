# CpiPOS Native 2.0 — Current Roadmap (updated 2026-10-22)

Items marked [x] indicate code in the active Native development branch;
they do not assert field acceptance.

## Foundation and developer workflow

- [x] Separate native Android repository / app id / Kotlin Compose UI
- [x] Gradle wrapper and JDK 17 debug APK CI
- [x] Windows VS Code + ADB/scrcpy installation script
- [x] Existing Web POS HTTPS session adapter (no WebView)
- [x] Keep Android 1.0.23 independent

## Authentication, branch and device

- [x] Live Store Code → branch → employee code → registered device selection
- [x] Server-validated session and active-shift gates before sale
- [x] Optional QA install UUID + pairing acknowledgment; default off
- [ ] Real Android QA device pairing, MDM scope and conflict-negative UAT
- [ ] Repeat login/reauth/lockout/session expiry physical device tests
- [ ] Review separate Supabase Auth/JWT foundation if a native-only backend
      is eventually introduced; Web POS cookie is not Supabase Auth

## Core POS

- [x] Branch-scoped catalog, cart, exact integer-satang cash keypad
- [x] Online takeaway order and cash payment via Web POS server
- [x] Stable order/pay idempotency keys, SQLite unresolved-sale journal
- [x] Server-confirmed receipt and Android PrintManager/PDF renderer
- [ ] Persist and reconcile true cash received/change in canonical Web record
- [ ] Server lookup/repair by payment request key after client/network crash
- [ ] Actual cashier, discount and stock audit across tenant/branch/device
- [ ] Real dine-in/Table Bill, bank-verified transfer, return/refund/void
- [ ] Reliable hardware thermal printer and barcode scanner support
- [ ] THB/LAK/USD tender precision, formatting and backend parity

## Offline and mobile reliability

- [x] SQLite draft/outbox boundary scaffolds
- [ ] Production offline-first Room/SQLite catalog and inventory snapshots
- [ ] Approved signed-out/inflight reconciliation strategy
- [ ] Idempotent replay without double stock/payment on reconnect
- [ ] Failed-network and low-memory process-death instrumented tests

## Release gates

- [ ] QA device and shop UAT documented with evidence
- [ ] Validate package/feature entitlements and all employee roles
- [ ] Production signing + Android 1.0.23 migration plan
- [ ] Reconcile Native draft PR #1/#2 and integrator PR #3 into buildable main
- [ ] Protect main and require passing Android CI
- [ ] Controlled 2.0 rollout; **no automatic removal of existing Android**

Production backend CpiPOS-001 remains unchanged by Native development.
