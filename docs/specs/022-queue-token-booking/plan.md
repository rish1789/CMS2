# Implementation Plan: Queue/Token Booking

**Branch**: `022-queue-token-booking` | **Date**: 2026-09-03 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/022-queue-token-booking/spec.md`

## Summary

Give both staff (Operations/ClinicAdmin) and authenticated Patient Account holders a way to
book into an active Queue/Token Session, minting a new token-numbered Slot via 013's already-
converged `QueueSlotService.issueNextSlot`, resolving/locking a fee per 015, resolving/
creating the Patient per 016 (staff) or 019 via `PatientLinkingService` (patient self-
service), and creating a `Booking` — the Queue-mode analog of 016/017's Fixed-Time booking
services. The one genuinely new technical risk (research.md) is calling convention: this
feature's own service method must NOT wrap `queueSlotService.issueNextSlot(...)` inside a
broader `@Transactional` boundary, or it will silently defeat that service's own proven
per-attempt-retry race closure — the same class of bug already found twice this session
(020, 021), this time avoidable proactively rather than fixed reactively.

## Technical Context

**Language/Version**: Java 21 (backend), TypeScript/React (frontend).

**Primary Dependencies**: Spring Boot, Spring Security, Spring Data JPA; React + Vite +
Tailwind CSS (frontend, matching 016/017's booking features).

**Storage**: PostgreSQL via Flyway migrations — no new tables; reuses `session`, `slot`,
`booking`, `patient`, `appointment_type`.

**Testing**: JUnit 5 + Spring Boot Test + Testcontainers (PostgreSQL 16-alpine) + MockMvc for
backend; Vitest + Testing Library for frontend — matches every prior feature this session.

**Target Platform**: Existing Spring Boot backend service + React SPA frontend.

**Project Type**: Web application (backend + frontend), extending existing `com.cms.booking`
and `com.cms.scheduling` modules.

**Performance Goals**: N/A beyond existing system norms.

**Constraints**: Must not alter `QueueSlotService`'s existing retry-closure behavior or
`FeeResolutionService`/`PatientLinkingService`/`Booking`'s existing contracts. Must not
introduce a new security chain (extends the two existing staff/patient chains, per 020/021
precedent).

**Scale/Scope**: Two new HTTP endpoints (staff + patient), two new services, no new entities,
no new migration.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. Test-First Development**: PASS (planned) — tasks.md includes integration tests
  (staff auth/roles, fee-block, walk-in creation, first-time patient link, not-a-queue-
  session rejection, concurrent-token-uniqueness) written before implementation.
- **II. Simplicity & YAGNI**: PASS — no new entity, no new migration, no new security chain,
  no reimplementation of `QueueSlotService`'s retry logic; accepts (rather than engineers
  around) the small, already-latent non-atomicity between Slot-issuance and Booking-creation
  that `QueueSlotService`'s own already-converged design already implies (research.md) —
  forcing full atomicity would mean either reimplementing proven retry logic inline or
  restructuring an already-converged service, both disproportionate to a rare failure mode
  with no data-integrity consequence (no duplicate identity, no double-booking).
- **III. Modular, Library-First Architecture**: PASS — `QueueSlotService` (013),
  `FeeResolutionService` (015), and `PatientLinkingService` (019) are reused via their
  existing service contracts, not reimplemented; new code splits across `com.cms.booking`
  (the two new booking services/controllers) consistently with 016/017/021.
- **IV. Data Privacy & Integrity by Design**: PASS — the identity-record-creation race
  (`PatientLinkingService`'s already-proven, 021-fixed `saveAndFlush` closure) is reused
  unchanged; no new duplicate-creation or double-booking race is introduced by this feature
  (spec Assumptions: each Queue booking mints a brand-new Slot, so there is no pre-existing-
  Slot race to close, unlike Fixed-Time booking).

No violations. Complexity Tracking section not needed.

## Project Structure

### Documentation (this feature)

```text
specs/022-queue-token-booking/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── queue-booking.md
└── tasks.md
```

### Source Code (repository root)

```text
backend/
├── src/main/java/com/cms/booking/
│   ├── BookingExceptionHandler.java  # EXTENDED - maps com.cms.scheduling's
│   │   SessionNotFoundException/NotAQueueSessionException/TokenIssuanceFailedException,
│   │   first HTTP-reachable since 013/019 (research.md - same gap class as 015→016)
│   ├── StaffQueueBookingService.java   # NEW
│   ├── StaffQueueBookingController.java # NEW
│   ├── PatientQueueBookingService.java  # NEW
│   ├── PatientQueueBookingController.java # NEW
│   └── dto/
│       ├── QueueBookSlotRequest.java        # NEW (staff)
│       ├── PatientQueueBookSlotRequest.java # NEW (patient)
│       └── QueueBookingResponse.java        # NEW (both) - BookingResponse's fields + tokenNumber
└── src/test/java/com/cms/booking/integration/
    ├── AbstractQueueBookingIntegrationTest.java # NEW fixture
    ├── StaffQueueBookingTest.java                # NEW
    ├── PatientQueueBookingTest.java               # NEW
    └── QueueBookingConcurrencyTest.java            # NEW

frontend/
├── src/features/staff-booking/
│   ├── queueApi.ts            # NEW
│   └── QueueBookSlotForm.tsx  # NEW
├── src/features/patient-booking/
│   ├── queueApi.ts            # NEW
│   └── QueueBookSlotForm.tsx  # NEW
└── tests/
    ├── staff-booking/QueueBookSlotForm.test.tsx    # NEW
    └── patient-booking/QueueBookSlotForm.test.tsx  # NEW
```

**Structure Decision**: Extends the existing web application's `backend/` and `frontend/`
trees along already-established module boundaries. `BookingExceptionHandler` (already in
`com.cms.booking`, per 015/016/017/021's own placement) gains the new mappings since it is
the single shared exception-translation point for this whole module — consistent with how
020's convergence pass extended it, not a new handler. Staff/patient booking logic each split
into their own service+controller pair, mirroring 016/017/021's existing staff/patient split
exactly rather than a single shared service parameterized by actor type (Constitution II —
the two actors already differ enough, per 016/017's own precedent, that a shared abstraction
would cost more than the ~20 lines of duplication it would save).

## Complexity Tracking

*No Constitution Check violations — this section is not needed.*
