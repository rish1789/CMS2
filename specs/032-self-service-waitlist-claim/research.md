# Research: Self-Service Waitlist Claim

## R1: Extend 028's existing `com.cms.waitlist` module — no new module

**Decision**: Every new type this feature adds lives in the existing `com.cms.waitlist` package
(and `dto` subpackage), alongside 028's own `WaitlistEntry`/`WaitlistJoinService`/
`WaitlistMatchingService`.

**Rationale**: This feature is entirely about the lifecycle of the same `WaitlistEntry` 028
already defined — there is no new module boundary the constitution's own named list would
suggest carving out.

## R2: Claim reuses `PatientBookingService.bookSlot` directly for booking creation

**Decision**: `WaitlistClaimService.claim(...)` does not create a `Booking` itself — it calls the
existing `PatientBookingService.bookSlot(patientAccountId, clinicId, slotId, BookSlotInput)`
(021), the same fee-resolution-first-write-gate path, race-closure-via-unique-constraint, and
patient-linking logic every other patient-initiated booking already goes through.

**Rationale**: Constitution II — a claim *is* a booking, created by a different trigger than
picking an open slot directly; reimplementing fee resolution, patient linking, or the
`uq_booking_slot_active` race-closure a second time here would duplicate exactly the logic 021
already got right, for no behavioral difference.

**Alternatives considered**: A parallel `WaitlistClaimService`-owned booking-creation path.
Rejected — pure duplication with no stated requirement that claiming behave any differently from
an ordinary booking once the slot and fee are known.

## R3: The claim request supplies `appointmentTypeId` and `patientName` at claim time

**Decision**: `ClaimWaitlistRequest` carries `appointmentTypeId` and `patientName` — neither was
captured when the patient joined the waitlist (028's `WaitlistEntry` only ever recorded a doctor
or specialization preference, spec.md 031).

**Rationale**: `PatientBookingService.bookSlot` requires both (`BookSlotInput`), and there is no
reasonable way to infer an appointment type from a bare doctor/specialization preference recorded
possibly days earlier. Asking for it at the moment of claim — when the patient actually knows
they're about to take the slot — mirrors how every other patient booking already collects it.

## R4: Claim ordering — guard-claim the entry first, then attempt the booking

**Decision**: `WaitlistClaimService.claim(...)`:
1. Loads the entry scoped to `id` AND `patientAccount.id` (ownership, R8).
2. Calls `WaitlistEntryRepository.claimIfOffered(id, now)` — a conditional update
   (`WHERE status = 'OFFERED' AND offer_expires_at > :now`) setting `status = CLAIMED`. `0` rows
   updated → `WaitlistOfferNotClaimableException` (covers "not `OFFERED`," "already resolved by a
   concurrent action," and "window already lapsed" uniformly).
3. Only once that succeeds, calls `PatientBookingService.bookSlot(...)` for the entry's
   `offeredSlot`. If that throws `SlotAlreadyBookedException` (the offered Slot was taken through
   the ordinary booking flow first, per the Clarifications decision), the entry is **not** left
   `CLAIMED` with no `Booking` behind it — the same transaction explicitly pivots it to `EXPIRED`
   and re-runs matching (R6/FR-004a), then re-throws so the caller sees the failure.

**Rationale**: Doing the entry-level guard first makes "did this claim attempt win the entry"
unambiguous before ever touching the Slot; the explicit pivot-on-`SlotAlreadyBookedException` step
avoids the alternative (letting the exception roll back the whole transaction) silently reverting
the entry to `OFFERED` as if nothing happened, which would contradict FR-004a's requirement that a
losing claim still advances the waitlist to the next entry.

**Alternatives considered**: Book the Slot first, guard-claim the entry second. Rejected — a
concurrent decline/expiry could then win the entry-level guard after a real `Booking` already
exists for it, leaving the entry `EXPIRED` despite the patient actually holding the slot; ordering
the entry guard first avoids that dangling state entirely.

## R5: Decline and expiry share one core: `WaitlistReleaseService.release(entry)`

**Decision**: Both `WaitlistClaimService.decline(...)` and `WaitlistExpirySweepService`'s sweep
call the same `WaitlistReleaseService.release(WaitlistEntry entry)`: a conditional update
(`WaitlistEntryRepository.expireIfOffered`, `WHERE status = 'OFFERED'`) setting `status =
EXPIRED`, and — only if that update won — reloads the entry's `offeredSlot`'s `Session` and calls
`WaitlistMatchingService.matchAndOffer(session, slot)` again (028) to search for the
next-longest-waiting eligible entry.

**Rationale**: The spec's own framing ("triggers the same cascade behavior as an expiry") states
these are one mechanism with two triggers, not two mechanisms — Constitution II.

**No explicit recursion needed for FR-008's "repeat until claimed or exhausted"**: each
`release()` call performs exactly one re-match attempt. If that finds a new entry, it's `OFFERED`
with a **fresh, non-lapsed** window — so it can never spuriously qualify for the *same* expiry
sweep pass that just triggered it. The "repeat until exhausted" behavior emerges naturally over
subsequent decline/expiry events on that newly-offered entry, exactly mirroring how 023's own
no-show sweep processes whatever is currently eligible each time it runs, not via an in-process
loop.

## R6: Expiry sweep is a real `@Scheduled` job, mirroring 023 exactly

**Decision**: `WaitlistExpirySweepService.sweepExpiredOffers()` (finds every `OFFERED` entry with
`offerExpiresAt` before now, releases each) is fired by `WaitlistExpirySweepTrigger`, a thin
`@Component` with `@Scheduled(cron = "0 * * * * *")` — the identical cadence and
service/trigger split as `NoShowDetectionService`/`NoShowDetectionTrigger` (023).

**Rationale**: Spring's task scheduler is already in production use in this codebase for exactly
this class of concern ("notice a time-based condition with no user action required") — reusing it
is simpler than inventing an HTTP-triggered manual-sweep alternative, and a 1-minute cadence
against a 30-minute window is comfortably precise enough with no stated tighter requirement.

## R7: `WaitlistMatchingService` (028) is extended in place to persist `offeredSlot`

**Decision**: `WaitlistMatchingService.matchAndOffer(Session, Slot)` now also sets the matched
entry's `offeredSlot` (alongside `offeredAt`/`offerExpiresAt`) via the same conditional
`offerIfWaiting` update, extended with one more column.

**Rationale**: 028 never needed to remember which Slot a notified entry referred to (its own
scope ended at notifying); this feature is the first consumer that needs to act on that
information again, both to book the *right* slot on claim and to re-match against the *same* slot
on decline/expiry. Extending 028's existing service in place — rather than adding a parallel
"029 version" of matching — keeps exactly one implementation of the tier-priority search.

## R8: Ownership check mirrors `PatientBookingCancellationController`'s pattern — 404, not 403

**Decision**: `WaitlistClaimService` looks up the entry by `id` filtered to
`patientAccount.id = callerId`; a non-owned or nonexistent entry both produce
`WaitlistEntryNotFoundException` (404), never a 403.

**Rationale**: Exactly 028's own `PatientBookingCancellationController` precedent (never 037's
own scoped-to-clinic-membership 403 pattern) — avoids confirming to an unauthorized caller that a
given entry ID exists at all.

