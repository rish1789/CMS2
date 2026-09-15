# Feature Specification: Staff Login (Password or Staff Code)

**Feature Branch**: `006-staff-login-dual-identifier`

**Created**: 2026-09-02

**Status**: Draft

**Input**: User description: "Staff Login (Password or Staff Code) — a staff member logs in using either email+password or staff code+password, both resolving to the same Account. Staff code is a memorable alternate identifier generated at onboarding, unique platform-wide. Same password policy applies. Entirely separate from Patient Account login. (Full source: backlog/003-staff-login-password-or-code.md)"

## Clarifications

### Session 2026-09-02

- Q: Email+password staff login already exists (built by 004-staff-onboarding-direct-hire as a necessary prerequisite for that feature). What does this feature actually add? → A: This feature is a narrow extension: adding the staff-code identifier as a second way to resolve the same login, alongside the existing email path. The password policy and the "separate from Patient Account" requirements are already fully satisfied by existing code; this spec still states them as requirements (they're part of this feature's acceptance bar per the backlog), but no new implementation is expected for them.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Staff Logs In With Staff Code Instead of Email (Priority: P1)

A staff member on a shared front-desk device logs in by typing their short staff code (e.g. `DR-4821`) and password, rather than their full email — faster and less error-prone on a shared keyboard.

**Why this priority**: This is the entire net-new capability this feature adds; everything else (email login, password policy) already exists.

**Independent Test**: Log in with a valid staff code + correct password; confirm authentication succeeds identically to logging in with that same Account's email + password (same token, same resulting identity).

**Acceptance Scenarios**:

1. **Given** a staff Account with email `dr.sharma@clinic.com` and staff code `DR-4821`, **When** the user logs in with the staff code + correct password, **Then** they are authenticated identically to logging in with the email + correct password (same Account, same resulting session).
2. **Given** an incorrect password paired with a valid staff code, **When** login is attempted, **Then** it is rejected.
3. **Given** a staff code that doesn't match any Account, **When** used to log in, **Then** it is rejected with the same response shape as an unknown email (no information leak about which part — identifier or password — was wrong).

---

### Edge Cases

- What happens if a submitted identifier could theoretically match either an email format or a staff-code format string that happens to collide? → Not a real concern: email and staff-code formats are structurally distinct (emails contain `@`; staff codes follow a short prefix-hyphen-digits pattern) and both identifier fields are already globally unique per-kind (001/004), so no ambiguity exists in resolving which field to check.
- What happens when the identifier field is blank or missing entirely? → Rejected as invalid input, same as any other missing required field.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST allow a staff member to authenticate using their Account's staff code together with their password, in addition to the already-supported email + password path.
- **FR-002**: A successful staff-code + password login MUST resolve to the exact same Account, and produce an equivalent authenticated session, as a successful email + password login for that same Account.
- **FR-003**: An incorrect password paired with a valid staff code MUST be rejected.
- **FR-004**: A staff code that matches no Account MUST be rejected with the identical response shape as an unknown email (no information leak distinguishing "unknown identifier" from "wrong password" from "unknown vs. known identifier type").
- **FR-005**: The password policy (minimum 8 characters, lowercase, uppercase, digit, special character) MUST apply to every staff password, regardless of which identifier is used to log in. *(Already satisfied by existing code — 004's `PasswordPolicyValidator` — restated here because it's part of this feature's acceptance bar per the source material.)*
- **FR-006**: Staff login MUST remain entirely separate from Patient Account login (002) — no shared table, session, or authentication code path. *(Already satisfied by existing code — distinct `STAFF`/`PATIENT` JWT audiences and separate modules — restated here for the same reason as FR-005.)*

### Key Entities

- **Account** (from 001/004): no new fields — this feature reads the existing `staff_code` field (already present and populated by 004) as an alternate login lookup key.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of valid staff-code + correct-password login attempts succeed, producing an equivalent session to the same Account's email-based login.
- **SC-002**: 100% of staff-code logins with an incorrect password, or with an unrecognized staff code, are rejected with the same response shape already used for the equivalent email-based failure cases.

## Assumptions

- No UI/UX decision is needed about *how* a user indicates which identifier type they're providing — the backend accepts a single "identifier" field and resolves it by trying both lookups (email format is structurally distinguishable from staff-code format, but even without that, both are globally unique so trying both lookups is safe and unambiguous).
- This feature does not touch password change, reset, or any account-lifecycle concern — strictly the login/authentication path.
