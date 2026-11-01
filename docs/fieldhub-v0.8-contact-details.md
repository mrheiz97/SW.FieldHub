# FieldHub v0.8 – Recruiting-Kontaktakte

## Android implementation
- Tapping a recruiting contact opens a separate, scrollable contact detail view.
- Header shows contact name, recruiting status, phone, email and existing notes.
- Consent section distinguishes no consent, legacy consent without signature and consent with a saved signature.
- Saved touch signatures are rendered read-only; consent timestamp is displayed if present.
- A local chronological activity feed stores manually entered conversation notes with timestamps.
- Existing contact editor is still accessible from the detail screen.
- Deleting a contact also deletes its local activity entries.
- Room migration v4 to v5 creates recruiting_activities; prior migrations remain registered.

## Important limitations
- Local-only data: phone and tablet do not sync until a backend is implemented.
- Consent text is a prototype and needs organization-specific privacy disclosures before collecting real personal data.
- Signature is a drawing, not a qualified electronic signature; legacy consents may have no signature.
- No Android build or device test was performed here.

## Test on phone and tablet
1. Build Android debug app and install on both devices.
2. Open Recruiting, create contact with consent and signature.
3. Tap contact; check contact details, signature preview and navigation to Edit.
4. Add two timeline notes; restart and verify chronological persistence.
5. Edit contact; verify existing timeline entries remain.
6. Delete a contact; confirm detail disappears and activities are removed.
7. Test small phone screen, software keyboard, rotation, and tablet landscape.
8. Upgrade an existing installation to verify Room migrations preserve earlier contacts, tasks and events.
