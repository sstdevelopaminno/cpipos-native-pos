# Supabase Connection Plan

Verified through the Supabase plugin on 2026-09-21:

- Project name: `CpiPOS-001`
- Project ref: `deejlitaivfnsbwqdugy`
- Region: `ap-south-1`
- Status: `ACTIVE_HEALTHY`
- Postgres: `17.6.1.121`

## Current app behavior

The Android app can read local Supabase configuration from `local.properties`:

```properties
CPIPOS_SUPABASE_URL=https://deejlitaivfnsbwqdugy.supabase.co
CPIPOS_SUPABASE_PUBLISHABLE_KEY=<publishable-key-only>
```

The APK must never include service-role keys, database passwords, or signing secrets.

## Development order

1. Keep the Mobile POS Preview flow mock/local until authentication is reviewed.
2. Define the trusted Store Code + employee PIN boundary as an RPC or Edge Function.
3. Return only the minimum session bootstrap context: tenant, employee, branch, device, package/features, and read-only product bootstrap.
4. Enable read-only catalog loading after authenticated tenant/branch scope is established.
5. Add Room/offline queue before any sale/payment/stock write path.
6. Add sale/payment mutations only through reviewed transactional server-side contracts.

## Production guardrails

- No direct client-side PIN hash reads.
- No Android client table writes for sale, payment, shift, stock, refund, void, or tax issuance.
- No CpiPOS-001 schema/RLS/data changes from mobile UI work.
- Any view or function exposed through Supabase must be reviewed for RLS and privilege behavior before use.
