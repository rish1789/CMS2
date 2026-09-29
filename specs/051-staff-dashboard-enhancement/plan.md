# Implementation Plan: Staff Operational Dashboard Enhancement

**Branch**: `051-staff-dashboard-enhancement` | **Date**: 2026-09-15 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/051-staff-dashboard-enhancement/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

Add a "Today's sessions" condensed list (US1, zero new backend — reuses the existing `GET /api/v1/clinics/{clinicId}/sessions` endpoint's `SessionSummary` rows) and a "Today's stats" completed/no-show tile (US2, one new minimal backend query) to `ClinicToolsDashboard.tsx`. Restyle the 3 existing live tiles onto 046's `Card`/`Badge` (US3). The new backend surface is a single grouped-count repository method (`SlotRepository`, mirroring 042's own `countBySessionIdIn` pattern) plus a small dedicated controller (`TodaySessionStatsController`, mirroring the existing `SessionDelayController`/`SlotCompletionController` one-endpoint-per-concern shape) reusing `ClinicSessionListController`'s exact "any active role at this clinic" authorization gate. "Walk-ins today" and any "Recent Activity" widget are explicitly not built (spec.md Edge Cases) — no schema support exists for either.

## Technical Context

**Language/Version**: Java 21 / Spring Boot 3.3.5 (backend); TypeScript 5 / React 18 (frontend) — existing stack, no change

**Primary Dependencies**: No new dependency on either side. Backend: Spring Data JPA (existing grouped-projection pattern, `SlotRepository.SlotCountBySession`). Frontend: 046's `Card`/`Badge` components, existing `fetch`-based API client pattern.

**Storage**: PostgreSQL (existing) — no schema change, no migration. The new query reads existing `Slot.status`/`Slot.session.sessionDate`/`Slot.session.clinic` columns only.

**Testing**: Backend: Mockito unit test for the new repository-consuming controller logic (`backend/src/test/java/com/cms/scheduling/unit/`), `@WebMvcTest` contract test for the new endpoint's success/failure responses. Frontend: Vitest + React Testing Library (`frontend/tests/`).

**Target Platform**: Web (existing Vite/React SPA + Spring Boot API), no new platform surface.

**Project Type**: Web application (frontend + backend, first full-stack feature in Step 10's design-system wave — 046/047 were frontend-only).

**Performance Goals**: The new query is a single grouped `COUNT ... GROUP BY status` scoped to one clinic + one date (bounded by a clinic's daily session volume) — no pagination needed, matches the sibling `countBySessionIdIn` query's own bounded shape.

**Constraints**: Zero new visual tokens (reuse `DESIGN.md`/046 exactly); the new endpoint reuses `ClinicSessionListController`'s existing authorization gate verbatim (FR-009) rather than inventing a new one; no schema/migration change (FR-004's "walk-ins" exclusion is exactly because that would require one).

**Scale/Scope**: 1 new backend endpoint (2 integer counts), 1 new repository method, 1 restyle pass + 1 new list section on 1 existing frontend page (`ClinicToolsDashboard.tsx`).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Principle I (Test-First)**: Applies fully here — this is genuinely new backend business logic (a new repository query + controller endpoint), not just frontend chrome. A failing contract test for the new endpoint and a unit test for its authorization/aggregation logic are written alongside implementation (backend integration tests can't execute in this sandbox — documented, established limitation — but are written and reviewed same as every prior backend feature this session).
- **Principle II (Simplicity & YAGNI)**: PASS — the new query is the minimal grouped-count addition needed (research.md), following an existing established pattern (`countBySessionIdIn`) rather than inventing a new one; no new service class where the sibling controller's own convention already calls repositories directly; "walk-ins today" is explicitly NOT built precisely because satisfying it would require more than a minimal query (a new persisted column) — the YAGNI line is drawn at real schema support, not effort.
- **Principle III (Modular, Library-First Architecture)**: PASS — the new endpoint lives entirely inside `com.cms.scheduling` (same module as its sibling `ClinicSessionListController`/`SlotRepository`), touching no other module, adding no new cross-module event (this is a pure read).
- **Principle IV (Data Privacy & Integrity by Design)**: PASS — no new data stored; the query reads existing, already-authorized clinic-scoped data; the existing "active role at this clinic" gate (FR-009) keeps tenant-scoping intact, unchanged from its sibling endpoint.

No violations — Complexity Tracking table not needed.

## Project Structure

### Documentation (this feature)

```text
specs/051-staff-dashboard-enhancement/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md         # Phase 1 output (/speckit-plan command)
├── quickstart.md         # Phase 1 output (/speckit-plan command)
├── contracts/             # Phase 1 output (/speckit-plan command)
└── tasks.md               # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
backend/
├── src/main/java/com/cms/scheduling/
│   ├── repository/SlotRepository.java        # EDIT - add countStatusByClinicAndDate
│   ├── dto/TodaySessionStatsResponse.java     # NEW
│   └── api/TodaySessionStatsController.java   # NEW
└── src/test/java/com/cms/scheduling/
    ├── unit/TodaySessionStatsControllerTest.java   # NEW (Mockito)
    └── contract/TodaySessionStatsControllerContractTest.java  # NEW (@WebMvcTest)

frontend/
├── src/
│   ├── features/day-sheet/api.ts                    # EDIT - add getTodayStats
│   └── routes/staff/ClinicToolsDashboard.tsx          # EDIT - Today's sessions list, stats tile, 046 restyle
└── tests/staff/ClinicToolsDashboard.test.tsx           # EDIT - existing file (5 tests today); one existing
                                                          #   assertion (listSessions called with size:1) needs
                                                          #   a deliberate update since US1 changes that size
```

**Structure Decision**: First full-stack feature in this design-system wave — backend work confined entirely to `com.cms.scheduling` (Constitution Principle III), frontend work confined to the one existing dashboard page plus its API client. No new module, no new frontend route.

## Complexity Tracking

*No violations — table not needed.*
