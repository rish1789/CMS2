# Quickstart: Individual Booking Cancellation & Waitlist Trigger

See [data-model.md](./data-model.md) and [contracts/booking-cancellation.md](./contracts/booking-cancellation.md).

## Prerequisites

- A confirmed Fixed-Time Booking (016/017), an Operations/ClinicAdmin staff token and/or the
  owning patient's token.

## Scenario 1 — Staff cancels at any time (US1)

1. `POST /api/v1/clinics/{clinicId}/bookings/{bookingId}/cancel` as staff, regardless of how
   close the scheduled slot time is (including already past). **Expect**: `200`,
   `status: "CANCELLED"`.
2. Re-fetch the Slot. **Expect**: `status = OPEN`.
3. Book that same Slot again via 016/017/018/025's existing endpoints. **Expect**: succeeds — a
   new `Booking` row, the original cancelled one still present and retrievable.
4. Attempt to cancel the same (now-cancelled) Booking again. **Expect**: `409 BOOKING_NOT_CANCELLABLE`.

## Scenario 2 — Patient cancels within the cutoff (US2)

1. As the owning patient, with the Booking's scheduled slot time more than 2 hours away,
   `POST /api/v1/patients/bookings/{bookingId}/cancel`. **Expect**: `200`, identical outcome to
   Scenario 1.

## Scenario 3 — Patient cutoff enforcement

1. As the owning patient, with the Booking's scheduled slot time less than 2 hours away, attempt
   cancellation. **Expect**: `409 CANCELLATION_CUTOFF_PASSED`, nothing changes.
2. Staff cancel the same Booking on the patient's behalf. **Expect**: `200` — staff are never
   subject to the cutoff.

## Scenario 4 — Ownership and access

1. A different patient attempts to cancel a Booking that isn't theirs. **Expect**:
   `404 BOOKING_NOT_FOUND`.
2. Staff with zero role assignment at the Booking's clinic attempt to cancel it. **Expect**:
   `403 FORBIDDEN`.

## Scenario 5 — Already-resolved Slot states

1. Cancel a Booking whose Slot is already `NO_SHOW` (021) or `COMPLETED` (026). **Expect**:
   `409 BOOKING_NOT_CANCELLABLE` in both cases.

## Scenario 6 — Queue-mode is out of scope

1. Attempt cancellation against a Queue-mode Booking (either endpoint). **Expect**:
   `409 NOT_A_FIXED_TIME_SESSION`.

## Scenario 7 — Waitlist trigger fires exactly once

1. Cancel a confirmed Booking. **Expect**: exactly one `BookingCancelledEvent` observed (no
   listener exists yet in this feature — this feature verifies the publish itself, not any
   downstream consumption, which is 028's later responsibility).
2. Fire two concurrent cancellation attempts against the same Booking. **Expect**: exactly one
   succeeds (`200`), the other `409 BOOKING_NOT_CANCELLABLE`; exactly one event published.

## Scenario 8 — Frontend

1. As staff, cancel a booking from the UI (`CancelBookingButton.tsx`,
   `frontend/src/features/booking-cancellation/`); as the owning patient, do the same from their
   own view, seeing the cutoff-blocked message when applicable.
