# CpIPOS Native POS 2.0

Native Android POS application for CUTTING POINT TECH CO., LTD.

## Project status

This repository is the clean development line for **CpIPOS Native POS 2.x**.

- Production Android `1.0.23` remains in the existing `sstdevelopaminno/CpIPOS` repository and must not be modified by work in this repository.
- The current CpIPOS Web application and Vercel deployment remain in service during the migration period.
- `CpiPOS-001` remains the authoritative production database. Native 2.0 must not perform destructive schema changes or production data migrations without a separately reviewed migration plan.
- `CpiPOS-002` is not a dependency of this project.

## Target architecture

- Kotlin
- Jetpack Compose
- Native Android UI (no WebView runtime)
- Room / SQLite local persistence for offline-first operation
- Supabase `CpiPOS-001` for cloud data, authentication, realtime and controlled RPC/Edge Function operations
- Multi-tenant: multiple business owners, each with one or more branches
- Existing subscription/package entitlements remain authoritative
- Existing CpIPOS IT / device-management concepts remain supported

## Application identity

Development builds use a separate application id so they can coexist with the production 1.0.23 app:

`com.cpipos.pos.next`

The production application id `com.cpipos.pos` is reserved for the controlled 2.0 production migration and must not be used by development builds.

## Safety rules

1. Do not copy the legacy WebView POS runtime into this repository.
2. Do not store Supabase service-role keys, database passwords, Android signing keys or other secrets in Git.
3. Do not modify the production `CpIPOS` repository as part of Native 2.0 work unless explicitly planned as a compatibility/release change.
4. Do not modify production `CpiPOS-001` schema or data from ordinary application development work.
5. Security boundaries must be enforced by Supabase Auth, RLS and transactional server-side database operations, not only by UI state.

See `docs/ARCHITECTURE.md`, `docs/SECURITY.md`, `docs/ROADMAP.md`, and `docs/DEVELOPMENT_STATUS.md` on the development branch for the detailed foundation.
