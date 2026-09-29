# Implementation Plan: Phase 1 Stabilization — Security Boundary and Booking Correctness

**Branch**: `065-phase1-stabilization` | **Date**: 2026-09-29 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/065-phase1-stabilization/spec.md`

## Summary

This plan fixes the audit's confirmed defects with the smallest structural change that expresses each rule. Changes by area:

- **Security chains:** the staff and patient chains become fail-closed (R1).
- **Launch config:** loads secrets from the gitignored `.env` instead of carrying literals (R2).
- **Session cancellation:** recorded in a new append-only `session_cancellation` table (R3). One `SessionAvailabilityService` evaluates bookability for all five booking paths and the two patient listings, using an injectable clock (R4, R5).
- **Clinical documents:** creation additionally requires an active Doctor role (R8).
- **Frontend tests:** three typing-heavy test files drop user-event's per-keystroke timer yield (R9).
- **Investigated, no change:** time zone (R6) and cross-clinic fees (R7). Both are recorded as needing a product decision.

## Technical Context

| Item | Value |
|---|---|
| **Language/Version** | Java 21 (Spring Boot 3.3.5); TypeScript ~6.0 (tests only) |
| **Primary Dependencies** | Spring Security 6, Spring Data JPA, Flyway; Vitest 4 and @testing-library/user-event 14 |
| **Storage** | PostgreSQL; one new forward-only migration `V41__session_cancellation.sql` |
| **Testing** | JUnit 5 + Mockito (unit), `@WebMvcTest` (contract), Testcontainers (integration; Docker unavailable locally), Vitest |
| **Target Platform** | Linux/Windows JVM server; browser SPA |
| **Project Type** | Web application (`backend/` + `frontend/`) |
| **Performance Goals** | No regression. Listing queries gain one `NOT EXISTS` over an indexed `session_id`. |
| **Constraints** | No API shape change. No change to `SlotStatus` semantics. Must not touch unrelated uncommitted work. No destructive git. |
| **Scale/Scope** | 2 security configs, 1 migration, 1 entity, 1 repository, 1 service, about 8 modified services/repositories, 1 exception, 3 frontend test files, 1 launch config |

## Constitution Check

| Principle | Status |
|---|---|
| I. Test-First | Each fix starts with a failing unit or contract test (see tasks). The migration's invariants (single whole record, range CHECK) get integration tests. These are written, but only executable with Docker, and are reported as such. |
| II. Simplicity/YAGNI | One table and one service, justified in the Complexity Tracking table below. No new bean for Clock (follows the existing constructor pattern). No zone migration. No fee-model change. |
| III. Modular | The new state and service live in `scheduling`, which owns `Session`. `booking` callers map verdicts to their own exceptions, adding no scheduling→booking dependency. No new cross-module reach-through beyond the existing booking→scheduling direction. |
| IV. Privacy/Integrity | Clinical creation is tightened (active role). Immutability is untouched. The whole-cancel race is closed with a partial unique index (data layer). |
| Workflow gate: business-rule rationale | Recorded in the spec's "Documented product decisions". It reverses the 029/030 clarifications and extends 034, on the owner's 2026-09-29 brief. |
| Out-of-scope boundaries | None touched |

**Result: PASS** (with the justified complexity below). Re-checked after design: PASS.

## Project Structure

### Documentation (this feature)

```text
specs/065-phase1-stabilization/
├── spec.md, plan.md, research.md, data-model.md, quickstart.md
├── contracts/api-behaviour-changes.md
├── checklists/requirements.md
└── tasks.md            (/speckit-tasks)
```

### Source Code (touched areas only)

```text
backend/src/main/java/com/cms/
├── identity/account/config/SecurityConfig.java            # R1 staff chain fail-closed
├── patient/account/config/SecurityConfig.java             # R1 patient chain fail-closed
├── scheduling/domain/SessionCancellationRecord.java       # R3 new entity
├── scheduling/repository/SessionCancellationRecordRepository.java  # R3 new
├── scheduling/service/SessionAvailabilityService.java     # R4 new: verdicts + record writes
├── scheduling/repository/SlotRepository.java              # R4 listing filters
├── scheduling/repository/SessionRepository.java           # R4 queue listing filter
├── booking/exception/SessionNotAcceptingBookingsException.java  # new, mapped in BookingExceptionHandler
├── booking/service/{PatientBookingService, StaffBookingService, PatientQueueBookingService,
│                     StaffQueueBookingService, FrontDeskWalkInService}.java  # R4 guard
├── booking/service/{SessionCancellationService, SessionPartialCancellationService}.java  # R3
└── clinical/service/{TreatingDoctorAuthorizationService, ConsultationNoteService,
                      PrescriptionService, ExternalRecordReferenceService}.java  # R8
backend/src/main/resources/db/migration/V41__session_cancellation.sql
backend/src/test/java/com/cms/...                          # new unit/contract/integration tests
frontend/tests/{clinic-registration,scheduling,external-record-references}/*.test.tsx  # R9
.claude/launch.json                                        # R2
```

**Structure Decision**: the existing web-application layout, with package-per-feature modules. No new module.

## Complexity Tracking

| Addition | Why needed | Simpler alternative rejected because |
|---|---|---|
| `session_cancellation` table + entity | A cancelled state must exist for empty sessions, queue sessions, and multiple or bounded ranges (spec FR-005–FR-011) | Session columns can't hold multiple ranges. A new slot status can't block queue or empty sessions and changes existing state semantics. |
| `SessionAvailabilityService` | One rule shared by 5 booking paths and 2 listings (SC-002, SC-004). Duplicating it 7 times recreates the audit's TD-03 pattern. | Inline checks per service would diverge |
