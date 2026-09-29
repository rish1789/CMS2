# Research: Unified Real-Time Inbox

## R1 — Real-time transport: Server-Sent Events, not WebSocket

**Decision**: Use Spring MVC's built-in `SseEmitter` for push delivery. No new Gradle dependency.

**Rationale**: The backlog source explicitly leaves transport as a plan-phase technical decision.
This codebase has never used a persistent-connection technology before. `SseEmitter` ships with
`spring-boot-starter-web` (already a dependency) — a WebSocket approach would require adding
`spring-boot-starter-websocket` plus a `StompEndpoint`/broker configuration, none of which any
other feature needs. SSE is also naturally one-directional (server → client), which matches this
feature's actual shape: clients call ordinary POST endpoints for claim/release/resolve, and only
*receive* push notifications. Constitution II (Simplicity & YAGNI) favors the option with zero new
dependencies.

**Alternatives considered**:
- WebSocket (`spring-boot-starter-websocket` + STOMP): rejected — bidirectional capability this
  feature doesn't need, new dependency, new broker/session-management surface.
- Short-interval polling (e.g. every 2s): rejected — technically meets the 5-second SC-001 bound,
  but is a strictly worse fit for "real-time... without a manual refresh" framing and multiplies
  load per open Inbox tab for no benefit over SSE, which this stack supports for free.

## R2 — SSE auth: `fetch` streaming, not native `EventSource`

**Decision**: The frontend consumes the SSE stream via `fetch(url, { headers: { Authorization } })`
and reads `response.body.getReader()` (a streaming reader), parsing `data: ...\n\n` frames itself,
rather than the browser's native `EventSource` API.

**Rationale**: `EventSource` cannot set custom request headers, so it cannot carry the existing
`Authorization: Bearer <staffJwt>` header `StaffJwtAuthenticationFilter` already requires. The only
way to authenticate a native `EventSource` connection is a token passed in the URL query string —
rejected because query strings are routinely captured in server access logs and browser history,
a materially worse exposure than a header for a security-sensitive bearer token, and because it
would require extending the shared `StaffJwtAuthenticationFilter` (used by every `/api/v1/clinics/**`
endpoint) to also accept a query-param token, widening that risk to the whole chain for one
endpoint's convenience. `fetch` streaming keeps the existing filter completely unchanged.

**Alternatives considered**:
- Native `EventSource` + query-param token: rejected for the reason above.
- Native `EventSource` + short-lived one-time SSE ticket endpoint: rejected as unnecessary added
  surface (a new ticket-issuance endpoint, a new short-lived-token concept) for a problem `fetch`
  streaming already solves with existing auth.

## R3 — Broadcast scope: single-instance, in-memory registry (accepted v1 limitation)

**Decision**: `InboxBroadcastService` holds an in-memory `Map<UUID clinicId, List<SseEmitter>>`.
On any Inbox Item create/claim/release/resolve, the owning service pushes the updated item to every
currently-registered emitter for that clinic.

**Rationale**: Matches this system's existing single-instance deployment assumption (no other
feature has introduced cross-instance coordination). A horizontally-scaled deployment would need a
shared pub/sub (e.g. Redis) to fan out across instances — explicitly out of scope until the
platform actually needs multi-instance backend deployment, per Constitution II.

**Alternatives considered**: Database-polling fallback per client (rejected — reintroduces polling
that R1 already rejected); a message broker (rejected — first such dependency in this codebase,
unjustified by current, confirmed scale).

## R4 — Item content: live-derived, not frozen, for patient-referencing types

**Decision**: `InboxItem` holds a `@ManyToOne Booking` reference for `WALK_IN` items and a
`@ManyToOne WaitlistEntry` reference for `WAITLIST_OFFER` items — never a frozen summary string.
Display content (patient name, slot time, etc.) is read through these relations at response-
assembly time. `DEVERIFICATION_CASCADE` items store frozen `doctorName`/`cancelledBookingCount`
fields instead, since Doctor identity is never anonymized or purged by this system (033/034 only
ever touch Patient/Booking/clinical-content data).

**Rationale**: Directly implements spec FR-016/Clarifications: once 033 scrubs a `Patient`'s
`name`/`phone` in place, or 034 deletes old `Booking` rows, every Inbox Item referencing that data
reflects the change automatically — no separate scrubbing/purge step to write, test, or forget.
This mirrors an already-established pattern: `com.cms.clinical.ConsultationNote` holds a direct
`@ManyToOne` to `com.cms.booking.Booking` for the same reason (cross-module read relations for
display are already normal in this codebase; only cross-module *write effects* go through events
per Constitution III).

