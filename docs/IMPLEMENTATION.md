# FieldHub v0.2 — Implementierungsvertrag

Status: Entwurf zur Implementierung. MULTI + APPROVAL. PostgreSQL 16+, NestJS, TypeScript, OIDC, REST `/v1`.

## Grenzen und Sicherheitsannahmen

- Identität: `(issuer, subject)` aus einem validierten OIDC-Access-Token, nie E-Mail. OIDC issuer, audience, signature, expiration, nbf, allowed algorithms und JWKS-Rotation validieren. Authorization Code + PKCE für öffentliche Clients.
- Platform Admin ist ein separates, eng begrenztes Privileg, kein automatisch lesbarer Mandanten-Superuser. Administration ausschließlich über eigene, auditierte Services.
- Alle Mandantendaten werden mit `organization_id` gefiltert. Das API setzt den Mandantenkontext ausschließlich innerhalb einer DB-Transaktion. `SET LOCAL fieldhub.organization_id = '<uuid>'` nur nach Authentifizierung und Membership-Prüfung; SQL-Wert ausschließlich als validierte UUID aus vertrauenswürdigem Kontext, vorzugsweise `select set_config('fieldhub.organization_id',$1,true)`.
- `core.current_org()` und die RLS-Policies sind zusätzliche Schutzschicht. **Wichtig**: Das gezeigte RLS-Schema ist nicht allein ausreichend gegen kompromittierte Backend-Credentials. DB-Rollen ohne BYPASSRLS, getrennte Migration- und Runtime-Rollen, REVOKE für alle Schemas, explizite GRANTs, kein Tabellenbesitz für Runtime, keine dynamischen unkontrollierten SQL-Abfragen. Eigentümer-/Administrator-Tabellen dürfen nur über dedizierte Privileged-Services angesprochen werden.
- Fail closed: fehlende Mitgliedschaft, deaktivierte Organisation, deaktiviertes Modul, fehlender Grant oder Scope-Verstoß => Zugriff verweigern.
- Auflisten: niemals ungefiltert lesen und erst danach in JS filtern. Scope direkt in WHERE-Klausel einbauen. TEAM erfordert aktuelle aktive Teammitgliedschaft; OWN erfordert exakte Zuweisung; ORGANIZATION bleibt auf die aktive Organisation begrenzt.
- Rollen dürfen keine Berechtigungen verleihen, die der Zuweisende selbst nicht delegieren darf. Owner-Schutz, mindestens ein aktiver Owner, keine Selbstbeförderung. Für Änderung kritischer Rollen zusätzliche Reauth erwägen.
- Plattform-Anträge und deren PII sind nicht mandantenbezogen; separates Admin-Rechtemodell, Rate-Limits, Abuse-Schutz und auditierte Zugriffe.
- Datenverarbeitung DSGVO-konform konzipieren: Rechtsgrundlage, Information, Widerruf/Widerspruch, Aufbewahrung, Datenexport/Löschung, Minderjährige, kein ungefragter Werbeanruf, keine permanente Mitarbeiter-Ortung.

## NestJS-Modulbaum

```
AppModule
├── ConfigModule (zod environment validation)
├── DatabaseModule (PgPool, transaction-context service)
├── IdentityModule (OidcVerifier, PrincipalResolver, AuthGuard)
├── ApplicationsModule (ApplicationController, ReviewController, ApplicationService)
├── OrganizationsModule (OrganizationsController, BootstrapService, ProvisioningService)
├── MembershipsModule (MembershipController, InvitationService)
├── AuthorizationModule (PermissionsGuard, AuthorizationService, ScopeQueryBuilder)
├── TeamsModule (TeamsController, TeamService)
├── ModulesConfigModule (ModuleCatalog, OrganizationModuleService)
├── RecruitingModule (ContactsController, LeadsController, LeadService)
└── AuditModule (AuditWriter, AuditController)
```

Dependency direction: controllers -> services -> repositories; Authorization and Database shared infrastructure; no cross-domain table writes outside owning service except a documented provisioning transaction. Use DTO validation via class-validator or Zod consistently; whitelist properties and reject unknown keys.

## API DTOs

