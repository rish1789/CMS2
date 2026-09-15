# Contract: External Record Reference Create / List (User Story 1)

Both endpoints require a valid staff bearer token (`/api/v1/clinics/**` chain) belonging to the
booking's own treating doctor — no ClinicAdmin, Operations, or peer-doctor override exists for
either.

## `POST /api/v1/clinics/{clinicId}/bookings/{bookingId}/external-record-references`

### Request

```json
{ "recordType": "Lab result", "sourceProvider": "City Diagnostics Lab", "recordDate": "2026-08-20", "summary": "CBC within normal limits." }
```

All four fields are required (research.md R4).

### Success Response — `201 Created`

```json
{
  "id": "uuid",
  "bookingId": "uuid",
  "doctorProfileId": "uuid",
  "recordType": "Lab result",
  "sourceProvider": "City Diagnostics Lab",
  "recordDate": "2026-08-20",
  "summary": "CBC within normal limits.",
  "createdAt": "2026-09-04T10:00:00Z"
}
```

### Error Responses

| Status | Condition | Body `error` |
|---|---|---|
| `401 Unauthorized` | Missing/invalid staff bearer token | — |
| `404 Not Found` | No `Booking` with `bookingId`, or it doesn't belong to `clinicId` | `BOOKING_NOT_FOUND` |
| `403 Forbidden` | Caller is not the booking's treating doctor | `FORBIDDEN` |

## `GET /api/v1/clinics/{clinicId}/bookings/{bookingId}/external-record-references`

Returns every External Record Reference for the booking (zero or more).

### Success Response — `200 OK`

```json
[
  { "id": "uuid", "bookingId": "uuid", "doctorProfileId": "uuid", "recordType": "Lab result", "sourceProvider": "City Diagnostics Lab", "recordDate": "2026-08-20", "summary": "CBC within normal limits.", "createdAt": "2026-09-04T10:00:00Z" }
]
```

An empty array is a valid, successful response — never a `404`.

### Error Responses

| Status | Condition | Body `error` |
|---|---|---|
| `401 Unauthorized` | Missing/invalid staff bearer token | — |
| `404 Not Found` | No `Booking` with `bookingId`, or it doesn't belong to `clinicId` | `BOOKING_NOT_FOUND` |
| `403 Forbidden` | Caller is not the booking's treating doctor | `FORBIDDEN` |

## Contract Invariants (traced to spec)

- No `PATCH`/`PUT`/`DELETE` method exists on this resource anywhere in this contract — genuinely
  absent (FR-002).
- No field on this resource represents a file/document attachment, anywhere (FR-006).
- A second, independent `POST` against a booking that already has a reference always succeeds
  (assuming valid input) — never rejected for that reason (FR-003).
- Every non-treating-doctor caller — including a ClinicAdmin or Operations staff at the same
  clinic — is rejected with `403 FORBIDDEN` for both endpoints, with no override (FR-004).
