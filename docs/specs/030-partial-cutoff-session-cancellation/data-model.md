# Data Model: Partial (Cutoff-Based) Session Cancellation

No new entity, no new field, no migration. Reuses 025/026's `Booking.status`/`Slot.status`
transitions entirely as-is, scoped by an additional time filter.

## Computation (`SessionPartialCancellationService.cancelFromCutoff(Session session, LocalTime cutoffTime)`)

1. Load all `Slot`s in the Session.
2. Compute the cutoff threshold for this Session's mode (R3): a `LocalDateTime` (Fixed-Time) or
   an `Instant` (Queue-mode), both derived from `session.getSessionDate()` + `cutoffTime`.
3. Filter to `Slot`s with `status == BOOKED` **and** scheduled time (`startTime` or `createdAt`,
   per mode) at or after that threshold.
4. For each: same guarded cancel-and-release sequence as 029 (`cancelIfActive`, flip to `OPEN`,
   notify if linked, never publish `BookingCancelledEvent`).
5. Return the count actually cancelled — **no rejection when this count would be zero** (R2),
   unlike 029.

## Validation Rules (from Functional Requirements)

- FR-001: staff-only, Operations-or-ClinicAdmin, clinic-scoped (Assumptions, mirrors 029).
- FR-002/FR-008/SC-001: only `BOOKED` Slots at/after the cutoff (per-mode time proxy, R3) are
  affected.
- FR-003: bare `OPEN` Slots are never touched, at/after the cutoff or otherwise (Clarifications).
- FR-004/SC-003: Slots before the cutoff, or already `COMPLETED` regardless of time, are
  untouched.
- FR-005/SC-002: no `BookingCancelledEvent` ever published (R1, identical to 029).
- FR-006/SC-005: `NotificationEventService.publish` called once per cancelled Booking with a
  linked Patient Account (identical to 029).
- FR-007: no `WaitlistEntry` or rebooking logic exists anywhere in this feature.
- FR-009/SC-004: zero qualifying Slots → `200` with `bookingsCancelled: 0`, not an error (R2).
