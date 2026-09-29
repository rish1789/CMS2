# Implementation Plan: Forms & Inline Validation Consistency Pass

**Branch**: `054-forms-validation-consistency` | **Date**: 2026-09-15 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/054-forms-validation-consistency/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

Extract a shared `FormField` component from 2 existing, duplicated local implementations (`SignupForm.tsx`, `RegistrationForm.tsx`), migrate both onto it, and wrap the 7 target forms' (5 named + patient `BookSlotForm` + the 2 dedup targets) existing native field markup in it. Migrate 4 not-yet-converted `api.ts` files onto the shared `apiClient` (mirroring 043's fix). Add per-form client-side pre-submit checks mirroring already-verified backend rules. Map `OnboardStaffForm`'s 2 genuinely field-identifying backend errors to their fields; every other backend error stays a top-level banner. `Input`/`Select` are explicitly not touched — no consumer for an error prop exists (caught and corrected during analyze).

## Technical Context

**Language/Version**: TypeScript 5 / React 18 — existing frontend stack, no change

**Primary Dependencies**: No new dependency — no form-management library (explicitly out of scope); reuses `apiClient` (043) and component-local `useState`, the existing convention throughout the codebase.

**Storage**: N/A — no data change. Consumes an already-existing backend error shape (`ErrorResponse.withField`) unchanged.

**Testing**: Vitest + React Testing Library — existing tests for the 5 named forms are the regression baseline; new tests for `FormField`, the 4 migrated `api.ts` files' error-reachability, and each form's inline-validation/field-mapping behavior.

**Target Platform**: Web (existing Vite/React SPA).

**Project Type**: Frontend-only (no backend change — FR-007 explicitly forbids any backend validation-rule change; this feature only changes how already-correct, already-implemented validation is displayed).

**Performance Goals**: No new goal — client-side checks are synchronous string/array operations on already-in-memory form state.

**Constraints**: Zero new visual tokens; zero new backend endpoint or constraint; zero new dependency; zero regression to any of the 7 forms' existing submit/success/loading behavior.

**Scale/Scope**: 1 new shared component (`FormField`) + 1 extended pair (`Input`/`Select` gain `error`), 4 `api.ts` migrations, 7 forms wrapped in `FormField` (5 named + patient `BookSlotForm` + 2 dedup targets), field-mapping logic in 1 form (`OnboardStaffForm`).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Principle I (Test-First)**: N/A strictly (frontend-only, no backend logic) — existing tests for all 7 touched forms are the regression baseline, updated only where behavior genuinely changes (a message that was wrong before is now correct; a new inline error appears where none did) per file, never silently.
- **Principle II (Simplicity & YAGNI)**: PASS — `FormField` is extracted from real, already-duplicated code (not invented); no form-management library added; client-side validation rules are each verified against an already-implemented backend rule, none invented; field-mapping is built only for the 1 form that genuinely has field-identifying backend errors today, not generalized speculatively.
- **Principle III (Modular, Library-First Architecture)**: N/A — no backend module touched.
- **Principle IV (Data Privacy & Integrity by Design)**: N/A — no data handling change; validation display only, never altering what's actually persisted or how the backend authorizes/validates.

No violations — Complexity Tracking table not needed.

## Project Structure

### Documentation (this feature)

```text
specs/054-forms-validation-consistency/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
└── quickstart.md         # Phase 1 output (/speckit-plan command)
```

No `data-model.md`/`contracts/` — no data model or API contract changes; consumes an already-existing backend error shape unchanged.

### Source Code (repository root)

```text
frontend/
├── src/
│   ├── components/
│   │   └── FormField.tsx                                   # NEW - extracted from SignupForm/RegistrationForm
│   └── features/
│       ├── scheduling/{ScheduleForm.tsx,api.ts}                 # EDIT - FormField wrap + apiClient migration + validation
│       ├── staff-booking/{BookSlotForm.tsx,WalkInForm.tsx,api.ts} # EDIT - same
│       ├── staff-onboarding/{OnboardStaffForm.tsx,api.ts}          # EDIT - same + field-mapped backend errors
│       ├── consultation-notes/{ConsultationNoteForm.tsx,api.ts}     # EDIT - same
│       ├── patient-booking/BookSlotForm.tsx                          # EDIT - FormField wrap only (api.ts already migrated)
│       ├── patient-account/SignupForm.tsx                             # EDIT - dedup onto shared FormField
│       └── clinic-registration/RegistrationForm.tsx                    # EDIT - dedup onto shared FormField
└── tests/
    └── components/FormField.test.tsx                                    # NEW
    (existing test files for each of the 8 touched forms updated as needed)
```

**Structure Decision**: Frontend-only. `FormField` lives in `frontend/src/components/` alongside 046's other shared UI (same location convention). All other edits are within already-established feature directories — no new directory structure, no backend directory touched.

## Complexity Tracking

*No violations — table not needed.*
