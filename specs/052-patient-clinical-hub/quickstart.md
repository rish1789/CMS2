# Quickstart: Patient Context & Clinical History Hub

## Automated verification

1. `cd backend && gradle compileJava compileTestJava spotlessCheck` — clean compile, zero new formatting violations.
2. `cd backend && gradle test --tests "*PatientBookingHistory*"` — new unit + contract tests pass.
3. `cd frontend && npx tsc -b && npm run lint` — zero type errors, zero new lint errors.
4. `cd frontend && npx vitest run tests/patient-search` — new/updated dashboard tests pass.
5. `cd frontend && npm run test -- --run` — full suite, zero regression, count increases by the new tests on both sides.
6. `cd backend && gradle test --tests "*ConsultationNote*" --tests "*Prescription*" --tests "*ExternalRecord*" --tests "*Anonymiz*"` — confirm 030/031/032/033's own existing tests pass unmodified (SC-005).

## Manual/live verification

7. Search for a patient with existing bookings, consultation notes, prescriptions, and external records — confirm a link/click now opens their hub (where none existed before).
8. On the hub, confirm Overview shows the patient's real name/phone, Bookings lists their real bookings at this clinic (newest first), and Consultations/Prescriptions/External Records each list the same bookings as clickable rows.
9. Click a Consultations row for a booking with an existing note — confirm it opens the real, existing consultation-note page showing that note (or the create form if none exists yet), unchanged from before this feature.
10. As a doctor who is *not* the treating doctor for a given booking, follow that booking's Consultations row — confirm the existing `ForbiddenException` behavior (030) still triggers, unchanged.
11. Anonymize a patient via the existing flow, then open their hub — confirm Overview shows the current scrubbed state (not a stale value).
12. Confirm no edit/delete control exists anywhere on the hub or the pages it links into, for an existing consultation note or prescription.
13. Confirm a `PatientAccount` with bookings at a second clinic (if available) does not leak that clinic's bookings into this clinic's hub view.
