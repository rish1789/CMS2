# Data Model: Walk-In / Priority Insertion

No new entity is introduced. This feature extends `Booking` (first defined by
016-staff-assisted-fixed-time-booking) with one new optional field, and reads — but does not
change the shape of — the existing `Slot` (012/021/022) and `Patient` (019) entities.

## Booking (extended)

| Field | Type | Change | Notes |
|---|---|---|---|
| `overrideReason` | `String`, nullable | **new** | Set only for a priority-(3) insertion (FR-003/FR-004); `null` for priority-(1)/(2) insertions. Free text — no fixed reason codes (spec Assumptions). |

All other `Booking` fields (`slot`, `patient`, `appointmentType`, `lockedFee`, `paymentStatus`,
`bookedByAccountId`, `createdAt`) are unchanged and populated exactly as 016's existing
`StaffBookingService` already does.

**Migration**: `V14__booking_override_reason.sql` —
```sql
ALTER TABLE booking ADD COLUMN override_reason TEXT;
```
Nullable, no default, no backfill needed (existing rows are all priority-(1)/(2)-equivalent — no
override reason ever applied to them retroactively).

## Slot (read-only for this feature)

No schema change. This feature reads `isBuffer`, `status` (`OPEN`/`NO_SHOW`/`BOOKED`), and
`session` to run the priority search (R1), and calls the existing `setStatus(BOOKED)` mutator to
close the loop on a successful insertion — identical to how 016 already flips a Slot's status.

## Patient (read-only / create, for this feature)

No schema change. A walk-in insertion either looks up an existing clinic-scoped `Patient` by ID, or
creates a new one exactly as 016's `StaffBookingService.resolveOrCreatePatient` already does
(nullable `patientAccount`, mobile number optional-but-validated).

## State Transitions

```
Slot.status:
  OPEN (buffer=true)  --[tier-1 insertion]--> BOOKED
  NO_SHOW             --[tier-2 insertion]--> BOOKED   (old Booking deleted first, R2/R3)
  OPEN (buffer=false) --[tier-3 insertion, override reason required]--> BOOKED
```

No transition touches a Slot that is already `BOOKED` (that Session's Slot is simply not a
candidate at any tier — SlotStatus.BOOKED is excluded from all three tiers by construction, since
tier 1/3 require `status == OPEN` and tier 2 requires `status == NO_SHOW`).

## Validation Rules (from Functional Requirements)

- FR-001 / SC-001: tier search order is fixed and exhaustive — tier 2 is only considered if no
  tier-1 candidate exists; tier 3 only if neither tier-1 nor tier-2 exists.
- FR-002: tier-1/tier-2 insertions MUST NOT require `overrideReason`.
- FR-003 / SC-002: tier-3 insertion MUST reject (before any write) if `overrideReason` is blank/absent.
- FR-004 / SC-004: a successful tier-3 `Booking.overrideReason` MUST be non-null and retrievable.
- FR-005 / SC-003: fee resolution MUST fail closed — no `Patient`, no `Booking`, no old-Booking
  deletion — if `FeeResolutionService.resolve` throws.
- FR-007: `patientPhone`, if supplied, MUST pass `IndianMobileNumberValidator` (016's existing
  validator, reused as-is) — rejected otherwise, contact-less walk-ins otherwise allowed.
- FR-009: if no Slot qualifies at any tier, reject with a distinct "nothing available" error before
  any write.
- FR-010 / SC-005: the `uq_booking_slot` DB constraint (already in place from 016) is the actual
  race-closure guarantee for a concurrent double-insertion onto the same Slot, checked via
  `saveAndFlush` inside a catchable scope — mirrors 016's own convergence-fixed pattern exactly.
