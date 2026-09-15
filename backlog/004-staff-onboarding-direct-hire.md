# 004 — Staff Onboarding (Direct-Hire)

**Module:** Identity & Access
**Status:** Ready for spec-kit intake

## User Story
As a ClinicAdmin, I want to add a new Doctor or Operations staff member directly — filling one form, with no invitation/accept-link step — so that they can start working immediately with system-generated login credentials I hand them myself.

## Context
BDD §7.1 settles a prior back-and-forth in earlier design sessions: staff onboarding is **direct-hire only**. Any earlier "invitation" concept (pending-invite table, accept-link flow) has been removed entirely (§5). This is the single onboarding mechanism for bringing Doctor or Operations staff into a clinic.

## Business Rules
- No invitation/accept-link flow exists anywhere in the system — no `Invitation` entity, no pending-invite state, no accept-link endpoint (BDD §5, §7.1).
- ClinicAdmin submits: name, contact info, role (`Doctor` or `Operations` only), and — if the role is Doctor — specialization, license number, and experience.
- Account creation and Role Assignment happen together in one step and are active immediately; there is no pending/awaiting-acceptance state for the new hire.
- The system auto-generates a login ID and a one-time temporary password. These are handed to the new hire directly by the ClinicAdmin, outside the system (e.g. verbally or on paper) — email/SMS delivery of credentials is not performed, since notification delivery is stubbed in v1 (see 037-notification-delivery-stub).
- If the hire is a Doctor, a Doctor Profile is created in the same transaction and automatically enters the license-verification queue (see 005-doctor-profile-auto-creation-license-queue).
- A ClinicAdmin can never create a peer ClinicAdmin or a Super Admin through this flow — the role selector only offers Doctor and Operations, and this is enforced server-side as well as in the UI (BDD §2 persona table, §6.1).
- The temporary password must satisfy the password policy: minimum 8 characters, lowercase, uppercase, digit, and special character (BDD §4).
- If a mobile number is collected for the new hire, it must match the Indian numbering plan (10 digits starting 6–9, optional `+91`/`0`).

## Acceptance Criteria
- Given a ClinicAdmin fills the Operations onboarding form and submits it, when processed, then an Account + Operations Role Assignment is created, active immediately, and a generated login ID + temp password are returned to the ClinicAdmin's screen.
- Given a ClinicAdmin fills the Doctor onboarding form (name, contact, specialization, license, experience) and submits it, when processed, then an Account + Doctor Role Assignment + Doctor Profile are all created in the same transaction, and the Doctor Profile's `licenseVerified` starts `false` (awaiting verification).
- Given the role selector, when a ClinicAdmin attempts to submit role = `ClinicAdmin` or `Super Admin`, then the request is rejected server-side even if the UI were somehow bypassed.
- Given the codebase/API surface, when inspected, then no `Invitation` entity, pending-invite table, or accept-link endpoint exists.
- Given a generated temporary password, when checked, then it satisfies the password policy.

## Dependencies
- Depends on: 001-clinic-registration — the clinic and its first ClinicAdmin must already exist.
- Feeds: 003-staff-login-password-or-code — the credentials generated here are what's used to log in.
- Feeds: 005-doctor-profile-auto-creation-license-queue — Doctor Profile creation is triggered from here.
- Related: 007-last-active-clinicadmin-protection — this flow never creates additional ClinicAdmins, which interacts with that feature's invariant (see 007's notes).

## Explicitly Out of Scope
- Any invitation/accept-link flow — removed entirely, must not be reintroduced.
- Creating ClinicAdmin or Super Admin accounts via this flow.
- Email/SMS delivery of generated credentials (notifications are stubbed in v1).

## Source References
- BDD §2 (Persona table: ClinicAdmin cannot create peers)
- BDD §3.1 (Doctor Profile auto-creation)
- BDD §4 (password policy, mobile number validation)
- BDD §5 (Invitation entity removed)
- BDD §6.1 (full onboarding flow, step by step)
- BDD §7.1 (settled: direct-hire only)
