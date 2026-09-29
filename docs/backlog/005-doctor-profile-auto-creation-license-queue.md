# 005 — Doctor Profile Auto-Creation & License Verification Queue

**Module:** Identity & Access
**Status:** Ready for spec-kit intake

## User Story
As the system, I want to automatically create a single global Doctor Profile and enter it into a license-verification queue the moment someone is onboarded as a Doctor, so that Super Admin has a clear worklist and the doctor cannot appear publicly until verified.

## Context
BDD §2 describes "Doctor Profile as one global record per Account, license verification queue, public-visibility toggle." §3.1 confirms the profile is created automatically at onboarding time and enters an "awaiting license verification" queue immediately. §7.5 confirms the profile is global per Account (not per-clinic) — this was an open architecture question in an earlier design phase and is now resolved by the actual implementation pattern this build follows.

## Business Rules
- Exactly one Doctor Profile exists per Account, globally — not one per clinic. A doctor working at multiple clinics still has a single Doctor Profile, referenced by multiple Role Assignments.
- The profile is created automatically and immediately as part of Doctor onboarding (004-staff-onboarding-direct-hire) — there is no separate manual "create profile" step.
- The profile starts with `licenseVerified = false` ("awaiting license verification").
- A separate public-visibility toggle exists on the Doctor Profile, independent of `licenseVerified`. Both `licenseVerified = true` AND visibility = on AND the clinic's `verified = true` are required for the doctor to appear in public discovery (BDD §3.7; see 035-public-discovery-search).
- License verification is manual/off-platform, performed by Super Admin — the same manual-review pattern as clinic verification (002), applied here to doctors: Super Admin reviews credentials externally, then flips `licenseVerified` in an admin screen.

## Acceptance Criteria
- Given a ClinicAdmin onboards a new Doctor (004), when the Doctor Role Assignment is created, then a Doctor Profile is created in the same transaction with `licenseVerified = false`.
- Given a Doctor already has a Doctor Profile from a prior onboarding at another clinic, when they are onboarded again at a second clinic, then no duplicate Doctor Profile is created — the existing global profile is reused and a new Role Assignment links them to the second clinic. *(Note for spec-kit: the source doc confirms the data model — one profile per Account — but does not describe the onboarding-time lookup/matching UX for "is this the same doctor as an existing Account." That lookup mechanism needs to be defined precisely during spec-kit's clarification phase.)*
- Given a Doctor Profile with `licenseVerified = false`, when public discovery is queried, then this doctor does not appear regardless of the visibility toggle's value.
- Given Super Admin reviews credentials externally and approves them, when `licenseVerified` is set to `true` (with visibility on and clinic verified), then the doctor becomes discoverable.

## Dependencies
- Depends on: 004-staff-onboarding-direct-hire — the trigger for profile creation.
- Depends on: 002-super-admin-clinic-verification — discovery also requires the clinic to be verified.
- Feeds: 006-doctor-license-edit-reverification-reset, 008-deverification-cascade-auto-cancel-bookings, 035-public-discovery-search.

## Explicitly Out of Scope
- In-app document upload for license proof.
- Automated license validation against an external registry.

## Source References
- BDD §2 (Doctor Profile as global record)
- BDD §3.1 (auto-creation at onboarding)
- BDD §3.7 (data-level discovery enforcement)
- BDD §7.5 (confirmed resolved: one global profile per Account)
