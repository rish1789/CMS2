# Phase 0 Research: Automatic No-Show Detection

## Decision: No self-invoked `@Transactional` helper for the per-Slot update

**Decision**: `NoShowDetectionService`'s sweep method fetches candidate Slots via a plain
read, filters by elapsed grace period in Java, then for each candidate calls
`slotRepository.save(slot)` directly — no separate `@Transactional`-annotated helper method
called via `this.` from within the same class.

**Rationale**: This session found the identical bug twice already in this exact codebase area:
020's original `StaffBookingService` used `save()` where `saveAndFlush()` was needed; 022's
first implementation of `StaffQueueBookingService`/`PatientQueueBookingService` introduced a
`createBooking(...)` helper marked `@Transactional` but invoked via self-invocation, silently
bypassing Spring's AOP proxy and making the annotation a no-op. `slotRepository.save(slot)` is
already independently atomic via Spring Data JPA's own repository-level transaction
demarcation — there is nothing here that needs an additional transactional wrapper, and adding
one via a same-class helper would only reintroduce the same risk for no benefit.

**Alternatives considered**: A single bulk `@Modifying @Query` UPDATE (like
`NotificationEventRepository.expireDuePending`) — rejected because the grace-period comparison
needs `Session.sessionDate` (a `LocalDate`) combined with `Slot.startTime` (a `LocalTime`) into
a single comparable instant, which is awkward to express portably as a single SQL predicate
across the `slot`/`session` join; filtering in Java after a simple read is clearer and, per
011/015's own established "per-item loop, not a single all-or-nothing bulk operation" precedent
(`SessionGenerationService.generate`'s per-schedule try/catch), keeps one bad Slot from ever
blocking the rest of the sweep — the same resilience reasoning already applied there.

## Decision: `SlotStatus` gains `NO_SHOW`; no new `Booking`-level status

**Decision**: `SlotStatus` becomes `OPEN, BOOKED, NO_SHOW` (extended in place, exactly as 020
extended it from `OPEN`-only to add `BOOKED`). No new field or status is added to `Booking`.

**Rationale**: The acceptance criteria and business rules consistently describe the *Slot*
as what changes state ("marks the slot NO_SHOW," "slot status reflects reality," "unattended
slots become eligible for walk-in reclaim") — this is a Slot-level lifecycle transition, not a
Booking-level one, and the codebase already has an established, proven pattern for exactly this
shape of change.

**Alternatives considered**: A `Booking.status` field — rejected; nothing in the spec describes
a Booking itself changing state, and 020-walk-in-priority-insertion (spec's own stated
downstream consumer) explicitly needs to find *Slots* eligible for reclaim, which is a Slot
query either way.

## Decision: `onHold` is a plain boolean on `Slot`, not part of the status enum

**Decision**: `Slot` gains a separate `onHold` boolean column (default `false`), not a new
`SlotStatus` value.

**Rationale**: A hold is orthogonal to a Slot's booking lifecycle — a held Slot could plausibly
still be `OPEN` or `BOOKED` (this feature only needs to know "is it held," not "held instead of
what"). Folding it into the status enum would force awkward combined states like a hypothetical
`BOOKED_AND_HELD`. A separate flag composes cleanly with the existing enum and mirrors
`PatientAccount.pushOptIn`/`smsOptIn`'s own precedent: a plain boolean with a getter and setter,
no HTTP endpoint or UI wired to it yet, added ahead of the not-yet-built feature that will
actually set it (Clarifications).

## Decision: No new repository query for FR-005's "queryable" requirement

**Decision**: This feature adds no `BookingRepository`/`SlotRepository` method for "no-show
history per patient, scoped by date." FR-005 is satisfied structurally: once `Slot.status =
NO_SHOW` exists and is set correctly, and given `Booking` already has `slot` and `patient`
associations, a future feature (022-buffer-slot-capacity-sizing) can write exactly the query it
needs, when it needs it.

**Rationale**: Constitution II — building a query method with no current caller and no test
that exercises it (since nothing in this feature or any already-converged feature calls it) is
speculative generality. This mirrors 015's `FeeResolutionService` precedent: that feature built
its full service contract but deliberately no HTTP endpoint, since 016 (its first real caller)
didn't exist yet either — the difference here is even smaller, since even the *query method
itself* isn't needed until its actual future caller defines exactly what shape it wants
(a raw count? a list of dates? paginated?) — guessing that shape now would likely guess wrong.

## Decision: sweep runs every 1 minute via `@Scheduled(cron = "0 * * * * *")`

**Decision**: `NoShowDetectionTrigger` mirrors `NightlySessionGenerationTrigger`'s
`@Scheduled`/`@Component` shape exactly, but with a 1-minute cron instead of a once-nightly one.

**Rationale**: The grace period itself is only 10 minutes; a once-nightly sweep (appropriate for
015's own session-generation job) would leave a no-show Slot incorrectly showing `BOOKED` for
up to nearly a full day, undermining the feature's own stated purpose ("slot status reflects
reality," walk-in reclaim eligibility). No acceptance criterion depends on the exact interval
chosen (SC-001 only says "within one sweep cycle"), so this is an implementation-level default,
not a scope decision — 1 minute is a reasonable, conservative choice that keeps the sweep's own
per-run cost trivially small (a handful of candidate rows at most, in any realistic clinic
scale) without requiring sub-minute precision no acceptance criterion asks for.

**Alternatives considered**: A fixed-delay executor triggered by application startup — rejected
in favor of `cron`, matching the existing codebase's own established `@Scheduled` style exactly
(`NightlySessionGenerationTrigger`).
