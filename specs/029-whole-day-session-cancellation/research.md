# Research: Whole-Day Session Cancellation

## R1: Module placement — `com.cms.booking`, keyed by Session

**Decision**: `SessionCancellationService` lives in `com.cms.booking` (not `com.cms.scheduling`),
even though it's keyed by `sessionId` (a `com.cms.scheduling`-owned entity).

**Rationale**: Unlike 026's `SessionDelayService` (which only *reads* `Slot`/`Session` and writes
a `Session`-owned field), this feature's actual work is entirely `Booking`-mutating — cancelling
every active `Booking` in the Session. `com.cms.booking` already depends on `com.cms.scheduling`
(read `Session`/`Slot` freely, as `WalkInInsertionService` already does), so this stays consistent
with the established one-directional dependency while keeping the write-heavy logic next to the
entity it actually writes (`Booking`), the same reasoning 028's `BookingCancellationService`
already used.

## R2: A parallel cancellation path that never publishes `BookingCancelledEvent`

**Decision**: `SessionCancellationService` does **not** call 028's `BookingCancellationService
.cancel(Booking)` (which unconditionally publishes the waitlist-bump event). Instead it calls
`BookingRepository.cancelIfActive(bookingId)` directly — the same data-layer-guarded conditional
update 028 already established — for each active Booking in the Session, then flips each
corresponding Slot to `OPEN`, with no event publish anywhere in the loop.

**Rationale**: FR-003/SC-002 explicitly forbid a waitlist bump from this action ever firing — this
is the entire reason build-order.md's own resolution #2 calls 025 "the ONLY trigger for a waitlist
bump in the entire system." Reusing `BookingCancellationService.cancel` as-is would violate that
by construction; the two features share the underlying data-layer primitive (`cancelIfActive`,
Constitution IV's race-closure requirement) without sharing the event-publishing behavior on top
of it, since that's precisely the one dimension they must differ on.

**Alternatives considered**: Add an `eventPublish: boolean` parameter to `BookingCancellationService
.cancel`. Rejected — a boolean parameter that flips a method's core behavioral contract (whether it
has a side effect as significant as a waitlist trigger) is a worse API than two call sites sharing
the one primitive that's actually identical (`cancelIfActive`) and diverging where they must.

## R3: "Already cancelled" — structural, not a stored flag

**Decision**: `SessionCancellationService.cancelSession(sessionId)` loads every `Slot` in the
Session (`SlotRepository.findBySession_Id`), finds every one whose current `Slot.status == BOOKED`
(i.e. has an active Booking to cancel), and rejects with `SessionAlreadyCancelledException` if
that set is empty — Clarifications' resolved answer means there is no separate
`Session.cancelled` flag to check instead (FR-005).

**Rationale**: A direct implementation of FR-005's own resolved definition. This also correctly
handles the edge case of a Session that legitimately never had any Bookings at all (rejected the
same way as an already-cancelled one — both are "nothing to cancel," which is the accurate,
user-facing outcome either way).

## R4: Race-closure at the per-Booking level, not a single Session-level lock

**Decision**: Each Booking's cancellation within the loop uses its own `cancelIfActive` call
(028's guarantee) — there is no session-wide lock or single atomic "cancel everything" statement.

**Rationale**: Directly satisfies the Edge Case "concurrent whole-session cancellation vs. an
individual cancellation (025) or walk-in insertion targeting the same Slot" — whichever action
reaches a given Booking row first wins, exactly the same per-row guarantee 028 already proved
correct under concurrency (its own 10-thread test). A session-wide lock would be new machinery
this feature's own requirements don't ask for (Constitution II) and would make concurrent
individual actions on *other* Bookings in the same Session block unnecessarily.

## R5: Feeding the notification pipeline — the first real production caller of `NotificationEventService.publish`

**Decision**: For each successfully-cancelled Booking whose `Patient.patientAccount` is non-null,
call `NotificationEventService.publish(patientAccountId, "BOOKING_CANCELLED_SESSION", payload,
null)` (no expiry — this is an informational notice, not a claimable offer like 029's future
waitlist claim). Walk-in Bookings with no linked Account are skipped (FR-006).

**Rationale**: 036/037 were deliberately built with "no fabricated call-sites" (their own
convergence notes) since no real trigger existed yet anywhere in this 39-feature backlog when they
shipped. This is the first one. `NotificationEventService.publish` already throws
`PatientAccountNotFoundException` for a missing account, which is exactly why the pre-check
(`patientAccount != null`) happens before calling it, rather than relying on that exception as
the skip mechanism — a clean skip, not a caught error, for an entirely expected, non-exceptional
case (a walk-in Patient always has no Account; this isn't a failure).

**Alternatives considered**: Skip 036/037 entirely, treating "recorded for notification purposes"
as satisfied by a plain log line. Rejected — 036/037 exist specifically to be this integration
point (Constitution III: notifications are event-driven and this is now a real, currently-existing
consumer), and `NotificationEventService.publish` is already a simple, direct, no-endpoint service
call (015/017's own precedent for calling a service-only contract directly) — there's no reason to
bypass it now that a real trigger exists.

## R6: Endpoint — one action, `POST /api/v1/clinics/{clinicId}/sessions/{sessionId}/cancel`

**Decision**: A single staff-only endpoint, clinic-scoped like every other staff-gated
Session-adjacent action (011/026's manual-trigger and delay-view endpoints).

**Rationale**: No patient-facing path exists for this feature (only staff/ClinicAdmin can decide
a whole day is off) — the spec's own User Story is staff-only, unlike 025/027/028's dual-audience
shape.

## R7: Reuses `SlotStatus`/`BookingStatus` as-is — no new enum values

**Decision**: No new `SlotStatus` or `BookingStatus` value. `Slot.status` transitions
`BOOKED → OPEN` (028's existing reverse transition, reused verbatim); `Booking.status` transitions
`ACTIVE → CANCELLED` (028's existing enum, reused verbatim).

**Rationale**: Clarifications' resolved answer explicitly avoids introducing a new Session-level
concept; the per-Booking/per-Slot transitions this feature performs are literally identical to
028's, just triggered in bulk from a different action with a different (absent) side effect.
