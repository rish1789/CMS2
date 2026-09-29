---
description: "Task list for Day Sheet Hardening"
---

# Tasks: Day Sheet Hardening

**Input**: Design documents from `specs/042-day-sheet-hardening/` (plan.md, spec.md, research.md, data-model.md, contracts/day-sheet-hardening.md, quickstart.md)

**Tests**: Included per this project's Constitution Principle I (Test-First Development, NON-NEGOTIABLE for behavioral changes) — matches every prior feature in this codebase's own task history. Backend Testcontainers-based integration tests are written and must compile per this project's documented sandbox limitation; execution is deferred to a real dev/CI environment.

**Organization**: Tasks are grouped by user story (from spec.md) to enable independent implementation and testing of each.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1–US5)

## Path Conventions

Web app: `backend/src/main/java/...`, `backend/src/test/java/...`, `frontend/src/...`, `frontend/tests/...` — matches this repository's existing layout (see plan.md Project Structure).

---

## Phase 1: Setup

No setup tasks required. This feature reuses the existing project structure, dependencies, and test frameworks with nothing new to initialize (see plan.md Technical Context).

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Infrastructure underlying the feature as a whole, independent of any single user story.

- [X] T001 Add Flyway migration `backend/src/main/resources/db/migration/V27__session_clinic_date_index.sql` creating `idx_session_clinic_id_session_date ON session (clinic_id, session_date)` (research.md R8, data-model.md)

**Checkpoint**: Migration in place — user story work can begin.

---

## Phase 3: User Story 1 - Confirm before cancelling a whole session (Priority: P1) 🎯 MVP

**Goal**: A single click can never complete a whole-session cancellation — an explicit confirm step is required first.

**Independent Test**: Click "Cancel whole session," verify a confirmation step appears with nothing yet cancelled, click Cancel and verify no change, repeat and click Confirm and verify cancellation proceeds exactly as before.

### Tests for User Story 1

- [X] T002 [P] [US1] Update `frontend/tests/session-cancellation/CancelSessionButton.test.tsx`: assert clicking "Cancel whole session" shows a confirmation step before any API call; assert clicking Cancel in that step makes no API call; assert clicking Confirm then calls the existing cancel endpoint exactly as before

### Implementation for User Story 1

- [X] T003 [US1] Add `phase` state (`idle` → `confirming` → `submitting` → `done`) to `frontend/src/features/session-cancellation/CancelSessionButton.tsx`, matching the existing Cancel/Confirm pattern used elsewhere in this codebase (research.md R6) — no backend change, contract unchanged (contracts/day-sheet-hardening.md "Not a contract change")

**Checkpoint**: User Story 1 fully functional and testable independently — no backend dependency.

---

## Phase 4: User Story 2 - Find one doctor's sessions in a busy, multi-doctor clinic (Priority: P1)

**Goal**: The session list is paginated server-side and filterable to one doctor, with a complete, page-independent doctor list to drive the filter.

**Independent Test**: Load the Day Sheet for a clinic with sessions from multiple doctors, filter to one, verify only that doctor's sessions remain and the count reflects only them; verify the initial page load does not download every session in the window.

### Tests for User Story 2

- [X] T004 [P] [US2] Update `backend/src/test/java/com/cms/scheduling/integration/ClinicSessionListControllerTest.java`: add cases for `page`/`size` pagination (correct slice, correct `totalCount`), `doctorProfileId` filtering (only that doctor's sessions returned), and the `doctors` list (complete across all pages, respects Doctor self-scoping — contains at most the caller's own entry for a Doctor-only caller). **Also add a self-scoping regression case on the main `sessions` results specifically (FR-006)**: a caller whose only active role at the clinic is Doctor, passing a *different* doctor's `doctorProfileId`, MUST get zero sessions back — not that other doctor's sessions — under the new pagination/filter params, not just on the `doctors` side-list.
- [X] T005 [P] [US2] Update `frontend/tests/day-sheet/DaySheet.test.tsx`: assert the doctor filter renders from the response's `doctors` field (not derived from the currently-loaded page); assert selecting a doctor re-fetches with `doctorProfileId`; assert paging forward re-fetches the next page rather than reusing already-fetched data

### Implementation for User Story 2

