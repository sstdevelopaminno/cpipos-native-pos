# Native Android POS — Web-backed online cash flow and receipt alignment
Date: 2026-09-25

## Scope/behavior

Android now starts on the **live** Store Code → actual backend branch selection →
registered terminal code + employee PIN → verified POS Web cookie session →
active shift or explicit opening shift → real branch products → takeaway cash sale.
The separate labeled UI preview remains available from the Store Code screen.
No demo tenant/branch/product IDs may be used by the live sale path.

The verified backend is the *existing* CpIPOS Web POS deployed on Vercel,
not a fabricated Supabase Auth JWT. The opaque Web POS cookie remains in memory
until app restart; employee PIN is never logged or stored. The Android APK
contains NO service_role/secret key. Configurable local.properties key:
`CPIPOS_WEB_POS_API_URL` (defaults to `https://cp-ipos-web.vercel.app`).

Trusted routes used:
- POST /api/pos/auth/store/resolve — actual active branch list/rate limiting.
- POST /api/pos/auth/store/login-context — checked terminal context/cookie.
- POST /api/pos/auth/verify — real PIN and branch policy; returns POS session cookie.
- GET /api/pos/session/current — server-confirmed session, device, permission, shift.
- POST /api/pos/shifts/open — existing server-verified shift, opening_cash 0.
- GET /api/pos/products — server scoped active product catalogue.
- POST /api/pos/orders — server prices items & idempotently creates order with stable UUID.
- POST /api/pos/orders/{id}/pay — cash payment via existing trusted server/RPC
  with a stable, distinct payment idempotency UUID.

Only a response with matching canonical order ID and `status=completed` produces a
real paid receipt or clears the cart. Product-price disagreement, expired shift,
revoked device, missing permission, HTTP error and uncertain response keep the
cart. A local SQLite attempt journal records UUID/order_id and halts further
checkout after an uncertain outcome pending managerial review, avoiding silently
resubmitting with a fresh key.

The Android receipt preview and Android PrintManager PDF now display the
owner-supplied CpIPOS logo (vector trace), store/branch details, cashier,
shift/counter, order number, Bangkok timestamp, item name/quantity/unit/line
totals, method, discount, amount, cash received and change. Supports 58/80 mm
paper in the renderer (UI defaults to 80 mm). Demo receipts retain explicit
PREVIEW ONLY labels; real receipts do not.

## Web/Vercel/Supabase checks

- Web repo `sstdevelopaminno/CpIPOS`,
  `apps/backoffice-web/src/app/api/pos/orders/route.ts` uses
  `create_pos_order_tx` and server-side product pricing.
  `.../orders/[orderId]/pay/route.ts` uses `complete_pos_payment_tx`,
  shift/permission checks, stock fallback, server canonical order/receipt.
- Web receipt template `lib/printing/receipt-html-template.ts` has 58/80 mm,
  logo, store, address, cashier, order_no, date, lines, discount, cash/change.
- Vercel `cp-ipos-web` READY production deployment at inspection:
  `31e1e1b55db3fee5d6d2b740b7b92b17586e43ca`.
- CpiPOS-001 Supabase ACTIVE_HEALTHY; existing schema has orders, payments,
  shifts, pos_sessions and products, and existing transaction RPCs.
- No production schema or data was changed by this commit. No Web/IT code was
  edited, and no Vercel deployment was triggered for this Android change.

## Explicit limitations before claiming field-ready

1. A *real registered device + active account + branch/package/shift* is needed
   to run a live cash test. Build/tests alone cannot verify merchant enrollment
   or physically print on the user's Bluetooth printer.
2. The **existing Web payment route** currently passes cash received as
   `body.amount` but writes only amount due to the payment RPC; it does not
   persist `orders.cash_received` and `orders.change_amount` in that route,
   while receipt history expects them. The Android receipt shows the entered
   amount/change for this transaction, but a server reprint may show zero until
   that Web backend discrepancy is fixed and separately deployed/tested.
3. Web order and payment are two RPC steps plus fallback stock handling; a
   network failure may have produced a paid order server-side even without a
   client response. DO NOT auto-repeat with a new UUID or show it as paid.
   Review the server record, then implement canonical reconciliation and
   server return-on-replay before unattended offline sync.
4. Transfers are disabled for paid receipts until bank confirmation exists.
   Real dine-in/Table Bill must use the Web dine-in flow; this live route is
   takeaway-only. The demo checkout remains isolated SQLite preview.
5. Real receipt history/reprint must use server receipt/permission endpoints,
   not demo history; this change prints the newly confirmed real receipt.
   For physical 58/80 mm printing Android needs a suitable printer service
   or a later direct ESC/POS adapter; Save to PDF works via Android PrintManager.
6. Review Web checkout transaction's final server cash fields and store receipt
   printer registration before introducing broad Production rollout.

Validation: GitHub Actions :app:testDebugUnitTest and :app:assembleDebug;
test physically with registered device and check the same order/payment
against POS Web. No seeded production sale was created by automated tests.

## 2026-09-25 UI regression correction (same approved login UI)

The first live-integrated APK accidentally replaced the original, centered
CpIPOS login card with an unrelated vertical live-login layout. Android now
reuses the exact original `LoginLandingScreen` for both flows: original symbol,
CpIPOS brand, TH/EN language control, outlined Store Code input and blue action.
On the same card a clearly labeled **UI test** action opens the already-built
mode picker / products / cart / payment / receipt directly, with no fake PIN and
without writing to the production store. Android Back from the test mode picker
returns to the real login; actual login still requires server-resolved branch,
registered terminal, authenticated employee and open shift.

Web POS public Store Code resolver accepts numeric customer access codes of
**six digits**, as well as existing legacy codes. A five-digit numeric test
value is not a guarantee that a merchant exists: the UI translates the server's
`store_not_found` (404) into an actionable Thai message rather than displaying
raw `POS API: store_not_found`. No client-side auto-padding or fake tenant was
introduced. Confirm the actual merchant Store Code in Web POS before a real test.
