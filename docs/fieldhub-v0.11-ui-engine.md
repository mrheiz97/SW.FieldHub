# FieldHub v0.11 — Local JSON UI configuration

## Implemented
- Bundled configuration: `apps/android/src/main/assets/fieldhub_ui_v1.json`.
- Parser: `FieldHubUiConfig.kt`, schemaVersion 1 only, bounded lists, unique screen IDs, allowlisted screens.
- Navigation labels and order for the three existing demo workspaces now come from JSON.
- Existing Ravens demo-role restrictions still apply independently of configuration.
- Invalid/missing configuration falls back to the original hardcoded navigation.
- Self-hosted server URL settings from the previous release remain local and unchanged.

## Not implemented
- No remote JSON download or backend authentication.
- No server-side authorization or tenant isolation.
- No generic component renderer or dynamic forms yet.
- Existing workspace definitions and screen implementations remain in Kotlin.

## Manual QA
1. Build and launch the app; confirm all three demo workspaces open.
2. Check that menu labels and ordering match the JSON.
3. Change a label in the JSON and rebuild: the new label should appear.
4. In Ravens, switch between Player and Coach/Admin: Team and Recruiting must remain hidden from Player.
5. Verify existing recruiting, contacts, followups, training and tasks still open.
6. Check phone and tablet widths and confirm settings gear still opens.

## Next architecture step
Introduce a bounded, allowlisted component schema (metric, text, action, list) with data-source adapters, then a secure remote configuration fetch with versioning, HTTPS, authentication and offline cache. Never treat JSON visibility as backend authorization.