```ts
export type UUID = string; // @IsUUID('4') or z.string().uuid()
export type ApplicationStatus = 'submitted'|'in_review'|'needs_info'|'rejected'|'approved';
export type LeadStatus = 'new'|'contacted'|'qualified'|'appointment'|'won'|'lost';
export type Scope = 'own'|'team'|'organization';
export interface CreateOrganizationApplicationDto {
  name: string; // 2..150 chars
  slug: string; // /^[a-z0-9][a-z0-9-]{2,62}$/
  purpose: string; // 10..2000 chars
}
export interface ReviewApplicationDto {
  decision: 'approve'|'reject'|'needs_info'|'start_review';
  reason?: string; // required for reject / needs_info
  expectedVersion: number; // optimistic concurrency
}
export interface InviteMemberDto { email: string; roleKeys: string[]; }
export interface AssignRolesDto { roleIds: UUID[]; expectedVersion?: number; }
export interface CreateContactDto {
  displayName: string;
  email?: string;
  phone?: string;
  lawfulBasis?: string;
  contactPermission?: Record<string,unknown>;
  retentionUntil?: string;
}
export interface CreateLeadDto {
  contactId: UUID;
  assignedMembershipId?: UUID;
  assignedTeamId?: UUID;
  status?: LeadStatus;
}
export interface UpdateLeadDto {
  status?: LeadStatus;
  assignedMembershipId?: UUID|null;
  assignedTeamId?: UUID|null;
}
export interface ApiErrorDto { code: string; message: string; requestId: string; }
```

Validate all IDs and cross-tenant foreign keys. Limit pagination (default 25, max 100); stable sorting with UUID tiebreakers; opaque cursor. Use `Idempotency-Key` on submission, approval, invitation and creation where retries could duplicate side effects.

## REST routes and authorization

| Verb | Route | Authorization |
|---|---|---|
| GET | `/v1/me` | authenticated |
| GET | `/v1/me/organizations` | authenticated, active memberships only |
| POST | `/v1/organization-applications` | authenticated + rate limit |
| GET | `/v1/me/organization-applications` | authenticated, own applications |
| GET | `/v1/platform/organization-applications` | platform reviewer |
| POST | `/v1/platform/organization-applications/:id/decisions` | platform reviewer, optimistic version, idempotent |
| GET | `/v1/organizations/:orgId/bootstrap` | active membership + active organization |
| GET | `/v1/organizations/:orgId/members` | `members.read` |
| POST | `/v1/organizations/:orgId/invitations` | `members.invite` + delegable roles |
| PUT | `/v1/organizations/:orgId/members/:memberId/roles` | `members.roles.assign` + escalation protection |
| GET | `/v1/organizations/:orgId/roles` | `members.read` |
| GET | `/v1/organizations/:orgId/teams` | `teams.read` |
| POST | `/v1/organizations/:orgId/teams` | `teams.manage` |
| GET | `/v1/organizations/:orgId/contacts` | `recruiting.contacts.read` + scope |
| POST | `/v1/organizations/:orgId/contacts` | `recruiting.contacts.create` |
| GET | `/v1/organizations/:orgId/leads` | `recruiting.leads.read` + scope |
| POST | `/v1/organizations/:orgId/leads` | `recruiting.leads.create` |
| GET | `/v1/organizations/:orgId/leads/:id` | `recruiting.leads.read` + scope |
| PATCH | `/v1/organizations/:orgId/leads/:id` | `recruiting.leads.update` + scope; assignment changes require `recruiting.leads.assign` |
| GET | `/v1/organizations/:orgId/audit-events` | `audit.events.read` |

Use 401 for missing/invalid authentication; 403 for insufficient role; 404 for objects outside visible tenant/scope to avoid enumeration; 409 for version conflicts; 422 for validly parsed but semantically invalid input. Never reveal whether cross-tenant object exists.

## Transactional approval algorithm

1. Authenticate platform reviewer. Begin transaction at READ COMMITTED; lock application `SELECT ... FOR UPDATE`.
2. Check expectedVersion and state transition. If already approved, return existing organization id (idempotent); if conflicting prior decision, return 409.
3. Reserve unique slug via organizations unique constraint. Create organization.
4. Create active membership for applicant; insert standard roles and grants; assign owner role; enable baseline recruiting module (configurable policy).
5. Set application approved + organization_id + reviewer + reviewed_at + increment version; write immutable audit event in same transaction.
6. Commit. Publish notification through transactional outbox (to be added in migration 002) rather than sending inside transaction.
7. On any error rollback everything. Duplicate slug -> 409 with non-sensitive message. Never leave partially active organization.

## Role templates for every new organization

These are provisioning templates, not globally shared mutable roles. Insert one role row per organization, then insert grants from the catalog and assign owner.

