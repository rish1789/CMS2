# 006 — Doctor License Edit Triggers Re-Verification Reset

**Module:** Identity & Access
**Status:** Ready for spec-kit intake

## User Story
As the system, I want editing a verified doctor's license number to automatically reset their verification status, so that a changed license number can never silently retain a stale verification.

## Context
BDD §3.1: "Editing a verified doctor's license number auto-resets `licenseVerified` to false." This is a narrow, precise rule — it fires only on the license number field, not on other Doctor Profile edits.

## Business Rules
- If a Doctor Profile currently has `licenseVerified = true`, and the license number field is edited and saved, then `licenseVerified` is automatically reset to `false` in the same transaction.
- This reset immediately removes the doctor from public discovery eligibility (since `licenseVerified` is a required condition, see 005/035) until Super Admin re-verifies.
- Editing any other Doctor Profile field (specialization, years of experience, contact info, visibility toggle) does **not** trigger this reset — only a change to the license number field does.
- This automatic, field-triggered reset does **not** cascade to existing bookings/schedules — it only affects discoverability. This is distinct from 008-deverification-cascade-auto-cancel-bookings, which is a deliberate, explicit Super Admin action (reject/revoke) that *does* cascade to auto-cancel future bookings. An edit-triggered reset is not the same event as an explicit revocation and is scoped narrower on purpose.

## Acceptance Criteria
- Given a Doctor Profile with `licenseVerified = true`, when the license number field is changed and saved, then `licenseVerified` becomes `false` in the same transaction as the edit.
- Given the same profile, when any other field (e.g., years of experience) is edited and saved, then `licenseVerified` is unchanged.
- Given `licenseVerified` resets to `false` due to this rule, when public discovery is queried, then the doctor no longer appears until re-verified.
- Given a license-number edit causes this reset, when checked, then no future bookings for this doctor are cancelled or flagged as a side-effect — only discoverability changes.

## Dependencies
- Depends on: 005-doctor-profile-auto-creation-license-queue — the profile and its `licenseVerified` field must exist.
- Related: 008-deverification-cascade-auto-cancel-bookings — a deliberately narrower, separate mechanism; this feature must not be implemented to also trigger that cascade.

## Explicitly Out of Scope
- Cascading this specific edit-triggered reset to existing bookings, schedules, or notifying patients — only the explicit Super Admin revoke/reject action in 008 does that.

## Source References
- BDD §3.1 (license-edit auto-reset rule)
