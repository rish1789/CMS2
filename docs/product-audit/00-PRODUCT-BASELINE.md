# 00 — Product Baseline (master document)

**Audit date:** 2026-09-28
**Scope:** the working tree on `main` at `C:\Users\risha\OneDrive\Documents\CMS2`. That includes about 835 uncommitted changes on top of commit `5474512`.
**Method:** static reading of backend, frontend, migrations, configuration and docs, plus the runtime checks listed in §13.
**No application code, schema, API or configuration was changed.** The only files created are the ones in `docs/product-audit/`.

## Evidence labels used in every document

| Label | Meaning |
|---|---|
| **[RUNTIME]** | Observed by running something in this session (a build, a test run, or an HTTP request to the local backend at `:8080` or frontend at `:5173`) |
| **[CODE]** | Read directly in source, a migration or config, and traced along its call path. Not executed. |
| **[INFERRED]** | A conclusion drawn from code or docs that has not been directly proven |
| **[UNKNOWN]** | Could not be determined; the reason is stated |

Companion documents:
- [01 Architecture](01-ARCHITECTURE.md)
- [02 Feature inventory](02-FEATURE-INVENTORY.md)
- [03 Business workflows](03-BUSINESS-WORKFLOWS.md)
- [04 Data model](04-DATA-MODEL.md)
- [05 API](05-API-INTEGRATION.md)
- [06 UI/UX](06-UI-UX-AUDIT.md)
- [07 Defects](07-BUG-AND-DEFECT-REGISTER.md)
- [08 Security](08-SECURITY-AUDIT.md)
- [09 Testing](09-TESTING-AND-QUALITY.md)
- [10 Backlog](10-PRODUCT-IMPROVEMENT-BACKLOG.md)

---

## 1. Product purpose

CMS2 is a multi-tenant clinic management system for the Indian outpatient market. Evidence: `IndianMobileNumberValidator`, INR fees, and the seed clinics in Noida, Indore and Ahmedabad.
- **Patients** hold one global account. They find clinics and doctors, book fixed-time appointments or queue tokens, join waitlists, track their queue position and live doctor status, and read their own clinical records.
- **Clinic staff** (ClinicAdmin, Doctor, Operations) run a clinic's day: schedules, sessions, bookings, walk-ins, check-in ("Appeared"), completion, cancellations, waitlist, and clinical documentation (notes, prescriptions, external record references).
- **Super Admin** verifies or rejects clinics and doctor licences, triggers session generation, and tunes abuse-protection settings.

Sources: `README.md`, `PRODUCT.md`, `.specify/memory/constitution.md`, `backlog/*.md`.

## 2. Current product scope

**In scope and present in code [CODE]:**
- Clinic self-registration and Super Admin verification or rejection
- Staff onboarding, deactivation and password reset
- Doctor licence verification
- Recurring schedules with break windows, generated 15 days ahead
- Fixed-time slots and queue tokens
- Staff, patient and walk-in booking
- Fee resolution and fee locking at booking time
- Automatic no-show detection
- Session delay and live status
- Queue position
- Cancellation: individual, batch, whole session and cutoff-based
- Waitlist with a 30-minute claim window
- Consultation notes, prescriptions and external record references (all write-once)
- Patient anonymization and a monthly retention purge
- Public discovery search
- A notification event pipeline whose delivery is a logging stub
- A real-time staff inbox over SSE
- Booking-abuse protection: rate limits, a per-patient active-booking cap, suspicious-activity flags

**Out of scope by explicit decision** (constitution §"Out of scope"; backlog 016/017/025): payments, file uploads, a single global medical record, live notification delivery, and atomic reschedule.

**Removed after being built:**
- Buffer / reserved-capacity slots (backlog 022, removed by `specs/058-remove-reserved-capacity`, migration `V35__drop_slot_is_buffer.sql`)
- The slot-insertion walk-in model (025), replaced by the untimed "walk-in line" in `specs/063-front-desk-walk-in`

## 3. Architecture summary

