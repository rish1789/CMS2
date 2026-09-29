# Research: 065 Phase 1 Stabilization

## R1 — Fail-open security chains (SEC-01 / BUG-001)

**Finding [code]:** Two security chains end with `anyRequest().permitAll()`:
- `identity/account/config/SecurityConfig.filterChain` (`/api/v1/clinics/**`, @Order 1)
- `patient/account/config/SecurityConfig.patientFilterChain` (`/api/v1/patients/**`, @Order 2)

Protection depends on each new path being added to an allowlist. The class-level Javadoc records 20+ past additions, each warning about this exact fall-through. Seven paths were missed [runtime-verified 500s].

**Public endpoints under those prefixes (exhaustive, from the controller mapping inventory):**
- `POST /api/v1/clinics/register` — `ClinicRegistrationController`
- `POST /api/v1/patients/signup`, `POST /api/v1/patients/login` — `PatientAccountController`

Staff login is `/api/v1/staff/**` (its own permitAll chain). Discovery is `/api/v1/discovery/**` (its own permitAll chain). Neither is affected.

**Decision:** invert both chains:
- explicit `permitAll()` for the three public endpoints above
- then `anyRequest().authenticated()`
- the per-endpoint `authenticated()` matchers become redundant and are removed, so there is one rule instead of about 80 matchers

The entry points (`StaffAuthenticationEntryPoint`, `PatientAuthenticationEntryPoint`) already render the 401 `UNAUTHORIZED` body. Per-clinic 403 logic lives in services and is untouched.

**Rationale:** fixes the class of defect, not the seven instances (the brief explicitly asks for this).
- CORS preflight is handled by `.cors(withDefaults())` before authorization, so it is unaffected.
- Error dispatches go to `/error`, which matches no chain, so behaviour is unchanged.
- The SSE `inbox/stream` path was already `authenticated()`, so its behaviour is unchanged.

**Alternatives rejected:**
- Adding the 7 paths to the allowlist: keeps the fail-open default, so the next endpoint repeats the bug.
- Method security (`@PreAuthorize`): a larger change, and a new mechanism in this codebase.

## R2 — Secrets in `.claude/launch.json` (SEC-02)

**Finding:**
- `git show HEAD:.claude/launch.json` contains no secret arguments. The values exist **only in the uncommitted working copy**, so no rotation is required on history grounds.
- The gitignored `.env` already contains identical values for all 5 keys. This was compared inside a script that printed only "set/match", never the values.

**Decision:**
- The backend launch configuration loads `.env` into the process environment, then runs Gradle `bootRun` with no secret arguments. It uses a PowerShell one-liner, since this is a Windows machine and the configured runtime is `gradle.bat`.
- `application.yml` already maps `SUPER_ADMIN_*`, `*_JWT_SECRET`, and so on from environment variables.
- **Usability is preserved:** the same values are used, from the gitignored file. This is the same mechanism as `dev.sh` (`set -a; source .env`).

**Alternatives rejected:**
- Removing the arguments without loading `.env`: every restart would invalidate sessions and randomize the Super Admin login.
- Hard-coded placeholders: forbidden by the brief.

## R3 — Representing session cancellation (BUG-002/003/004)

