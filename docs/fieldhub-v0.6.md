# FieldHub v0.6 – Recruiting foundation

## Implemented
- Recruiting contacts in Room: name, phone, email, notes, status, consent flag, consent timestamp and creation timestamp.
- Workspace-filtered Flow DAO; Room v1-to-v2 migration preserves v0.5 events/tasks.
- Search, status filtering, create/edit/delete with deletion confirmation.
- Navigation: Ravens recruiting visible in trainer/admin demo roles; other demo workspaces show the module.
- Data remains entirely local. Demo role visibility is NOT authorization.

## Not implemented yet
- Calendar picker, normalized event dates and integrated event attendance workflow. Those remain planned, not shipped in this commit.
- Follow-up reminders, assigned recruiters, audit history, CSV export, remote synchronization.
- Encryption at rest, authentication, real role authorization, deletion/retention policy, consent evidence beyond a checkbox, GDPR production readiness.

## Privacy and security
Do not use with real personal contact details until access control, secure storage, backup policy, data minimization, consent/legal-basis workflow, retention and deletion, and auditability are designed and tested. Do not enter sensitive information into free-text notes. Avoid using contact data outside the purpose for which it was collected.

## Testing
1. Pull feature/android-foundation and Gradle sync.
2. On Ravens switch to Trainer and open Kontakte; on Field Sales Demo open Kontakte directly.
3. Create a test contact, edit status, filter and search.
4. Restart app and verify persistence and workspace isolation.
5. Verify v0.5 tasks/events survive the Room migration.
6. Test deletion confirmation.

No successful device build or automated test has been confirmed for this version.
