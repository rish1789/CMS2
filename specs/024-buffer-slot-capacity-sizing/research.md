# Phase 0 Research: Buffer Slot Capacity Sizing

## Decision: the new calculator lives in `com.cms.booking`, not `com.cms.scheduling`

**Decision**: `RiskBasedBufferSlotCalculator implements com.cms.scheduling.BufferSlotCalculator`
is a class in the `com.cms.booking` package, marked `@Primary` so Spring's existing
constructor-injection of `BufferSlotCalculator` into `SlotGenerationService` picks it up
automatically with zero changes to `SlotGenerationService` itself.

**Rationale**: The calculator's real logic needs `BookingRepository` (to know which Slots in
the trailing window were actually booked, and which of those are `NO_SHOW`) — a
`com.cms.booking` concern. `com.cms.booking` already depends on `com.cms.scheduling` (its
`StaffBookingService`/`PatientBookingService`/etc. already inject `SlotRepository`/
`SessionRepository` directly, an established precedent for cross-module read access in this
codebase). `com.cms.scheduling`, by contrast, has zero dependency on `com.cms.booking` today.
Putting the new implementation in `com.cms.scheduling` and having it reach into
`com.cms.booking.BookingRepository` would create the first-ever circular package dependency in
this codebase — a real architectural smell Constitution III's modular-boundary principle exists
to prevent. Spring does not require an interface's implementation to live in the same package
as the interface, so implementing `com.cms.scheduling.BufferSlotCalculator` from
`com.cms.booking` costs nothing and keeps the dependency graph one-directional.

**Alternatives considered**: Moving `BookingRepository` (or a subset of Booking-querying logic)
into `com.cms.scheduling` — rejected; `Booking` is unambiguously a `com.cms.booking`-owned
entity (015/016 already established this), and splitting its repository across two modules
would be a worse violation of module cohesion than the chosen approach.

## Decision: `ColdStartBufferSlotCalculator` is reused, not duplicated

**Decision**: `RiskBasedBufferSlotCalculator` takes `ColdStartBufferSlotCalculator` as a
constructor dependency (by its concrete type, not the `BufferSlotCalculator` interface type —
avoiding any Spring bean-selection ambiguity) and delegates to it directly for the
below-5-samples branch.

**Rationale**: `ColdStartBufferSlotCalculator`'s entire body is `return 1` — 018's own already-
converged, already-correct transcription of this feature's own "<5 samples → 1 slot" rule.
Reusing it via delegation (rather than re-writing `return 1` a second time) keeps that rule
defined in exactly one place, consistent with Constitution II.

## Decision: sample size and no-show rate, computed via one new repository query

**Decision**: `BookingRepository` gains one query:

```java
@Query("SELECT b FROM Booking b WHERE b.slot.session.doctorProfile.id = :doctorProfileId "
     + "AND b.slot.session.mode = com.cms.scheduling.ScheduleMode.FIXED_TIME "
     + "AND b.slot.session.sessionDate BETWEEN :windowStart AND :windowEnd")
List<Booking> findByDoctorAndFixedTimeWindow(
        @Param("doctorProfileId") UUID doctorProfileId,
        @Param("windowStart") LocalDate windowStart,
        @Param("windowEnd") LocalDate windowEnd);
```

`RiskBasedBufferSlotCalculator` calls this once per `calculateBufferSlotCount` invocation with
`windowStart = referenceDate.minusDays(90)`, `windowEnd = referenceDate` (the session's own
`sessionDate` — the date session generation is producing this Session *for*, not
`LocalDate.now()`, since a 15-day-horizon generation run can be computing sessions for dates
several days in the future — see the next decision). Sample size is the returned list's size;
no-show count is how many of those have `slot.status = NO_SHOW`. Only `FIXED_TIME` mode is
queried, since 023's no-show detection is itself Fixed-Time-only (nothing in `com.cms.booking`
gains from also fetching Queue-mode bookings this feature has no use for).

**Alternatives considered**: Two separate `COUNT(...)` queries (sample size, no-show count)
instead of fetching the list — rejected as a premature optimization; realistically-sized
90-day windows per doctor are small (tens, not thousands, of rows), and fetching once avoids a
second round trip for no real benefit at this scale.

## Decision: the trailing window ends at the *Session's own* date, not `LocalDate.now()`

**Decision**: `calculateBufferSlotCount(Session session)` uses `session.getSessionDate()` as
the window's upper bound, not the wall-clock date the calculation happens to run on.

**Rationale**: `SessionGenerationService.generate(runDate)` generates Sessions across a 15-day
horizon starting at `runDate` — a Session being generated for `runDate + 10` should have its
buffer sized as of *that* date's own trailing history, not the (earlier) date the generation
job itself happens to be running on. Since no-show data only accumulates in the *past* relative
to real time regardless, this mostly matters at the margins, but using the Session's own date
is the more correct, well-defined reference point and costs nothing extra to implement (the
`Session` passed to the calculator already carries it).

## Decision: rounding uses standard round-half-up; not specified by any acceptance criterion

**Decision**: `bufferCount = Math.round(percentage * totalSlots)`, then capped at 3.

**Rationale**: No acceptance criterion in spec.md depends on the exact rounding method — they
test only threshold-exceeded behavior (caps) and the zero-rate case (0% always rounds to 0
regardless of method). `Math.round` is Java's standard, unsurprising default; picking anything
else would be arbitrary without a stated reason to.
