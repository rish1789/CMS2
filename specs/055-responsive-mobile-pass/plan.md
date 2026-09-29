# Implementation Plan: Responsive & Mobile Pass

**Branch**: `055-responsive-mobile-pass` | **Date**: 2026-09-15 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/055-responsive-mobile-pass/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

A verification-and-fix pass, not a redesign: live-check every screen built by 046-051 (Staff/Admin shell, 6 table-based screens, 8 forms, day sheet/session views) at ~400px and ~768px viewports using this project's existing Browser-pane responsive-testing workflow, and fix only the genuine defects verification finds. Three of the backlog's four named surfaces (shell, forms, `prefers-reduced-motion`) are already confirmed correct by code inspection before any browser check (spec.md's "Corrections to the backlog brief") — this plan's real work is the live verification itself, plus whatever narrow, targeted fixes it surfaces, concentrated on the 6 table screens and the day sheet/session views the backlog names as highest-risk.

## Technical Context

**Language/Version**: TypeScript 5 / React 18 — existing frontend stack, no change

**Primary Dependencies**: No new dependency — Tailwind v4 utility classes already defined in `frontend/src/index.css`'s `@theme` block; the Browser-pane preview tooling already used for every prior 046-051 live-verification pass.

**Storage**: N/A — no data change.

**Testing**: Vitest + React Testing Library for any new/updated automated test (a fixed defect that has a stable, testable DOM signature); the primary verification method for this feature is live browser inspection at multiple viewport widths, since responsive layout is fundamentally a rendered-output property jsdom does not simulate.

**Target Platform**: Web (existing Vite/React SPA), verified at ~400px (mobile), ~768px (tablet), and desktop (≥1024px) viewport presets.

**Project Type**: Frontend-only (no backend change — this is CSS/layout only).

**Performance Goals**: No new goal.

**Constraints**: Zero desktop-width (≥640px) visual regression; zero new dependency; zero new backend endpoint; no wholesale redesign of any screen already confirmed correct.

**Scale/Scope**: 2 shells (regression-check only), 6 table-based screens (verify + targeted fix if needed), 8 forms (verify + patient-facing touch-target check), 2 day-sheet/session-view screens (named highest-risk, individually verified).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Principle I (Test-First)**: N/A strictly (frontend CSS/layout, no backend logic) — any defect fix that has a stable DOM-testable signature (e.g. a column newly hidden below a breakpoint) gets a regression test; a pure visual/CSS-only fix (e.g. an adjusted `min-w` value) is verified live, matching this project's own established practice for prior visual-only changes.
- **Principle II (Simplicity & YAGNI)**: PASS — spec.md's corrections explicitly reject building 6 new card-view table layouts and a bespoke day-sheet mobile mode in favor of verifying the already-working, already-sanctioned contained-scroll pattern first; a targeted fix is used only where live verification finds a genuine gap, never a preemptive rebuild.
- **Principle III (Modular, Library-First Architecture)**: N/A — no backend module touched.
- **Principle IV (Data Privacy & Integrity by Design)**: N/A — no data handling change.

No violations — Complexity Tracking table not needed.

## Project Structure

### Documentation (this feature)

```text
specs/055-responsive-mobile-pass/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
└── quickstart.md        # Phase 1 output (/speckit-plan command)
```

No `data-model.md`/`contracts/` — no data model or API contract changes; this feature is a frontend layout verification-and-fix pass with no new interface.

### Source Code (repository root)

```text
frontend/
├── src/
│   ├── components/
│   │   └── SidebarDrawer.tsx                                         # VERIFY only (FR-001) - regression re-check, no planned edit
│   └── features/
│       ├── day-sheet/{DaySheet.tsx,SessionSlotsView.tsx}              # VERIFY + targeted fix if found (FR-002, FR-005)
│       ├── staff-picker/StaffPicker.tsx                                 # VERIFY + targeted fix if found (FR-002)
│       ├── doctor-picker/DoctorPicker.tsx                                # VERIFY + targeted fix if found (FR-002)
│       ├── clinic-verification/PendingClinicsList.tsx                     # VERIFY + targeted fix if found (FR-002)
│       ├── doctor-verification/PendingDoctorsList.tsx                      # VERIFY + targeted fix if found (FR-002)
│       ├── scheduling/ScheduleForm.tsx                                       # VERIFY only (FR-003)
│       ├── staff-booking/{BookSlotForm.tsx,WalkInForm.tsx}                    # VERIFY only (FR-003)
│       ├── staff-onboarding/OnboardStaffForm.tsx                                # VERIFY only (FR-003)
│       ├── consultation-notes/ConsultationNoteForm.tsx                           # VERIFY only (FR-003)
│       ├── patient-booking/BookSlotForm.tsx                                       # VERIFY + touch-target check (FR-003, FR-004)
│       ├── patient-account/SignupForm.tsx                                          # VERIFY + touch-target check (FR-003, FR-004)
│       └── clinic-registration/RegistrationForm.tsx                                 # VERIFY only (FR-003)
└── tests/
    (updated only for a screen where a genuine, DOM-testable defect fix is made)
```

**Structure Decision**: Frontend-only, no new directory. Every file above is a **verification target**; only a file where live verification finds a real defect gets an actual code edit, per spec.md's own corrected scope. No file outside this list is touched.

## Complexity Tracking

*No violations — table not needed.*
