# Contract: Day Sheet Smart Status Flow

## `POST /api/v1/clinics/{clinicId}/slots/{slotId}/complete` (existing endpoint, authorization extended)

**Changed from `026-session-delay-tracking`**: authorization and eligibility, both extended additively — see the resolution order below. ClinicAdmin/Operations' existing `BOOKED`-only path is preserved unchanged; `APPEARED` and the treating-doctor caller are both new.

### Request
No body. Path: `clinicId`, `slotId`.

### Resolution order
1. Caller is an active ClinicAdmin or Operations staff at `clinicId` → authorized, **eligible slot statuses: `BOOKED` or `APPEARED`** (unchanged `BOOKED` path preserved — this feature is additive, FR-008; `APPEARED` is newly allowed too).
2. **New**: caller is the treating doctor for this slot's session (`slot.session.doctorProfile.account.id == callerAccountId`) → authorized, **eligible slot status: `APPEARED` only**. A doctor cannot complete a still-`BOOKED` slot — FR-006 scopes their access to "their own Appeared slot" specifically, matching the front-desk-checks-in-first-then-doctor-completes workflow this feature is built around.
3. Else → `403 Forbidden`.

Then, as today: slot must belong to `clinicId` and session must be `FIXED_TIME`.

### Success — `200 OK`
Unchanged response shape: `{ "slotId": "uuid", "status": "COMPLETED", ... }` (existing `SlotCompletionResponse`).

### Errors
| Status | Condition |
|---|---|
| `403 Forbidden` | Caller has no active ClinicAdmin/Operations role at the clinic and is not the treating doctor (a non-treating doctor, or a doctor with no role assignment at all, gets this) |
| `404 Not Found` | Slot not found in this clinic |
| `409 Conflict` | Slot is not `FIXED_TIME`, or the slot's current status isn't eligible for *this caller* (a doctor caller with the slot still `BOOKED` gets this, not a silent no-op — existing `SlotNotCompletableException`/`NotAFixedTimeSessionException`, reused) |

---

## `POST /api/v1/clinics/{clinicId}/slots/{slotId}/appeared` (new)

Marks a slot as Appeared — the patient has arrived. The one action that both starts the normal flow (from `BOOKED`) and corrects a mistaken automatic No-Show (from `NO_SHOW`) — see research.md Decision 3.

### Request
No body. Path: `clinicId`, `slotId`.

### Resolution order
1. Caller is an active ClinicAdmin or Operations staff at `clinicId` → authorized. **Doctors are never authorized for this action** (FR-007) — there is no treating-doctor branch here, unlike `/complete` above.
2. Else → `403 Forbidden`.

Then: slot must belong to `clinicId`, session must be `FIXED_TIME`, and slot status must be `BOOKED` or `NO_SHOW`.

### Success — `200 OK`
```json
{ "slotId": "uuid", "status": "APPEARED" }
```

### Errors
| Status | Condition |
|---|---|
| `403 Forbidden` | Caller has no ClinicAdmin/Operations role at the clinic (including a Doctor caller) |
| `404 Not Found` | Slot not found in this clinic |
| `409 Conflict` | Slot is not `FIXED_TIME`, or slot is not currently `BOOKED`/`NO_SHOW` |

---

## `POST /api/v1/clinics/{clinicId}/sessions/{sessionId}/bookings/cancel-batch` (new)

Cancels one or more bookings in a single request, applying the exact same rule as today's single-booking cancellation to each one independently (research.md Decision 7).

### Request
```json
{ "bookingIds": ["uuid", "uuid", "..."] }
```

### Resolution order
1. Caller is an active ClinicAdmin or Operations staff at `clinicId` → authorized for the whole batch. **Doctors are never authorized** (FR-015), unlike the existing single-booking cancel endpoint, which currently allows any active role — this new batch path is deliberately narrower; the existing single-cancel endpoint is unchanged.
2. Else → `403 Forbidden` for the entire request (an all-or-nothing authorization gate, unlike the per-booking outcome below).

Each `bookingId` in the batch is then processed independently — same eligibility as today's single cancellation (booking must belong to a `FIXED_TIME` session in `clinicId`, and its slot must currently be `BOOKED` or `APPEARED` per the extended eligibility in data-model.md).

### Success — `200 OK`
```json
{
  "cancelled": ["uuid", "uuid"],
  "failed": [
    { "bookingId": "uuid", "reason": "BOOKING_NOT_CANCELLABLE" },
    { "bookingId": "uuid", "reason": "NOT_FOUND" }
  ]
}
```
A `200` is returned even if some (or all) individual bookings failed — FR-014 requires the specific failure to be reported per booking, not the whole request rejected. Every successful cancellation in the batch produces the exact same `BookingCancelledEvent` (and therefore the exact same waitlist-offer trigger, where applicable) that a single cancellation produces today.

### Errors
| Status | Condition |
|---|---|
| `403 Forbidden` | Caller has no ClinicAdmin/Operations role at the clinic |
| `404 Not Found` | `sessionId` itself not found in this clinic |
| `400 Bad Request` | `bookingIds` missing or empty |

## Contract Invariants (traced to spec)

- FR-002/FR-003: a slot's eligibility for the automatic No-Show sweep is governed entirely by its `SlotStatus` (`BOOKED` only) — no endpoint above changes the sweep's own query or timing.
- FR-004/FR-005: automatic completion (a background sweep, not an HTTP endpoint — see plan.md/research.md Decision 2) produces the identical `SlotCompletionResponse`-equivalent state and the identical `SessionDelayService.recalculate` side effect as the manual `/complete` endpoint above.
- FR-012: the batch-cancel endpoint's per-booking outcome is defined entirely by delegating to the same `BookingCancellationService.cancel(Booking)` the existing single-cancel endpoints call — no separate business-rule implementation exists for the batch path.
- FR-015: only the two **new** endpoints (`/appeared`, `/cancel-batch`) carry the ClinicAdmin/Operations-only, no-doctor-exception rule. The existing single-booking `/cancel` endpoint's current "any active role" authorization is unchanged by this feature.
