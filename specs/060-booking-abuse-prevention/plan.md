# Implementation Plan: Booking Protection / Appointment Abuse Prevention

**Branch**: `060-booking-abuse-prevention` | **Date**: 2026-09-22 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/060-booking-abuse-prevention/spec.md`

## Summary

Add three layered protections to self-service patient booking: (1) a global + optional per-clinic
cap on active appointments, (2) a persisted, patient-account-keyed rate limiter on booking
*attempts* (not just successes) enforced in the service layer, and (3) a periodic signal sweep that
flags suspicious patterns (attempt volume, cancellations, no-shows, overlapping bookings, repeated
rate-limit hits) for clinic staff to review — never auto-enforced. The two synchronous gates
(booking limit, rate limit) live inside the existing `booking` module, next to the write-gates they
extend (`PatientBookingService.bookSlot`, `PatientQueueBookingService.bookSlot`). A new `protection`
module owns everything read-only/asynchronous (flag detection, the settings a Super Admin and
ClinicAdmin configure) and depends one-way on `booking` and `scheduling`, mirroring this codebase's
existing `clinical → booking` and `booking → scheduling` dependency precedents — nothing depends on
`protection`, so no new module cycle is introduced.

## Technical Context

**Language/Version**: Java 21 (backend, Spring Boot 3.3), TypeScript 5 / React 19 (frontend, Vite)

**Primary Dependencies**: Spring Data JPA/Hibernate — no new dependency of any kind; this feature's
"rate limiting" and "settings" needs are met with a plain relational table plus ordinary
transactional reads/writes, not a new library (Bucket4j/Redis/etc. considered and rejected — see
research.md Decision 2).

**Storage**: PostgreSQL via Flyway migrations. Six new tables: `booking_attempt_log`,
`clinic_booking_limit_override`, `clinic_booking_limit_override_change_log` (all owned by the
`booking` module), `protection_setting`, `suspicious_activity_flag`,
`protection_setting_change_log` (all owned by the new `protection` module) — the two `*_change_log`
tables are append-only audit history for AUD-002/AUD-003 (research.md Decision 9).

**Testing**: JUnit 5 + Mockito (unit), `@WebMvcTest` (contract), Testcontainers (integration,
written/compiled but unexecuted in this sandbox per the project's standing Docker limitation) on
the backend; Vitest + Testing Library on the frontend — matching every prior feature in this
codebase.

**Target Platform**: Existing web app (Spring Boot backend on :8080, Vite/React frontend on :5173)
— no new platform.

**Project Type**: Web application (existing `backend/` + `frontend/` structure).

**Performance Goals**: The two synchronous booking-time checks (limit, rate limit) must each be a
small number of indexed queries — no full-table scans, no N+1 — so they add no perceptible latency
to booking creation (spec.md NFR-002). The flag-detection sweep runs on its own schedule, off the
request path entirely, so it has no latency budget shared with booking.

**Constraints**: The booking-limit and rate-limit checks MUST hold under concurrent simultaneous
attempts from the same patient account (spec.md FR-006, NFR-003) — addressed with a database-level
guard, not application-level locking alone (see research.md Decision 1). No new module may create a
dependency cycle with `booking` or `scheduling` (Constitution III).

**Scale/Scope**: Touches the `booking` module (two new entities, one new service, a new
precondition in both existing patient booking-creation services, one new repository method for
active-appointment counting) and introduces one new module, `protection` (four new entities/
services combined, three new controllers spanning the Super Admin and Staff realms), plus the
corresponding frontend surfaces (a patient-facing error state, a new ClinicAdmin "Booking
Protection" screen, a new Super Admin settings screen). No changes to any other existing module.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. Test-First Development**: Every new service method, repository query, and endpoint below
  gets unit/contract coverage written first, mirroring this codebase's established three-tier shape
  (unit → contract → integration). The concurrency guarantee (FR-006, NFR-003) gets its own explicit
  concurrent-request test, matching this project's existing `*ConcurrencyTest` precedent (e.g.
  `WalkInConcurrencyTest`, `QueueBookingConcurrencyTest`). **PASS** (see research.md Decision 1 for
  the concurrency mechanism itself).
- **II. Simplicity & YAGNI**: No new dependency, no generic event bus, no caching layer for
  settings reads (a plain repository lookup per check is cheap enough — see research.md Decision 2).
  The new "runtime settings" mechanism is scoped to exactly the named values enumerated in
  data-model.md's settings table (16 distinct names) — this feature's own actual need,
  not a general-purpose settings framework (spec.md explicitly rules this out). Flag detection reuses
  a periodic `@Scheduled` sweep — the exact same shape as the existing `NoShowDetectionService` —
  instead of new event-driven infrastructure. **PASS**.
- **III. Modular, Library-First Architecture**: The two synchronous booking-time gates live inside
  `com.cms.booking` itself, since they are booking-creation preconditions of the same kind as the
  existing fee-resolution and slot-availability checks already in `PatientBookingService.bookSlot` —
  not a foreign concern bolted on. The new `protection` module depends one-way on `booking` (reads
  `Booking`/`BookingAttemptLog`) and `scheduling` (reads `Slot` no-show data), mirroring the
  established `clinical → booking` and `booking → scheduling` precedents; nothing depends on
  `protection`, so introducing it creates no cycle. See research.md Decision 3 for the full
  dependency-direction analysis, including the alternative (an event-driven push from `booking` into
  `protection`) that was considered and rejected specifically because it would have required a
  reverse dependency. **PASS**.
- **IV. Data Privacy & Integrity by Design**: `BookingAttemptLog` rows are retained only as long as
  the longest active window needs them, then age out (spec.md SEC-005) — a new, explicit retention
  rule, not indefinite storage. No clinical/DPDP-governed data (consultation notes, prescriptions) is
  touched. The concurrency guarantee for the booking-limit check closes the same class of
  duplicate/race risk this principle already requires for patient-identity creation, applied here to
  "how many active bookings does this patient have" instead. **PASS**.
- **Multi-tenancy**: `BookingAttemptLog` and the global-limit check are patient-account-scoped
  (intentionally cross-clinic, matching the existing `PatientAccount`-spanning precedent in "My
  Bookings"). Every *admin-facing* read (flags, evidence) is explicitly clinic-scoped per spec.md
  FR-021/FR-022/BR-004, with the one documented cross-clinic exception (the global-limit fact) — this
  plan's contracts enforce that scoping the same way this codebase already enforces it elsewhere
  (explicit `SecurityConfig` matchers, `RoleAssignmentRepository`-based clinic-membership checks).
  **PASS**.
- **Out-of-scope boundaries**: No payments, uploads, notifications, or reschedule touched. This
  feature does not add a patient-suspension/ban capability (spec.md Assumptions) — flagged and
  explicitly deferred, not silently expanded. **PASS**.

No violations to justify — Complexity Tracking table is intentionally omitted below.

**Post-Design Re-check** (after Phase 0/1 artifacts below): still **PASS** on every principle. The
module-boundary decision (Decision 3) was the one genuine risk area and was reinforced, not
weakened, once the exact call graph was traced through both `PatientBookingService.bookSlot` and
`PatientQueueBookingService.bookSlot` — both already have a single, well-defined precondition point
this feature extends, requiring no restructuring of either existing method beyond adding two new
checks and one new attempt-recording wrapper.

## Project Structure

### Documentation (this feature)

```text
specs/060-booking-abuse-prevention/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
└── tasks.md             # Phase 2 output (/speckit-tasks — not created here)
```

### Source Code (repository root)

```text
backend/src/main/java/com/cms/
├── booking/
│   ├── domain/
│   │   ├── BookingAttemptLog.java              # NEW — patient_account_id, clinic_id, attempted at,
│   │   │                                          outcome (SUCCESS/LIMIT_REACHED/RATE_LIMITED/OTHER_FAILURE)
│   │   ├── ClinicBookingLimitOverride.java      # NEW — clinic_id, max_active_appointments
│   │   └── ClinicBookingLimitOverrideChangeLog.java  # NEW — append-only audit history (AUD-003)
│   ├── repository/
│   │   ├── BookingAttemptLogRepository.java     # NEW
│   │   ├── ClinicBookingLimitOverrideRepository.java  # NEW
│   │   ├── ClinicBookingLimitOverrideChangeLogRepository.java  # NEW
│   │   └── BookingRepository.java               # + countByPatient_PatientAccount_IdAndStatus (global count)
│   ├── service/
│   │   ├── BookingProtectionService.java        # NEW — checkAndRecordAttempt(patientAccountId, clinicId)
│   │   │                                          throws RateLimitedException / BookingLimitReachedException;
│   │   │                                          called first thing in both bookSlot() methods below
│   │   ├── PatientBookingService.java            # + BookingProtectionService precondition + attempt recording
│   │   └── PatientQueueBookingService.java        # + same precondition + attempt recording
│   ├── exception/
│   │   ├── RateLimitedException.java            # NEW
│   │   └── BookingLimitReachedException.java    # NEW
│   └── api/
│       └── ClinicBookingLimitOverrideController.java  # NEW — ClinicAdmin reads/writes FR-004/FR-028
│                                                   (thin: delegates storage to booking, exposed under
│                                                   /api/v1/clinics/{clinicId}/protection/limit-override
│                                                   so it sits alongside protection's other clinic-facing
│                                                   endpoints in the frontend's single "Booking Protection"
│                                                   admin area)
└── protection/                                   # NEW MODULE
    ├── domain/
    │   ├── ProtectionSetting.java                # NEW — named key/value rows (global cap, rate-limit
    │   │                                            threshold/window/cooldown, 5 signal thresholds/windows,
    │   │                                            3 protection-enabled toggles)
    │   ├── ProtectionSettingChangeLog.java        # NEW — append-only audit history (AUD-002)
    │   └── SuspiciousActivityFlag.java           # NEW — patient_account_id, clinic_id (nullable = cross-clinic),
    │                                                signal_type, reason, detected_at, status, resolved_by,
    │                                                resolved_at
    ├── repository/
    │   ├── ProtectionSettingRepository.java      # NEW
    │   ├── ProtectionSettingChangeLogRepository.java  # NEW
    │   └── SuspiciousActivityFlagRepository.java # NEW
    ├── service/
    │   ├── ProtectionSettingService.java         # NEW — read-with-fallback-default + Super Admin write
    │   └── FlagDetectionService.java             # NEW — @Scheduled sweep, one method per signal (FR-016–FR-020),
    │                                                reads booking.BookingRepository/BookingAttemptLogRepository
    │                                                and scheduling.SlotRepository (both one-way reads)
    └── api/
        ├── SuperAdminProtectionSettingController.java  # NEW — /api/v1/admin/protection-settings
        └── ClinicProtectionFlagController.java          # NEW — /api/v1/clinics/{clinicId}/protection/flags

