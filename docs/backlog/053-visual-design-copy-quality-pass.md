# 053 — Visual Design & Copy Quality Pass

**Module:** Frontend / Design System Application
**Status:** Ready for spec-kit intake — **user discussion required before implementation**

## User Story
As the product owner reviewing the shipped 046-052 redesign, I looked at the running app and found real problems: layout that wastes space and places the sidebar, forms, and individual fields in ways that don't feel deliberate ("irrational"), making the product look unpolished/sloppy; and page headings/subheadings that read as generic or overly simplistic rather than meaningful, specific copy. I want these fixed, but I want to discuss specifics first — not have Claude guess and start implementing.

## Context
Raised verbatim on 2026-09-15, immediately after the 040-052 backlog wave (Step 9 hardening + Step 10 design-system redesign) fully converged and the dev servers were started for a live look. The user's own words:

> "A lot of issue and mistakes is there, like waste of space, the field, side bar and form is placed irrationally taking unnecessary spaces make project work looks sloppy. Page heading and sub heading text has no meaning or they are guide for 3 year child."

Immediately after, the user said: **"don't start implementing things. We will talk about this tomorrow."** This feature therefore intentionally starts at discussion/`/speckit-clarify`, not at implementation — clarify (or direct conversation) MUST pin down exactly which screens and which heading/subheading text the user means before any design or code change happens.

**Known at time of writing (deliberately not yet confirmed with the user — do not treat as settled):**
- No specific page(s) were named in the complaint itself.
- No specific heading/subheading text was quoted as an example of the problem.
- A brief live look at two screens was in progress when the user interrupted with "don't start implementing": the Staff clinic-tools dashboard (`frontend/src/routes/staff/ClinicToolsDashboard.tsx`) and the Onboard Staff form (`frontend/src/features/staff-onboarding/OnboardStaffForm.tsx`, reached via a sidebar-shelled route). No conclusions were reached about which specific issues apply to which specific screen — this was cut off before any assessment.
- Screens with a sidebar shell + fields + forms, built or touched across 046-052 (candidates the eventual complaint likely concerns, not a confirmed list): `ClinicToolsDashboard.tsx`, `OnboardStaffForm.tsx`, `ScheduleForm.tsx`, staff `BookSlotForm.tsx`/`WalkInForm.tsx`, `ConsultationNoteForm.tsx`, `PatientHubPage.tsx`, the Admin console pages, and the patient-facing forms (`SignupForm.tsx`, `RegistrationForm.tsx`, patient `BookSlotForm.tsx`).

## Business Rules
- This feature MUST NOT begin implementation before the user has confirmed, in conversation, which specific screens and which specific copy they mean — clarify/discussion comes first, not last.
- Any layout fix MUST be grounded in an actual, identified problem (a specific screen, a specific instance of wasted space, a specific misplaced field) — not a blanket re-layout of every screen 046-052 already built.
- Any copy fix MUST replace vague/generic text with something concrete and specific to what that screen actually does — not simply "more words" or filler.
- This is corrective polish on already-shipped 046-052 screens, not new functionality.

## Acceptance Criteria
- Given the user's complaint, when clarify/discussion runs, then it identifies the specific screen(s) and specific heading/subheading text the user is reacting to, before any design decision is made.
- Given a confirmed specific layout problem, when fixed, then the fix is a targeted spacing/placement correction (informed by a real design audit, e.g. the `impeccable` skill) — not a wholesale redesign of screens that weren't actually named as a problem.
- Given a confirmed specific copy problem, when fixed, then the replacement heading/subheading is concrete and specific to that screen's actual purpose, not generically reworded.

## Dependencies
- Depends on 046-052 (the full design-system redesign wave) already being shipped — this is a review/critique pass on their actual delivered output, not a hypothetical or pre-emptive one.

## Explicitly Out of Scope
- Any new functionality — this is a visual/copy quality corrective pass only.
- Guessing at specifics without the user's confirmation first — explicitly what the user asked to avoid.

## Source References
- User's direct feedback, 2026-09-15 (quoted verbatim above).
- `PRODUCT.md` / `DESIGN.md` (this project's own existing tone and visual conventions — this pass should measure the shipped screens against them, not invent new ones).
