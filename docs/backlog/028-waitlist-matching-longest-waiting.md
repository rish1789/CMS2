# 028 — Waitlist Matching (Longest-Waiting, Doctor/Specialization)

**Module:** Cancellation & Waitlist
**Status:** Ready for spec-kit intake

## User Story
As the system, I want to select the correct waitlist entry to offer when a fixed-time slot opens up, so that the longest-waiting, best-matching patient gets the first opportunity to claim it.

## Context
This was an open item in the design-phase BDD (§7.4) and is now resolved by code behavior. It runs exclusively off the single trigger defined in 025. BDD §3.4, §6.2, §7.4.

## Business Rules
- Matching is a strict two-tier priority, evaluated in order — NOT a single "longest-waiting overall" sort:
  1. **Doctor-match tier**: among all waitlist entries specifying the same doctor as the newly-available slot, offer the longest-waiting one.
  2. **Specialization-only tier**: only if tier 1 has zero eligible entries, consider entries specifying the same specialization with no doctor preference set, and offer the longest-waiting one among those.
- A specialization-only entry NEVER outranks a doctor-match entry, regardless of how much longer the specialization-only entry has been waiting. Doctor-match is an absolute priority tier, not a tiebreaker.
- Within a tier, selection is strictly first-come-first-served by waitlist join time.
- Only ever invoked by feature 025 (individual voluntary fixed-time cancellation) — no other trigger calls this matching logic.

## Acceptance Criteria
- Given a doctor-match entry waiting 1 hour and a specialization-only entry (no doctor preference) waiting 3 days, when a slot opens for that doctor, then the doctor-match entry is offered, even though it has waited less time.
- Given multiple entries are eligible within the same tier (both doctor-match, or both specialization-only-match), when a slot opens, then the entry that joined the waitlist earliest is offered first.
- Given no waitlist entries match either criterion, when a slot opens via cancellation, then no offer is made and the slot becomes available for regular booking.

## Dependencies
- Depends on: 025-individual-booking-cancellation-waitlist-trigger — the only trigger for this matching logic
- Feeds into: 029-self-service-waitlist-claim — the selected entry receives the offer

## Explicitly Out of Scope
- Matching logic for no-show releases or whole/partial session cancellations — they never invoke this feature (per 025's exclusivity).

## Source References
- BDD §3.4 (Waitlist match selection rule)
- BDD §6.2 (Waitlist Bump flow, step 2)
- BDD §7.4 (Waitlist match ordering — resolved)
