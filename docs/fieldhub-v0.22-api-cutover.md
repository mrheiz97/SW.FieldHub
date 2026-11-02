# FieldHub v0.22 — internal identity cutover

This API release requires SQL migration `002_identity_mapping.sql` to have completed successfully **before** deployment. Keep a database backup.

## Behavior

- JWTs are validated against the configured OIDC issuer, audience and JWKS.
- `(verified issuer, subject)` maps to an internal `users.id`.
- Unknown valid identities are provisioned without memberships or permissions.
- Bootstrap returns the internal UUID in `user.id`.
- Workspace authorization uses `memberships.user_id`.
- Existing workspace member administration still accepts external subject strings in URLs and responses for compatibility with the local demo. A later API version will replace this with internal user IDs.
- Do not change `FIELDHUB_OIDC_ISSUER` yet. No automatic account merging by email is performed.

## Local test

```powershell
git pull --ff-only
docker compose --env-file .env -f deploy/docker/compose.yaml up -d --build api
```

Open `http://127.0.0.1:8000/dev/login`. Verify Ravens and permissions are unchanged. Compare the new `user.id` with `SELECT user_id FROM memberships WHERE workspace_id='ravens';` and ensure they match. Confirm unknown authenticated users have zero workspaces, and unauthorized workspace requests return 403.

The demo interface may still label members by Keycloak subject; this is a compatibility detail, not the authorization identity. This change has not been integration-tested in the user's environment.
