# Tasks: Role-Aware Clinic Tools (073)

**Input**: [spec.md](spec.md), [plan.md](plan.md)

- [X] T001 Branch from the 072 branch, which is stacked on 071, so the audit and plan rows do not conflict.
- [X] T002 Write Vitest `RoleAwareTools.test.tsx`. Expect RED.
- [X] T003 Implement `clinicRoles.ts`, the `ClinicShell` roles and status, the `Sidebar` `activeRoles` prop, the dashboard tile filter and the `RequireClinicRole` gate. T002 should then be GREEN; the existing shell, sidebar and dashboard tests should stay green.
- [X] T004 Gates: `tsc`, lint (24 baseline), Vitest and build. Backend: the existing refusal tests for onboarding, protection, limit override and walk-in, run unchanged.
- [X] T005 Runtime check with synthetic accounts: a Doctor, an Operations user and a ClinicAdmin (who is also Doctor at a second clinic). Check the tiles, the direct URLs and a clinic switch. Do not submit any onboarding.
- [X] T006 Docs: a progress row, the audit finding 7 status, and the plan's 2R.5 status and repair-wave exit. Then commit, push and open the PR.

## Observed results (2026-10-01, IST)

- **T001:** branched from `claude/072-discovery-paging` @ `4e1d784`, which is stacked on 071.
- **T002 (red):** 11 of 19 failed. Doctor and Operations saw the admin tiles; every restricted URL rendered its form; there was no loading or failed state; the old clinic's admin tiles carried over on a clinic switch. The 8 that already passed are the ClinicAdmin-access, multi-role and Operations walk-in cases, which guard existing behaviour.
- **T003 (green):** 19 of 19. The existing `ClinicShell`, `Sidebar`, `ClinicToolsDashboard` and day-sheet tests run unchanged and pass (134 across those 15 files).
- **T004:**
  - Frontend: `tsc` clean; lint exit 0 with the 24 baseline warnings (none new); Vitest **522/522 in 84 files**; build OK.
  - Backend: no backend file changed. The onboarding, limit-override, protection-flag and walk-in tests (24 classes) pass 66/66 unchanged. The full backend suite was run on the 072 head, which has identical backend code: 1,146 passed, 0 failed, 0 skipped.
- **T005 (runtime):** fresh Postgres 16, the jar, Vite and headless Chromium. Synthetic staff: Doctor and Operations at Clinic Alpha; a ClinicAdmin at Alpha who is also a Doctor at Clinic Beta. No onboarding was submitted.
  - **Doctor @ Alpha:** the tiles exclude Onboard staff and Booking protection. `/onboard`, `/protection`, `/protection/limit-override` and `/walk-in` each show "… isn't available for your role at this clinic", with **0 form fields**.
  - **Operations @ Alpha:** the same tiles are hidden, and the three admin URLs are denied with 0 fields. `/walk-in` opens normally (4 fields).
  - **ClinicAdmin @ Alpha:** all 7 tiles show, and all four pages open.
  - **Same user @ Beta (Doctor there):** the admin tiles are hidden, and `/onboard` is denied. Admin rights do not carry across clinics.
- **Live-audit status:** finding 7 (dashboard exposes administrator-only tools) is **fixed**.
