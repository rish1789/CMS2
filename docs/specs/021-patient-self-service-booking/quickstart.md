# Quickstart: Patient Self-Service Fixed-Time Booking

Prerequisites: a running backend against Postgres (Testcontainers in tests, or a real
instance), a clinic with a verified doctor, a `FIXED_TIME` Schedule with generated Sessions/
Slots for a future date (012), at least one `AppointmentType` for the doctor with a resolvable
fee (015), and a signed-up Patient Account (039) with a login JWT.

## Scenario 1 — List open Slots

1. `POST /api/v1/patients/login` with the Patient Account's credentials → capture the JWT.
2. `GET /api/v1/patients/clinics/{clinicId}/slots` with `Authorization: Bearer <jwt>`.
3. **Expect**: `200`, an array containing every currently-OPEN Fixed-Time Slot at that clinic,
   each with `doctorName` and its doctor's `appointmentTypes`.

## Scenario 2 — Book as an already-linked patient

Precondition: this Patient Account already has a `Patient` record at the clinic (e.g. from a
prior booking).

1. Pick a `slotId` from Scenario 1's response.
2. `POST /api/v1/patients/clinics/{clinicId}/slots/{slotId}/book` with
   `{"patientName": "...", "appointmentTypeId": "..."}` and the same Bearer token.
3. **Expect**: `201`, `paymentStatus: "PENDING"`, a non-null `lockedFee`.
4. Re-run Scenario 1's list call — the booked Slot no longer appears.

## Scenario 3 — First-ever booking at a clinic auto-creates the Patient record

Precondition: this Patient Account has never booked at this clinic before, and no walk-in
`Patient` record at that clinic shares its phone number.

1. Repeat Scenario 2 against a different clinic.
2. **Expect**: `201`, exactly as Scenario 2 — a new `Patient` record is created and linked
   behind the scenes; no separate step is required.

## Scenario 4 — Race: two attempts on the same Slot

1. Concurrently issue two book requests (different Patient Accounts or the same one) against
   the same `slotId`.
2. **Expect**: exactly one `201`; the other `409 SLOT_ALREADY_BOOKED`.

## Scenario 5 — No fee configured

Precondition: an `AppointmentType`/doctor pairing with neither a `feeOverride` nor a
`DoctorDefaultFee` set.

1. Attempt to book against that appointment type.
2. **Expect**: `409 NO_FEE_CONFIGURED`; no `Patient` or `Booking` row created.

## Scenario 6 — Unauthenticated access

1. Call either endpoint with no `Authorization` header (or an expired/malformed token).
2. **Expect**: `401 UNAUTHORIZED` on both.
