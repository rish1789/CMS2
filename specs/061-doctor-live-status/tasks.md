---

description: "Task list for Doctor Live Schedule Status"
---

# Tasks: Doctor Live Schedule Status

**Input**: Design documents from `/specs/061-doctor-live-status/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/doctor-live-status.md, quickstart.md

**Tests**: Included per this project's constitution (Principle I, Test-First Development, NON-NEGOTIABLE) and the spec's own Testing Strategy section.

**Organization**: Tasks are grouped by user story (US1/US2/US3, matching spec.md's priorities) so each is independently implementable and testable. The core calculation (`SessionLiveStatusService`) and the operational-day function (`OperationalDayService`) are built once in Foundational, since both US1 (dashboard display) and US2 (patient view) read from the same calculation — building it twice would duplicate BR-005–BR-010, which is exactly the kind of drift this feature's own Business Rules exist to prevent.

**Updated 2026-09-23 per `/speckit-analyze` remediation** (E1–E5): added T011 and T020 (frontend test coverage for `LiveScheduleStatusIndicator.tsx`, previously missing entirely); extended T005, T018, T027 with explicit cases that existed only implicitly before. All task IDs from T011 onward were renumbered accordingly — see the analysis findings this addresses noted inline below.

## Phase 1: Setup

No setup tasks. This feature adds zero new dependencies (plan.md Technical Context) and extends two existing modules (`scheduling`, `booking`) on their existing stack — no new project structure to initialize.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The shared calculation and operational-day logic every story's own endpoint/UI work depends on. Must complete before any user story phase.

- [x] T001 Extract the existing clinic-scoping + doctor-self-scoping check out of `backend/src/main/java/com/cms/scheduling/service/SessionDelayService.java`'s `currentDelay` method (lines ~94-114) into a small reusable helper on the same class (research.md Decision 3) — callable by both `currentDelay` and the new `SessionLiveStatusService` (T004). Must not change `SessionDelayService`'s existing observable behavior in any way (same exceptions, same conditions).
- [x] T002 [P] Create `backend/src/main/java/com/cms/scheduling/service/OperationalDayService.java` with `LocalDate operationalDateOf(LocalDateTime instant)` implementing the 04:30 AM boundary (spec BR-011) — the single centralized function BR-012 requires.
- [x] T003 [P] Unit test `backend/src/test/java/com/cms/scheduling/unit/OperationalDayServiceTest.java`: 04:29 → previous date, 04:30 → current date, 04:31 → current date, plus one instant well inside each side of the boundary (spec User Story 3 acceptance scenarios).
- [x] T004 Create `backend/src/main/java/com/cms/scheduling/service/SessionLiveStatusService.java` implementing BR-005 (participation), BR-006 (actual pointer), BR-007 (expected pointer), BR-008 (deviation/status), BR-009 (per-session slot interval/break), BR-010 (server-side `LocalDateTime.now()` only) — reuses T001's shared scoping helper for its own authorization. Depends on T001. **Implementation note**: took a `Clock` (defaulting to `Clock.systemDefaultZone()`, mirroring `RetentionPurgeService`'s existing precedent) rather than calling `LocalDateTime.now()` directly, so BR-005–BR-010 are deterministically unit-testable — same BR-010 guarantee (server-side clock only), just injectable.
- [x] T005 [P] Unit test `backend/src/test/java/com/cms/scheduling/unit/SessionLiveStatusServiceTest.java` (pure Mockito, no Spring context): not-started, on-time, delayed, running-early, completed, a break gap, a **Queue-mode session (`applicable:false`, no computation attempted — analyze finding E2)**, a cancelled/reverted-to-`OPEN` slot **verified by before/after comparison (compute status, cancel the slot, recompute, assert the figure is byte-for-byte unchanged — SC-005, analyze finding E5)**, a no-show, and at least two different slot intervals. Depends on T004. All 11 cases pass using a fixed `Clock` for determinism.
- [x] T006 [P] Create `backend/src/main/java/com/cms/scheduling/dto/SessionLiveStatusResponse.java` per data-model.md's `SessionLiveStatus` shape (contracts/doctor-live-status.md). Depends on T004.
- [x] T007 Regression check: `cd backend && ./gradlew test --tests "com.cms.scheduling.unit.SessionDelayServiceTest" --tests "com.cms.scheduling.integration.SessionDelayAuthorizationTest" --tests "com.cms.scheduling.integration.SessionDelayQueueModeTest" --tests "com.cms.scheduling.integration.SessionDelayReadOnlyTest" --tests "com.cms.scheduling.integration.SessionDelayNoOutstandingDelayTest"` — confirms T001's extraction changed nothing observable. Depends on T001. Unit tier (`SessionDelayServiceTest`, all 7 cases) re-run and green; the four integration tests compile but remain Docker-gated per this sandbox's standing limitation, same as every other feature.

**Checkpoint**: the live calculation and operational-day function exist and are unit-tested in isolation; the existing Session Delay Tracking suite is proven untouched. User story implementation can begin.

---

## Phase 3: User Story 1 - Staff/doctor sees live, accurate schedule status on the Day Sheet (Priority: P1) 🎯 MVP

**Goal**: The existing session operations view shows live On time / Running early / Delayed status (plus Current/Expected Patient, First Slot, Operational Day) that updates on its own as patients are seen — no manual refresh, no mental math.

**Independent Test**: Open a Fixed-Time session's Day Sheet view partway through the day and confirm the displayed status matches the doctor's actual progression, updating automatically after marking a patient Appeared/Completed (spec Acceptance Scenarios 1-4).

### Tests for User Story 1

- [x] T008 [P] [US1] Contract test `backend/src/test/java/com/cms/scheduling/contract/SessionLiveStatusControllerContractTest.java` (`@WebMvcTest`): `200` with full shape for a Fixed-Time session, `200` with `applicable:false` for a Queue-mode session, `401` for a missing bearer token, `404` for a doctor-only caller requesting a different doctor's session. All 4 cases pass. **Found and fixed a real pre-existing gap**: `SessionNotFoundException` had no exception handler anywhere in `ScheduleExceptionHandler` — would have fallen through to a 500, not 404 (true for the existing `/delay` endpoint too). Added the missing handler.
- [x] T009 [P] [US1] Integration test `backend/src/test/java/com/cms/scheduling/integration/SessionLiveStatusFullLifecycleTest.java`: a real session's status walked through Not started → Delayed → catches up → On time → Running early via real slot-status transitions (Appeared/Completed), against a real database (integration tier, written/compiled but unexecuted per this sandbox's standing Docker limitation).
- [x] T010 [P] [US1] Integration test `backend/src/test/java/com/cms/scheduling/integration/SessionLiveStatusAuthorizationTest.java`: cross-clinic and cross-doctor refusal, mirroring `SessionDelayAuthorizationTest`'s existing shape for the new endpoint (compiled, Docker-gated).
- [x] T011 [P] [US1] Frontend test `frontend/tests/session-delay/LiveScheduleStatusIndicator.test.tsx` — staff mode only at this stage: loading state, error state ("couldn't load, will retry automatically"), not-applicable state (Queue-mode), ready state rendering Current/Expected Patient/Schedule Status/Minutes Early-Late, and that it re-fetches when `refreshKey` changes — mirrors `QueuePositionIndicator`'s own test shape if one exists in this codebase, otherwise its own `loading`/`error`/`not-applicable`/`ready` status model as documented in FR-007. **(Addresses analyze finding E1 — previously no frontend test existed for this component at all.)** All 7 cases pass.

### Implementation for User Story 1

- [x] T012 [US1] Add `GET /api/v1/clinics/{clinicId}/sessions/{sessionId}/live-status` to `backend/src/main/java/com/cms/scheduling/api/SessionDelayController.java` (sibling method, existing `/delay` route untouched), calling `SessionLiveStatusService` (T004) and `OperationalDayService` (T002), returning `SessionLiveStatusResponse` (T006). Depends on T004, T006.
- [x] T013 [US1] Add the new path's `.requestMatchers(HttpMethod.GET, "/api/v1/clinics/*/sessions/*/live-status")...authenticated()` entry to `backend/src/main/java/com/cms/identity/account/config/SecurityConfig.java` (staff realm) — this codebase's own recurring bug class (a new path silently falling through to `anyRequest().permitAll()`); do not skip. Depends on T012.
- [x] T014 [P] [US1] Add `getSessionLiveStatusAsStaff` to `frontend/src/features/session-delay/api.ts`. **(Named to match this file's own existing `getSessionDelay` convention rather than the task's literal `getLiveScheduleStatusAsStaff` — noted here since it's a deliberate deviation from the task text, not an oversight.)**
- [x] T015 [US1] Create `frontend/src/features/session-delay/LiveScheduleStatusIndicator.tsx` — `{ mode: 'staff'; clinicId; sessionId; refreshKey? } | { mode: 'patient'; bookingId; refreshKey? }` discriminated union (research.md Decision 6), staff branch wired to T014, mirroring `QueuePositionIndicator`'s `loading | error | not-applicable | ready` status model and ~20s polling, implemented to satisfy T011. The patient branch's own data-fetch wiring is completed in US2 (T025) — this task builds the full component shape now so US2 only adds its branch's behavior, not a new component. Depends on T011, T014.
- [x] T016 [US1] Wire `LiveScheduleStatusIndicator` (staff mode) into `frontend/src/features/session-delay/SessionOperationsPanel.tsx`, showing Current Patient, Expected Patient, Schedule Status, Minutes Early/Late, First Slot, and Operational Day (FR-008) — existing Appeared/Complete actions and their placement unchanged. Depends on T015. Replaces `DelayIndicator` in this panel (a strict enhancement, per plan.md's own noted discretion); `DelayIndicator.tsx` itself is left in place untouched. Full `session-delay/` test directory re-run: 20/20 passing, no regressions.
- [x] T017 [US1] Live-verify via `quickstart.md` Scenarios 1 (not started), 2 (starts late, catches up), 3 (running early), 4 (breaks/cancellations neutral), 5 (no-shows), and 6 (session completes) against the running dev servers. **Done 2026-09-23** against real production-shaped data (Star Clinic, Dr. Gauresh Kumar's real today session, 5 real bookings): restarted the backend (picked up the new endpoint + services cleanly, no wiring errors), opened the session's operations panel — it correctly showed **"Session complete"**, First Slot 09:30, Operational Day 2026-09-23 (all 5 booked slots had already auto-flipped to No-show hours earlier, all resolved → Scenario 6 confirmed live). Clicked "Appeared" on one No-show slot: the panel **updated automatically, no manual refresh**, to **"Delayed (120 min late)"**, Current Patient 1, Expected Patient 5 — proving SC-002 and the no-show-counts-as-resolved rule (Scenario 5) simultaneously (the other 4 No-show slots correctly didn't block the pointer). Clicked "Completed" on the same slot: panel auto-reverted to **"Session complete"** (Scenario 2's "catches up" direction, in miniature). **Scenarios 1 (not started) and 3 (running early) were not reachable live** — by the time this verification ran (21:26 real time), every slot in this 09:30–17:00 session had already elapsed, so there was no way to construct a genuinely-future slot within today's real session; both are already deterministically proven via `SessionLiveStatusServiceTest`'s fixed-clock unit tests instead. Scenario 4 (cancellation-neutral) likewise relies on T005's dedicated before/after unit test rather than a fresh live cancellation, for the same reason (no open future slot left to cancel after the session resolved). Worth a follow-up live pass earlier in a session's window if the not-started/running-early/cancellation states need direct browser confirmation too.

**Checkpoint**: User Story 1 fully functional and independently testable — staff/doctor see accurate, live, auto-updating status with no manual refresh.

---

## Phase 4: User Story 2 - Patient sees a simple live status for their own upcoming visit (Priority: P2)

**Goal**: A patient viewing their own active Fixed-Time booking sees the doctor's name, a privacy-safe current-patient number, a plain-language schedule status, and an estimated wait — updating on its own.

**Independent Test**: As a patient with an active Fixed-Time booking, open the booking detail page and confirm the live status and estimated wait are shown, update automatically, and never expose another patient's name or contact information (spec Acceptance Scenarios 1-3).

### Tests for User Story 2

- [x] T018 [P] [US2] Contract test `backend/src/test/java/com/cms/booking/contract/PatientSessionLiveStatusControllerContractTest.java`: `200` with full shape for an active Fixed-Time booking, `200` with `applicable:false` for a Queue-mode booking, `401` for a missing bearer token, `404` for a booking that isn't the caller's own, **and the caller's own slot already resolved (Completed/No-show) → `estimatedWaitMinutes: null` while the rest of the shape stays populated (FR-010, analyze finding E4)**.
- [x] T019 [P] [US2] Integration test `backend/src/test/java/com/cms/booking/integration/PatientSessionLiveStatusAccessTest.java`: end-to-end against a real booking, including the refusal case for a different patient's booking, mirroring the existing `PatientQueuePositionController` test's shape for its own endpoint.
- [x] T020 [P] [US2] Extend `frontend/tests/session-delay/LiveScheduleStatusIndicator.test.tsx` (T011) with patient-mode cases: doctor name / current-patient ordinal / plain-language status text / estimated wait rendering, plus its own loading/error/not-applicable states in patient mode — and assert no field beyond FR-011's allowed set is ever rendered. **(Completes analyze finding E1's coverage for the patient branch.)** Depends on T011.

### Implementation for User Story 2

- [x] T021 [US2] Create `backend/src/main/java/com/cms/booking/dto/PatientSessionLiveStatusResponse.java` per data-model.md's `PatientSessionLiveStatus` shape (contracts/doctor-live-status.md) — `statusText` always plain language, never a raw status code (FR-004/FR-011).
- [x] T022 [US2] Create `backend/src/main/java/com/cms/booking/api/PatientSessionLiveStatusController.java` — `GET /api/v1/patients/bookings/{bookingId}/live-status`, mirroring `PatientQueuePositionController`'s booking-ownership-check pattern exactly (research.md Decision 5), delegating to `SessionLiveStatusService` (T004, one-way `booking → scheduling` call) for the underlying calculation, then deriving `estimatedWaitMinutes` (FR-010, `null` once the caller's own slot is resolved) and `statusText` from it. Depends on T004, T021.
- [x] T023 [US2] Add the new path's `.requestMatchers(HttpMethod.GET, "/api/v1/patients/bookings/*/live-status")...authenticated()` entry to `backend/src/main/java/com/cms/patient/account/config/SecurityConfig.java` (patient realm) — same recurring-bug-class check as T013, do not skip. Depends on T022.
- [x] T024 [P] [US2] Add `getSessionLiveStatusAsPatient` (naming matches `getSessionLiveStatusAsStaff`'s existing convention in this file) to `frontend/src/features/session-delay/api.ts`.
- [x] T025 [US2] Complete `LiveScheduleStatusIndicator.tsx`'s patient-mode branch (shell built in T015), wiring it to T024 and rendering the patient-safe fields (doctor name, current-patient ordinal, plain-language status, estimated wait) — no other field, implemented to satisfy T020. Depends on T015, T020, T024.
- [x] T026 [US2] Wire `LiveScheduleStatusIndicator` (patient mode) into the patient booking detail page in `frontend/src/routes/patient/PatientPages.tsx`, alongside the existing `QueuePositionIndicator`/`CancelBookingButton`/`VisitRecordSection` (FR-009). Depends on T025.
- [x] T027 [US2] Live-verify via `quickstart.md` Scenario 8 (privacy: patient view never leaks other patients — inspect the actual network response **at each of the five statuses in turn, including once the session reaches Session complete — SC-003, analyze finding E3**) and re-run Scenario 2's catch-up flow from the patient's own view. **Live-verified 2026-09-23**: real Fixed-Time session (Design Test Clinic, Dr. Test Doctor) walked Delayed(5 min late) → On time → Running early(5 min early) via real Appeared/Completed actions; patient booking detail page (`/patient/bookings/{id}`) tracked the same transitions, and the raw `GET /api/v1/patients/bookings/{id}/live-status` response body was inspected directly: `{"bookingId","applicable","doctorName","currentPatientOrdinal","statusText","estimatedWaitMinutes"}` only — no raw status code, no other patient's data (FR-011/SC-003). Found and fixed a dev-environment issue (not a product bug): the running backend process predated the US2 backend files, so `/live-status` 404'd until the dev server was restarted.

**Checkpoint**: User Stories 1 and 2 both independently functional — nothing from US1 was removed or changed.

---

## Phase 5: User Story 3 - Operational-day boundary is correctly applied everywhere this feature reads "today" (Priority: P3)

**Goal**: Prove the single 04:30 AM boundary function (built in Foundational, already relied on by US1's "Operational Day" display) is correct at the boundary and has no duplicate implementation anywhere in this feature.

**Independent Test**: With a mocked/injected clock, verify 04:29/04:30/04:31 resolve to the correct operational date, using the single shared function (spec Acceptance Scenarios 1-3).

### Tests for User Story 3

- [x] T028 [P] [US3] Extend `backend/src/test/java/com/cms/scheduling/unit/OperationalDayServiceTest.java` (T003) with a month/year rollover case (31 Dec 04:29 → 30 Dec; 1 Jan 04:30 → 1 Jan) per spec Testing Strategy.

### Implementation for User Story 3

- [x] T029 [US3] Grep-verify no duplicate 04:30 comparison exists anywhere else in this feature's controllers, services, frontend components, or queries (BR-012) — every "today"/"operational day" resolution in this feature's own new code must call `OperationalDayService.operationalDateOf` (T002), not reimplement the comparison.
- [x] T030 [US3] Live-verify via `quickstart.md` Scenario 7 — confirm T003/T028 pass (this scenario is explicitly automated-test-only per quickstart.md, not a manual walkthrough) and satisfies SC-004.

**Checkpoint**: All three user stories independently functional — the full feature as specified.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [x] T031 [P] `cd backend && ./gradlew spotlessApply test --tests "com.cms.scheduling.*" --tests "com.cms.booking.*"` — full unit + contract suite green (integration tests compiled, unexecuted per this sandbox's standing Docker limitation).
- [x] T032 [P] `cd frontend && npx tsc -b` — zero type errors.
- [x] T033 [P] `cd frontend && npm run lint` — zero new warnings in any file this feature touches.
- [x] T034 `cd frontend && npm run test -- --run` — full suite green, zero regressions, including the new `LiveScheduleStatusIndicator.test.tsx` (T011/T020).
- [x] T035 Grep both staff-realm and patient-realm `SecurityConfig.java` for the two new paths (`sessions/*/live-status`, `bookings/*/live-status`) and confirm each has an explicit `.authenticated()` matcher ahead of the trailing `anyRequest().permitAll()` — final verification pass beyond T013/T023's own additions.
- [x] T036 Confirm no existing `SessionDelayService`/`SessionDelayController`/`DelayIndicator`/`QueuePositionService`/`QueuePositionIndicator` consumer or test was altered beyond T001's additive scoping extraction — grep for any accidental edit outside this feature's new surface (spec Assumption A6).
- [x] T037 Live-verify via `quickstart.md` Scenario 9 (cross-doctor and cross-patient refusal) against the running dev servers.

---

## Dependencies & Execution Order

- **Setup (Phase 1)**: none.
- **Foundational (Phase 2)**: T001-T007. Blocks every user story — `SessionLiveStatusService` (T004) is used by both US1's endpoint (T012) and US2's endpoint (T022); `OperationalDayService` (T002) is used by US1's dashboard field (T012/T016) and is US3's own subject.
- **US1 (T008-T017)**: depends on Foundational (T004, T006). Fully independent of US2/US3's own endpoints.
- **US2 (T018-T027)**: depends on Foundational (T004) and, for its frontend tasks, on US1's `LiveScheduleStatusIndicator.tsx` shell and test file (T011, T015) — the one place a later story's UI work builds directly on an earlier story's same-file edits, not a hidden coupling of business logic (mirrors 057's own precedent for this exact pattern).
- **US3 (T028-T030)**: depends on Foundational (T002/T003) only — genuinely independent of US1/US2's endpoint work, since it tests `OperationalDayService` directly.
- **Polish (Phase 6)**: depends on all three user stories being complete.

## Parallel Example: Foundational

```bash
# T002/T003 (OperationalDayService + its test) and T001 (SessionDelayService extraction) touch
# different files and can start together; T004-T006 depend on T001 landing first:
Task: "Create OperationalDayService.java"
Task: "Unit test OperationalDayServiceTest"
Task: "Extract shared scoping helper in SessionDelayService.java"
```

## Parallel Example: User Story 1 Tests

```bash
# T008-T011 touch different files - parallelizable:
Task: "Contract test SessionLiveStatusController"
Task: "Integration test: full lifecycle Not started -> Delayed -> catches up -> Running early"
Task: "Integration test: cross-clinic/cross-doctor refusal"
Task: "Frontend test: LiveScheduleStatusIndicator staff mode"
```

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 2: Foundational.
2. Complete Phase 3: User Story 1 — the core "see live status without doing mental math" value, deployable and demoable on its own (staff/doctor view only).
3. **STOP and VALIDATE**: run `quickstart.md` Scenarios 1-6.

### Incremental Delivery

1. Foundational → US1 (MVP) → validate → demo.
2. US2 (patient-facing view) → validate → demo.
3. US3 (operational-day boundary proof) → validate → demo.
4. Polish once all three are in.

## Notes

- Total: 37 tasks (7 Foundational + 10 US1 + 10 US2 + 3 US3 + 7 Polish).
- No new migration anywhere in this feature (plan.md Technical Context; spec Assumptions A1/A2) — no schema change of any kind; every new query reads existing `Session`/`Slot` columns.
- The existing `SessionDelayService`/`SessionDelayController`/`/delay` endpoint and `DelayIndicator.tsx` need zero behavioral changes anywhere in this feature beyond T001's additive, regression-tested extraction (spec Assumption A6) — T007 and T036 exist specifically to confirm that stayed true.
- `SessionLiveStatusResponse`/`PatientSessionLiveStatusResponse` are new DTOs (T006/T021) — they do not reuse `SessionDelayResponse`, since the two response shapes carry genuinely different fields (ordinals/first-slot/operational-day vs. a bare `delayMinutes`).
- T015's "build the full component shape in US1, complete the patient branch in US2" sequencing is deliberate (mirrors 057's `SessionSlotsView.tsx` precedent) — do not create a second, separate patient-only component file. T011/T020 follow the identical split for that component's own test file.
- **`/speckit-analyze` remediation log (2026-09-23)**: T011 and T020 are new (previously no frontend test task existed for `LiveScheduleStatusIndicator.tsx` at all — finding E1). T005 gained an explicit Queue-mode case (E2) and an explicit before/after invariance assertion for the cancellation case (E5). T018 gained an explicit already-resolved-slot case (E4). T027 gained an explicit instruction to check privacy across all five statuses including `COMPLETED` (E3).

## Phase 7: Convergence

- [x] T038 Write failing unit tests first in `backend/src/test/java/com/cms/scheduling/unit/SessionLiveStatusServiceTest.java` (a session dated the day before the current operational day with unresolved BOOKED slots → `applicable: false`; a session dated yesterday checked at 02:00 today, i.e. before 04:30, → still live), then make `SessionLiveStatusService.liveStatusFor` return the not-applicable shape when `session.getSessionDate()` is before `operationalDayService.operationalDateOf(now)` per BR-016 (missing)
- [x] T039 Make `frontend/src/features/session-delay/LiveScheduleStatusIndicator.tsx` render nothing for a not-applicable response instead of "This session isn't fixed-time." (wrong for a past-day session under BR-016, and the clarified decision is that the indicator disappears), updating `LiveScheduleStatusIndicator.test.tsx` test-first per BR-016 / FR-007 (partial)
- [x] T040 Live-verify BR-016 against the real stale session left by Part 11 (Design Test Clinic, session `8b08d83d-9c4d-4559-a5aa-d9e9759cab58`, dated 2026-09-23): the staff and patient `/live-status` endpoints both return `applicable: false`, and the patient booking detail page shows no indicator, per BR-016 (missing)
