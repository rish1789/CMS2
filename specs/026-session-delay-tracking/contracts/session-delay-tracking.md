# Contract: Session Delay Tracking (Fixed-Time Only)

Requires a valid staff bearer token (`/api/v1/clinics/**` chain). A missing/invalid token returns `401`.

## `POST /api/v1/clinics/{clinicId}/slots/{slotId}/complete`

Marks a `BOOKED` Fixed-Time Slot completed and recalculates its Session's delay figure as part of
the same action (FR-001, FR-002).

### Request

No body.

### Success Response — `200 OK`

```json
{ "slotId": "uuid", "status": "COMPLETED" }
```

### Error Responses

| Status | Condition | Body `error` |
|---|---|---|
| `401 Unauthorized` | Missing/invalid staff bearer token | — |
| `403 Forbidden` | Caller is neither an active Operations nor ClinicAdmin at `clinicId` | `FORBIDDEN` |
| `404 Not Found` | No `Slot` with `slotId` at `clinicId` | `SLOT_NOT_FOUND` |
| `409 Conflict` | The Slot belongs to a Queue-mode Session | `NOT_A_FIXED_TIME_SESSION` |
| `409 Conflict` | The Slot's status is not `BOOKED` (still `OPEN`, or already `COMPLETED`) | `SLOT_NOT_COMPLETABLE` |

## `GET /api/v1/clinics/{clinicId}/sessions/{sessionId}/delay`

Returns the Session's current (last-recalculated) delay figure — read-only, never recomputes
(FR-005).

### Success Response — `200 OK`

Fixed-Time Session, currently delayed:

```json
{ "sessionId": "uuid", "applicable": true, "delayMinutes": 12 }
```

Fixed-Time Session, no outstanding delay (never triggered, or last recalculation found none):

```json
{ "sessionId": "uuid", "applicable": true, "delayMinutes": null }
```

Queue-mode Session (FR-007 — not an error, `delayMinutes` is always `null` alongside `applicable: false`):

```json
{ "sessionId": "uuid", "applicable": false, "delayMinutes": null }
```

### Error Responses

| Status | Condition | Body `error` |
|---|---|---|
| `401 Unauthorized` | Missing/invalid staff bearer token | — |
| `404 Not Found` | No `Session` with `sessionId` at `clinicId` | `SESSION_NOT_FOUND` |

## Contract Invariants (traced to spec)

- A `200` from `POST .../complete` is always followed by that Session's `delayMinutes` reflecting
  the correct recalculation as of that moment (SC-001).
- `GET .../delay`'s `delayMinutes` never changes between two calls unless a trigger point occurred
  in between, regardless of elapsed wall-clock time (SC-002).
- Every rejected `POST .../complete` (403/404/409) leaves the target Slot's `status` and every
  Session's `delayMinutes` unchanged (SC-003).
- A Queue-mode Session's `GET .../delay` always returns `applicable: false, delayMinutes: null` —
  never a numeric value (SC-004).
- Once every past-due Slot in a Session is `COMPLETED`, the next trigger's recalculation stores
  `delayMinutes: null` (SC-005).
