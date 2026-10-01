# Phase 3 Decision Record: 3C and 3D

**Date:** 2026-10-01 (IST).

**Scope:** packages 3C and 3D of [`NEXT_PHASES_ACTION_PLAN.md`](NEXT_PHASES_ACTION_PLAN.md). 3A (operational time zone: `Asia/Kolkata`, #29) and 3B (clinic-owned fees: spec 068, #30 and #32) are already decided and shipped.

**Status:** **approved by the product owner** in this session. The answers are quoted below. Implementation follows as one spec per package (3C, then 3D). Nothing here is implemented yet.

---

## 3C: Account deactivation, sessions and login errors

Audit references: B-05 (SEC-07), B-06 (SEC-05, PB-006), B-07 (SEC-04).

### Current behaviour (traced in source)

| Area | Today | Evidence |
|---|---|---|
| Staff deactivation | Per clinic. The `RoleAssignment` at that clinic is deactivated, and every clinic-scoped action re-checks for an active role at that clinic, so it is refused at once. | `StaffDeactivationService`; `existsBy…AndActiveTrue` checks in each service |
| Session after deactivation | The staff JWT stays valid until it expires, **12 hours** after issue. The shell still loads; it just has no usable clinic. | `StaffJwtService.TOKEN_TTL`; `StaffJwtAuthenticationFilter` checks only the signature and expiry |
| Login with no active role | **Allowed**: "an account with no roles at all signs in exactly as before". Only "every role is Doctor/Operations at a rejected clinic" is refused. | `StaffAuthService.requireAnActiveClinic` |
| Account-level `active` flags | `account.active` and `patient_account.active` exist, but nothing sets them to false and nothing reads them. | `Account.isActive()` has no callers |
| Login error messages | Staff and patients both get **`ACCOUNT_NOT_FOUND`** separately from **`INCORRECT_PASSWORD`**, so anyone can test whether an email is registered (enumeration). | `StaffExceptionHandler`, `PatientExceptionHandler`; the patient `LoginForm` shows the two messages separately |
| Failed-attempt limits | Per IP only: 30 per 60 s, in memory. There is **no per-account limit**. | `RateLimitingFilter` (047); 071 made its 429 readable |

### Decisions

**D-3C-1: cut off deactivated staff.** The owner answered: "yes".
- **Last active role deactivated:** once a staff member has **no active role at any clinic**, their existing sessions stop working on the next request (401), and they can no longer log in.
- **Still active elsewhere:** someone deactivated at one clinic but active at another keeps working at the other clinic. This is today's per-clinic behaviour, unchanged.
- **Refusal message:** after a correct password, the refusal is a clear "this account has no active clinic access". The person has proved they know the password, so this reveals nothing to an attacker.

**D-3C-2: industry-standard login errors (OWASP Authentication Cheat Sheet, NIST SP 800-63B §5.2.2).** The owner had no preference and chose the industry standard: "Industry standard (Recommended)".
- **One generic message:** "Incorrect email or password" for both an unknown account and a wrong password, on staff and patient login. The separate `ACCOUNT_NOT_FOUND` response and the patient form's "email not registered" message are retired.
- **Per-account limit:** **5** consecutive failed passwords for one identifier lock that identifier for **15 minutes**. A successful login resets the count.
  - The limit is keyed by the normalised identifier string, whether or not an account exists, so the lockout itself cannot reveal which emails are registered.
  - During the lockout, every attempt gets the same "too many attempts" response, even with the right password.
- **Existing limit stays:** the per-IP limit (047) remains, in front of the per-account limit.
- **Recovery:** the lockout expires on its own. A ClinicAdmin who is locked out can still have Super Admin reset their password, using the existing tool.

### Notes for the 3C spec

- The per-account counter must hold across restarts if the pilot runs as one instance with occasional restarts. **Proposal:** a small table, which needs a forward-only migration. The spec must settle this, together with the per-IP limiter's unbounded in-memory map (B-06), which needs an eviction bound.
- **Unchanged:** the signup endpoint still returns `EMAIL_ALREADY_IN_USE`. That is unavoidable for self-signup and is covered by the per-IP limit. The spec should say so explicitly.
- **Patients:** the generic message and lockout apply. `patient_account.active` stays unused, because there is no patient deactivation feature (out of scope).

---

## 3D: Loss of clinic or doctor verification

Audit reference: PB-009, plus waitlist eligibility.

### Current behaviour (traced in source)

| Area | Today | Evidence |
|---|---|---|
| Discovery | Unverified clinics and licence-unverified doctors are hidden. | `DiscoveryResultRepository.search` eligibility conjunction |
| Patient booking | Only a **rejected** clinic is refused. An unverified clinic or a licence-revoked doctor is **still bookable** through the patient API. | `PatientBookingService.requireClinicAcceptingAppointments` checks only `isRejected()`; booking has no `licenseVerified` check |
| De-verification cascade | Unverifying a clinic or revoking a licence cancels existing bookings (033 cascade events), but new bookings can then be made straight away. | `ClinicVerificationService` publishes `ClinicDeVerifiedEvent` |
| Super Admin delete | A clinic or doctor can be permanently deleted **only if Rejected and only with no real activity attached** (guarded). | `ClinicVerificationService.deleteGuarded`; `DoctorVerificationController` |

### Decisions

**D-3D-1: no new bookings while de-verified.** The owner answered: "no" to "can still be booked", and "yes" to "drops out of search".
- **Clinic:** while a clinic is unverified, it takes **no new bookings**: patient fixed-time, patient queue, waitlist offers and claims, staff booking and front-desk walk-in. It stays hidden from discovery.
- **Doctor:** while a doctor's licence is unverified, **that doctor** takes no new bookings at any clinic, on the same paths. The doctor stays hidden from discovery.
- **Existing bookings:** they follow the existing 033 cascade, unchanged.

**D-3D-2: re-verifying restores.** The owner answered: "Re-verify restores (Recommended)".
- **Re-verification:** when Super Admin verifies the clinic or licence again, booking and discovery come back automatically. Nothing else needs restoring, because bookings cancelled by the cascade stay cancelled.
- **Permanent deletion:** remains Super Admin's existing tool, allowed only for Rejected records with no real activity attached. Consultation notes and prescriptions are write-once (constitution) and are **never** deleted.

### Notes for the 3D spec

- **Error code:** use one refusal code, the existing `CLINIC_NOT_ACCEPTING_APPOINTMENTS`, or a sibling for the doctor case. Reuse 062's rejected-clinic gate point in each booking path rather than adding a new check site.
- **Waitlist:** an offer must not be issued, or claimed, for a de-verified clinic or doctor.
- **Staff screens:** front-desk and staff booking forms should explain the refusal, following the 062 pattern.

---

## Sequence

1. **Spec 075 (3C):** generic login errors, the per-account lockout, and deactivated staff cut off. Includes a migration if the counter is persisted.
2. **Spec 076 (3D):** no new bookings while de-verified; re-verify restores.

Each package is test-first, gets its own PR, and needs a browser check.
