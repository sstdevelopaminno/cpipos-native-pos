# Native Mobile: dine-in kitchen routing and per-item details — QA and release gates

Date: 2026-11-08. Scope: Android Native POS + existing CpIPOS Web/IT backend.
Status: **code proposed; not physical-device accepted; no new customer APK published.**

## Verified architecture
- Order entry on the handset uses the authenticated branch/device/shift session and existing CpIPOS POS API.
- POS central database `CpiPOS-001` shows active order-item kitchen routing triggers (inspected read-only on 2026-11-08).
- Existing `/api/pos/orders/{orderId}/kitchen-dispatch` routes/repairs kitchen tickets and deduplicates previously queued work.
- `GET /api/pos/recipe-products?strict_native=1` provides branch-scoped modifier groups/options and base recipe ingredients.
- `POST /api/pos/sales` validates Native selections server-side **only** when the separately managed `NATIVE_ATOMIC_MENU_SALES_ENABLED` release gate is active. Keep that gate off until migration/deployment/integration acceptance.
- The existing Web/IT kitchen display must never be replaced with a second mobile-side ticket ledger.

## Change in this branch
- Each live takeaway and dine-in product card has an independent **รายละเอียด** action. A normal tap still quick-adds an unconfigured item.
- The shared details sheet accepts notes for plain products and modifier selections for configured ones. Both remain distinct by `cart_line_id` for checkout.
- A successful table save verifies the server order ID, order quantities and discount. An acknowledged sale is never resubmitted to recover kitchen delivery.
- After acknowledgement, the app requests a deterministic central kitchen dispatch/repair event. If delivery cannot be verified, retain the table and show **ตรวจและส่งครัวอีกครั้ง**. The retry never performs a sale/payment again.
- This changes neither payment settlement, release signature, tenant permission, inventory API, nor the authoritative Web/IT backend.

## Mandatory controlled test cases (test merchant only)
| Scenario | Expected |
|---|---|
| Plain takeaway item | Tap card adds; details button opens note editor; note stays on one checkout line |
| Plain dine-in item | Note saves on the exact item; table does not close; kitchen sees note |
| Required modifier | Cannot add until required choice selected; branch-scoped configured options match Web |
| Optional ingredient change | Resulting modifier selection and recipe stock deltas match Web; no arbitrary client price |
| Same product, different options/notes | Distinct order lines, kitchen ticket rows, receipt lines, stock movements and history |
| Regular and special price | Server-authoritative total; discount/tax/receipts match |
| Table first order | Exactly one order and correct kitchen ticket(s) / configured zones |
| Table append/additional round | Kitchen sees only new items with correct table/round; no duplicate earlier items |
| Offline/timeout after POST commit | Original sale UUID/order ID reconciled; no duplicate order, payment or stock deduction |
| Kitchen dispatch temporary failure | Sale remains acknowledged; retry routes/repairs same order without a duplicate sale |
| Kitchen auto-send disabled / missing kitchen zone | No misleading success; warning and required store policy review |
| Printer Bluetooth, USB, LAN where supported | 58/80mm physical output, notes and modifiers readable, duplicate print guard works |
| Package expired, shift closed, disabled MDM device | Both sale and kitchen mutation remain blocked as required |
| Thai / English / Lao and Android 8.1 / 15 | Visible/usable labels; notes input accepts Thai and Lao; no layout overlap |

## Security and release blockers
1. **Backend gate:** Vercel `cp-ipos-web` production variable inventory was inspected on 2026-11-08 and `NATIVE_ATOMIC_MENU_SALES_ENABLED` was **not configured**, so configured selections are expected to fail closed until reviewed activation. SQL migrations and kitchen triggers were observed in production; this is not a substitute for sale/stock/kitchen integration testing.
2. **Existing customized table bills:** Android currently blocks full-cart edits when the server already has custom metadata or special prices. Its legacy flattened snapshot cannot safely replay `native_selection` on each persisted line. Keep fail-closed until line-by-line round-trip is implemented and accepted.
3. **Base ingredient changes:** The Native UI currently **lists** `baseRecipe` but only modifier groups are selectable. Do not simulate adding/removing recipe ingredients via a free-text note because this would corrupt stock deductions. Build a structured backend contract first if ingredient delta controls are required.
4. **Settings permission:** The existing `/api/pos/settings/order-kitchen` requires `settings:view`. The related proposed CpIPOS backend branch adds `/api/pos/native-mobile/kitchen-policy` (sale:create, branch scoped; effective auto-send boolean only). Deploy the reviewed backend endpoint before installing this Native candidate.
5. **Release authority:** Use the existing `CpIPOS/.github/workflows/build-native-mobile-release.yml` with explicit reviewed source SHA and existing signing certificate. The separate Native repo release workflow lacks its own four secrets. No production APK is approved on this branch.
6. **Physical acceptance:** Use Android 8.1/15, staff and manager test accounts, a registered device, real 58/80mm printers and QA-only data. Attach redacted evidence and complete issue #20 before marking green.
7. **Printing:** Validate the 2026-11-07 canonical receipt HTML patch on a real printer and against CpIPOS Web/Windows.
8. **Backend visibility:** Kitchen queue route, zone configuration, order-kitchen policy and KDS popup/sound must be checked against test events. Do not use real customer transactions for QA.

## Release checklist
- [ ] Native Foundation Build CI green
- [ ] Android API 27 and 35 Compose instrumented smoke CI green
- [ ] Scoped kitchen policy endpoint deployed to CpIPOS Web before Native candidate
- [ ] Native selection backend integration + modifier stock test green
- [ ] Dine-in new/add/notes to kitchen receipt KDS physically observed
- [ ] All Issue #20 acceptance evidence collected
- [ ] All Issue #30 contracts supported with no silent omission
- [ ] Stable signing cert, APK SHA-256, package, versionCode verified
- [ ] Owner approval, central signed release workflow, website link update
