# Research: Session Delay Tracking (Fixed-Time Only)

## R1: Module placement — `com.cms.scheduling`, not `com.cms.booking`

**Decision**: `SlotCompletionService`, `SlotCompletionController`, `SessionDelayService`, and
`SessionDelayController` all live in `com.cms.scheduling` — a new self-contained slice of that
module, not `com.cms.booking`.

**Rationale**: Marking a Slot completed is purely a `Slot.status` transition (no `Booking`,
`Patient`, or fee involvement at all — unlike 016/020/025's staff-initiated actions, which all
create a `Booking`). It only needs `SlotRepository`/`SessionRepository`, both already
`com.cms.scheduling`-owned. Keeping it there means `WalkInInsertionService` (025,
`com.cms.booking`) calling into `SessionDelayService.recalculate(...)` preserves this codebase's
one-directional dependency rule established by 022's `RiskBasedBufferSlotCalculator`
research.md: `com.cms.booking` depends on `com.cms.scheduling`, never the reverse. Placing the new
services in `com.cms.booking` instead would either violate that direction (scheduling code reaching
into booking) or force an awkward split.

**Alternatives considered**: `com.cms.booking`, mirroring 016/020/025's location since this is
still a "staff marks something about a Slot" action. Rejected — the action touches no
`com.cms.booking`-owned entity at all; placing it there would be the first place in this codebase
where `com.cms.booking` code doesn't actually need anything `com.cms.booking` owns, purely for
the sake of matching other staff actions' location rather than its actual data dependencies.

## R2: Delay storage — a nullable column on `Session`, recalculated and written by `SessionDelayService`

**Decision**: `Session` gains a nullable `delayMinutes` (Integer) column. `SessionDelayService
.recalculate(sessionId)` is the only method that ever writes it — it re-derives the value from
current `Slot` state and overwrites the stored figure. Reading it (`SessionDelayService
.currentDelay(sessionId)` / the `GET .../delay` endpoint) never recomputes — it only returns
what's stored.

**Rationale**: FR-005 and the source material's "stale-but-correct-as-of-last-trigger by design"
requirement are explicit that reads must never trigger recomputation — a query at any wall-clock
time between triggers must return the exact same value. A stored, write-on-trigger /
read-verbatim column is the only design that satisfies this; computing live at query time would
produce a different (and wrong, per spec) answer every time more time has passed.

**Null semantics**: `null` uniformly means "no outstanding delay" — collapsing two cases FR-004's
own wording explicitly treats as equivalent ("zero/absent"): a Session that has never had a
trigger point occur yet (US3 AC3), and a Session whose last recalculation found no still-unresolved
past-due Slot. No separate "never triggered" state is modeled — Constitution II: nothing in the
spec's acceptance criteria distinguishes the two, so a single `null` representation is the simplest
design that satisfies every acceptance scenario.

**Alternatives considered**: A separate `SessionDelay` entity/table keyed by `session_id`, or a
non-nullable `0`-default column. Rejected — a 1:0..1 scalar value with no independent lifecycle
doesn't warrant its own table (same reasoning 025's `Booking.overrideReason` column used); a
non-nullable `0` default would make "no delay" and "computed exactly zero" indistinguishable from
"delay of zero minutes," which is fine semantically here but a nullable column reads more honestly
as "there is no outstanding delay" rather than implying a phantom zero-minute measurement always
exists.

## R3: `SlotStatus.COMPLETED` — extend the existing enum in place

**Decision**: Add `COMPLETED` as a fourth `SlotStatus` value, reachable only from `BOOKED`.

**Rationale**: Mirrors this session's own established precedent exactly — `BOOKED` was added by
016, `NO_SHOW` by 021, each extending the enum in place rather than introducing a parallel
completion concept on `Booking`. The spec's own business rule describes the *Slot's* status
changing ("a slot being marked completed" / "status open/booked, not completed"), not the
Booking's, consistent with how every prior Slot-lifecycle feature phrased its own rules.

**Alternatives considered**: A `completed` boolean/timestamp on `Booking` instead. Rejected — the
delay computation (R4) already reads exclusively from `Slot.status`/`Slot.startTime`; adding a
second, `Booking`-side field for the same concept would mean checking two entities for what is a
single state transition, and there is no requirement (e.g. an audit trail needing a
completed-*when*) that only a timestamp could satisfy that a fourth enum value cannot.

## R4: Delay computation — earliest OPEN/BOOKED Slot whose `sessionDate` + `startTime` has passed

