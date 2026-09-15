# 001 — Clinic Registration

**Module:** Identity & Access
**Status:** Ready for spec-kit intake

## User Story
As a prospective clinic owner/administrator, I want to register my clinic on the platform, so that I can start onboarding staff and eventually appear in public discovery once verified.

## Context
Clinic registration is the entry point for every clinic on the platform. Per BDD §3.1, clinic creation and its first ClinicAdmin are created atomically — there is no such thing as a clinic without an admin, even transiently. The clinic starts unverified and only becomes publicly discoverable once Super Admin verifies it (see 002-super-admin-clinic-verification).

## Business Rules
- Clinic creation and its first ClinicAdmin role assignment happen atomically, in a single transaction — a Clinic can never exist without an admin (BDD §3.1).
- The new Clinic starts in an unverified state (`verified = false`); verification is a separate, later Super Admin action (002).
- No Grievance Officer field is collected at registration or anywhere else — explicitly dropped from v1 scope (resolved decision; the field exists in the reference doc but is unused there too).
- No billing/subscription/payment step is part of registration — there is no Subscription/Plan/Invoice entity in v1, and clinic access is never gated by payment status (resolved decision; §8#9).
- No file/document upload at registration — consistent with the system-wide rule that no clinical or verification file uploads exist anywhere (BDD §2 Out-of-Scope).
- If a clinic contact mobile number is collected, it must match the Indian numbering plan: 10 digits starting with 6–9, optional `+91`/`0` prefix (BDD §4).
- The ClinicAdmin's password must meet the password policy: minimum 8 characters, requiring lowercase, uppercase, digit, and special character (BDD §4).

## Acceptance Criteria
- Given no existing account, when a prospective owner submits clinic details (name, address, contact info) together with their own ClinicAdmin details (name, email, password), then a new Clinic record and a ClinicAdmin Account + Role Assignment are created together in one atomic transaction.
- Given the transaction fails partway through (e.g., admin account creation fails), when it rolls back, then no orphaned Clinic record without an admin is left in the system.
- Given a newly registered clinic, when creation completes, then `verified = false` by default and it does not appear in public discovery search (035) until Super Admin verifies it (002).
- Given the registration form, when rendered, then it contains no Grievance Officer field anywhere.
- Given a submitted ClinicAdmin password that doesn't meet policy, when registration is submitted, then it is rejected with a validation error identifying which rule failed.
- Given a clinic contact mobile number that doesn't match the Indian numbering plan, when submitted, then registration is rejected with a validation error.

## Dependencies
- Blocks: 002-super-admin-clinic-verification — a clinic must exist before it can be verified.
- Blocks: 004-staff-onboarding-direct-hire — the first ClinicAdmin created here is the one who onboards further staff.
- Blocks: 009-recurring-schedule-definition — schedules are defined per clinic.
- Blocks: 035-public-discovery-search — only verified clinics from this feature are discoverable.

## Explicitly Out of Scope
- Grievance Officer field (collection or display) — dropped entirely for v1.
- Billing/subscription capture at registration or ever — no platform-side billing model exists in v1.
- Any document/file upload as part of registration.
- Online payment collection.

## Source References
- BDD §2 (In-Scope: clinic registration + verification gate; Out-of-Scope: no file uploads, no billing)
- BDD §3.1 (atomic clinic + admin creation)
- BDD §4 (password policy, mobile number validation)
- BDD §5 (data model)
- BDD §7.9 (#4 Grievance Officer, #6 billing) — both resolved as dropped/out-of-scope for v1
- BDD §8 (#9 billing, #14 Grievance Officer) — resolved during scoping for this build
