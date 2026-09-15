# Quickstart: Staff Onboarding (Direct-Hire)

See [data-model.md](./data-model.md) and [contracts/staff-onboarding.md](./contracts/staff-onboarding.md).

## Prerequisites

- A registered, active ClinicAdmin (001) to log in as.

## Scenario 1 — ClinicAdmin logs in, onboards Operations staff

1. `POST /api/v1/staff/login` with the ClinicAdmin's email/password. **Expect**: `200`, a `STAFF` JWT.
2. `POST /api/v1/clinics/{clinicId}/staff` with that token, `role=Operations`. **Expect**: `201`, a `staffCode` and `temporaryPassword`, `doctorProfileId: null`.
3. Log in as the new hire with the returned credentials via `POST /api/v1/staff/login`. **Expect**: `200`.

## Scenario 2 — Onboard a Doctor

1. Same as above but `role=Doctor` with specialization/license/experience. **Expect**: `201`, non-null `doctorProfileId`; querying the DB confirms `license_verified=false`.

## Scenario 3 — Role restriction enforced server-side

1. `POST /api/v1/clinics/{clinicId}/staff` with `role=ClinicAdmin`. **Expect**: `400 INVALID_ROLE`, no rows created.

## Scenario 4 — Authorization

1. Call the onboarding endpoint with no token. **Expect**: `401`.
2. Call it with a valid Doctor/Operations staff token (not ClinicAdmin). **Expect**: `403`.

## Scenario 5 — Atomicity

1. Force a failure after Account creation but before Doctor Profile creation (test-only fault injection). **Expect**: `500`, zero rows left behind across all three tables.
