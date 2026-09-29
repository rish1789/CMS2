# Feature Specification: Forms & Inline Validation Consistency Pass

**Feature Branch**: `054-forms-validation-consistency`

**Created**: 2026-09-15

**Status**: Draft

**Input**: User description: "backlog/051-forms-inline-validation-consistency-pass.md" — add per-field inline validation error display to 5 named high-traffic forms (`ScheduleForm`, `BookSlotForm`, `WalkInForm`, `OnboardStaffForm`, `ConsultationNoteForm`), building on 043's API-client fix and 046's shared `Input`/`Select` components.

## Corrections to the backlog brief

Verified during specification (not assumed):

1. **046's `Input`/`Select` have no error-message slot today, AND none of the 5 named forms use `Input`/`Select` at all.** The backlog assumes "046's shared `Input`/`Select` components" already support an error slot and that these forms already use them ("Every form using 046's shared `Input`/`Select` components MUST support an inline error-message slot") — verified false on both counts: neither component has an `error` prop today, and all 5 forms hand-roll plain native `<input>`/`<select>`/`<textarea>` markup, not `Input`/`Select`. **A better-grounded design was found in the process**: `frontend/src/features/patient-account/SignupForm.tsx` and `frontend/src/features/clinic-registration/RegistrationForm.tsx` each already have their own near-identical local `Field` wrapper component (`label`/`htmlFor`/`required`/`error`/`hint`/`children`) — real, already-working, already-duplicated (2 copies) prior art for exactly this feature's need. This feature extracts that pattern into one shared `FormField` component (rather than baking an `error` prop directly into `Input`/`Select`) and wraps each of the 5 forms' *existing* native field markup in it — no forced migration onto `Input`/`Select`, which would be a larger, unmotivated rewrite for no functional gain over the proven wrapper pattern. `Input`/`Select` still separately gain an `error` prop too (the backlog's literal ask, and useful for future forms already using them going forward), but that is no longer the critical path for these 5 forms specifically.
2. **043 only migrated 4 of 31 `api.ts` files onto the shared `apiClient`** — and none of this feature's 5 named forms' own `api.ts` files (`scheduling/api.ts`, `staff-booking/api.ts` [shared by staff `BookSlotForm`/`WalkInForm`], `staff-onboarding/api.ts`, `consultation-notes/api.ts`) are among the 4 migrated. All 4 still have 043's own originally-identified dead-code bug (`defaultMessageFor(body) ?? body.message`, where the `??` never falls through to the real backend message) **independently, in their own files** — meaning backend error messages are *not yet actually reachable* for 4 of these 5 forms, contradicting this feature's own stated dependency on 043 having already fixed this everywhere. (The 5th, `patient-booking/BookSlotForm.tsx`, *is* already migrated — confirmed via 043's own progress note that this was the specific file the bug was fixed against.)
3. **Most backend errors for these forms are genuinely field-agnostic, not field-identifying.** Verified per form: `ScheduleForm`'s and `ConsultationNoteForm`'s backend endpoints have almost no server-side field validation at all (`CreateScheduleRequest`/`CreateConsultationNoteRequest` have no `@Valid`, no constraint annotations — their real validation is business-rule exceptions like schedule-overlap, inherently field-agnostic). `BookSlotForm`/`WalkInForm`'s backend DTOs have `@Size` constraints (bean-validation, genuinely field-identifying when tripped) but their actual, common failure paths are domain exceptions (slot already booked, no fee configured) that are field-agnostic by nature. Only `OnboardStaffForm`'s backend has 2 genuinely field-identifying, already-implemented error types (`MISSING_REQUIRED_FIELD`, `INVALID_MOBILE_NUMBER`, each carrying a real `field` name) — currently discarded by the same dead-code bug.

These corrections don't shrink this feature's real value — they clarify *where* each kind of validation feedback actually belongs: client-side inline checks for already-known, already-documented rules (every form), backend-message reachability fixes (4 of 5 forms), and field-mapped backend errors specifically where the backend genuinely provides one (`OnboardStaffForm` today).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Backend validation messages are actually reachable for every named form (Priority: P1)

A user submitting `ScheduleForm`, staff `BookSlotForm`, `WalkInForm`, `OnboardStaffForm`, or `ConsultationNoteForm` with invalid data wants to see the backend's real, specific error message — not a generic fallback string, because the real message was silently discarded by a bug.

**Why this priority**: Every other part of this feature (field mapping, consistent display) is meaningless if the underlying message is still discarded — this is the same class of fix 043 already made for 4 other files, applied to the 4 files this feature's own forms depend on that 043 didn't reach.

**Independent Test**: Trigger a real backend validation failure on each of the 4 not-yet-migrated forms and confirm the displayed message is the backend's actual message, not a hardcoded generic one.

**Acceptance Scenarios**:

1. **Given** `scheduling/api.ts`, staff `staff-booking/api.ts`, `staff-onboarding/api.ts`, and `consultation-notes/api.ts`, **When** migrated onto the shared `apiClient`, **Then** each form's error banner shows the backend's real message on a domain-exception failure, with its local `*ApiError` class and dead-code bug removed.
2. **Given** the migration, **When** any of these 4 forms' existing tests run, **Then** they pass unmodified except where a test specifically asserted the old, wrong (fallback-only) message — that one is deliberately corrected, not silently left wrong.

---

### User Story 2 - Every field-level input can show its own inline error (Priority: P1)

A user filling out any of the 5 named forms wants a validation problem shown right next to the field it concerns — for a check the frontend can already determine on its own (a required field left blank, an Indian mobile number in the wrong format) — as they type or on submit attempt, not only in a generic banner after a round-trip to the server.

**Why this priority**: The core, named user value in the backlog — immediate feedback for checks that don't need a server round-trip at all, equally important to the P1 reachability fix since together they are what "inline validation" actually means.

**Independent Test**: On each of the 5 forms, leave a required field blank (or enter an invalid mobile number where that field exists) and attempt submit; confirm an inline message appears at that specific field, not only in a banner, and the field's other already-valid values are not cleared.

**Acceptance Scenarios**:

1. **Given** the new shared `FormField` component (extracted from `SignupForm.tsx`/`RegistrationForm.tsx`'s existing, duplicated local `Field`), **When** used to wrap a form field, **Then** it renders a label, the field itself, and an inline error message with `role="alert"`, matching the exact accessible pattern those 2 existing forms already established.
2. **Given** each of the 5 forms' fields wrapped in `FormField`, **When** a user leaves a required field blank and attempts to submit, **Then** an inline error appears at that field before any network request is made, mirroring an already-documented rule (required-ness, or Indian mobile number format where that field exists) — not a new rule invented for this feature.
3. **Given** a submission blocked by inline validation, **When** the user corrects the field, **Then** every other field's already-entered value is preserved exactly as typed.
4. **Given** `SignupForm.tsx` and `RegistrationForm.tsx`'s own pre-existing local `Field` components, **When** this feature ships, **Then** both are migrated onto the new shared `FormField`, removing the duplication that prompted extracting it in the first place — with zero change to either form's own behavior.

---

### User Story 3 - A genuinely field-identifying backend error highlights that field (Priority: P2)

A ClinicAdmin onboarding a new staff member with an invalid mobile number or a blank required field wants the backend's own validation error to appear at that specific field, using the backend's real wording — not just in a generic banner, since the backend already tells the frontend exactly which field is wrong.

**Why this priority**: Secondary to US1/US2 because it depends on both (reachability + a rendering slot) and applies to only 1 of the 5 forms today (the only one with genuinely field-identifying backend errors, verified) — but still real, backlog-named value once its prerequisites exist.

**Independent Test**: Submit `OnboardStaffForm` with a blank required field or an invalid mobile number and confirm the backend's `MISSING_REQUIRED_FIELD`/`INVALID_MOBILE_NUMBER` error (with its real `field` and `message`) renders at that specific field, not only in the banner.

**Acceptance Scenarios**:

1. **Given** `OnboardStaffForm`'s backend returns `ErrorResponse.withField(...)` for a missing field or an invalid mobile number, **When** the form catches that error, **Then** the corresponding `Input`'s `error` prop shows the backend's real message at that field, and no redundant top-level banner duplicates it.
2. **Given** any other backend error from any of the 5 forms that does *not* identify a field (verified: the large majority, today), **When** it occurs, **Then** it continues to show as the existing top-level banner — this feature does not force an artificial field mapping onto a field-agnostic error.

---

### Edge Cases

- What happens to a backend error that's genuinely field-agnostic (e.g. "slot already booked", "schedule overlaps an existing one")? Stays as the existing top-level banner — per the backlog's own explicit rule, and verified true for the large majority of these 5 forms' real failure paths.
- What happens to `ScheduleForm`/`ConsultationNoteForm`, whose backends have almost no field-level validation at all? Their inline-validation value (US2) comes from client-side checks of already-known rules (e.g. end-time-after-start-time for schedules, non-blank content for notes) — this feature does **not** add new backend `@Valid`/constraint annotations to manufacture field-level backend errors that don't exist today, since changing backend validation rules is explicitly out of scope (backlog's own boundary).
- What happens to a form-management library need? None — every migration builds on the same component-local `useState` pattern already used throughout the codebase, per the backlog's explicit "no new dependency" rule.
- What happens to already-entered, still-valid field values when one field fails validation? They must remain exactly as typed — verified as already true today (React re-render preserves controlled-input state unless the component unmounts) and re-confirmed by test for each migrated form, not just assumed.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: `frontend/src/features/scheduling/api.ts`, `frontend/src/features/staff-booking/api.ts`, `frontend/src/features/staff-onboarding/api.ts`, and `frontend/src/features/consultation-notes/api.ts` MUST be migrated onto the shared `apiClient` (`apiRequest`/`ApiError`), removing each file's own local `*ApiError` class and dead-code bug — mirroring 043's own fix pattern exactly.
- **FR-002**: A new shared `FormField` component MUST be extracted from `SignupForm.tsx`/`RegistrationForm.tsx`'s existing, duplicated local `Field` components (`label`/`htmlFor`/`required`/`error`/`hint`/`children`). `Input`/`Select` (046) are explicitly **not** extended with an error prop by this feature — verified no form in this feature's scope uses either, and no other current consumer exists; adding one with zero consumer would be exactly the speculative extension point Constitution Principle II forbids. `FormField` alone satisfies every real need this feature has.
- **FR-003**: `ScheduleForm.tsx`, both `BookSlotForm.tsx` components (patient and staff), `WalkInForm.tsx`, `OnboardStaffForm.tsx`, and `ConsultationNoteForm.tsx` MUST wrap their existing native field markup in the shared `FormField` — no forced migration onto `Input`/`Select`, which would rewrite working markup for no functional gain over the proven wrapper pattern. `SignupForm.tsx`/`RegistrationForm.tsx` MUST also migrate onto the shared `FormField`, removing their own local duplicate definitions.
- **FR-004**: Each of the 5 forms MUST perform client-side pre-submit validation for its own already-documented rules (required fields; Indian mobile number format where that field exists), showing the result via FR-002's inline slot — before any network request, not only after a failed one.
- **FR-005**: `OnboardStaffForm.tsx` MUST map its backend's genuinely field-identifying errors (`MISSING_REQUIRED_FIELD`, `INVALID_MOBILE_NUMBER`) to the corresponding field's inline error slot, using the backend's real message — not a client-side re-derivation of it.
- **FR-006**: Every other backend error from any of the 5 forms (verified: the majority) MUST continue to render as the existing top-level banner — this feature MUST NOT invent an artificial field mapping for a field-agnostic error.
- **FR-007**: This feature MUST NOT change any backend validation rule, add any new backend constraint annotation, or introduce a form-management library — all explicitly out of scope per the backlog.
- **FR-008**: No already-entered, still-valid field value may be lost when a validation error (client-side or server-side) occurs on submission.

### Key Entities

N/A — no data model changes; this is a frontend consistency pass consuming an already-existing backend error shape (`ErrorResponse.withField`).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: All 4 not-yet-migrated forms' backend error messages are the real backend message, verified by test, where today they show a hardcoded generic fallback instead.
- **SC-002**: Every one of the 5 forms shows an inline, field-specific error for at least one already-documented client-checkable rule, verified by test.
- **SC-003**: `OnboardStaffForm` shows the backend's real field-identifying error at the correct field for both of its 2 genuinely field-identifying error types, verified by test.
- **SC-004**: Zero already-entered, valid field values are lost on a validation failure, verified by test on each of the 5 forms.
- **SC-005**: The full frontend test suite passes after this feature with zero regressions to the 5 forms' existing submit/success/loading behavior, plus new tests for the above.

## Assumptions

- The 5-form scope named in the backlog (`ScheduleForm`, both `BookSlotForm`s, `WalkInForm`, `OnboardStaffForm`, `ConsultationNoteForm`) is kept as-is — verified all 5 are real, distinct, high-traffic forms; no form added or removed from the backlog's own named floor.
- Client-side inline validation is limited to rules already documented/enforced elsewhere in this system (required-ness, Indian mobile number format) — no new business rule is invented purely to have something to validate client-side (Constitution Principle II).
- `Input`/`Select`'s new `error` prop follows this project's existing `aria-invalid`/`role="alert"`-adjacent accessibility conventions already used for top-level banners elsewhere in the codebase.
