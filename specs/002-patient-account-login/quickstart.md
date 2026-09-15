# Quickstart: Patient Account & Global Login

See [data-model.md](./data-model.md) and [contracts/patient-account.md](./contracts/patient-account.md) for full details.

## Prerequisites

- Backend running locally with PostgreSQL available and Flyway migrations applied (including 001's migration plus this feature's `V2__create_patient_account.sql`).

## Scenario 1 — Signup + login happy path

1. `POST /api/v1/patients/signup` with a valid email + policy-conforming password.
2. **Expect**: `201 Created`.
3. `POST /api/v1/patients/login` with the same email + password.
4. **Expect**: `200 OK` with a JWT.
5. Confirm the token's audience/scope claim is patient-only (not accepted by any staff-only endpoint — none exist yet in this feature to test directly, but the claim itself should be inspectable).

## Scenario 2 — Duplicate email within Patient Accounts

1. Sign up successfully with `email = "patient@example.com"`.
2. Attempt to sign up again with the same email.
3. **Expect**: `409 Conflict`, `EMAIL_ALREADY_IN_USE`. No second row created.

## Scenario 3 — Same email usable across identity systems

1. Sign up a Patient Account with `email = "shared@example.com"`.
2. Separately, register a Clinic (001) whose ClinicAdmin also uses `email = "shared@example.com"`.
3. **Expect**: both succeed independently — no cross-system conflict (FR-004).

## Scenario 4 — Password policy and mobile validation

1. Attempt signup with a weak password. **Expect**: `400`, `INVALID_PASSWORD` with `failedRules`.
2. Attempt signup with an invalid-format mobile number. **Expect**: `400`, `INVALID_MOBILE_NUMBER`.
3. Attempt signup with no mobile number at all. **Expect**: `201 Created`.

## Scenario 5 — Login failure doesn't leak information

1. Attempt login with an unregistered email. **Expect**: `401`, `INVALID_CREDENTIALS`.
2. Attempt login with a registered email and wrong password. **Expect**: `401`, `INVALID_CREDENTIALS` — same shape as step 1.
