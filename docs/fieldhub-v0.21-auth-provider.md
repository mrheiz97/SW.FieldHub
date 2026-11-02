# FieldHub v0.21 — Provider layer

The authentication provider is configured through `FIELDHUB_AUTH_PROVIDER`, currently `keycloak` (default) or `oidc`. Both use the existing verified OIDC JWT validation. No arbitrary runtime code loading is supported.

Existing memberships continue to use the external `sub` value. Do not change issuers or use multiple identity providers with this schema.

## Migration plan

1. Introduce internal users with stable UUIDs and external identity links keyed by `(issuer, subject)`.
2. Migrate memberships in a transactional, reversible database migration.
3. Link existing accounts only after explicit verification; never match by email alone.
4. Add issuer-specific identity validation and audit logging, then implement SW.Identity when ready.
5. Run automated authentication, permission, and migration tests before production use.

## Local smoke test

Keep the existing Keycloak environment values and optionally set `FIELDHUB_AUTH_PROVIDER=keycloak`. Rebuild the API and log in through `http://127.0.0.1:8000/dev/login`. Confirm Ravens bootstrap and workspace administration work. Check that missing or invalid bearer tokens are rejected and nonmember workspaces return 403.

This is a development increment; integration tests and the internal user mapping are not yet implemented.
