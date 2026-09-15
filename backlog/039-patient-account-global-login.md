# 039 — Patient Account & Global Login

**Module:** Patient Identity
**Status:** Ready for spec-kit intake

## User Story
As a patient, I want to create a single login identity that works across every clinic on the platform, so that I don't need a separate username/password for each clinic I visit, even though my actual visit history and clinical records stay specific to each clinic.

## Context
Patient Account is a genuinely new, global entity, entirely separate from staff Accounts (which are clinic-scoped). It solves *login* identity only — it deliberately does **not** create a single global medical record; clinical/booking records remain clinic-scoped (see 019, which handles the per-clinic Patient record and its phone-based linking to walk-in history). BDD §2, §5, §7.2.

## Business Rules
- Patient Account is a **separate, global** self-service identity, distinct from any clinic-scoped Patient record and distinct from staff Accounts (email+password/staff-code login is a different system, see 003).
- One real person, one Patient Account — recognized across every clinic they use, but **for login purposes only**. It does not merge or unify clinical/booking data across clinics.
- Relationship: **Patient Account 1─N Patient** — one Patient Account may be associated with multiple clinic-scoped Patient records (one per clinic actually visited), created via feature 019 as the account holder books at each new clinic.
- Password policy: minimum 8 characters, requiring lowercase, uppercase, digit, and special character — enforced identically to staff signup (003/004).
- Mobile number validation: must match the Indian numbering plan (10 digits starting 6–9, optional `+91`/`0`) when a mobile number is provided at signup.
- Login is self-service — the patient registers directly (email/phone + password), there is no staff-mediated onboarding step for a Patient Account (contrast with staff onboarding, 004, which is ClinicAdmin-initiated).
- A Patient Account holds the platform-wide notification opt-in/out preference used by feature 036.

## Acceptance Criteria
- Given a new visitor, when they self-register a Patient Account with a valid email/phone and a password meeting the policy (8+ chars, upper, lower, digit, special char), then the account is created and they can log in.
- Given a password that fails any one of the policy's requirements (e.g. missing a special character), when the patient attempts to register or change their password, then the system rejects it with a clear reason.
- Given a mobile number that doesn't match the Indian numbering plan (e.g. wrong length, doesn't start 6–9), when provided at signup, then the system rejects it; given no mobile number is provided at all, then signup still succeeds (mobile number is optional at the account level, mirroring the walk-in optionality elsewhere in the system).
- Given a Patient Account holder logs in, when they view "their" visit history, then they only see the clinic-scoped Patient records (and associated bookings) actually linked to their account (via 019) — not a unified cross-clinic medical record.
- Given the same Patient Account holder books at two different clinics for the first time, when each booking is confirmed, then two separate clinic-scoped Patient records are created (one per clinic, per 019), both linked to the same Patient Account for login purposes.
- Given a Patient Account holder attempts to log into a staff-only area (ClinicAdmin/Doctor/Operations/Super Admin surfaces), when they try, then access is denied — Patient Account and staff Account are entirely separate identity systems with no crossover.

## Dependencies
- Feeds into: 019-patient-record-auto-creation-phone-linking — a Patient Account holder's first booking at a given clinic triggers (or links to) a clinic-scoped Patient record.
- Feeds into: 017-patient-self-service-fixed-time-booking, 018-queue-token-booking — self-service booking requires being logged in as a Patient Account holder.
- Feeds into: 036-notification-event-pipeline-opt-in-out — opt-in/out preference lives on the Patient Account.
- Related: 003-staff-login-password-or-code, 004-staff-onboarding-direct-hire — parallel but entirely separate identity system; do not share tables/auth logic with staff Accounts.

## Explicitly Out of Scope
- Any unification of clinical/booking records across clinics into a single global medical record — explicitly and deliberately out of scope; login identity is global, clinical data is not.
- Staff-mediated Patient Account creation — this is self-service only; staff create clinic-scoped walk-in Patient records instead (see 019/020), which are a different thing entirely.
- Social login / SSO / third-party identity providers — not mentioned in the source doc; assume email/phone + password only unless clarified during spec-kit intake.
- Multi-language/localized signup UI — English-only for v1.

## Source References
- BDD §2 (in-scope: "Patient Account as a separate, global self-service identity, distinct from any clinic-scoped Patient record")
- BDD §5 (Entities: "Patient Account — a genuinely new entity..."; Relationships: "Patient Account 1─N Patient")
- BDD §7.2 (Patient identity duplication — clarified, not fully resolved)
- BDD §4 (NFR: password policy, mobile number validation — "enforced identically across staff signup and patient signup")