- **Backend:** one Spring Boot 3.3.5 (Java 21) monolith, organised package-per-feature under `backend/src/main/java/com/cms/<module>` in 11 top-level modules.
  - 7 path-scoped `SecurityFilterChain` beans serve 3 independent JWT realms (staff, patient, super admin).
  - PostgreSQL schema managed by 40 Flyway migrations.
  - Cross-module effects run through Spring application events (`@TransactionalEventListener(AFTER_COMMIT)`).
- **Frontend:** a React 18 + TypeScript + Vite single-page app with three role shells (`PatientShell`, `StaffShell`/`ClinicShell`, `AdminShell`) and 36 `features/*` directories. No state library; component state plus `sessionStorage` tokens.
- **Live updates:** SSE only for the staff inbox. Queue position, live status and the walk-in panels poll every 20 s.

Details: [01-ARCHITECTURE.md](01-ARCHITECTURE.md).

## 4. Technology stack [CODE]

| Layer | Technology (version from build files) |
|---|---|
| Backend | Java 21, Spring Boot 3.3.5 (web, data-jpa, validation, security, actuator), Flyway, PostgreSQL driver, jjwt 0.12.6, springdoc-openapi 2.6.0 |
| Backend build/quality | Gradle 8.10, Spotless 6.25, JaCoCo (reporting only), OWASP dependency-check 13.0.0 (runs in CI only when `NVD_API_KEY` is set) |
| Backend tests | JUnit 5, Mockito, AssertJ, spring-security-test, Testcontainers 1.21.3 (Postgres) |
| Frontend | React 18.3, react-router-dom 6.30, Tailwind CSS 4.3 (config inside `src/index.css` `@theme`), Vite 8.2, TypeScript ~6.0 |
| Frontend quality | Vitest 4.1 + Testing Library + jsdom; oxlint 1.79 |
| Database | PostgreSQL 16 (local install per README; a `docker-compose.yml` for Postgres also exists) |
| CI | GitHub Actions `.github/workflows/ci.yml` (Spotless, tests, dependency scans, lint, `tsc`, Vitest) |

## 5. Major modules

**Backend** (`com.cms.*`):
- `identity` (account, admin, api, clinic, doctor, staff)
- `patient` (account, record)
- `scheduling`
- `booking`
- `waitlist`
- `clinical`
- `inbox`
- `notification`
- `discovery`
- `protection`
- `common`

**Frontend:** 36 feature directories (`frontend/src/features/*`), 3 role shells plus a public header, and 31 shared components in `src/components`.

## 6. User roles [CODE]

| Role | Realm | How the role is determined |
|---|---|---|
| Patient | patient JWT (`/api/v1/patients/**`) | Any `patient_account` |
| ClinicAdmin | staff JWT | Active `role_assignment(role='ClinicAdmin')` at that clinic, checked per request |
| Doctor | staff JWT | Active `role_assignment(role='Doctor')` plus a global `doctor_profile` |
| Operations (front desk) | staff JWT | Active `role_assignment(role='Operations')` |
| Super Admin | super-admin JWT (`/api/v1/admin/**`) | A single credential from environment variables; signs in on the same `/staff/login` screen |

The staff JWT carries no role claim. Every clinic-scoped service re-reads `role_assignment` for each request [CODE]. One account can hold different roles at different clinics.

## 7. Major workflows

Mapped in [03-BUSINESS-WORKFLOWS.md](03-BUSINESS-WORKFLOWS.md). There are 17 workflow areas: patient registration, patient search, new-patient registration, booking (staff fixed-time, patient fixed-time, queue), walk-in, doctor assignment, queue management, availability, live status, buffer (removed), no-show, cancellation, reschedule (absent), check-in, consultation lifecycle, completion, and administration.

## 8. Current feature maturity

Full table in [02-FEATURE-INVENTORY.md](02-FEATURE-INVENTORY.md). Summary:

