# Quickstart: Patient Clinical Record Access

Validates the feature end-to-end against a running dev environment (`./dev.sh`, or `preview_start`
for `backend`/`frontend` per this repo's own tooling).

## Prerequisites

- Backend and frontend dev servers running.
- A verified clinic with a treating doctor and a completed booking for a self-service patient
  account (i.e. a `Patient` row linked to a real `PatientAccount`, not a walk-in-only patient).
- As that doctor (staff side), that booking already has a consultation note, at least one
  prescription, and at least one external record reference written for it (reuse the existing
  staff-side pages: `.../bookings/{bookingId}/consultation-note`, `/prescription`,
  `/external-record`).
- A second booking for the same patient with no clinical documentation at all, to exercise the
  "none exist" path.
- A booking belonging to a *different* patient account, to exercise the refusal path.

## Scenario 1 — Reading a documented visit

1. Sign in as the patient (self-service login) whose booking has all three record types.
2. Open "My Bookings" and confirm the documented visit shows an indicator that a record is
   available (FR-004), and the undocumented visit does not.
3. Open the documented visit. Confirm the consultation note's content, every prescription and its
   items, and every external record reference are all displayed.
4. Confirm no edit, delete, or correction control appears anywhere on the page (FR-006).
5. Confirm no download/export/print control appears anywhere (FR-010).

## Scenario 2 — A visit with nothing written yet

1. As the same patient, open the second booking (no clinical documentation).
2. Confirm the page clearly shows nothing exists for each of the three record types — not an
   error page, not a blank/broken layout (Acceptance Scenario 2, all three stories).

## Scenario 3 — Refusing another patient's visit

1. As the same patient, attempt to view the third booking (belongs to a different patient
   account) — e.g. by directly navigating to its URL if the id is known.
2. Confirm the request is refused (the same way an entirely invalid booking id would be) — no
   content from another patient ever appears (FR-005, SC-002).

## Scenario 4 — Purged records disappear identically for staff and patient

1. (If feasible without a real 3-year wait) directly invoke or fast-forward
   `RetentionPurgeService.purge()` for a booking old enough to be retention-eligible, per its
   existing 038 quickstart/test setup.
2. Confirm that booking's records are now absent both from the existing staff-side view and from
   this feature's patient-side view — no drift between the two (FR-009, research.md Decision 2).

## Automated coverage

- Backend: `cd backend && ./gradlew test --tests "com.cms.clinical.*" --tests "com.cms.booking.*"`
  (unit + contract; integration tests are written/compiled but require Docker, per this project's
  standing sandbox limitation).
- Frontend: `cd frontend && npx vitest run tests/patient-clinical-records tests/patient-bookings`
  (adjust paths to match the actual new/changed test files once written).
