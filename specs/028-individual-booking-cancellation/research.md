# Research: Individual Booking Cancellation & Waitlist Trigger

## R1: `Booking.status` — a new field, independent of `PaymentStatus`

**Decision**: Add `BookingStatus { ACTIVE, CANCELLED }` as a new `status` field on `Booking`
(default `ACTIVE` for every existing row), entirely separate from the existing `paymentStatus`
field.

**Rationale**: `Booking` has never had a lifecycle status before now — `PaymentStatus`
(`PENDING`/`PAID`) tracks payment, not whether the booking itself is still live. Clarifications
explicitly requires the cancelled record to be retained (not deleted) and distinguishable from an
active one — a new field is the direct expression of that, mirroring how `Slot`'s own lifecycle
status (`SlotStatus`) is a dedicated field, not overloaded onto something else.

**Alternatives considered**: A `cancelledAt` nullable timestamp instead of an enum (null =
active). Rejected — an enum reads more directly as "the actual state" and leaves room for a
future third state without redefining what null means; a timestamp is marginally more compact but
this codebase's own precedent (`SlotStatus`, `PaymentStatus`, `NotificationEventStatus`) is
enums for exactly this shape of field.

## R2: `uq_booking_slot` becomes a partial unique index scoped to non-cancelled Bookings

**Decision**: Drop the existing flat `uq_booking_slot` unique index (016) and replace it with
`CREATE UNIQUE INDEX uq_booking_slot_active ON booking (slot_id) WHERE status <> 'CANCELLED'`.

**Rationale**: Clarifications' resolved answer requires a Slot to be bookable again by a *new*
Booking after cancellation while the *old*, cancelled Booking row still exists referencing the
same `slot_id` — a flat unique index on `slot_id` would reject that new row outright. A partial
index scoped to non-cancelled rows preserves the original guarantee (at most one *active* Booking
per Slot, ever, at any moment) while allowing unlimited historical cancelled rows to accumulate on
the same Slot over repeated book/cancel/rebook cycles.

**Alternatives considered**: Deleting the cancelled Booking instead (keeping the flat index
unchanged) — this was Clarifications' rejected option (mirrors 025-walk-in's no-show-reclaim
precedent, but explicitly not chosen here since this codebase's broader retention-aware design
already keeps records with lifecycle awareness rather than deleting them, e.g. 033/034's
anonymization/purge lifecycle existing at all presupposes records are kept until their own
lifecycle says otherwise).

## R3: Concurrency race-closure — a data-layer-guarded conditional update, not a plain read-then-write

**Decision**: `BookingRepository` gains `@Modifying @Query("UPDATE Booking b SET b.status = 'CANCELLED' WHERE b.id = :id AND b.status <> 'CANCELLED'") int cancelIfActive(UUID id)`.
`BookingCancellationService.cancel(bookingId)` calls this as the actual guarantee; a return value
of `0` means a concurrent caller already won the race, mapped to the same rejection as an
already-cancelled Booking. An upfront `findById` + Slot-status fast-path check still runs first,
for a fast, clear rejection in the non-racing common case — the conditional update is what FR-008/
SC-005 actually rely on.

