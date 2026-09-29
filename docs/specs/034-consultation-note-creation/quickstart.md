# Quickstart: Consultation Note Creation

See [data-model.md](./data-model.md) and [contracts/consultation-note.md](./contracts/consultation-note.md).

## Prerequisites

- A verified Clinic with a staffed, verified Doctor, a confirmed Booking for that doctor
  (016/017/018), and a staff JWT for that doctor.

## Scenario 1 — Treating doctor creates and retrieves a note

1. As the booking's treating doctor, `POST /api/v1/clinics/{clinicId}/bookings/{bookingId}/consultation-notes`
   with `{"content": "Patient presented with..."}`. **Expect**: `201`.
2. `GET` the same path. **Expect**: `200`, identical content.

## Scenario 2 — Immutability and one-per-booking

1. Repeat the same `POST` from Scenario 1 against the same booking, same doctor. **Expect**:
   `409 CONSULTATION_NOTE_ALREADY_EXISTS`.
2. Confirm no `PATCH`/`PUT`/`DELETE` route exists for this resource at all.

## Scenario 3 — Authorization

1. As a different doctor at the same clinic, `POST` a note for the booking from Scenario 1.
   **Expect**: `403 FORBIDDEN`.
2. As a ClinicAdmin at the same clinic, `POST` a note for the same booking. **Expect**:
   `403 FORBIDDEN`.

## Scenario 4 — Unknown booking

1. `POST`/`GET` against a random, nonexistent `bookingId`. **Expect**: `404`.