| Classification | Count | Examples |
|---|---|---|
| IMPLEMENTED | 34 | Clinic registration, staff login, onboarding, schedules, session generation, staff and patient booking, queue tokens, walk-in line, waitlist, clinical documents, discovery, inbox, protection |
| PARTIALLY IMPLEMENTED | 8 | Whole and partial session cancellation (slots reopen, BUG-002/003), notifications (stub delivery, 5 event types, no preference UI), check-in and completion (auto-complete), no-show handling (automatic only), patient account (no profile or password management), verification gating (only "rejected" blocks booking), doctor availability (schedules only, no leave or absence) |
| BROKEN | 0 whole features | Individual defects are listed in 07 instead |
| MISSING | 6 | Reschedule (by decision), payments (by decision; `payment_status` column exists but is never set to anything but `PENDING`), forgot-password or patient password reset, notification-preference UI/API, manual no-show marking, audit trail for booking/clinical actions |
| REMOVED | 1 | Buffer slots |

## 9. Known technical limitations

- **Single-instance assumptions:** in-memory SSE emitter registry (`InboxBroadcastService`), in-memory rate limiter (`RateLimitingFilter`), and 8 `@Scheduled` jobs with no distributed lock [CODE].
- **Server time zone:** all "now" comparisons use the JVM default time zone (`LocalDateTime.now()`, `ZoneId.systemDefault()`). There is no per-clinic time zone [CODE].
- **Full-table scans every minute:** no-show detection and auto-completion load every BOOKED/APPEARED fixed-time slot in the system, then filter in Java [CODE].
- **Eager loading:** all 40 JPA relationships use the default EAGER `@ManyToOne` fetch [CODE].
- **Integration tests have never run here:** 249 Testcontainers classes cannot start without Docker, which is not installed on this machine [RUNTIME].

## 10. Known UX limitations (detail in 06)

- Visit-state vocabulary is inconsistent ("Appeared", "In with the doctor", "Send in", "Completed").
- A "cancelled" session cannot be shown as cancelled because no such state exists.
- There is no manual no-show action.
- Clinical documentation for one visit spans three separate routes.
- Rescheduling is absent.
- Patients have no self-service password reset. The on-screen message tells them to contact their clinic, but clinics have no tool for it.
- There is no global handling for session expiry.

## 11. Known bugs (detail in 07)

**6 confirmed**, including:
- 7 staff endpoints return **500 instead of 401** when unauthenticated [RUNTIME]
- Whole-session and partial-session cancellation **reopen the cancelled slots as bookable** [CODE-traced]
- Patients are offered, and can book, **today's slots whose start time has already passed** [CODE-traced]
- 6 frontend tests time out under full-suite load [RUNTIME]

**10 potential**, including:
- A new-patient phone collision is reported as "slot already booked" or produces an unhandled error
- The queue-token retry loop does not work inside the caller's transaction
- No date guards on staff, queue and walk-in booking APIs
- Time-zone sensitivity

## 12. Security concerns (detail in 08)

**10 findings.** Most significant:
- The fail-open `anyRequest().permitAll()` default in the staff and patient filter chains. It is currently saved only by controllers throwing, which produces a 500.
- Literal JWT secrets and the Super Admin password in the working copy of the **tracked** file `.claude/launch.json`. Not committed yet, but they would be on the next commit.
- Doctor-global fee and appointment-type configuration is writable by a ClinicAdmin at **any** of the doctor's clinics.
- Login responses distinguish "account not found" from "incorrect password", which allows account enumeration [RUNTIME].
- The rate limiter is in-memory, keyed per IP, and never evicts entries.

## 13. Testing status (detail in 09)