- [X] T006 [US2] In `backend/src/main/java/com/cms/scheduling/SessionRepository.java`: change the existing clinic+date-range query to accept `Pageable` and return `Page<Session>`; add a `doctorProfileId`-filtered variant; add a query for the distinct doctors (id, name) with ≥1 session in a given clinic+date range (research.md R1/R2)
- [X] T007 [US2] Update `backend/src/main/java/com/cms/scheduling/dto/SessionListResponse.java` and `SessionSummaryResponse.java` to add `doctors`, `page`, `pageSize`, `totalCount` per contracts/day-sheet-hardening.md (leave `bookedSlotCount`/`totalSlotCount` for US5 — do not add them here)
- [X] T008 [US2] Update `backend/src/main/java/com/cms/scheduling/ClinicSessionListController.java`: accept `page`/`size`/`doctorProfileId` query params (all optional, defaults per contracts/day-sheet-hardening.md), pass through to the repository, preserve existing Doctor self-scoping filtering (FR-006) applied consistently with the new params
- [X] T009 [US2] Update `frontend/src/features/day-sheet/api.ts`: extend `SessionSummary`/list response types for `doctors`/`page`/`pageSize`/`totalCount`, add `page`/`size`/`doctorProfileId` params to `listSessions`
- [X] T010 [US2] Update `frontend/src/features/day-sheet/DaySheet.tsx`: replace the client-side `useClientPagination`/"Show more" pattern with real pagination driven by the API's `page`/`pageSize`/`totalCount`, add the doctor filter `<select>` sourced from the response's `doctors` field (mirroring the `FilterSelect` pattern already established on the Roster page)

**Checkpoint**: User Stories 1 AND 2 both work independently. US2 has no dependency on US1.

---

## Phase 5: User Story 3 - Predictable "More actions" menu on every slot (Priority: P2)

**Goal**: Only one `ActionMenu` popup can be open at a time, and it always renders fully within the viewport.

**Independent Test**: Open the "More" menu on one slot, then a different slot, verify the first closes; open the menu on the last visible slot in a long list, verify it's fully visible without scrolling.

### Tests for User Story 3

