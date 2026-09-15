# Research: Notification Event Pipeline & Opt-In/Out

No `NEEDS CLARIFICATION` markers remain in Technical Context — every open question was resolved during Specify with documented reasoning (spec.md's Scope Decisions / Assumptions), most upstream of that by build-order.md's own pre-written resolution for this exact feature. This document records the resulting technical decisions.

## Decision: `markActioned` and `expireDue` are both status-guarded conditional updates, not read-then-write

**Rationale**: Constitution Principle IV requires concurrency-sensitive operations to close races at the data layer, not merely the application layer. Two scenarios matter here:
1. `expireDue` running concurrently with `markActioned` on the same event — implemented as `UPDATE notification_event SET status = 'EXPIRED' WHERE id = :id AND status = 'PENDING' AND expires_at < :now` (via a derived/`@Modifying @Query` repository method or an equivalent JPA save guarded by a freshly-checked status): whichever transaction commits first wins the row; the loser's predicate simply matches zero rows on its own attempt, which is exactly the "safely re-runnable, no side effect" requirement (FR-007) and the "reject a stale action" requirement (Edge Cases) at once.
2. `markActioned` itself checks the loaded entity's status is still `PENDING` immediately before flipping it to `ACTIONED`; if it's already `EXPIRED`, it throws `NotificationEventAlreadyExpiredException` rather than silently succeeding or silently no-op'ing — spec Edge Cases says "Rejected/no-op," and an explicit exception (matching this codebase's established pattern of throwing a specific domain exception rather than swallowing a conflict — e.g. `DuplicateLicenseNumberException`) gives a future caller (029) an unambiguous signal that the offer/window is gone, rather than a silent no-op it could easily miss.

**Alternatives considered**: A separate "lock" table or optimistic-locking `@Version` column — rejected as unnecessary complexity (Principle II) for a single boolean-shaped state machine (`PENDING` → `ACTIONED` or `PENDING` → `EXPIRED`, both terminal) with only two competing writers, well-served by a plain conditional `UPDATE`.

## Decision: channel eligibility is computed and stored once, at publish time

**Rationale**: Spec FR-002 explicitly requires this ("MUST record that eligibility decision on the created event, not recompute it later"). This is a deliberate audit/correctness choice: an event's `pushEligible`/`smsEligible` columns are a snapshot of "what the patient's preference was when this was decided to notify them," which stays meaningful even if the patient changes their preference afterward — a future delivery feature (037) reading a `NotificationEvent` should see what was *decided*, not re-derive a possibly-different answer from the patient's *current* state.

**Alternatives considered**: Storing only the patient reference and computing eligibility on read — rejected; it would silently change the answer for an already-published event if the patient's preference changes in between, which is both surprising and unauditable.

## Decision: `PatientAccount` gains two new additive fields; `notificationOptIn` is untouched

**Rationale**: See spec Assumptions. `notificationOptIn` (a single boolean, defaults `true`) has zero consumers anywhere in the codebase today besides its own already-converged, already-tested signup-default assertion (`PatientSignupHappyPathTest`). Repurposing or removing it to make room for channel-level granularity would risk regressing a converged feature's contract for no benefit, since two new, independent columns satisfy this feature's actual FR-003/FR-009 requirement (two independent channels) cleanly and additively.

**Alternatives considered**: A separate `NotificationPreference` entity (one-to-one with `PatientAccount`) — rejected as unnecessary indirection (Principle II) for two booleans that are intrinsically patient-identity attributes, not a notification-pipeline concern in their own right; every other per-patient preference-shaped field in this codebase (including the existing `notificationOptIn`) already lives directly on `PatientAccount`.

## Decision: `eventType` is an opaque `String`, not a fixed enum

**Rationale**: Per spec Assumptions and SC-005 — no concrete event-producing feature exists yet, so any enum defined now would be a guess this feature has no way to get right, and every future producer would need a code change here just to add its own event type, which is exactly the coupling a generic pipeline is supposed to avoid. A `String` (with the caller supplying a stable, self-namespaced value like `"booking.confirmed"` when that feature is eventually built) keeps this module extensible with zero changes.

**Alternatives considered**: A `NotificationEventType` enum seeded now with the three scenarios named in the source material (booking confirmation, waitlist offer, follow-up reminder) — rejected; inventing enum constants for features that don't exist yet is exactly the speculative surface Constitution Principle II warns against, and yields three enum values with no code anywhere that ever sets them in this feature's own scope.

## Decision: `NotificationEventService.publish/markActioned/expireDue/get` is the entire public contract — no HTTP endpoint

**Rationale**: Identical reasoning to 009's precedent (see that feature's research.md) — this feature's only eventual callers (016/017/018/029, plus a future admin/ops trigger for `expireDue`) don't exist yet. Constitution Principle III's "service interface or REST endpoint" is satisfied by the service interface; `contracts/notification-event-service.md` documents it as a Java-interface-shaped contract.

**Alternatives considered**: A speculative internal `/api/v1/notifications/*` admin endpoint for triggering `expireDue` manually — rejected; nothing calls it yet, and no scheduling/ops-trigger decision has been made anywhere else in this project outside the separately-built, unrelated nightly-session-generation feature.

## Decision: New package `com.cms.notification`, `PatientAccount` extended in place (not wrapped)

**Rationale**: "Notification" is not owned by any existing module (`identity`, `patient`, `discovery`) — it's a new bounded context reading `PatientAccount` as a cross-module reference, exactly like `com.cms.discovery` reads `Clinic`/`DoctorProfile`. The two new preference fields, however, are patient-identity data (not notification-pipeline data), so they belong directly on `PatientAccount` itself, extended in place — consistent with how 008 extended `DoctorProfile` in place (new setters, no new wrapper entity) for a conceptually similar "add fields the owning entity was always going to need" situation.

**Alternatives considered**: Putting the new preference fields on a `NotificationEvent`-adjacent entity instead — rejected; eligibility needs to read the patient's *current* preference at publish time (see the eligibility-snapshot decision above), so the preference must live on `PatientAccount`, not on the notification module's own entities.
