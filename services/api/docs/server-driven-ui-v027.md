# FieldHub v0.27 — Server-driven UI contract

The API provides a **declarative** UI document at
`GET /v1/workspaces/{workspace_id}/ui`.

The response contains:
- `schemaVersion`: UI contract version, currently `1`
- `dashboard.layout`: currently `grid`
- `dashboard.cards`: authorized navigation shortcuts
- `navigation`: authorized entries from enabled modules
- `pages`: server-defined page layouts and component descriptors

Clients must implement a fixed allowlist of supported component types:
`navigation-card` and `resource-list`. Unknown types must be ignored or
displayed as unsupported, never interpreted as executable code. Routes are
workspace-relative; the client must scope all API requests to the selected
workspace. Never treat UI visibility as permission to call an API: the server
checks authorization independently.

This is a **static server-side schema registry** in v0.27, not a remotely
editable JSON editor. No arbitrary HTML, JavaScript, URLs, SQL, or client code
is accepted from workspaces. Further customization should use validated,
versioned schema updates and a migration plan.

Module activation is stored in `workspace_modules`; existing workspaces
default to enabled modules. The `027_workspace_modules.sql` migration must
be applied to existing PostgreSQL volumes before using this endpoint.

This endpoint returns UI metadata, not resource data. Resource-list clients
must fetch data separately using existing authorized API endpoints.