**Findings:**
- `session` has no state column (V8).
- Specs 029 and 030 deliberately chose "slots return to `OPEN`, no session-level concept". The Phase 1 brief reverses this (see the spec's "Documented product decisions").
- Partial cancellation accepts a **range** (`cutoffTime`, optional `toTime`), and repeated calls are allowed. Two columns on `session` cannot hold several ranges.
- Queue sessions and empty fixed-time sessions have no slots to mark, so a slot-level marker alone cannot block them.

**Decision:** a new append-only table `session_cancellation`, with these columns:
- `session_id` FK `ON DELETE CASCADE`, so the existing session deletion and purge paths keep working
- `from_time` (nullable)
- `to_time` (nullable)
- `cancelled_at`

Its constraints:
- NULL `from_time` means the whole session.
- A partial unique index allows at most one whole-session record per session. This closes the double-whole-cancel race at the data layer.
- A CHECK constraint: `to_time` requires `from_time` and must be after it.

Other rules:
- **Slot states are unchanged.** Cancelled bookings' slots still go to `OPEN`, so existing semantics, tests and the day sheet are untouched. Bookability is derived from the records.
- **No backfill.** Past cancellations left no marker.

**Rationale:**
- It is the minimal structure that expresses whole, range and repeated cancellations for all session modes.
- No existing enum or state meaning changes.
- It is additive and forward-only.

**Alternatives rejected:**
- A new `SlotStatus.CANCELLED`: changes the meaning of the slot state machine, touches the UI status maps, and still can't block queue or empty sessions.
- `session.cancelled_at` + `cancelled_from` columns: cannot represent `toTime` ranges or multiple ranges.

## R4 — One bookability rule for five booking paths (BUG-002/003/005, FR-006/007/012/013)

**Decision:** a new `scheduling/service/SessionAvailabilityService`, following the module that owns sessions. It has:
- `Verdict evaluate(Session, Slot|null)` returns `ACCEPTING`, `PAST_DATE`, `ELAPSED`, or `CANCELLED`
- `isWholeCancelled(Session)`
- `recordWholeCancellation(Session)`
- `recordRangeCancellation(Session, from, to)`

Each booking-module caller maps a verdict to its own module's exception, avoiding a scheduling→booking dependency:
- `PAST_DATE` / `ELAPSED` on a timed slot → existing `SlotDateInThePastException` (409 `SLOT_DATE_IN_THE_PAST`; the frontend already handles it)
- `PAST_DATE` for a queue or walk-in request → new `SessionNotAcceptingBookingsException` (409 `SESSION_NOT_ACCEPTING_BOOKINGS`)
- `CANCELLED` → new `SessionNotAcceptingBookingsException` (409 `SESSION_NOT_ACCEPTING_BOOKINGS`)

The time proxy for untimed requests (queue, walk-in) is the current time on the session date, the same proxy spec 030 FR-008 uses.

**Clock:** follows the existing `SessionLiveStatusService` pattern:
- the production constructor uses `Clock.systemDefaultZone()`
- a package-visible constructor accepts a `Clock` for tests

No new bean (YAGNI).

**Listing queries (FR-008, FR-012):**
- `SlotRepository.findOpenFixedTimeSlots[OnDate]` gain `NOT EXISTS (covering cancellation)` and `(sessionDate > :from OR startTime > :nowTime)`. The existing `:from` is already "today".
- `SessionRepository.findUpcomingQueueSessionsByClinic` gains `NOT EXISTS (whole cancellation, or a range covering :nowTime when sessionDate = :from)`.

The JPQL is validated at application startup (Spring Data parses `@Query`).

**Walk-in after the session's scheduled end:** stays allowed (spec 063). Only past-date and cancelled sessions are refused.

## R5 — Elapsed boundary (BUG-005)

**Decision:** a slot is elapsed when `sessionDate + startTime <= now`. "Exactly now" counts as elapsed; see the spec Assumptions.

In the query this is `startTime > :nowTime` for today's rows. Seconds precision comes from `LocalTime` in the database. Tests pin the clock.

## R6 — Time zone (PB-005, investigated)

**Finding:**
- Evidence the product targets Indian clinics only: `IndianMobileNumberValidator`, INR fees, and all seed or test clinics being Indian.
- The repository contains no deployment configuration (no Dockerfile or JVM options) that sets another zone.
- Every business rule uses the JVM default zone consistently, so on an IST host the behaviour is correct.
- The only failing configuration is a non-IST JVM, and no such configuration is defined anywhere in the project.

**Decision:** no zone migration in Phase 1 (per the brief). New rules use an injected clock in the same default zone, so tests are deterministic.

Recorded as **requires a product/deployment decision:** pin `-Duser.timezone=Asia/Kolkata` in deployment, or introduce an explicit clinic time zone.

## R7 — Cross-clinic fee configuration (SEC-03, investigated)

**Finding:** the behaviour is explicitly specified:
- backlog 015 Business Rules: "An Appointment Type is scoped to a Doctor (global), not to a Clinic"; configuration by "an active ClinicAdmin at any clinic where that doctor currently holds an active Role Assignment"
- spec 017 FR-005/FR-006

**Decision:** no change. Recorded as INVESTIGATED — matches the specification, and BLOCKED for any change pending a product decision (per-clinic pricing would need a data model and API change).

## R8 — Deactivated doctor clinical access (SEC-06 / PB-008)

**Finding:** `TreatingDoctorAuthorizationService.requireTreatingDoctor` compares account id only. Spec 034 Edge Cases say re-checking staffing was explicitly out of its scope. Result: a doctor whose Doctor role at the clinic is inactive can still create documents (confirmed by code).

**Decision:**
- Add `requireActiveTreatingDoctor(Booking, callerId)`: the treating-doctor check **plus** `existsByAccount_IdAndClinic_IdAndRoleAndActiveTrue(caller, bookingClinic, Doctor)`.
- Use it in the three `create` methods only. Read paths keep `requireTreatingDoctor` (historical visibility).
- The clinical module already depends on identity.

The booking-state precondition (cancelled or future bookings) is out of scope.

## R9 — Frontend timeouts (BUG-006)

**Finding [runtime]:**
- In isolation, the RegistrationForm tests take 1.0–1.3 s each, ScheduleForm 0.4–0.8 s, and ExternalRecordReferenceForm up to 1.1 s.
- The full suite runs about 78 files in parallel workers, with a total environment time of 546 s. That slows each test by about 4–5×, so the ~1.3 s tests cross Vitest's 5 s default.
- The cost is `user.type` typing about 60 characters one key at a time. In user-event v14, the default `delay: 0` still awaits a `setTimeout(0)` between keystrokes, which queues behind other work under load, and each key re-renders the form (inline validation, spec 054).
- 53 test files use `userEvent.setup()`; 8 already pass options.

**Decision:** in the affected tests, use `userEvent.setup({ delay: null })`. This removes the per-keystroke timer yield while still dispatching every key event, so onChange and validation behaviour stay fully exercised. No global timeout change.

Verify with 3 consecutive full-suite runs (SC-006).

**Alternatives rejected:**
- Raising `testTimeout`: hides the problem (the brief forbids it without justification).
- Replacing typing with `paste`: changes what is being exercised.
