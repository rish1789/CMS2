# 027 — Partial (Cutoff-Based) Session Cancellation

**Module:** Cancellation & Waitlist
**Status:** Ready for spec-kit intake

## User Story
As staff, I want to cancel only the remaining portion of a session from a certain point onward (e.g. a doctor needs to leave early), so that patients already seen or already past that point aren't affected while the rest of the day's slots are freed.

## Context
Distinct from whole-day cancellation (026), this cancels only a trailing portion of a session. BDD §2 names it explicitly as "partial (cutoff-based) session cancellation." BDD §3.4.

## Business Rules
- Cutoff is defined by **time**, not token/slot position: staff select a cutoff time (e.g. "cancel everything from 4:00 PM onward").
- Every not-yet-completed slot (OPEN or BOOKED) scheduled at or after the cutoff time is cancelled. Slots already COMPLETED, or scheduled before the cutoff time, are left untouched.
- Does NOT trigger a waitlist bump for any released slot — same exclusion as whole-day cancellation (026); only feature 025 bumps the waitlist.
- Applies uniformly to both fixed-time and queue-mode sessions, since the cutoff is always a wall-clock time compared against each slot's scheduled time (queue-mode slots still carry a scheduled/generated time even though patients are called by queue position, not by strict clock adherence).

## Acceptance Criteria
- Given a fixed-time session with some slots completed and others still open/booked, when staff triggers a partial cutoff cancellation at a given cutoff time, then every OPEN/BOOKED slot scheduled at or after that time transitions to cancelled; earlier/completed slots are untouched.
- Given a partial cancellation occurs, when the waitlist is checked, then no bump occurs for the released slots.
- Given patients affected by the cancelled (post-cutoff) portion, when the cancellation completes, then their bookings are marked cancelled and fed to the notification pipeline (036/037).

## Dependencies
- Depends on: 011-nightly-rolling-session-generation — a generated session must exist to cancel
- Depends on: 016/017/018 booking features — bookings within the session must exist to be cancelled
- Explicitly excluded from: 025-individual-booking-cancellation-waitlist-trigger's bump logic

## Explicitly Out of Scope
- Automatic re-waitlisting of affected patients.
- Automatic rebooking of affected patients into a different session.

## Source References
- BDD §2 (In-Scope: "Whole-day and partial (cutoff-based) session cancellation")
- BDD §3.4 (waitlist bump exclusivity rule)
