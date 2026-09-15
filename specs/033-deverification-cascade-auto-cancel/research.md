# Research: De-Verification Cascade (Auto-Cancel Future Bookings)

## R1: Trigger 1 consumes the existing `ClinicDeVerifiedEvent` (003) — no new event

**Decision**: `DeVerificationCascadeListener` adds a `@TransactionalEventListener(phase =
AFTER_COMMIT)` method for `com.cms.identity.admin.ClinicDeVerifiedEvent`, which
`ClinicVerificationService.unverify()` already publishes on every genuine `true -> false`
transition.

**Rationale**: That event's own documentation states this feature would add the listener; it
already carries exactly what's needed (`clinicId`). Inventing a second event for the same trigger
would be pure duplication.

## R2: Trigger 2 needs a new admin action + a new, analogous event

**Decision**: `DoctorVerificationService` gains `revoke(UUID doctorProfileId)`, mirroring
`ClinicVerificationService.unverify()`'s exact idempotent shape (only a genuine
`true -> false` transition writes the flag and publishes an event). A new
`DoctorLicenseRevokedEvent(doctorProfileId, occurredAt)` record, placed alongside
`ClinicDeVerifiedEvent` in `com.cms.identity.admin`, mirrors its shape exactly.

**Rationale**: No such explicit revoke action exists anywhere in the codebase — `verify()` is
one-way, and `edit()`'s automatic reset is explicitly required to *never* trigger this cascade
(FR-002/FR-009), so it cannot double as Trigger 2's source. A new, narrowly-scoped action is the
minimal necessary prerequisite (Constitution II) — not a general "set verification status"
setter that could be misused to also flip it back to `true` outside of `verify()`'s own path.

**Alternatives considered**: Overload `edit()` with an optional "and also revoke" flag. Rejected
— would conflate two semantically different actions (an editorial correction vs. a punitive
revocation) behind one method, and risks a future caller accidentally triggering the cascade via
an ordinary edit.

## R3: The cascade service lives in `com.cms.booking`, not `com.cms.identity.admin`

**Decision**: `DeVerificationCascadeService` (the actual booking-cancellation logic) and
`DeVerificationCascadeListener` (the two event listeners) both live in `com.cms.booking`.

**Rationale**: The cascade's entire job is finding and cancelling Bookings — exactly what
`BookingCancellationService`/`SessionCancellationService`/`SessionPartialCancellationService`
already do, all placed in `com.cms.booking`. Placing it in `com.cms.identity.admin` instead would
mean that module reaching into booking internals, the reverse of this codebase's established
"cross-module effects live in the module that performs the effect, triggered by an event from the
module that detected the condition" shape (mirrors `com.cms.waitlist` depending on
`com.cms.booking`'s event, not the other way around).

## R4: Fixed-Time bookings use `BookingCancellationService.cancel` directly; Queue-mode bookings use a direct `cancelIfActive` path

**Decision**: For each qualifying Booking, `DeVerificationCascadeService` checks
`booking.getSlot().getSession().getMode()`:
- `FIXED_TIME` → calls `BookingCancellationService.cancel(booking)` unchanged — the real
  `BookingCancelledEvent` publish and downstream waitlist-bump chain (025→028) fire exactly as an
  individual cancellation would (FR-004).
