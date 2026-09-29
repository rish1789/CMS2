# Feature Specification: Patient Account & Global Login

**Feature Branch**: `002-patient-account-login`

**Created**: 2026-09-02

**Status**: Draft

**Input**: User description: "Patient Account & Global Login — a patient creates a single login identity that works across every clinic on the platform, so they don't need a separate username/password per clinic, even though their visit history and clinical records stay clinic-scoped. Self-service signup, email + password with policy enforcement, optional Indian-format mobile number, entirely separate from staff Accounts, holds the platform-wide notification opt-in/out preference. (Full source: backlog/039-patient-account-global-login.md)"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Self-Service Patient Signup and Login (Priority: P1)

A new visitor creates their own Patient Account with an email and password, and can then log in with those credentials — once, for use across every clinic they visit on the platform.

**Why this priority**: This is the foundational identity a patient needs before any self-service booking (017, 018) can exist. Without it, patients can only be walk-ins handled entirely by staff.

**Independent Test**: Can be fully tested by submitting a signup form with a valid email and a policy-conforming password, then logging in with those same credentials and confirming access to a patient-only session.

**Acceptance Scenarios**:

1. **Given** a new visitor, **When** they self-register a Patient Account with a valid email and a password meeting the policy (8+ chars, upper, lower, digit, special char), **Then** the account is created and active immediately, and they can log in with that email and password.
2. **Given** a password that fails any one of the policy's requirements, **When** the patient attempts to register, **Then** the system rejects it with an error identifying which specific rule(s) failed.
3. **Given** a Patient Account holder, **When** they log in with the wrong password, **Then** the login attempt is rejected without revealing whether the email itself is registered.

---

### User Story 2 - Optional Mobile Number at Signup (Priority: P2)

A patient optionally adds a mobile number when signing up, for future contact purposes — but isn't blocked from signing up without one.

**Why this priority**: Secondary to the core signup/login flow — the account is fully functional without a mobile number, but many patients will want to add one, and the validation rule needs to be right from day one to avoid bad data.

**Independent Test**: Can be tested independently by submitting signup once with a valid mobile number and once with none, confirming both succeed, and once with an invalid-format number, confirming it's rejected.

**Acceptance Scenarios**:

1. **Given** a mobile number that matches the Indian numbering plan, **When** provided at signup, **Then** signup succeeds and the number is stored.
2. **Given** a mobile number that doesn't match the Indian numbering plan (wrong length, or doesn't start 6–9), **When** provided at signup, **Then** signup is rejected with a validation error.
3. **Given** no mobile number is provided at all, **When** signup is submitted, **Then** it still succeeds — the mobile number is optional.

---

### Edge Cases

- What happens when someone tries to register a Patient Account with an email that's already registered as a Patient Account? → Rejected with a "this email is already registered" error (no new account created).
- What happens when someone tries to register a Patient Account using an email that's already in use by a staff Account (a different identity system entirely, per 003/004)? → Succeeds — Patient Account and staff Account are separate identity spaces with independent uniqueness; the same email may exist in both.
- What happens when a Patient Account holder tries to access a staff-only area (ClinicAdmin/Doctor/Operations/Super Admin surfaces)? → Access is denied; a Patient Account session carries no staff role and is never treated as one.
- What happens when a Patient Account holder views their booking/visit history? → They see only the clinic-scoped Patient records actually linked to their account (via 019-patient-record-auto-creation-phone-linking) — never a unified cross-clinic record, since none exists.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST allow a visitor to self-register a Patient Account by submitting an email and a password — no staff-mediated step exists or is required.
- **FR-002**: System MUST validate the submitted password against the policy — minimum 8 characters, at least one lowercase letter, one uppercase letter, one digit, and one special character — and reject non-conforming passwords with an error identifying which rule(s) failed.
- **FR-003**: System MUST enforce that a Patient Account's email is unique among Patient Accounts; a signup attempt with an already-registered Patient Account email MUST be rejected.
- **FR-004**: System MUST treat Patient Account email uniqueness independently from staff Account email uniqueness (001/003/004) — the same email MAY be used for both a Patient Account and a staff Account, since they are separate identity systems sharing no table or auth logic.
- **FR-005**: System MUST activate a newly created Patient Account immediately — there is no pending/awaiting-verification state before the account can be used to log in.
- **FR-006**: System MUST allow (not require) a mobile number at signup; when provided, it MUST match the Indian numbering plan (10 digits starting 6–9, with an optional `+91` or `0` prefix) or be rejected with a validation error. Signup MUST succeed when no mobile number is provided.
- **FR-007**: System MUST authenticate a Patient Account holder using their registered email and password, and MUST reject an incorrect password without revealing whether the email itself is registered.
- **FR-008**: System MUST keep the Patient Account login system entirely separate from staff Account login (003) — no shared tables, no shared authentication logic, and a Patient Account session MUST never grant access to any staff-only (ClinicAdmin/Doctor/Operations/Super Admin) area.
- **FR-009**: A Patient Account MUST store a per-account notification opt-in/out preference, for use by the notification pipeline (036).
- **FR-010**: System MUST NOT unify or merge clinical/booking data across clinics under a Patient Account — a Patient Account is a login identity only; visit/clinical history remains attached to separate, clinic-scoped Patient records (019), and viewing "my visit history" MUST show only those linked records, not a synthesized global one.
- **FR-011**: System MUST NOT offer social login, SSO, or any third-party identity provider for Patient Account signup or login in this version.

### Key Entities

- **Patient Account**: The patient's global, self-service login identity — email (unique among Patient Accounts), password (hashed, policy-enforced), an optional mobile number (Indian-format if present), a notification opt-in/out preference, and an active flag (true from creation). Distinct from the clinic-scoped Patient entity (019) and from staff Account (001/003/004) — no shared table or identifier space with either.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A visitor can complete Patient Account signup and immediately log in with the same credentials, with no intermediate verification step blocking access.
- **SC-002**: 100% of signup submissions with a non-conforming password are rejected with a rule-identifying error message — none are silently accepted.
- **SC-003**: 100% of signup submissions with an invalid-format mobile number are rejected; 100% of submissions omitting a mobile number entirely succeed.
- **SC-004**: 0% of Patient Account sessions are ever able to reach a staff-only area — access control between the two identity systems has no crossover, verified by attempting cross-access.
- **SC-005**: 100% of duplicate-email signup attempts within the Patient Account system are rejected, while the same email remaining usable for an independent staff Account signup is never blocked by this feature.

## Assumptions

- Email is the required signup/login credential; the mobile number is optional supplementary contact information, not an alternate login identifier (unlike staff Accounts' email-or-staff-code duality — nothing in the source material describes an equivalent generated code for patients).
- No email-verification-link or phone-OTP step exists in v1: the account activates immediately on signup. This follows directly from the system-wide notification-delivery stub (037-notification-delivery-stub) — there is no live email/SMS sending capability to deliver a verification message with in the first place, and 004's precedent (staff accounts active immediately, no pending state) supports the same pattern here.
- Standard email format validation applies to the email field; no additional constraint beyond that and the stated uniqueness rule.
- Password change/reset flows are out of scope for this feature (mirroring 003's explicit scope note that a forgot-password flow is a separate future decision) — this feature covers signup and login only.
