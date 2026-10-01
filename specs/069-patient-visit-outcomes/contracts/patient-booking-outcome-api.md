# Contract: patient booking outcome and eligibility (069)

All endpoints are on the patient chain and need a Patient Account Bearer token. Every booking is ownership-scoped: another account's booking returns **404 `BOOKING_NOT_FOUND`**.

## Added to every patient booking summary

Applies to `GET /api/v1/patients/bookings` (each item) and to the new detail endpoint:

```json
"visitOutcome": "SCHEDULED | CHECKED_IN | COMPLETED | NO_SHOW | CANCELLED | NOT_RECORDED",
"cancellation": { "allowed": false, "reason": "CUTOFF_PASSED" }
```

`reason` is one of `ALREADY_CANCELLED`, `VISIT_RESOLVED`, `QUEUE_BOOKING`, `WALK_IN` or `CUTOFF_PASSED`, and is null when `allowed` is true. All existing fields are unchanged.

## New: `GET /api/v1/patients/bookings/{bookingId}`

- **200:** a single `PatientBookingSummaryResponse`, including the fields above.
- **404 `BOOKING_NOT_FOUND`:** the booking is unknown or not the caller's.
- **401:** no token or an invalid token.

## Changed: `GET /api/v1/patients/bookings/{bookingId}/live-status`

- **Added:** `visitOutcome`, always present, including when `applicable` is false.
- **When `applicable` is true** and the outcome is COMPLETED, NO_SHOW or CANCELLED, `statusText` is respectively "Visit complete", "Missed appointment" or "Booking cancelled", instead of the session-progress text.
- **`applicable`:** its rule is unchanged.
- **Otherwise unchanged.**

## Unchanged: `POST /api/v1/patients/bookings/{bookingId}/cancel`

Same checks, order and error codes as before.