| Role key | Example grants / scope |
|---|---|
| `owner` | organizations.read/organization, organizations.settings.update/organization, members.read/organization, members.invite/organization, members.roles.assign/organization, teams.read/organization, teams.manage/organization, modules.manage/organization, audit.events.read/organization; business data only if explicitly granted |
| `admin` | organizations.read/organization, members.read/organization, members.invite/organization, teams.read/organization, teams.manage/organization, modules.manage/organization |
| `manager` | organizations.read/organization, teams.read/organization, recruiting.leads.read/team, recruiting.leads.update/team, recruiting.leads.assign/team, analytics.reports.read/team |
| `recruiter` | recruiting.contacts.create/own, recruiting.contacts.read/team, recruiting.leads.create/own, recruiting.leads.read/team, recruiting.leads.update/own |
| `sales_agent` | sales.visits.create/own, recruiting.contacts.create/own, recruiting.leads.create/own, recruiting.leads.read/own, recruiting.leads.update/own |
| `viewer` | organizations.read/organization, recruiting.leads.read/team |

For create permissions, scope is the *allowed target assignment*, not the scope of a nonexistent resource. Define this explicitly in service policies. `contacts` currently lack assignment fields: until contact-scoped policies are implemented, grant contact read only through authorized lead joins, not an unscoped contacts listing. `owner` can delegate only roles in an explicit server-managed allowlist; cannot grant platform roles.

## Bootstrap response

```json
{
  "organization": {"id":"<uuid>","name":"Ravens Fürstenwalde","slug":"ravens"},
  "membership":{"id":"<uuid>","roles":["recruiter"]},
  "configVersion":1,
  "modules":[{"key":"recruiting","enabled":true,"navigation":[{"label":"Interessenten","route":"/recruiting/leads"}]}],
  "permissions":["recruiting.leads.read"],
  "scopes":{"recruiting.leads.read":"team"}
}
```

Bootstrap is a UI hint, **not** an authorization token. Modules and routes are from a server-side allowlisted catalog; never transmit arbitrary executable scripts.

## Security acceptance tests (mandatory)

1. No bearer token -> 401; wrong issuer, audience, alg, expired token -> 401.
2. User A member of org A cannot read org B contacts/leads by replacing path ID or object UUID -> 404/403, zero records.
3. User with revoked/suspended membership cannot access cached bootstrap or API even with previously valid token.
4. Team scope cannot read lead assigned to unrelated team; own scope cannot read teammate lead.
5. User with `recruiting.leads.update` but not `.assign` cannot change assigned IDs.
6. Role assignment to role of different org fails; self-promotion fails; removing final active owner fails.
7. Two reviewers approve same application concurrently -> exactly one organization, owner membership and audit decision.
8. Duplicate approval retries return same organization; approval after rejection -> 409.
9. Slug collision causes transaction rollback, no orphan organization/membership.
10. RLS with unset context returns zero tenant rows; with org A context cannot read org B.
11. Context on pooled connection does not leak between transactions (SET LOCAL reset).
12. SQL injection payloads in search, slug, IDs cannot alter queries.
13. Disabled module blocks API even when role grant exists.
14. Pagination/filtering cannot bypass scope; aggregate counts also scoped.
15. Audit captures actor, target, decision, timestamp without tokens, contact secrets or raw PII in metadata.
16. CSRF protection for cookie-based web sessions; mobile bearer tokens never stored in browser localStorage.
17. Rate limiting for organization requests, invitations, login-related endpoints; no account enumeration.
18. Reject stale expectedVersion (409) and ensure no partial writes.

## Release gate

- Integration tests against disposable PostgreSQL with runtime role and RLS enabled (not mocked).
- Test migrations up and clean bootstrap on fresh DB; lint, typecheck, SAST, dependency scan, secrets scan.
- Docker containers run non-root, healthchecks, resource limits, TLS at reverse proxy, database inaccessible from public network.
- Encrypted backups with tested restore; structured logs with redaction; audit retention policy.
- Security review before real personal data is imported.

## Known follow-up work before production

Migration 001 is a **foundation schema**, not the complete production schema. Add: invitations (hashed tokens, expiry, one-time use), transactional outbox, permission-grant versioning, durable role templates and grant seeding, application attachments, team assignment history, contact-channel permissions and consent provenance, campaign/visit/task tables, soft-deletion lifecycle, tenant-specific encryption strategy, and runtime DB role/GRANT migration. Implement all before claiming production readiness. PostgreSQL RLS alone cannot guarantee isolation if the runtime DB account can alter custom session settings or bypass application logic; combine with tested authorization and least-privilege database access.
