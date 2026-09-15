# Feature Specification: Clinic Registration

**Feature Branch**: `001-clinic-registration`

**Created**: 2026-09-02

**Status**: Draft

**Input**: User description: "Clinic Registration — a prospective clinic owner/administrator registers their clinic on the platform, so they can start onboarding staff and eventually appear in public discovery once verified. Clinic creation and its first ClinicAdmin are created atomically — a Clinic can never exist without an admin. The clinic starts unverified. No Grievance Officer field, no billing/subscription step, no file upload. Password policy and Indian mobile number validation apply. (Full source: backlog/001-clinic-registration.md)"

## Clarifications

### Session 2026-09-02

- Q: Must a ClinicAdmin's email address be unique across the entire platform, or could the same email be reused for a different ClinicAdmin account at a different clinic? → A: Globally unique — one email maps to exactly one staff Account across the whole platform, regardless of clinic.
- Q: Should clinic registration also generate a memorable staff code (e.g. `CA-4821`-style) for the founding ClinicAdmin, the same way feature 004 does for staff hired later? → A: Yes — generate a staff code for the founding ClinicAdmin at registration, identical mechanism to 004. All staff Accounts behave uniformly.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Register a New Clinic with its Founding Admin (Priority: P1)

A prospective clinic owner submits their clinic's details along with their own account details, and in a single step becomes both the clinic's record-holder and its first ClinicAdmin, ready to start onboarding staff.

**Why this priority**: This is the entry point for every clinic on the platform — without it, no other feature (staff onboarding, scheduling, booking, discovery) has anything to attach to. It is the minimum viable slice: a clinic that exists with someone able to manage it.

**Independent Test**: Can be fully tested by submitting a registration form with clinic details and admin credentials, and confirming a Clinic record and an active ClinicAdmin Role Assignment both exist immediately afterward, with no intermediate state where one exists without the other.

**Acceptance Scenarios**:

1. **Given** no existing account, **When** a prospective owner submits clinic details (name, address, contact info) together with their own ClinicAdmin details (name, email, password), **Then** a new Clinic record and a ClinicAdmin Account + Role Assignment are created together in one atomic transaction.
2. **Given** the registration transaction fails partway through (e.g., admin account creation fails), **When** it rolls back, **Then** no orphaned Clinic record without an admin is left in the system.
3. **Given** a newly registered clinic, **When** creation completes, **Then** its verified status is false by default and it does not appear in public discovery search results until a Super Admin verifies it.
4. **Given** the registration form, **When** rendered, **Then** it contains no Grievance Officer field anywhere.
5. **Given** a submitted ClinicAdmin email that already belongs to an existing staff Account anywhere on the platform, **When** registration is submitted, **Then** it is rejected with a validation error indicating the email is already in use.
6. **Given** a successful registration, **When** it completes, **Then** the ClinicAdmin's Account has both a working email + password login and a system-generated staff code that authenticates the same Account identically.

---

### Edge Cases

- What happens when the submitted ClinicAdmin password doesn't meet the password policy? → Registration is rejected with a validation error identifying which specific rule failed (length, missing lowercase/uppercase/digit/special character).
- What happens when a submitted clinic contact mobile number doesn't match the Indian numbering plan? → Registration is rejected with a validation error; the mobile number field itself remains optional (a clinic can register without one).
- What happens if a registration attempt includes billing/payment information or a file upload? → No such fields exist in the registration flow to submit in the first place.
- What happens when a submitted ClinicAdmin email is already registered to an existing staff Account (at this clinic or any other)? → Registration is rejected with a validation error indicating the email is already in use; no new Clinic or Account is created.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST allow a prospective clinic owner to submit clinic details (name, address, contact info) and their own ClinicAdmin account details (name, email, password) in a single registration submission.
- **FR-002**: System MUST create the Clinic record and the ClinicAdmin Account + Role Assignment atomically, in one transaction — a Clinic MUST never exist without an admin, even transiently.
- **FR-003**: System MUST roll back the entire registration transaction, leaving no orphaned Clinic record, if any part of clinic or admin creation fails.
- **FR-004**: System MUST set every newly created Clinic's verified status to false by default.
- **FR-005**: System MUST exclude unverified clinics from public discovery search results.
- **FR-006**: System MUST NOT present, collect, or store a Grievance Officer field anywhere in the registration flow.
- **FR-007**: System MUST NOT include any billing, subscription, or payment step in registration, and MUST NOT gate clinic access on payment status.
- **FR-008**: System MUST NOT accept file or document uploads as part of registration.
- **FR-009**: System MUST validate the ClinicAdmin password against policy — minimum 8 characters, at least one lowercase letter, one uppercase letter, one digit, and one special character — and reject non-conforming passwords with an error identifying which rule failed.
- **FR-010**: System MUST validate a provided clinic contact mobile number against the Indian numbering plan (10 digits starting with 6–9, with an optional `+91` or `0` prefix) and reject non-conforming numbers with a validation error.
- **FR-011**: The clinic contact mobile number MUST be optional — registration MUST be completable without one.
- **FR-012**: System MUST enforce that a ClinicAdmin's email address is unique across the entire platform (not merely within one clinic); a registration attempt using an email already associated with any existing staff Account MUST be rejected with a validation error.
- **FR-013**: System MUST generate a unique, memorable staff code (e.g. `CA-4821`-style) for the founding ClinicAdmin's Account at registration time, using the same generation mechanism as staff onboarded later (004-staff-onboarding-direct-hire), so the Account supports both email+password and staff-code+password login (003-staff-login-password-or-code) from the moment it's created.

### Key Entities

- **Clinic**: The registering business — name, address, contact info (including an optional mobile number), and a verified flag that defaults to false. Always created together with its first ClinicAdmin.
- **Account**: The login identity created for the ClinicAdmin — a globally unique email, a password credential (validated against the password policy), and a globally unique, system-generated staff code (an alternate login identifier for the same Account).
- **Role Assignment**: Links an Account to a Clinic with a role (ClinicAdmin, in this feature). Created in the same atomic transaction as the Clinic and Account.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A prospective clinic owner can complete clinic + admin registration in a single submission, with no separate follow-up step required to activate the admin.
- **SC-002**: 100% of clinics that exist in the system have at least one active ClinicAdmin at all times — zero orphaned clinics are ever observed, including after a failed registration attempt.
- **SC-003**: 100% of newly registered clinics are absent from public discovery search results until explicitly verified.
- **SC-004**: 100% of registration submissions with a non-conforming password or mobile number are rejected with a specific, rule-identifying error message — none are silently accepted.

## Assumptions

- Clinic name, address, and contact fields other than the mobile number carry standard presence/format validation only; the source material does not specify additional constraints on them.
- Clinic name is NOT required to be globally unique — two different clinics may share a name (e.g. a common business name reused in different locations); only ClinicAdmin email and staff code are subject to platform-wide uniqueness (see Clarifications).
- The staff-code generation mechanism this feature relies on (FR-013) is shared with 004-staff-onboarding-direct-hire — both features must generate codes from the same platform-wide-unique namespace so no collision can occur between a founding ClinicAdmin's code and a later-hired staff member's code.
- Clinic verification and public discovery are separate, already-scoped features; this spec only asserts the boundary condition (an unverified clinic is hidden from discovery), not their internal implementation.
