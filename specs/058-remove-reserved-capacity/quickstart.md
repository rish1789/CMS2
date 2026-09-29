# Quickstart: Remove Reserved-Capacity Walk-In Slots

Validates the removal end-to-end against a running dev environment (`./dev.sh`, or `preview_start`
for `backend`/`frontend` per this repo's own tooling).

## Prerequisites

- Backend and frontend dev servers running, migrated to `V35`.
- A verified clinic with an active ClinicAdmin and one treating Doctor with a Fixed-Time schedule.

## Scenario 1 — Every newly generated slot is directly bookable

1. Trigger session generation for a doctor (Super Admin console, or the nightly job).
2. Open that session's Day Sheet. Confirm no row shows "Reserved capacity" and no row shows "No
   direct booking — use 'Insert a walk-in'".
3. As staff, book any slot directly. Confirm it succeeds regardless of which slot was chosen —
   there is no longer a subset that rejects direct booking.
4. As a patient, browse open slots for that same session/doctor. Confirm every slot the Day Sheet
   shows as `Open` also appears in the patient's browsing list (nothing is held back).

## Scenario 2 — Walk-in insertion still works with the 2-tier fallback

1. On a session with a slot that has already auto-flipped to No-Show (or use the existing
   No-Show-detection quickstart flow from feature 057 to produce one quickly), insert a walk-in
   with no override reason. Confirm it succeeds and lands on the no-show-freed slot, replacing its
   old booking — exactly as before this change.
2. On a session with no No-Show slot but at least one other `Open` slot, insert a walk-in with no
   override reason. Confirm it is **rejected** requiring an override reason (this is now the
   universal fallback behavior, matching what the old "tier 3" already required).
3. Retry step 2 with an override reason supplied. Confirm it succeeds.
4. On a session with zero `Open` slots and no No-Show slot, attempt a walk-in. Confirm the
   existing "no slot available" rejection still occurs, unchanged from before this feature.

## Scenario 3 — A no-show rate no longer changes slot generation

1. Compare the slot count/spacing generated for a doctor with a high recent no-show rate against a
   doctor with none, same schedule shape. Confirm both produce an identical number of slots at
   identical times — no slots are held back for either.

## Automated coverage

- Backend: `cd backend && ./gradlew test --tests "com.cms.scheduling.*" --tests "com.cms.booking.*"`
  (unit + contract; integration tests are written/compiled but require Docker, per this project's
  standing sandbox limitation).
- Frontend: `cd frontend && npx vitest run tests/day-sheet`