| Check | Result |
|---|---|
| Backend `spotlessCheck` | **pass** [RUNTIME] |
| Backend `test` | 620 test entries: **371 unit and contract tests pass**; all **249 integration classes fail at initialisation** with "Could not find a valid Docker environment"; 0 real assertion failures [RUNTIME] |
| Frontend `tsc -b` | **pass** [RUNTIME] |
| Frontend `oxlint` | **pass**, 24 warnings [RUNTIME] |
| Frontend Vitest | full run: **430 pass / 6 fail** (all 5 s timeouts). The 3 affected files pass in isolation (16/16) [RUNTIME]. HANDOFF.md's claim that all 436 pass does not hold under full-suite load on this machine. |
| `npm audit --audit-level=high` | **pass**; 2 moderate advisories (react-router) [RUNTIME] |
| Backend startup, DB connection, Flyway | **pass**: backend started on :8080 against local Postgres, and `/api-docs`, `/actuator/health` and discovery all answer [RUNTIME] |
| End-to-end tests | **none exist** [CODE] |
| CI history | [UNKNOWN] — `gh` CLI is not installed and the GitHub connector is not authorized. Only 2 commits exist on `main`. |

## 14. Documentation status

Strong internal documentation:
- `README.md`, `CLAUDE.md`, `CONTRIBUTING.md`, `SECURITY.md`, `DESIGN.md`, `PRODUCT.md`
- The constitution
- 53 backlog briefs and 64 spec directories
- `HANDOFF.md` (1,347 lines)
- `_diagnostics/` (an earlier static audit)

It has drifted in places [CODE]:
- `README.md` says "this project does not containerize the database", but `docker-compose.yml` exists.
- `PRODUCTION_ROADMAP.md` §1.1/§1.4 say there is no version control and no CI; both now exist.
- `CLAUDE.md` says "six path-scoped filter chains"; `SECURITY.md` and the code have seven.
- HANDOFF.md Part 12 says backlog 053 is "Not Started"; `backlog/progress.md` says Converged.
- Two spec directories share the number 003 (`specs/003-super-admin-clinic-verification`, `specs/003-super-admin-verification`).
- There is no end-user or operator documentation (a clinic staff guide, a deployment guide).

## 15. Technical debt (detail in 07 §TD and 10 §J)

- Business logic and authorization in 26 controllers that inject repositories directly
- 16 copy-pasted private role-check methods (37 role-existence calls)
- 7 `ForbiddenException`, 5 `NotStaffedAtClinicException` and 2 `PasswordPolicyValidator` classes
- 12 `@RestControllerAdvice` classes, 10 of them unscoped
- Frontend: 73 raw `fetch` calls in 29 files alongside the shared `apiClient` (used in 12 files); `API_BASE_URL` defined in 30 files
- Visit state lives on `slot.status`, not on `booking`
- `payment_status` column is never set to anything but `PENDING`
- Few database CHECK constraints on status columns
- A working tree of about 835 uncommitted changes

## 16. Areas requiring further investigation

These could not be verified here:
- Staff and patient UI workflows end to end. No valid staff or patient credentials were available: the HANDOFF test clinic has been purged, and the other clinics' passwords were reset and deliberately not recorded. Staff-side UI findings are therefore [CODE].
- Whether the 249 integration tests pass. This needs Docker or CI.
- CI status on GitHub.
- Behaviour under concurrent load (queue-token issuance, walk-ins).
- Behaviour when deployed with a UTC JVM.
- Accessibility with a real screen reader. Only static jsx-a11y lint was run.

## 17. Overall current-state summary

CMS2 is a working monolith with broad functional coverage for a clinic's outpatient day:
- It is spec-driven: 57 backlog and out-of-band features are marked Converged.
- It has a careful tenancy model: role checks are re-evaluated per request and scoped to a clinic.
- Its test inventory is large (984 `@Test` methods), but the integration third of it has not been executed in the environments available so far.

The weaknesses are concentrated in four areas:
1. **Lifecycle modelling:** sessions have no cancelled state; visit state sits on slots; there are few date or state guards on staff-side booking APIs.
2. **Cross-cutting consistency:** a fail-open security default; duplicated authorization and exception code; two frontend HTTP styles.
3. **Operational readiness for more than one instance:** in-memory SSE, in-memory rate limiting, unlocked schedulers, JVM time zone.
4. **Verification debt:** integration tests never run, no end-to-end tests, and a large uncommitted working tree.
