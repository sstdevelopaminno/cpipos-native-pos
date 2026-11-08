**8 Nov update:** Latest customer Stable is **2.1.0**, published from central CpIPOS Releases 5 Nov 2026. Follow-up Native branch adds manual customer update menu and safe-install guard, not yet released. Central CpIPOS PR #424 merged (read-only effective kitchen setting); follow-up PR #425 adds a fail-closed Native atomic-sale fallback guard (CI required). Backend production env still lacks `NATIVE_ATOMIC_MENU_SALES_ENABLED`; no approval to activate until integration and physical QA.

## Current review: 2026-11-08 (supplements the 2026-10-22 historical snapshot)

- `main` contains the November 7 canonical HTML receipt rasterization change, but the subsequent native-repository signed release failed at the signing-secret validation step.
- Latest successful PR CI confirms Android debug build and Android 8.1/15 emulator smoke; there is no new physical-device acceptance evidence.
- A new work branch `fix/native-dine-kitchen-item-details-20261108` proposes a product details entrypoint for plain-product notes in takeaway/dine-in and idempotent server kitchen dispatch verification after table save.
- The production CpiPOS-001 database has enabled kitchen item-insert route triggers and the November 5 atomic modifier/cart-line SQL migrations; this does **not** mean that the deployed server flag or physical feature acceptance is complete.
- The Native checkout gate `NATIVE_ATOMIC_MENU_SALES_ENABLED` must be tested in the active backend environment; do not override price/stock validation to make the UI seem functional.
- Existing customized bills currently block unsafe edit; structured persisted line snapshot, base ingredient deltas, and kitchen/receipt round-trip tests remain P0.
- Acceptance tasks: [mobile dine-in/kitchen details QA](NATIVE_DINE_IN_KITCHEN_ITEM_DETAILS_QA_2026-11-08.md), GitHub Issue #20 and #30.

# Development Status

Last verified: 2026-10-22

## Verified Native branch

Active development branch: `feature/native-auth-phase1`.

- Kotlin + Jetpack Compose native app; no WebView runtime.
- Debug identity remains `com.cpipos.pos.next.dev`.
- GitHub Actions runs JVM unit tests and `:app:assembleDebug`.
- Entry is the real CpIPOS Web pre-entry flow: Store Code → branch → employee code → registered cashier device.
- POS session, device status, permission and active shift are re-checked server-side.
- Product catalog is server scoped; canonical product media is loaded separately and media failure does not block selling.
- Takeaway cash checkout creates a real server order and completes payment with stable idempotency IDs.
- Cart clears only after confirmed payment. An uncertain response remains journaled and blocks a replacement bill.
- Journal v2 stores immutable cart/tender data and supports Reconcile of prepared/order-created/requires-review sales.
- Receipt history/search uses the authenticated Web receipt API with the tenant/package/permission gates preserved.
- Real receipts can be displayed and sent to Android PrintManager / Save to PDF.

## Web compatibility work

CpIPOS Web PR #345 was reviewed, passed CI and was merged as
`6be1182be0e3cf4e9c88521efdc08c7ecf48e255`. Vercel Production deployment
`dpl_2oXsfdP7SrfM9kvPaajnAjJ32xYy` is READY and
`/api/system/build-info` reports that exact commit.

Production now provides:

- read-only tenant/branch-scoped `GET /api/pos/orders/{orderId}/status`;
- persisted `cash_received`, `change_amount`, payment completion time/user;
- real cash/change fields in immediate receipt data;
- no database schema migration.

Native keeps compatibility fallbacks for older deployments/dev environments,
but Production no longer depends on those fallbacks for the recovery endpoint.

## Still incomplete

1. Real bank-transfer verification/settlement.
2. Camera/barcode scanner.
3. Dine-in / table-bill flow.
4. Close-shift flow in Native UI.
5. Direct Bluetooth/LAN ESC/POS transport and printer routing.
6. Fully wired offline outbox/sync worker and conflict/reconciliation policy.
7. Real server receipt-history pagination/date UI beyond the current last-30-day search surface.
8. Instrumented/E2E Android tests and controlled merchant/printer field acceptance.

## Local handset check

Use `docs/WINDOWS_VSCODE_ANDROID_DEVICE.md` and build/install from
`feature/native-auth-phase1`. Do not reset local changes with `git reset --hard`.

CI passing is not evidence that a USB handset, merchant permissions, physical
printer or a real payment was tested.
