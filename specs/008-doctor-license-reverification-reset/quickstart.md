# Quickstart: Doctor License Edit Triggers Re-Verification Reset

See [data-model.md](./data-model.md) and [contracts/doctor-profile-edit.md](./contracts/doctor-profile-edit.md).

## Prerequisites

- Backend running with PostgreSQL and Flyway migrations applied (no new migration in this feature — through 007's `V4` is sufficient).
- A Doctor Profile already onboarded (004/007) to edit.

## Scenario 1 — Editing the license number resets a verified profile

1. Onboard a Doctor (004/007) and verify their license (`POST /api/v1/admin/doctors/{id}/verify`, from 007). **Expect**: `licenseVerified: true`.
2. `PATCH /api/v1/admin/doctors/{id}` with the same specialization/experience/visible but a **different** `licenseNumber`. **Expect**: `200`, response `licenseVerified: false`.
3. Query the pending-verification list (`GET /api/v1/admin/doctors?verified=false`). **Expect**: this profile now appears.

## Scenario 2 — Editing any other field never resets verification

1. Starting from a verified profile (as in Scenario 1 step 1), `PATCH /api/v1/admin/doctors/{id}` with the **same** `licenseNumber` but a changed `experienceYears`. **Expect**: `200`, response `licenseVerified: true` (unchanged).
2. Repeat with only `visible` changed. **Expect**: same — `licenseVerified: true` (unchanged), `visible` reflects the new value.

## Scenario 3 — Duplicate license number on edit is rejected

1. Onboard two separate Doctors with two different license numbers (A and B).
2. `PATCH` doctor A's profile, setting `licenseNumber` to doctor B's license number. **Expect**: `409 DUPLICATE_LICENSE_NUMBER`.
3. Query both profiles. **Expect**: neither changed — doctor A still has license number A.

## Scenario 4 — Reset does not touch bookings

1. Verify a doctor's license, then create a future booking for them (016/017, once built — until then, verify via code inspection that `edit()` never calls into the booking/cascade modules).
2. Edit the license number (triggering the reset, as in Scenario 1).
3. Query the doctor's bookings. **Expect**: unchanged — no cancellation, no flag.

## Scenario 5 — Unauthorized access rejected

1. `PATCH /api/v1/admin/doctors/{id}` with no credentials. **Expect**: `401`.
2. Repeat with a valid staff Account's credentials (e.g. a ClinicAdmin). **Expect**: `401` — a staff Account is never accepted here.
