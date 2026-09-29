# Quickstart: Visual Design & Copy Quality Pass

## Automated verification

1. `cd frontend && npx tsc -b` — zero type errors.
2. `npm run lint` — zero new lint errors.
3. `npx vitest run tests/routes/HomePage.test.tsx tests/staff/StaffShell.test.tsx tests/staff/ClinicShell.test.tsx` — new tests pass.
4. `npx vitest run tests/staff/ClinicToolsDashboard.test.tsx` — existing test still passes unregressed (it renders inside the restructured shells).
5. `npm run test -- --run` — full suite, zero regression, count increases by the new tests above.

## Manual/live verification

6. Start the frontend dev server (`http://localhost:5173`), load `/` — confirm the six confirmed elements (spec.md FR-009: title, subheading, both role-card bodies, "Running a clinic?" card, "Super Admin?" line) show the new, screen-specific copy, and no other text on the page changed.
7. Sign in as staff, open any `/staff/clinics/:id/*` page (e.g. the Clinic dashboard, Onboard staff) at a normal desktop width (≥1280px) — confirm the sidebar sits flush against the left edge of the browser window (no empty margin to its left), and the content area fills the remaining width up to its own reading-width cap, with no large symmetric empty gutters on both sides of a floating centered column.
8. Scroll a long content page — confirm the sidebar stays in view (sticky) rather than scrolling away, and its nav items/active-highlight behavior is unchanged from before this feature.
9. Resize to ~400px width — confirm `SidebarDrawer`'s existing hamburger/off-canvas behavior is completely unchanged (opens, lists all items, closes on backdrop/Escape/selection) — this feature must not alter mobile behavior (research.md Decision 3).
10. Load the Patient portal and the Super Admin console — confirm both are pixel-identical to before this feature (FR-007; `PatientShell`/`AdminShell` are out of scope).
11. Compare `OnboardStaffForm.tsx`'s field grid before/after — confirm any dropped inner width wrapper did not break its 2-column field layout or its Doctor-details conditional section.
