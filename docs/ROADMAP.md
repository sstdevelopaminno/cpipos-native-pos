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
- [ ] Trusted authentication RPC/Edge Function contract
- [ ] Authenticated session model
- [ ] Device registration/read-only health contract

No sale/payment/stock mutations are enabled in this phase.

## Phase 2 - Offline foundation

- [ ] Room database
- [ ] Sync queue
- [ ] Idempotency model
- [ ] Connectivity state
- [ ] Initial product/config sync
- [ ] Conflict/audit strategy

## Phase 3 - Core POS

- [ ] Login and activation
- [ ] Open shift
- [ ] Product grid/search/barcode
- [ ] Cart
- [ ] Cash/transfer payment
- [ ] Transactional sale RPC
- [ ] Receipt rendering and printing
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
