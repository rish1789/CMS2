# 025 — Individual Booking Cancellation & Waitlist Trigger

**Module:** Cancellation & Waitlist
**Status:** Ready for spec-kit intake

## User Story
As a patient or staff member, I want to cancel an individual fixed-time booking, so that the slot is released and, if a matching patient is waiting, they're automatically offered it.

## Context
This is the single, precisely-scoped trigger for waitlist activity in the system (BDD §3.4, §6.2) — deliberately narrower than "any way a slot becomes free." No-show release and whole/partial session cancellations do not bump the waitlist; only this voluntary, individual cancellation does.

## Business Rules
- Applies to fixed-time bookings.
- Can be initiated by staff at any time, or by the patient themselves — patient-initiated cancellation is only allowed up to **2 hours before** the scheduled slot time. After that, only staff can cancel it (e.g. patient calls the clinic).
- This is the ONLY trigger for a waitlist bump in the entire system. No-show releases (021) and whole/partial session cancellations (026, 027) explicitly do NOT bump the waitlist.
- On cancellation, the system finds the longest-waiting matching Waitlist Entry (full matching logic in 028) and offers it with a 30-minute claim window (claim mechanics in 029).
- The bump is event-driven — an automatic consequence of cancellation, not a separate manual staff step.
- "Reschedule" is NOT a separate atomic feature in v1. It is implemented purely as this cancellation feature followed by a fresh booking (016/017/018) — no atomic slot-swap exists.

## Acceptance Criteria
- Given a confirmed fixed-time booking, when staff cancels it, then the slot is released and a waitlist bump attempt is triggered per 028/029.
- Given a confirmed fixed-time booking, when the patient cancels it within the cutoff window, then the same waitlist bump behavior applies.
- Given a patient attempts to cancel less than 2 hours before the scheduled slot time, when they submit the cancellation, then the self-service action is blocked with a message directing them to contact the clinic; staff can still cancel it on their behalf.
- Given a booking is cancelled, when the waitlist is checked, then exactly one bump attempt occurs for the released slot.
- Given a user wants to change their appointment time, when they act, then the system requires a cancel action followed by a separate new booking — there is no single "reschedule" action.

## Dependencies
- Depends on: 016-staff-assisted-fixed-time-booking, 017-patient-self-service-fixed-time-booking — a booking must exist to cancel
- Feeds into: 028-waitlist-matching-longest-waiting, 029-self-service-waitlist-claim

## Explicitly Out of Scope
- Atomic reschedule (single action preserving fee/slot state) — accepted v1 gap; use cancel + rebook.
- Waitlist bump as a consequence of no-show (021) or whole/partial session cancellation (026, 027) — explicitly excluded by design.

## Source References
- BDD §3.4 (Cancellation & Waitlist: sole trigger rule)
- BDD §6.2 (Waitlist Bump flow)
- BDD §8.5 (reschedule — resolved as cancel-then-rebook for v1)
