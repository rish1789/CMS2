# 08 — Security Audit

Labels: [RUNTIME] = observed while running · [CODE] = read in source · [INFERRED] = drawn from code or docs, not proven · [UNKNOWN] = could not be determined.

**Method:**
- Static review of the code and configuration.
- Non-destructive local probes: unauthenticated GET/POST/DELETE requests with random or nonexistent IDs, a CORS preflight, and read-only authenticated GETs.
- Nothing was exploited or modified.
- **No secret values are reproduced in this document.**

`SECURITY.md` (in the repository) already documents the 7 filter chains and the "unmatched path" risk. This audit confirms that risk is realised for 7 endpoints.

## Remediation status — Phase 1 (`specs/065-phase1-stabilization`, 2026-09-29)

| ID | Status | Summary |
|---|---|---|
| SEC-01 | **CONFIRMED FIXED** | Staff and patient chains are fail-closed. Regression tests: `StaffChainFailClosedContractTest`, `PatientChainFailClosedContractTest`. Runtime 401s verified; public endpoints and CORS preflight unaffected. **Re-verified 2026-09-29 after the V41 work**, on a freshly migrated backend (port 8081, Flyway at V41, Hibernate `validate` passed): the 7 audit endpoints, an unmapped `/clinics/{id}/x` and an unmapped `/patients/nonexistent` → 401 anonymous; `POST /clinics/register` with `{}` → 400 `MISSING_REQUIRED_FIELD`; `/discovery/cities` and `/actuator/health` → 200; CORS preflight → 200. |
| SEC-02 | **CONFIRMED FIXED** | `.claude/launch.json` no longer carries secrets. The backend entry loads the gitignored `.env` (same mechanism as `dev.sh`), then runs `bootRun`. A history scan of both commits found 0 occurrences of the Super Admin password or any JWT secret. The username value only matches inside route names such as `/super-admin-console`; it is not a secret. **No rotation required.** Super Admin login verified after restart; no random-secret warnings in the logs. |
| SEC-03 | **FIXED — 068-per-clinic-fees** (owner decision B, 2026-10-01) | A doctor's default fee and each appointment type's price are now stored per clinic (`clinic_doctor_fee`, `clinic_appointment_type_price`, V42). Only an active ClinicAdmin **of that clinic** can change them, and only for a doctor actively staffed there (`ClinicFeeService`). Every booking resolves its own clinic's price; another clinic's price is never used. V43 copied the old doctor-wide prices to every clinic where the doctor was actively staffed, so nothing changed on day one. The doctor-wide writes are retired: `PUT /default-fee` returns 410, and a type created with a fee returns 400 `FEE_MOVED_TO_CLINIC`. |
| SEC-06 | **CONFIRMED FIXED** (creation) | Creating clinical documentation now requires an active Doctor role at the clinic. Reading earlier records is unchanged (spec 034 historical visibility). |
| SEC-04, SEC-05, SEC-07…SEC-10 | OPEN | Not in Phase 1 scope |

## Findings (10)

| ID | Severity | Area | Finding | Evidence | Status |
|---|---|---|---|---|---|
| SEC-01 | High | Access control | **Fail-open filter chains.** The staff (`/api/v1/clinics/**`) and patient (`/api/v1/patients/**`) chains end with `anyRequest().permitAll()`. Seven staff endpoints are not allowlisted and are reachable without a token. They currently fail with 500 only because the controllers call `SecurityConfig.currentAccountId()`, which throws. | `identity/account/config/SecurityConfig.java:143-276`, `patient/account/config/SecurityConfig.java:85-148`; 07 BUG-001 | [RUNTIME confirmed] |
| SEC-02 | High (on commit) | Secrets | The working copy of the **git-tracked** `.claude/launch.json` passes literal values for the Super Admin username and password and all three JWT signing secrets as `bootRun` args. `HEAD`'s version contains none; the next commit of that file would publish them to `github.com/rish1789/CMS2` (repository visibility [UNKNOWN]). `.env` is correctly ignored. | `git show HEAD:.claude/launch.json` has 0 secret args; the working copy has them; `.gitignore` does not list `.claude/` | [CODE] |
| SEC-03 | Medium | Tenant isolation | Appointment types and default fee are per doctor and global. A ClinicAdmin at clinic A can change the fees used when patients book that doctor at clinic B. | `AppointmentTypeService.java:121-135` ("ClinicAdmin at any clinic that Doctor is actively staffed at"); `appointment_type` / `doctor_default_fee` have no `clinic_id` (V9) | [CODE] |
| SEC-04 | Medium | Session / token | Bearer JWTs with a 12 h TTL are stored in `sessionStorage` (readable by any injected script). There is no refresh, server-side logout or revocation list. Mitigation: roles are re-checked per request, so a deactivated staff member loses clinic access immediately. Patient accounts (`patient_account.active`) and `account.active` are not checked by the JWT filters. | `StaffJwtService.java:33`, `JwtService.java:33`, `SuperAdminJwtService.java:34`; `features/*/token.ts`; `StaffJwtAuthenticationFilter.doFilterInternal` | [CODE] |
| SEC-05 | Medium | Brute force / abuse | Rate limiting covers only 4 public POSTs (staff login, patient login, signup, clinic registration). It is in-memory, per instance and per `remoteAddr`, with no eviction (PB-006), no per-account lockout and no CAPTCHA. Super Admin shares the staff login path. Booking-level protection (attempt limits, active caps, flags) is separate and more complete. | `common/RateLimitingConfig.java`, `RateLimitingFilter.java:47,70`; `protection/*` | [CODE] |
| SEC-06 | Medium | Clinical data access | A doctor keeps create and read access to notes and prescriptions for bookings at a clinic after their role there is deactivated. The treating-doctor check does not test for an active role assignment. | `clinical/service/TreatingDoctorAuthorizationService.java:39-45` | [CODE] POTENTIAL (PB-008) |
| SEC-07 | Medium | Account enumeration | Staff login returns `INCORRECT_PASSWORD` for an existing email (deliberately split from "account not found" in HANDOFF Part 8), so emails can be enumerated despite the rate limit. | [RUNTIME] `POST /staff/login` with a known email and a wrong password → 401 `INCORRECT_PASSWORD` | [RUNTIME] |
| SEC-08 | Low | Super Admin | A single shared credential from environment variables. No MFA, no per-person accounts, no audit identity beyond the username string (`rejected_by`, `updated_by`). Random at startup if unset (good fail-safe). | `application.yml` `admin.super-admin.*`; `SuperAdminSecurityConfig` | [CODE] |
| SEC-09 | Low | Information disclosure | Swagger UI (`/docs`) and the OpenAPI spec (`/api-docs`) are served unauthenticated. This is fine for development, but a production decision is needed. | [RUNTIME] `/docs` 302, `/api-docs` 200 | [RUNTIME] |
| SEC-10 | Low | Dependencies | `npm audit`: 2 moderate advisories via react-router, none high or critical. The OWASP dependency-check in CI is skipped unless `NVD_API_KEY` is configured. | [RUNTIME]; `ci.yml` | [RUNTIME] |

