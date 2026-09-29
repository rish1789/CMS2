# Feature Specification: Notification Delivery Stub (Log-Only Send)

**Feature Branch**: `012-notification-delivery-stub`

**Created**: 2026-09-03

**Status**: Draft

**Input**: User description: "Notification Delivery Stub — a well-defined 'send' step that the notification pipeline (036) invokes for every eligible NotificationEvent, that in v1 only logs what would have been sent (channel, recipient, message) instead of contacting a real provider. No provider (email/SMS/push/WhatsApp) is connected, and none of its configuration is required for the app to start. The send step's contract is designed so a future real provider is a drop-in replacement of the stub's internals, with zero changes to 036's event-creation/opt-in logic. Since 036 (its sole dependency) is now built, this feature genuinely wires into it — unlike 036 itself, which deliberately deferred wiring into features that didn't exist yet. (Full source: backlog/037-notification-delivery-stub.md)"

## Scope Decisions Made During Drafting

No prior conversation turn resolved these — they're research-backed defaults made while writing this spec, not a `/speckit-clarify` session (which runs next and may revisit any of them).

- **This feature wires into 036's `publish()` via a Spring application event, not a direct method call.** 036's `NotificationEventService.publish()` already exists, already converged, and its FR-001 requires it to "return immediately... without invoking, waiting on, or depending on any message-delivery step." This feature respects that boundary by having `publish()` emit a plain, in-process event (mirroring this codebase's own precedent: `003-super-admin-clinic-verification`'s `ClinicDeVerifiedEvent`, published by 003, with no listener until 008 was later built to consume it — the exact same "publish now, a later feature listens" shape this feature now completes for 036). The send step itself listens for that event **after the publishing transaction commits** — so a notification is never logged for an event that didn't actually get persisted (e.g. a rolled-back `publish()` call), and the connection remains structurally decoupled (a log statement, not a network call, so there is nothing to meaningfully "wait on" either way).
- **"Recipient identifier" is the patient's mobile number for the SMS channel, and the patient's email for the push channel.** No push-token/device-registration concept exists anywhere in this 39-feature backlog, so there is no push-specific address to log; email is used as the best available stand-in patient identifier for a push-channel log line. SMS always has a real recipient value available when it's logged, because 036's own FR-004 already guarantees SMS is never marked eligible for a patient with no mobile on file.
- **The "send" contract is a small interface (one method: channel, recipient, message) with exactly one implementation (the logging stub).** This is what makes "a future real provider integration is a drop-in replacement of the stub's internals, not a rework of the pipeline" true structurally: swapping in a real provider means adding a new implementation of the same interface and changing which one is wired up — zero changes to 036, and zero changes to this feature's own event-listening/channel-eligibility-reading logic.
- **No delivery status is recorded anywhere.** Explicitly out of scope per the source material ("Delivery receipts, bounce handling, retry logic, or delivery status tracking — none of these are meaningful without a real provider"). This feature's only observable effect is a log line; it does not add a "delivered" status to `NotificationEvent` or touch its `PENDING`/`ACTIONED`/`EXPIRED` lifecycle in any way.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Every Eligible Channel on a Published Event Produces a Verifiable Log Line (Priority: P1)

Whenever the notification pipeline creates an event, the send step is invoked once for each channel that passed the opt-in/out check, and produces a log entry with enough detail (channel, recipient, message) that a developer — or a future feature's own tests — can confirm a notification *would* have fired, without any real provider being contacted.

**Why this priority**: This is the entire feature — the one observable behavior it's meant to have. Every other requirement (no config needed, pluggable design) supports this core guarantee.

