# Quickstart: Staff-Assisted Fixed-Time Booking

See [data-model.md](./data-model.md) and [contracts/staff-booking.md](./contracts/staff-booking.md).

## Prerequisites

- A Fixed-Time Schedule generated into Sessions/Slots (012), an Appointment Type with a resolvable fee for that doctor (015), an Operations or ClinicAdmin staff token for the clinic.

## Scenario 1 — Book an existing patient

1. Create a clinic-scoped Patient directly (or via a prior visit).
2. `POST /api/v1/clinics/{clinicId}/slots/{slotId}/book` with `{"patientId": ..., "appointmentTypeId": ...}`. **Expect**: `201`, `paymentStatus: "PENDING"`, `lockedFee` matches resolution.
3. Re-fetch the Slot. **Expect**: `status = BOOKED`.

## Scenario 2 — Book a new walk-in patient

1. `POST .../book` with `{"patientName": "Walk-in Patient", "patientPhone": "9812345670", "appointmentTypeId": ...}`. **Expect**: `201`, and a new Patient record now exists at that clinic, unlinked to any Patient Account.
2. Repeat with an invalid phone (`"12345"`). **Expect**: `400 INVALID_MOBILE_NUMBER`, no Patient or Booking created.
3. Repeat with no phone at all. **Expect**: `201` — phone is optional.

## Scenario 3 — No fee configured blocks everything

1. Using an Appointment Type/doctor combination with no override and no default fee, attempt a booking (existing or new patient). **Expect**: `409 NO_FEE_CONFIGURED`, and — for the new-patient case — no Patient record was created either.

## Scenario 4 — Already-booked Slot rejected, race-safely

1. Book Scenario 1's Slot successfully.
2. Attempt to book it again (a different patient). **Expect**: `409 SLOT_ALREADY_BOOKED`.
3. Fire two concurrent booking requests against a single fresh `OPEN` Slot. **Expect**: exactly one succeeds (`201`), the other `409 SLOT_ALREADY_BOOKED`.

## Scenario 5 — Authorization

1. `POST .../book` with a Doctor's own token (not Operations/ClinicAdmin). **Expect**: `403 FORBIDDEN`.

## Scenario 6 — Frontend form

1. As Operations/ClinicAdmin in the UI, open the booking form (`frontend/src/features/staff-booking/BookSlotForm.tsx`), submit a booking. **Expect**: success confirmation showing the locked fee.
