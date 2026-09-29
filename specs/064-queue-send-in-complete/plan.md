# Implementation Plan: Send-In and Complete for Queue Sessions

**Branch**: `064-queue-send-in-complete` | **Date**: 2026-09-24 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/064-queue-send-in-complete/spec.md`

## Summary

Queue tokens are minted as waiting (`BOOKED`), and the existing In with doctor, Complete and staff-cancel actions accept Queue sessions. Queue position then counts only the tokens still waiting ahead, so it falls as patients are seen. The 063 front-desk counts and waiting-line panel extend to Queue sessions. A one-off migration moves the existing active queue bookings over. No new endpoint and no new mechanism (research.md).

## Technical Context

**Language/Version**: Java 21 (backend); TypeScript + React (frontend)

**Primary Dependencies**: Spring Boot (Web MVC, Data JPA); Vite, Tailwind v4, Vitest

**Storage**: PostgreSQL. One data-only migration, `V40__queue_tokens_booked.sql` (no schema change).

**Testing**: JUnit 5 + Mockito (unit), `@WebMvcTest` (contract), Testcontainers (integration, Docker-gated here); Vitest

**Target Platform**: Web application

**Project Type**: web-service + SPA

**Performance Goals**: Unchanged. Position stays one per-session slot read; the counts reuse 063's grouped query.

**Constraints**: No second queue, status or "now serving" mechanism. Appointment-session behavior unchanged. Clinic-scoped.

**Scale/Scope**:
- Backend: 5 service edits (issuance, Appeared, Complete, cancel, position), 1 query filter, 1 migration.
- Frontend: SessionStep, FrontDeskWalkInPage and WalkInLinePanel extended to Queue sessions.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | How |
|---|---|---|
| I. Test-First | PASS (planned) | Each change is preceded by a failing test. The data migration gets an integration test proving the migrated token counts as waiting and that cancelled bookings are left alone. |
| II. Simplicity & YAGNI | PASS | Mostly removing mode guards, plus one status change at the single issuance point. No new endpoint, table or abstraction. |
| III. Modular | PASS | Changes stay in their owning modules (scheduling issuance and slot actions; booking position and cancel; frontend features). No new cross-module calls. |
| IV. Privacy & Integrity | PASS | No clinical record touched. Cancellation stays race-guarded; the patient self-cancel rule is unchanged. |
| Multi-tenancy | PASS | The existing actions already resolve the slot or booking scoped to the path's clinic; nothing new is added. |
| Rationale note | PASS | Implements spec 064 (product decision "option B", 2026-09-24). It changes queue behavior from 018/024 (tokens now tracked as waiting/in-with-doctor/completed) and extends 057's Appeared/Complete and 028's staff cancel to Queue sessions. Patient self-cancel (028) and the waitlist bump (031) stay Fixed-Time only. |

## Project Structure

### Documentation (this feature)

```text
specs/064-queue-send-in-complete/
├── plan.md, research.md, data-model.md, quickstart.md
├── contracts/queue-send-in-complete.md
└── tasks.md
```

### Source Code (repository root)

```text
backend/src/main/resources/db/migration/V40__queue_tokens_booked.sql            # NEW (data only)
backend/src/main/java/com/cms/scheduling/service/QueueSlotService.java          # mint tokens BOOKED
backend/src/main/java/com/cms/scheduling/service/SlotAppearedService.java       # allow QUEUE
backend/src/main/java/com/cms/scheduling/service/SlotCompletionService.java     # allow QUEUE
backend/src/main/java/com/cms/scheduling/repository/SlotRepository.java         # walk-in line counts for QUEUE
backend/src/main/java/com/cms/booking/service/BookingCancellationService.java   # staff cancel for QUEUE
backend/src/main/java/com/cms/booking/service/QueuePositionService.java         # not applicable unless waiting
backend/src/main/java/com/cms/booking/service/FrontDeskWalkInService.java       # drop redundant setStatus
frontend/src/features/front-desk-walk-in/{SessionStep,FrontDeskWalkInPage,WalkInLinePanel}.tsx
```

**Structure Decision**: Existing layout; every change lands in the owning module.

## Complexity Tracking

None: no constitution violations.
