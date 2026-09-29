# Implementation Plan: Shared UI Component Library

**Branch**: `049-shared-ui-components` | **Date**: 2026-09-15 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/049-shared-ui-components/spec.md`

## Summary

Build `Modal` (a headless native-`<dialog>` shell) + `ModalHeader`, `Toast`/`ToastProvider`/`useToast`, `Button`, `Input`, `Select`, `Card`, `Badge`, `EmptyState`, `LoadingState` in `frontend/src/components/`, all from `DESIGN.md`'s existing tokens. Migrate all 3 real dialog consumers onto `Modal`; wire `Toast` into 2 of them (delete/reject success); migrate `AdminDashboard.tsx`/`PatientDashboard.tsx`'s tile grids onto `Card`/`Button`. `PaginationControls`/`SortableColumnHeader`/`FilterSelect` (already existing, already table-support primitives) are recognized as satisfying FR-003's "Table" item — no redundant new Table component built here; full table consistency is 050's job.

## Technical Context

**Language/Version**: TypeScript / React 18 / Vite / Tailwind v4 (unchanged).

**Primary Dependencies**: None new — native `<dialog>` (already used), React context (built-in) for Toast, no portal/modal/toast library added, per Constitution Principle II and this codebase's zero-UI-library convention.

**Storage**: N/A.

**Testing**: Vitest + Testing Library (existing). New component tests for `Modal`/`Toast`/`Button`/etc.; existing tests for the 3 migrated dialogs and 2 migrated dashboards re-run to confirm zero regression.

**Target Platform**: Browser (unchanged).

**Project Type**: Existing web application; adds `frontend/src/components/{Modal,Toast,Button,Input,Select,Card,Badge,EmptyState,LoadingState}.tsx` and edits 5 existing files (3 dialogs + 2 dashboards).

**Performance Goals**: N/A.

**Constraints**: No new color/radius/shadow/spacing/type-scale value beyond `DESIGN.md`; WCAG 2.1 AA; respects `prefers-reduced-motion`; zero regression to the 3 dialogs' existing accessibility-hardened behavior (Part 3).

**Scale/Scope**: 10 new component files, 5 edited existing files (3 modals + 2 dashboards), ~2-4 new test files.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Principle I (Test-First)**: New component tests written for `Modal`/`Toast` (real new behavior); the 3 migrated dialogs' and 2 migrated dashboards' *existing* tests are the regression baseline, re-run unmodified where possible.
- **Principle II (Simplicity & YAGNI)**: PASS — no new dependency; `Modal` built as a minimal headless shell rather than a rigid header/body/footer layout specifically because `EmployeeModal`'s real layout (side tabs) doesn't fit that rigid shape — the abstraction matches the real, current variety of usage, not a speculative one-size-fits-all; `Table` explicitly not rebuilt since `PaginationControls` already exists and works.
- **Principle III (Modular Architecture)**: N/A on the backend; on the frontend, `components/` as the existing shared-UI location is reused, not a new seam invented.
- **Principle IV (Data Privacy & Integrity)**: N/A.

No violations. No Complexity Tracking entries needed.

## Project Structure

### Documentation (this feature)

```text
specs/049-shared-ui-components/
├── plan.md
├── research.md
├── data-model.md    # N/A — no data model
├── quickstart.md
└── tasks.md
```

No `contracts/` — internal component API, not a service/network contract.

### Source Code (repository root)

```text
frontend/src/components/
├── Modal.tsx              # NEW — headless <dialog> shell
├── ModalHeader.tsx         # NEW — title + close button sub-component
├── Toast.tsx                # NEW — ToastProvider + useToast + Toast
├── Button.tsx               # NEW — primary/secondary/destructive variants
├── Input.tsx                 # NEW — thin wrapper formalizing the existing `.input` class + label
├── Select.tsx                # NEW — same pattern as Input
├── Card.tsx                  # NEW
├── Badge.tsx                  # NEW (distinct from existing RoleBadge, which stays as-is — a specific badge use)
├── EmptyState.tsx             # NEW
└── LoadingState.tsx           # NEW — consolidates ad hoc "Loading…" text + reuses ListSkeleton where it already fits

frontend/src/components/DeleteConfirmModal.tsx      # EDITED — onto Modal + Toast
frontend/src/components/RejectConfirmModal.tsx       # EDITED — onto Modal + Toast
frontend/src/features/staff-picker/EmployeeModal.tsx  # EDITED — onto Modal
frontend/src/routes/admin/AdminDashboard.tsx          # EDITED — tile grid onto Card/Button
frontend/src/routes/patient/PatientDashboard.tsx       # EDITED — tile grid onto Card/Button
```

**Structure Decision**: All new components live in the existing `frontend/src/components/` shared-UI directory — no new top-level seam. `Modal` is deliberately a minimal shell (not a rigid header/body/footer template) so `EmployeeModal`'s genuinely different layout (side tabs) can adopt it without being forced into a bad fit.

## Complexity Tracking

No Constitution Check violations — this section is not applicable.