## R10: `WaitlistMatchingService.matchAndOffer` gains a Slot-still-`OPEN` guard

**Decision**: `matchAndOffer(Session, Slot)` checks `slot.getStatus() == SlotStatus.OPEN` before
searching for a candidate at all; if not, it no-ops (no query, no offer).

**Rationale**: 028's own call path (`WaitlistBumpListener`, reacting to `BookingCancelledEvent`)
always calls this with a Slot `BookingCancellationService` just set `OPEN`, so this guard is a
pure no-op there — purely additive safety, not a behavior change to 028. It becomes load-bearing
for 029's own reuse: `WaitlistReleaseService.release(...)` (R5) re-runs `matchAndOffer` against
the *same* Slot a claim just lost (research.md R4's `SlotAlreadyBookedException` pivot), and
without this guard it would incorrectly offer an already-`BOOKED` Slot to yet another waitlist
entry — a false promise that entry could never actually claim (quickstart Scenario 5).

**Alternatives considered**: Put the check at each of 029's own call sites instead of inside
`matchAndOffer` itself. Rejected — two call sites duplicating the same guard is worse than one
method guaranteeing its own precondition, and there's no scenario where offering an already-taken
Slot is ever correct for *either* caller.

## R11: The claim-failure pivot (R4) requires `Propagation.REQUIRES_NEW`, not a plain in-line write

**Decision**: `WaitlistClaimService.claim`'s `catch (SlotAlreadyBookedException e)` block delegates
to `WaitlistReleaseService.releaseById(entryId)` — a separate method, on a separate bean, annotated
`@Transactional(propagation = Propagation.REQUIRES_NEW)` — rather than mutating the entry in-line
and calling `WaitlistMatchingService.matchAndOffer` directly inside `claim`'s own transaction.

**Rationale**: Found during implementation, not merely a style preference. `PatientBookingService
.bookSlot` (021) is itself `@Transactional` and, invoked via normal bean injection, *participates*
in `claim`'s already-active transaction (does not start a new one). When `bookSlot` throws
`SlotAlreadyBookedException`, Spring's transaction advice marks that *shared* transaction
rollback-only at the moment the exception leaves `bookSlot`'s own boundary — before `claim`'s catch
block ever runs — and `claim` itself then re-throws the same exception out of its own
`@Transactional` method, which independently triggers a full rollback of everything in that
transaction. An in-line pivot (`entry.expire()` + `save` + `matchAndOffer`, all still inside that
same doomed transaction) would therefore silently vanish along with the failed claim, leaving the
entry looking untouched (reverted to its pre-claim `OFFERED` row) instead of `EXPIRED` with the
next entry offered — directly contradicting FR-004a. `REQUIRES_NEW` suspends the doomed outer
transaction and commits the release independently; `releaseById` re-loads the entry fresh by id
(rather than reusing `claim`'s own managed reference) since that reference belongs to the
soon-to-be-rolled-back transaction's persistence context, and — under normal `READ_COMMITTED`
isolation — correctly observes the entry as still genuinely `OFFERED` (the participating
transaction's own uncommitted `CLAIMED` write is invisible to it, and never survives anyway).

**Alternatives considered**: Catching and handling the failure inside `PatientBookingService`
itself. Rejected — `SlotAlreadyBookedException` is a generic, correct signal for *any* caller of
`bookSlot`; teaching a shared, reused service about one specific caller's (waitlist claim's)
compensating behavior would violate its own existing, already-proven contract (research.md R2).

## R9: Claiming/declining is patient-self-service only — no staff-on-behalf path

**Decision**: Only `PatientWaitlistClaimController` exists; there is no staff-facing equivalent.

**Rationale**: Unlike 028's join action (which the source material explicitly frames as usable by
staff "on the patient's behalf," e.g. a phone call), this feature's entire stated purpose is
*removing* the phone-call/staff-mediated path for claiming — spec Assumptions.
