# Research: Forms & Inline Validation Consistency Pass

## Decision 1: A shared `FormField` wrapper, extracted from 2 real, already-duplicated implementations — not a forced `Input`/`Select` migration

**Decision**: Extract `frontend/src/components/FormField.tsx` (`label`, `htmlFor`, `required?`, `error?`, `hint?`, `children`) from `SignupForm.tsx`'s local `Field` (the fuller of the two — `RegistrationForm.tsx`'s is a strict subset missing `hint`). The 5 target forms wrap their *existing* native `<input>`/`<select>`/`<textarea>` markup in it, unchanged otherwise. `SignupForm.tsx`/`RegistrationForm.tsx` are also migrated onto it, removing both local duplicates. `Input`/`Select` (046) are **not** touched — verified zero consumer for an error prop exists anywhere (this feature's 8 forms all use native fields via `FormField`, not `Input`/`Select`), so adding one would be a speculative extension point with no real caller, exactly what Constitution Principle II forbids (caught during analyze, corrected before implementation).

**Rationale**: Verified the backlog's premise false on two counts: `Input`/`Select` have no error slot, and none of the 5 target forms use them at all — all 5 hand-roll native fields styled with the existing `.input` CSS class. Rather than forcing an unmotivated migration onto `Input`/`Select` (rewriting working markup for no functional gain), a real, already-proven pattern for exactly this need already exists in production, duplicated twice (`SignupForm.tsx`, `RegistrationForm.tsx`) — the textbook case for extraction (Constitution Principle II), and lower-risk than a wholesale field-markup rewrite across 5 forms.

**Alternatives considered**: Add `error` directly to `Input`/`Select` and migrate all 5 forms onto them — rejected, larger diff and real risk (rewriting 5 forms' field markup) for the same visual/functional outcome the wrapper already achieves; leave `SignupForm`/`RegistrationForm`'s duplication alone since it's not in the named 5 — rejected, once the shared component exists, leaving 2 confirmed duplicates unmigrated when the fix is a same-shape one-line swap is exactly the kind of drift this feature exists to close.

## Decision 2: Migrate the 4 remaining `api.ts` files onto `apiClient`, mirroring 043's fix exactly

**Decision**: `scheduling/api.ts`, `staff-booking/api.ts` (shared by staff `BookSlotForm`/`WalkInForm`), `staff-onboarding/api.ts`, `consultation-notes/api.ts` each drop their own local `*ApiError` class (and its `defaultMessageFor(body) ?? body.message` dead-code bug) in favor of `apiRequest`/`ApiError` from `../../lib/apiClient`.

**Rationale**: Verified all 4 have the identical, already-diagnosed bug 043 fixed in 4 *other* files — this feature's own forms depend on it being fixed here too, and 043 itself named "the remaining 27 files" as a stated, explicit follow-up rather than a hidden gap. Reusing the exact same client (not a second implementation) keeps one fix, one place.

**Alternatives considered**: Fix each file's own `defaultMessageFor(body) ?? body.message` bug in place, in each local error class, without adopting the shared client — rejected, would re-solve the same problem 4 times with 4 slightly different implementations instead of reusing the one already-correct, already-tested client.

## Decision 3: Per-form client-side validation rules — each verified against real backend business rules, not invented

**Decision**: Client-side pre-submit checks mirror only already-implemented backend rules, per form:

- **`ScheduleForm.tsx`**: at least one day of week selected; `startTime` strictly before `endTime` — verified exact wording in `ScheduleService.java` ("At least one day of the week is required", "startTime must be strictly before endTime"). `slotIntervalMinutes` positive-in-FIXED_TIME/absent-in-QUEUE is already correctly handled by the form's existing conditional field rendering + `min={1}`/`required` HTML5 attributes — no new client check needed there.
- **`OnboardStaffForm.tsx`**: required `name`/`email`/`role` (mirrors `OnboardStaffRequest`'s `@NotBlank` fields) and Indian mobile number format for the mobile field, using the *exact* backend pattern (`backend/src/main/java/com/cms/common/IndianMobileNumberValidator.java`: `^(?:\+91|0)?[6-9]\d{9}$`) — not a re-derived approximation.
- **staff `BookSlotForm.tsx`/`WalkInForm.tsx`**: required patient-identifying fields already marked `required` via HTML5 today; inline validation formalizes the same required-ness through `FormField`'s error slot rather than only the browser's native tooltip, matching FR-004's "required fields" floor. No `@Size`-constraint client mirror is added speculatively — the bean-validation edge case verified in research is narrow and not the common failure path.
- **`ConsultationNoteForm.tsx`**: non-blank content — the backend runs no server-side validation on this field at all (verified: no `@Valid`, no constraints), so this client-side check is the *only* validation surfacing before the note is saved; still a real, already-implied rule (an empty consultation note is meaningless), not an invented one.
- **patient `BookSlotForm.tsx`**: already migrated onto `apiClient` (043); this feature only wraps its fields in `FormField` for visual/structural consistency with the other 4 — no new validation rule needed beyond what US2's required-field floor already covers.

**Rationale**: Verified each rule against the actual backend code (service methods, validator classes, DTO annotations) rather than assumed from the backlog's own generic phrasing ("Indian mobile number format, password policy" — password policy doesn't apply to any of these 5 forms; none has a password field).

**Alternatives considered**: A generic, rule-agnostic "required-field checker" applied uniformly with no per-form specificity — rejected, loses the real value of mirroring each form's actual documented rule (e.g. `ScheduleForm`'s day-of-week/time-order checks, which no generic required-field pass would catch since neither is a single required input).

## Decision 4: Field-mapped backend errors — only for `OnboardStaffForm`, verified as the sole form with genuinely field-identifying errors today

**Decision**: Only `OnboardStaffForm.tsx` maps a backend error to a specific field (`MISSING_REQUIRED_FIELD`/`INVALID_MOBILE_NUMBER`, each carrying a real `field` name via `ErrorResponse.withField`). Every other backend error, on any of the 5 forms, stays a top-level banner.

**Rationale**: Verified per form: `ScheduleForm`/`ConsultationNoteForm`'s backends have almost no field-level validation at all (business-rule exceptions are field-agnostic by nature); `BookSlotForm`/`WalkInForm`'s common failure paths are domain exceptions (slot taken, no fee configured), also field-agnostic — their narrow bean-validation edge case exists but isn't the real, common path worth building a field-mapping UI around. Forcing every one of these into an artificial field mapping would violate FR-006 and produce a worse UX (a banner reads more naturally for "this slot was just taken by someone else" than a field-highlighted error would).

**Alternatives considered**: Build a generic error-to-field mapper reusable across all 5 forms in case a future field-identifying error appears — rejected as speculative (Principle II); the 1 real case today (`OnboardStaffForm`) is simple enough as a direct `switch` on `err.body.error`, matching the existing pattern already used in every one of these forms' catch blocks.
