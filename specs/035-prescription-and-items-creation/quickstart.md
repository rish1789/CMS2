# Quickstart: Prescription + Items Creation

See [data-model.md](./data-model.md) and [contracts/prescription.md](./contracts/prescription.md).

## Prerequisites

- A verified Clinic with a staffed, verified Doctor, a confirmed Booking for that doctor
  (016/017/018), and a staff JWT for that doctor.

## Scenario 1 — Treating doctor creates a Prescription with multiple Items and lists it

1. As the booking's treating doctor,
   `POST /api/v1/clinics/{clinicId}/bookings/{bookingId}/prescriptions` with two Items.
   **Expect**: `201`, both Items present.
2. `GET` the same path. **Expect**: `200`, an array containing that one Prescription.

## Scenario 2 — Multiple independent Prescriptions per booking

1. Repeat the `POST` from Scenario 1 against the same booking, same doctor, different Items.
   **Expect**: `201` again (not `409` — unlike 030's Consultation Notes).
2. `GET` the same path. **Expect**: `200`, an array of two Prescriptions.

## Scenario 3 — Immutability and zero-Item rejection

1. Confirm no `PATCH`/`PUT`/`DELETE` route exists for this resource or any Item.
2. `POST` with `"items": []`. **Expect**: `400 PRESCRIPTION_ITEM_REQUIRED`.

## Scenario 4 — Authorization

1. As a different doctor at the same clinic, `POST`/`GET` for the booking from Scenario 1.
   **Expect**: `403 FORBIDDEN` for both.
2. As a ClinicAdmin or Operations staff member at the same clinic, `POST`/`GET` the same.
   **Expect**: `403 FORBIDDEN` for both.

## Scenario 5 — Unknown booking

1. `POST`/`GET` against a random, nonexistent `bookingId`. **Expect**: `404 BOOKING_NOT_FOUND`.

## Scenario 6 — 030 regression

1. Confirm 030's own Consultation Note create/get flows still behave identically after the
   shared `TreatingDoctorAuthorizationService` extraction (research.md R2).
