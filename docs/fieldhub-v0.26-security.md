# FieldHub v0.26 – Security hardening (first pass)

Branch: `feature/android-foundation`

## Findings and changes

- **Privilege delegation:** Both member APIs now refuse direct grants the acting administrator does not effectively hold. Role creation and assignment already validate effective permissions; invitation creation now does too.
- **Last administrator:** Mutations to direct permissions, member deletion, role edits and role assignment now flush and verify that the workspace still has at least one user with effective `workspace.manage`. Invalid changes return HTTP 409.
- **Concurrency:** These mutations lock the workspace row with `SELECT ... FOR UPDATE` to serialize security-sensitive changes per workspace. PostgreSQL is required for this guarantee; SQLite is used only for basic functional tests.
- **Role isolation:** Role assignments and effective permissions are always scoped by workspace; new regression tests cover isolation and delegation.
- **Compatibility:** No new SQL migration. Existing direct grants and assigned roles continue to combine additively.

## Run

From the repository root:

```powershell
git pull --ff-only
docker compose --env-file .env -f deploy/docker/compose.yaml up -d --build api
docker compose --env-file .env -f deploy/docker/compose.yaml exec api pytest -q
```

If the image does not include test files, run `cd services/api; python -m pytest -q` in a Python environment with `requirements.txt` installed.

## Not yet verified

These tests have been committed but **have not been executed in the user's PostgreSQL environment**. They use SQLite for quick endpoint regression checks; PostgreSQL integration tests must additionally exercise simultaneous updates and transaction ordering. A full production security review also requires review of Keycloak configuration, authentication provider routing, OIDC token lifecycle, rate limiting for invitation claims, logging, dependency vulnerability scanning, TLS, CORS/CSRF policies, backup/restore and deployment configuration.

Do not treat this version as audited or production-hardened yet.
