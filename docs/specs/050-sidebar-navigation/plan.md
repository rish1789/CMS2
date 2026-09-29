# Implementation Plan: Application Shell Sidebar Navigation

**Branch**: `050-sidebar-navigation` | **Date**: 2026-09-15 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/050-sidebar-navigation/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

Add a persistent, role-filtered sidebar to the Staff (`ClinicShell`, nested inside `StaffShell`) and Admin (`AdminShell`) app shells, built on a new `Sidebar`/`SidebarDrawer` component pair (react-router `NavLink` for active-state, zero new dependency) reusing 046's design tokens and existing icon set. `PatientShell` is untouched. Below the responsive breakpoint, both shells' sidebars collapse to the same hamburger-triggered off-canvas drawer. Role-based item visibility reuses `ClinicShell`'s existing `listMyClinics` call (no new fetch) filtered against one verified backend restriction (`Onboard staff` is ClinicAdmin-only). Existing dashboard tile grids (`ClinicToolsDashboard`, `AdminDashboard`) are left unchanged.

## Technical Context

**Language/Version**: TypeScript 5 / React 18 (existing frontend stack, no change)

**Primary Dependencies**: react-router-dom (already a dependency; `NavLink` used for active-state — no new package)

**Storage**: N/A — reuses existing `sessionStorage`-backed staff session (`loadStaffSession`) and the existing `listMyClinics` REST call; no new persistence

**Testing**: Vitest + React Testing Library (existing frontend convention, `frontend/tests/`)

**Target Platform**: Web (existing Vite/React SPA), responsive down to ~400px per this project's established standard

**Project Type**: Web application (frontend-only feature — no backend change)

**Performance Goals**: No new goal beyond existing SPA responsiveness; sidebar renders from data already in memory (route list is static, role comes from an already-in-flight fetch) with no added network round-trip

**Constraints**: Zero new visual tokens (reuse `DESIGN.md`/046 exactly); zero change to backend authorization (FR-010); zero change to any existing page's `<Outlet/>` content (FR-009)

**Scale/Scope**: 2 shells (`ClinicShell` 8 nav items, `AdminShell` 4 nav items); `PatientShell` unchanged (0 items)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Principle I (Test-First)**: Applies to `Sidebar`'s genuinely new behavior — active-item highlighting, role-based filtering, and the responsive drawer's open/close/focus behavior. New tests are written for these before/alongside implementation (`Sidebar.test.tsx`, `SidebarDrawer.test.tsx`), plus regression coverage on `ClinicShell`/`AdminShell` confirming existing breadcrumb/header behavior is unregressed. PASS.
- **Principle II (Simplicity & YAGNI)**: PASS — no new dependency (`NavLink` is already-present react-router-dom); the drawer is a plain fixed-position panel, not a new modal-library dependency; role-filtering reuses data already fetched by `ClinicShell` rather than adding a new endpoint or a speculative permissions table (research.md Decision 4); only one item (`Onboard staff`) is actually role-gated, matching the one restriction verified against real backend code — no defensive over-gating.
- **Principle III (Modular, Library-First Architecture)**: N/A — frontend-only feature, no backend module touched, no cross-module event added.
- **Principle IV (Data Privacy & Integrity by Design)**: PASS — no new data introduced or stored; role-based visibility is a read of already-authorized session/membership data, and FR-010 keeps the backend the sole authority on what any role can actually do regardless of sidebar display.

No violations — Complexity Tracking table not needed.

## Project Structure

### Documentation (this feature)

```text
specs/050-sidebar-navigation/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md         # Phase 1 output (/speckit-plan command)
├── quickstart.md         # Phase 1 output (/speckit-plan command)
└── tasks.md              # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

No `contracts/` — this feature exposes no new API; it's frontend-only, reusing an existing endpoint's already-fetched response.

### Source Code (repository root)

```text
frontend/
├── src/
│   ├── components/
│   │   ├── Sidebar.tsx           # NEW - nav list, NavLink active-state, role filtering
│   │   └── SidebarDrawer.tsx     # NEW - off-canvas responsive collapse wrapper
│   └── routes/
│       ├── staff/
│       │   ├── StaffShell.tsx    # unchanged
│       │   └── ClinicShell.tsx   # EDIT - adds Sidebar (staff's 8 items), passes resolved role
│       └── admin/
│           └── AdminShell.tsx    # EDIT - adds Sidebar (admin's 4 items)
└── tests/
    └── components/
        ├── Sidebar.test.tsx       # NEW
        └── SidebarDrawer.test.tsx # NEW
```

**Structure Decision**: Frontend-only change within the existing `frontend/src/components/` (cross-feature shared UI, same location as 046's primitives) and `frontend/src/routes/{staff,admin}/` (existing shell locations). No backend directory touched — matches Technical Context (no backend dependency) and Constitution Principle III's N/A above.

## Complexity Tracking

*No violations — table not needed.*
