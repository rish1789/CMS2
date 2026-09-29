# Feature Specification: Notification Event Pipeline & Opt-In/Out

**Feature Branch**: `011-notification-event-pipeline`

**Created**: 2026-09-03

**Status**: Draft

**Input**: User description: "Notification Event Pipeline & Opt-In/Out — a decoupled, event-driven pipeline that creates a NotificationEvent whenever a relevant business action occurs, respecting each patient's own per-channel (push/SMS) opt-in/out preference, with a generic notify-once/auto-expire lifecycle for time-boxed events (e.g. a future waitlist offer's 30-minute claim window, a future follow-up reminder). Creation/queuing never waits on delivery — delivery itself is a separate, stubbed feature (037). Per build-order.md's documented resolution, this feature builds ONLY the generic pipeline + opt-in storage + expiry lifecycle now, since none of its eventual triggering features (booking confirmation, waitlist offer, follow-up reminder) exist yet in this backlog — those wire in later, each as part of its own build. (Full source: backlog/036-notification-event-pipeline-opt-in-out.md)"

## Scope Decisions Made During Drafting

No prior conversation turn resolved these — they're research-backed defaults made while writing this spec, not a `/speckit-clarify` session (which runs next and may revisit any of them).

- **This feature builds generic infrastructure only, per build-order.md's own explicit resolution** ("036 is generic pub/sub infrastructure... It doesn't need any particular emitter to exist before it does; emitters... just call into it once built"). Concretely: a `publish` capability any future feature can call, per-patient channel opt-in storage, and a generic notify-once/auto-expire lifecycle for time-boxed events. This feature does **not** invent a fake booking-confirmation call-site, a fake waitlist-offer call-site, or a fake "follow-up reminder due" trigger — no such source data model (a Booking, a Waitlist Entry, a clinical follow-up date) exists anywhere yet in this backlog. Building fabricated call-sites now would be exactly the kind of speculative surface Constitution Principle II warns against, and would almost certainly need to be redone once 016/017/018/029 actually define what a booking/waitlist offer look like.
- **Channel opt-in is two separate flags (push, SMS), not one combined flag.** The source business rule explicitly requires this ("per-patient push/SMS opt-in/opt-out") and an acceptance criterion explicitly requires independent behavior ("push, if opted in, may still be marked eligible independently" of an SMS opt-out) — this isn't a judgment call, the two-flag requirement is already stated in the source material.
- **`PatientAccount` (039) already carries one existing field, `notificationOptIn`** (a single boolean, defaults `true`, no setter, zero consumers anywhere in the codebase today besides its own signup-time default check). It predates this feature's channel-level requirement and can't by itself represent "SMS off, push on." This feature adds two new, independent fields (`smsOptIn`, `pushOptIn`) to `PatientAccount` rather than repurposing or removing the existing field, to avoid any risk of regressing 039's already-converged, already-tested contract (`PatientSignupHappyPathTest` asserts `notificationOptIn` is `true` after signup) for a coarse field this feature doesn't otherwise need. `notificationOptIn` itself is left untouched and out of this feature's scope.
- **"Claimed/actioned" (the thing that prevents an event from expiring) is a boolean/timestamp this feature exposes, not a specific claim workflow.** No claim workflow (029) exists yet. This feature's own scope stops at: an event either gets marked actioned (by whatever future caller has a reason to), or its window lapses and it auto-expires — which of those happened is queryable. What "actioned" *means* in a specific future scenario (e.g. a waitlist offer being claimed) is that future feature's concern.
- **No delivery, no channel-send, no UI for a patient to change their own opt-in/out from this feature.** Changing the two new preference flags is possible at the data/service layer (so a future settings feature can call it), but this feature does not add a settings page or endpoint of its own — nothing in the source material asks for one, and inventing one would be scope creep beyond "the pipeline must respect [the] setting."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - A Future Feature Publishes a Notification Event Without Waiting on Delivery (Priority: P1)

Some other part of the system (in the future: booking confirmation, a waitlist offer, etc.) needs to tell a patient something happened. It calls this pipeline's publish capability and moves on immediately — the pipeline decides, from the patient's own channel preferences, which channels (push, SMS) are eligible, records the event, and returns without attempting to actually deliver anything.

