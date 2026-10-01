# Implementation Plan: Duplicate Patient Phone in Staff Booking (074)

**Spec**: [spec.md](spec.md)

## Backend

- **New `booking.service.WalkInPatientRegistrar`:** the one place that creates an unlinked patient for the three staff paths.
  1. Pre-check with `PatientRepository.findByClinic_IdAndPhoneAndPatientAccountIsNull(clinicId, phone)`, which already exists and is used by 009 linking. A hit throws `PatientPhoneAlreadyRegisteredException(existing)`.
  2. Otherwise, `saveAndFlush` the new `Patient`. A `DataIntegrityViolationException` whose cause names `uq_patient_clinic_phone_unlinked` throws the same exception with no existing patient. Anything else is rethrown unchanged.
  3. A blank or null phone skips the check: walk-ins may omit the phone, and the partial index ignores NULL.
- **Callers:**
  - `StaffBookingService.resolveOrCreatePatient` and `StaffQueueBookingService.resolveOrCreatePatient` each delegate to the registrar after their existing phone validation.
  - `FrontDeskWalkInService.register` delegates for a new patient.

  The patient is flushed **before** the slot or token work, so a refusal happens before any slot, token or booking is written. The exception propagates, the request's transaction rolls back, and no re-query happens after the failure.
- **`BookingExceptionHandler`:** maps the exception to 409 using `DuplicatePatientPhoneErrorResponse(error, message, existingPatient)`, following the `RateLimitedErrorResponse` precedent.

## Frontend

- Each API client (staff `BookSlotForm` and `QueueBookSlotForm`, and the front-desk walk-in API) already surfaces the error body. Each of the three forms handles `PATIENT_PHONE_ALREADY_REGISTERED`:
  - it keeps the form state;
  - it shows an alert with the existing patient's name and a **Use this patient** button, which switches to existing-patient mode with that id selected;
  - with no `existingPatient`, it shows the search hint instead.

## Tests (first, red)

- **Integration** `DuplicatePatientPhoneTest` (real Postgres):
  - for each path: the collision gives 409 with `existingPatient` and no artifacts;
  - cross-clinic success;
  - a linked-patient phone succeeds;
  - for each path, two concurrent registrations give exactly one 201 and one 409, one patient, and no orphan booking or slot.
- **Unit** `WalkInPatientRegistrarTest`: the pre-check hit, a constraint-name match, another constraint rethrown, and a blank phone.
- **Vitest:** each form on a 409 keeps its values and shows the message, and **Use this patient** sends `patientId`.

## Constitution check

- Test-first: yes.
- No schema change.
- Tenant-scoped: the lookup is by `clinicId` from the path.
- No identity merge.
- 066 and 067 locking are untouched: the patient INSERT happens before `issueNextSlot`'s session lock.
