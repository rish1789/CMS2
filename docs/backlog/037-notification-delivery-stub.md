# 037 — Notification Delivery Stub (Log-Only Send)

**Module:** Discovery & Notifications
**Status:** Ready for spec-kit intake

## User Story
As the System, I want a well-defined "send" step that the notification pipeline calls for every NotificationEvent, so that the delivery mechanism is cleanly pluggable — even though in v1 it only logs instead of contacting a real provider.

## Context
This is a **deliberate v1 design decision, not an apology-worthy gap**: the event pipeline (036) is fully real and complete, but no email/SMS/push/WhatsApp provider is wired up yet. Building the send step as a stub now means a real provider can be dropped in later without touching the event pipeline itself. BDD §2, §3.7, §4.

## Business Rules
- The "send" step is invoked by the notification pipeline (036) for every eligible NotificationEvent (i.e. one that has passed the opt-in/out check).
- In v1, "send" performs **no real external call** to any provider — it logs a line describing what would have been sent (e.g. channel, recipient identifier, message content) and nothing more.
- No provider is connected — this explicitly includes email, SMS, push, and WhatsApp; no provider-specific configuration, API keys, or account setup exists in v1 (not even a named placeholder like Brevo wired up but disabled — genuinely nothing is called).
- The send step's interface/contract should be designed so a future real provider integration is a drop-in replacement of the stub's internals, not a rework of the pipeline that calls it (this is a design intent for the implementer, not a testable business rule in itself).

## Acceptance Criteria
- Given a NotificationEvent that has passed the opt-in/out check (036), when the pipeline invokes the send step, then a log entry is produced containing enough detail to verify what *would* have been sent (channel, recipient, message), and no outbound network call to any messaging/email provider is made.
- Given no provider credentials or configuration exist in the system, when the application starts up, then it does not fail or warn about a missing provider — the stub requires no external configuration to function.
- Given a developer wants to verify notification behavior in tests, when they inspect the log output for a triggering action (e.g. booking confirmed), then they can confirm a notification *would* have fired, without needing a real provider account.
- Given this stub is later replaced with a real provider integration (out of scope for this feature, but a stated intent), when that future work happens, then it should not require changes to feature 036's event-creation/opt-in logic — only to this send step's implementation.

## Dependencies
- Depends on: 036-notification-event-pipeline-opt-in-out — this feature is the terminal step that pipeline invokes.

## Explicitly Out of Scope
- Any real provider integration (email, SMS, push, or WhatsApp) — this is a stub by deliberate v1 decision, not a partially-built integration.
- Delivery receipts, bounce handling, retry logic, or delivery status tracking — none of these are meaningful without a real provider.
- Provider selection/evaluation (e.g. choosing Brevo vs. Twilio vs. MSG91) — explicitly deferred; not decided as part of this build.

## Source References
- BDD §2 (out-of-scope: "Live email/SMS/push delivery — the event pipeline exists but the actual 'send' step is a stub")
- BDD §3.7 (Discovery & Notifications: "delivery is stubbed — nothing is actually sent to a patient today")
- BDD §4 (NFR: "Notification delivery: not live... it's a named placeholder, not a working integration")
- BDD §7.9 item 1 (notification delivery entirely stubbed, no provider connected)
