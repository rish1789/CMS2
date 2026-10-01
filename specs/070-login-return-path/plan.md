# Plan: 070 Return to the Selected Clinic After Login

**Stack:** frontend only (React 18, React Router, Vitest).

**Constitution:**
- Test-first: the pure validator gets unit tests, plus route-level journey tests.
- YAGNI: patient realm only, no HTTP-client migration (that is Phase 5A).

## Design

- **`src/features/patient-account/returnTo.ts`:**
  - `safeReturnPath(raw: unknown): string | null`, which implements rules 2 and 3;
  - `returnPathFrom(state, search): string | null`, which reads `state.from`, then `?returnTo`;
  - `withReturnTo(path, returnTo)`, which builds the link to signup or login.
- **`PatientLoginPage`:** computes the destination and navigates to it (or `/patient`) on success. It passes `returnTo` to `LoginForm` for the "Create an account" link.
- **`LoginForm`:** a new optional `returnTo` prop. When set, the signup link becomes `/patient/signup?returnTo=…`.
- **`SignupForm`:** a new optional `returnTo` prop. The success screen gets a "Log in" link to `/patient/login`, plus `?returnTo=…` when set.
- **`App.tsx`:** the signup route reads `?returnTo` and passes it through.

## Tests

- `tests/patient-account/returnTo.test.ts`: the validator matrix.
- `tests/routes/patient/LoginReturn.test.tsx`: guard → login → clinic route; direct login; unsafe `returnTo`; login → signup → login journey.
