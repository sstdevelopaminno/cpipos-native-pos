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

CpIPOS Web PR #345 was created for Native recovery:

- read-only tenant/branch-scoped `GET /api/pos/orders/{orderId}/status`;
- persists `cash_received`, `change_amount`, payment completion time/user;
- returns real cash/change in immediate receipt data;
- no schema migration.

Native includes compatibility fallbacks until that PR is actually merged and
deployed. Do not infer Production deployment from Native CI.

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
