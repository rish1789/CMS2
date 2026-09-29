# Quickstart: Whole-Day Session Cancellation

See [data-model.md](./data-model.md) and [contracts/session-cancellation.md](./contracts/session-cancellation.md).

## Prerequisites

- A generated Session (Fixed-Time or Queue-mode) with several confirmed Bookings (016/017/018),
  an Operations/ClinicAdmin staff token.

## Scenario 1 — Cancel a Fixed-Time Session with mixed Slot states (US1)

1. A Session with 3 `BOOKED` Slots (active Bookings), 2 still-`OPEN` Slots, 1 `NO_SHOW` Slot, 1
   `COMPLETED` Slot.
2. `POST /api/v1/clinics/{clinicId}/sessions/{sessionId}/cancel`. **Expect**: `200`,
   `bookingsCancelled: 3`.
3. Re-fetch the 3 formerly-`BOOKED` Slots. **Expect**: all `OPEN`. The `NO_SHOW`/`COMPLETED` Slots
   and the already-`OPEN` ones are untouched.
4. Confirm no `BookingCancelledEvent` was observed (no listener exists to consume it, but this
   feature's own code path never calls `publishEvent` for it at all — verified structurally).

## Scenario 2 — Cancel a Queue-mode Session

1. A Queue-mode Session with several active Bookings (013/018/022).
2. `POST .../cancel`. **Expect**: `200`, every active Booking cancelled, same as Scenario 1.

## Scenario 3 — Already-cancelled / nothing to cancel

1. Repeat Scenario 1's cancellation against the same, now-cancelled Session. **Expect**:
   `409 SESSION_ALREADY_CANCELLED`, zero further state change.
2. A brand-new Session that has never had any Booking at all. **Expect**: `409
   SESSION_ALREADY_CANCELLED` (structurally "nothing to cancel" either way — Clarifications).

## Scenario 4 — Notification pipeline integration

1. A Session with one Booking whose Patient has a linked Patient Account, and one walk-in Booking
   with no linked Account.
2. `POST .../cancel`. **Expect**: `200`; exactly one `NotificationEvent` created (for the
   linked-Account Booking); the walk-in Booking produces none.

## Scenario 5 — Authorization

1. `POST .../cancel` with a Doctor's own token. **Expect**: `403 FORBIDDEN`.

## Scenario 6 — Frontend

1. As Operations/ClinicAdmin in the UI, cancel a whole Session
   (`frontend/src/features/session-cancellation/CancelSessionButton.tsx`) and see the count of
   bookings cancelled.
