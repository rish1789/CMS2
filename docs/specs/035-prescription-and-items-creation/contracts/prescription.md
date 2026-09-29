# Contract: Prescription Create / List (User Story 1)

Both endpoints require a valid staff bearer token (`/api/v1/clinics/**` chain) belonging to the
booking's own treating doctor — no ClinicAdmin, Operations, or peer-doctor override exists for
either.

## `POST /api/v1/clinics/{clinicId}/bookings/{bookingId}/prescriptions`

### Request

```json
{
  "items": [
    { "medicationName": "Amoxicillin", "dosage": "500mg", "frequency": "3x daily", "duration": "7 days", "instructions": "Take with food" }
  ]
}
```

`instructions` is optional per item; `items` must contain at least one entry.

### Success Response — `201 Created`

```json
{
  "id": "uuid",
  "bookingId": "uuid",
  "doctorProfileId": "uuid",
  "createdAt": "2026-09-04T10:00:00Z",
  "items": [
    { "id": "uuid", "medicationName": "Amoxicillin", "dosage": "500mg", "frequency": "3x daily", "duration": "7 days", "instructions": "Take with food" }
  ]
}
```

### Error Responses

| Status | Condition | Body `error` |
|---|---|---|
| `401 Unauthorized` | Missing/invalid staff bearer token | — |
| `404 Not Found` | No `Booking` with `bookingId`, or it doesn't belong to `clinicId` | `BOOKING_NOT_FOUND` |
| `403 Forbidden` | Caller is not the booking's treating doctor | `FORBIDDEN` |
| `400 Bad Request` | `items` is empty or missing | `PRESCRIPTION_ITEM_REQUIRED` |

## `GET /api/v1/clinics/{clinicId}/bookings/{bookingId}/prescriptions`

Returns every Prescription for the booking (zero or more — unlike 030's single-note get).

### Success Response — `200 OK`

```json
[
  { "id": "uuid", "bookingId": "uuid", "doctorProfileId": "uuid", "createdAt": "2026-09-04T10:00:00Z", "items": [ ... ] }
]
```

An empty array is a valid, successful response (no Prescriptions yet) — never a `404`.

### Error Responses

| Status | Condition | Body `error` |
|---|---|---|
| `401 Unauthorized` | Missing/invalid staff bearer token | — |
| `404 Not Found` | No `Booking` with `bookingId`, or it doesn't belong to `clinicId` | `BOOKING_NOT_FOUND` |
| `403 Forbidden` | Caller is not the booking's treating doctor | `FORBIDDEN` |

## Contract Invariants (traced to spec)

- No `PATCH`/`PUT`/`DELETE` method exists on this resource, or on any Item, anywhere in this
  contract — genuinely absent, not merely unauthorized (FR-002).
- A second, independent `POST` against a booking that already has a Prescription always succeeds
  (assuming valid input) — never rejected for that reason (FR-003).
- Every non-treating-doctor caller — including a ClinicAdmin or Operations staff at the same
  clinic — is rejected with `403 FORBIDDEN` for both endpoints, with no override (FR-004).
