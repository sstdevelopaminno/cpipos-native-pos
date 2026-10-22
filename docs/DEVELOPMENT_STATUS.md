# Native POS 2.0 — Development Status

Verified from GitHub source: 2026-10-22. This supersedes the old 2026-09-22
mock-only summary. It is **not a field acceptance signoff**.

## Current source of truth

- This development line builds on `feature/native-auth-phase1`; latest integration
  branch: `feature/native-device-pairing-preview-20261022`.
- The `main` branch is handoff-only and cannot compile an Android APK yet.
- Kotlin/Jetpack Compose, Android package `com.cpipos.pos.next` with
  `.dev` debug suffix. Android 1.0.23 (`com.cpipos.pos`) remains untouched.
- The visible `MainActivity` calls `MobilePosLiveScreen(WebPosClient)` using
  the existing, real HTTPS Web POS cookie-based session. The historical
  `GuardedNativeAuthGateway` remains an inactive scaffold; it is *not* the
  running login gateway.
- Login steps: Store Code → server branch → back-office numeric employee code
  → employee-scoped registered cashier terminal → verified POS session →
  open shift → sale mode → server-scoped products → takeaway cash payment.
- Native cash sale uses stable order/payment idempotency keys; no receipt is
  marked paid without server confirmation. `LiveSaleJournal` stores unresolved
  attempts locally to block silent duplicate payments.
- The demo/offline `NativeSaleOutbox` and `DemoSaleLedger` are
  scaffolds, **not** connected to production offline sync.
- Existing Native HTTP endpoints were cross-checked against the Web POS
  `main@063ef1ae` on 2026-10-22: all 11 targeted routes exist (static check).

## Changes in this continuation

- Add opt-in stable Native install UUID and explicit Web server terminal pairing
  acknowledgment in the real cashier selection path. Pairing defaults **OFF**
  unless a dedicated QA terminal is authorized in ignored `local.properties`
  (`CPIPOS_ANDROID_PAIRING_ENABLED=true`).
- When enabled, Native supplies `android_install_id`,
  `android_app_version` and Android marker header only in the terminal
  selection request; requires server response `paired`, `confirmed` or
  `lab-roaming`. Fail closed on pairing failure.
- Dedicated tests cover request payloads, invalid install IDs and ACKs.
- Update Windows/VS Code/USB installation instructions to the new branch.

## Release-blocking gaps (not completed)

1. End-to-end authenticated test on dedicated store, real employee and QA
   device; negative cases: wrong branch, disabled employee, device locked,
   expired shift, cross-tenant access.
2. Existing Web order payment only settles *due*, without reliably saving
   the actual cash tendered and change in canonical order/receipt history.
   Review and test a separate Web backend change before full Native release.
3. Uncertain network payment needs server read-back/reconciliation by stable
   request key/order ID. Current Native journal prevents automatic double
   charge but unresolved bills require operator review.
4. Native bank-transfer verification, real dine-in/Table Bill, camera barcode,
   thermal printer ESC/POS, robust offline resync and full THB/LAK/USD
   currency formats/scale remain unfinished.
5. Debug APK CI and phone tests are separate gates; CI alone never proves
   printer hardware, registration, bank confirmation or POS field reliability.
6. GitHub PR #3 and earlier draft #1/#2 must be reconciled before moving
   complete source to buildable `main`. Do not blindly merge old draft branches.

## Safety

Never commit an admin key, production keystore, PIN or POS cookie. Do not
replace Android 1.0.23, change CpiPOS-001 schema/data or deploy Web from
this Native-only workstream. Client preview pairing flag must stay false
when reusing any production POS terminal.