frontend/src/
├── features/
│   ├── patient-booking/
│   │   └── (existing booking-flow components) # + limit/cooldown error states, no new files required —
│   │                                             existing error-surfacing pattern already used for
│   │                                             SlotAlreadyBookedException-style failures
│   ├── clinic-protection/                       # NEW — ClinicAdmin's flag review + local limit override
│   │   ├── api.ts
│   │   ├── ProtectionFlagsList.tsx
│   │   └── ClinicLimitOverrideForm.tsx
│   └── admin-protection-settings/                # NEW — Super Admin's system-wide settings screen
│       ├── api.ts
│       └── ProtectionSettingsPage.tsx
└── routes/
    ├── staff/
    │   └── (new route into clinic-protection, alongside existing ClinicShell tools)
    └── admin/
        └── (new route into admin-protection-settings, alongside existing AdminShell sections)
```

**Structure Decision**: Existing `backend/` + `frontend/` web-application layout, unchanged.
Synchronous enforcement lives inside the existing `booking` module (Constitution III — these are
booking-creation preconditions, not a foreign concern). Everything else (settings, flag detection,
admin review) lives in one new module, `com.cms.protection`, depending one-way on `booking` and
`scheduling` — no existing module is modified to depend on `protection`. No new frontend route
shells; new screens hang off the existing `StaffShell`/`AdminShell`.

## Complexity Tracking

*No Constitution Check violations — table intentionally omitted.*
