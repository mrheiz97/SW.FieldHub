# FieldHub v0.20 – Workspace administration (first backend slice)

All routes require a valid Keycloak access token (issuer, audience, signature and expiry are verified). Workspace administration additionally requires the server-side membership permission `workspace.manage` for the **same workspace**.

## Routes

- `GET /v1/workspaces/{workspace_id}/admin` – read workspace metadata
- `PATCH /v1/workspaces/{workspace_id}/admin` – update name: `{"name":"..."}`
- `GET /v1/workspaces/{workspace_id}/members` – list member subjects and permissions
- `PUT /v1/workspaces/{workspace_id}/members/{subject}` – create/update: `{"permissions":["forms.read"]}`
- `DELETE /v1/workspaces/{workspace_id}/members/{subject}` – remove membership

Allowed permissions: `forms.read`, `forms.submissions.read`, `forms.submissions.write`, `sync.write`, `workspace.manage`. Unknown/duplicate permissions are rejected. Self-modification/removal is blocked. The API cannot create a workspace or grant global admin rights.

## One-time local bootstrap

For the local demo only, grant the existing verified Keycloak subject workspace administration via psql:

```sql
UPDATE memberships
SET permissions = '["forms.read","forms.submissions.read","workspace.manage"]'::json
WHERE workspace_id = 'ravens'
  AND subject = '137d4fe7-d27f-4d92-8867-dd7ca25abf5d';
```

Confirm exactly one row was updated. This subject belongs to the demo session and should not be used for any other environment.

## Testing

Restart the API after pulling the branch:

```powershell
git pull --ff-only
docker compose --env-file .env -f deploy/docker/compose.yaml up -d --build api
```

Use the bearer token obtained via local OIDC login to call `GET /v1/workspaces/ravens/members`; the same token must return HTTP 403 for `GET /v1/workspaces/stumpfworks/members`. The local login UI does not yet expose token-based admin actions.

## Not yet implemented

- Browser admin UI, audit log, server-managed provisioning, Keycloak user directory lookup
- Transactional protection against concurrent last-admin removal
- Admin bootstrap without SQL, fine-grained roles, automated integration tests
- Production-grade authorization reviews and schema migrations

Do not expose this prototype publicly until those safeguards and tests are completed.