**Why this priority**: This is the entire reason the feature exists — decoupling "something happened, tell the patient" from "actually sending a message" is the core architectural value called out in the source material, and every other capability (opt-out honoring, expiry) is a refinement of this same publish path.

**Independent Test**: Call the publish capability directly for a patient with known channel preferences and an event type/payload; confirm it returns quickly with a persisted `NotificationEvent` reflecting the correct eligible channels, and confirm no delivery-related code path (none exists in this feature) was invoked.

**Acceptance Scenarios**:

1. **Given** a patient with both push and SMS opted in, **When** an event is published for them, **Then** a `NotificationEvent` is created recording both channels as eligible, and the call returns without performing or waiting on any delivery step.
2. **Given** a patient has opted out of SMS but is opted in to push, **When** an event is published for them, **Then** the created event records push as eligible and SMS as not eligible.
3. **Given** a patient has opted out of both push and SMS, **When** an event is published for them, **Then** the created event records zero eligible channels (the event still exists — the decision *not* to deliver on any channel is itself the recorded, auditable outcome, not a suppressed/skipped record).

---

### User Story 2 - A Time-Boxed Event Automatically Expires If Never Actioned (Priority: P2)

An event published with a claim/response window (the generic mechanism a future waitlist-offer or follow-up-reminder feature will use) notifies once. If nothing marks it actioned before its window lapses, it automatically becomes expired and is never eligible to notify again for that same occurrence.

**Why this priority**: This is the specific "notify once, then automatically expire — not a standing recurring nag" guarantee called out by name in the business rules, and it's the piece of infrastructure that removes the need for any future feature to build its own expiry-tracking mechanism from scratch. It's P2 because it extends US1's publish path rather than being usable on its own.

**Independent Test**: Publish an event with a short expiration window and no action taken; run the expiry sweep; confirm the event's status is now expired. Separately, publish an event with a window, mark it actioned before the window lapses, run the sweep, and confirm it is *not* expired (actioned events are left alone).

**Acceptance Scenarios**:

1. **Given** an event was published with an expiration window, **When** that window lapses without the event being marked actioned, **Then** the event's status becomes expired, and it is excluded from any future "pending" query.
2. **Given** an event was published with an expiration window and is marked actioned before the window lapses, **When** the expiry sweep subsequently runs, **Then** the event remains in its actioned state and is not marked expired.
3. **Given** an event was published with an expiration window and the window has not yet lapsed, **When** it is queried, **Then** its remaining window/expiration time is visible.
4. **Given** an event was published with no expiration window at all (a non-time-boxed notification), **When** the expiry sweep runs, **Then** that event is left untouched (never expired) — the expiry mechanism only ever applies to events that opted into it by carrying a window.

---

### Edge Cases

