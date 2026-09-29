# Quickstart: Waitlist Matching (Longest-Waiting, Doctor/Specialization)

See [data-model.md](./data-model.md) and [contracts/waitlist-join.md](./contracts/waitlist-join.md).

## Prerequisites

- A verified Clinic with a staffed Doctor, a Patient Account, a confirmed Fixed-Time Booking to
  later cancel (016/017), staff and patient tokens.

## Scenario 1 — Doctor-match outranks specialization-only regardless of wait time (US1)

1. Patient A joins the waitlist for Dr. X (`doctorProfileId`) 1 hour ago (backdated in test
   fixtures). Patient B joins the same clinic's waitlist for Dr. X's specialization, no doctor
   preference, 3 days ago.
2. Cancel a confirmed Booking for one of Dr. X's slots via 025's individual cancellation.
3. **Expect**: Patient A's entry transitions to `OFFERED` (not Patient B's), with `offerExpiresAt`
   ≈ now + 30 minutes; Patient A receives a notification.

## Scenario 2 — Longest-waiting within a tier

1. Two doctor-match entries for the same doctor, joined at different times.
2. Cancel a Booking for that doctor. **Expect**: the earlier-joined entry is offered.

## Scenario 3 — No eligible entry

1. A clinic with no waitlist entries at all. Cancel a Booking. **Expect**: no entry changes state,
   no notification sent, the slot simply becomes available for regular booking.

## Scenario 4 — Matching is 025-exclusive

1. Trigger a no-show release (021), a whole-day cancellation (026), and a partial cutoff
   cancellation (027) against sessions that each have an eligible waiting entry. **Expect**: none
   of them ever transitions any entry to `OFFERED`.

## Scenario 5 — Joining the waitlist (US2)

1. As a patient, `POST /api/v1/patients/clinics/{clinicId}/waitlist` with `{"doctorProfileId": ...}`.
   **Expect**: `201`, `status: "WAITING"`.
2. As staff, `POST /api/v1/clinics/{clinicId}/waitlist` with `{"patientAccountId": ..., "specialization": "Cardiology"}`.
   **Expect**: `201`, identical shape.

## Scenario 6 — Frontend

1. As a patient, join a clinic's waitlist for a doctor or specialization
   (`frontend/src/features/waitlist/JoinWaitlistForm.tsx`).
