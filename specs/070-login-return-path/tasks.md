# Tasks: 070

- [X] T001 Write `tests/patient-account/returnTo.test.ts`. It covers accepted paths (`/patient/clinics/c1?doctorId=d1#x`, `/patient`, `/discover?q=x`) and rejected ones (`//evil.com`, `/\evil.com`, `https://evil.com`, `javascript:alert(1)`, `/patient/login`, `/patient/signup?x`, `/staff/x`, an empty string, a non-string, and a control character). Expect RED.
- [X] T002 Write `tests/routes/patient/LoginReturn.test.tsx`. It covers US1, US2 and US3 through real routes, with `loginPatient` and `signupPatient` mocked. Expect RED.
- [X] T003 Implement `returnTo.ts`, then the `PatientLoginPage`, `LoginForm`, `SignupForm` and `App` wiring. T001 and T002 should then be GREEN.
- [X] T004 Frontend gates: `tsc`, lint, the full Vitest suite and the build.
- [X] T005 Browser check (headless Chromium, synthetic data): discovery → login → clinic page keeps `doctorId`; direct login → `/patient`.
- [X] T006 Docs: progress row and audit status, then commit, push and open the PR.

## Observed results (2026-10-01, IST)

- **T001 and T002:** red. The `returnTo` module and `PatientSignupPage` did not exist.
- **T003:** green. Validator matrix: 5 accepted paths, 17 rejected forms, plus `returnPathFrom` and `withReturnTo` cases.
  - Route journeys: guard → login → the clinic with its `doctorId`; direct login → `/patient`; 4 unsafe `returnTo` values → `/patient`; login → signup → login → the clinic.
  - The existing `SignupForm` tests now render inside a Router, because the success screen has a real "Log in" link. Their assertions are unchanged.
- **T004:**
  - `tsc` is clean, and the build passes.
  - Lint exits 0 with the 24-warning baseline. A `no-control-regex` warning in the new code was removed by switching to a char-code check, not by suppressing it.
  - Vitest: **490/490 in 81 files**.
- **T005 (headless Chromium, real backend, fresh Postgres, synthetic data):**
  - `/discover` → click the doctor → `/patient/login` → log in → `/patient/clinics/aaaaaaaa-…?doctorId=dddddddd-…` (the selected clinic and doctor).
  - A direct login lands on `/patient`.
  - `returnTo=//evil.example.com/x` lands on `/patient`.
- **No backend change.**

