# Research: Per-Clinic Fees (068)

Traced at `main` `4c2a407` (Spring Boot 4.1).

## R1: Today's model and every touch point

- **Schema (V9).**
  - `appointment_type(id, doctor_profile_id, name, fee_override, created_at)`.
  - `doctor_default_fee(id, doctor_profile_id, amount, updated_at)`, unique on `doctor_profile_id`.
  - Neither table has a clinic.
- **Resolution.** `FeeResolutionService.resolve(doctorProfileId, appointmentTypeId)`: the type's `fee_override`, else `doctor_default_fee`, else `NoFeeConfiguredException`.
- **Callers.** Five booking services call it:
  - `PatientBookingService`, which also serves the waitlist claim;
  - `PatientQueueBookingService`, `StaffBookingService` and `StaffQueueBookingService`;
  - `FrontDeskWalkInService`.

  Each already has the booking's `clinicId` in scope.
- **Writes.** `BookingController` (`/api/v1/doctors/{doctorProfileId}/…`) handles `POST`/`GET` appointment-types (create carries an optional `feeOverride`), `PUT` appointment-types/{id} (rename) and `PUT` default-fee. `AppointmentTypeService.requireAuthorized` allows the doctor, or an active ClinicAdmin at **any** of the doctor's clinics. That is the SEC-03 gap.
- **Reads that expose prices.**
  - `AppointmentTypeResponse(id, doctorProfileId, name, feeOverride)` is returned by the staff list, by `GET /api/v1/patients/doctors/{id}/appointment-types` (no clinic context), and embedded in the patient open-slot and queue-session listings (which do have clinic context).
  - The frontend booking forms display these fees.
- **Readiness.** `DoctorBookingReadinessService.forClinic(clinicId)` already runs per clinic, but evaluates the doctor-wide fee tables.
- **Guards.**
  - The doctor delete guard (`DoctorVerificationService.deleteGuarded`) blocks on doctor-wide types and default fee.
  - The clinic permanent delete (`ClinicVerificationService`) deletes per-clinic housekeeping rows, such as booking-limit overrides.
- **Security.** `/api/v1/doctors/**` is `BookingSecurityConfig`'s staff-JWT, authenticated-only chain. `OpenApiConfig`'s description wrongly calls it "public", a doc bug that 068 fixes in passing. `/api/v1/clinics/**` is the staff chain, which is fail-closed after SEC-01: new paths must be allowlisted there, or they return 401.

## R2: Data model: new clinic-scoped price tables (chosen)

**Decision.** Two new tables:

| Table | Columns | Unique key |
|---|---|---|
| `clinic_doctor_fee` | `id`, `clinic_id` → clinic, `doctor_profile_id` → doctor_profile, `amount NUMERIC(10,2) NOT NULL CHECK (amount >= 0)`, `updated_at`, `updated_by_account_id` | `(clinic_id, doctor_profile_id)` |
| `clinic_appointment_type_price` | `id`, `clinic_id` → clinic, `appointment_type_id` → appointment_type, `amount NUMERIC(10,2) NOT NULL CHECK (amount >= 0)`, `updated_at`, `updated_by_account_id` | `(clinic_id, appointment_type_id)` |

The unique keys enforce FR-001 and FR-002 at the data layer, so two admins saving at once still leave at most one row (Constitution IV).

**Rationale.** Prices become clinic-scoped data, like every other tenant table. Appointment types stay doctor-level (spec assumption). The old columns stay in place: migrations are forward-only, and dropping is not needed for correctness. They simply stop being read (FR-012).

**Alternatives rejected.**
- *Add `clinic_id` to `appointment_type`*: this would duplicate type names per clinic, contradicting "types stay shared".
- *Add `clinic_id` to `doctor_default_fee` and reuse the table*: this requires dropping its unique constraint and re-keying, a riskier migration of a shipped table for no gain.
- *A single polymorphic price table*: it needs nullable columns and a CHECK constraint to say "default or type", and is harder to constrain uniquely.

## R3: Upgrade copy (FR-008)

**Decision.** A data migration after the table creation. Each insert matches only active Doctor role assignments, with `ra.active = true AND ra.role = 'Doctor'`.

