# CpIPOS Native POS 2.0 — New Chat Handoff
Date: 2026-10-22 (Asia/Bangkok)

Use this file as the authoritative starting point for the next development chat.
Do not restart architecture discovery from zero and do not switch development to
the default `main` branch.

## Repositories and active branches

### Native Android
Repository: `sstdevelopaminno/cpipos-native-pos`

Active development branch:
`feature/native-auth-phase1`

Current HEAD before this handoff file:
`884139d6c8c0411c5214b0a0057382df2eaf4647`

Open PR:
`#3 Feature/native auth phase1`

The default Native `main` branch is an old cross-project handoff line and is
NOT the branch to continue feature implementation from.

### CpIPOS Web / authoritative POS backend
Repository: `sstdevelopaminno/CpIPOS`

Production `main`:
`6be1182be0e3cf4e9c88521efdc08c7ecf48e255`

Native compatibility PR #345 was merged and deployed.

Vercel production project:
`cp-ipos-web`

Production deployment:
`dpl_2oXsfdP7SrfM9kvPaajnAjJ32xYy`

Production alias:
`https://cp-ipos-web.vercel.app`

Production `/api/system/build-info` was verified to report commit
`6be1182be0e3cf4e9c88521efdc08c7ecf48e255`.

## CI / build state

Native workflow for HEAD `884139d6...`:

- push run `39751612941`: SUCCESS
- pull_request run `39751622819`: SUCCESS
- unit tests: passed
- `:app:assembleDebug`: passed
- debug APK artifact uploaded

Latest push artifact:
- name: `cpipos-native-preview-debug-apk`
- artifact id: `12414057137`
- expires: 2026-10-29T09:00:18Z

CpIPOS Web:
- PR #345 CI passed typecheck, lint, tests, schema drift checks and build
- merge commit on production main: `6be1182...`
- Vercel deployment is READY
- post-deploy runtime error scan returned no errors in the checked window

## What is live in Native now

The app is a 100% native Android Kotlin + Jetpack Compose POS app.
No WebView POS runtime is used.

Development package:
`com.cpipos.pos.next.dev`

Production app `com.cpipos.pos` / Android 1.0.23 remains separate and must not
be overwritten by the Native development build.

Current real entry flow:

`Store Code → branch → employee code → registered cashier device → authenticated POS session → active shift → mode → product grid → cart → cash checkout → server order/payment → receipt`

Implemented real behavior:

1. Store Code verification through existing CpIPOS Web server.
2. Branch selection and employee code verification.
3. Registered cashier device selection and POS session issuance.
4. Device/session/sale permission checks.
5. Open-shift validation and Native shift opening.
6. Server-scoped product catalog.
7. Canonical product-media image loading through authenticated Web POS API.
8. Takeaway product grid, search and cart.
9. Real cash checkout through server-authoritative order/payment APIs.
10. Stable order and payment idempotency keys.
11. Real receipt rendering.
12. Android PrintManager / Save to PDF path.
13. Durable uncertain real-sale journal (SQLite v2).
14. Real server Reconcile flow for uncertain cash transactions.
15. Real receipt history/search from CpIPOS Web for the last 30 Bangkok days.
16. Re-open a historical real receipt inside Native and print it.

## Reconcile design that MUST be preserved

Files:
- `app/src/main/java/com/cpipos/pos/next/core/webpos/LiveSaleJournal.kt`
- `app/src/main/java/com/cpipos/pos/next/core/webpos/LiveSaleReconciler.kt`
- `app/src/main/java/com/cpipos/pos/next/core/webpos/WebPosClient.kt`

Safety rule:
**Never create a new sale UUID because a real payment response was uncertain.**

Journal v2 stores:
- stable sale UUID
- tenant id
- branch id
- cashier device code
- canonical server order id/order number when known
- immutable item snapshot
- integer-satang total
- integer-satang cash received
- current recovery stage

Recovery behavior:
- preferred server endpoint:
  `GET /api/pos/orders/{orderId}/status`
- same tenant + branch POS-session scope
- does not require the original shift to remain open for read-only recovery
- if completed: mark local journal confirmed and show real receipt
- if cancelled: mark resolved cancelled
- if unpaid and original cart/tender snapshot + shift/total still match:
  resume payment using the ORIGINAL stable sale/payment key
- if price/shift/server state is ambiguous:
  remain `requires_review` and block replacement checkout
- compatibility fallback exists for older/non-production backend:
  current-shift list → authorized receipt history

Do not weaken these protections.

## Backend changes already deployed for Native recovery

CpIPOS Production now has:

`GET /api/pos/orders/{orderId}/status`

The endpoint:
- requires a real POS session
- requires sale permission
- scopes by tenant + branch
- returns canonical order, items, payments, branch/store receipt profile and
  cashier/seller information
- intentionally does not require the order's original shift to remain active

