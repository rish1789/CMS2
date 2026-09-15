# Quickstart: Monthly Automatic Retention Purge

## Prerequisites

- Backend running with `admin.super-admin.username`/`admin.super-admin.password` configured (see `SuperAdminSecurityConfig`).
- A clinic, doctor, and a booking whose `createdAt` is 3+ years in the past (seed directly in test data — no UI path exists for backdating a booking).
- A patient anonymized via feature 037's `POST /api/v1/clinics/{clinicId}/patients/{patientId}/anonymize`.
- Clinical content (Consultation Note, Prescription with items, External Record Reference) attached to that booking via features 034/035/036's existing endpoints.

## Scenario 1 — automatic sweep purges eligible content

1. Seed a booking as above: 3+ years old, patient anonymized, all three content types attached.
2. Invoke `RetentionPurgeService.purge()` directly (integration test) or wait for `RetentionPurgeTrigger`'s scheduled firing.
3. Verify: `consultationNoteRepository.findByBooking_Id(bookingId)` empty, `prescriptionRepository.findByBooking_Id(bookingId)` empty, `externalRecordReferenceRepository.findByBooking_Id(bookingId)` empty.
4. Verify: `bookingRepository.findById(bookingId)` still present; `patientRepository.findById(patientId)` still present and still anonymized.

## Scenario 2 — ineligible bookings are left alone

1. Seed a second booking: 3+ years old, but patient NOT anonymized, with a Consultation Note attached.
2. Seed a third booking: patient anonymized, but `createdAt` recent (< 3 years), with a Consultation Note attached.
3. Run the purge.
4. Verify: both Consultation Notes still present.

## Scenario 3 — Super Admin manual re-trigger

```bash
curl -u super-admin:<password> -X POST http://localhost:8080/api/v1/admin/retention-purge/run
```

Expect `200 OK` with `{"purgedBookingCount": N}` reflecting Scenario 1's booking (and any other eligible ones).

## Scenario 4 — non-Super-Admin rejected

```bash
curl -H "Authorization: Bearer <staff-jwt>" -X POST http://localhost:8080/api/v1/admin/retention-purge/run
```

Expect `401 Unauthorized` — the staff JWT is not valid Basic Auth for the `/api/v1/admin/**` chain, and no purge occurs.
