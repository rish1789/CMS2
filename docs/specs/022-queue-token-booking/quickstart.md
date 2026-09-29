# Quickstart: Queue/Token Booking

Prerequisites: a running backend against Postgres (Testcontainers in tests, or a real
instance), a clinic with a verified doctor, a `QUEUE`-mode Schedule with a generated Session
for a future date (013), at least one `AppointmentType` for the doctor with a resolvable fee
(015), a staff token (Operations/ClinicAdmin) and a signed-up Patient Account (039) with a
login JWT.

## Scenario 1 — Staff books an existing patient into a queue

1. `POST /api/v1/clinics/{clinicId}/sessions/{sessionId}/queue-bookings` with
   `{"patientId": "...", "appointmentTypeId": "..."}` and a staff `Authorization` header.
2. **Expect**: `201`, `paymentStatus: "PENDING"`, a non-null `lockedFee`; the returned
   `slotId` is a Slot that did not exist before this call.

## Scenario 2 — Staff books a walk-in

1. Same endpoint with `{"patientName": "...", "patientPhone": "...", "appointmentTypeId": "..."}`
   and no `patientId`.
2. **Expect**: `201`; a new `Patient` record now exists at the clinic.

## Scenario 3 — Patient self-service books into a queue (first time at this clinic)

1. `POST /api/v1/patients/clinics/{clinicId}/sessions/{sessionId}/queue-bookings` with
   `{"patientName": "...", "appointmentTypeId": "..."}` and a Patient Account Bearer token.
2. **Expect**: `201`; a new (or phone-matched) `Patient` record is created and linked, with no
   separate step.

## Scenario 4 — No fee configured

1. Attempt either endpoint against an appointment type with neither a `feeOverride` nor a
   `DoctorDefaultFee`.
2. **Expect**: `409 NO_FEE_CONFIGURED`; no Slot, Patient, or Booking row created.

## Scenario 5 — Not a Queue/Token Session

1. Attempt either endpoint against a `FIXED_TIME`-mode Session.
2. **Expect**: `409 NOT_A_QUEUE_SESSION`.

## Scenario 6 — Concurrent bookings each get a distinct token

1. Issue several concurrent booking requests against the same active Queue Session.
2. **Expect**: every request succeeds `201`, each response's `tokenNumber` distinct and
   never-reused across the whole set.

## Scenario 7 — Unauthenticated / unauthorized access

1. Call the staff endpoint with no token, and with a Doctor's own token.
2. Call the patient endpoint with no token.
3. **Expect**: `401` for both no-token cases; `403` for the Doctor-token case.
