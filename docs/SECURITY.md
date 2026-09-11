# CpIPOS Native 2.0 Security Rules

## Secrets

Never commit or embed any of the following in the Android application or Git repository:

- Supabase service-role key
- PostgreSQL connection string/password
- Android production signing private key/password
- Payment-provider private secrets
- Administrative API credentials

Development builds may use a Supabase publishable/anon client key only when the corresponding access is protected by reviewed RLS policies.

## Authorization

- Authentication is not authorization.
- Tenant and branch isolation must be enforced in the database/RPC layer.
- UI-hidden features are not security boundaries.
- Package/feature entitlements must be validated on authoritative operations.
- Manager approvals, voids, refunds and privileged configuration changes must be auditable.

## Critical operations

Sale completion, payment posting, stock deduction, refund, void, shift close, tax issuance and device-management commands must use reviewed transactional server-side contracts.

## Production protection

Until a production migration is explicitly approved:

- application id remains separate from `com.cpipos.pos`;
- production signing material is not introduced;
- no destructive migration is applied to CpiPOS-001;
- no release replaces Android 1.0.23;
- no Vercel production route is removed for the existing POS.
