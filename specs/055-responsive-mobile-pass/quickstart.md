# Quickstart: Responsive & Mobile Pass

## Automated verification

1. `cd frontend && npx tsc -b` — zero type errors.
2. `npm run lint` — zero new lint errors.
3. `npm run test -- --run` — full suite, zero regression (plus any new test added for a fixed defect).

## Manual/live verification (primary method — see research.md Decision 4)

Use the Browser pane's `resize_window` tool with presets `mobile` (~400px width equivalent) and `tablet` (~768px), and `desktop` to reset/compare, per this project's existing responsive-testing workflow.

4. **Shell regression check (FR-001)**: Load the Staff (`ClinicShell`) and Admin (`AdminShell`) shells at mobile width. Confirm the sidebar collapses to its hamburger-triggered drawer, the drawer opens/closes correctly, and item-selection auto-closes it — re-confirming 047's own already-live-verified behavior still holds after 048-051.
5. **Table screens (FR-002)**: At mobile width, load each of `DaySheet.tsx`, `SessionSlotsView.tsx`, `StaffPicker.tsx`, `DoctorPicker.tsx`, `PendingClinicsList.tsx`, `PendingDoctorsList.tsx`. For each: confirm the page body does not scroll horizontally (only the table's own container does), confirm every row action/sort-header is reachable via that contained scroll, confirm text is legible without zooming. Record pass/fail per screen; fix only what fails.
6. **Forms (FR-003, FR-004)**: At mobile width, load each of the 8 forms touched by 051 (`ScheduleForm`, staff `BookSlotForm`/`WalkInForm`, `OnboardStaffForm`, `ConsultationNoteForm`, patient `BookSlotForm`, `SignupForm`, `RegistrationForm`). Confirm every multi-column field grid renders single-column with no overflow. On `SignupForm`/patient `BookSlotForm` specifically, measure the submit button and a representative input against ~44×44 CSS pixels.
7. **Day sheet deep check (FR-005)**: At both mobile and tablet width, specifically exercise `DaySheet.tsx` (a session list with several rows) and `SessionSlotsView.tsx` (a session with several slots). Confirm both remain legible and every interactive element reachable — this is the backlog's named highest-risk pattern and gets individual attention beyond step 5's general table pass.
8. **Desktop regression (FR-006)**: For every screen this feature actually modifies (if any), compare its desktop-width (≥1024px) rendering before and after the change — confirm pixel-equivalent, zero unintended shift.
9. **Reduced motion (FR-007)**: Confirm `frontend/src/index.css`'s existing global `prefers-reduced-motion` override still applies (spot-check one transition, e.g. a nav link hover, with the OS/browser reduced-motion setting simulated) and that no new transition this feature adds bypasses it.
