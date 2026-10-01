# Feature Specification: Login Hardening and Deactivated-Staff Cutoff

**Feature Branch**: `claude/075-login-hardening`

**Created**: 2026-10-01

**Status**: Approved decisions; implementation

**Input**: Phase 3C of `docs/NEXT_PHASES_ACTION_PLAN.md`, decisions D-3C-1 and D-3C-2 in `docs/PHASE_3_DECISION_RECORD.md`. Audit references: B-05 (SEC-07), B-06 (part), B-07 (SEC-04).

## Context

Today:
- Staff and patient login answer `ACCOUNT_NOT_FOUND` for an unknown email or staff code, and `INCORRECT_PASSWORD` for a wrong password. Anyone can therefore test which emails are registered. That split was a deliberate earlier trade-off; the owner has now chosen the industry standard instead.
- The only failed-attempt limit is per IP.
- A staff member deactivated at their last clinic keeps a working 12-hour token, and can still log in.

## Decisions applied (from the decision record)

- **D-3C-1:** a staff account with **no active role at any clinic** gets 401 on its existing sessions, from the next request, and cannot log in. It gets 403 `NO_ACTIVE_CLINIC_ACCESS`, which is shown only after a correct password.
- **D-3C-2:**
  - **Generic message:** one `INVALID_CREDENTIALS` response for an unknown identifier and for a wrong password, on staff and patient login.
  - **Lockout:** **5** consecutive failures for one identifier within a 15-minute window lock that identifier for **15 minutes**. While locked, every attempt gets 429 `TOO_MANY_LOGIN_ATTEMPTS` with `Retry-After`, **whatever the password and whether or not the account exists**. A successful login clears the count.
  - The per-IP limit (047 and 071) is unchanged.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Login no longer reveals which accounts exist (Priority: P1)

**Acceptance Scenarios** (staff and patient):

1. **Given** an unknown email, **When** someone logs in, **Then** the response is 401 `INVALID_CREDENTIALS`, "Incorrect email or password."
2. **Given** a real email and a wrong password, **Then** the response is identical: same status, code and message.
3. **Given** the right password, **Then** login succeeds as before.

### User Story 2 - Repeated guessing is locked out (Priority: P1)

1. **Given** 5 wrong passwords for one identifier within 15 minutes, **When** a 6th attempt is made, even with the right password, **Then** it gets 429 `TOO_MANY_LOGIN_ATTEMPTS` with `Retry-After`.
2. **Given** an identifier with no account, **When** it is tried 5 times, **Then** the 6th try gets the same 429. The lockout reveals nothing.
3. **Given** a lock, **When** 15 minutes pass, **Then** login works again.
4. **Given** 4 failures, **When** the right password is used, **Then** login succeeds and the count resets.
5. **Given** failures spread more than 15 minutes apart, **Then** they never add up to a lock.
6. **Given** a staff identifier and a patient with the same email, **Then** the two realms are counted separately.
7. **Given** "Asha@Example.com " and "asha@example.com", **Then** they count as the same identifier.

### User Story 3 - Deactivated staff are cut off (Priority: P1)

1. **Given** a staff member is deactivated at their only clinic, **When** their existing session makes its next request, **Then** it gets 401.
2. **Given** that member logs in with the right password, **Then** they get 403 `NO_ACTIVE_CLINIC_ACCESS`, "This account has no active clinic access."
3. **Given** a staff member deactivated at one clinic but active at another, **Then** their session and login still work, and only the first clinic refuses them (unchanged).

### Edge Cases

- **Super Admin:** the Super Admin username goes through staff login. It gets the generic error and is subject to the same lockout. That is the accepted trade-off: anyone can lock it for 15 minutes, and it recovers on its own.
- **062:** "every role is at a rejected clinic" still gives `STAFF_CLINIC_NOT_ACTIVE` after a correct password (unchanged).
- **Signup:** `EMAIL_ALREADY_IN_USE` is unchanged; self-signup needs it, and the per-IP limit covers it.

## Requirements *(mandatory)*

- **FR-001:** Staff and patient login MUST answer unknown-identifier and wrong-password attempts with the same 401 `INVALID_CREDENTIALS` body.
- **FR-002:** An unknown identifier MUST still run a password-hash comparison, so that response timing does not reveal whether the account exists.
- **FR-003:** Failed attempts MUST be counted per realm (STAFF, PATIENT) and per normalised identifier (trimmed, lower-cased). They are stored only as a SHA-256 hash, so unregistered emails are never kept in plain text.
- **FR-004:** The 5th failure within a 15-minute window from the first counted failure MUST set a lock for 15 minutes. While the lock lasts, attempts MUST get 429 `TOO_MANY_LOGIN_ATTEMPTS` with `Retry-After` (in seconds), and the password MUST NOT be checked.
- **FR-005:** A failure MUST be recorded even though the login request itself fails (its own transaction). Concurrent failures for one identifier MUST NOT lose counts.
- **FR-006:** A successful password check MUST clear that identifier's record.
- **FR-007:** A staff token MUST authenticate only while its account holds at least one active role. Staff login MUST refuse an account without one with 403 `NO_ACTIVE_CLINIC_ACCESS`, after the password check.
- **FR-008:** Both login forms MUST show the generic message, the lockout message (with the wait time when known), and the no-access message.

## Out of scope

- **Per-IP limiter (B-06):** its eviction and a proxy-aware client key depend on the deployment topology (Phase 6). This is recorded, not changed here.
- **Patient account deactivation:** there is no such feature.
- **Refresh tokens and logout semantics:** Phase 5A.
- **Frontend handling of an expired staff session (PB-010):** Phase 5A. A cut-off session shows the existing error text until then.

## Success Criteria *(mandatory)*

- **SC-001:** 0 login responses differ between an unknown identifier and a wrong password.
- **SC-002:** A 6th guess within the window is refused 100% of the time, for real and for unregistered identifiers alike.
- **SC-003:** A fully deactivated staff session is refused on its first request after deactivation.
