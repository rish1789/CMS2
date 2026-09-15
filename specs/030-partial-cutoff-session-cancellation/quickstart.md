# Quickstart: Partial (Cutoff-Based) Session Cancellation

See [data-model.md](./data-model.md) and [contracts/partial-session-cancellation.md](./contracts/partial-session-cancellation.md).

## Prerequisites

- A generated Fixed-Time or Queue-mode Session with several confirmed Bookings spanning both
  sides of a chosen cutoff time, an Operations/ClinicAdmin staff token.

## Scenario 1 — Cancel the trailing portion of a Fixed-Time Session (US1)

1. A Session with Bookings at 9:00, 9:15 (`COMPLETED`), 15:45 (`BOOKED`), 16:15 (`BOOKED`), and an
   `OPEN` Slot at 16:30.
2. `POST /api/v1/clinics/{clinicId}/sessions/{sessionId}/cancel-from-cutoff` with
   `{"cutoffTime": "16:00:00"}`. **Expect**: `200`, `bookingsCancelled: 1` (only the 16:15
   Booking — 15:45 is before the cutoff, 9:15 is already `COMPLETED`, 16:30 was already `OPEN`).
3. Re-fetch the 16:15 Slot. **Expect**: `OPEN`. The 15:45 and 9:15 Slots are unchanged.

## Scenario 2 — Queue-mode cutoff uses token-issuance time

1. A Queue-mode Session with tokens issued (and booked) at two different times, one before and
   one at/after a chosen cutoff.
2. `POST .../cancel-from-cutoff` with that cutoff. **Expect**: only the at/after-cutoff token's
   Booking is cancelled.

## Scenario 3 — Nothing qualifies

1. A cutoff time later than every remaining slot. **Expect**: `200`, `bookingsCancelled: 0` — not
   an error (unlike 029's whole-session `SESSION_ALREADY_CANCELLED`).

## Scenario 4 — No waitlist bump, notification pipeline integration

1. Cancel a trailing portion with one Booking whose Patient has a linked Patient Account.
   **Expect**: exactly one `NotificationEvent` created; no `BookingCancelledEvent` observed.

## Scenario 5 — Authorization

1. `POST .../cancel-from-cutoff` with a Doctor's own token. **Expect**: `403 FORBIDDEN`.

## Scenario 6 — Frontend

1. As Operations/ClinicAdmin in the UI, enter a cutoff time and cancel the trailing portion of a
   Session (`frontend/src/features/partial-session-cancellation/CancelFromCutoffForm.tsx`), and
   see the count of bookings cancelled.
