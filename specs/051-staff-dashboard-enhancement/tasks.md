---

description: "Task list for Staff Operational Dashboard Enhancement"
---

# Tasks: Staff Operational Dashboard Enhancement

**Input**: Design documents from `/specs/051-staff-dashboard-enhancement/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: Backend (US2) is genuinely new business logic — Constitution Principle I (Test-First) applies: tests are written before/alongside implementation. Frontend `ClinicToolsDashboard.test.tsx` already exists (5 tests) and is the regression baseline for US1/US3 — one existing assertion legitimately needs updating (not "pass unmodified") since US1 changes `listSessions`' requested page size.

**Organization**: US1 (P1, Today's sessions list) is frontend-only, zero new backend — the MVP. US2 (P2, Today's stats) is the one full-stack story: new repository method + DTO + controller + tests, then the frontend tile consuming it. US3 (P3, restyle) is a pure visual pass over the 3 existing tiles, no behavior change.

## Phase 1-2: Setup / Foundational

Not needed — no shared infrastructure blocks either story; `SlotRepository`/`ClinicToolsDashboard.tsx` already exist.

---

## Phase 3: User Story 1 - See today's actual schedule at a glance (Priority: P1) 🎯 MVP

**Goal**: A "Today's sessions" list appears on the dashboard, each row linking into Day Sheet.

**Independent Test**: Load the dashboard for a clinic with real sessions today and confirm the list shows doctor/time/mode/slot-fill per session, linking into Day Sheet.

### Implementation for User Story 1

- [x] T001 [US1] Edit `frontend/src/routes/staff/ClinicToolsDashboard.tsx`: change the existing `listSessions(clinicId, session.token, { from: today, to: today, page: 0, size: 1 })` call to a real page size (`MAX_SESSIONS_SHOWN = 10`), store the returned `.sessions` array (not just `.totalCount`), and keep computing `todaySessionCount` from `.totalCount` (unchanged, still feeds the existing Day sheet tile's badge).
- [x] T002 [US1] Add a "Today's sessions" section to `ClinicToolsDashboard.tsx`: one row per session (doctor name, time range via a small local formatter — a 3-line pure function matching `DaySheet.tsx`'s own private `formatSessionTimeRange`, not exported/reused, to avoid coupling two unrelated route components over a trivial formatter; mode shown via 046's `Badge`, not a hand-rolled class string; `bookedSlotCount`/`totalSlotCount`), ordered by start time (already the backend's default order), each row linking to `/staff/clinics/${clinicId}/day-sheet/${sessionId}`. A clear empty state when `sessions.length === 0`. If `totalCount > sessions.length`, a light "+N more in Day Sheet" note linking to the full Day Sheet list.
- [x] T003 [US1] Update `frontend/tests/staff/ClinicToolsDashboard.test.tsx`'s existing `listSessions` call-argument assertion (currently expects `size: 1`) to the new real size — a deliberate, stated update (US1 changes this behavior), not a silent regression.
- [x] T004 [US1] Add new test cases to `ClinicToolsDashboard.test.tsx`: renders each session's doctor/time/slot-fill and links into Day Sheet; shows the empty state at zero sessions; shows the "+N more" note when `totalCount` exceeds the shown rows. 3 new tests, all passing (8/8 in the file at this point).

**Checkpoint**: Today's sessions are visible and actionable directly from the dashboard.

---

## Phase 4: User Story 2 - See today's completed/no-show counts (Priority: P2)

**Goal**: A "Today's stats" tile shows real completed/no-show counts, backed by one new minimal backend query.

**Independent Test**: For a clinic with a mix of completed/no-show/open slots today, confirm the tile's counts match a direct query against today's slots.

### Tests for User Story 2 (write first — Constitution Principle I)

> Backend integration tests can't execute in this sandbox (established Testcontainers/Docker limitation) — written and reviewed same as every prior backend feature, verified they compile.

- [x] T005 [P] [US2] Add `backend/src/test/java/com/cms/scheduling/unit/TodaySessionStatsControllerTest.java` (Mockito, no Spring context): caller with an active role gets counts folded correctly from mocked `SlotRepository.SlotStatusCount` rows (including the zero-count-for-missing-group case); caller with no active role at the clinic gets `NotStaffedAtClinicException`. 3 tests.
- [x] T006 [P] [US2] Add `backend/src/test/java/com/cms/scheduling/contract/TodaySessionStatsControllerContractTest.java` (`@WebMvcTest`, mocked repositories — no service layer exists, per research.md Decision 2): `200` with the correct JSON shape on success; `403` on `NotStaffedAtClinicException`; `401` on a missing bearer token (matches contracts/today-session-stats.md). **This third test caught a real bug before it shipped**: the endpoint initially had no `SecurityConfig` matcher at all, so it fell through to `anyRequest().permitAll()` — publicly accessible with no authentication in production. Fixed in `SecurityConfig.java` (see T009's note). 3 tests, all passing after the fix.

### Implementation for User Story 2

- [x] T007 [US2] Edit `backend/src/main/java/com/cms/scheduling/repository/SlotRepository.java`: add `countStatusByClinicAndDate(UUID clinicId, LocalDate date)` returning `List<SlotStatusCount>` (research.md Decision 2 — grouped `COUNT ... GROUP BY status`, mirroring `countBySessionIdIn`'s existing shape) and the `SlotStatusCount` projection interface (`getStatus()`, `getCount()`).
- [x] T008 [US2] Create `backend/src/main/java/com/cms/scheduling/dto/TodaySessionStatsResponse.java`: `record TodaySessionStatsResponse(int completedCount, int noShowCount)` with a static `from(List<SlotRepository.SlotStatusCount>)` folding COMPLETED/NO_SHOW rows into the 2 counts, defaulting to 0 (data-model.md).
- [x] T009 [US2] Create `backend/src/main/java/com/cms/scheduling/api/TodaySessionStatsController.java`: `GET /api/v1/clinics/{clinicId}/sessions/today-stats`, reusing `ClinicSessionListController`'s exact authorization gate (`roleAssignmentRepository.findByAccount_IdAndClinic_IdAndActiveTrue`, throw `NotStaffedAtClinicException` if empty), calling `slotRepository.countStatusByClinicAndDate(clinicId, LocalDate.now())`, returning `TodaySessionStatsResponse.from(...)` (contracts/today-session-stats.md). **Real bug found and fixed** (caught by T006's own test, not spotted during writing): added `.requestMatchers(HttpMethod.GET, "/api/v1/clinics/*/sessions/today-stats").authenticated()` to `SecurityConfig.java`'s `/api/v1/clinics/**` chain, right after the sibling `/sessions` list endpoint's own matcher — without it, this endpoint would have fallen through to that chain's `anyRequest().permitAll()` and been publicly reachable with no authentication at all.
- [x] T010 [US2] Add `getTodayStats(clinicId, token)` to `frontend/src/features/day-sheet/api.ts`: `fetch` + existing `DaySheetApiError` handling shape, returning the new `TodaySessionStats` type (data-model.md).
- [x] T011 [US2] Add a "Today's stats" tile to `ClinicToolsDashboard.tsx`: fetches `getTodayStats` independently (same silent-fail-per-tile pattern as the 3 existing tiles), shows `completedCount`/`noShowCount`.
- [x] T012 [US2] Add test cases to `ClinicToolsDashboard.test.tsx`: renders real completed/no-show counts; a failed stats fetch never blocks the other tiles (matches the existing "one failing count never blocks the others" test's established pattern). 2 new tests, all passing (10/10 in the file at this point).

**Checkpoint**: Today's completed/no-show counts are real, visible, and independently verified backend-to-frontend.

---

## Phase 5: User Story 3 - Existing live tiles keep working, visually upgraded (Priority: P3)

**Goal**: The 3 pre-existing tiles and 6 quick-action links are visually restyled onto 046's `Card`/`Badge`, with zero behavior change.

**Independent Test**: Confirm all 3 existing tiles still show the same real counts, now styled via `Card`/`Badge`; confirm all 6 quick-action links still work.

### Implementation for User Story 3

- [x] T013 [US3] Edit `ClinicToolsDashboard.tsx`: replace the hand-rolled tile `<Link>` className strings and the local `CountBadge` component with 046's `Card` (wrapped in `Link`, matching 046's own `AdminDashboard.tsx`/`PatientDashboard.tsx` migration pattern) and `Badge` — visual only, no route/data-fetching change.
- [x] T014 [US3] Re-run `ClinicToolsDashboard.test.tsx` in full (all tests from T003/T004/T012 plus the 5 pre-existing ones) — confirm all pass with the restyled markup (tests query by role/text, not CSS classes, so a pure visual change shouldn't require test changes; if any test breaks, that's a real regression to fix, not a test to loosen). Confirmed: 10/10 passing, zero test changes needed.

**Checkpoint**: All 3 user stories complete; dashboard is real-data-only, visually consistent with the rest of the redesigned app.

---

## Phase 6: Polish

- [x] T015 `cd backend && gradle compileJava compileTestJava spotlessCheck` — clean compile, zero new formatting violations. Confirmed.
- [x] T016 `cd backend && gradle test --tests "*TodaySessionStats*"` — new unit + contract tests pass. Confirmed: 6/6 passing. Also ran the full backend suite: 320 tests, 226 failing — verified every single failure carries the established Docker/Testcontainers signature (`grep`-checked every failing result XML), zero non-Docker failures, zero regressions.
- [x] T017 `cd frontend && npx tsc -b && npm run lint` — zero type errors, zero new lint errors. Confirmed.
- [x] T018 `cd frontend && npm run test -- --run` — full suite, zero regression, count increases by the new test cases. Confirmed: 268/268 passing (263 baseline + 5 new dashboard tests).
- [x] T019 Manual live verification per `quickstart.md` steps 7-12. **Verified live against the real backend + real Postgres** (not mocked, unlike 046/047's substitutions — Docker's daemon still isn't running in this sandbox, but Postgres itself was directly reachable, so the full real stack could run): registered a fresh clinic through the real registration flow, signed in as its ClinicAdmin, and loaded the clinic dashboard — confirmed the "Today's sessions" empty state, the "Today's stats" tile showing real `0`/`0` (a genuine zero from a genuine query, not a fabricated placeholder), the Day sheet tile's real "0 sessions today" badge, and all 6 quick-action tiles rendering on `Card`. Confirmed via network inspection that `GET .../sessions/today-stats` returned a real `200` from the live backend. Non-zero completed/no-show counts (step 9) were not additionally verified live — that would require standing up a full session-generation → booking → completion lifecycle — and instead rely on T005/T006's passing tests, which already exercise that exact counting logic against realistic non-zero data.
- [x] T020 Update `backlog/progress.md`'s row for `048-staff-operational-dashboard`.

---

## Dependencies & Execution Order

- US1 (T001-T004) has no dependency on US2/US3 — the MVP.
- US2 (T005-T012) is independent of US1 (different section of the same file) but its tests (T005/T006) MUST be written before its implementation (T007-T009), per Constitution Principle I.
- US3 (T013-T014) touches the same file as US1/US2's additions — sequenced last so the restyle wraps the final markup once, not twice.
- Polish depends on all 3 stories complete.

## Notes

- Total: 20 tasks.
- First full-stack feature in this design-system wave (046/047 were frontend-only) — backend scope is deliberately minimal: 1 repository method, 1 DTO, 1 controller, reusing an existing authorization gate verbatim.
- "Walk-ins today" and any "Recent Activity" widget have zero tasks — explicitly not built (research.md Decisions 4-5, spec.md Edge Cases).