- `QUEUE` → calls `BookingRepository.cancelIfActive(booking.getId())` directly, then
  `slot.setStatus(SlotStatus.OPEN)`, then (if a lost race, i.e. 0 rows updated) skips — mirroring
  `SessionCancellationService`/`SessionPartialCancellationService`'s (029/030) identical shape for
  every other bulk-cancellation path that isn't 025's own individual trigger. No
  `BookingCancelledEvent` is ever published on this path (FR-005 — 028's matching stays
  exclusively fixed-time, per 025's own established scope).

**Rationale**: `BookingCancellationService.cancel` explicitly rejects non-Fixed-Time Sessions
(`NotAFixedTimeSessionException`) — it structurally cannot be reused as-is for Queue-mode. The
spec's own text ("for fixed-time bookings this means the standard waitlist-bump flow fires")
implies Queue-mode bookings are cancelled too, just without that specific consequence — exactly
what 029/030 already do for their own non-025 cancellation paths.

**Alternatives considered**: Relaxing `BookingCancellationService.cancel`'s Fixed-Time-only
constraint to also accept Queue-mode. Rejected — that constraint is 025's own explicit,
tested contract (FR-009 there); loosening it to serve this one caller risks that service silently
starting to accept Queue-mode from *any* future caller, an unintended, unreviewed scope expansion
of an already-shipped, converged feature.

## R5: "Not-yet-occurred" is defined by Slot status, not a calendar-date comparison

**Decision**: `BookingRepository` gains two queries selecting `Booking`s where `status = ACTIVE`
and `slot.status = BOOKED`, scoped by clinic (Trigger 1) or doctor (Trigger 2) — no
`sessionDate`/time comparison at all.

**Rationale**: This codebase already has dedicated, tested owners for "has this Slot's time
already passed" — 021's no-show sweep (`BOOKED` → `NO_SHOW`) and 026's completion action
(`BOOKED` → `COMPLETED`). A Slot still sitting in `BOOKED` is, by this codebase's own existing
definition, not yet resolved one way or the other — reusing that existing state is simpler and
more consistent than re-deriving a parallel date/time boundary (Constitution II), and correctly
handles Queue-mode bookings uniformly (which have no `startTime`/`endTime` to compare against at
all — only `BOOKED` vs. everything else is meaningful for them, since no-show detection and
completion tracking are both explicitly Fixed-Time-only, so a Queue-mode Slot only ever leaves
`BOOKED` via cancellation).

**Alternatives considered**: Comparing `session.sessionDate` (and, for Fixed-Time, `slot.startTime`)
against "now." Rejected — doesn't apply to Queue-mode at all (no `startTime`), and would require
inventing a same-day boundary rule (is a session earlier today "future" or not?) this codebase
has never needed to answer anywhere else; the Slot-status-based definition sidesteps the question
entirely by relying on state that already exists for exactly this purpose.

## R8: `BookingCancellationService.cancel` needed `Propagation.REQUIRES_NEW` for safe batch reuse

**Decision**: `BookingCancellationService.cancel` (025) is changed from the default `REQUIRED`
propagation to `Propagation.REQUIRES_NEW`.

**Rationale**: Found during implementation, independently confirmed. `DeVerificationCascadeService`'s
`cancelBatch` loop calls `cancel(booking)` from within its own already-active `@Transactional`
method. A lost race for any *one* booking in the batch (another process already resolved it
concurrently — an expected, routine outcome for a bulk operation, exactly like 026/027's own
"skip on lost race" precedent) makes `cancel` throw `BookingNotCancellableException`. With the
default `REQUIRED` propagation, `cancel` merely *participates* in the cascade's transaction
(`isNewTransaction = false`); Spring's transaction advice marks that *shared* transaction
rollback-only the moment the exception leaves `cancel`'s own boundary — before
`DeVerificationCascadeService`'s catch block ever runs. Since the cascade method never re-throws,
it returns normally, but the whole transaction is already doomed: attempting to commit it discards
every *other* successfully-cancelled booking in that same batch too, not just the one that lost
its race. `REQUIRES_NEW` gives every individual `cancel` call its own independent, isolated
transaction, so one lost race can never affect any other booking in the batch — and, as a
beneficial side effect, each booking's real waitlist-bump listener (`BookingCancelledEvent`,
`AFTER_COMMIT`) now fires the moment *that specific* cancellation durably commits, rather than all
being deferred to the cascade's own eventual commit.

**Verified safe for `cancel`'s only other two callers** — `StaffBookingCancellationController` and
`PatientBookingCancellationController` — both plain, non-transactional `@RestController`s with no
ambient transaction; `REQUIRES_NEW` behaves identically to `REQUIRED` when there is nothing to
join or suspend in the first place.

**Alternatives considered**: Duplicating `cancel`'s cancellation logic directly inside
`DeVerificationCascadeService` (calling `cancelIfActive` + `slot.setStatus(OPEN)` +
`eventPublisher.publishEvent` inline) instead of reusing `cancel` at all, to avoid touching 025's
already-converged code. Rejected — this is exactly the duplication research.md R4 already argued
against; the one-line propagation change is strictly safer (provably backward-compatible for
every existing caller) and keeps exactly one implementation of the cancellation logic.

## R6: No request body/reason field on the revoke action

**Decision**: `POST /api/v1/admin/doctors/{doctorProfileId}/revoke` takes no request body.

**Rationale**: Unlike 020's walk-in override (which has an explicit, spec-stated audit-trail
requirement for a reason), this feature's source material states no such requirement — adding one
would be unjustified complexity (Constitution II).

## R7: No new `SecurityConfig` — the revoke endpoint falls under the existing blanket admin matcher

**Decision**: No change to `SuperAdminSecurityConfig` — its existing `/api/v1/admin/**` chain
already requires authentication for `anyRequest()`, unlike the staff/patient chains, which need an
explicit matcher added per new authenticated path.

**Rationale**: `SuperAdminSecurityConfig`'s own design (research.md of 003) already authenticates
every admin path uniformly; every existing admin endpoint (`ClinicVerificationController`,
`DoctorVerificationController`) already relies on this without any per-path matcher, so the new
revoke endpoint needs none either.
