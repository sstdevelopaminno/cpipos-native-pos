# Native POS real-sale reconciliation + receipt history
Date: 2026-10-22

## Why this checkpoint exists

A mobile cashier must never create a replacement bill only because the HTTP
response was lost. The Native app now journals a cash sale BEFORE network
submission and retains the same sale UUID across create/payment retry.

## Native flow

1. Cashier confirms cash checkout.
2. Android stores an immutable local snapshot in `LiveSaleJournal`:
   - stable sale UUID / idempotency key
   - tenant, branch and cashier device scope
   - product IDs, names, quantity and unit price in integer satang
   - total and cash received
3. Android creates the Web POS order with that stable UUID.
4. Canonical order ID + order number are persisted in the journal.
5. Android pays the existing order with a stable derived payment key.
6. Cart is cleared and a real receipt is shown ONLY after server status is
   confirmed `completed`.
7. If the process/network fails at any point, the bill remains unresolved and
   blocks another checkout on that terminal until reconciliation.

## Reconcile behavior

`LiveSaleReconciler` checks unresolved bills in order:

- Preferred: `GET /api/pos/orders/{orderId}/status` (new Web compatibility API).
  It is scoped to the authenticated POS tenant/branch and does not require the
  original shift to remain open.
- During backend rollout: current-shift orders are checked first.
- If needed and entitled: receipt history is searched using the canonical
  order number around the original Bangkok sale date.
- A `completed` order is marked confirmed locally and may be reprinted.
- A `cancelled` order is marked resolved without creating a payment.
- An existing unpaid order may resume payment only with the ORIGINAL sale UUID,
  original immutable cart/tender snapshot and matching shift/total.
- Cross-shift, price mismatch, missing old v1 snapshot or unknown server state
  stays `requires_review`; the app does not guess and does not open a new bill.

The v1 SQLite journal is migrated non-destructively to v2. Old unresolved rows
remain available; rows without a replay snapshot require server/manual review.

## Real receipt history/search

The live sale screen now opens an in-app receipt history sheet using the existing
authenticated `/api/pos/receipts` API. Default scope is the last 30 Bangkok
calendar days, page size 40. Search uses bill/customer/external order search from
the Web API. Opening a result uses the same Native receipt viewer and Android
PrintManager print/PDF path. No direct database access is performed by the APK.

If the tenant/package/role does not authorize receipt history, the Native app
shows the server denial and does not bypass feature or permission checks.

## Web POS Production compatibility

CpIPOS Web PR #345 passed typecheck, lint, tests, schema-drift checks and
backoffice build, then was squash-merged to `main` as
`6be1182be0e3cf4e9c88521efdc08c7ecf48e255`.

Vercel Production deployment `dpl_2oXsfdP7SrfM9kvPaajnAjJ32xYy` reached
`READY`; the production `/api/system/build-info` endpoint reported the exact
same commit. Runtime error scan after deployment was clean.

Production therefore now has:
- scoped read-only `/api/pos/orders/{orderId}/status` for recovery;
- persistence of `cash_received`, `change_amount`,
  `payment_completed_at`, and `payment_completed_by`;
- real cash/change fields in the immediate receipt response;
- contract tests.

No database schema migration was required because these order columns already
existed and were already read by the receipt-history API. Native retains
compatibility fallbacks for older/non-production deployments.

## Acceptance checks on a registered phone

- normal exact-cash sale;
- cash over tender (for example due 245, receive 300 => change 55);
- disable network after server order creation, relaunch, login same store/device,
  and run Reconcile: no second order is allowed;
- disable network after server payment commits but before Android receives the
  response: Reconcile must discover completed order and show real receipt;
- search the resulting bill number in Native history;
- compare order/payment/receipt totals with CpIPOS Web;
- test Android Save to PDF / configured thermal print service.

Never generate a second sale UUID to "fix" an uncertain real payment.
