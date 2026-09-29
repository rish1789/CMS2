# 036 — Notification Event Pipeline & Opt-In/Out

**Module:** Discovery & Notifications
**Status:** Ready for spec-kit intake

## User Story
As the System, I want to emit a decoupled notification event whenever a relevant business action occurs (booking confirmed, waitlist slot offered, follow-up reminder due, etc.), respecting each patient's own opt-in/out preference, so that patients can be kept informed without the triggering request having to wait on or know about delivery mechanics.

## Context
This is the "plumbing" half of notifications — a fully real, event-driven pipeline. The actual "send" step is deliberately stubbed in a separate feature (037); this feature is everything up to and including deciding *whether* and *what* to notify, not the delivery mechanism itself. BDD §2, §3.7, §4.

## Business Rules
- The pipeline is **event-driven and decoupled from the triggering request** — e.g. confirming a booking does not synchronously wait for a notification to be "sent"; a NotificationEvent is created/queued and processed separately.
- Each patient has a **per-patient push/SMS opt-in/opt-out** setting that the pipeline must respect before creating (or at least before attempting delivery of) an event for them.
- Events cover the scenarios implied by scope, including at minimum: booking confirmation, waitlist slot offer (feeding feature 029), and follow-up reminders with their **automatic notify/expire lifecycle** (a follow-up reminder notifies once, then automatically expires — it isn't a standing recurring nag).
- The pipeline is patient-facing notification infrastructure — it is not the same as the Unified Inbox (038), which is a staff-facing, claim-based work-item view; a NotificationEvent may be one of several inputs that populate an Inbox item, but they are conceptually distinct.
- Notification content/UI text is English-only for v1 (no localization).

## Acceptance Criteria
- Given a booking is confirmed (staff-assisted, self-service, or queue/token), when the confirmation completes, then a NotificationEvent is created for that patient without the booking-confirmation request itself blocking on delivery.
- Given a patient has opted out of SMS notifications, when a NotificationEvent that would normally deliver via SMS is generated for them, then the pipeline honors that opt-out (no SMS delivery is attempted for that channel; push, if opted in, may still proceed independently).
- Given a follow-up reminder becomes due for a patient, when the reminder's notify step fires, then a NotificationEvent is created; when the reminder's window subsequently lapses without action, then it automatically expires (no further notification is generated for that same reminder).
- Given a waitlist slot is offered to a patient (see 029), when the offer is made, then a NotificationEvent is created carrying the 30-minute claim window information.
- Given a patient has both push and SMS enabled, when an event fires, then both channels are eligible (subject to 037's stub behavior for actual sending).

## Dependencies
- Depends on: 016-staff-assisted-fixed-time-booking, 017-patient-self-service-fixed-time-booking, 018-queue-token-booking — booking confirmations are a primary event source.
- Depends on: 029-self-service-waitlist-claim — waitlist offers are a primary event source.
- Depends on: 039-patient-account-global-login — opt-in/out preference is stored per patient identity.
- Feeds into: 037-notification-delivery-stub — this feature decides *whether/what* to notify; 037 handles the (stubbed) *how*.
- Related: 038-unified-realtime-inbox — a separate, staff-facing surface that may be informed by some of the same underlying events but is not the same pipeline.

## Explicitly Out of Scope
- Actual message delivery to any real provider — that is entirely feature 037's concern, and 037 is stubbed (log-only) in v1.
- Multi-language/localized notification content — English-only for v1.
- Fine-grained per-event-type opt-out (e.g. "notify me for bookings but not follow-ups") beyond the basic push/SMS channel-level opt-in/out described in the source doc, unless clarified during spec-kit intake.

## Source References
- BDD §2 (in-scope: "Follow-up reminders with automatic notify/expire lifecycle")
- BDD §3.7 (Discovery & Notifications: "Notification pipeline is fully wired end-to-end... but delivery is stubbed")
- BDD §4 (NFR: "per-patient push/SMS opt-in/opt-out")
