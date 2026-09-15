# Quickstart: Doctor Profile Auto-Creation & License Verification Queue

See [data-model.md](./data-model.md), [contracts/doctor-verification.md](./contracts/doctor-verification.md), and [contracts/staff-onboarding-extension.md](./contracts/staff-onboarding-extension.md).

## Prerequisites

- Backend running with PostgreSQL and Flyway migrations applied (through `V4`), plus `admin.super-admin.username`/`password` configured.
- A verified clinic (001 + 002/003) and an authenticated ClinicAdmin staff token (004) for onboarding calls.

## Scenario 1 — New Doctor onboarded, appears in the pending-verification queue

1. `POST /api/v1/clinics/{clinicId}/staff` with `role=Doctor`, a fresh license number. **Expect**: `201`, `existingAccount=false`, `temporaryPassword` present, `doctorProfileId` present.
2. `GET /api/v1/admin/doctors?verified=false` with Super Admin Basic Auth. **Expect**: the new Doctor Profile appears with its specialization/licenseNumber/experienceYears.
3. `POST /api/v1/admin/doctors/{doctorProfileId}/verify`. **Expect**: `200`, `licenseVerified: true`.
4. Repeat `GET /api/v1/admin/doctors?verified=false`. **Expect**: the doctor no longer appears.

## Scenario 2 — Same doctor onboarded at a second clinic reuses the profile

1. Using the same license number and specialization (any casing/whitespace) as Scenario 1's doctor, `POST /api/v1/clinics/{otherClinicId}/staff` with `role=Doctor` from a *different* clinic's ClinicAdmin. **Expect**: `201`, `existingAccount=true`, `temporaryPassword: null`, `staffCode` equal to the original doctor's staff code, `doctorProfileId` equal to the original profile's ID.
2. Query the database directly. **Expect**: exactly one `doctor_profile` row and one `account` row for this doctor, but two `role_assignment` rows (one per clinic).
3. If the doctor from Scenario 1 was verified in step 3 above, confirm `doctor_profile.license_verified` is still `true` after this reuse — unaffected by the new Role Assignment (FR-002c).

## Scenario 3 — Specialization mismatch on a matching license number is rejected

1. `POST /api/v1/clinics/{clinicId}/staff` with `role=Doctor`, the same license number as an existing profile, but a different `specialization` (e.g. the existing profile is "ENT", submit "Radiology"). **Expect**: `409 SPECIALIZATION_MISMATCH`.
2. Query the database. **Expect**: no new Account, Doctor Profile, or Role Assignment row from this call.

## Scenario 4 — Discovery eligibility requires all three conditions

1. Take a Doctor Profile with `licenseVerified=false` (fresh from Scenario 1 before verification), `visible=true` (default), clinic `verified=true`. Query discovery eligibility directly at the data layer. **Expect**: not eligible.
2. Verify the license (as in Scenario 1 step 3). **Expect**: now eligible (visible=true and clinic verified=true already held).
3. Manually flip `visible=false` on the row (no endpoint writes this yet — see spec Assumptions). **Expect**: not eligible again, despite `licenseVerified=true`.

## Scenario 5 — Unauthorized access rejected

1. Call `GET /api/v1/admin/doctors?verified=false` with no credentials. **Expect**: `401`.
2. Call the same endpoint with a valid staff Account's credentials (e.g. a ClinicAdmin). **Expect**: `401` — a staff Account is never accepted here.

## Scenario 6 — Idempotent verify

1. `POST /api/v1/admin/doctors/{doctorProfileId}/verify` on an already-verified profile. **Expect**: `200`, `licenseVerified: true`, no error, no duplicate side effects.
