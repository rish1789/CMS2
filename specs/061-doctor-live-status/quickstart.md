# Quickstart: Doctor Live Schedule Status

Validates the feature end-to-end against a running dev environment (`./dev.sh`, or `preview_start`
for `backend`/`frontend` per this repo's own tooling).

## Prerequisites

- Backend and frontend dev servers running.
- A verified clinic with a Fixed-Time doctor schedule and at least 4-5 consecutive slots on a
  near-future date, all booked by real (self-service or walk-in) patients — enough to move the
  actual pointer through several ordinals during the walkthrough.
- Staff/ClinicAdmin login for that clinic, and the treating doctor's own staff login.
- A self-service patient account with one of the bookings above (for the patient-view scenarios).

## Scenario 1 — Not started yet

1. Before the session's first slot's scheduled time, open the Day Sheet's session view as staff.
2. Confirm the live status shows a "Not started yet" style state — not "Delayed" (BR-002).
3. As the same patient (Scenario prerequisite), open the booking detail page and confirm the same
   "not started" framing, no minutes figure.

## Scenario 2 — Doctor starts late, then catches up

1. Let the first slot's scheduled time pass with nobody marked Appeared. Confirm status flips to
   "Delayed" with a minutes-late figure roughly matching elapsed time since the first slot.
2. Mark the first slot Appeared, then Completed. Confirm the minutes-late figure changes on the
   next poll (within ~20s, no manual refresh) as the actual pointer advances.
3. Continue marking slots Appeared/Completed faster than new ones become due. Confirm the
   minutes-late figure shrinks each time, and the status transitions to "On time" once the actual
   and expected pointers land on the same slot (BR-008).

## Scenario 3 — Doctor gets ahead

1. Continuing from Scenario 2, keep completing slots faster than their scheduled times arrive.
2. Confirm status shows "Running early" with a minutes-early figure — never a negative delay, and
   never mislabeled "Delayed" (Edge Cases — Doctor gets ahead).

## Scenario 4 — Breaks and cancellations are delay-neutral

1. If the schedule under test has a break window, confirm the live status never regresses (jumps
   backward into "Delayed") purely because of crossing the break — the expected pointer should
   skip straight over it (BR-005/BR-009, "breaks fall out for free").
2. Cancel one of the still-`BOOKED` slots ahead of the current pointer. Confirm the displayed
   delay/early figure is unchanged immediately after cancellation (SC-005) — the cancelled slot's
   `OPEN` status excludes it from both pointers.

## Scenario 5 — No-shows don't strand the pointer

1. Let a `BOOKED` slot age past the existing 10-minute no-show grace period without marking it
   Appeared. Confirm it auto-flips to No-show (existing behavior, unchanged) and that the actual
   pointer then advances past it on the next poll — the doctor is not shown as perpetually "stuck"
   on the no-show patient (Edge Cases — No-shows).

## Scenario 6 — Session completes

1. Resolve (Complete or No-show) every remaining participating slot in the session.
2. Confirm status shows a "Session complete" style state (BR-003), with no more current/expected
   ordinals or minutes figure.

## Scenario 7 — Operational-day boundary

1. Using a unit test with an injected/mocked clock (not a manual midnight wait), verify
   `OperationalDayService.operationalDateOf(...)` at 04:29, 04:30, and 04:31 resolves to the
   previous / current / current calendar date respectively (User Story 3, SC-004). This scenario
   is validated by the automated test suite, not a live walkthrough — there's no practical way to
   manually observe a 04:30 AM transition during a normal working session.

## Scenario 8 — Privacy: patient view never leaks other patients

1. As the patient from the prerequisites, open the booking detail page at any point during
   Scenarios 1–3 above (any status).
2. Confirm the page shows only: the doctor's name, `currentPatientOrdinal` as a bare number,
   plain-language status text, and (when applicable) an estimated wait — inspect the actual network
   response body to confirm no other field is present (FR-011, SC-003).

## Scenario 9 — Cross-doctor / cross-patient refusal

1. As a different doctor at the same clinic (not the treating doctor for the session under test),
   request the staff/doctor endpoint for that session directly — confirm `404 Not Found`
   (FR-012, mirrors `SessionDelayController`'s existing fail-closed pattern).
2. As a different patient account, request the patient endpoint for the booking under test —
   confirm `404 Not Found` (FR-013).

## Automated coverage

- Backend unit: `cd backend && ./gradlew test --tests "com.cms.scheduling.unit.SessionLiveStatusServiceTest" --tests "com.cms.scheduling.unit.OperationalDayServiceTest"`
- Backend contract: `cd backend && ./gradlew test --tests "com.cms.scheduling.contract.*" --tests "com.cms.booking.contract.*"`
- Backend full module regression (confirms existing SessionDelay* suite untouched): `cd backend && ./gradlew test --tests "com.cms.scheduling.*"`
- Frontend: `cd frontend && npx vitest run tests/session-delay` (adjust to the actual new/changed test file paths once written)
