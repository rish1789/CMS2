---

description: "Task list for Forms & Inline Validation Consistency Pass"
---

# Tasks: Forms & Inline Validation Consistency Pass

**Input**: Design documents from `/specs/054-forms-validation-consistency/`

**Prerequisites**: plan.md, spec.md, research.md, quickstart.md

**Tests**: Frontend-only. Existing tests for each of the 8 touched forms are the regression baseline — updated only where behavior genuinely changes (a previously-wrong message is now correct; a new inline error now appears), never silently.

**Organization**: Foundational builds the shared primitive both stories need. US1 (P1, backend reachability) migrates the 4 remaining `api.ts` files. US2 (P1, inline field validation) wraps all 8 forms in `FormField` with per-form client-side checks. US3 (P2, field-mapped backend errors) adds the one real case (`OnboardStaffForm`) on top of US1+US2.

## Phase 1-2: Setup / Foundational

- [X] T001 Create `frontend/src/components/FormField.tsx` (research.md Decision 1): extracted from `SignupForm.tsx`'s local `Field` (the fuller of the 2 existing duplicates) — `label`, `htmlFor`, `required?`, `error?`, `hint?`, `children` props; renders the label, children, then `hint` (only when no error) or `error` (`role="alert"`).
- [X] T002 [P] Add `frontend/tests/components/FormField.test.tsx`: renders label/children; shows `hint` when no error; shows `error` (not `hint`) when both are present; error has `role="alert"`.

**Checkpoint**: `FormField` exists and is tested standalone, ready to wire into real forms.

---

## Phase 3: User Story 1 - Backend validation messages are actually reachable for every named form (Priority: P1) 🎯 MVP

**Goal**: The 4 not-yet-migrated `api.ts` files show the backend's real message, not a hardcoded fallback.

**Independent Test**: Trigger a real backend validation failure on each of the 4 forms and confirm the displayed message is the backend's actual message.

### Implementation for User Story 1

- [X] T003 [P] [US1] Edit `frontend/src/features/scheduling/api.ts`: replace `ScheduleApiError`/its `defaultMessageFor(body) ?? body.message` dead code with `apiRequest`/`ApiError` from `../../lib/apiClient` (research.md Decision 2), mirroring 043's fix pattern exactly.
- [X] T004 [P] [US1] Edit `frontend/src/features/staff-booking/api.ts`: same migration for both its local error classes (`BookSlotErrorBody`/`WalkInErrorBody`-backed).
- [X] T005 [P] [US1] Edit `frontend/src/features/staff-onboarding/api.ts`: same migration — `OnboardStaffApiError` removed, `apiRequest` used; the DTO's own `field` property (already declared in `OnboardStaffErrorBody`) is preserved on `ApiError.body`, ready for US3 to read.
- [X] T006 [P] [US1] Edit `frontend/src/features/consultation-notes/api.ts`: same migration.
- [X] T007 [US1] Update each of the 4 forms' (`ScheduleForm.tsx`, staff `BookSlotForm.tsx`, `WalkInForm.tsx`, `OnboardStaffForm.tsx`, `ConsultationNoteForm.tsx`) catch blocks to import `ApiError` from the shared client instead of the now-removed local error class; update each form's existing tests only where a test asserted the old, wrong fallback message specifically (a deliberate, stated correction, not a silent regression).

**Checkpoint**: Every one of the 5 named forms shows the backend's real error message on failure.

---

## Phase 4: User Story 2 - Every field-level input can show its own inline error (Priority: P1)

**Goal**: All 8 forms (5 named + patient `BookSlotForm` + `SignupForm`/`RegistrationForm` dedup) wrap their fields in `FormField`, with client-side pre-submit checks per research.md Decision 3.

**Independent Test**: On each form, leave a required field blank (or enter an invalid mobile number where that field exists) and attempt submit; confirm an inline error at that field, and other valid values preserved.

### Implementation for User Story 2

