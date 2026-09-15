# Feature Specification: Staff Onboarding (Direct-Hire)

**Feature Branch**: `004-staff-onboarding-direct-hire`

**Created**: 2026-09-02

**Status**: Draft

**Input**: User description: "Staff Onboarding (Direct-Hire) — a ClinicAdmin adds a new Doctor or Operations staff member directly, filling one form, no invitation/accept-link step, so they can start working immediately with system-generated login credentials. Account + Role Assignment created together, active immediately. System generates a login ID and one-time temp password, handed over outside the system. If Doctor, a Doctor Profile is created in the same transaction, entering the license-verification queue. A ClinicAdmin can never create a peer ClinicAdmin or Super Admin. Password policy and Indian mobile validation apply. (Full source: backlog/004-staff-onboarding-direct-hire.md)"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - ClinicAdmin Onboards an Operations Staff Member (Priority: P1)

A ClinicAdmin fills a single form with a new Operations hire's details and submits it, immediately getting back a login ID and temporary password to hand over.

**Why this priority**: This is the simpler of the two onboarding paths (no Doctor Profile/license-verification involved) and establishes the core direct-hire mechanism — one-step, active-immediately, credentials-generated — that the Doctor path (US2) builds on.

**Independent Test**: Submit an Operations onboarding form with valid details; confirm an Account and an Operations Role Assignment exist, active, with a returned login ID and temp password satisfying the password policy.

**Acceptance Scenarios**:

1. **Given** a ClinicAdmin fills the Operations onboarding form (name, contact info) and submits it, **When** processed, **Then** an Account + Operations Role Assignment are created together, active immediately, and a generated login ID + temporary password are returned to the ClinicAdmin's screen.
2. **Given** the role selector, **When** a ClinicAdmin attempts to submit a role other than Doctor or Operations (e.g. ClinicAdmin or Super Admin), **Then** the request is rejected server-side, even if the UI were somehow bypassed.
3. **Given** a generated temporary password, **When** checked, **Then** it satisfies the password policy (8+ chars, upper, lower, digit, special character).

---

### User Story 2 - ClinicAdmin Onboards a Doctor (Priority: P2)

A ClinicAdmin fills the Doctor onboarding form (adding specialization, license number, experience to the base fields) and submits it, creating the Doctor's staff identity and their Doctor Profile together.

**Why this priority**: Builds directly on US1's mechanism, adding the Doctor-specific fields and the Doctor Profile side-effect — a meaningful but incremental extension, not a separate onboarding path.

**Independent Test**: Submit a Doctor onboarding form with valid details; confirm an Account, a Doctor Role Assignment, and a Doctor Profile (with `licenseVerified=false`) all exist together.

**Acceptance Scenarios**:

1. **Given** a ClinicAdmin fills the Doctor onboarding form (name, contact, specialization, license number, experience) and submits it, **When** processed, **Then** an Account + Doctor Role Assignment + Doctor Profile are all created in the same transaction, and the Doctor Profile's `licenseVerified` starts `false` (awaiting verification).

---

### Edge Cases

