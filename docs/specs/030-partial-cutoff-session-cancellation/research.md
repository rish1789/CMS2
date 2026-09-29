# Research: Partial (Cutoff-Based) Session Cancellation

This feature reuses nearly all of 029's (whole-day cancellation) design decisions verbatim —
this document only covers where it genuinely differs.

## R1: Reuses 029's core shape, adding a time filter

**Decision**: `SessionPartialCancellationService` mirrors `SessionCancellationService` (029)
exactly — same module (`com.cms.booking`), same direct use of `BookingRepository.cancelIfActive`
(never 028's event-publishing `BookingCancellationService.cancel`, for the identical FR-005/029's
FR-003 no-waitlist-bump reason), same per-Slot race-closure (research.md R4 of 029), same
notification-for-linked-Patients-only rule (029's R5). The only addition: before the "is this
Slot `BOOKED`?" filter, an additional "is this Slot's scheduled time at or after the cutoff?"
filter (FR-002/FR-008).

**Rationale**: 029 already established every non-cutoff-specific decision this feature needs;
duplicating that reasoning here would just restate it. The two features differ on exactly one
dimension (a time filter) and one edge-case outcome (R2 below) — everything else is identical by
design, per Clarifications' explicit choice to mirror 029's OPEN-slot and authorization scope.

## R2: "Nothing qualifies" is a normal zero-count success, not a rejection

**Decision**: Unlike 029's `SessionAlreadyCancelledException` (thrown when zero `BOOKED` Slots
exist at all), this feature returns `200` with `bookingsCancelled: 0` when no `BOOKED` Slot
qualifies at/after the cutoff (FR-009/SC-004).

**Rationale**: The two "nothing to do" cases mean different things. For 029, zero `BOOKED` Slots
in the whole Session strongly signals "this was already cancelled" (or never had anything) — an
error is the right signal. For this feature, a cutoff simply late enough that nothing remaining
qualifies (e.g. staff picks a cutoff after the last booked slot) is an entirely ordinary, valid
choice with a legitimately empty result — not an error condition, per the spec's own explicit
resolution (FR-009).

## R3: Time comparison — Fixed-Time uses `startTime`, Queue-mode uses `createdAt` (Clarifications)

**Decision**: For a Fixed-Time Slot: `LocalDateTime.of(session.getSessionDate(), slot.getStartTime())`
compared against `LocalDateTime.of(session.getSessionDate(), cutoffTime)` (reuses 023/026's
established Session-date + Slot-time combining technique). For a Queue-mode Slot: convert the
cutoff to an `Instant` (`LocalDateTime.of(session.getSessionDate(), cutoffTime)
.atZone(ZoneId.systemDefault()).toInstant()`) and compare directly against `Slot.getCreatedAt()`
(already an `Instant`) — Clarifications' resolved proxy for a Queue Slot's lack of `startTime`.

**Rationale**: Two different stored time representations (`LocalTime` vs `Instant`) need two
different comparison paths; converting the cutoff to whichever representation the Slot type
actually has, rather than forcing one universal comparison, avoids a lossy or incorrect
conversion in either direction.

## R4: Endpoint and response — reuses 029's `SessionCancellationResponse` DTO

**Decision**: `POST /api/v1/clinics/{clinicId}/sessions/{sessionId}/cancel-from-cutoff` with a
JSON body `{ "cutoffTime": "16:00:00" }`, returning the identical `SessionCancellationResponse`
(`sessionId`, `bookingsCancelled`) 029 already defined — no new response DTO.

**Rationale**: The response shape is identical (a count of affected Bookings); introducing a
second, structurally-identical DTO would be unjustified duplication (Constitution II). The
request needs one new field 029's endpoint didn't (a cutoff time), so a new request DTO is
warranted, but not a new response one.
