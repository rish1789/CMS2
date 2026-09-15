# Implementation Plan: Waitlist Matching (Longest-Waiting, Doctor/Specialization)

**Branch**: `031-waitlist-matching-longest-waiting` | **Date**: 2026-09-04 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/031-waitlist-matching-longest-waiting/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

Give the system a way to automatically select and offer the correct waitlist entry — doctor-match
tier first, specialization-only tier only if empty, longest-waiting within a tier — whenever 025's
individual voluntary cancellation releases a fixed-time slot, and give patients/staff a way to
actually join that waitlist in the first place (the necessary prerequisite no earlier feature
built). Technical approach: a new `com.cms.waitlist` module (the project constitution's own named
module boundary) with a `WaitlistEntry` entity, a `WaitlistJoinService` behind two thin
controllers, and a `WaitlistMatchingService` triggered only by an
`@TransactionalEventListener(phase = AFTER_COMMIT)` on 025's `BookingCancelledEvent` — mirroring
037's identical event-consumption pattern (research.md).

## Technical Context

**Language/Version**: Java 21 (backend), TypeScript/React (frontend) — matches every prior feature this session.

**Primary Dependencies**: Spring Boot, Spring Data JPA, Spring Security (staff JWT chain + patient JWT chain), Spring's transactional event listener support; React + Tailwind CSS on the frontend.

**Storage**: PostgreSQL via a new Flyway migration (`V17`) — this feature's first new table since 028's `booking.status` column.

**Testing**: JUnit 5 + Spring Boot Test (`@WebMvcTest`/`@SpringBootTest` + Testcontainers) on the backend; Vitest/React Testing Library on the frontend — mirrors every prior feature.

**Target Platform**: Linux server (Spring Boot backend), browser (React frontend).

**Project Type**: Web application (`backend/` + `frontend/`, existing structure).

**Performance Goals**: No new performance target — matching runs once per individual cancellation, over a small per-clinic candidate set.

**Constraints**: The matching search MUST only ever be reachable from 025's `BookingCancelledEvent` — no HTTP endpoint, no other call site (FR-009).

**Scale/Scope**: One new module (`com.cms.waitlist`), one new entity + migration, two thin join controllers over one shared service, one event listener + matching service, one frontend join form.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. Test-First Development**: PASS. Tasks will include failing tests (unit + Testcontainers
  integration) for the tier-priority ordering, the longest-waiting-within-a-tier selection, the
  no-match no-op case, the 025-exclusivity guarantee (no-show/whole-day/partial-cutoff never
  trigger a match), the join action's validation, and the dual staff/patient access split.
- **II. Simplicity & YAGNI**: PASS. No redundant `specialization` snapshot on doctor-match entries
  (research.md R2); no duplicate-entry prevention or clinic-verification gate the spec doesn't
  require (R6); the `OFFERED` status is introduced with no claim/expiry machinery attached yet,
  deliberately deferred to 029.
- **III. Modular, Library-First Architecture**: PASS. This is the constitution's own named
  "waitlist" module, materializing for the first time; cross-module communication is entirely
  event-driven (consuming `BookingCancelledEvent`, publishing via `NotificationEventService`) —
  no reach-through into `com.cms.booking`'s or `com.cms.notification`'s internals.
- **IV. Data Privacy & Integrity by Design**: PASS. Every entry requires a linked Patient Account
  (no anonymous/contact-less waitlist entry, unlike a walk-in Booking) — matching always has a
  valid notification target. No concurrency-sensitive identity-matching logic is introduced (join
  simply creates a new row; no dedup/race-closure requirement is stated).

No violations — Complexity Tracking table not needed.

**Post-Phase-1 re-check**: PASS, unchanged. Phase 1 design (data-model.md, contracts/,
quickstart.md) introduced nothing beyond what this gate already evaluated.

## Project Structure

### Documentation (this feature)

```text
specs/[###-feature]/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
backend/
├── src/main/java/com/cms/waitlist/                    # new module
│   ├── WaitlistEntry.java
│   ├── WaitlistEntryStatus.java
│   ├── WaitlistEntryRepository.java
│   ├── WaitlistJoinService.java
│   ├── WaitlistMatchingService.java
│   ├── WaitlistBumpListener.java
│   ├── ClinicNotFoundException.java
│   ├── DoctorNotStaffedAtClinicException.java
│   ├── PatientAccountNotFoundException.java
│   ├── WaitlistTargetRequiredException.java
│   ├── ForbiddenException.java
│   ├── PatientWaitlistController.java
│   ├── StaffWaitlistController.java
│   ├── WaitlistExceptionHandler.java
│   └── dto/
│       ├── JoinWaitlistRequest.java
│       ├── StaffJoinWaitlistRequest.java
│       └── WaitlistEntryResponse.java
├── src/main/resources/db/migration/
│   └── V17__create_waitlist_entry.sql                 # new
├── src/main/java/com/cms/identity/account/
│   └── SecurityConfig.java                            # extend: + 1 new matcher (staff join)
├── src/main/java/com/cms/patient/account/
│   └── SecurityConfig.java                            # extend: + 1 new matcher (patient join)
└── src/test/java/com/cms/waitlist/integration/         # new package
    └── (new test classes)

frontend/
├── src/features/waitlist/
│   ├── api.ts
│   └── JoinWaitlistForm.tsx
└── tests/waitlist/
```

**Structure Decision**: A new top-level module, `com.cms.waitlist` — the constitution's own named
module boundary materializing for the first time, alongside the existing `com.cms.scheduling`/
`com.cms.booking`/`com.cms.notification`/`com.cms.discovery` modules. No new `SecurityConfig`
chain — this module's two endpoints fall under the existing `/api/v1/clinics/**` (staff) and
`/api/v1/patients/**` (patient) prefixes exactly like every prior feature, so it adds one matcher
to each of those two already-existing chains rather than introducing a third.

## Complexity Tracking

*No violations — this section is not applicable.*
