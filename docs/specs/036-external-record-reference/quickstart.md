# Quickstart: External Record Reference

See [data-model.md](./data-model.md) and [contracts/external-record-reference.md](./contracts/external-record-reference.md).

## Prerequisites

- A verified Clinic with a staffed, verified Doctor, a confirmed Booking for that doctor
  (016/017/018), and a staff JWT for that doctor.

## Scenario 1 — Treating doctor creates and lists a reference

1. As the booking's treating doctor,
   `POST /api/v1/clinics/{clinicId}/bookings/{bookingId}/external-record-references` with all
   four fields. **Expect**: `201`.
2. `GET` the same path. **Expect**: `200`, an array containing that one reference.

## Scenario 2 — Multiple independent references per booking

1. Repeat the `POST` from Scenario 1 against the same booking, same doctor. **Expect**: `201`
   again.
2. `GET` the same path. **Expect**: `200`, an array of two references.

## Scenario 3 — Immutability and no file field

1. Confirm no `PATCH`/`PUT`/`DELETE` route exists for this resource.
2. Confirm the create request/response shape has no field representing a file/document
   attachment anywhere.

## Scenario 4 — Authorization

1. As a different doctor at the same clinic, `POST`/`GET` for the booking from Scenario 1.
   **Expect**: `403 FORBIDDEN` for both.
2. As a ClinicAdmin or Operations staff member at the same clinic, `POST`/`GET` the same.
   **Expect**: `403 FORBIDDEN` for both.

## Scenario 5 — Unknown booking

1. `POST`/`GET` against a random, nonexistent `bookingId`. **Expect**: `404 BOOKING_NOT_FOUND`.

## Scenario 6 — Frontend

1. As a treating doctor, create an External Record Reference and see existing ones read-only
   (`frontend/src/features/external-record-references/ExternalRecordReferenceForm.tsx`).
