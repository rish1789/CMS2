# Contract: Session Deletion (real-bug-fix, 2026-09-17)

Requires a valid staff bearer token (`/api/v1/clinics/**` chain). Only an active Operations staff
member or ClinicAdmin at the clinic may delete a Session — never the Doctor, mirroring
`session-cancellation.md`'s identical authorization gate exactly.

A `Schedule` edit is deliberately non-retroactive (`ScheduleService`'s own documented invariant) —
correcting a wrong recurring schedule (wrong hours, wrong days) never rewrites Sessions already
generated from the old values. This endpoint is the intended way to remove one of those stale
Sessions so the next generation run rebuilds it from the now-correct Schedule. It is distinct from
`POST .../sessions/{sessionId}/cancel` (`session-cancellation.md`), which only cancels Bookings
inside a Session — it never deletes the Session/Slot rows themselves.

## `DELETE /api/v1/clinics/{clinicId}/sessions/{sessionId}`

### Request

No body.

### Success Response — `200 OK`

Empty body. The Session and all of its Slots are permanently removed. (Matches
`ClinicVerificationController.delete`/`DoctorVerificationController.delete`'s own `void`-returning,
no-`@ResponseStatus` convention for this codebase's other permanent-delete endpoints, rather than
`204 No Content`.)

### Error Responses

| Status | Condition | Body `error` |
|---|---|---|
| `401 Unauthorized` | Missing/invalid staff bearer token | — |
| `403 Forbidden` | Caller is neither an active Operations nor ClinicAdmin at `clinicId` | `FORBIDDEN` |
| `404 Not Found` | No `Session` with `sessionId` at `clinicId` | `SESSION_NOT_FOUND` |
| `409 Conflict` | Any Slot in this Session has ever had a Booking (any status) or a waitlist offer (even a lapsed one) attached | `SESSION_DELETION_BLOCKED` |

## Contract Invariants

- Deletion is blocked outright the instant any real activity is attached — it never cascades
  through Booking or waitlist history, mirroring `ClinicVerificationService.deleteGuarded`'s own
  "block, don't cascade" precedent for a clinic's permanent delete.
- "Real activity" is checked as any Booking row (any status, since a cancelled Booking is retained
  not deleted — `Booking`'s own class doc) or any `WaitlistEntry.offeredSlot` reference against any
  Slot in the Session — every other Slot lifecycle state (`BOOKED`/`COMPLETED`/`NO_SHOW`) is only
  reachable by way of a Booking existing first, so this is a complete proxy for "has this Session
  ever really been used."
- A `409 SESSION_DELETION_BLOCKED` response is always accompanied by zero state changes — neither
  the Session nor any of its Slots are touched (matches `SESSION_ALREADY_CANCELLED`'s own
  all-or-nothing guarantee in `session-cancellation.md`).
- A successful delete removes every Slot belonging to the Session, then the Session itself, in the
  same transaction.
