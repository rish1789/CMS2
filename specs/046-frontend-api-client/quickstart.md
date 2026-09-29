# Quickstart: Frontend Shared API Client

## Automated verification

1. `cd frontend && npx tsc -b` — confirm no type errors.
2. `npm run lint` — confirm no new lint errors.
3. `npm run test -- --run` — confirm the full suite passes, same or higher test count than before.
4. Specifically re-run the 4 migrated features' own test files: `npx vitest run tests/booking-detail tests/partial-session-cancellation tests/patient-booking tests/waitlist`.

## Manual/live verification (the actual bug fix, US1)

5. Start the backend (`preview_start` per this project's established workflow) and frontend dev server.
6. In the browser, trigger a real backend-rejected booking (e.g. attempt to book a slot that's already booked, or an appointment type with no fee configured) and confirm the UI shows the backend's real message, not a hand-guessed generic one — cross-check by reading the actual network response body in the browser's network panel against what's displayed.
