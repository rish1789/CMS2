# 002 — Super Admin Clinic Verification

**Module:** Identity & Access
**Status:** Ready for spec-kit intake

## User Story
As a Super Admin, I want to review and verify (or un-verify) a clinic, so that only legitimate clinics become publicly discoverable and I can act if a clinic later loses legitimacy.

## Context
BDD §2 lists "Clinic registration + Super Admin verification gate for public discoverability" as in-scope. §3.7 confirms discovery "enforces verification (clinic verified, doctor license-verified + visibility on) at the data level, not just response filtering." Per the resolved v1 scope decision, verification is a fully manual, off-platform review — Super Admin checks legitimacy outside the system and simply flips a flag in-app; there is no in-app document upload or review workflow.

## Business Rules
- Verification is manual and off-platform: Super Admin reviews clinic legitimacy through external means (phone, physical documents, etc.) — the system provides no document upload or structured review queue beyond a simple pending list.
- Super Admin toggles a boolean `verified` flag on the Clinic, optionally with a note, via an admin screen listing unverified/pending clinics.
- A clinic must have `verified = true` to be eligible for public discovery search (035) — this is enforced at the data-query level, not merely by filtering an API response (BDD §3.7).
- Only the Super Admin role may perform verify/un-verify actions; ClinicAdmin cannot self-verify their own clinic.
- Un-verifying a previously verified clinic triggers the de-verification cascade defined in 008-deverification-cascade-auto-cancel-bookings (future bookings at that clinic are auto-cancelled). This feature owns the verify/un-verify toggle itself; the cascade side-effect is specified separately in 008.

## Acceptance Criteria
- Given an unverified clinic, when Super Admin opens the pending-verification list, then the clinic appears with its registration details.
- Given Super Admin has reviewed a clinic externally and approves it, when they mark it verified in the admin UI, then `Clinic.verified` becomes `true` and it becomes eligible for public discovery.
- Given a verified clinic, when Super Admin un-verifies it, then `Clinic.verified` becomes `false`, it is immediately removed from public discovery results, and the cascade in 008 fires.
- Given a non-Super-Admin user (ClinicAdmin, Doctor, Operations), when they attempt to call the verify/un-verify action, then the request is rejected as unauthorized.

## Dependencies
- Depends on: 001-clinic-registration — a clinic must exist before it can be verified.
- Blocks: 035-public-discovery-search — discovery reads the verified flag at the data level.
- Related: 008-deverification-cascade-auto-cancel-bookings — un-verification triggers this cascade.
- Related: 005-doctor-profile-auto-creation-license-queue — doctors follow the same manual-verification pattern.

## Explicitly Out of Scope
- In-app document upload or structured review-queue workflow for verification evidence.
- Automated/algorithmic verification checks (e.g., against a government registry).

## Source References
- BDD §2 (In-Scope: verification gate)
- BDD §3.7 (data-level enforcement of verification for discovery)
- BDD §7.9 (#5 de-verification has no cascade — resolved separately in 008)
- BDD §8 (#2 verification criteria — resolved as fully manual/off-platform; #11 de-verification cascade — resolved in 008)
