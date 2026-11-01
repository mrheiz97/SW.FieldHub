# FieldHub v0.5 – Local operations

## Included
- Room 2.6.1 and KSP configured for Kotlin 2.0.21.
- Local Room entities for events and tasks with workspace IDs.
- Event editor: create, view, edit, delete. Ravens player demo role is read-only.
- Task editor: create, edit, delete, priority and completion.
- Local persistence across restarts and workspace isolation at the query layer.
- Existing demo Today card, RSVP demonstration and trainer overview remain independent of the new Room data.

## Important limitations
- The earlier sample events in DemoEventRepository are not migrated to Room and are not shown in the new local Events list; the Today demo card continues using its sample event.
- Room data is not synchronized to any backend or other device.
- Role switching is demonstrative and cannot be used as a security boundary.
- Event date/time is currently a free-text label, not a normalized timestamp.
- No schema migration policy yet. Do not ship production data before adding migrations and tests.
- Existing SharedPreferences demo task states remain on device but are not migrated to Room.
- No CI/device build has been performed for this change.

## Next
1. Replace free-text dates with structured timestamps and date/time pickers.
2. Consolidate demo and user-created events in one repository, including RSVP.
3. Add explicit domain repositories, ViewModels, and migrations.
4. Add authorization, OIDC, backend sync, offline queue and conflict handling.
5. Write automated UI and database tests.
