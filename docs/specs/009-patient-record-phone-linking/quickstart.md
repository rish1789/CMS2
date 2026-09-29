# Quickstart: Patient Record Auto-Creation & Phone-Based Linking

See [data-model.md](./data-model.md) and [contracts/patient-linking-service.md](./contracts/patient-linking-service.md).

This feature has no HTTP surface (see spec.md Assumptions) — every scenario below is exercised by calling `PatientLinkingService.findOrCreatePatient(...)` directly (e.g. from an integration test with a Spring context), not via `curl`/MockMvc.

## Prerequisites

- Backend running with PostgreSQL and Flyway migrations applied (through this feature's new `V5`).
- A registered clinic (001) and a signed-up Patient Account (039) to test against.

## Scenario 1 — First booking reuses a matching walk-in record

1. Insert a `Patient` row directly (simulating a prior walk-in): `clinic_id = A`, `phone = '9812345670'`, `patient_account_id = NULL`, some `name`.
2. Create a `PatientAccount` with `mobile = '9812345670'`.
3. Call `findOrCreatePatient(accountId, clinicIdA, "Ignored Name")`. **Expect**: returns the walk-in row's id (not a new one), and that row's `patient_account_id` is now set to `accountId`.

## Scenario 2 — First booking with no match creates a new record

1. Create a `PatientAccount` with a mobile number that matches no existing `Patient` at Clinic A.
2. Call `findOrCreatePatient(accountId, clinicIdA, "Jane Doe")`. **Expect**: a new `Patient` row exists, `clinic_id = A`, `patient_account_id = accountId`, `name = "Jane Doe"`, `phone` = the account's mobile.

## Scenario 3 — Repeated calls for the same account+clinic never duplicate

1. Following Scenario 2, call `findOrCreatePatient(accountId, clinicIdA, "Different Name")` again. **Expect**: returns the *same* `Patient` id as before; `name` is still `"Jane Doe"` (unchanged — the new `name` argument is ignored on this path); no second row exists.

## Scenario 4 — A phone-shared, already-linked record is protected

1. Following Scenario 1 (walk-in now linked to Account X), create a *second* `PatientAccount` Y with the same mobile `'9812345670'`.
2. Call `findOrCreatePatient(accountIdY, clinicIdA, "Y's Name")`. **Expect**: a *new*, separate `Patient` row is created for Y (not the one linked to X) — query the table directly and confirm two rows now exist for `clinic_id = A, phone = '9812345670'`, one linked to X and one to Y.

## Scenario 5 — Clinic scoping is independent

1. Following Scenario 2 (Account has a `Patient` at Clinic A), call `findOrCreatePatient(accountId, clinicIdB, "Jane Doe")` for a *different* clinic B where no record exists. **Expect**: a new `Patient` row is created at Clinic B — the account now has two independent `Patient` rows, one per clinic, neither referencing the other's visit history.

## Scenario 6 — Concurrent first bookings for the same account never duplicate

1. With no pre-existing `Patient` for `(accountId, clinicIdA)`, issue two concurrent calls to `findOrCreatePatient(accountId, clinicIdA, "Jane Doe")` from two threads.
2. **Expect**: both calls return successfully (neither throws), both return the same `Patient` id, and exactly one row exists in the table afterward — verified by querying the count directly.
