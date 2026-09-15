# 008 — De-Verification Cascade (Auto-Cancel Future Bookings)

**Module:** Identity & Access
**Status:** Ready for spec-kit intake

## User Story
As a Super Admin, when I un-verify a clinic or explicitly reject/revoke a doctor's license, I want the system to automatically cancel their future bookings, so that patients aren't left holding appointments against a provider who has just lost verification, without anyone having to notice and act manually.

## Context
The source doc originally flagged this as an unresolved gap: "De-verification has no cascade — un-verifying a clinic or rejecting a doctor's license after they've already treated patients doesn't touch existing schedules/bookings; appears to only affect future discoverability, but this was never an explicit product decision" (BDD §7.9#5, §8#11). For this build, the product decision has been made: de-verification **does** cascade, and the cascade **auto-cancels** future bookings (not merely flags them for review).

## Business Rules
- **Trigger 1:** Super Admin un-verifies a previously verified Clinic (002-super-admin-clinic-verification).
- **Trigger 2:** Super Admin explicitly rejects or revokes a Doctor's license via an intentional admin action (distinct from the automatic edit-triggered reset in 006-doctor-license-edit-reverification-reset, which does **not** trigger this cascade).
- On either trigger, every future (not-yet-occurred) booking tied to the affected clinic (trigger 1) or affected doctor (trigger 2) is automatically cancelled.
- Cancellation reuses the normal individual-booking cancellation mechanics (025-individual-booking-cancellation-waitlist-trigger) — for fixed-time bookings this means the standard waitlist-bump flow fires per affected booking, exactly as if each had been cancelled individually.
- Each affected patient generates a cancellation notification event (036-notification-event-pipeline-opt-in-out); actual delivery is stubbed per 037-notification-delivery-stub, but the event still fires and is recorded.
- The cascade never touches past/completed bookings, clinical documentation, or any historical data — only bookings that haven't happened yet.
- Re-verifying the clinic or doctor later does **not** automatically restore the cancelled bookings — they remain cancelled; patients would need to be rebooked from scratch.

## Acceptance Criteria
- Given a verified Clinic with 5 future bookings across various doctors, when Super Admin un-verifies it, then all 5 future bookings are automatically cancelled, and fixed-time ones trigger waitlist bumps where a matching waitlist entry exists.
- Given a Doctor with `licenseVerified = true` and 3 future bookings, when Super Admin explicitly rejects/revokes their license, then those 3 future bookings are auto-cancelled.
- Given a Doctor whose `licenseVerified` resets purely because they edited their license number (006, not an explicit Super Admin action), when that happens, then **no cascade fires** — only discoverability is affected.
- Given past/completed bookings for a de-verified clinic or doctor, when the cascade runs, then those bookings are left completely untouched.
- Given a clinic is later re-verified, when checked, then the previously auto-cancelled bookings remain cancelled — they are not restored.

## Dependencies
- Depends on: 002-super-admin-clinic-verification, 005-doctor-profile-auto-creation-license-queue — the verification actions that can trigger this cascade.
- Depends on: 025-individual-booking-cancellation-waitlist-trigger — reuses its cancellation + waitlist-bump mechanics.
- Feeds: 036-notification-event-pipeline-opt-in-out — generates cancellation notification events.

## Explicitly Out of Scope
- Restoring auto-cancelled bookings upon later re-verification.
- Cascading from the automatic, edit-triggered `licenseVerified` reset in 006 (that stays discoverability-only, by design).
- A "flag for staff review instead of auto-cancel" mode — this was considered and explicitly rejected in favor of auto-cancel for v1.

## Source References
- BDD §7.9 (#5 — originally an open gap, now resolved for this build)
- BDD §8 (#11 — originally an open question, now resolved for this build)
- BDD §3.7 (verification enforcement generally)
