# 051 — Forms & Inline Validation Consistency Pass

**Module:** Frontend / Design System Application
**Status:** Ready for spec-kit intake

## User Story
As a user filling out any form in CMS2 (staff booking a slot, scheduling a doctor, a patient joining a waitlist), I want clear field-level validation errors as I type or on submit, consistent loading/disabled/success states, and confidence that a server-side error tells me specifically what went wrong, so that I'm never left guessing whether my input was valid or whether my action succeeded.

## Context
Verified current state (2026-09-15): sampled forms (`ScheduleForm.tsx`, `BookSlotForm.tsx`) already do some things right — both show a red `role="alert"` error banner on failure, disable-and-relabel the submit button while in flight ("Saving…"/"Booking…"), and show a distinct success panel on completion. What's missing: **neither does per-field inline validation** — both rely solely on native HTML5 `required`/`min`/`type` attribute constraints, with no field-level error messages telling the user *which* field is wrong and *why* before or during submission.

This feature is explicitly downstream of 043 (frontend shared API client) — that feature fixes the underlying bug where backend-specific validation messages were unreachable dead code. This feature's job is to actually *display* those now-correctly-surfaced messages at the right field, not just in a generic top-of-form banner.

## Business Rules
- Every form using 046's shared `Input`/`Select` components MUST support an inline error-message slot tied to that specific field, not just a form-level banner.
- Client-side validation MUST mirror (not replace) existing backend validation rules already documented per-feature (e.g. Indian mobile number format, password policy, `@Size`/`@DecimalMin` constraints added in a prior audit pass) — the backend remains the source of truth and the final authority; client-side validation is purely a faster feedback loop, per the constitution's "do not rely only on browser validation" instruction is actually about the *reverse* (backend must validate too, which it already does) — this feature adds client-side inline checks *in addition to*, never *instead of*, existing server-side validation.
- A server-side validation error (now correctly surfaced per 043) MUST be mapped to the specific field it concerns wherever the backend error identifies one (e.g. a "fee override must be non-negative" error highlights the fee field, not just a generic banner) — if the backend error is genuinely field-agnostic (e.g. "slot no longer available"), a top-level banner remains the correct pattern; don't force every error into an artificial field mapping.
- Do not lose entered form data on a validation error or failed submission — verify this is already true (native form state is typically preserved by React re-render unless a component unmounts) and treat any case where it isn't as a bug to fix, not a known limitation to accept.
- This feature must migrate a defined, named set of high-traffic forms (at minimum: `ScheduleForm.tsx`, `BookSlotForm.tsx`, `WalkInForm.tsx`, `OnboardStaffForm.tsx`, `ConsultationNoteForm.tsx`) — the exact list should be finalized at planning time based on real usage frequency, not attempted as "every form in the app" in one pass given the scale.

## Acceptance Criteria
- Given one of the migrated forms, when a user enters invalid data in a specific field and moves focus away (or attempts submit), then an inline error message appears next to that specific field, not only in a generic top banner.
- Given a backend validation error that identifies a specific field (e.g. fee, phone number, password), when the form receives that error after 043's fix, then the specific message is shown at the corresponding field, using the backend's actual wording, not a generic client-side re-derivation of it.
- Given a user who submits a migrated form with invalid data, when the error is shown, then their already-entered valid field values remain in the form (not cleared).
- Given the full frontend test suite, when run after this feature, then all existing tests for migrated forms pass, plus new tests asserting inline field-level error display for at least one realistic validation failure per migrated form.

## Dependencies
- Depends on 043 (frontend shared API client) — this feature is only meaningful once backend-specific error messages are actually reachable.
- Depends on 046 (shared UI component library) for the `Input`/`Select` components with built-in error-slot support.

## Explicitly Out of Scope
- Migrating every form in the app in one pass — scope is the named high-traffic set above; additional forms can be a follow-up if wanted.
- Introducing a form-management library (e.g. react-hook-form, Formik) — build on existing component-local state patterns already used throughout the codebase, consistent with the constitution's "no unnecessary new dependencies."
- Changing any backend validation rule itself — this feature only changes how existing, already-correct validation is displayed client-side.

## Source References
- `PRODUCTION_ROADMAP.md` §1.3 (the dead-code error-message bug this feature depends on 043 fixing)
- `HANDOFF.md` Part 2 (items 9-10: the `@Size`/`@DecimalMin` validation additions this feature will finally surface correctly at the field level)
- Verified against current repository state via direct inspection, 2026-09-15 (`ScheduleForm.tsx`/`BookSlotForm.tsx` confirmed to rely solely on native HTML5 constraints with no field-level inline error display)