- What happens if a mobile number is collected for the new hire and it doesn't match the Indian numbering plan? → Rejected with a validation error; the mobile number field remains optional (onboarding can proceed without one).
- What happens if part of the transaction fails (e.g. Doctor Profile creation fails after the Account was about to be created)? → The entire transaction rolls back — no orphaned Account, Role Assignment, or Doctor Profile is left behind.
- What happens when a non-ClinicAdmin (Doctor, Operations, or an unauthenticated request) attempts to call this action? → Rejected as unauthorized; only an authenticated ClinicAdmin for the target clinic may onboard staff there.
- Given the codebase/API surface, when inspected, is there any invitation/accept-link flow? → No — no `Invitation` entity, pending-invite table, or accept-link endpoint exists anywhere.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST allow an authenticated ClinicAdmin to submit a single form onboarding a new staff member for their own clinic, specifying name, contact info, and role (`Doctor` or `Operations` only).
- **FR-002**: System MUST restrict this action to an authenticated ClinicAdmin — any other role, or an unauthenticated request, MUST be rejected as unauthorized.
- **FR-003**: System MUST reject, server-side, any attempt to submit a role other than `Doctor` or `Operations` — a ClinicAdmin can never create a peer ClinicAdmin or a Super Admin through this flow, regardless of what the client sends.
- **FR-004**: System MUST create the new hire's Account and Role Assignment together, in one step, active immediately — there is no pending/awaiting-acceptance state.
- **FR-005**: System MUST auto-generate a login ID and a one-time temporary password for the new hire, and return both to the ClinicAdmin's screen for hand-off outside the system — the system itself MUST NOT attempt to email or SMS these credentials to the new hire (notification delivery is stubbed; out of scope here).
- **FR-006**: The generated temporary password MUST satisfy the password policy (minimum 8 characters, at least one lowercase letter, one uppercase letter, one digit, one special character).
- **FR-007**: When the role is `Doctor`, the form MUST additionally collect specialization, license number, and experience, and the system MUST create a Doctor Profile in the same transaction as the Account and Role Assignment, with `licenseVerified` starting `false`.
- **FR-008**: If a mobile number is collected for the new hire, it MUST match the Indian numbering plan (10 digits starting 6–9, optional `+91`/`0` prefix) or be rejected with a validation error; the mobile number MUST remain optional.
- **FR-009**: System MUST roll back the entire onboarding transaction — leaving no orphaned Account, Role Assignment, or Doctor Profile — if any part of it fails.
- **FR-010**: System MUST NOT provide any invitation, pending-invite, or accept-link mechanism anywhere in this flow — direct-hire is the sole onboarding mechanism.

### Key Entities

- **Account** (from 001): a new row created here for the hire, following the same shape (globally unique email, globally unique generated staff code, hashed password) as the founding ClinicAdmin's Account from 001.
- **Role Assignment** (from 001): a new row linking the new Account to the ClinicAdmin's own clinic, with role `Doctor` or `Operations`.
- **Doctor Profile** (new in this feature; the full verification-queue *workflow* around it belongs to 005-doctor-profile-auto-creation-license-queue): created only for the Doctor path — specialization, license number, experience, and a `licenseVerified` flag defaulting `false`. This feature is responsible for creating the row correctly; reviewing/verifying it is out of scope here.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A ClinicAdmin can onboard a new Operations or Doctor staff member in a single form submission, with credentials returned immediately — no second step, no waiting on the new hire to accept anything.
- **SC-002**: 100% of onboarding attempts specifying a role other than Doctor or Operations are rejected server-side, regardless of client-side bypass.
- **SC-003**: 100% of generated temporary passwords satisfy the password policy.
- **SC-004**: 100% of Doctor onboardings result in exactly one Doctor Profile row with `licenseVerified=false`; 100% of Operations onboardings result in zero Doctor Profile rows.
- **SC-005**: 100% of failed onboarding attempts (any cause) leave no partial data — zero orphaned Account, Role Assignment, or Doctor Profile rows are ever observed.

## Assumptions

- Authenticating the ClinicAdmin caller (FR-002) requires a staff login mechanism. No such mechanism exists yet as a dedicated, built feature — 003-staff-login-password-or-code (the formal login feature, adding a staff-code alternate identifier) is scoped to be built after this one, since it needs credentials generated by this feature to test against. This feature is responsible for a minimal, functioning ClinicAdmin email+password authentication path as a foundation; 003 extends it later with the staff-code identifier. This mirrors the resolution already applied to a similar ordering tension elsewhere in this backlog (buffer-slot capacity sizing's cold-start default, later upgraded).
- Doctor Profile (FR-007) is created by this feature, but the review/verification *workflow* around it (an admin screen to list/verify pending doctors, analogous to 003-super-admin-clinic-verification's pattern) belongs to 005-doctor-profile-auto-creation-license-queue, not here.
- The ClinicAdmin can only onboard staff for their own clinic — cross-clinic onboarding is not described anywhere in the source material and is assumed out of scope.