Cash order payment now persists:
- `cash_received`
- `change_amount`
- `payment_completed_at`
- `payment_completed_by`

Immediate receipt response also returns real cash/change.

No database schema migration was required.

## Real receipt history/search

Files:
- `app/src/main/java/com/cpipos/pos/next/ui/LiveReceiptHistorySheet.kt`
- `MobilePosSalePreview.kt`
- `WebPosClient.receiptHistory(...)`

Uses:
`GET /api/pos/receipts?mode=custom&from=...&to=...&status=...&page=...&page_size=...&q=...`

Current Native UI:
- default last 30 Bangkok calendar days
- page size 40
- searches bill/customer/external order using existing server API
- opens canonical receipt in Native receipt viewer
- uses existing Android PrintManager output path
- preserves server feature/permission gates; never bypass them

## Tests added in this checkpoint

Native:
- `LiveSaleAttemptTest.kt`
- `WebPosReceiptHistoryTest.kt`

Backend:
- `native-pos-reconcile-receipt.contract.test.ts`

These tests passed CI.

## Important current limitations / not yet done

The following are NOT complete and must not be described as production-ready:

1. Real bank-transfer verification/settlement in Native.
2. Camera barcode scanner.
3. Dine-in / table bill.
4. Close-shift UI/flow in Native.
5. Direct Bluetooth/LAN ESC/POS printer transport/routing.
6. Fully wired offline outbox/sync worker and conflict strategy.
7. Native receipt history date filters/pagination UI beyond the current
   last-30-day first-page surface.
8. Android instrumented/E2E tests.
9. Controlled real merchant/device/payment/printer acceptance test.

## Recommended next development order

Unless the user gives a different priority, continue in this order:

### A. Controlled on-device acceptance of the just-finished work
Test on the registered phone:
- exact cash sale
- over-tender cash sale (example: due 245, receive 300, change 55)
- verify same bill/order/payment in CpIPOS Web
- search that bill from Native history
- Android Save to PDF / printer
- network interruption after order creation
- network interruption after payment commits but before Android gets response
- Reconcile must recover the SAME bill and never make a duplicate order

Fix any defect found before adding another payment mode.

### B. Native close-shift + end-of-shift reconciliation
Reason: cash sale is now real and recoverable; the next core cashier lifecycle
gap is closing the live shift safely with totals/reconciliation.

### C. Camera barcode scanning
Wire a native camera scanner into the existing product search/cart without
changing the real server sale contract.

### D. Real bank-transfer payment
Do not simply mark transfer paid from the client.
Use an authoritative server-side verification/approval contract.

### E. Dine-in / table bill
Reuse the authoritative backend table/order semantics, not a separate local bill model.

## Local Windows / phone workflow

Use the user's existing local clone of this repository. Before pulling, inspect
local changes first; do NOT use `git reset --hard`.

Typical safe sequence:

```powershell
cd E:\cpipos-native-pos\cpipos-native-pos
git status
git fetch origin
git switch feature/native-auth-phase1
git pull --ff-only origin feature/native-auth-phase1
adb devices
.\scripts\android-device-preview.ps1 -DeviceSerial "10AEBE1N9C001RD" -Mirror
```

If the actual clone path/device serial differs, use the values shown by
`pwd` / `adb devices`. Never guess a new serial.

The script builds the debug APK, installs only
`com.cpipos.pos.next.dev`, launches it and can open scrcpy.

A GitHub CI success is NOT proof of a real USB/device/payment test. The next
chat must state clearly which tests were performed on GitHub/Vercel versus on
the user's physical handset.

## Production safety boundaries

- Do not embed Supabase service-role keys, DB passwords or PIN hashes in APK.
- Do not authenticate a cashier with only a publishable Supabase key.
- Do not directly mutate production DB from Native client code.
- Do not alter production Android app id/build while developing Native 2.0.
- Do not make destructive CpiPOS-001 schema/data changes without a separate
  reviewed migration task.
- Keep tenant, branch, permission, device/session, shift and idempotency
  enforcement server-authoritative.
- Do not invent a replacement bill for uncertain payment recovery.

## One-message prompt for the next chat

Paste or reference this:

> Continue CpIPOS Native POS 2.0 from
> `sstdevelopaminno/cpipos-native-pos` branch
> `feature/native-auth-phase1`. Read
> `docs/NEXT_CHAT_HANDOFF_2026-10-22.md` first and inspect current GitHub HEAD
> before changing code. Do not restart planning from zero.
> Real takeaway cash sale, Reconcile and real receipt history/search are already
> implemented. CpIPOS backend recovery support is already merged/deployed on
> production commit `6be1182`.
> First help me update/install the latest Native build on my Windows-connected
> Android phone and perform controlled real-device acceptance. Fix defects found.
> After acceptance, continue with Native close-shift + end-of-shift
> reconciliation unless I explicitly choose another next feature.

