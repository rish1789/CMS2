# Contract changes: 065 Phase 1 Stabilization

- No endpoint is added, removed or renamed.
- No request or response body shape changes.
- The changes are status codes for previously broken cases, and one new error code.

## Security boundary

| Request | Before | After |
|---|---|---|
| Anonymous → any `/api/v1/clinics/**` path except `POST /api/v1/clinics/register` | 401 if allowlisted; **500** for 7 paths; 404/200 for unmapped paths | **401** `{"error":"UNAUTHORIZED"}` |
| Anonymous → any `/api/v1/patients/**` path except `POST /signup`, `POST /login` | 401 if allowlisted; otherwise fell through | **401** |
| Anonymous → `POST /api/v1/clinics/register`, `POST /api/v1/patients/signup`, `POST /api/v1/patients/login` | processed | processed (unchanged) |
| Authenticated staff without an active role at clinic X → clinic X endpoint | 403 | 403 (unchanged) |

## Booking paths

The error body shape is the existing `ErrorResponse`.

| Endpoint | New refusal | Status / `error` |
|---|---|---|
| `POST /api/v1/patients/clinics/{c}/slots/{s}/book` | slot's session cancelled (whole, or a range covering the slot) | 409 `SESSION_NOT_ACCEPTING_BOOKINGS` |
| same | slot start time already reached today | 409 `SLOT_DATE_IN_THE_PAST` (existing code, now also for same-day elapsed) |
| `POST /api/v1/clinics/{c}/slots/{s}/book` (staff) | cancelled session or range | 409 `SESSION_NOT_ACCEPTING_BOOKINGS` |
| same | past date, or elapsed today | 409 `SLOT_DATE_IN_THE_PAST` (new for staff) |
| `POST /api/v1/patients/clinics/{c}/sessions/{s}/queue-bookings` | past-date session, whole cancellation, or a range covering the current time today | 409 `SESSION_NOT_ACCEPTING_BOOKINGS` |
| `POST /api/v1/clinics/{c}/sessions/{s}/queue-bookings` (staff) | same | 409 `SESSION_NOT_ACCEPTING_BOOKINGS` |
| `POST /api/v1/clinics/{c}/walk-ins` | same | 409 `SESSION_NOT_ACCEPTING_BOOKINGS` |

## Listings

| Endpoint | Change |
|---|---|
| `GET /api/v1/patients/clinics/{c}/slots[?date]` | excludes slots in cancelled sessions or ranges, and today's slots whose start time has been reached |
| `GET /api/v1/patients/clinics/{c}/queue-sessions` | excludes whole-cancelled sessions, and today's sessions with a range covering the current time |

## Cancellation endpoints

| Request | Before | After |
|---|---|---|
| `POST /api/v1/clinics/{c}/sessions/{s}/cancel` on a session with no Booked slots, not previously whole-cancelled | 409 `SESSION_ALREADY_CANCELLED` | **200** `{sessionId, cancelledCount: 0}`; the session stops accepting bookings |
| same, on an already whole-cancelled session | 409 (only if nothing booked) | 409 `SESSION_ALREADY_CANCELLED` |
| `POST …/cancel-from-cutoff` on an already whole-cancelled session | 200 (0 cancelled) | 409 `SESSION_ALREADY_CANCELLED` |

## Clinical documentation

| Request | Before | After |
|---|---|---|
| `POST /api/v1/clinics/{c}/bookings/{b}/consultation-notes`, `/prescriptions`, `/external-record-references` by the treating doctor with an **inactive** Doctor role at `c` | 201 | 403 `FORBIDDEN` |
| `GET` of the same resources by that doctor | 200 | 200 (unchanged) |

## Frontend consumers

- The booking forms (`features/patient-booking`, `features/staff-booking`, `features/front-desk-walk-in`) and session cancellation (`features/session-cancellation`) surface the backend `message` through the shared client's message-first priority (`lib/apiClient.ts`) or their own `message` fallback. No frontend code change is required for the new codes.
- The patient booking API already maps `SLOT_DATE_IN_THE_PAST`.
