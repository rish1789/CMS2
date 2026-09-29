---

description: "Task list for Tables & Lists Consistency Pass"
---

# Tasks: Tables & Lists Consistency Pass

**Input**: Design documents from `/specs/053-tables-lists-consistency/`

**Prerequisites**: plan.md, spec.md, research.md, quickstart.md

**Tests**: Frontend-only, zero new backend logic — Principle I's NON-NEGOTIABLE test-first mandate is scoped to backend; here the existing tests for the 2 behavior-migrated files (`DaySheet.test.tsx`, `StaffPicker.test.tsx`) are the regression baseline, updated where a query targets old markup, verified to still prove the same underlying behavior.

**Organization**: US1 (P1) is the one backlog-mandated fix (DaySheet pagination). US2 (P2) is the sort-header migration (StaffPicker). US3 (P2) is the 7-file empty/loading-state sweep, independent of US1/US2's own markup changes to those same 2 files (sequenced to touch DaySheet/StaffPicker once, not twice).

## Phase 1-2: Setup / Foundational

Not needed — all 4 target components (`PaginationControls`, `SortableColumnHeader`, `EmptyState`, `LoadingState`) already exist (046).

---

## Phase 3: User Story 1 - Day Sheet paginates the same way every other list does (Priority: P1) 🎯 MVP

**Goal**: `DaySheet.tsx` uses the shared `PaginationControls` component instead of its hand-rolled footer.

**Independent Test**: Load Day Sheet with more sessions than one page and confirm pagination matches `PendingClinicsList.tsx`'s.

### Implementation for User Story 1

- [x] T001 [US1] Edit `frontend/src/features/day-sheet/DaySheet.tsx`: replace the hand-rolled "Page X of Y (Z sessions)" + Previous/Next footer (research.md Decision 2) with `<PaginationControls page={page} pageSize={pageSize} totalCount={totalCount} onPageChange={setPage} itemLabel="sessions" />`, removing the now-unused local `totalPages` computation the footer alone needed (`PaginationControls` computes it internally).
- [x] T002 [US1] Re-run `frontend/tests/day-sheet/DaySheet.test.tsx` as-is first — its existing "paging forward re-fetches the next page" test queries `getByRole('button', { name: /^next$/i })`, which `PaginationControls` also satisfies. Confirmed: passed unmodified, exactly as predicted.

**Checkpoint**: Day Sheet's pagination is visually and behaviorally identical to every other paginated list.

---

## Phase 4: User Story 2 - Staff roster sorts through the shared column-header control (Priority: P2)

**Goal**: `StaffPicker.tsx` uses `SortableColumnHeader` instead of its local `SortHeader`.

**Independent Test**: Sort the staff roster by each column and confirm the header matches `PendingClinicsList.tsx`'s sortable columns.

### Implementation for User Story 2

- [x] T003 [US2] Edit `frontend/src/features/staff-picker/StaffPicker.tsx`: replace the locally-defined `SortHeader` component's 3 usages (name/experienceYears/joinedAt columns) with the shared `SortableColumnHeader<SortField>`, removing the now-unused local component; keep the existing `sortBy`/`sortDir` state and the `listStaff` call's params unchanged (FR-002 — behavior, not just header markup, must stay the same). `currentSort` bridges the local `SortKey | null` state to `SortableColumnHeader`'s non-nullable `TField` via `sortKey ?? ('' as SortKey)` (an empty string never matches a real field, correctly rendering no column as active until the first click).
- [x] T004 [US2] Update `frontend/tests/staff-picker/StaffPicker.test.tsx`'s "filters by specialization and sorts by name" test: `SortableColumnHeader`'s button accessible name is the plain column label ("Name"), not "Sort by Name" — updated the query, and added an `aria-sort` assertion. **A real test bug was caught and fixed in the process**: the first draft cached the `<th>` reference before both clicks, but the table unmounts/remounts between fetches (the loading state swaps in) — the second click landed on a stale, detached node and silently did nothing. Fixed by re-querying `getByRole('columnheader', ...)` fresh before each click; both directions now correctly verified.

**Checkpoint**: Staff roster sorting looks and behaves like every admin verification queue's sortable columns, with unchanged underlying sort behavior.

---

## Phase 5: User Story 3 - Every migrated list shows empty/loading states the same way (Priority: P2)

**Goal**: All 7 verified files render empty/loading states via `EmptyState`/`LoadingState`.