## Checklist results

| Topic | Result |
|---|---|
| Authentication | 3 separate JWT realms with separate secrets; BCrypt passwords; password policy validator; temporary passwords shown once [CODE]. Login verified for staff and Super Admin [RUNTIME]. |
| Authorization / role boundaries | Per-request role lookup; clinic scoping by filtering loaded aggregates. Spot checks were consistent (walk-in, appeared, complete, cancel, clinical) [CODE]. A realm token cannot satisfy another realm: the chains use different filters and secrets [CODE]. Gaps: SEC-01, SEC-03, SEC-06. |
| API access control | 401 for allowlisted endpoints without a token [RUNTIME]. 403 `FORBIDDEN` "Not an active staff member at this clinic" for a valid token at a foreign clinic [RUNTIME]. |
| IDOR | Every inspected by-ID endpoint re-checks `clinicId` ownership, for example `.filter(s -> s.getSession().getClinic().getId().equals(clinicId))` [CODE]. Patient endpoints resolve the patient from the token. No IDOR found in the paths read. Not every endpoint was traced line by line [INFERRED coverage]. |
| Input validation | Mixed: bean validation on 19/35 bodies; service-level validation elsewhere [CODE]. |
| Injection | Spring Data JPQL with parameters. No string-concatenated SQL found in repositories [CODE, grep of `@Query`]. Notification payload JSON is concatenated from UUIDs only (low risk). |
| Sensitive data exposure | Notification PII logging off by default (`app.notification.log-pii=false`); generic 500 body with no stack trace [RUNTIME]. The request-ID MDC is in logs. PII in request logs not assessed [UNKNOWN]. |
| Secrets / environment | No committed defaults for secrets (`application.yml`). `.env` ignored. `.env.example` has placeholders. See SEC-02. |
| CORS | Allowlist from `APP_CORS_ALLOWED_ORIGINS`; a foreign-origin preflight → 403 [RUNTIME]. |
| CSRF | Disabled on all chains. Acceptable for bearer-token APIs with no cookies [CODE]. |
| Error leakage | Handled: coded errors. Unhandled: generic message [RUNTIME]. |
| Actuator | Only `/health` exposed (200). `/actuator/env` → 403 [RUNTIME]. |
| Logging | Request ID filter; request logging config (`common/RequestLoggingConfig`). Not reviewed for PII in payload logging [UNKNOWN]. |
| File uploads | None (out of scope) [CODE]. |
| Rate limiting | See SEC-05. |
| Abuse scenarios | Booking abuse protection exists (caps, attempt logs, flags, Super Admin settings) [CODE; RUNTIME: settings listed]. Mass account creation is limited only by the per-IP signup limit. |
| Audit trail | Change logs only for protection settings and the booking limit override. No "who" for cancellations, check-ins, completions or verifications beyond free-text `rejected_by` [CODE]. Update 2026-09-29 (spec 065, V41): whole and partial **session** cancellations now record `cancelled_by_account_id`; individual booking cancellations, check-ins, completions and verifications still record no actor. |
