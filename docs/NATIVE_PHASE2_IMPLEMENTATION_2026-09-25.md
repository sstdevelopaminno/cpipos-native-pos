# Native 2.0 implementation checkpoint — 2026-09-25

Owner approved continuation of (1) UI, (2) Supabase integration, (3) POS core.
This change is a concrete safe foundation, **not a claim that the production cashier is live**.

## What is delivered

- Mobile UI keeps the approved login / branch / counter / mode / product / cart /
  cash keypad / transfer preview. The screens explicitly identify mock mode even
  when a Supabase URL and publishable key are configured.
- `SaleDraft` uses integer satang, validates quantities, totals, cash received,
  change and duplicate products; a stable UUID is reused as the idempotency key.
- `NativeSaleOutbox` persists authenticated-session-scoped draft receipts locally
  in SQLite without PINs, JWTs or banking credentials. Duplicate IDs are rejected.
  Claim/confirm/retry/review use scoped compare-and-swap operations.
- `AuthenticatedCatalogBootstrap` checks a real Supabase Auth user against the verified employee before tenant/branch-scoped SELECTs. Product prices retain exact Postgres numeric text; the session is not enabled until trusted native login exists.
- `NativeSyncBoundary` validates session expiration and scope and defines a
  server-acknowledged sync contract. There is intentionally no production backend
  implementation or UI invocation while auth and transactional operations are absent.
- Unit tests exercise exact-money totals, change, invalid tender and duplicate items.

## CpiPOS-001 read-only schema inspection (no database mutation)

Existing public tables include `tenants`, `branches`, `products`, `users_profiles`
(`pin_hash` is present), `user_branch_roles`, `branch_login_policies`,
`branch_devices`, `pos_sessions`, `shifts`, `orders`, `order_items`,
`payments` and `stock_movements`. Product money is Postgres numeric.
RLS for `products`, `branches`, `orders` and `payments` uses
`app.has_branch_access`; a publishable key by itself is NOT login.

Existing `complete_pos_payment_tx` accepts an existing order_id and payment
lines. It is not an end-to-end Native offline sale ingestion contract. Do not
blindly use it to write sales or call unrelated INSERTs on the client.
No `cpipos-native-login-v1` function existed at inspection time.

## Required before Production wiring

1. Review the actual existing Web/IT login/session mechanism and create a
   server-verified Store Code + PIN endpoint with rate limiting, device and
   branch policy checks, JWT/Auth binding and no returned hash. Validate
   bad PIN, disabled user, wrong branch, cross-tenant user, locked device
   and expired session negative cases.
2. Hydrate `NativeBootstrap` only from that verified authenticated backend.
   Read catalog with the resulting user JWT and existing RLS; reconcile
   schema read model (products price, feature entitlement, package).
3. Review one atomic idempotent server-side Native sale transaction to resolve
   order/items/payments/stock exactly once and return the same receipt on retry.
   Bank transfer stays unverified without independent bank confirmation.
   In-flight app-crash recovery requires server reconciliation by request key.
4. Wire offline outbox from actual authenticated UI and implement pending,
   conflict and provisional-receipt UX, sync queue lifecycle and shift policy.
   Add migration tests and encrypted-at-rest treatment as appropriate for
   customer-identifying POS data before a pilot.
5. Execute Gradle tests/build; test on physical phone; do not replace Android
   1.0.23, deploy Vercel, or modify CpiPOS-001 schema/data without separate
   review of the precise production change.
