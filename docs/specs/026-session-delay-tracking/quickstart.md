# Quickstart: Session Delay Tracking (Fixed-Time Only)

See [data-model.md](./data-model.md) and [contracts/session-delay-tracking.md](./contracts/session-delay-tracking.md).

## Prerequisites

- A Fixed-Time Session with Slots generated (012), at least one `BOOKED` (016/020/025), an
  Operations or ClinicAdmin staff token for the clinic.

## Scenario 1 — Completing a Slot recalculates delay (US1)

1. A Fixed-Time Session with Slots at 9:00 (still `BOOKED`), 9:15 (`BOOKED`), 9:30 (`OPEN`); "now"
   is 9:20.
2. `POST /api/v1/clinics/{clinicId}/slots/{9:15 slotId}/complete`. **Expect**: `200`,
   `status: "COMPLETED"`.
3. `GET /api/v1/clinics/{clinicId}/sessions/{sessionId}/delay`. **Expect**: `applicable: true`,
   `delayMinutes: 20` (minutes between 9:20 "now" and the 9:00 Slot's scheduled time — the
   earliest still-OPEN/BOOKED past-due Slot).

## Scenario 2 — Rejections leave nothing changed

1. Attempt `POST .../complete` on a still-`OPEN` Slot. **Expect**: `409 SLOT_NOT_COMPLETABLE`.
2. Repeat on the same Slot after it's `COMPLETED` once. **Expect**: `409 SLOT_NOT_COMPLETABLE`
   (one-way transition).
3. Attempt `POST .../complete` on a Queue-mode Session's Slot. **Expect**:
   `409 NOT_A_FIXED_TIME_SESSION`.

## Scenario 3 — Not a live timer

1. After Scenario 1, wait (or fast-forward the clock) 20 minutes with no further trigger.
2. `GET .../delay` again. **Expect**: still `delayMinutes: 20` — unchanged, since no trigger
   occurred (SC-002).

## Scenario 4 — Walk-in insertion is the second trigger (US2)

1. A Fixed-Time Session with an earlier `BOOKED` Slot whose scheduled time has passed, and an
   `OPEN` buffer Slot.
2. Insert a walk-in (025) via `POST /api/v1/clinics/{clinicId}/sessions/{sessionId}/walk-in`.
   **Expect**: `201` (025's existing contract, unchanged).
3. `GET .../delay`. **Expect**: `applicable: true`, `delayMinutes` reflects the still-outstanding
   earlier Slot, recalculated as part of the walk-in insertion itself.

## Scenario 5 — No outstanding delay

1. Mark every past-due Slot in a Session `COMPLETED`.
2. `GET .../delay`. **Expect**: `applicable: true`, `delayMinutes: null`.

## Scenario 6 — Queue-mode Session never has a delay figure

1. A Queue-mode Session. `GET .../delay`. **Expect**: `applicable: false`, `delayMinutes: null` —
   not an error (FR-007).

## Scenario 7 — Authorization

1. `POST .../complete` with a Doctor's own token (not Operations/ClinicAdmin). **Expect**:
   `403 FORBIDDEN` (Clarifications).

## Scenario 8 — Frontend

1. As Operations/ClinicAdmin in the UI, mark a Slot completed from the session view
   (`frontend/src/features/session-delay/CompleteSlotButton.tsx`) and see the delay figure
   (`DelayIndicator.tsx`) update accordingly.
