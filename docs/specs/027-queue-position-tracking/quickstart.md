# Quickstart: Queue Position Tracking (Queue-Mode Only)

See [data-model.md](./data-model.md) and [contracts/queue-position-tracking.md](./contracts/queue-position-tracking.md).

## Prerequisites

- A Queue-mode Session with several tokens issued and booked (013/018), some marked completed via
  a mechanism this feature doesn't itself define (test setup sets `Slot.status` directly, since no
  Queue-mode "complete" action exists yet in this backlog).

## Scenario 1 — Staff views a booking's position (US1)

1. A Queue-mode Session with tokens 1-5 booked; tokens 1-2 `COMPLETED`, token 3 still `BOOKED`.
2. `GET /api/v1/clinics/{clinicId}/bookings/{token5BookingId}/queue-position`. **Expect**: `200`,
   `applicable: true, position: 3` (tokens 3 and 4 active ahead, +1).

## Scenario 2 — Position reflects a token ahead becoming no-show

1. Continuing Scenario 1, mark token 3's Slot `NO_SHOW`.
2. Re-query token 5's position. **Expect**: `position: 2` (only token 4 active ahead now).

## Scenario 3 — Not applicable cases

1. `GET .../queue-position` for a Fixed-Time Session's Booking. **Expect**: `200`,
   `applicable: false, position: null`.
2. `GET .../queue-position` for a Booking whose own Slot is already `COMPLETED`. **Expect**:
   `200`, `applicable: false, position: null`.

## Scenario 4 — Patient views their own booking's position (US2)

1. As the patient who holds token 5 from Scenario 1, `GET /api/v1/patients/bookings/{token5BookingId}/queue-position`.
   **Expect**: identical response to Scenario 1's staff query.
2. As a different, unrelated patient, attempt the same request for token 5's Booking. **Expect**:
   `404 BOOKING_NOT_FOUND`.

## Scenario 5 — No tokens ahead, or all resolved

1. Query position for token 1 (nothing ahead). **Expect**: `position: 1`.
2. Mark every token ahead of some later token completed or no-show, then query that later token's
   position. **Expect**: `position: 1`.

## Scenario 6 — Staff/patient agreement

1. Query the same active Booking's position via both the staff and patient endpoints at the same
   moment. **Expect**: identical `applicable`/`position` values (SC-005).

## Scenario 7 — Frontend

1. As staff in the UI, view a queue-mode Booking's position (`QueuePositionIndicator.tsx`,
   `frontend/src/features/queue-position/`); as the patient, view the same figure for their own
   booking.
