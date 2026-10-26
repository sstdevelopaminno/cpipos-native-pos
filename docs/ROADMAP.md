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
