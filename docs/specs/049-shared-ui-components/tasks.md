---

description: "Task list for Shared UI Component Library"
---

# Tasks: Shared UI Component Library

**Input**: Design documents from `/specs/049-shared-ui-components/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md (N/A), quickstart.md

**Tests**: New tests for `Modal`/`Toast` (real new behavior). The 3 migrated dialogs' and 2 migrated dashboards' existing tests are the regression baseline.

**Organization**: US1 (Modal) first as MVP since it's the most concrete, lowest-risk win; US2 (Toast) next; US3 (Button/Input/Select/Card/Badge/EmptyState/LoadingState + migrations) last since it depends on nothing from US1/US2 but benefits from them existing (Toast wiring in US1's own migrated dialogs).

## Phase 1-2: Setup / Foundational

Not needed — `frontend/src/components/` already exists.

---

## Phase 3: User Story 1 - One Modal component (Priority: P1) 🎯 MVP

- [x] T001 [US1] Create `frontend/src/components/Modal.tsx`: headless `<dialog>` shell (research.md Decision 1) — `showModal()` on mount, backdrop-click-to-close, `onClose` passthrough, `ariaLabel`/`className`/`children` props.
- [x] T002 [US1] Create `frontend/src/components/ModalHeader.tsx`: title + close-button sub-component, matching the exact markup duplicated in `DeleteConfirmModal`/`RejectConfirmModal`.
- [x] T003 [US1] Add `frontend/tests/components/Modal.test.tsx`: confirm backdrop-click-to-dismiss, Escape-to-close (via the existing jsdom polyfill from Part 3), and that `onClose` fires.
- [x] T004 [US1] Migrate `frontend/src/components/DeleteConfirmModal.tsx` onto `Modal`+`ModalHeader`, removing its own duplicated dialog-mechanics code.
- [x] T005 [US1] Migrate `frontend/src/components/RejectConfirmModal.tsx` onto `Modal`+`ModalHeader`.
- [x] T006 [US1] Migrate `frontend/src/features/staff-picker/EmployeeModal.tsx` onto `Modal` (shell only — its own side-tab header/body stays custom, per research.md Decision 1).
- [x] T007 [US1] Run the 3 migrated components' existing test files — confirm they pass unmodified (or with only import-path changes, no behavior-assertion changes). **Finding**: no dedicated test files exist for `DeleteConfirmModal`/`RejectConfirmModal`/`EmployeeModal` themselves; ran the real regression baseline instead — the parent-feature tests that render them (`PendingClinicsList.test.tsx`, `PendingDoctorsList.test.tsx`, `StaffPicker.test.tsx`). This surfaced a real gap: those two verification-queue test files rendered their component without a `ToastProvider` ancestor, so once T011 wired `useToast()` into `DeleteConfirmModal`/`RejectConfirmModal`, 8 tests broke (`useToast must be used within a ToastProvider`). Fixed by wrapping both files' `renderWithSession()` helper in `ToastProvider` (test-setup fix, no production or assertion changes) — all 40 tests across the 5 files now pass.

**Checkpoint**: All 3 real dialog consumers share one `Modal` shell; existing accessibility behavior (Part 3) unregressed.

---

## Phase 4: User Story 2 - Toast system (Priority: P1)

- [x] T008 [US2] Create `frontend/src/components/Toast.tsx`: `ToastProvider`, `useToast()` hook, `Toast` visual component (research.md Decision 2) — fixed-position stack, 4s auto-dismiss, individually dismissible, respects `prefers-reduced-motion`.
- [x] T009 [US2] Wrap `App.tsx` in `ToastProvider`.
- [x] T010 [US2] Add `frontend/tests/components/Toast.test.tsx`: shows on `showToast`, auto-dismisses, dismissible manually, doesn't block page interaction.
- [x] T011 [US2] Wire `useToast()` into `DeleteConfirmModal.tsx`'s and `RejectConfirmModal.tsx`'s success paths (after `onSubmit` succeeds, before closing) — proof-of-concept on real screens.

**Checkpoint**: A working, demonstrated Toast system exists, distinct from the existing inline `role="alert"` pattern.

---

## Phase 5: User Story 3 - Button/Input/Select/Card/Badge/EmptyState/LoadingState (Priority: P2)

- [x] T012 [P] [US3] Create `frontend/src/components/Button.tsx` (primary/secondary/destructive variants, research.md Decision 3).
- [x] T013 [P] [US3] Create `frontend/src/components/Input.tsx` and `frontend/src/components/Select.tsx` (thin wrappers around the existing `.input` class + optional label).
- [x] T014 [P] [US3] Create `frontend/src/components/Card.tsx` (research.md Decision 4).
- [x] T015 [P] [US3] Create `frontend/src/components/Badge.tsx`.
- [x] T016 [P] [US3] Create `frontend/src/components/EmptyState.tsx` (message + optional action).
- [x] T017 [P] [US3] Create `frontend/src/components/LoadingState.tsx` (wraps existing `ListSkeleton` for list contexts; a simple centered variant for the ad hoc "Loading…" text cases).
- [x] T018 [US3] Migrate `frontend/src/routes/admin/AdminDashboard.tsx`'s tile grid onto `Card`/`Button` (research.md Decision 5). Note: the tile's own markup was `rounded-lg`+`hover:border-indigo-300`+focus-visible ring (interactive-link states, not part of the plain-content `Card` treatment) — kept those on the wrapping `<Link>`/via `className` passthrough on `Card`, and accepted `Card`'s `rounded-xl`/`active:scale-[0.98]` in place of the tile's prior `rounded-lg`/`active:scale-[0.99]` as the intended consolidation onto one convention (research.md Decision 4).
- [x] T019 [US3] Migrate `frontend/src/routes/patient/PatientDashboard.tsx`'s tile grid onto `Card`/`Button` (same pattern as T018 — this file's tile markup was byte-identical to `AdminDashboard.tsx`'s).
- [x] T020 [US3] Run both dashboards' existing test files — confirm they pass unmodified. **Finding**: no test files exist for `AdminDashboard.tsx`/`PatientDashboard.tsx` at all (not a regression — pre-existing gap). Substituted the full suite (`npm run test -- --run`, 251/251 passing) plus live browser verification (see T024) as the correctness gate for these 2 files.

**Checkpoint**: 8 primitives exist, matching `DESIGN.md` exactly; 2 real screens demonstrate `Card`/`Button` with identical visual output to before.

---

## Phase 6: Polish

- [x] T021 `npx tsc -b` — zero type errors. Confirmed clean.
- [x] T022 `npm run lint` — zero new errors. Confirmed: 0 errors; only pre-existing warnings across the codebase (none in files this feature touched, except `Toast.tsx`'s pre-existing `react(only-export-components)` warning from T008, unchanged today).
- [x] T023 `npm run test -- --run` — full suite, zero regression, count increases by new component tests. Confirmed: 251/251 passing across 49 files (up from the pre-feature baseline; includes 4 new `Modal.test.tsx` + 3 new `Toast.test.tsx` cases).
- [x] T024 Manual live verification per `quickstart.md` steps 5-7. Step 7 (Admin/Patient dashboard tile grids) verified live via the dev server + Browser pane: both render correctly on `Card`, hover border-color and shadow-lift confirmed by hovering a tile, no console errors beyond expected backend-connection-refused (no backend running in this sandbox pass). Steps 5-6 (Reject-flow-toast, `EmployeeModal` side-tab layout) were **not** re-verified live — no backend/Postgres was started this pass — and were instead confirmed via the equivalent automated coverage that already renders and interacts with the real components: `PendingClinicsList.test.tsx`/`PendingDoctorsList.test.tsx` (reject flow + toast-on-success, post the T007 `ToastProvider` fix) and `StaffPicker.test.tsx` (`EmployeeModal` open, Info/Actions tab switch, deactivate flow, close) — all passing. Documented here rather than silently treated as equivalent.
- [x] T025 Update `backlog/progress.md`'s row for `046-shared-ui-component-library`.

---

## Dependencies & Execution Order

- US1 (Modal) and US2 (Toast) are independent of each other but US2's proof-of-concept (T011) wires into US1's already-migrated dialogs — so US1 (T001-T007) before US2's T011, though T008-T010 (building Toast itself) could run in parallel with US1.
- US3 is fully independent of US1/US2 (different files) — T012-T017 can run in parallel with each other.
- Polish depends on all 3 stories complete.

## Notes

- Total: 25 tasks.
- `Table` (named in FR-003) is deliberately not rebuilt — `PaginationControls`/`SortableColumnHeader`/`FilterSelect` already exist and serve this need (research.md, plan.md Summary); no task creates a redundant new Table component.
