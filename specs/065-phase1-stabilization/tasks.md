# Tasks: Phase 1 Stabilization — Security Boundary and Booking Correctness

**Input**: Design documents from `specs/065-phase1-stabilization/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/api-behaviour-changes.md

**Tests**: REQUIRED. The spec mandates test-first and constitution Principle I applies. Every implementation task is preceded by a failing test.

**Working-tree safety**:
- The repository has about 835 pre-existing uncommitted changes.
- Edit only the files named below, and only the relevant section.
- No `git reset`, `clean`, `checkout`, `restore` or `stash`.
- Never run `spotlessApply` across the tree. Use `-x spotlessApply` so unrelated files are not reformatted.

**Path roots**:
- `B/` = `backend/src/main/java/com/cms/`
- `BT/` = `backend/src/test/java/com/cms/`
- `F/` = `frontend/`

## Phase 1: Setup (baseline)

- [x] T001 Record the pre-change baseline in `specs/065-phase1-stabilization/baseline.md`. Include:
  - `git status --short` counts
  - backend `test -x spotlessApply --continue` pass/fail counts (the audit's 371 pass / 249 Docker-init failures)
  - frontend `tsc`, lint and full Vitest counts
  - the backend `spotlessCheck` result

## Phase 2: Foundational (blocking for US2, US3, US4)

- [ ] T002 Write `backend/src/main/resources/db/migration/V41__session_cancellation.sql`. It creates the table, constraints and indexes exactly as in data-model.md.
- [ ] T003 [P] Create entity `B/scheduling/domain/SessionCancellationRecord.java`:
  - fields `id`, `session`, `fromTime`, `toTime`, `cancelledAt`
  - factory methods `whole(Session)` and `range(Session, LocalTime from, LocalTime to)`
  - `covers(LocalTime probe)`
- [ ] T004 [P] Create `B/scheduling/repository/SessionCancellationRecordRepository.java` with `findBySession_Id(UUID)` and `existsBySession_IdAndFromTimeIsNull(UUID)`.
- [x] T005 Write failing unit test `BT/scheduling/service/SessionAvailabilityServiceTest.java` against a fixed `Clock`. Cases:
  - past date → PAST_DATE
  - today, start before now → ELAPSED
  - start == now → ELAPSED
  - start after now → ACCEPTING
  - tomorrow → ACCEPTING
  - whole record → CANCELLED
  - range covering the slot start → CANCELLED
  - slot before the range → ACCEPTING
  - slot at `toTime` → ACCEPTING
  - untimed today, now inside the range → CANCELLED
  - untimed today, now before the range → ACCEPTING
  - untimed future date with a range → ACCEPTING
  - untimed past date → PAST_DATE
- [x] T006 Implement `B/scheduling/service/SessionAvailabilityService.java` so T005 passes:
  - `evaluate(Session, Slot)`
  - `isWholeCancelled`, `recordWholeCancellation` (`saveAndFlush`; a `DataIntegrityViolationException` surfaces to the caller), `recordRangeCancellation`
  - `now()`
  - constructors: public (`Clock.systemDefaultZone()`) and package/test (`Clock`)
- [x] T007 Create `B/booking/exception/SessionNotAcceptingBookingsException.java` and map it in `B/booking/exception/BookingExceptionHandler.java` to 409 `SESSION_NOT_ACCEPTING_BOOKINGS`.

**Checkpoint**: the availability rule is unit-proven.

## Phase 3: User Story 1 — Fail-closed security boundary (P1, MVP)

**Goal**: anonymous requests to protected paths return 401 at the boundary; public endpoints stay public; 403 behaviour is unchanged.

**Independent test**: T008 and T009 pass, and the runtime curl check in quickstart §3.

- [x] T008 [P] [US1] Write failing `BT/identity/account/contract/StaffChainFailClosedContractTest.java` (`@WebMvcTest` over the 6 affected controllers plus `ClinicRegistrationController`, with the staff `SecurityConfig` imported):
  - the 7 audit endpoints anonymous → 401
  - an unmapped `/api/v1/clinics/{id}/nonexistent` anonymous → 401
  - `POST /api/v1/clinics/register` anonymous → not 401
  - `GET /api/v1/clinics/{id}/bookings/{id}` with a valid staff token → reaches the controller (not 401)
- [x] T009 [P] [US1] Write failing `BT/patient/contract/PatientChainFailClosedContractTest.java`:
  - an unmapped `/api/v1/patients/nonexistent` anonymous → 401
  - `POST /api/v1/patients/signup` and `/login` anonymous → not 401
- [x] T010 [US1] In `B/identity/account/config/SecurityConfig.java` `filterChain`: `permitAll` `POST /api/v1/clinics/register`, then `anyRequest().authenticated()`. Remove the redundant per-path matchers and add a Javadoc note explaining the fail-closed rule.
- [x] T011 [US1] In `B/patient/account/config/SecurityConfig.java` `patientFilterChain`: `permitAll` `POST /api/v1/patients/signup` and `/login`, then `anyRequest().authenticated()`. Remove the redundant matchers.
- [x] T012 [US1] Run all existing `@WebMvcTest` contract tests and confirm no regression.

## Phase 4: User Story 2 — Cancelled session or range is unbookable (P1)

**Goal**: FR-005 to FR-008 and FR-010/FR-011.

**Independent test**: T013 to T016 pass; the integration test T025 is written.

- [x] T013 [P] [US2] Write failing `BT/booking/service/SessionCancellationServiceTest.java`:
  - booked slots cancelled, count returned, whole record written
  - the waitlist event is never published (unchanged)
- [x] T014 [P] [US2] Write failing `BT/booking/service/SessionPartialCancellationServiceTest.java`:
  - a range record is written with `cutoff` and `toTime`
  - an already whole-cancelled session → `SessionAlreadyCancelledException`
  - zero qualifying bookings still records the range
- [x] T015 [P] [US2] Write failing `BT/booking/service/BookingPathsAvailabilityTest.java` (Mockito) for `PatientBookingService.bookSlot`, `StaffBookingService.bookSlot`, `PatientQueueBookingService.bookSlot`, `StaffQueueBookingService` and `FrontDeskWalkInService.register`:
  - verdict CANCELLED → `SessionNotAcceptingBookingsException`, and no booking is saved
- [x] T016 [US2] Update `B/booking/service/SessionCancellationService.java` so T013 passes (via `SessionAvailabilityService`).
- [x] T017 [US2] Update `B/booking/service/SessionPartialCancellationService.java` so T014 passes.
- [x] T018 [US2] Add the availability guard to the 5 booking services (verdict → exception mapping per research R4) so T015 passes. Files:
  - `B/booking/service/PatientBookingService.java`
  - `B/booking/service/StaffBookingService.java`
  - `B/booking/service/PatientQueueBookingService.java`
  - `B/booking/service/StaffQueueBookingService.java`
  - `B/booking/service/FrontDeskWalkInService.java`
- [x] T019 [US2] Extend the JPQL in `B/scheduling/repository/SlotRepository.java`:
  - `findOpenFixedTimeSlots` and `findOpenFixedTimeSlotsOnDate`, including their count queries
  - add a `NOT EXISTS` covering-cancellation condition
- [x] T020 [US2] Extend `findUpcomingQueueSessionsByClinic` in `B/scheduling/repository/SessionRepository.java` with a `NOT EXISTS` condition for a whole cancellation, or a range covering `:nowTime` when `sessionDate = :from`.
- [x] T021 [US2] Update `PatientBookingService.listOpenSlots` and `listQueueSessions` to pass `nowTime` from `SessionAvailabilityService.now()`. Update the two integration callers:
  - `BT/booking/integration/WalkInLineLifecycleTest.java`
  - `BT/scheduling/integration/UntimedSlotSweepsTest.java` (new signature)

## Phase 5: User Story 3 — Empty session cancellable; honest repeat (P2)

**Independent test**: T022 passes; T024 is updated.

- [x] T022 [US3] Add failing cases to `BT/booking/service/SessionCancellationServiceTest.java`:
  - an empty session → returns 0, record written
  - already whole-cancelled → `SessionAlreadyCancelledException`, nothing written
  - only APPEARED or NO_SHOW visits → success, visits untouched
  - after a range cancellation → whole record written
- [x] T023 [US3] Adjust `SessionCancellationService` so T022 passes. Remove the "no BOOKED slots ⇒ already cancelled" rule.
- [x] T024 [US3] Update `BT/booking/integration/SessionCancellationRejectionTest.java`:
  - `sessionThatNeverHadAnyBookingIsAlsoRejected` encodes BUG-004 as expected, so replace it with "an empty session cancels with 0, then a repeat → 409"
  - keep the repeat-after-booked case
  - rationale: spec 065 reverses 029's rule

## Phase 6: User Story 4 — Elapsed times never offered or accepted (P2)

**Independent test**: T026 passes.

- [ ] T025 [P] [US2] [US3] [US4] Write integration test `BT/booking/integration/SessionAvailabilityIntegrationTest.java` (Testcontainers):
  - listing excludes whole-cancelled, range-covered and elapsed slots
  - booking on a cancelled session → 409 `SESSION_NOT_ACCEPTING_BOOKINGS`
  - a double whole-cancellation leaves exactly one record
  - the V41 CHECK rejects `to_time <= from_time`
- [x] T026 [US4] Add failing cases to `BookingPathsAvailabilityTest`:
  - patient fixed-time ELAPSED and PAST_DATE → `SlotDateInThePastException`
  - staff fixed-time ELAPSED and PAST_DATE → `SlotDateInThePastException`
  - queue and walk-in PAST_DATE → `SessionNotAcceptingBookingsException`
- [x] T027 [US4] Make T026 pass. The mapping lives in the guard added by T018. Remove `PatientBookingService`'s date-only check, which is now subsumed by the verdict.
- [x] T028 [US4] Ensure `SlotRepository` listing JPQL includes `(s.session.sessionDate > :from OR s.startTime > :nowTime)` (done alongside T019).

## Phase 7: User Story 5 — Only active doctors create clinical records (P2)

**Independent test**: T029 passes, and the existing clinical service tests still pass.

- [x] T029 [P] [US5] Write failing `BT/clinical/service/TreatingDoctorAuthorizationServiceTest.java`:
  - `requireActiveTreatingDoctor`: active Doctor role → ok; inactive → `ForbiddenException`; not the treating doctor → `ForbiddenException`
  - `requireTreatingDoctor` unchanged for reads
- [x] T030 [US5] Implement `requireActiveTreatingDoctor` in `B/clinical/service/TreatingDoctorAuthorizationService.java` (inject `RoleAssignmentRepository`).
- [x] T031 [US5] Use it in the `create` methods of `B/clinical/service/ConsultationNoteService.java`, `PrescriptionService.java` and `ExternalRecordReferenceService.java`. Update the constructor wiring in the existing unit tests (`ConsultationNoteServiceTest`, `PrescriptionServiceTest`, `ExternalRecordReferenceServiceTest`), and add one deactivated-doctor create case to each.

## Phase 8: User Story 6 — No secrets in launch config; stable frontend suite (P3)

- [x] T032 [US6] Rewrite the backend entry in `.claude/launch.json`:
  - PowerShell loads `.env` into the process environment, then runs `gradle.bat -p backend bootRun`
  - no literal values
  - verify with `preview_start backend` and a Super Admin login (values not printed)
- [x] T033 [P] [US6] In `F/tests/clinic-registration/RegistrationForm.test.tsx`, `F/tests/scheduling/ScheduleForm.test.tsx` and `F/tests/external-record-references/ExternalRecordReferenceForm.test.tsx`, use `userEvent.setup({ delay: null })`, with a comment citing BUG-006. Confirm the isolated per-test durations drop.
- [x] T034 [US6] Run the full frontend suite 3 consecutive times; all must pass (SC-006). If any other test times out, apply the same root-cause fix to that file.

## Phase 9: Polish & verification

- [ ] T035 Backend: `spotlessCheck` on the changed files, compile, and the full `test -x spotlessApply --continue`. Compare against the T001 baseline.
- [ ] T036 Frontend: `tsc -b`, `npm run lint` (no new warnings), full test run.
- [ ] T037 Restart the local backend: V41 applies and Hibernate `validate` plus JPQL parsing succeed. Run the quickstart §3 curl smoke tests.
- [ ] T038 Update `docs/product-audit/07-BUG-AND-DEFECT-REGISTER.md`, `08-SECURITY-AUDIT.md` and `10-PRODUCT-IMPROVEMENT-BACKLOG.md` with each issue's status, root cause, fix, tests and verification. Add a row for 065 to `backlog/progress.md`.

## Dependencies

- T001 comes first.
- T002–T007 block US2, US3 and US4.
- US1 (T008–T012) is independent of the Foundational phase and can run right after T001.
- US5 (T029–T031) and US6 (T032–T034) are independent.
- US3 and US4 depend on US2's service edits (T016, T018).
- Phase 9 comes last.

## Parallel opportunities

- T003 ∥ T004
- T008 ∥ T009
- T013 ∥ T014 ∥ T015
- T029 ∥ T033 ∥ T032, alongside backend work

## Implementation strategy

MVP = US1 (a security boundary defect with runtime proof). Then US2 → US3 → US4, which share the foundational rule. Then US5 and US6. Verify and document last.
