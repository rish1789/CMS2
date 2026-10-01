# Tasks: Patient Visit Outcomes and Valid Actions (069)

**Input**: [spec.md](spec.md), [plan.md](plan.md), [contracts/patient-booking-outcome-api.md](contracts/patient-booking-outcome-api.md)

Tests come first in every phase (Constitution I). `[P]` means the task can run in parallel.

## Phase 1: Setup

- [ ] T001 Confirm the branch is based on current `main`, the backend compiles, and the frontend gates pass.

## Phase 2: Foundational (derivation)

- [ ] T002 [P] Write `backend/src/test/java/com/cms/booking/unit/PatientVisitOutcomesTest.java`. It is a plain unit test (no Spring context) with a fixed clock. Cover:
  - every row of the outcome table: cancelled; completed; no-show; checked-in today; scheduled today with its time already passed; scheduled in the future; past BOOKED; past APPEARED; legacy OPEN queue token;
  - every eligibility row, plus the cutoff boundary at exactly 2h (allowed) and 2h minus 1 minute (refused);
  - the display order: a past no-show reports `VISIT_RESOLVED`, not `CUTOFF_PASSED`.

  Expect RED.
- [ ] T003 Implement `VisitOutcome`, `PatientCancellationRefusal`, the `CancellationEligibility` record and `PatientVisitOutcomes` (spec decision tables). T002 should then be GREEN.

## Phase 3: US1 + US2 + US3 backend

- [ ] T004 Write `backend/src/test/java/com/cms/booking/integration/PatientBookingOutcomeTest.java`. Expect RED. It covers:
  - the list returns `visitOutcome` and `cancellation` for synthetic no-show, completed, cancelled, today-BOOKED, future and past-BOOKED bookings;
  - the new detail endpoint returns 200 to the owner and 404 to another patient;
  - live status for an own no-show in a fully resolved session today says "Missed appointment" and never "Visit complete";
  - an own COMPLETED booking says "Visit complete";
  - the cancel endpoint still refuses a cancelled booking and a queue booking with the existing errors.
- [ ] T005 Add the fields to `PatientBookingSummaryResponse`. Map them in `PatientMyBookingsController`, and add `GET /api/v1/patients/bookings/{bookingId}` (owner-only).
- [ ] T006 Add `visitOutcome` to `PatientSessionLiveStatusResponse`, and use outcome text for terminal outcomes in `PatientSessionLiveStatusController`.
- [ ] T007 Make `PatientBookingCancellationController` use `PatientVisitOutcomes.requestRefusal`, mapped to the same exceptions in the same order. Run the existing cancellation tests unchanged.
- [ ] T008 Re-run T004 (GREEN), plus every `*Cancellation*`, `*LiveStatus*` and `PatientBooking*` test.

## Phase 4: Frontend

- [ ] T009 [P] Write Vitest tests. Expect RED:
  - `NextAppointmentCard` picks the earliest upcoming booking, ignores no-show, completed, cancelled and not-recorded bookings, keeps today's elapsed BOOKED booking, and orders timed before untimed;
  - `MyBookings` shows the outcome labels;
  - the new `PatientBookingDetail` test checks the outcome header, that the cancel button appears only when allowed, the reason text for each refusal, and that a stale server refusal error is shown.
- [ ] T010 Update `api.ts`, add `visitOutcome.ts`, and update `NextAppointmentCard`, `MyBookings` and `PatientBookingDetailPage`. T009 should then be GREEN.
- [ ] T011 Run `npx tsc -b`, `npm run lint` and `npx vitest run` (all GREEN).

## Phase 5: Verification and docs

- [ ] T012 Run the full backend suite with `spotlessCheck`.
- [ ] T013 Runtime check on a fresh database with synthetic data: a no-show today in a resolved session, compared across the staff day sheet, the patient list, the detail page and the dashboard. No real patient data.
- [ ] T014 Docs:
  - set the live-audit findings 1–3 status;
  - update the plan's 2R.1 status;
  - add a `backlog/progress.md` row;
  - add a HANDOFF part.
- [ ] T015 Check off tasks with their evidence, check `git status`, commit, push and open the PR.
