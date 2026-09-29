# Data Model: Whole-Day Session Cancellation

No new entity, no new field, no migration. This feature reuses 028's `Booking.status`
(`ACTIVE`/`CANCELLED`) and `Slot.status`'s existing `BOOKED → OPEN` reverse transition entirely
as-is, applied in bulk.

## Computation (`SessionCancellationService.cancelSession(sessionId)`)

1. Load `Session` by id (404 if missing/wrong clinic).
2. Load all `Slot`s in the Session (`SlotRepository.findBySession_Id`).
3. Filter to those with `status == BOOKED` — the set of Slots with an active Booking to cancel
   (R3). If this set is empty, throw `SessionAlreadyCancelledException` (FR-005).
4. For each such Slot: look up its active Booking (`BookingRepository.findBySlot_IdAndStatus`,
   028's own safe query), call `BookingRepository.cancelIfActive(bookingId)` (028's guarded
   update, R2/R4). If it returns `1` (won the race — a concurrent action might have already
   claimed this specific Booking, R4):
   - Flip the Slot to `OPEN`.
   - If the Booking's Patient has a linked Patient Account, call
     `NotificationEventService.publish(...)` (R5).
   - Never publish `BookingCancelledEvent` (R2/FR-003).
   If it returns `0` (lost the race), skip that Slot/Booking silently — some other action already
   resolved it; this is correct behavior, not an error, for a bulk operation.
5. Return a summary (count cancelled) for the response.

## Validation Rules (from Functional Requirements)

- FR-001: staff-only, clinic-scoped, Operations-or-ClinicAdmin only (never the Doctor) — the
  standard *write-action* authorization gate this codebase uses for every state-changing
  booking-lifecycle action (016/020/025/028), distinct from the *view-only* unrestricted-role gate
  026/027's read endpoints use (Assumptions).
- FR-002/SC-001: every Slot with `status == BOOKED` at the start of the action gets its Booking
  cancelled, in the same transaction.
- FR-003/SC-002: no `BookingCancelledEvent` is ever published by this code path (R2).
- FR-004/SC-003: Slots already `NO_SHOW`, `COMPLETED`, or `OPEN` (no Booking) are never touched —
  the filter in step 3 excludes them by construction.
- FR-005/SC-004: zero qualifying Slots → `SessionAlreadyCancelledException`, no other state
  change.
- FR-006/SC-005: `NotificationEventService.publish` called once per cancelled Booking with a
  linked Patient Account; walk-in Bookings skipped, not erred.
- FR-007: no `WaitlistEntry` or rebooking logic exists anywhere in this feature.
- FR-008: every cancelled Booking's Slot returns to `OPEN` (step 4), identical to 028's own
  transition.
