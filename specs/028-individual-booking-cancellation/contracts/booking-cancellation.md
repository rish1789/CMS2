# Contract: Individual Booking Cancellation & Waitlist Trigger

Both endpoints return the identical response shape — 020's existing `BookingResponse`, extended
with a new `status` field (`ACTIVE` | `CANCELLED`) — only authorization/eligibility differs.

## `POST /api/v1/clinics/{clinicId}/bookings/{bookingId}/cancel`

Requires a valid staff bearer token (`/api/v1/clinics/**` chain). Any active role at the clinic
may cancel — no time restriction relative to the scheduled slot (FR-001).

### Success Response — `200 OK`

```json
{
  "id": "uuid",
  "slotId": "uuid",
  "patientId": "uuid",
  "appointmentTypeId": "uuid",
  "lockedFee": 500.00,
  "paymentStatus": "PENDING",
  "status": "CANCELLED",
  "createdAt": "2026-09-03T10:00:00Z"
}
```

### Error Responses

| Status | Condition | Body `error` |
|---|---|---|
| `401 Unauthorized` | Missing/invalid staff bearer token | — |
| `403 Forbidden` | Caller has no active role assignment at all at `clinicId` (mirrors 027's identical pattern) | `FORBIDDEN` |
| `404 Not Found` | No `Booking` with `bookingId` at `clinicId` | `BOOKING_NOT_FOUND` |
| `409 Conflict` | The Booking's Slot belongs to a Queue-mode Session | `NOT_A_FIXED_TIME_SESSION` |
| `409 Conflict` | The Booking is already cancelled, or its Slot is not `BOOKED` (already `NO_SHOW`/`COMPLETED`) | `BOOKING_NOT_CANCELLABLE` |

## `POST /api/v1/patients/bookings/{bookingId}/cancel`

Requires a valid patient bearer token (`/api/v1/patients/**` chain). Only the caller's own
Booking may be cancelled, and only while its scheduled slot time is at least 2 hours away
(FR-002).

### Success Response — `200 OK`

Same shape as the staff endpoint above.

### Error Responses

| Status | Condition | Body `error` |
|---|---|---|
| `401 Unauthorized` | Missing/invalid patient bearer token | — |
| `404 Not Found` | No `Booking` with `bookingId` belonging to the caller's own Patient Account | `BOOKING_NOT_FOUND` |
| `409 Conflict` | The Booking's Slot belongs to a Queue-mode Session | `NOT_A_FIXED_TIME_SESSION` |
| `409 Conflict` | The Booking is already cancelled, or its Slot is not `BOOKED` | `BOOKING_NOT_CANCELLABLE` |
| `409 Conflict` | The scheduled slot time is less than 2 hours away | `CANCELLATION_CUTOFF_PASSED` |

## Contract Invariants (traced to spec)

- Every successful cancellation results in `Booking.status = CANCELLED`, `Slot.status = OPEN`,
  and exactly one `BookingCancelledEvent` published — never zero, never more than one (SC-003).
- Every rejected cancellation attempt (403/404/409) leaves the target Booking's `status` and its
  Slot's `status` completely unchanged (SC-002 for the cutoff case specifically; implied for
  every other rejection).
- A Slot released by cancellation is immediately eligible for a brand-new Booking via any of
  016/017/018/025's existing endpoints (SC-004).
- Concurrent cancellation attempts against the same Booking never result in more than one
  cancelled outcome or more than one published event (SC-005).