**Independent Test**: Publish a NotificationEvent (via 036's existing service) for a patient with known channel eligibility and contact details; inspect the send step's invocations (or captured log output) and confirm exactly one send per eligible channel, each carrying the correct channel, recipient, and message — and confirm no outbound network call of any kind was made.

**Acceptance Scenarios**:

1. **Given** a patient opted into both push and SMS with a mobile number on file, **When** an event is published for them, **Then** the send step is invoked twice — once for `push` (recipient: their email) and once for `sms` (recipient: their mobile number) — each carrying the event's message content.
2. **Given** a patient opted into push only, **When** an event is published for them, **Then** the send step is invoked exactly once, for `push` only.
3. **Given** a patient opted into neither channel (or SMS-eligible-but-no-mobile, per 036's own gate), **When** an event is published for them, **Then** the send step is invoked zero times for the ineligible channel(s).
4. **Given** any invocation of the send step, **When** it runs, **Then** it performs no outbound network call, requires no provider credentials, and does not fail — it only logs.

---

### Edge Cases

- What happens if the transaction publishing the event is rolled back (e.g. a future caller's own transaction fails after calling `publish()` but before committing)? → No send is ever invoked for that event — the send step only fires after the publishing transaction has actually committed, so a phantom notification for data that was never actually persisted can never be logged.
- What happens if the application starts with no notification-provider configuration of any kind present? → It starts normally; nothing in this feature requires any configuration, credential, or external client to be present at startup (there is no external client at all in v1).
- What happens when a real provider is eventually integrated? → Out of scope for this feature (a stated future intent only) — but by design, that work replaces only this feature's one internal implementation of its send contract; it requires no change to 036's event-creation/opt-in logic, and no change to how eligible channels are determined.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST invoke a well-defined "send" step once for every channel (push, SMS) that was marked eligible on a published NotificationEvent (036) — and MUST NOT invoke it for a channel that was not marked eligible.
- **FR-002**: The send step MUST NOT make any outbound call to any real messaging/email/push provider — in v1 it MUST only record (log) what would have been sent.
- **FR-003**: Each send-step invocation MUST carry enough information to identify the channel, a recipient identifier, and the message content, so its effect can be verified without a real provider.
- **FR-004**: The send step MUST require no external configuration, credentials, or provider account of any kind to function, and its presence MUST NOT cause application startup to fail or warn about a missing provider.
- **FR-005**: The send step MUST only fire for a NotificationEvent that was actually committed/persisted by 036's publish step — never for one belonging to a transaction that did not commit.
- **FR-006**: The send step's contract (what a caller invokes) MUST be defined independently of its v1 (logging) implementation, such that a future real-provider implementation can be substituted without changing 036's event-creation/opt-in logic or this feature's own channel-eligibility-reading logic.
- **FR-007**: This feature MUST NOT introduce any delivery-status tracking, retry logic, or receipt/bounce handling of any kind.

### Key Entities

- **NotificationEvent** (from 036, read-only here): supplies the per-channel eligibility flags, event type, and payload this feature reads to decide what (and whether) to send.
- **PatientAccount** (from 039, read-only here): supplies the recipient identifiers (`mobile` for SMS, `email` for push) this feature logs.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of eligible channels (per 036's own eligibility computation) on a published event result in exactly one send-step invocation each; 100% of ineligible channels result in zero.
- **SC-002**: 100% of send-step invocations, across every tested scenario, involve zero outbound network calls (verified structurally: no network client of any kind exists in this feature's implementation).
- **SC-003**: The application starts successfully with no notification-provider configuration present, in 100% of tested startup runs.
- **SC-004**: A rolled-back `publish()` call results in zero send-step invocations, in 100% of tested cases.

## Assumptions

- **No push-token/device-registration concept exists anywhere in this backlog**, so `email` is used as the logged recipient identifier for the push channel — a reasonable stand-in patient identifier, not a real push address, consistent with this being a stub with no real provider behind it.
- **The connection from 036 to this feature is a plain, synchronously-published Spring application event** (not a message queue, not `@Async`), mirroring this codebase's existing `ClinicDeVerifiedEvent` precedent (003 publishes, 008 later listens) — the smallest mechanism that satisfies both 036's "don't invoke/wait on a delivery step from `publish()` itself" boundary and this feature's own "fire only after commit" requirement, via `@TransactionalEventListener(phase = AFTER_COMMIT)` on the listening side.
- **This feature modifies 036's `NotificationEventService.publish()`** to add one `ApplicationEventPublisher.publishEvent(...)` call after the event is saved — a small, additive change to a converged feature's code, consistent with how earlier features in this backlog (e.g. 006/008 extending `DoctorProfile`) have safely extended prior converged work without breaking its existing tests/contract.
