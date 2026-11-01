# FieldHub v0.6 – local training workflow

Implemented in Android branch feature/android-foundation:
- Room schema v3, migration 2->3 adds structured event timestamp and attendance without dropping existing records; migrations 1->2 and 2->3 remain registered.
- DatePickerDialog followed by TimePickerDialog in the event editor; event creation requires a structured date.
- Ravens Today screen prioritizes the next upcoming locally created training; demo training is fallback.
- Locally created Ravens events support "Dabei" and "Absagen"; attendance persists on the local device.
- Trainer Team tab now shows local training records and their device-local attendance status.
- Recruiting module from previous v0.6 commits is preserved.

Limitations:
- Attendance is ONE status per event on this device, not a real multi-member roster; cannot count teammates across devices until identity and backend synchronization are implemented.
- Legacy v0.5 event dates remain free text with startAt=0 until an editor selects a structured date.
- This work was not compiled or tested on Android hardware in the assistant environment.
- Do not store real recruiting personal data in this development prototype.

Manual QA:
1. Gradle sync and build; report first compiler error if any.
2. Verify existing v0.5 tasks and events and v0.6 recruiting contacts survive migration.
3. Ravens Trainer: create future event type Training with date/time picker.
4. Check Today and Team show the new event.
5. Tap Dabei, restart app, verify status; change to Absagen and verify.
6. Switch workspaces and verify event isolation.