**Rationale**: FR-008/SC-005 explicitly require the race-closure to be enforced "at the data
layer," not merely by application-level `if` logic — this codebase's own most recent precedent for
exactly this class of requirement is 036's convergence fix to `NotificationEventService
.markActioned`, which replaced a read-then-check-then-write method with a data-layer-guarded
conditional `@Modifying` update for the identical reason (a concurrent writer could otherwise
silently win a lost-update race). `ClinicVerificationService.unverify`'s plain read-then-write
`@Transactional` method was considered but is *not* the right precedent here — that method has no
stated concurrency requirement in its own spec, unlike this feature's explicit FR-008.

**Alternatives considered**: Optimistic locking via a `@Version` column. Rejected — no entity in
this codebase uses one yet, and a conditional update achieves the identical guarantee with less
new machinery (Constitution II), consistent with the `@Modifying`-update precedent this session
already established.

## R4: Waitlist bump trigger — a plain `ApplicationEvent`-style POJO, published only on the genuine transition

**Decision**: `BookingCancelledEvent(UUID bookingId, UUID slotId, Instant occurredAt)` — a plain
record (not extending `ApplicationEvent`), published via `ApplicationEventPublisher.publishEvent`
only after `cancelIfActive` confirms the transition actually happened (not on a lost race, not on
an already-cancelled Booking). No listener exists yet.

**Rationale**: This is the third time this session's backlog needs the "publish now, consumer
built later" shape — `ClinicDeVerifiedEvent` (003→008) and `NotificationEventPublishedEvent`
(036→037) are the exact precedents, both plain POJO records for the same "modern Spring supports
arbitrary event objects" reason. Publishing only after the conditional update succeeds is what
makes SC-003's "exactly one... never zero, never more than one" true even under a concurrent
race — the loser's call never reaches the publish line at all.

**Alternatives considered**: A stored "pending waitlist bump" row instead of a transient event, so
028 (not yet built) could poll for unprocessed bumps rather than needing to be online to receive a
live Spring event at the moment of cancellation. Rejected — out of scope per this feature's own
Explicitly Out of Scope section ("this feature only needs to guarantee that a matching/claim
consumer built later has something reliable to hook into"); 028 is free to choose its own consumption
model (a `@TransactionalEventListener` mirroring 037's own precedent is the most likely one, but
that's 028's decision to make, not this feature's).

## R5: Shared cancellation core, two thin authorization-first controllers

**Decision**: One `BookingCancellationService.cancel(Booking booking)` performs the entire
concurrency-sensitive core (status/slot-state gate, R3's atomic update via `booking.getId()`,
Slot release, R4's event publish) — it takes an already-fetched `Booking`, not a bare `bookingId`,
mirroring `QueuePositionService.positionOf(Booking)`'s shape after 027's own convergence pass
removed its unused bare-ID overload for the identical reason (analyze finding E1 here, applying
that lesson proactively instead of repeating it). `StaffBookingCancellationController` and
`PatientBookingCancellationController` each do their own single fetch-and-filter (staff: clinic
scoping + any active role, no time restriction; patient: ownership + the 2-hour cutoff), then pass
that one fetched Booking into the shared core.

**Rationale**: Unlike 016/017's or 020/021's separate staff/patient service classes (which each
own their *entire* method, including business logic that only lightly overlaps, e.g.
`resolveOrCreatePatient`), this action's core is a single concurrency-sensitive database
transition that must behave identically regardless of caller. Duplicating R3's conditional-update
logic across two services would double the surface area for the exact bug class 036's convergence
pass already found once this session (a race-closure that looks right but doesn't actually apply
where it matters) — sharing it structurally rules that out. This mirrors 027's own "one shared
computation, two thin controllers" shape, adapted for a write instead of a read.

**Alternatives considered**: Full duplication, matching 016/017's precedent exactly. Rejected for
this specific action given the concurrency-critical core described above — 027's more recent
precedent (a shared computation behind two callers) is the better fit than 016/017's older one when
the shared part is the correctness-critical piece, not an incidental convenience helper.

## R6: Patient 2-hour cutoff — computed the same way 023's delay figure combines Slot/Session time

**Decision**: `PatientBookingCancellationController`/its authorization step computes
`LocalDateTime.of(session.getSessionDate(), slot.getStartTime())` and compares it to
`LocalDateTime.now().plusHours(2)` — cancellation is allowed only if the scheduled time is still
at or after that threshold.

**Rationale**: Directly reuses the same `Session.sessionDate` + `Slot.startTime` → comparable
instant technique 023's `SessionDelayService.recalculate` and 021's no-show sweep both already use
for the identical Fixed-Time-only combination — no new time-handling pattern needed.

## R7: This feature is Fixed-Time-only — reuses `NotAFixedTimeSessionException`

**Decision**: Both cancellation paths reject a Queue-mode Booking with the existing
`NotAFixedTimeSessionException` (025/026), the same reused exception every prior Fixed-Time-only
feature this session already uses.

**Rationale**: FR-009 states this explicitly; no new exception type is warranted for a condition
this codebase already has a name for.
