# Data Model: Queue Position Tracking (Queue-Mode Only)

No new entity, no new field, no migration. This feature is a pure read over existing 013/016/018
data (`Slot.tokenNumber`, `Slot.status`, `Session.mode`, `Booking.slot`, `Patient.patientAccount`).

## Computation (`QueuePositionService.position(bookingId)`)

1. Load `Booking` by id (404 if missing).
2. Load its `Slot` → `Session`. If `Session.mode != QUEUE`, return `applicable=false` (FR-006).
3. If the Booking's own `Slot.status` is `COMPLETED` or `NO_SHOW`, return `applicable=false`
   (FR-007 — already resolved, no position to report).
4. Otherwise: load all `Slot`s in the same Session (`SlotRepository.findBySession_Id`), filter to
   `tokenNumber < thisSlot.tokenNumber && status == BOOKED` (R3), count them, add 1
   (FR-001/FR-002).

## Response Shape

`QueuePositionResponse(bookingId: UUID, applicable: boolean, position: Integer)` —
`position` is always `null` when `applicable=false`; always a positive integer (≥1) when
`applicable=true`. Mirrors 026's `SessionDelayResponse` shape (`applicable` boolean +
nullable value), for the same reason: `applicable=false` needs to be distinguishable from a
would-be `0`/`null` numeric reading, and there is no numeric alternative that could otherwise be
confused with it here either.

## Validation Rules (from Functional Requirements)

- FR-001/FR-002/SC-001: position = count of `BOOKED` Slots with a lower `tokenNumber` in the same
  Session, + 1.
- FR-003/SC-002: computed fresh on every call — no stored value read or written anywhere in this
  feature.
- FR-004: the staff endpoint filters the Booking by `clinicId` via its Slot's Session's Clinic
  (404 if it doesn't match) before computing.
- FR-005/SC-004: the patient endpoint filters by `Booking.getPatient().getPatientAccount()`
  equal to the caller's own Patient Account id (rejected otherwise — a Patient record with no
  linked Account, i.e. a pure walk-in, can never be "owned" by any patient caller).
- FR-006/SC-003: `Session.mode != QUEUE` → `applicable=false`.
- FR-007: the Booking's own Slot already `COMPLETED`/`NO_SHOW` → `applicable=false`.
- SC-005: both endpoints call the identical `QueuePositionService.position(bookingId)` — no
  separate computation to drift apart.