**Alternatives considered**: Frozen summary string computed at creation time — rejected outright;
it is the one option FR-016 explicitly rules out, since it would require a new, untested scrubbing
pass wired into 033/034 for a data shape neither feature currently touches.

## R5 — Referenced-record deletion: `ON DELETE CASCADE`

**Decision**: The `booking_id` and `waitlist_entry_id` foreign keys on `inbox_item` are declared
`ON DELETE CASCADE` in the Flyway migration.

**Rationale**: R4 means an `InboxItem` has no independent meaning once its referenced `Booking` or
`WaitlistEntry` no longer exists. In the ordinary case an item is resolved (and, per FR-012,
retained) long before 034's 3-year purge could ever reach its underlying `Booking` — but for the
rare item that somehow outlives that window, `ON DELETE CASCADE` guarantees the row disappears
along with its source, with zero new code in 034's purge service and zero risk of a dangling
foreign key blocking that deletion.

**Alternatives considered**: Nullable, non-cascading FK with a "purged" placeholder rendered when
the reference is missing — rejected as unnecessary complexity for a case FR-015 already says this
feature does not need to actively manage.

## R6 — Module placement and authorization: mirrors 025/026's direct-call, Operations-or-ClinicAdmin shape

**Decision**: `com.cms.inbox` is a new module. All five endpoints (list, stream, claim, release,
resolve) require an active `Operations` or `ClinicAdmin` `RoleAssignment` at the path's `clinicId`
— the same `requireAuthorized` shape `WalkInInsertionService`/`SessionCancellationService` already
use, added as one new `RoleAssignmentRepository` query
(`existsByAccount_IdAndClinic_IdAndRoleInAndActiveTrue`). New matchers are added to the existing
`/api/v1/clinics/**` chain in `com.cms.identity.account.SecurityConfig`, continuing that file's
own documented running list — proactively included as its own task (this session hit the "new
path silently falls through to `permitAll()`" bug class twice before, in 014 and 020).

**Rationale**: The spec's own User Story framing ("As front-desk staff (ClinicAdmin or
Operations)...") names exactly these two roles, unlike 027's queue-position endpoint (any active
role) — this feature's authorization gate should match 020/025's write-action precedent, not
027's read-only one, since claim/resolve are themselves write actions with real coordination
consequences if under-authorized.

## R7 — Cross-module trigger points (direct calls, mirroring 026's R5 / 036-037's precedent)

**Decision**: No new domain event type. Three existing, already-converged services gain one new
call site each into `InboxItemService`:

1. `WalkInInsertionService.insertWalkIn` — after the Booking is saved and the Slot is marked
   `BOOKED`, calls `InboxItemService.createWalkInItem(clinicId, booking)`.
