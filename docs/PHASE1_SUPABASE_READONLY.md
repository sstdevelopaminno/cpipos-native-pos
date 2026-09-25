# Phase 1 - CpiPOS-001 Read-only

This phase connects CpIPOS Native 2.0 to the existing CpiPOS-001 Supabase project without enabling any production write path.

## Production isolation rules

- Do not modify the CpIPOS 1.0.23 Android production application.
- Do not modify or redeploy the current Web POS on Vercel as part of this phase.
- Do not change the CpiPOS-001 schema, RLS policies, triggers, or production data in this phase.
- Never place a Supabase service-role key in the Android project.
- The Android app may use only the Supabase project URL plus a publishable key and authenticated end-user sessions.
- Database RLS remains the authoritative tenant/branch security boundary.

## Local configuration

`local.properties` is ignored by Git and is the only supported local credential source for this phase.

Append these values to the existing `local.properties` file on the development machine:

```properties
CPIPOS_SUPABASE_URL=https://deejlitaivfnsbwqdugy.supabase.co
CPIPOS_SUPABASE_PUBLISHABLE_KEY=<COPY_ACTIVE_PUBLISHABLE_KEY_FROM_SUPABASE_CONNECT>
```

Do not commit `local.properties`.

## Read-only data boundary

`ReadonlyCatalogRepository` exposes SELECT-only access for:

- active tenant metadata
- active branches scoped by tenant
- active products scoped by tenant + branch
- active subscription package metadata
- enabled tenant/branch feature subscriptions

It intentionally exposes no insert, update, delete, upsert, RPC mutation, order, payment, shift, or stock-movement method.

## Authentication sequencing

The repository is not a replacement for authentication. The next step is to map the existing CpIPOS store -> employee -> device -> session flow onto Supabase Auth/RLS without Vercel as the POS runtime intermediary.

No catalog query should be connected to production UI until an authenticated session and tenant/branch scope have been validated.
