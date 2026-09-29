# Implementation Plan: Day Sheet Hardening

**Branch**: `042-day-sheet-hardening` | **Date**: 2026-09-09 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/042-day-sheet-hardening/spec.md`

## Summary

A defect/hardening pass on the already-shipped Day Sheet feature (041-staff-console-pickers): add a confirmation step before whole-session cancellation, add server-side pagination and a doctor filter to the session list (closing a real scalability gap confirmed against live data), add a supporting database index, fix the shared `ActionMenu` component's two known-bad behaviors (already fixed once elsewhere this session on an equivalent component), close a data gap where the session detail page's header depends on frontend navigation state instead of the API response, add a per-session booked/total slot indicator, and bring the page's visual language in line with the already-converged Roster page. No new entities, no new endpoints — every change extends an existing response or adds query params to an existing endpoint.

## Technical Context

**Language/Version**: Java 21 (Spring Boot, backend) / TypeScript + React 18 (frontend) — established by 001, unchanged.

**Primary Dependencies**: Spring Data JPA, Flyway (backend); React Router, Tailwind CSS (frontend) — all already in use, no new dependency introduced.

**Storage**: PostgreSQL. No new tables/entities — one new index on the existing `session` table.

**Testing**: JUnit 5 + Spring Boot Test + Testcontainers (backend integration tests — subject to this sandbox's documented Docker/Testcontainers limitation, written and compiling per established project convention, execution deferred to a real dev/CI environment); Vitest + Testing Library (frontend).

**Target Platform**: Web (staff console), same runtime as every other feature in this codebase.

**Project Type**: Web application (backend + frontend), matching the existing repository layout.

**Performance Goals**: The session list query must resolve its clinic+date filter via an index instead of a scan (SC-002's underlying mechanism); the frontend must request only the current page of sessions, not the full 14-day/all-doctors result set.

**Constraints**: Every response change is additive (new fields/params only) — no existing field removed or repurposed, preserving every current consumer. Doctor self-scoping authorization (FR-006) is unchanged. Migrations are forward-only per the constitution's platform constraints.

**Scale/Scope**: Two backend controllers touched (`ClinicSessionListController`, `SessionDaySheetController`) plus their response DTOs and repositories; one new Flyway migration (index only); three frontend files restyled/fixed (`DaySheet.tsx`, `SessionSlotsView.tsx`, `ActionMenu.tsx`) plus `CancelSessionButton.tsx` for the confirm step. No new backend module, no new frontend feature folder.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. Test-First Development**: PASS. Every behavioral change (pagination, doctor filter, bulk-loaded fullness count, doctorName/sessionDate in the day-sheet response, cancellation confirm-step, ActionMenu exclusivity/positioning) gets new or updated tests before/alongside implementation, per this session's established pattern for every prior change. The new index is a performance change, not a new invariant, so it does not require its own "prove the invariant" test the way a new uniqueness constraint would (Principle I's own stated trigger is invariants like uniqueness/status-transitions, not query performance).
- **II. Simplicity & YAGNI**: PASS. No new entity, no new module, no new endpoint. Doctor filter and pagination are added as query params to the existing list endpoint (mirroring its own existing optional `from`/`to` params). Fullness count and doctorName/sessionDate are additive response fields computed from already-loaded/already-queryable data — no denormalized counter columns, no new join tables.
- **III. Modular, Library-First Architecture**: PASS. All backend changes stay inside `com.cms.scheduling` (Session/Slot, already home to the list endpoint) and `com.cms.booking` (the day-sheet detail endpoint, already documented there per its own javadoc as composing Slot/Booking/Patient). No module boundary is crossed or newly introduced.
- **IV. Data Privacy & Integrity by Design**: PASS. No patient-identifying data is newly exposed (doctorName, sessionDate, and booked/total slot counts are not patient data). No change to anonymization, retention, or clinical-documentation immutability. The confirm-step and ActionMenu fixes are UI-layer only.

No violations. Complexity Tracking table not needed.

**Post-Phase 1 re-check**: PASS, unchanged. Phase 1 design (data-model.md, contracts/, quickstart.md) confirmed zero new entities, zero new modules, and every response change additive-only — nothing surfaced during design that alters the pre-research gate evaluation above.

## Project Structure

### Documentation (this feature)

```text
specs/042-day-sheet-hardening/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md         # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
└── tasks.md             # Phase 2 output (/speckit-tasks - not created here)
```

### Source Code (repository root)

```text
backend/
├── src/main/java/com/cms/scheduling/
│   ├── ClinicSessionListController.java     # + pagination, doctorProfileId filter, doctors[]/counts in response
│   ├── SessionRepository.java               # + Pageable-aware query, + doctor-filtered variant, + distinct-doctors-in-window query
│   └── dto/
│       ├── SessionSummaryResponse.java      # + bookedSlotCount, totalSlotCount
│       └── SessionListResponse.java         # + doctors[], + page metadata
├── src/main/java/com/cms/booking/
│   ├── SessionDaySheetController.java       # (unchanged logic, response extended)
│   └── dto/SessionDaySheetResponse.java     # + doctorName, sessionDate
└── src/main/resources/db/migration/
    └── V27__session_clinic_date_index.sql   # new composite index

frontend/
├── src/features/day-sheet/
│   ├── DaySheet.tsx            # pagination controls, doctor filter, fullness badge, restyle
│   ├── SessionSlotsView.tsx    # danger-zone separation, restyle, reads doctorName/sessionDate from API
│   └── api.ts                  # request/response types for the extended contracts
├── src/features/session-cancellation/
│   └── CancelSessionButton.tsx # confirm/cancel phase, same pattern as staff deactivation
└── src/components/
    └── ActionMenu.tsx          # exclusivity (name grouping) + portal-based fixed positioning
```

**Structure Decision**: Existing `backend/` (Spring Boot, module-per-package) + `frontend/` (React feature-folder) layout, unchanged. This feature adds no new top-level directory on either side — every touched file already exists inside an established module/feature folder.

## Complexity Tracking

*No violations — table not needed.*
