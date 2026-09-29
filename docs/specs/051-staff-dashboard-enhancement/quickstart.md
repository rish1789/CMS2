# Quickstart: Staff Operational Dashboard Enhancement

## Automated verification

1. `cd backend && ./gradlew compileJava compileTestJava spotlessCheck` — clean compile, zero new formatting violations.
2. `cd backend && ./gradlew test --tests "*TodaySessionStats*"` — new unit + contract tests (unit tests execute in this sandbox; the contract test compiles and runs via `@WebMvcTest`, which does not require Testcontainers/Docker).
3. `cd frontend && npx tsc -b` — zero type errors.
4. `npm run lint` — zero new lint errors.
5. `npx vitest run tests/staff/ClinicToolsDashboard.test.tsx` — new dashboard tests pass.
6. `npm run test -- --run` — full suite, zero regression, count increases by the new tests on both sides.

## Manual/live verification

7. Start the app against a real backend with real clinic data. Load the Staff dashboard for a clinic with sessions scheduled today — confirm a "Today's sessions" list appears with real doctor names, time ranges, and slot-fill counts, ordered by start time, each linking into that session's Day Sheet detail view.
8. Load the dashboard for a clinic with zero sessions today — confirm the "Today's sessions" section shows a clear empty state, not an error.
9. Mark one of today's slots Completed and another No-show (via the existing Day Sheet slot-completion / no-show flows) — reload the dashboard and confirm the "Today's stats" tile's counts increase to match.
10. Confirm the 3 pre-existing tiles (inbox unclaimed count, day-sheet session count, waitlist waiting count) still show correct real counts, now restyled via 046's `Card`/`Badge`.
11. Confirm all 6 quick-action tiles (Day sheet, Doctors, Staff, Find a patient, Onboard staff, Join waitlist) still link to their real, working pages.
12. Confirm neither the Super Admin console nor the Patient dashboard shows any of this feature's new sections (FR-008) — unchanged from before.
