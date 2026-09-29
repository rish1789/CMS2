# Quickstart: Patient Immediate Anonymization

See [data-model.md](./data-model.md) and [contracts/patient-anonymization.md](./contracts/patient-anonymization.md).

## Prerequisites

- A verified Clinic with a Patient record, staff (Operations or ClinicAdmin) token.

## Scenario 1 — Anonymize a patient with no active future bookings

1. As Operations/ClinicAdmin staff, `POST /api/v1/clinics/{clinicId}/patients/{patientId}/anonymize`.
   **Expect**: `200`, `anonymized: true`.
2. Confirm the Patient's `name` is the fixed placeholder and `phone` is cleared.

## Scenario 2 — Blocked while an active future booking exists

1. With a Patient holding an active, still-`BOOKED` Fixed-Time booking, attempt anonymization.
   **Expect**: `409 PATIENT_HAS_ACTIVE_FUTURE_BOOKING`; no fields modified.
2. Cancel that booking (025), then retry. **Expect**: `200` this time.

## Scenario 3 — Historical records untouched

1. An anonymized Patient has past bookings and clinical documentation (Consultation Notes,
   Prescriptions, External Record References). **Expect**: all remain fully intact, still
   referencing the (now-anonymized) Patient record.

## Scenario 4 — Idempotent retry

1. Anonymize the same patient a second time. **Expect**: `200`, the same `anonymizedAt` as the
   first call (unchanged).

## Scenario 5 — Unauthorized / unknown patient

1. As a Doctor's own token (no Operations/ClinicAdmin role), attempt anonymization. **Expect**:
   `403 FORBIDDEN`.
2. Against a random, nonexistent `patientId`. **Expect**: `404 PATIENT_NOT_FOUND`.

## Scenario 6 — Frontend

1. As Operations/ClinicAdmin staff, trigger anonymization from the UI
   (`frontend/src/features/patient-anonymization/AnonymizePatientButton.tsx`).