**Independent Test**: Trigger an empty result and a loading state on each of the 7 lists and confirm both use the shared components with each list's own wording.

### Implementation for User Story 3

- [x] T005 [P] [US3] Edit `frontend/src/features/day-sheet/DaySheet.tsx`: replace the raw `<p>No sessions scheduled in the next 14 days.</p>` empty state with `<EmptyState message="No sessions scheduled in the next 14 days." />`, and the direct `<ListSkeleton rows={4} />` loading render with `<LoadingState variant="list" rows={4} />`.
- [x] T006 [P] [US3] Edit `frontend/src/features/staff-picker/StaffPicker.tsx`: same swap — `<EmptyState message="No staff match your search." />`, `<LoadingState variant="list" rows={3} />`.
- [x] T007 [P] [US3] Edit `frontend/src/features/doctor-picker/DoctorPicker.tsx`: same swap for its own existing empty/loading wording and row count (preserved the dynamic search-vs-empty-clinic message).
- [x] T008 [P] [US3] Edit `frontend/src/features/clinic-verification/PendingClinicsList.tsx`: same swap for its own existing empty/loading wording and row count.
- [x] T009 [P] [US3] Edit `frontend/src/features/doctor-verification/PendingDoctorsList.tsx`: same swap for its own existing empty/loading wording and row count.
- [x] T010 [P] [US3] Edit `frontend/src/routes/staff/ClinicToolsDashboard.tsx`: in `TodaySessionsSection` only, replace the raw `<p>No sessions scheduled today.</p>` with `<EmptyState message="No sessions scheduled today." />` and the hand-rolled `<div className="h-24 animate-pulse ...">` with `<LoadingState variant="list" rows={2} />` — the section's cap-at-10/"+N more" behavior (research.md Decision 3) stays unchanged; only its own empty/loading markup changes.
- [x] T011 [P] [US3] Edit `frontend/src/routes/staff/PatientHubPage.tsx`: it already uses `EmptyState` (no change needed there) — replaced its 2 direct `<ListSkeleton />` loading renders with `<LoadingState variant="list" />`.
- [x] T012 [US3] Re-run every test file for the 7 touched components — confirmed: all pass, and (aside from T004's deliberate update) zero test changes were needed, since every touched empty/loading message's text is unchanged, only its wrapper component.

**Checkpoint**: All 3 user stories complete; every list named in this feature shows consistent empty/loading presentation.

---

## Phase 6: Polish

- [x] T013 `cd frontend && npx tsc -b` — zero type errors. Confirmed.
- [x] T014 `cd frontend && npm run lint` — zero new lint errors. Confirmed: 0 errors; only pre-existing warnings already present on these same files before this feature.
- [x] T015 `cd frontend && npm run test -- --run` — full suite, zero regression to actual list behavior. Confirmed: 275/275 passing (unchanged count — this feature added 0 net new tests, only fixed T004's query pattern).
- [x] T016 Manual live verification per `quickstart.md` steps 5-8. **Verified live** via the dev server with mocked multi-page/multi-row responses: Day Sheet's pagination footer now renders and behaves exactly like `PaginationControls` elsewhere (clicked "Next", confirmed it re-fetched page 2 and correctly re-triggered the single-doctor merged-tab view); the Staff roster's "Name" column now renders the shared sortable-header arrow indicator and pagination footer, matching the same visual pattern.
- [x] T017 Update `backlog/progress.md`'s row for `050-tables-lists-consistency-pass`.

---

## Dependencies & Execution Order

- US1 (T001-T002) and US2 (T003-T004) touch different files (`DaySheet.tsx` vs. `StaffPicker.tsx`) — independent, but US3's own edits to those same 2 files (T005/T006) are sequenced after US1/US2's edits to avoid touching either file twice in unrelated passes.
- US3 (T005-T012) can start after US1/US2's edits to `DaySheet.tsx`/`StaffPicker.tsx` land (same-file sequencing) — its other 5 files (T007-T011) have no dependency on US1/US2 at all and could run in parallel with them.
- Polish depends on all 3 stories complete.

## Notes

- Total: 17 tasks.
- Zero backend tasks — this feature touches no backend code (plan.md Technical Context, Constitution Principle III: N/A).
- No task builds a new `Table` component or adds sort/pagination capability beyond what already exists — both explicitly out of scope (research.md Decisions 1/3, spec.md FR-005/FR-006/FR-007).