**Decision**: `SessionDelayService.recalculate(sessionId)`:
1. Loads the `Session` and all its `Slot`s (`SlotRepository.findBySession_Id`, the same query
   025's priority search already reuses).
2. Filters to `status == OPEN || status == BOOKED` (excludes `COMPLETED` and `NO_SHOW` — the
   spec's business rule says "status open/booked, not completed," and `NO_SHOW` slots are already
   established elsewhere in this codebase as a *resolved* outcome, not a still-outstanding one).
3. Combines `session.getSessionDate()` + `slot.getStartTime()` into a `LocalDateTime`, filters to
   those strictly before "now," and takes the minimum by `startTime` (the earliest).
4. If found, stores `Duration.between(thatDateTime, now).toMinutes()` (always a positive integer,
   since the filter already guarantees it's in the past); if not found, stores `null` (R2).

**Rationale**: A direct, literal implementation of the spec's own formula (FR-004) — no
aggregation query needed since a Session's Slot count is small (bounded by one clinic-day's
schedule, the same scale 021/025 already treat as Java-side-filterable).

**Alternatives considered**: A single JPQL query computing the minimum directly in SQL. Rejected —
combining `Session.sessionDate` (a date) with `Slot.startTime` (a time) into one comparable instant
isn't a clean single SQL predicate across this join, the same reasoning 021's
`findBookedFixedTimeCandidatesForNoShow()` research.md already documented for an almost identical
join shape; Java-side filtering is simpler and already-proven at this scale.

## R5: Two trigger call sites — `SlotCompletionService` (new) and `WalkInInsertionService` (025, extended in place)

**Decision**: `SlotCompletionService.completeSlot(...)` calls
`sessionDelayService.recalculate(sessionId)` as its last step, in the same transaction, after
flipping the Slot to `COMPLETED`. `WalkInInsertionService.insertWalkIn(...)` (025, already
converged) gets one new line added at its own last step — the same call — after a successful
insertion.

**Rationale**: Both trigger points are described in the spec as happening "as part of the same
action," not as a fire-and-forget async event — a plain synchronous call, in the same transaction,
is the direct, literal reading of that requirement. This is the second time this session's backlog
extends an already-converged feature in place to wire in a new call site it structurally
anticipated (037 wiring into 036's `publish()` is the precedent for "genuinely extending converged
code," as opposed to 022's swap-in-a-new-`@Primary`-bean upgrade of 012).

**Alternatives considered**: A Spring `ApplicationEvent` (mirroring 003→008's `ClinicDeVerifiedEvent`
or 036→037's `NotificationEventPublishedEvent`) published by both action sites and consumed by a
single listener. Rejected — those event-based precedents exist specifically because the *emitter*
either didn't yet exist when the consumer feature shipped (008, 037) or needed to stay decoupled
from arbitrary future emitters (036). Here, both emitters (`SlotCompletionService`,
`WalkInInsertionService`) already exist in the same build and this feature is their only consumer
— a direct method call is simpler (Constitution II) with no decoupling benefit an event would add.

## R6: Querying delay — a dedicated `GET` endpoint, response distinguishes "not applicable" from "applicable, zero"

**Decision**: `GET /api/v1/clinics/{clinicId}/sessions/{sessionId}/delay` returns
`{ "sessionId": "...", "applicable": true|false, "delayMinutes": number|null }` — `applicable`
is `false` only for a Queue-mode Session (`delayMinutes` always `null` alongside it); for a
Fixed-Time Session, `applicable` is always `true` and `delayMinutes` is the stored figure (`null`
= no outstanding delay per R2).

**Rationale**: FR-007 explicitly requires a Queue-mode Session's query to "indicate no delay figure
exists, not an error and not zero-as-a-computed-value" — a bare `delayMinutes: number|null` field
alone can't distinguish "Fixed-Time, currently no delay" from "Queue-mode, concept doesn't apply,"
both of which would otherwise serialize identically as `null`. The explicit `applicable` boolean
resolves that without needing an error status for an otherwise-valid, existing Session.

**Alternatives considered**: `404`/`409` for a Queue-mode Session's delay query. Rejected — FR-007
explicitly says "not an error"; the Session itself exists and is valid, it simply has no delay
concept, which is a normal, successful response shape, not an error condition.

## R7: Authorization — reuse 016/020/025's exact pattern, not `ScheduleService`'s doctor-inclusive one

**Decision**: `SlotCompletionService.requireAuthorized` duplicates the same
`RoleAssignmentRepository.existsByAccount_IdAndClinic_IdAndRoleAndActiveTrue` check for
`Operations`/`ClinicAdmin` used by `StaffBookingService`/`StaffQueueBookingService`/
`WalkInInsertionService` — not `ScheduleService`'s existing `com.cms.scheduling`-local
`requireAuthorized`, which is doctor-inclusive (the doctor themselves, or a ClinicAdmin).

**Rationale**: Clarifications (Session 2026-09-04) confirmed staff-only, explicitly excluding the
Doctor — this must NOT reuse `ScheduleService`'s existing helper of the same name in the same
package, since that one's whole purpose is letting the doctor manage their own schedule. Reusing it
here would silently let a Doctor mark their own Slots completed, contradicting the clarified
decision. A new, separately-named private method with the exact 016/020/025 shape avoids that
name collision and behavior drift.

**Alternatives considered**: Generalizing `ScheduleService.requireAuthorized` with a parameter for
whether the doctor is included. Rejected — an unjustified abstraction for two call sites with
opposite authorization rules and no third caller in sight (Constitution II); this codebase's own
precedent is to duplicate these small authorization checks per service already (research.md R4 of
025 documents this exact same reasoning for `resolveOrCreatePatient`).
