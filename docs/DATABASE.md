# CpiPOS-001 Integration Contract

`CpiPOS-001` is the authoritative production database for CpIPOS Native 2.0.

## Existing production concepts to preserve

Native 2.0 must integrate with the existing multi-tenant and subscription model rather than inventing a parallel production data model. Relevant current concepts include tenants, branches, user/branch roles, products, shifts, orders, payments, stock movements, subscription packages/features, branch devices, POS sessions and device-health/command records.

## Integration policy

1. Start read-only.
2. Do not rename or delete production tables during application bootstrap.
3. Do not bulk-rewrite customer data.
4. Add database changes only through reviewed migrations.
5. Every exposed table/RPC must be reviewed for RLS/authorization.
6. Critical multi-table writes must be transactional and idempotent.
7. Service-role access belongs only in controlled server-side execution environments, never in the Android APK.

## Initial Native 2.0 access sequence

```text
Authenticate
  -> resolve user profile
  -> resolve authorized tenant(s)
  -> resolve authorized branch(es)
  -> resolve subscription/features
  -> sync branch configuration/products
  -> open local POS context
```

No production write path is enabled as part of the foundation bootstrap.
