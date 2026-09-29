# Contract: Partial (Cutoff-Based) Session Cancellation

Requires a valid staff bearer token (`/api/v1/clinics/**` chain). Only an active Operations
staff member or ClinicAdmin at the clinic may cancel — never the Doctor (Assumptions, mirrors
029).

## `POST /api/v1/clinics/{clinicId}/sessions/{sessionId}/cancel-from-cutoff`

### Request

```json
{ "cutoffTime": "16:00:00" }
```

### Success Response — `200 OK`

Reuses 029's `SessionCancellationResponse` exactly:

```json
{ "sessionId": "uuid", "bookingsCancelled": 4 }
```

`bookingsCancelled` may legitimately be `0` (a cutoff with nothing qualifying — not an error,
R2).

### Error Responses

| Status | Condition | Body `error` |
|---|---|---|
| `401 Unauthorized` | Missing/invalid staff bearer token | — |
| `403 Forbidden` | Caller is neither an active Operations nor ClinicAdmin at `clinicId` | `FORBIDDEN` |
| `404 Not Found` | No `Session` with `sessionId` at `clinicId` | `SESSION_NOT_FOUND` |

Note: unlike 029's `SESSION_ALREADY_CANCELLED`, this endpoint has no equivalent rejection — a
cutoff that matches nothing is a normal `200` with `bookingsCancelled: 0` (R2).

## Contract Invariants (traced to spec)

- Every `BOOKED` Slot whose scheduled time (per-mode proxy) is at or after `cutoffTime` ends up
  with its Booking cancelled, in the same call (SC-001).
- No `BookingCancelledEvent` is ever published as a result of this endpoint (SC-002).
- Every Slot scheduled before the cutoff, or already `COMPLETED` regardless of time, remains
  completely unchanged (SC-003).
- A cutoff matching nothing always returns `200` with `bookingsCancelled: 0`, never an error
  (SC-004).
- Every cancelled Booking whose Patient has a linked Patient Account results in exactly one
  `NotificationEventService.publish` call (SC-005).
