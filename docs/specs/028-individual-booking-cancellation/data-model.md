# Data Model: Individual Booking Cancellation & Waitlist Trigger

## Booking (extended)

| Field | Type | Change | Notes |
|---|---|---|---|
| `status` | `BookingStatus` (`ACTIVE`, `CANCELLED`), not null | **new** | Default `ACTIVE` for every existing row (backfilled by the migration itself). Independent of `paymentStatus` (R1). One-way `ACTIVE → CANCELLED` transition, enforced at the data layer (R3). |

**Migration**: `V16__booking_cancellation.sql` —
```sql
ALTER TABLE booking ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
DROP INDEX uq_booking_slot;
CREATE UNIQUE INDEX uq_booking_slot_active ON booking (slot_id) WHERE status <> 'CANCELLED';
```

## Slot (read/write, no schema change)

No new field. Gains a new *reverse* transition this feature is the first to perform:
`BOOKED → OPEN` (R2, FR-004), the mirror image of every prior transition in this codebase, which
has always moved a Slot forward, never back.

## New Types

- **`BookingStatus`** *(new enum, `com.cms.booking`)*: `ACTIVE`, `CANCELLED`.
- **`BookingCancelledEvent`** *(new record, `com.cms.booking`)*: `bookingId`, `slotId`,
  `occurredAt` — the waitlist bump trigger signal (R4). Not persisted anywhere; a future 028 adds
  its own listener.

## State Transitions

```
Booking.status:
  ACTIVE --[BookingCancellationService.cancel, data-layer-guarded]--> CANCELLED

Slot.status (this feature's own contribution):
  BOOKED --[same action, same transaction]--> OPEN
```

`Slot.status = OPEN` after a cancellation is then eligible for every existing forward path again
(a brand-new `Booking` via 016/017/018/025, exactly as any other `OPEN` Slot).

## Validation Rules (from Functional Requirements)

- FR-001: staff cancellation has no time restriction relative to the scheduled slot.
- FR-002/SC-002: patient cancellation requires `LocalDateTime.of(session.sessionDate, slot.startTime)`
  to be at least 2 hours from now (R6); rejected otherwise, with zero state change.
- FR-003: rejected if `Booking.status == CANCELLED` already, or if `Slot.status` is not `BOOKED`
  (already `NO_SHOW` or `COMPLETED` — a resolved outcome, nothing to cancel).
- FR-004/FR-004a: a successful cancellation sets `Booking.status = CANCELLED` and
  `Slot.status = OPEN` in the same transaction; the partial unique index (R2) allows a later new
  Booking against the same Slot without conflicting with the retained cancelled row.
- FR-005/SC-003: `BookingCancelledEvent` is published exactly once per successful cancellation,
  only after the data-layer transition (R3) is confirmed to have actually happened.
- FR-006: no `WaitlistEntry`, matching, or claim logic exists in this feature at all.
- FR-007: the patient endpoint filters by `Booking.patient.patientAccount` matching the caller,
  identical to 027's own ownership-check shape.
- FR-008/SC-005: `BookingRepository.cancelIfActive` (R3) is the actual concurrency guarantee —
  exactly one concurrent caller ever transitions the row and therefore ever publishes the event.
- FR-009: both endpoints reject a Queue-mode Booking's Slot via the reused
  `NotAFixedTimeSessionException` (R7).
