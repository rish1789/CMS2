# Implementation Plan: Login Hardening and Deactivated-Staff Cutoff (075)

**Spec**: [spec.md](spec.md)

## Backend

- **Migration `V44__create_login_attempt.sql`:** the table `login_attempt (realm, identifier_hash, failed_count, window_started_at, locked_until)`, with the primary key `(realm, identifier_hash)` and `CHECK failed_count >= 0`.
- **`common.login.LoginAttemptGuard`:** a Spring bean using `JdbcTemplate` and a `Clock`, with the test constructor following the `RejectedRecordPurgeService` precedent.
  - `requireNotLocked(realm, identifier)` throws `LoginTemporarilyLockedException(retryAfterSeconds)`.
  - `recordFailure` and `recordSuccess` each run `@Transactional(REQUIRES_NEW)`. The upsert is `INSERT … ON CONFLICT DO NOTHING`, then `SELECT … FOR UPDATE`, then the count, window and lock arithmetic in Java, then `UPDATE`. That keeps it atomic under concurrent failures.
  - The identifier is hashed with SHA-256 after `trim().toLowerCase(Locale.ROOT)`.
- **`StaffAuthService.login`, in order:**
  1. the lock check;
  2. the Super Admin path, whose wrong password counts as a failure;
  3. the account lookup, with a dummy-hash comparison when the account is unknown;
  4. a failure leads to `recordFailure` and `InvalidCredentialsException`;
  5. a success leads to `recordSuccess`, then the 062 check, then the new no-active-role check (`NoActiveClinicAccessException`).
- **`PatientAccountService.authenticate`:** the same flow, without the role step.
- **Handlers** (`StaffExceptionHandler`, `PatientExceptionHandler`): `INVALID_CREDENTIALS` (401) and `NO_ACTIVE_CLINIC_ACCESS` (403). A shared `@RestControllerAdvice` in common maps `TOO_MANY_LOGIN_ATTEMPTS` (429 with `Retry-After`).
- **The old split:** `AccountNotFoundException` and `IncorrectPasswordException` (both realms) are retired from login; their handlers are removed where unused.
- **Session cutoff:**
  - a new `identity.account.config.StaffSessionPolicy` interface;
  - `ActiveRoleStaffSessionPolicy` as the bean, using a new `RoleAssignmentRepository.existsByAccount_IdAndActiveTrue`;
  - `StaffJwtAuthenticationFilter` authenticates only when the policy allows;
  - `SecurityConfig` and `BookingSecurityConfig` pass the policy to the filter;
  - `@WebMvcTest` slices import `support.AllowAllStaffSessionsTestConfig`, which mirrors 069's `PatientVisitOutcomesTestConfig`.

## Frontend

- **Staff and patient login forms:** show the server message for `INVALID_CREDENTIALS` and `NO_ACTIVE_CLINIC_ACCESS`, and a lockout message with the wait (reusing `formatRetryAfter`, as 071 did).
- **Patient `LoginForm`:** the two separate error branches go. Its tests are updated to the decided behaviour.

## Tests (first, red)

- **Unit `LoginAttemptGuardTest`**, with a fixed clock: 5 failures lead to a lock; the window resets the count; the lock expires; success clears the record; normalisation; separate realms.
- **Integration `LoginHardeningTest`** (real Postgres):
  - generic and identical bodies for staff and patient;
  - the lockout for a real and for an unregistered identifier;
  - the right password refused while locked;
  - concurrent failures counted;
  - a deactivated staff session refused, and its login refused with 403;
  - a member deactivated at one clinic only still works.
- **Existing tests** that asserted `ACCOUNT_NOT_FOUND` or `INCORRECT_PASSWORD` change to `INVALID_CREDENTIALS`, as the decision requires. Each change is listed in tasks.md.
- **Vitest:** the staff and patient login forms with the new codes.

## Constitution check

- **Test-first:** yes, including the migration (exercised through the integration test).
- **Forward-only migration:** V44 is new.
- **Tenant scoping:** not affected; login is pre-tenant.
- **Scope:** no speculative abstraction; one policy interface exists only because the slices need it.
