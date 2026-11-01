# FieldHub API v1 — architecture decision (v0.17)

Status: **Draft contract; no backend is deployed or connected.**

## Boundaries
- Modular monolith, PostgreSQL, HTTPS REST JSON under `/v1`.
- Identity via external OIDC Authorization Code + PKCE; API validates issuer, audience, signature, expiry and scopes; never trust a client-supplied user ID.
- Workspace membership, permissions and resource ownership are looked up server-side on **every** protected request.
- `workspaceId` in paths is a resource selector, not proof of authorization. Cross-workspace access returns 404 (avoid enumeration) or 403 according to the endpoint policy.
- Server owns the authoritative UI configuration, form schema and permission decisions. The client only renders supported component types.
- All timestamps are RFC3339 UTC. IDs are opaque strings. API errors use a stable machine-readable code and requestId.

## Endpoints (planned)
| Method | Route | Permission | Purpose |
| --- | --- | --- | --- |
| GET | /v1/bootstrap | authenticated | Current user, permitted workspaces, supported UI configuration revisions |
| GET | /v1/workspaces/{workspaceId}/forms | forms.read | Visible form metadata and current published versions |
| GET | /v1/workspaces/{workspaceId}/forms/{formId} | forms.read | Immutable published form schema version |
| GET | /v1/workspaces/{workspaceId}/forms/{formId}/submissions | forms.submissions.read | Paginated, workspace-scoped submissions |
| POST | /v1/workspaces/{workspaceId}/forms/{formId}/submissions | forms.submissions.write | Create submission; validate schema server-side |
| PATCH | /v1/workspaces/{workspaceId}/forms/{formId}/submissions/{submissionId} | forms.submissions.write | Optimistic concurrency with revision / If-Match |
| POST | /v1/workspaces/{workspaceId}/sync | sync.write | Idempotent batch changes and per-item acknowledgements |

The paths and payloads are design contracts, **not working endpoints**.

## Security and privacy
- Never infer permissions from the presence of a menu item, `minRole` or a client-provided role.
- A membership can be revoked while a device is offline; restrict sensitive offline actions and revalidate on sync.
- Use scoped SQL queries and preferably PostgreSQL row-level security as defense in depth.
- Require bounded request sizes, pagination, rate limits, input validation, audit logs and retention/deletion policy before production.
- No secrets or access tokens in bootstrap or UI schema. Do not log personal form contents.
- Form schema versions are immutable once published; submissions reference the version used at capture time.
- Consent is **not** inferred from a checkbox field: record purpose, wording/version, time and evidence separately when required.
- Authenticate all endpoints, including sync; idempotency keys must be scoped to authenticated principal and workspace.

## Offline migration strategy
The current Room database (v7) and its local UUIDs remain untouched in v0.17.
Later introduce a local outbox, server revision, sync status and a mapping strategy for local IDs. Never replace local data on bootstrap. Conflict handling must be explicit: reject stale writes (409), fetch current revision, offer user resolution for sensitive edits. A successful HTTP response is not enough; each sync item needs an acknowledgement.

## Compatibility
- API major version in URL, schemaVersion in JSON, unknown noncritical properties ignored.
- Unsupported form field types must fail closed, not silently collect partial data.
- Bootstrap may hide modules for UX but does not authorize access.
- Define contract tests against OpenAPI before implementing the server.
