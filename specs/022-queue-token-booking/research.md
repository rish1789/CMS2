# Phase 0 Research: Queue/Token Booking

## Decision: Do NOT wrap `queueSlotService.issueNextSlot(...)` in this feature's own `@Transactional` boundary

**Decision**: `StaffQueueBookingService.bookSlot` / `PatientQueueBookingService.bookSlot` are
themselves **not** `@Transactional` methods. Each individual step (fee resolution, patient
resolution, token/Slot issuance, Booking creation) runs in its own transaction, exactly as it
already would if called standalone. `queueSlotService.issueNextSlot(sessionId)` is called
directly from this non-transactional top-level method — never from inside a broader
`@Transactional` scope this feature opens itself.

**Rationale**: `QueueSlotService.issueNextSlot` (013) has a pre-existing, load-bearing
subtlety: it calls its own `attemptIssueSlot` via **self-invocation** (`this.attemptIssueSlot(...)`
from within the same class). Spring's proxy-based AOP — which is how `@Transactional` is
implemented — only intercepts calls that arrive through the bean's proxy from *outside* the
bean; a self-invocation bypasses the proxy entirely, so `attemptIssueSlot`'s own
`@Transactional` annotation is silently a no-op when called this way (a well-documented Spring
gotcha, not a hypothetical). Today, with no real caller, this is harmless: `attemptIssueSlot`'s
`slotRepository.save(...)` call is itself wrapped by Spring Data JPA's own `@Transactional` on
`SimpleJpaRepository`, and since no ambient transaction exists when `issueNextSlot` is called
standalone, that `save()` call becomes its own top-level auto-committing transaction —
flushing (and therefore constraint-checking) immediately, which is exactly what
`issueNextSlot`'s outer retry loop needs to actually catch a lost race via
`DataIntegrityViolationException`.

**The risk this feature must avoid introducing**: if this feature's own service method were
`@Transactional` and called `issueNextSlot` from inside that scope, `slotRepository.save(...)`
would instead **join the already-open ambient transaction** (Spring transaction propagation is
thread-bound, not proxy-call-chain-bound — it does not matter that the call arrived via a
bypassed self-invocation once *some* transaction is already active on the thread). The INSERT
would then be deferred to this feature's own outer commit, exactly the same bug class already
found and fixed twice this session (020's `StaffBookingService`, 021's `PatientLinkingService`)
— except this time the retry loop's catch block would never fire at all, because the exception
would surface later, at this feature's own outer commit, entirely outside `issueNextSlot`'s own
try/catch. Calling it from a non-transactional context sidesteps this whole class of failure
without touching `QueueSlotService` itself (out of scope — it is already converged, and 013's
own retry-closure logic is correct and proven on its own terms).

**Alternatives considered**: Fixing `QueueSlotService` itself (e.g. removing the self-invocation
by injecting a self-proxy, or converting `issueNextSlot` to use `TransactionTemplate`
explicitly) — rejected as out of scope: `QueueSlotService` is not broken on its own terms, and
013 is an already-converged feature this one should not need to modify to work correctly. A
future feature that needs `QueueSlotService`'s internals restructured can revisit this; nothing
here requires it.

## Decision: Slot-issuance and Booking-creation are two separate atomic units, not one

**Decision**: Accept that this feature's overall flow is not fully atomic end-to-end. Fee
resolution (a pure read) gates everything. Patient resolution (its own already-transactional
call) happens next. `queueSlotService.issueNextSlot` runs as its own already-proven,
independently-committing unit. Booking creation is a final, separate `@Transactional` step
referencing the by-then-already-persisted Patient and Slot. A failure between any of these
steps (e.g. `TokenIssuanceFailedException` after exhausting retries under extreme contention,
or a transient failure between Slot-issuance and Booking-creation) can leave an orphaned
`Patient` record with no Booking, or an orphaned `OPEN` Slot with an unused token number and no
Booking — neither of which is created again on retry (the client would resubmit and get a
fresh Patient-reuse via `PatientLinkingService`'s existing-link branch, and a fresh token via
`issueNextSlot`).

