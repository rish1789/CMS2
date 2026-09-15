# Quickstart: Automatic No-Show Detection

Prerequisites: a running backend against Postgres (Testcontainers in tests, or a real
instance), a Fixed-Time Schedule with a generated Session/Slot for a past date/time, and a
Booking against that Slot (016 or 021's own patient-booking flow both work).

Since this feature has no HTTP endpoint, verification is via direct service/repository
inspection (in tests) or by observing the `slot` table after the scheduled sweep runs (in a
real deployment).

## Scenario 1 — Grace period elapsed, no hold

1. Create a Fixed-Time Slot whose scheduled time is now more than 10 minutes in the past,
   `BOOKED`, `onHold = false`.
2. Call `NoShowDetectionService.detectAndMarkNoShows()` directly (or wait for the scheduled
   trigger in a real deployment).
3. **Expect**: the Slot's `status` is now `NO_SHOW`.

## Scenario 2 — Grace period not yet elapsed

1. Create a Fixed-Time Slot whose scheduled time is less than 10 minutes in the past, `BOOKED`.
2. Run the sweep.
3. **Expect**: the Slot's `status` is still `BOOKED`.

## Scenario 3 — On hold

1. Create a Fixed-Time Slot whose scheduled time is well past the grace period, `BOOKED`,
   `onHold = true`.
2. Run the sweep.
3. **Expect**: the Slot's `status` is still `BOOKED` — never marked `NO_SHOW` while held.

## Scenario 4 — Already NO_SHOW, or never booked

1. Run the sweep twice in a row against the same already-`NO_SHOW` Slot from Scenario 1.
2. Run the sweep against an `OPEN` Slot with no Booking, past its scheduled time.
3. **Expect**: neither Slot's status changes on either additional run.

## Scenario 5 — Queue/Token Slot is untouched

1. Create a Queue/Token Slot (via `QueueSlotService`), `BOOKED` via a queue booking, whose
   issuance time is well in the past.
2. Run the sweep.
3. **Expect**: the Slot's `status` is unaffected — this feature is Fixed-Time-only
   (Clarifications).