| From | Inserted into `clinic_doctor_fee` / `clinic_appointment_type_price` | Join |
|---|---|---|
| `doctor_default_fee` | `(clinic_id, doctor_profile_id, amount)` | `role_assignment ra` on `ra.account_id = dp.account_id` |
| `appointment_type` where `fee_override IS NOT NULL` | `(clinic_id, appointment_type_id, fee_override)` | the same join |

**Equivalence.** For every (doctor, active clinic, type), resolution after the upgrade = clinic type price (copied from the override) else clinic default (copied from the doctor default) else block. That is exactly the old result, which gives SC-003.

**Test-first (Constitution I).** An integration test seeds the old shape, runs the copy, and asserts per-clinic equivalence. The column names are confirmed in V1: `role_assignment(account_id, clinic_id, role, active)`, where `role` is one of ClinicAdmin, Doctor or Operations.

## R4: API

**New endpoints, clinic-scoped, on the staff chain:**

| Method | Path | Body or result | Access |
|---|---|---|---|
| `GET` | `/api/v1/clinics/{clinicId}/doctors/{doctorProfileId}/fees` | `{defaultFee, types:[{appointmentTypeId, name, price}]}` | Read: the clinic's active staff (FR-006) |
| `PUT` | `/api/v1/clinics/{clinicId}/doctors/{doctorProfileId}/fees/default` | `{amount}` | Write: the clinic's active ClinicAdmin only (FR-005) |
| `PUT` | `/api/v1/clinics/{clinicId}/doctors/{doctorProfileId}/fees/appointment-types/{appointmentTypeId}` | `{amount}` | Write: as above |
| `DELETE` | `/api/v1/clinics/{clinicId}/doctors/{doctorProfileId}/fees/appointment-types/{appointmentTypeId}` | removes the type price, so the default applies | Write: as above |

- All of them are added to the staff chain's authenticated allowlist (SEC-01).
- **Write rule.** The doctor must be actively staffed at the clinic, or the request is refused. Writes use an upsert on the unique key.

**Retired doctor-wide price writes (FR-012).**
- `PUT /api/v1/doctors/{id}/default-fee` returns **410 Gone** with code `FEE_MOVED_TO_CLINIC`.
- `POST /appointment-types` with a non-null `feeOverride` returns **400** `FEE_MOVED_TO_CLINIC`.
- Create without a fee, rename and list keep their existing rules.
- *Rejected: silently ignoring a supplied fee*, because a caller would believe it was saved.

**Reads with clinic context.**
- The patient appointment-type list gains a clinic-scoped path, `GET /api/v1/patients/clinics/{clinicId}/doctors/{doctorProfileId}/appointment-types`, which returns each type with the clinic's effective `fee` (type price, else default, else null meaning not bookable).
- The old doctor-only path is kept for one release for compatibility, and returns names without fees.
- The listings embedded in clinic-scoped patient responses use the clinic's prices.
- `AppointmentTypeResponse` gains `fee` (the effective price at the clinic in context). `feeOverride` is removed from responses.

## R5: Readiness, guards and the UI

- **Readiness.** `forClinic(clinicId)` evaluates the clinic tables. A doctor is fee-ready if the clinic has a default for them, or a price for each of their types (FR-010).
- **Doctor delete guard.** It also blocks when any clinic price row references the doctor or the doctor's types.
- **Clinic permanent delete.** It also deletes that clinic's price rows (housekeeping, like the booking-limit override).
- **Frontend.**
  - The `AppointmentTypeConfigForm` admin screen edits the **current clinic's** prices through the new endpoints. Doctors see the screen read-only.
  - The booking forms read the clinic-scoped `fee`.

## R6: Test strategy (Constitution I)

**Red first.**
- Resolution unit tests with a `clinicId`.
- An integration test: two clinics, different prices, each booking locks its own clinic's price, and a change at A leaves B untouched (US1, SC-001).
- Authorization contract tests: the clinic's admin succeeds; another clinic's admin, the doctor, Operations staff and an unstaffed doctor are refused (US2, SC-002).
- A migration-copy integration test (US3, SC-003).
- A per-clinic readiness test (US4, SC-005).
- 410/400 tests for the retired doctor-wide writes.

**Updated tests.** The existing 017 fee tests and every booking test that seeds `doctor_default_fee` or `fee_override` move to seeding clinic prices. That is mechanical, but wide.
