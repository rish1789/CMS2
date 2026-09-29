# Quickstart: Tables & Lists Consistency Pass

## Automated verification

1. `cd frontend && npx tsc -b` — zero type errors.
2. `npm run lint` — zero new lint errors.
3. `npx vitest run tests/day-sheet tests/staff-picker tests/doctor-picker tests/clinic-verification tests/doctor-verification tests/staff` (adjust to match actual test locations for the 7 touched files) — confirm zero regression to actual behavior.
4. `npm run test -- --run` — full suite, zero regression.

## Manual/live verification

5. Load Day Sheet for a clinic with more sessions than one page — confirm pagination now looks and behaves like `PendingClinicsList.tsx`'s (page indicator, record range, jump-to-page), and that the doctor filter still works unchanged.
6. Load the Staff roster and click each sortable column — confirm it now uses the shared sortable-header look, with sorting still working correctly in both directions.
7. Trigger an empty result on each of the 7 touched lists (e.g. search for a nonsense term) — confirm each shows the shared `EmptyState` component with its own correct wording.
8. Confirm `ClinicToolsDashboard.tsx`'s Today's Sessions and `PatientHubPage.tsx`'s Bookings/Consultations/Prescriptions/External Records tabs are visually consistent with the other lists' loading/empty states, with no change to their pagination/capping behavior (Today's Sessions still capped at 10 with its "+N more" link; Patient Hub's clinical tabs still show the Bookings page's own filtered subset, no new pager).