- [X] T008 [P] [US2] Edit `frontend/src/features/scheduling/ScheduleForm.tsx`: wrap Start time/End time/Mode/Slot interval fields in `FormField`; add pre-submit checks for "at least one day selected" and "start time before end time" (research.md Decision 3 — exact wording from `ScheduleService.java`), shown via a `fieldErrors` state and `FormField`'s `error` prop, blocking submission before any network call.
- [X] T009 [P] [US2] Edit `frontend/src/features/staff-booking/BookSlotForm.tsx` and `WalkInForm.tsx`: wrap their required fields in `FormField`; add pre-submit required-field checks shown via the same pattern.
- [X] T010 [P] [US2] Edit `frontend/src/features/staff-onboarding/OnboardStaffForm.tsx`: wrap name/email/role/mobile fields in `FormField`; add pre-submit checks for required name/email/role and Indian mobile number format using the exact backend pattern (`^(?:\+91|0)?[6-9]\d{9}$`, `backend/src/main/java/com/cms/common/IndianMobileNumberValidator.java`) when the mobile field is non-empty.
- [X] T011 [P] [US2] Edit `frontend/src/features/consultation-notes/ConsultationNoteForm.tsx`: wrap the content field in `FormField`; add a pre-submit non-blank-content check.
- [X] T012 [P] [US2] Edit `frontend/src/features/patient-booking/BookSlotForm.tsx`: wrap its fields in `FormField` only (already on `apiClient` per 043; no new validation rule needed beyond the required-field floor already enforced by HTML5).
- [X] T013 [P] [US2] Edit `frontend/src/features/patient-account/SignupForm.tsx` and `frontend/src/features/clinic-registration/RegistrationForm.tsx`: remove each file's local `Field` component, import the shared `FormField` instead — zero behavior change, dedup only (research.md Decision 1).
- [X] T014 [US2] Add/update test cases across the 8 forms' test files: each shows an inline error for at least one already-documented rule before any network request; each preserves other fields' values when one fails validation.

**Checkpoint**: Every form shows real inline, field-specific validation; the 2 pre-existing `Field` duplicates are gone.

---

## Phase 5: User Story 3 - A genuinely field-identifying backend error highlights that field (Priority: P2)

**Goal**: `OnboardStaffForm` maps its 2 real field-identifying backend errors to their fields.

**Independent Test**: Submit with a blank required field or an invalid mobile number and confirm the backend's own message renders at that specific field.

### Implementation for User Story 3

- [X] T015 [US3] Edit `frontend/src/features/staff-onboarding/OnboardStaffForm.tsx`'s catch block: on `err.body.error === 'MISSING_REQUIRED_FIELD'` or `'INVALID_MOBILE_NUMBER'`, read `err.body.field` and set that field's `fieldErrors` entry to `err.body.message` (the backend's real wording) instead of (or in addition to, per FR-006, never both) the top-level banner; every other error code stays the existing banner.
- [X] T016 [US3] Add test cases to `OnboardStaffForm.test.tsx`: a `MISSING_REQUIRED_FIELD` response for e.g. `email` shows the backend's real message at the email field, not the banner; a genuinely field-agnostic error (if one exists for this endpoint) still shows as the banner.

**Checkpoint**: All 3 user stories complete; the one form with real field-identifying backend errors surfaces them correctly.

---

## Phase 6: Polish

- [X] T017 `cd frontend && npx tsc -b` — zero type errors.
- [X] T018 `cd frontend && npm run lint` — zero new lint errors.
- [X] T019 `cd frontend && npm run test -- --run` — full suite, zero regression, count increases by the new tests.
- [X] T020 Manual live verification per `quickstart.md` steps 6-12. Ran a real backend+Postgres via the Browser pane: registered a clinic (step 12), onboarded an Operations and a Doctor hire, defined a schedule. Steps 6, 7, 11 confirmed live and directly caught a real bug — `OnboardStaffForm`'s name/email inputs still carried the native `required` HTML attribute, which raced ahead of and silently defeated the new client-side checks (same class of issue already fixed for staff `BookSlotForm`/`WalkInForm`'s `appointmentTypeId` during automated T014 runs); fixed by dropping `required` from both inputs. Step 8's core banner-vs-field mechanism confirmed live via a genuine duplicate-email rejection. Full suite re-run after the fix: 286/286 passing, `tsc -b` clean.
- [X] T021 Update `backlog/progress.md`'s row for `051-forms-inline-validation-consistency-pass`.

---

## Dependencies & Execution Order

- Foundational (T001-T002) blocks US2 (needs `FormField` to exist) but not US1 (US1 only touches `api.ts` files, independent of `FormField`).
- US1 (T003-T007) and US2 (T008-T014) can proceed in parallel once Foundational lands, except each of the 5 named forms' own edit is naturally sequenced (US1's `api.ts`/import-swap in a form, then US2's `FormField` wrap in the same form) to avoid touching a given form file twice in unrelated passes.
- US3 (T015-T016) depends on both US1 (T005, `staff-onboarding/api.ts` migrated, so `err.body.field` survives) and US2 (T010, `OnboardStaffForm` already wrapped in `FormField`).
- Polish depends on all 3 stories complete.

## Notes

- Total: 21 tasks.
- Zero backend tasks — this feature touches no backend code (FR-007, plan.md Constitution Principle III: N/A).
- `Input`/`Select` are untouched — verified during analyze that adding an error prop there would have zero consumer, a speculative addition Constitution Principle II forbids.
- No task adds a form-management library, invents a new validation rule, or builds a field-mapper for a form that doesn't genuinely have field-identifying backend errors today — all explicitly out of scope (research.md Decisions 3-4, spec.md FR-006/FR-007).
