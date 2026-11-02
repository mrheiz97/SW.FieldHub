# FieldHub v0.23 — User API

This increment introduces provider-neutral internal FieldHub user identifiers to the public user and workspace member API.

## Endpoints

- `GET /v1/users/me`: return authenticated user's internal UUID and display name.
- `PATCH /v1/users/me` with `{"displayName":"Example Name"}`: update own display name.
- `GET /v1/workspaces/{workspace_id}/users`: list members by internal UUID, name and permissions. Requires `workspace.manage`.
- `PUT /v1/workspaces/{workspace_id}/users/{target_id}` with `{"permissions":["forms.read"]}`: update an existing member. Requires `workspace.manage`.
- `DELETE /v1/workspaces/{workspace_id}/users/{target_id}`: remove an existing member. Requires `workspace.manage`.

Self-permission changes and self-removal are rejected. The legacy subject-based workspace admin routes still exist for the local development interface; do not expose them as the new public client contract. Member invitations and adding new members by internal ID are not yet implemented.

## Local test

Run `git pull --ff-only` and `docker compose --env-file .env -f deploy/docker/compose.yaml up -d --build api`. Log in via `http://127.0.0.1:8000/dev/login`, verify the Ravens workspace, then inspect the new endpoints in `/docs` using an authenticated bearer token. No new SQL migration is required after v0.22.

**Status:** Code committed; automated integration tests and user-environment tests still pending. Keep this development-only until permission and regression tests are complete.
