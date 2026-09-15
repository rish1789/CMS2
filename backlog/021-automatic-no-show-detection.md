# 021 — Automatic No-Show Detection

**Module:** Day-of Operations
**Status:** Ready for spec-kit intake

## User Story
As the scheduling system, I want to automatically mark a fixed-time booking as no-show after a grace period, so that slot status reflects reality, no-show history is captured for buffer sizing, and unattended slots become eligible for walk-in reclaim.

## Context
Clinics need the system to detect, without staff intervention, when a booked patient simply doesn't show up, so downstream features (walk-in insertion, buffer sizing) have accurate signal to work from. BDD §2 and §3.3.

## Business Rules
- 10-minute grace period past a slot's scheduled time before an unattended booked slot is auto-marked NO_SHOW.
- Skipped entirely if the slot is on an explicit hold — held slots are never auto-marked no-show.
- Auto-marking a no-show frees nothing for automatic rebooking. The walk-in queue can claim the slot manually as a "reclaimed no-show" (see 020), but nothing auto-assigns it.
- Increments the patient's no-show count, which is the raw input to the 90-day trailing history used by buffer-slot sizing (022).
- Does NOT trigger a waitlist bump — waitlist bumps are exclusively triggered by feature 025 (individual voluntary fixed-time cancellation).
- Applies to fixed-time slots, which carry an individual scheduled time. Whether queue-mode bookings are also subject to no-show auto-release is not stated explicitly in the source doc — flagged below.

## Acceptance Criteria
- Given a fixed-time slot booked for 10:00 AM with no hold, when the clock reaches 10:10 AM and the patient has not been marked arrived/completed, then the system automatically marks the slot NO_SHOW and increments the patient's no-show count.
- Given a slot is on an explicit hold, when 10+ minutes past its scheduled time elapse, then the system does NOT auto-mark it no-show.
- Given a slot is auto-marked no-show, when the waitlist is checked, then no bump is triggered and the slot is not auto-reassigned — it becomes eligible for manual walk-in insertion (020) as a "reclaimed no-show" source.
- Given a patient is auto-marked no-show, when their trailing history is later queried by the buffer-sizing job (022), then this event counts within the 90-day window.

## Dependencies
- Depends on: 016-staff-assisted-fixed-time-booking, 017-patient-self-service-fixed-time-booking — a confirmed fixed-time booking must exist to be marked no-show
- Feeds into: 020-walk-in-priority-insertion — reclaimed no-show slots are priority #2 in the walk-in fallback order
- Feeds into: 022-buffer-slot-capacity-sizing — no-show count is the raw signal for the 90-day trailing formula
- Explicitly does NOT feed into: 025-individual-booking-cancellation-waitlist-trigger — no-show release never bumps the waitlist

## Explicitly Out of Scope
- Automatic rebooking of a no-show slot.
- Waitlist bump as a consequence of no-show.
- No-show detection while a slot is on hold.
- Whether queue-mode bookings get equivalent no-show detection is unresolved in the source doc — clarify during spec-kit intake before implementation; this feature is written assuming fixed-time-only scope.

## Source References
- BDD §2 (In-Scope: "Automatic no-show detection (10-minute grace)")
- BDD §3.3 (Day-of Operations: no-show auto-release rule)
