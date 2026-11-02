# FieldHub v0.24 — Workspace invitations

## API

All endpoints require a valid OIDC bearer token. Invitation management requires workspace.manage.

- POST /v1/workspaces/{workspace_id}/invitations: JSON `{"permissions":["forms.read"],"expiresInHours":48}`. Returns a one-time plaintext token; share privately.
- GET /v1/workspaces/{workspace_id}/invitations: recent invitation metadata, never plaintext tokens.
- DELETE /v1/workspaces/{workspace_id}/invitations/{invitation_id}: revoke unused invitation.
- POST /v1/invitations/claim: JSON `{"token":"..."}`. Must be signed in first; atomically claims invitation and grants workspace membership.

Invitations are single-use, expire in 1–168 hours, and cannot grant rights the creator does not hold. The current schema still requires a legacy subject field in memberships; claiming uses the authenticated user's mapped identity for the configured issuer. Do not send invitation secrets to logging, analytics or URLs.

## Required database migration

**Back up PostgreSQL first.** The v0.24 API requires `services/api/sql/003_workspace_invitations.sql` before rebuilding the API.

From the repository root in PowerShell:

```powershell
Get-Content -Raw services/api/sql/003_workspace_invitations.sql | docker compose --env-file .env -f deploy/docker/compose.yaml exec -T db psql -X -v ON_ERROR_STOP=1 -1 -U fieldhub -d fieldhub
docker compose --env-file .env -f deploy/docker/compose.yaml up -d --build api
```

Run migration once only. If an error occurs, do not rerun blindly. The local development page does not yet have invitation controls. Use /docs with a bearer token for API exploration. Automated integration tests, rate limiting and end-to-end acceptance tests are still pending; do not expose this workflow publicly yet.
