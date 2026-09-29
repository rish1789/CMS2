# Implementation Plan: Tables & Lists Consistency Pass

**Branch**: `053-tables-lists-consistency` | **Date**: 2026-09-15 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/053-tables-lists-consistency/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

Migrate `DaySheet.tsx`'s hand-rolled Previous/Next pagination footer onto the shared `PaginationControls` component (FR-001, the one backlog-mandated fix). Replace `StaffPicker.tsx`'s locally-defined sort-header with the shared `SortableColumnHeader` (FR-002). Swap raw `<p>` empty-state markup and directly-rendered `ListSkeleton` loading markup for the shared `EmptyState`/`LoadingState` components across 7 verified files (FR-003/FR-004). No backend change, no new component, no new capability added to any list that doesn't already support it.

## Technical Context

**Language/Version**: TypeScript 5 / React 18 — existing frontend stack, no change

**Primary Dependencies**: No new dependency — `PaginationControls`, `SortableColumnHeader`, `EmptyState`, `LoadingState` all already exist (046).

**Storage**: N/A — no data change, reuses existing endpoints' existing query parameters unchanged.

**Testing**: Vitest + React Testing Library (existing convention) — updated assertions where markup changes (e.g. querying by role/text through the new shared components instead of the old raw markup), zero change to assertions about actual search/filter/sort/pagination behavior.

**Target Platform**: Web (existing Vite/React SPA).

**Project Type**: Frontend-only (third feature in this design-system wave to touch code — 046/047 were pure frontend, 048/049 were full-stack; this one has zero backend surface).

**Performance Goals**: No new goal — this is a markup/component-usage change over already-working data flows.

**Constraints**: Zero new visual tokens; zero new backend capability (FR-005); zero new pagination on the 2 explicitly-excluded derived/capped views (FR-006); zero new `Table` component or list-shape conversion (FR-007).

**Scale/Scope**: 2 real behavior migrations (DaySheet pagination, StaffPicker sort header) + empty/loading-state markup swaps across 7 files total.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Principle I (Test-First)**: Applies to the 2 behavior migrations (DaySheet pagination, StaffPicker sort) — their existing tests are the regression baseline, updated where markup queries change, verified to still prove the same underlying behavior (pagination advances pages correctly, sort still sorts). The empty/loading-state swaps are pure markup substitution with identical rendered text — existing tests that query by the message text continue to pass unmodified in most cases.
- **Principle II (Simplicity & YAGNI)**: PASS — this feature explicitly does *not* build a new `Table` component (046 already declined), does *not* add sort to `DoctorPicker` (no backend support), and does *not* add pagination to the 2 deliberately-capped/derived views — each a direct application of "don't build what isn't backed by a real, current need."
- **Principle III (Modular, Library-First Architecture)**: N/A — no backend module touched.
- **Principle IV (Data Privacy & Integrity by Design)**: N/A — no data handling change; this is presentation-layer consistency only.

No violations — Complexity Tracking table not needed.

## Project Structure

### Documentation (this feature)

```text
specs/053-tables-lists-consistency/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── quickstart.md         # Phase 1 output (/speckit-plan command)
└── tasks.md               # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

No `data-model.md`/`contracts/` — no data model or API contract changes.

### Source Code (repository root)

```text
frontend/
├── src/
│   ├── features/day-sheet/DaySheet.tsx                          # EDIT - PaginationControls, EmptyState, LoadingState
│   ├── features/staff-picker/StaffPicker.tsx                     # EDIT - SortableColumnHeader, EmptyState, LoadingState
│   ├── features/doctor-picker/DoctorPicker.tsx                    # EDIT - EmptyState, LoadingState only
│   ├── features/clinic-verification/PendingClinicsList.tsx         # EDIT - EmptyState, LoadingState only
│   ├── features/doctor-verification/PendingDoctorsList.tsx          # EDIT - EmptyState, LoadingState only
│   ├── routes/staff/ClinicToolsDashboard.tsx                        # EDIT - EmptyState, LoadingState for Today's Sessions only
│   └── routes/staff/PatientHubPage.tsx                               # EDIT - LoadingState only (already uses EmptyState)
└── tests/
    ├── day-sheet/DaySheet.test.tsx                                    # EDIT - pagination + empty/loading assertions
    └── staff-picker/StaffPicker.test.tsx                               # EDIT - sort header + empty/loading assertions
    (other 5 files' existing tests updated only if their empty/loading text queries need adjusting)
```

**Structure Decision**: Frontend-only, no new files except tests already existing for the 2 files with real behavior migrations. All edits are within already-established feature/route directories — no new directory structure.

## Complexity Tracking

*No violations — table not needed.*
