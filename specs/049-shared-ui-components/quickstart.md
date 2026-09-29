# Quickstart: Shared UI Component Library

## Automated verification

1. `cd frontend && npx tsc -b` — zero type errors.
2. `npm run lint` — zero new lint errors.
3. `npx vitest run tests/components tests/admin tests/patient tests/staff-picker` (adjust paths to match actual test locations for the 3 migrated dialogs + 2 migrated dashboards) — confirm zero regression.
4. `npm run test -- --run` — full suite, zero regression, count increases by the number of new component tests.

## Manual/live verification

5. Start the frontend dev server, open the Super Admin console's pending-clinics list, trigger a Reject — confirm the dialog opens/closes identically to before (backdrop click, Escape, focus trap) and a toast appears on success.
6. Open the staff console's staff roster, click a row to open `EmployeeModal` — confirm its side-tab layout still renders correctly on the shared `Modal` shell.
7. Load the Admin and Patient dashboards — confirm the tile grids render identically to before (visually unchanged, now backed by `Card`/`Button`).
