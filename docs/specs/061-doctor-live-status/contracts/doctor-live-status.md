# Contract: Doctor Live Schedule Status

Two new endpoints, both `GET`-only, both read-only, both safe to poll on a short interval (spec API Requirements). Neither modifies any data. The existing `GET /api/v1/clinics/{clinicId}/sessions/{sessionId}/delay` endpoint is unchanged by this feature (A6) and is not documented again here.

## `GET /api/v1/clinics/{clinicId}/sessions/{sessionId}/live-status`

Staff/doctor realm (`/api/v1/clinics/**`). Sibling route on the existing `SessionDelayController`, reusing its existing clinic-scoping + doctor-self-scoping check (research.md Decision 3).

### Success — `200 OK`, Fixed-Time session
```json
{
  "sessionId": "uuid",
  "applicable": true,
  "status": "DELAYED",
  "currentPatientOrdinal": 1,
  "expectedPatientOrdinal": 3,
  "deviationMinutes": 15,
  "firstSlotTime": "09:00:00",
  "operationalDay": "2026-09-23"
}
```
`status` is one of `NOT_STARTED` / `ON_TIME` / `RUNNING_EARLY` / `DELAYED` / `COMPLETED` (BR-001). `currentPatientOrdinal`/`expectedPatientOrdinal`/`deviationMinutes` are `null` wherever data-model.md's `SessionLiveStatus` table says so for that status (e.g. both ordinals `null` and `deviationMinutes` null when `status: "NOT_STARTED"`).

### Success — `200 OK`, Queue-mode session
```json
{ "sessionId": "uuid", "applicable": false, "status": null, "currentPatientOrdinal": null, "expectedPatientOrdinal": null, "deviationMinutes": null, "firstSlotTime": null, "operationalDay": null }
```
Mirrors `SessionDelayResponse`'s existing `applicable: false` contract exactly (spec API Requirements) — never an error for a Queue-mode session.

### Errors
| Status | Condition |
|---|---|
| `401 Unauthorized` | No/invalid staff bearer token |
| `404 Not Found` | `sessionId` doesn't exist, doesn't belong to `clinicId`, or (when the caller's only active role at this clinic is Doctor) belongs to a different doctor — all three refused identically, mirroring `SessionDelayController`'s existing fail-closed behavior (FR-012) |

---

## `GET /api/v1/patients/bookings/{bookingId}/live-status`

Patient realm (`/api/v1/patients/**`). New controller, mirroring `PatientQueuePositionController`'s booking-ownership check exactly (research.md Decision 5).

### Success — `200 OK`, an active Fixed-Time booking
```json
{
  "bookingId": "uuid",
  "applicable": true,
  "doctorName": "Dr. Asha Rao",
  "currentPatientOrdinal": 1,
  "statusText": "12 min delayed",
  "estimatedWaitMinutes": 25
}
```
`statusText` is always plain language (BR-001, FR-004/FR-011) — never one of the raw `NOT_STARTED`/`ON_TIME`/etc. codes the staff endpoint returns. `estimatedWaitMinutes` is `null` once the caller's own slot is resolved (FR-010).

### Success — `200 OK`, a Queue-mode booking
```json
{ "bookingId": "uuid", "applicable": false, "doctorName": null, "currentPatientOrdinal": null, "statusText": null, "estimatedWaitMinutes": null }
```

### Errors
| Status | Condition |
|---|---|
| `401 Unauthorized` | No/invalid patient bearer token |
| `404 Not Found` | `bookingId` doesn't exist, or doesn't resolve to the caller's own patient account — both cases refused identically, no enumeration signal (mirrors `PatientQueuePositionController`) |

## Contract Invariants (traced to spec)

- FR-001/FR-002: both endpoints compute their response fresh on every call — never read back a cached figure written at some earlier trigger point.
- FR-003: both endpoints return `applicable: false` (never an error, never a delay/status figure) for anything Queue-mode — queue position (024) remains the only live-ish signal for those sessions/bookings.
- FR-004/FR-011: the patient endpoint's `currentPatientOrdinal` is the only other-visit-adjacent figure it ever returns, and it is a bare integer — no endpoint in this contract returns another patient's name, phone number, or any other identifying detail, at any status.
- FR-005/FR-006/FR-007: both endpoints are designed be polled repeatedly (no side effects, no rate-sensitive state); the frontend distinguishes loading/error/not-applicable/ready from these two responses exactly as `QueuePositionIndicator` already does for its own endpoint pair.
- FR-009 (estimated wait): derived server-side from `deviationMinutes`/the caller's own slot time — never computed client-side from the browser's clock (BR-010).
- FR-012/FR-013: every error case above is enforced by the same scoping/ownership checks already proven in production by `SessionDelayController`/`PatientQueuePositionController` — no new authorization logic was invented for this contract.
- A6: neither endpoint replaces or modifies `GET .../sessions/{sessionId}/delay` — that route, its response shape, and its existing consumers are unaffected.
