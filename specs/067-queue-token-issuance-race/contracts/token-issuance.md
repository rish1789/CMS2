# Contract: Token issuance behaviour on existing endpoints

**No new endpoints, no request/response shape changes.** This feature changes *when* existing responses occur on three existing endpoints:

| Endpoint | Spec of record |
|---|---|
| `POST /api/v1/patients/clinics/{clinicId}/sessions/{sessionId}/queue-bookings` | 022 `contracts/queue-booking.md` |
| `POST /api/v1/clinics/{clinicId}/sessions/{sessionId}/queue-bookings` (staff) | 022 `contracts/queue-booking.md` |
| Front-desk walk-in registration | 063 `contracts/front-desk-walk-in.md` |

## Behaviour changes

| Situation | Before | After |
|---|---|---|
| Many simultaneous requests for one session | usually success; occasionally **503 `TOKEN_ISSUANCE_FAILED`** (queue paths) or registration failure (walk-in) | **success for every request** (FR-001) |
| Booking step fails after a token was reserved | original error returned; token left behind (orphan) | **same original error**, no token left behind (FR-008) |
| A request waits more than 5 s for the session's issuance lock | n/a | **503 `TOKEN_ISSUANCE_FAILED`**, nothing left behind (FR-007) |
| Session not accepting / wrong mode / duplicate walk-in / booking limit / rate limit / rejected clinic | existing code and status | **unchanged** (FR-004) |

## `TOKEN_ISSUANCE_FAILED` - meaning updated

- Status stays **503**, and the error code is unchanged. It is mapped in `BookingExceptionHandler`.
- Old meaning (022 contract): "exhausted its retry budget under extreme contention".
- New meaning: "could not obtain the session's issuance turn within the bound; retry later". It should essentially never occur. SC-001 bursts do not produce it.

## Documentation corrections made alongside this feature

- 022 `contracts/queue-booking.md`: the `TOKEN_ISSUANCE_FAILED` row's description is updated to the new meaning.
- 063 `contracts/front-desk-walk-in.md` lists `TOKEN_ISSUANCE_FAILED` as **409**, but the only handler (`BookingExceptionHandler`) returns **503**. That was already the behaviour before this feature; verified by grep, as there is no other handler for this exception. The row is corrected to 503 to match the code. This is a documentation fix, not a behaviour change.
