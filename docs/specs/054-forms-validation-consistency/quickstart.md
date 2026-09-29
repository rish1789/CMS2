# Quickstart: Forms & Inline Validation Consistency Pass

## Automated verification

1. `cd frontend && npx tsc -b` — zero type errors.
2. `npm run lint` — zero new lint errors.
3. `npx vitest run tests/components/FormField.test.tsx` — new component tests pass.
4. `npx vitest run tests/scheduling tests/staff-booking tests/staff-onboarding tests/consultation-notes tests/patient-booking tests/patient-account tests/clinic-registration` (adjust to actual test locations) — confirm zero regression plus new inline-validation/field-mapping tests pass.
5. `npm run test -- --run` — full suite, zero regression.

## Manual/live verification

6. Submit `ScheduleForm` with zero days selected, and again with an end time before the start time — confirm both show inline errors at the right field(s) before any network request.
7. Submit `OnboardStaffForm` with a blank name and, separately, an invalid mobile number — confirm the backend's real field-identifying error appears at the correct field, not just a banner.
8. Submit `WalkInForm`/staff `BookSlotForm` with a required field blank — confirm an inline error, and confirm a genuinely field-agnostic backend error (e.g. slot already booked) still shows as the existing top-level banner.
9. Submit `ConsultationNoteForm` with blank content — confirm an inline error at the content field.
10. Trigger a real backend validation failure on each of the 4 newly-migrated forms — confirm the message shown is the backend's actual message, not a generic fallback.
11. On any form, trigger a validation error after filling in several fields correctly — confirm none of the other, valid field values are cleared.
12. Confirm `SignupForm.tsx`/`RegistrationForm.tsx` still work exactly as before, now rendering through the shared `FormField`.
