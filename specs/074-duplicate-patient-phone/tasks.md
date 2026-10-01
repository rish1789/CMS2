# Tasks: Duplicate Patient Phone in Staff Booking (074)

**Input**: [spec.md](spec.md), [plan.md](plan.md)

- [X] T001 Branch from current `main`.
- [X] T002 Write `DuplicatePatientPhoneTest` (integration, including concurrency) and the `WalkInPatientRegistrar` unit test. Expect RED; record today's actual responses (`SLOT_ALREADY_BOOKED` or 500).
- [X] T003 Implement the registrar, the exception, the handler mapping and the three callers. T002 should then be GREEN; the existing staff booking, queue, walk-in and linking tests should stay green.
- [X] T004 Write Vitest tests for the three forms. Expect RED.
- [X] T005 Implement the form handling. T004 should then be GREEN.
- [X] T006 Gates: the full backend suite with `spotlessCheck`; `tsc`, lint, Vitest and build.
- [X] T007 Runtime check with synthetic data: a collision in each form, Use this patient, then a successful booking.
- [X] T008 Docs: a progress row, the PB-001/PB-002 status in the defect register, and the plan's 2B status. Then commit, push and open the PR.

## Observed results (2026-10-01, IST)

- **T001:** branched from `main` @ `ed742ce` (#39).
- **T002 (red, reproduced against real Postgres):** 6 of the 8 integration tests failed, exactly as the register says:
  - staff fixed-time booking answered **409 `SLOT_ALREADY_BOOKED`** (PB-001);
  - staff queue booking and front-desk walk-in threw an unhandled `ConstraintViolationException` on `uq_patient_clinic_phone_unlinked` (a 500 in a real server; PB-002);
  - the three concurrent cases failed the same ways.

  The cross-clinic and linked-patient cases were already green; they guard existing behaviour.
- **T003 (green):**
  - `DuplicatePatientPhoneTest`: 8/8.
  - `WalkInPatientRegistrarTest`: 4/4. It covers the pre-check naming, the index race with no re-query, another constraint being rethrown, and the no-phone case.
  - Booking, patient, queue-slot and linking tests: **488/488**.
  - One existing unit stub (`FrontDeskWalkInServiceTest`) moved from `save` to `saveAndFlush`, because the patient is now flushed. No assertion changed.
- **T004 (red):** 4 of 4 new form tests failed.
- **T005 (green):** 4 of 4 pass.
  - The shared `DuplicatePhoneConflict` notice offers "Book {name} instead" on the staff forms and "Use {name}" on the walk-in screen. Without a name, it shows the search hint.
  - The body helper lives in its own `.ts` file, which removed a new `only-export-components` lint warning instead of suppressing it.
- **T006:**
  - Backend: full suite **1,158 passed, 0 failed, 0 skipped** (1,146 + 12), with `spotlessCheck` green, in 23m 28s.
  - Frontend: `tsc` clean; lint exit 0 with the 24 baseline warnings; Vitest **526/526 in 84 files**; build OK.
- **T007 (runtime):** fresh Postgres 16, the 074 jar, Vite and headless Chromium. Synthetic data: clinic "Clinic Phone", with unlinked patient "Asha Rao" on 9876543210.
  - **Staff fixed-time booking:** a new patient on that phone gets **409** naming Asha Rao. The typed name ("New Person") is kept. "Book Asha Rao instead" gives **201**, "Booking confirmed".
  - **Staff queue booking:** **409** naming Asha Rao, then "Book Asha Rao instead" gives **201**, token 1.
  - **Front-desk walk-in:** **409** naming Asha Rao, with the reason (Pain) kept. "Use Asha Rao" selects her. Registering then gets the existing 063 confirmation, "already in this session today. Register them again?", correctly, because the queue booking above gave her token 1.
  - **Database afterwards:** 1 patient with that phone, 2 bookings (both Asha's), 1 queue token. No orphan rows.
- **Defect register:** PB-001 and PB-002 are marked **FIXED**.
