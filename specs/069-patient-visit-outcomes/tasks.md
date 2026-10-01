# Tasks: Patient Visit Outcomes and Valid Actions (069)

**Input**: [spec.md](spec.md), [plan.md](plan.md), [contracts/patient-booking-outcome-api.md](contracts/patient-booking-outcome-api.md)

Tests come first in every phase (Constitution I). `[P]` means the task can run in parallel.

## Phase 1: Setup

- [X] T001 Confirm the branch is based on current `main`, the backend compiles, and the frontend gates pass.

## Phase 2: Foundational (derivation)

- [X] T002 [P] Write `backend/src/test/java/com/cms/booking/unit/PatientVisitOutcomesTest.java`. It is a plain unit test (no Spring context) with a fixed clock. Cover:
  - every row of the outcome table: cancelled; completed; no-show; checked-in today; scheduled today with its time already passed; scheduled in the future; past BOOKED; past APPEARED; legacy OPEN queue token;
  - every eligibility row, plus the cutoff boundary at exactly 2h (allowed) and 2h minus 1 minute (refused);
  - the display order: a past no-show reports `VISIT_RESOLVED`, not `CUTOFF_PASSED`.

  Expect RED.
- [X] T003 Implement `VisitOutcome`, `PatientCancellationRefusal`, the `CancellationEligibility` record and `PatientVisitOutcomes` (spec decision tables). T002 should then be GREEN.

## Phase 3: US1 + US2 + US3 backend

- [X] T004 Write `backend/src/test/java/com/cms/booking/integration/PatientBookingOutcomeTest.java`. Expect RED. It covers:
  - the list returns `visitOutcome` and `cancellation` for synthetic no-show, completed, cancelled, today-BOOKED, future and past-BOOKED bookings;
  - the new detail endpoint returns 200 to the owner and 404 to another patient;
  - live status for an own no-show in a fully resolved session today says "Missed appointment" and never "Visit complete";
  - an own COMPLETED booking says "Visit complete";
  - the cancel endpoint still refuses a cancelled booking and a queue booking with the existing errors.
- [X] T005 Add the fields to `PatientBookingSummaryResponse`. Map them in `PatientMyBookingsController`, and add `GET /api/v1/patients/bookings/{bookingId}` (owner-only).
- [X] T006 Add `visitOutcome` to `PatientSessionLiveStatusResponse`, and use outcome text for terminal outcomes in `PatientSessionLiveStatusController`.
- [X] T007 Make `PatientBookingCancellationController` use `PatientVisitOutcomes.requestRefusal`, mapped to the same exceptions in the same order. Run the existing cancellation tests unchanged.
- [X] T008 Re-run T004 (GREEN), plus every `*Cancellation*`, `*LiveStatus*` and `PatientBooking*` test.

## Phase 4: Frontend

- [X] T009 [P] Write Vitest tests. Expect RED:
  - `NextAppointmentCard` picks the earliest upcoming booking, ignores no-show, completed, cancelled and not-recorded bookings, keeps today's elapsed BOOKED booking, and orders timed before untimed;
  - `MyBookings` shows the outcome labels;
  - the new `PatientBookingDetail` test checks the outcome header, that the cancel button appears only when allowed, the reason text for each refusal, and that a stale server refusal error is shown.
- [X] T010 Update `api.ts`, add `visitOutcome.ts`, and update `NextAppointmentCard`, `MyBookings` and `PatientBookingDetailPage`. T009 should then be GREEN.
- [X] T011 Run `npx tsc -b`, `npm run lint` and `npx vitest run` (all GREEN).

## Phase 5: Verification and docs

- [X] T012 Run the full backend suite with `spotlessCheck`.
- [X] T013 Runtime check on a fresh database with synthetic data: a no-show today in a resolved session, compared across the staff day sheet, the patient list, the detail page and the dashboard. No real patient data.
- [X] T014 Docs:
  - set the live-audit findings 1–3 status;
  - update the plan's 2R.1 status;
  - add a `backlog/progress.md` row;
  - add a HANDOFF part.
- [X] T015 Check off tasks with their evidence, check `git status`, commit, push and open the PR.

## Observed results (2026-10-01, IST)

- **T001:** branched from `main` @ `7b2550a`; the baseline gates were green (Phase 2A).
- **T002 and T003 (unit):** `PatientVisitOutcomesTest` was red, because the classes did not exist. With the derivation implemented it is green, 13/13. It covers every decision-table row, the cutoff boundary (exactly 2h is allowed; 1 minute less is refused), the display order (a past no-show reports `VISIT_RESOLVED`) and the endpoint's unchanged order.
- **T004 to T008 (integration and contract):**
  - `PatientBookingOutcomeTest` was red on the missing fields and the missing endpoint.
    - Two of its own fixture bugs were fixed first: the `bookedByPatient` factory, and one Patient record per (clinic, account).
    - `theCancelEndpointStillRefusesOnItsOwn` was already green before the change, as intended: it guards existing behaviour.
  - All 6 are now green.
  - The 4 `@WebMvcTest` contract tests needed the new bean; they import the real policy through `PatientVisitOutcomesTestConfig`, not a mock.
  - The live-status contract fixture now sets a session date and a booking status, which real data always has, and a contract case was added: an own no-show in a completed session reads "Missed appointment".
  - The targeted backend set passed 175/175, with `spotlessCheck` green.
- **T009 to T011 (frontend):**
  - The new tests were red as expected.
  - Now green: `tsc` is clean, lint exits 0 with the 24 baseline warnings (none new), Vitest passes **457/457 in 79 files**, and the build passes.
- **T013 (runtime):** fresh Postgres 16 and the 069 jar on port 8090 (Flyway V43, IST log timestamps), plus Vite and headless Chromium, with synthetic data only. Scenario: the patient's 09:00 slot today is NO_SHOW, another patient's 09:15 slot is COMPLETED, and a booking on 4 October is still BOOKED.
  - **Staff day sheet:** the 09:00 slot shows NO_SHOW.
  - **Patient API:**
    - The list reports today's booking as NO_SHOW (VISIT_RESOLVED) and the 4 October booking as SCHEDULED, cancellable.
    - The detail endpoint reports NO_SHOW.
    - Live status returns `"statusText":"Missed appointment"`.
    - Another patient's booking returns 404.
    - A direct cancel of the no-show is still refused (`CANCELLATION_CUTOFF_PASSED`).
  - **UI:**
    - The dashboard's "Your next visit" is "Oct 4, 10:00".
    - My bookings shows "Missed appointment" and "Upcoming".
    - The no-show detail shows "Missed appointment", 0 cancel buttons, 0 "Visit complete", and the explanation "already been recorded by the clinic".
    - The 4 October detail shows 1 cancel button.
- **Live-audit status:**
  - Finding 1 (no-show shown as "Visit complete"): **fixed**.
  - Finding 2 (no-show shown as the next visit and "Active"): **fixed**.
  - Finding 3 (cancel offered for ineligible appointments): **fixed**.

  Evidence is above. The audit document itself is updated once #33 (which adds it) is merged.
- **T012:** full backend suite **1,133 passed, 0 failed, 0 skipped**, `spotlessCheck` green, 21m 14s.
- **T014:** the `backlog/progress.md` row is added. The audit-document status and the HANDOFF part are deferred until #33, which adds those documents, is merged, so the two PRs don't conflict.

