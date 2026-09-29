# 026 — Whole-Day Session Cancellation

**Module:** Cancellation & Waitlist
**Status:** Ready for spec-kit intake

## User Story
As staff (or in response to a doctor being unavailable), I want to cancel an entire day's session at once, so that every affected booking is handled consistently without cancelling them one by one.

## Context
A doctor may be unavailable for a whole session (sick day, emergency). Rather than requiring staff to cancel each booking individually — which would incorrectly trigger the waitlist bump per-booking — this feature cancels the whole session as one action with its own (non-bumping) semantics. BDD §2, §3.4.

## Business Rules
- Cancels every slot/booking within a full session in one action.
- Does NOT trigger a waitlist bump for any released slot — only feature 025 (individual voluntary fixed-time cancellation) bumps the waitlist. This is an explicit, deliberate exclusion.
- Applies to both fixed-time and queue-mode sessions.
- Affected patients' bookings are marked cancelled, which feeds the notification pipeline (036/037) — but the source doc does not specify an automatic re-waitlisting flow for these patients; this feature assumes they are NOT auto-added to any waitlist, consistent with the general exclusion rule.

## Acceptance Criteria
- Given a session (fixed-time or queue-mode) with active bookings, when staff cancels the whole session, then every booking within it transitions to cancelled.
- Given the whole session is cancelled, when the waitlist is checked, then no bump occurs for any of the released slots.
- Given the whole session is cancelled, when affected patients are identified, then each cancelled booking is recorded for notification purposes (036/037) but no automatic waitlist entry is created for them.

## Dependencies
- Depends on: 011-nightly-rolling-session-generation — a generated session must exist to cancel
- Depends on: 016/017/018 booking features — bookings within the session must exist to be cancelled
- Explicitly excluded from: 025-individual-booking-cancellation-waitlist-trigger's bump logic

## Explicitly Out of Scope
- Automatic re-waitlisting of patients affected by whole-session cancellation.
- Automatic rebooking of affected patients into a different session.

## Source References
- BDD §2 (In-Scope: "Whole-day and partial (cutoff-based) session cancellation")
- BDD §3.4 (waitlist bump exclusivity rule)