2. `WaitlistMatchingService.matchAndOffer` — after `offerIfWaiting` succeeds and `match.offer(...)`
   updates the in-memory entity, calls `InboxItemService.createWaitlistOfferItem(clinicId, match)`.
   Two existing call sites gain the mirror-image auto-resolve call
   (`InboxItemService.resolveByWaitlistEntry(waitlistEntryId)`), implementing spec FR-013 without
   any new event type: `WaitlistClaimService.claim` (after `patientBookingService.bookSlot`
   actually succeeds, not immediately after `entry.markClaimed()` — placed post-booking so a
   claim that later pivots to `waitlistReleaseService.releaseById`'s independent `REQUIRES_NEW`
   transaction rolls this call back too, rather than leaving the Inbox Item stuck `RESOLVED`
   while the entry itself gets `EXPIRED` by that separate transaction — the same
   rollback-poisoning bug class 029/033's convergence passes already found this session) and
   `WaitlistReleaseService.release` (after `entry.expire()` succeeds — the shared core behind
   both an explicit decline and the automatic expiry sweep, per that service's own documented
   "one mechanism, two triggers" design, so no separate hook into `WaitlistExpirySweepService`
   is needed).
3. `DeVerificationCascadeService.cancelBatch` — after the existing per-booking cancellation loop,
   groups the successfully-cancelled bookings by clinic and calls
   `InboxItemService.createCascadeNotices(doctorName, groupedByClinic)` once per affected clinic.

**Rationale**: All three source modules and `com.cms.inbox` already exist in the same build (no
build-order sequencing problem, unlike 029/036's original circular dependency) — a direct
synchronous call is simpler than introducing a new `ApplicationEvent` type for no decoupling
benefit, mirroring 026-session-delay-tracking's identical reasoning (research.md R5 there) rather
than 003→008 or 025→028's "publish now, consumer built later" event precedent (which exists
specifically because the consumer *didn't* exist yet at publish time — not the case here).

**Alternatives considered**: A new `WalkInInsertedEvent`/`WaitlistOfferedEvent`/
`DeVerificationCascadedEvent` consumed via `@TransactionalEventListener` — rejected as
unnecessary indirection per Constitution II, since there is exactly one producer and one consumer
for each, both already built, in the same transaction boundary.

## R8 — Claim/resolve concurrency: data-layer-guarded conditional update

**Decision**: `InboxItemRepository` gets `claimIfUnclaimed(id, accountId)`,
`releaseIfClaimedBy(id, accountId)`, `resolveIfClaimedBy(id, accountId)`, and
`resolveIfNotResolved(waitlistEntryId)` (the FR-013 auto-resolve path, closing a race against a
concurrent staff-initiated resolve on the same item — analyze finding F1) as `@Modifying` queries
returning the updated row count, exactly mirroring `WaitlistEntryRepository.offerIfWaiting` and
`BookingRepository.cancelIfActive`. A `0` result throws `AlreadyClaimedException` (claim) or
`NotClaimantException` (release/resolve) mapped to `409`; `resolveIfNotResolved` returning `0` is a
silent no-op (the item was already resolved by the other path — not an error, mirrors 026/027's
lost-race precedent).

**Rationale**: This is the third time this session a claim/state-transition race needed closing at
the data layer, not via read-then-write (Constitution IV) — reusing the exact proven shape avoids
reintroducing the read-then-write bug class 011/025/028's convergence passes each found and fixed
in earlier features.

**Alternatives considered**: Optimistic locking (`@Version`) — rejected as a less direct fit than
the conditional-update pattern already proven three times in this codebase for the same class of
problem, and it would surface as a generic `OptimisticLockException` requiring its own translation
to the specific `409` responses this feature's contract defines.

## R9 — Accepted exception: `com.cms.inbox` and `com.cms.booking`/`com.cms.waitlist` depend on each other

**Decision**: Unlike every prior module pair in this codebase (each a one-way edge — e.g.
`com.cms.booking → com.cms.scheduling`, `com.cms.booking/waitlist → com.cms.notification`),
`com.cms.inbox` and `com.cms.booking`/`com.cms.waitlist` depend on each other: R4 requires
`InboxItem` to hold a live `@ManyToOne` relation into `Booking`/`WaitlistEntry` (inbox → booking,
inbox → waitlist), while R7 requires those modules to call `InboxItemService` directly (booking →
inbox, waitlist → inbox) — a genuine two-way dependency, accepted as this feature's own deliberate
exception rather than silently introduced.

**Rationale**: Unlike 022's `RiskBasedBufferSlotCalculator` (which had a real one-way-preserving
alternative — placing the class in `com.cms.booking` instead of `com.cms.scheduling` — and took
it, explicitly to avoid "this codebase's first circular package dependency"), the Inbox has no
such alternative: its entire reason to exist, per the spec's own framing, is being a hub that both
*receives* events from three other modules and *reads live content* from what those events
reference (FR-016). Breaking the cycle would mean either (a) inverting FR-016 back to frozen
summary strings (R4's rejected alternative, reopening the exact anonymization-propagation gap
Clarify surfaced), or (b) a generic content-provider-registry indirection so `com.cms.inbox` never
imports `Booking`/`WaitlistEntry` at all — a materially larger abstraction with no other consumer
in this single-Gradle-module codebase (no JPMS/multi-module boundary actually enforces
acyclicity here), rejected as unjustified complexity per Constitution II. Since Constitution III
prohibits *reach-through into another module's internals for async effects*, not inter-module
dependency cycles per se, and both of the Inbox's cross-edges are ordinary public-service/JPA-
relation reads (not effect reach-through), this stays within the constitution's actual letter.

**Alternatives considered**: Content-provider registry (each producer module registers a
`Function<UUID, Map<String,Object>>` for its item type, so `com.cms.inbox` stays type-agnostic) —
rejected as premature generality for a single, fixed, small set of item types (Constitution II);
revisit only if a future feature needs `com.cms.inbox` to stay decoupled from a fourth producer
module for an independent reason.
