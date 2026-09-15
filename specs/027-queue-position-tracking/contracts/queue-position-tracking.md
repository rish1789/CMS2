# Contract: Queue Position Tracking (Queue-Mode Only)

Both endpoints return the identical computation (`QueuePositionService.position`, R4) — only
authorization/scoping differs.

## `GET /api/v1/clinics/{clinicId}/bookings/{bookingId}/queue-position`

Requires a valid staff bearer token (`/api/v1/clinics/**` chain). Any active role at the clinic
may call this — no Operations/ClinicAdmin-only gate (R4).

### Success Response — `200 OK`

Queue-mode, still active:

```json
{ "bookingId": "uuid", "applicable": true, "position": 3 }
```

Not applicable (Fixed-Time Session, or this Booking's own Slot already resolved):

```json
{ "bookingId": "uuid", "applicable": false, "position": null }
```

### Error Responses

| Status | Condition | Body `error` |
|---|---|---|
| `401 Unauthorized` | Missing/invalid staff bearer token | — |
| `403 Forbidden` | Caller has no active role assignment at all at `clinicId` (analyze finding E1 — mirrors 016/020/025/026's identical staff-gated-action pattern) | `FORBIDDEN` |
| `404 Not Found` | No `Booking` with `bookingId` at `clinicId` | `BOOKING_NOT_FOUND` |

## `GET /api/v1/patients/bookings/{bookingId}/queue-position`

Requires a valid patient bearer token (`/api/v1/patients/**` chain). Only the caller's own
Booking may be queried.

### Success Response — `200 OK`

Same shape as the staff endpoint above.

### Error Responses

| Status | Condition | Body `error` |
|---|---|---|
| `401 Unauthorized` | Missing/invalid patient bearer token | — |
| `404 Not Found` | No `Booking` with `bookingId` belonging to the caller's own Patient Account | `BOOKING_NOT_FOUND` |

## Contract Invariants (traced to spec)

- `position` is always the exact count of currently-active (`BOOKED`) Bookings with a lower token
  number in the same Session, plus one (SC-001).
- Two calls separated by a change in an ahead Booking's active/resolved state always reflect that
  change on the very next call (SC-002) — nothing is ever read from a stored/cached value.
- Every Fixed-Time Session Booking's query returns `applicable: false, position: null` — never a
  number (SC-003).
- Every patient query for a Booking that isn't theirs returns `404 BOOKING_NOT_FOUND` — the
  Booking's existence is never confirmed or denied differently for a non-owned Booking (SC-004).
- The staff and patient endpoints return byte-identical `applicable`/`position` values for the
  same Booking queried at the same moment (SC-005).
