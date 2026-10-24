# Native Android cash checkout / receipt checkpoint (2026-09-25)

## Owner-provided design and implemented DEV behavior

- Cash screen is now a full-screen Jetpack Compose cashier view, not a tiny AlertDialog.
  It follows reference: back/header, item amount / discount / green total, received
  input, green change, quick tender presets, 4-column keypad including 00, decimal,
  backspace and +50/+100/+500, and bottom cancel/confirm.
- Tender input and arithmetic are exact integer satang, tested with JUnit.
  Confirm is disabled for underpayment and while a save is running.
- Confirm snapshots the current mock cart and writes ONE immutable demo cash receipt
  and all item lines to a separate Android SQLite database in a transaction.
  Only a successful commit clears the cart; a failed insert keeps the cart and
  shows an error. A stable bill UUID is retained through retry. The bill has
  a Bangkok-date-based demo number and no fabricated production order ID.
- Saved receipt is shown after commit, with print and return-to-sale actions.
  Android PrintManager renders an 80-mm-width PDF matching the existing Web
  receipt fields: seller, shift/counter, mode, order no., date, items, cash,
  discount, amount, received and change. Physical print needs a configured
  Android print service/printer; Android also supports Save to PDF.
- The top sales screen links to today's local demo bill count/total and
  reopens previously saved demo receipts for reprint. No production reports
  are polluted by these receipts.
- Transfer preview intentionally does NOT mark a bill paid, print a paid receipt
  or clear cart: no bank transaction verification is implemented yet.

## Web POS / Vercel read-only inspection

GitHub `sstdevelopaminno/CpIPOS`, branch `agent-docs-preflight-schema-drift`:
- `/api/pos/orders` requires session, shift and server-side product pricing,
  then calls `create_pos_order_tx` with `x-idempotency-key`.
- `/api/pos/orders/[orderId]/pay` validates scope, shift, payment method, order
  status and cash amount, then calls `complete_pos_payment_tx` with its own
  idempotency key; it also handles stock and receipt preview.
- `lib/printing/receipt-html-template.ts` provides 58/80 mm receipt fields.
- `lib/pos-offline-sale-store.ts` queues cash sales locally (IndexedDB) for
  later reconciliation. This Android demo SQLite database is NOT that queue.

Vercel `cp-ipos-web`, production deployment inspected was READY on commit
`31e1e1b55db3fee5d6d2b740b7b92b17586e43ca` at inspection time.
No Vercel deployment or Web/IT write was requested for this code change.

## Production activation requirements (not delivered by this DEV UI)

1. Replace the current guarded PIN gateway with a vetted native-compatible
   trusted authentication exchange. The existing Web POS cookie session is
   NOT a Supabase Auth JWT and may not be faked using only a publishable key.
   Check active staff, branch, registered device, policy, package and shift.
2. Wire an authenticated product catalog; mock products and branch labels must
   not be accepted as genuine sales input.
3. Implement a server-authoritative idempotent native order/payment/stock API
   (reuse the Web services' rules but avoid duplicating partial transactions),
   return canonical order ID/receipt, and reconcile app-crash in-flight bills.
4. Switch the UI from the isolated DEMO ledger to the validated scoped
   production outbox. Provisional offline cash receipts must be clearly tagged
   PENDING SYNC until confirmed. Report totals must avoid double counts.
5. Bank transfer must be independently verified before marking paid/printing
   a paid receipt. Add explicit printer routing / ESC-POS integration if direct
   thermal print is required; Android PrintManager alone cannot guarantee it.

IMPORTANT: preview receipts say "PREVIEW ONLY" in UI and printed PDF, and
they never sync to the real orders/payments/shifts/stock tables. No production
database/schema/Web/IT/Android 1.0.23 data or deployment was changed.
