# Quickstart: Day Sheet Smart Status Flow

Validates the feature end-to-end against a running dev environment (`./dev.sh`, or `preview_start` for `backend`/`frontend` per this repo's own tooling).

## Prerequisites

- Backend and frontend dev servers running.
- A verified clinic with an active ClinicAdmin, at least one Operations staff member, and one treating Doctor with a Fixed-Time schedule for today.
- One patient Booking on a Fixed-Time slot for that doctor today (staff-assisted booking is fine).

## Scenario 1 — Appeared removes No-Show eligibility, then auto-completes

1. Sign in as ClinicAdmin or Operations; open the Day Sheet for today.
2. On the booked slot's row, mark it **Appeared**. Confirm the row's status badge changes to Appeared and the old inline "Mark complete"/"Cancel" row actions are gone for this row.
3. Wait (or, for a fast local check, book a slot whose end time is already in the past) until the slot's scheduled end time passes.
4. Within one minute (the sweep's cadence — see `SlotAutoCompletionTrigger`), confirm the row's status badge flips to Completed with no manual click, and the session's delay indicator (if shown) reflects the same recalculation a manual completion would trigger.
5. Separately, book a second slot and let it sit un-marked past its start time + the existing 10-minute grace period. Confirm it still becomes No-Show exactly as it does today — unaffected by this feature.

## Scenario 2 — Doctor completes their own Appeared slot, but never sees Appeared/No-Show

1. Mark a booked slot Appeared as ClinicAdmin/Operations (as in Scenario 1, step 2).
2. Sign in as the treating doctor for that slot; open the Day Sheet.
3. Confirm: no "Appeared" or "No-Show" action or label appears anywhere on the page for the doctor.
4. Confirm: a "Completed" action is available on the doctor's own Appeared slot, and clicking it succeeds.
5. As the same doctor, confirm a still-`BOOKED` slot (never marked Appeared) does *not* offer them a Completed action — only ClinicAdmin/Operations can complete directly from Booked (unchanged existing path).
6. Sign back in as ClinicAdmin/Operations and confirm every action they had before this feature (marking Appeared, completing from either Booked or Appeared) is still present.

## Scenario 3 — Correcting a mistaken No-Show

1. Let a booked slot auto-flip to No-Show (Scenario 1, step 5).
2. As ClinicAdmin/Operations, mark that No-Show slot **Appeared**. Confirm it succeeds and the slot now behaves exactly like any other Appeared slot (eligible for auto-completion, eligible for cancellation).

## Scenario 4 — Bulk cancel via checkbox selection

1. On the Day Sheet, confirm every `Booked`/`Appeared` row now shows a selection checkbox and no longer shows an inline "Cancel" link; confirm `Completed`/`No-Show`/already-cancelled rows show no checkbox.
2. Select a single eligible slot and cancel it. Confirm the same effect today's single cancellation has (e.g. if a waitlist entry exists for that doctor/appointment type, confirm an offer is generated exactly as it is today).
3. Select several eligible slots (mixing at least one `Booked` and one `Appeared` slot) and cancel them together in one action. Confirm all succeed and each produces its own correct individual effect.
4. Use "select all" and confirm it simply checks every currently-eligible box on the visible Day Sheet — it must not behave differently from, or route through, the separate whole-day-cancellation feature.
5. Select a slot, then (in a second browser session or tab) complete or cancel that same slot as someone else before confirming the batch cancel. Confirm the batch response reports that specific slot as failed without blocking the other selected slots from succeeding.
6. As the treating doctor, confirm no selection checkbox or cancel action is visible anywhere on their Day Sheet.

## Automated coverage

- Backend: `cd backend && ./gradlew test --tests "com.cms.scheduling.*" --tests "com.cms.booking.*"` (unit + contract; integration tests are written/compiled but require Docker, per this project's standing sandbox limitation).
- Frontend: `cd frontend && npx vitest run tests/day-sheet tests/session-delay tests/booking-cancellation` (adjust paths to match the actual new/changed test files once written).
