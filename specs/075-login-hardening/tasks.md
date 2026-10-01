# Tasks: Login Hardening and Deactivated-Staff Cutoff (075)

**Input**: [spec.md](spec.md), [plan.md](plan.md)

- [ ] T001 Branch from the decision-record branch (#41).
- [ ] T002 Write `LoginAttemptGuardTest` (unit) and `LoginHardeningTest` (integration). Expect RED.
- [ ] T003 Implement the V44 migration, `LoginAttemptGuard`, the exceptions and handlers, both login services, `StaffSessionPolicy`, the filter wiring, and the slice test config. T002 should then be GREEN.
- [ ] T004 Update the existing tests that pinned the old split codes. Run the identity, patient and contract suites.
- [ ] T005 Write Vitest tests for both login forms. Expect RED; implement until GREEN.
- [ ] T006 Gates: the full backend suite with `spotlessCheck`; `tsc`, lint, Vitest and build.
- [ ] T007 Runtime check: generic errors, lockout and its expiry (with a shortened window), and a deactivated staff session cut off in the browser.
- [ ] T008 Docs: a progress row, the audit backlog B-05 and B-07 statuses, and the plan. Then commit, push and open the PR.