- What happens when an event is published for a patient with no phone number on file (mobile is optional per 039) but SMS is (nominally) opted in? → SMS is recorded as not eligible for that event regardless of the opt-in flag — there's no number to send to. Push eligibility is unaffected.
- What happens if the expiry sweep runs twice over the same lapsed, unactioned event? → Idempotent: the event is already expired after the first run; the second run makes no further change and does not error.
- What happens if an event is marked actioned *after* it has already auto-expired? → Rejected/no-op — once expired, an event's status is terminal for this feature's purposes; a future feature encountering this should treat the offer/window as gone, not retroactively revive it.
- What happens to an event for a patient who is later deactivated (`PatientAccount.active = false`, if that ever happens) or whose account is otherwise no longer valid? → Out of scope for this feature; no deactivation mechanism exists on `PatientAccount` today, so this is not evaluated.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST provide a way to publish a notification event for a given patient (event type, an opaque payload, and an optional expiration window), which persists the event and returns immediately — without invoking, waiting on, or depending on any message-delivery step.
- **FR-002**: System MUST determine, at publish time, which of the two channels (push, SMS) are eligible for the event based on the patient's own current per-channel opt-in preferences, and MUST record that eligibility decision on the created event, not recompute it later.
- **FR-003**: System MUST treat push and SMS opt-in as two independent settings — a patient may be opted into one, both, or neither, and an opt-out on one channel MUST NOT affect the other channel's eligibility.
- **FR-004**: System MUST NOT mark SMS eligible for a patient with no phone number on file, regardless of their SMS opt-in setting.
- **FR-005**: System MUST support publishing an event with an optional expiration window (a point in time after which, if unactioned, the event auto-expires); an event published without one MUST never be affected by the expiry mechanism.
- **FR-006**: System MUST provide a way to mark a published, not-yet-expired, time-boxed event as actioned, which permanently excludes it from ever being auto-expired.
- **FR-007**: System MUST provide an expiry mechanism (evaluated on demand / on a recurring sweep) that transitions any time-boxed event whose window has lapsed, and that has not been marked actioned, into an expired state — exactly once per event, and safely re-runnable without side effects on an already-expired event.
- **FR-008**: System MUST provide a way to read back a published event's current state (channel eligibility, expiration window if any, actioned/expired/pending status) for verification and for future features to build on.
- **FR-009**: System MUST store, per Patient Account, independent push and SMS opt-in preferences (both defaulting to opted-in, consistent with 039's existing single-flag default), readable and updatable at the data/service layer.

### Key Entities

- **NotificationEvent** (new entity, first defined by this feature): one occurrence of "something happened, the patient should potentially be told." Fields: the Patient Account it's for, an event type (an extensible identifier — this feature doesn't enumerate a fixed set, since no concrete event-producing feature exists yet), an opaque payload, which channels (push/SMS) were eligible at publish time, an optional expiration window, and a status (pending, actioned, or expired).
- **PatientAccount** (from 039, extended by this feature): gains two new independent fields, `smsOptIn` and `pushOptIn` (both boolean, default `true`), read by this feature's eligibility check at publish time. Its existing fields (including the pre-existing, unrelated `notificationOptIn`) are unchanged.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of publish calls, across every tested combination of the two channel opt-in flags and phone-number presence, record exactly the correct eligible-channel set on the created event.
- **SC-002**: 100% of publish calls return without any measurable dependency on a delivery step (verified by the fact that no delivery code path exists in or is reachable from this feature at all).
- **SC-003**: 100% of time-boxed events whose window lapses without being actioned are found expired by the very next expiry sweep; 100% of time-boxed events actioned before their window lapses are never expired, regardless of how many subsequent sweeps run.
- **SC-004**: 100% of non-time-boxed events (no expiration window) remain unaffected by any number of expiry sweep runs.
- **SC-005**: A future feature can integrate a new event-producing call site using only this feature's publish contract, with zero changes required to this feature's own code (verified structurally: the event type is an opaque, extensible value, not a fixed enum requiring a code change to extend).

## Assumptions

- **No triggering feature (booking confirmation, waitlist offer, follow-up reminder) is wired into this pipeline by this feature.** Per build-order.md's own documented resolution for this exact feature, and because none of those source features/data models exist yet anywhere in this 39-feature backlog. This feature ships as a generic, directly-testable service contract (Constitution Principle III's "service interface or REST endpoint" — no HTTP endpoint here either, mirroring 009's precedent, since there is no UI or cross-service caller yet that would need one).
- **`smsOptIn`/`pushOptIn` are added to `PatientAccount` as new, independent fields; the existing `notificationOptIn` field is left completely untouched.** This avoids any regression risk to 039's converged, tested contract, at the cost of `PatientAccount` carrying one now-superseded coarse flag alongside two new granular ones — an acceptable, low-risk trade-off given `notificationOptIn` has zero consumers anywhere in the codebase today.
- **"Actioned" is a generic terminal marker, not a specific claim/response record.** This feature defines the mechanism (a status transition that blocks expiry) but not the business meaning of "acting" on any particular kind of event — that's each future producing feature's concern (e.g., 029 will define what "claiming" a waitlist offer means and call this mechanism when it happens).
- **No scheduled/automatic invocation of the expiry sweep is built by this feature** (e.g., no cron/nightly job wiring) — the sweep is exposed as a callable capability (mirroring the shape of 011-nightly-rolling-session-generation's later, separate scheduling concern in this same backlog), and this feature's tests invoke it directly rather than asserting anything about a schedule, since no scheduling infrastructure decision has been made yet for this project outside the one already-built nightly-session feature (out of numeric order relative to this one, and not a dependency of this feature).
- **The expiration window's exact duration (e.g. a future waitlist offer's specific "30 minutes") is a parameter this feature accepts, not a hardcoded business rule this feature owns.** The one concrete duration mentioned in the source material (30 minutes) belongs to 029, which hasn't been built; this feature's own tests exercise the generic mechanism with an arbitrary test window.