- [X] T011 [P] [US3] Create `frontend/tests/components/ActionMenu.test.tsx`: assert both instances in a shared group carry the same `name` attribute; assert the popup is rendered via portal (present in `document.body` outside the component's own DOM subtree). **Note, discovered while implementing (widens the previously-known gap)**: jsdom implements neither `getBoundingClientRect()` sizing (FR-009 boundary-flip) *nor* `<details name="...">` cross-instance exclusivity itself (FR-008) — confirmed empirically, not assumed (a same-named second `<details>` opening does not close the first in this suite, unlike a real browser). Both behaviors are unit-testable only up to "wired correctly" (same `name`, portal used); the actual behaviors are covered by quickstart.md Scenario 4 (manual, real-browser verification) instead.

### Implementation for User Story 3

- [X] T012 [US3] Update `frontend/src/components/ActionMenu.tsx`: apply the proven Roster fix (research.md R4) — add a `name` prop for native `<details name="...">` cross-instance exclusivity, and render the popup via `createPortal` to `document.body` with `position: fixed` computed from the trigger's `getBoundingClientRect()`, flipping upward when there isn't room below (same `MENU_HEIGHT_ESTIMATE_PX`-style approach already proven on Roster's `RowActionsMenu`)
- [X] T013 [US3] Update `frontend/src/features/day-sheet/SessionSlotsView.tsx`: pass a shared `name` (e.g. per-session, so menus across different slots in the same session exclude each other) to each `ActionMenu` instance in `SlotRow`

**Checkpoint**: User Stories 1–3 all work independently.

---

## Phase 6: User Story 4 - Session detail page survives a refresh or direct link (Priority: P2)

**Goal**: The session detail page always shows the correct doctor name and date, sourced from the API response, not navigation state.

**Independent Test**: Open a session detail URL directly (no prior click-through), verify doctor name and date are correct; refresh, verify they remain correct.

### Tests for User Story 4

- [X] T014 [P] [US4] Update `backend/src/test/java/com/cms/booking/integration/SessionDaySheetControllerTest.java`: assert the response includes `doctorName` and `sessionDate` matching the underlying Session/DoctorProfile/Account
- [X] T015 [P] [US4] Update `frontend/tests/day-sheet/SessionSlotsView.test.tsx`: render the component with no navigation `state` (simulating a direct URL visit) and assert the header still shows the correct doctor name/date, sourced from the mocked API response

### Implementation for User Story 4

- [X] T016 [US4] Update `backend/src/main/java/com/cms/booking/dto/SessionDaySheetResponse.java` to add `doctorName`/`sessionDate`, populated in `SessionDaySheetController.java` from the already-loaded `Session`/`DoctorProfile`/`Account` (research.md R5 — zero new queries)
- [X] T017 [US4] Update `frontend/src/features/day-sheet/api.ts`: add `doctorName`/`sessionDate` to the `SessionDaySheet` type
- [X] T018 [US4] Update `frontend/src/features/day-sheet/SessionSlotsView.tsx`: read `doctorName`/`sessionDate` from the fetched `daySheet` response instead of (or as a fallback-free replacement for) `location.state`

**Checkpoint**: User Stories 1–4 all work independently.

---

## Phase 7: User Story 5 - Visual consistency and at-a-glance session load (Priority: P3)

**Goal**: The Day Sheet matches Roster's visual language, and each session shows its booked/total slot count.

**Independent Test**: Compare Day Sheet and Roster side by side for visual consistency; compare a session card's booked/total indicator against that session's own detail page.

### Tests for User Story 5

- [X] T019 [P] [US5] Update `backend/src/test/java/com/cms/scheduling/integration/ClinicSessionListControllerTest.java`: assert `bookedSlotCount`/`totalSlotCount` per session match the actual Slot/active-Booking data, including a session with zero generated slots
- [X] T020 [P] [US5] Update `frontend/tests/day-sheet/DaySheet.test.tsx`: assert each session card shows the booked/total indicator from the API response, and that a zero-slot session shows an explicit "no slots yet" state rather than "0 of 0"

### Implementation for User Story 5

- [X] T021 [US5] Add a bulk booked/total slot count query (keyed by session id, one query for the current page's sessions — no N+1) in `backend/src/main/java/com/cms/scheduling/SlotRepository.java` (total count per session) and `backend/src/main/java/com/cms/booking/BookingRepository.java` (active-booking count per session) — these aggregate Slot/Booking data respectively, not `Session` itself (research.md R3)
- [X] T022 [US5] Update `backend/src/main/java/com/cms/scheduling/dto/SessionSummaryResponse.java` and `ClinicSessionListController.java` to populate `bookedSlotCount`/`totalSlotCount` per contracts/day-sheet-hardening.md
- [X] T023 [US5] Update `frontend/src/features/day-sheet/api.ts` and `DaySheet.tsx` to render the booked/total badge per session card (explicit "no slots yet" when `totalSlotCount` is 0)
- [X] T024 [US5] Restyle `frontend/src/features/day-sheet/DaySheet.tsx`, `SessionSlotsView.tsx`, and `frontend/src/components/ActionMenu.tsx` to Roster's established visual language (`slate-*` palette, gradient initial-avatars, `rounded-lg`/`rounded-xl` cards with `shadow-sm`, `indigo-600` accents, hover transitions — research.md R7)
- [X] T025 [US5] In `SessionSlotsView.tsx`, visually separate the two destructive session-level actions (Cancel from a cutoff, Cancel whole session) from the routine "Insert a walk-in" action into their own sub-section, as part of the same restyle (FR-003)

**Checkpoint**: All five user stories independently functional.

---

## Phase 8: Polish & Cross-Cutting Concerns

- [ ] T026 [P] Run `quickstart.md` Scenarios 1–7 manually against a running backend+frontend
- [X] T027 Run full backend build (`compileJava`, `compileTestJava`, `spotlessApply`) and confirm zero regressions in unrelated modules
- [X] T028 Run full frontend suite (`vitest run`) and `tsc -b`, confirm zero regressions
- [ ] T029 Verify `idx_session_clinic_id_session_date` (T001) is actually used by the session list query — run `EXPLAIN ANALYZE` for the query behind `GET /api/v1/clinics/{clinicId}/sessions` against a real Postgres instance (dev/CI, not this sandbox) and confirm an index scan on `idx_session_clinic_id_session_date`, not a sequential scan on `session`. Closes the verification gap for FR-007 that no automated test covers.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: none (empty)
- **Foundational (Phase 2)**: no dependencies — can start immediately; does not block any user story's *functional* correctness (only its query performance), but should land first since it's a one-line, risk-free migration
- **User Stories (Phase 3–7)**: independent of each other and of Foundational's actual completion, except:
  - US2 (T006–T010) and US5 (T021–T024) both touch `SessionSummaryResponse.java`/`SessionListResponse.java`/`ClinicSessionListController.java` — sequence US2 before US5 (matches priority order P1 before P3) to avoid the same files being edited by unrelated work at the same time
  - US3 (T012) and US5 (T024) both touch `ActionMenu.tsx` — sequence US3 (the bug fix) before US5 (the restyle) so the restyle is applied to the already-fixed component, not the reverse
- **Polish (Phase 8)**: depends on all desired user stories being complete

### Parallel Opportunities

- T004/T005 (US2 tests, different files) in parallel
- T014/T015 (US4 tests, different files) in parallel
- T019/T020 (US5 tests, different files) in parallel
- US1 (Phase 3) and US4 (Phase 6) touch entirely disjoint files from each other and could be built in parallel by different people
- US1 (frontend-only) has zero backend dependency and can be delivered as a standalone increment before anything else

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 2 (T001, one-line migration)
2. Phase 3 / US1 (T002–T003) — a real, shippable safety fix on its own
3. **STOP and VALIDATE**: quickstart.md Scenario 1
4. Deploy/demo if ready — this alone closes the most severe (safety) finding

### Incremental Delivery

1. Foundational → US1 (MVP: safety fixed) → US2 (scale fixed, the second P1) → US3 (bug parity with Roster) → US4 (data-gap closed) → US5 (visual polish + orientation)
2. Each story is independently testable per its own Independent Test above and does not regress any earlier story
