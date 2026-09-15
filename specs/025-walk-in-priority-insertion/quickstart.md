# Quickstart: Walk-In / Priority Insertion

See [data-model.md](./data-model.md) and [contracts/walk-in-insertion.md](./contracts/walk-in-insertion.md).

## Prerequisites

- A Fixed-Time Session with Slots generated (012), some of them possibly `isBuffer=true` (022) or
  `NO_SHOW` (021), an Appointment Type with a resolvable fee for that doctor (015), an Operations
  or ClinicAdmin staff token for the clinic.

## Scenario 1 — Buffer slot used first, no override reason needed (US1)

1. A Session with at least one `isBuffer=true`/`OPEN` Slot and at least one plain `OPEN` regular
   Slot.
2. `POST /api/v1/clinics/{clinicId}/sessions/{sessionId}/walk-in` with
   `{"patientName": "Walk-in Patient", "patientPhone": "9812345670", "appointmentTypeId": ...}`
   (no `overrideReason`). **Expect**: `201`, `paymentStatus: "PENDING"`, `lockedFee` matches
   resolution.
3. Re-fetch the buffer Slot. **Expect**: `status = BOOKED`. The plain regular Slot remains `OPEN`
   (priority order was honored — SC-001).

## Scenario 2 — No-show-freed slot used when no buffer slot is available (US2)

1. A Session with no `OPEN` buffer Slot, but one Slot marked `NO_SHOW` (from 021) with its
   original no-show `Booking` still present, plus at least one plain `OPEN` regular Slot.
2. `POST .../walk-in` with an existing `patientId` and `appointmentTypeId` (no `overrideReason`).
   **Expect**: `201`. The original no-show `Booking` no longer exists (FR-001a); the new `Booking`
   references the same `Slot`, now `status = BOOKED`. The plain regular Slot remains `OPEN`.
3. Repeat Scenario 1's setup but with *both* an `OPEN` buffer Slot and a `NO_SHOW` Slot present.
   **Expect**: the buffer Slot is used, not the no-show-freed one (SC-001, strict order).

## Scenario 3 — Regular slot requires an override reason (US3)

1. A Session with no `OPEN` buffer Slot and no `NO_SHOW` Slot, but at least one plain `OPEN`
   regular Slot.
2. `POST .../walk-in` with `appointmentTypeId` set and no `overrideReason`. **Expect**:
   `400 OVERRIDE_REASON_REQUIRED`, nothing created (SC-002).
3. Retry with `"overrideReason": "Family emergency, doctor agreed to fit them in"`. **Expect**:
   `201`; re-fetch the created `Booking` and confirm `overrideReason` is retrievable (SC-004).

## Scenario 4 — Nothing available

1. A Session where every Slot is already `BOOKED` (no `OPEN` buffer, no `NO_SHOW`, no `OPEN`
   regular). `POST .../walk-in` with any valid body. **Expect**: `409 NO_SLOT_AVAILABLE`, nothing
   created (FR-009).

## Scenario 5 — Fee resolution blocks everything, including the no-show delete

1. Using an Appointment Type/doctor combination with no override and no default fee, against a
   Session with a `NO_SHOW` Slot available. Attempt an insertion. **Expect**:
   `409 NO_FEE_CONFIGURED`; the original no-show `Booking` is still present, untouched (FR-001a,
   SC-003).

## Scenario 6 — Concurrency (SC-005)

1. Set up a Session with exactly one eligible Slot at the highest available tier. Fire two
   concurrent `walk-in` requests. **Expect**: exactly one succeeds (`201`), the other
   `409 SLOT_ALREADY_BOOKED`.

## Scenario 7 — Authorization

1. `POST .../walk-in` with a Doctor's own token (not Operations/ClinicAdmin). **Expect**:
   `403 FORBIDDEN`.

## Scenario 8 — Frontend form

1. As Operations/ClinicAdmin in the UI, open the walk-in insertion form
   (`frontend/src/features/staff-booking/WalkInForm.tsx`), submit against a Session. **Expect**:
   success confirmation showing the locked fee; if the system determines only the override-reason
   path is available, the form surfaces that requirement inline rather than failing silently.
