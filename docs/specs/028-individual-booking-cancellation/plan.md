# Implementation Plan: Individual Booking Cancellation & Waitlist Trigger

**Branch**: `028-individual-booking-cancellation` | **Date**: 2026-09-04 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/028-individual-booking-cancellation/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

Let staff cancel any confirmed Fixed-Time booking, and let the owning patient cancel their own
up to 2 hours before the scheduled slot time. A successful cancellation marks the Booking
`CANCELLED` (retained, not deleted — Clarifications), releases its Slot back to `OPEN`, and
publishes a durable `BookingCancelledEvent` — the sole waitlist-bump trigger in the system,
with no listener yet since 028/029 (matching/claim) haven't been built. Technical approach: one
new `BookingStatus` field + a partial unique index replacing the flat `uq_booking_slot`
constraint, one shared concurrency-critical `BookingCancellationService` behind two thin,
separately-secured controllers (research.md).

## Technical Context

**Language/Version**: Java 21 (backend), TypeScript/React (frontend) — matches every prior feature this session.

**Primary Dependencies**: Spring Boot, Spring Data JPA, Spring Security (staff JWT chain + patient JWT chain), Spring's `ApplicationEventPublisher`; React + Tailwind CSS on the frontend.

**Storage**: PostgreSQL via Flyway-versioned migrations.

**Testing**: JUnit 5 + Spring Boot Test (`@WebMvcTest`/`@SpringBootTest` + Testcontainers) on the backend; Vitest/React Testing Library on the frontend — mirrors every prior feature.

**Target Platform**: Linux server (Spring Boot backend), browser (React frontend).

**Project Type**: Web application (`backend/` + `frontend/`, existing structure).

**Performance Goals**: No new performance target — a single Booking lookup plus one conditional update, the same scale every prior booking-lifecycle action in this codebase already operates at.

**Constraints**: The concurrent-cancellation race-closure (FR-008) must be enforced at the data layer, not merely application-level — a conditional `@Modifying` update, not a plain read-then-write.

**Scale/Scope**: One new `Booking` field + migration (including a constraint replacement on an already-converged table), one new repository method, one new event type, one shared service, two thin controllers, one extended shared response DTO, one new frontend component shared by both audiences.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. Test-First Development**: PASS. Tasks will include failing tests (unit + Testcontainers
  integration, mirroring every prior feature's precedent) for both cancellation paths, the
  cutoff enforcement, the already-resolved-Slot rejection, the Fixed-Time-only gate, and — given
  this feature's own explicit FR-008 — a genuine concurrency test proving the data-layer
  race-closure, written before the corresponding implementation.
- **II. Simplicity & YAGNI**: PASS. No `WaitlistEntry` entity, no matching/claim logic — strictly
  the trigger signal this feature's own spec scopes it to (FR-006); no optimistic-locking
  machinery introduced where a conditional update already achieves the same guarantee (research.md
  R3).
- **III. Modular, Library-First Architecture**: PASS. Lives entirely inside the existing
  `com.cms.booking` module; the waitlist bump is an explicit event-driven interface (Constitution
  III's own named example), not a direct reach-through into not-yet-built matching code.
- **IV. Data Privacy & Integrity by Design**: PASS. This is the feature's central concern —
  FR-008's concurrency-sensitive cancellation race is closed at the data layer (research.md R3),
  matching Constitution IV's explicit requirement verbatim; retaining (not deleting) the cancelled
  Booking record is itself the more privacy/audit-conscious choice per Clarifications' resolved
  answer.

No violations — Complexity Tracking table not needed. One flagged risk, not a violation: this
feature modifies an already-converged table's constraint (`uq_booking_slot` → partial index) on
016's `Booking` entity — the migration and its interaction with 016/017/018/025's existing
`saveAndFlush`-based race-closure pattern needs explicit regression coverage in tasks.md, not just
new-behavior coverage.

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
├── src/main/java/com/cms/booking/
│   ├── Booking.java                          # extend: + BookingStatus status field
│   ├── BookingStatus.java                    # new
│   ├── BookingCancelledEvent.java             # new
│   ├── BookingRepository.java                # extend: + cancelIfActive @Modifying query
│   ├── BookingCancellationService.java        # new (shared core)
│   ├── BookingNotCancellableException.java    # new
│   ├── CancellationCutoffPassedException.java # new
│   ├── StaffBookingCancellationController.java   # new
│   ├── PatientBookingCancellationController.java # new
│   ├── BookingExceptionHandler.java           # extend: + 2 new mappings
│   └── dto/BookingResponse.java               # extend: + status field
├── src/main/resources/db/migration/
│   └── V16__booking_cancellation.sql          # new
├── src/main/java/com/cms/identity/account/
│   └── SecurityConfig.java                    # extend: + 1 new matcher
├── src/main/java/com/cms/patient/account/
│   └── SecurityConfig.java                    # extend: + 1 new matcher
└── src/test/java/com/cms/booking/integration/
    └── (new test classes)

frontend/
├── src/features/booking-cancellation/
│   ├── api.ts                                 # new
│   └── CancelBookingButton.tsx                # new
└── tests/booking-cancellation/                # new
```

**Structure Decision**: Existing web-application structure (`backend/` + `frontend/`). Backend
work lives entirely in the existing `com.cms.booking` module (015/016/017/018/025's home), with
matcher additions to both existing staff and patient `SecurityConfig` chains (mirrors 027's
identical pattern); frontend work is a new `booking-cancellation` feature directory.

## Complexity Tracking

*No violations — this section is not applicable.*
