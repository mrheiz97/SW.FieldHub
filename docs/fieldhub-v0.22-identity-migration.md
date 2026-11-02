# FieldHub v0.22 — Identity mapping, stage 1

**This is an additive database migration, NOT the API identity cutover.** The current API still authenticates and authorizes via the existing Keycloak subject. Membership rows additionally receive a stable internal `user_id`, and `external_identities` maps verified issuer + subject to that ID. Do not switch issuer yet.

## Before deploying

1. Back up PostgreSQL and test restoration. Do not apply this migration against production.
2. Stop API writes and keep the old API running until migration succeeds.
3. Run the migration with the **exact** issuer from the Keycloak access token's verified `iss` claim (current local development configuration: `http://localhost:8080/realms/fieldhub`).
4. Check membership counts, user mappings, and constraints before restarting the updated API.

Example from repository root (adjust compose service name if yours differs):

```powershell
git pull --ff-only
Get-Content -Raw services/api/sql/002_identity_mapping.sql | docker compose --env-file .env -f deploy/docker/compose.yaml exec -T db psql -X -v ON_ERROR_STOP=1 -1 -v legacy_issuer=http://localhost:8080/realms/fieldhub -U fieldhub -d fieldhub
```

Check:

```sql
SELECT count(*) AS memberships, count(user_id) AS mapped FROM memberships;
SELECT issuer, count(*) FROM external_identities GROUP BY issuer;
SELECT workspace_id, subject, user_id, permissions FROM memberships;
```

Then rebuild the API:

```powershell
docker compose --env-file .env -f deploy/docker/compose.yaml up -d --build api
```

## Compatibility and restrictions

- Existing bootstrap and membership permissions remain keyed by legacy subject until stage 2.
- The admin API can update/remove existing members. New membership creation requires an **existing** mapped external identity. The sign-in flow does **not yet** automatically register new identities; user provisioning is pending.
- Do not manually change issuer, do not link identities by email, and do not create duplicate users.
- The migration is intended to be applied **once** to the current schema; it is not an idempotent migration runner. Use a database backup for rollback.
- There is no automatic database migration at startup.
- The next stage must migrate all API authorization to internal user IDs and implement verified identity provisioning, with integration tests before deployment.
