# Contract: Whole-Day Session Cancellation

Requires a valid staff bearer token (`/api/v1/clinics/**` chain). Only an active Operations
staff member or ClinicAdmin at the clinic may cancel — never the Doctor (Assumptions).

## `POST /api/v1/clinics/{clinicId}/sessions/{sessionId}/cancel`

### Request

No body.

### Success Response — `200 OK`

```json
{ "sessionId": "uuid", "bookingsCancelled": 7 }
```

### Error Responses

| Status | Condition | Body `error` |
|---|---|---|
| `401 Unauthorized` | Missing/invalid staff bearer token | — |
| `403 Forbidden` | Caller is neither an active Operations nor ClinicAdmin at `clinicId` | `FORBIDDEN` |
| `404 Not Found` | No `Session` with `sessionId` at `clinicId` | `SESSION_NOT_FOUND` |
| `409 Conflict` | The Session has zero currently-active Bookings (already cancelled, or never had any — FR-005) | `SESSION_ALREADY_CANCELLED` |

## Contract Invariants (traced to spec)

- `bookingsCancelled` always equals the exact count of Bookings that were `ACTIVE` (Slot `BOOKED`)
  in this Session immediately before the call (SC-001).
- No `BookingCancelledEvent` is ever published as a result of this endpoint, regardless of how
  many Bookings it cancels (SC-002).
- Every Slot already `NO_SHOW`, `COMPLETED`, or `OPEN` (no Booking) before the call remains in
  that exact state afterward (SC-003).
- A `409 SESSION_ALREADY_CANCELLED` response is always accompanied by zero state changes anywhere
  (SC-004).
- Every cancelled Booking whose Patient has a linked Patient Account results in exactly one
  `NotificationEventService.publish` call (SC-005); a walk-in Booking results in zero.