**Rationale**: This lack of full atomicity is not introduced by this feature — it is inherent
to reusing `QueueSlotService.issueNextSlot` exactly as 013 already designed and converged it
(its own javadoc: "no automatic trigger — 018 is this feature's only real caller," with no
transactional-coupling promise to that caller). Forcing full atomicity would require either
reimplementing `issueNextSlot`'s proven retry logic inline in this feature (violates
Constitution II — duplicating already-correct, already-tested logic) or restructuring
`QueueSlotService` itself (out of scope, risks the already-converged feature). The failure
modes involved are rare (sustained extreme contention, or a mid-request infrastructure blip)
and their worst case is an operationally-harmless orphaned row — not a duplicate identity, not
a double-booking, not any Constitution IV-class integrity violation. spec.md's own Assumptions
section already documents that no pre-existing-Slot race needs closing here, consistent with
this framing.

**Alternatives considered**: Wrapping everything in one transaction (rejected — defeats
`issueNextSlot`'s retry closure, see the decision above); a Saga/compensating-transaction
pattern to clean up orphans (rejected — Constitution II, no requirement calls for this, and the
orphan rows are harmless clutter, not integrity risks).

## Decision: New exception-to-HTTP mappings needed

**Decision**: `com.cms.booking.BookingExceptionHandler` gains three new mappings:
`SessionNotFoundException` → `404 SESSION_NOT_FOUND`, `NotAQueueSessionException` →
`409 NOT_A_QUEUE_SESSION`, `TokenIssuanceFailedException` → `503 TOKEN_ISSUANCE_FAILED`.

**Rationale**: All three exceptions (013/019) have existed with no HTTP mapping until now,
since `QueueSlotService` had no real caller before this feature — the identical situation
015's `FeeResolutionService` was in before 016 (research.md of 020 already documents this exact
gap class; 020's own `/speckit-analyze` caught it for `AppointmentTypeNotFoundException`/
`NoFeeConfiguredException`). `TokenIssuanceFailedException` maps to `503` rather than `409`
specifically because it represents transient exhaustion under load, not a client-correctable
conflict — the caller should retry later, which `503 Service Unavailable` communicates more
accurately than a `409`.

## Decision: Booking-creation uses `issueNextSlot`'s Slot reference directly, no re-fetch

**Decision** *(superseded once, see history below)*: `bookingRepository.save(new Booking(slot, ...))`
is called directly inline at the end of `bookSlot`, using the `Slot` reference `issueNextSlot`
returned — no separate helper method, no re-fetch.

**History**: The first version of this decision called for a dedicated `@Transactional`
helper method that re-fetched the Slot by id before constructing the `Booking`, specifically
to keep the reference attached. Convergence (first pass) found that helper was invoked via
**self-invocation** from `bookSlot` (`createBooking(...)`, implicit `this`, same class
instance) — the exact same Spring AOP proxy-bypass gotcha this document's first decision
above describes for `QueueSlotService.attemptIssueSlot`. That meant the helper's own
`@Transactional` was silently a no-op, so the re-fetch never actually achieved attached-entity
safety in the first place — it was a decorative extra read, not a functional fix. Removing it
entirely (rather than making it "genuinely" transactional via a second bean or
`TransactionTemplate`) is the simpler, equally-correct fix: `bookingRepository.save(...)` is
already atomic on its own via Spring Data JPA's own repository-level transaction demarcation,
and a detached JPA entity is fine to reference in a new entity's `@OneToOne`/`@ManyToOne`
field for a simple, non-cascading INSERT (Hibernate only needs the id to write the FK column).

## Decision: Booking-creation needs no race-closure catch block

**Decision**: Unlike `StaffBookingService`/`PatientBookingService`'s `saveAndFlush` +
`DataIntegrityViolationException` → `SlotAlreadyBookedException` pattern, this feature's
Booking-creation step uses a plain `bookingRepository.save(...)` with no special catch.

**Rationale**: `uq_booking_slot` (one Booking per Slot, ever) still exists and still applies,
but every Slot this feature's Booking references was *just* freshly minted, moments earlier,
by `issueNextSlot` — no other caller can already hold a reference to that exact Slot id to race
against, since Slot ids are UUIDs generated fresh per call, not selected from a shared list of
already-existing candidates the way Fixed-Time Slots are. The race Fixed-Time booking closes
(two concurrent requests both selecting the *same pre-existing* Slot) structurally cannot occur
here.
