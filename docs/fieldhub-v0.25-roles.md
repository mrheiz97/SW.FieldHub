# FieldHub v0.25 – workspace-specific custom roles

## Upgrade

1. Back up PostgreSQL.
2. Apply `services/api/sql/004_workspace_roles.sql` **once** to the existing database after migration 003.
3. Rebuild the API and refresh `/dev/login`.

The migration adds a unique constraint on `memberships(workspace_id,user_id)`. If legacy data contains duplicate memberships, clean them up before applying it. Do not rerun earlier migrations.

## API

- `GET /v1/workspaces/{workspace_id}/roles`
- `POST /v1/workspaces/{workspace_id}/roles` body `{"name":"Trainer","description":"Training","permissions":["forms.read"]}`
- `PUT /v1/workspaces/{workspace_id}/roles/{role_id}` same body
- `DELETE /v1/workspaces/{workspace_id}/roles/{role_id}` (only unassigned roles)
- `GET /v1/workspaces/{workspace_id}/users/{user_id}/roles`
- `PUT /v1/workspaces/{workspace_id}/users/{user_id}/roles` body `{"roleIds":["<role UUID>"]}`

Workspace management permission is required for these operations. Roles cannot grant permissions beyond those of the acting administrator. Self-assignment is blocked. Role edits that would expand permissions of the editing administrator's own assigned role are blocked. Roles belong to one workspace; assignments are scoped to members of that workspace.

## Compatibility and current limits

Existing direct member permissions and invitation permissions remain active. Effective permissions are the union of direct grants and assigned role grants. The bootstrap returns effective permissions; existing direct-grant editing and invitation creation continue to work.

This is an initial role-management implementation, **not** a completed security hardening pass. In particular, the API does not yet enforce a durable last-administrator invariant across all legacy direct-permission and role paths, and role-based invitations are not yet supported. Add integration tests for authorization, concurrent edits, membership deletion, role removal and privilege escalation before production.
