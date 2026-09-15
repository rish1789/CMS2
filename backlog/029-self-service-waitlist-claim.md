# 029 — Self-Service Waitlist Claim

**Module:** Cancellation & Waitlist
**Status:** Ready for spec-kit intake

## User Story
As a waitlisted patient, I want to claim or decline an offered slot myself within the claim window, so that I don't have to wait for a phone call from the clinic to secure it.

## Context
The source doc explicitly describes this as a gap: "No self-service claim endpoint exists yet — the 30-minute window is tracked, but claiming currently appears to happen by phone, outside the system" (BDD §3.4, §7.9(2), §8(6)). **This project's v1 scope deliberately reverses that gap and builds the self-service claim flow**, since notifications are already being built as a stubbed-but-structured pipeline (036/037) and this is a natural extension of it.

## Business Rules
- Offer window: 30 minutes from the moment the offer is made (per the match selected in 028).
- The patient can Claim or Decline the offer via an in-app action (delivered through the notification pipeline, 036/037).
- Claiming within the window atomically converts the Waitlist Entry into a confirmed Booking — the fee is resolved and locked at the moment of claim, per the normal booking-time fee rules (015).
- Declining releases the offer immediately, before the 30 minutes elapse, and triggers the same cascade behavior as an expiry (below).
- If the window expires with no claim or decline (or the patient explicitly declines), the system automatically re-runs the matching logic (028) against the remaining waitlist and offers the slot to the next-longest-waiting eligible entry, with a fresh 30-minute window. This repeats until an entry claims the slot or the eligible waitlist is exhausted, at which point the slot becomes available for regular booking.

## Acceptance Criteria
- Given a waitlist entry is offered a slot, when the patient claims it within 30 minutes, then the Waitlist Entry is converted into a confirmed Booking atomically and the slot is no longer available to others (including to the rest of the waitlist).
- Given a waitlist entry is offered a slot, when the patient declines, then the offer is released immediately and the system offers the slot to the next-longest-waiting eligible entry per 028.
- Given a waitlist entry is offered a slot, when 30 minutes elapse with no claim or decline, then the offer expires and the system offers the slot to the next-longest-waiting eligible entry per 028, with a new 30-minute window.
- Given the eligible waitlist is exhausted (every entry has declined or let its offer expire), when the last offer lapses, then the slot becomes available for regular booking with no further waitlist offers.
- Given a patient claims a slot, when the booking is created, then normal fee-resolution-and-locking rules (015) apply at that moment, not at the original waitlist join time.

## Dependencies
- Depends on: 028-waitlist-matching-longest-waiting — provides the matched entry to offer
- Depends on: 036-notification-event-pipeline-opt-in-out / 037-notification-delivery-stub — the patient must be notified of the offer to act on it
- Depends on: 015-fee-resolution-and-locking — applied at the moment of claim
- Depends on: 039-patient-account-global-login — the patient needs a logged-in identity to perform a self-service claim action

## Explicitly Out of Scope
- Phone-based claiming as the primary mechanism — this feature replaces that gap with an in-app flow (staff can still call patients informally, but the system itself doesn't require it).

## Source References
- BDD §3.4, §6.2 (Waitlist Bump flow, step 3-4 — step 4 explicitly reversed by this project's v1 scope)
- BDD §7.9(2) (new material gap: no self-service waitlist claim flow)
- BDD §8(6) (pending clarification, resolved as: build it)
