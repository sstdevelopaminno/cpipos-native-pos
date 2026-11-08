**Release/update note (8 Nov 2026):** Customer Stable = Native Mobile 2.1.0. Candidate includes self-update under More and safe-install gating. Next signed APK must have **versionCode above 2010000** and the same approved certificate. Do not publish before kitchen, order, notes/stock, receipt and upgrade acceptance. Central backend PR #424 merged; PR #425 pending tests. 

## Update 2026-11-08 — current P0 gates (not completed)

1. Native plain-item notes and configured modifier popup UI are proposed for Android; still require server gate and real device acceptance.
2. Native dine-in kitchen acknowledgement + idempotent repair path proposed; validate every KDS zone, alert and round against the CpIPOS Web/IT backend with test data.
3. Existing custom table lines require immutable selected-option snapshots, not flattening by product ID. Keep edit fail-closed until that parity is complete.
4. Signed APK release must use central CpIPOS release authority and undergo physical printer/MDM/billing tests.
5. See [QA evidence matrix](NATIVE_DINE_IN_KITCHEN_ITEM_DETAILS_QA_2026-11-08.md). Historical checkboxes above are not production acceptance indicators.

# CpIPOS Native 2.0 Roadmap

## Phase 0 - Foundation

- [x] Separate GitHub repository
- [x] Separate development application id
- [x] Native Android + Compose baseline
- [x] Architecture and security boundaries
- [x] Generate/commit Gradle wrapper from a verified local JDK 17 + Gradle 9.5 environment
- [x] CI build for debug APK

## Phase 1 - Read-only platform connection

- [x] Supabase client abstraction for CpiPOS-001
- [x] Native authentication UI and guarded gateway contract
- [x] Tenant and branch read model
- [x] Package/feature entitlement read model
- [x] Product read model
- [x] Trusted server authentication contract via existing CpIPOS Web POS session APIs
- [x] Authenticated tenant/branch/employee/device/session model
- [x] Registered device selection/status contract

Production mutations are performed only through the existing trusted Web POS server boundary; direct APK database mutation remains prohibited.

## Phase 2 - Offline foundation

- [ ] Room database
- [ ] Sync queue
- [x] Idempotency model + durable uncertain-sale journal
- [ ] Connectivity state
- [ ] Initial product/config sync
- [ ] Conflict/audit strategy

## Phase 3 - Core POS

- [x] Login and activation
- [x] Open shift
- [ ] Product grid/search/barcode (grid/search/images done; camera barcode pending)
- [x] Cart
- [ ] Cash/transfer payment (cash live; transfer verification pending)
- [x] Server-authoritative order/payment transaction path + reconcile journal
- [x] Receipt rendering + Android PrintManager/PDF (direct ESC/POS pending)
- [ ] Close shift

## Phase 4 - Business modules

- [ ] Inventory
- [ ] Reports
- [ ] Multi-branch workflows
- [ ] Kitchen/table/QR where entitled
- [ ] Customer display
- [ ] Tax invoice workflows

## Phase 5 - Device/IT integration

- [ ] Device heartbeat
- [ ] Diagnostics
- [ ] Approved device commands
- [ ] Managed updater
- [ ] Android Device Owner/MDM compatibility

## Phase 6 - 2.0 migration

- [ ] Pilot stores
- [ ] Production data compatibility tests
- [ ] Signing/update-path verification from 1.0.23
- [ ] Release candidate
- [ ] Controlled 2.0 rollout
- [ ] Web POS retirement plan only after successful rollout
