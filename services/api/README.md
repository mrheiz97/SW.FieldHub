# FieldHub API — v0.18 development scaffold

**Not production ready.** This service is independent of the current offline Android app.

## Local start
From the repository root:

```bash
export FIELDHUB_DB_PASSWORD='choose-a-long-random-password'
export FIELDHUB_OIDC_ISSUER='https://your-oidc-provider.example'
export FIELDHUB_OIDC_AUDIENCE='fieldhub-api'
export FIELDHUB_OIDC_JWKS_URL='https://your-oidc-provider.example/.well-known/jwks.json'
docker compose -f deploy/docker/compose.yaml up --build -d
curl http://127.0.0.1:8000/health
```

Windows PowerShell: use `$env:FIELDHUB_DB_PASSWORD="..."` and likewise for other variables.

The API binds to **localhost only**. Use a properly secured HTTPS reverse proxy before remote access. Docker creates the initial SQL schema only on a fresh database volume; changing that SQL does not migrate existing volumes. Do not delete volumes containing important data.

## Implemented
- `GET /health` (liveness only, not a database readiness check)
- `GET /v1/bootstrap` using verified OIDC JWT and database memberships
- Workspace permission checks on form listing, form retrieval, submission listing
- Explicitly disabled writes and sync (HTTP 501), avoiding unsafe or incomplete persistence

## Not yet implemented
- OIDC login UI, client token acquisition, database provisioning UI
- Server-side validation and durable idempotency for writes, optimistic locking, real sync
- Production migrations, auditing, rate limits, pagination cursors, RLS, integration tests
- Form version publishing workflow and administration

**Never expose this prototype publicly or process real personal data yet.**
The API contract is in `packages/contracts/openapi.yaml`; the current implementation is a subset and may not fully match it.
