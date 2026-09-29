# Quickstart: Application Shell Sidebar Navigation

## Automated verification

1. `cd frontend && npx tsc -b` — zero type errors.
2. `npm run lint` — zero new lint errors.
3. `npx vitest run tests/components/Sidebar.test.tsx tests/components/SidebarDrawer.test.tsx` — new component tests pass.
4. `npm run test -- --run` — full suite, zero regression, count increases by the new `Sidebar`/`SidebarDrawer` tests.

## Manual/live verification

5. Start the frontend dev server, sign in as a ClinicAdmin, open any page inside a clinic (e.g. the day sheet) — confirm the sidebar is visible, lists all 8 items including "Onboard staff", and highlights "Day sheet" as active.
6. Sign in as a Doctor or Operations staff member at the same clinic — confirm the sidebar shows the same items *except* "Onboard staff".
7. Click a different sidebar item (e.g. "Staff") — confirm direct navigation to that page, no dashboard round-trip, and the sidebar's active highlight moves to "Staff".
8. Resize to ~400px width — confirm the sidebar collapses to a hamburger-triggered off-canvas drawer; open it and confirm all items are still listed and reachable, and it closes on backdrop click, Escape, or selecting an item.
9. Repeat steps 5-8 for the Super Admin console (`AdminShell`) with its 4 items — no role filtering expected (single role).
10. Load `/staff` (the clinic picker, before selecting a clinic) and the Patient portal — confirm neither shows a sidebar (unchanged from before this feature).
11. Confirm `ClinicToolsDashboard`'s and `AdminDashboard`'s existing tile grids still render exactly as before (FR-011) — this feature only adds the sidebar alongside them.
