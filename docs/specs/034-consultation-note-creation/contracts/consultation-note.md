# Contract: Consultation Note Create / Get (User Story 1)

Both endpoints require a valid staff bearer token (`/api/v1/clinics/**` chain) belonging to the
booking's own treating doctor — no ClinicAdmin or peer-doctor override exists for either.

## `POST /api/v1/clinics/{clinicId}/bookings/{bookingId}/consultation-notes`

### Request

```json
{ "content": "string" }
```

### Success Response — `201 Created`

```json
{
  "id": "uuid",
  "bookingId": "uuid",
  "doctorProfileId": "uuid",
  "content": "string",
  "createdAt": "2026-09-04T10:00:00Z"
}
```

### Error Responses

| Status | Condition | Body `error` |
|---|---|---|
| `401 Unauthorized` | Missing/invalid staff bearer token | — |
| `404 Not Found` | No `Booking` with `bookingId`, or it doesn't belong to `clinicId` | `BOOKING_NOT_FOUND` |
| `403 Forbidden` | Caller is not the booking's treating doctor | `FORBIDDEN` |
| `409 Conflict` | A consultation note already exists for this booking | `CONSULTATION_NOTE_ALREADY_EXISTS` |

## `GET /api/v1/clinics/{clinicId}/bookings/{bookingId}/consultation-notes`

### Success Response — `200 OK`

Same shape as the create response above.

### Error Responses

| Status | Condition | Body `error` |
|---|---|---|
| `401 Unauthorized` | Missing/invalid staff bearer token | — |
| `404 Not Found` | No `Booking` with `bookingId`, or it doesn't belong to `clinicId`, or no note exists for it yet | `CONSULTATION_NOTE_NOT_FOUND` |
| `403 Forbidden` | Caller is not the booking's treating doctor | `FORBIDDEN` |

## Contract Invariants (traced to spec)

- No `PATCH`/`PUT`/`DELETE` method exists on this resource anywhere in this contract — genuinely
  absent, not merely unauthorized (FR-002, research.md R7).
- A second `POST` against an already-documented booking always fails with
  `CONSULTATION_NOTE_ALREADY_EXISTS`, even for the original authoring doctor (FR-003).
- Every non-treating-doctor caller — including a ClinicAdmin at the same clinic — is rejected with
  `403 FORBIDDEN` for both endpoints, with no override (FR-004).
