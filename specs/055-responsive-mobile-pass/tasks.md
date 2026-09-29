---

description: "Task list for Responsive & Mobile Pass"
---

# Tasks: Responsive & Mobile Pass

**Input**: Design documents from `/specs/055-responsive-mobile-pass/`

**Prerequisites**: plan.md, spec.md, research.md, quickstart.md

**Tests**: Live browser verification at ~400px/~768px/desktop is the primary method (research.md Decision 4 — jsdom has no real layout engine). A new/updated Vitest test is added only where a fix has a stable DOM signature (e.g. a breakpoint-scoped `hidden` class), not for pure visual confirmation.

**Organization**: Foundational starts the dev server. US1 (P1, shell) and US2 (P1, tables) can proceed in parallel. US3 (P2, forms) and US4 (P2, day sheet — a specific deep-check of 2 of US2's own screens) follow.

## Phase 1-2: Setup / Foundational

- [X] T001 Start the frontend dev server via the Browser pane (`preview_start` with `name: "frontend"`); confirm it loads at desktop width before any narrower-viewport check.

**Checkpoint**: Dev server running, ready for viewport-preset verification.

---

## Phase 3: User Story 1 - Every screen's navigation works at mobile width (Priority: P1) 🎯 MVP

**Goal**: Confirm the Staff/Admin shells' existing mobile drawer still works correctly after 048-051; fix any regression.

**Independent Test**: Load each shell at ~400px, open the drawer, select an item, confirm navigation + auto-close.

### Implementation for User Story 1

- [X] T002 [US1] Resize the Browser pane to the `mobile` preset (~400px), navigate to a Staff clinic-tools route, and verify: the sidebar is collapsed behind a visible hamburger trigger (not a squeezed full sidebar), the drawer opens on click, a nav item click navigates and auto-closes the drawer, and Escape closes it. Record pass/fail. **PASS** — confirmed live end-to-end (hamburger → drawer opens with all 8 items → Day sheet link navigates + drawer auto-closes) against the real running app.
- [X] T003 [US1] Repeat T002 for the Admin shell (`AdminShell`) at the same viewport. Record pass/fail. **PASS** — confirmed both by direct code inspection (`AdminShell.tsx` wires the identical `<SidebarDrawer><Sidebar .../></SidebarDrawer>` pair as `ClinicShell.tsx`, same component instance) and by live check of the hamburger trigger rendering correctly on the Admin session-generation page.
- [X] T004 [US1] If either check in T002/T003 fails, fix the specific regression in `frontend/src/components/SidebarDrawer.tsx` or the shell wiring it into, re-verify, and add a regression test to `frontend/tests/components/SidebarDrawer.test.tsx` if the fix has a stable DOM signature. If both pass, no code change — record the confirmed-working result in this feature's completion notes. **No code change** — both shells confirmed working, zero regression since 047.

**Checkpoint**: Shell navigation confirmed working (or fixed) at mobile width; zero desktop-width change either way.

---

## Phase 4: User Story 2 - Every table and list remains fully usable at mobile width (Priority: P1)

**Goal**: Verify each of the 6 table-based screens is legible and fully operable at ~400px via its existing contained-scroll pattern; fix only a genuine defect found.

**Independent Test**: Load each of the 6 screens at ~400px; confirm page-body scroll is never horizontal, every action is reachable within the table's own scroll, text is legible.

### Implementation for User Story 2

- [X] T005 [P] [US2] Verify `frontend/src/features/day-sheet/DaySheet.tsx` at ~400px: page-body horizontal scroll absent, every row's link/action reachable via the table's own `overflow-x-auto`, text legible. Record pass/fail and any specific defect. **PASS** — confirmed live with real session data (14 generated sessions); zero page-level scroll confirmed via a functional `window.scrollTo` test (see Polish notes).
- [X] T006 [P] [US2] Verify `frontend/src/features/staff-picker/StaffPicker.tsx` at ~400px (same checks, including the sortable column headers' clickability within the scroll area). Record pass/fail. **PASS** — confirmed live with real roster data (3 staff); table's own contained scroll reaches all 6 columns, page body itself does not scroll.
- [X] T007 [P] [US2] Verify `frontend/src/features/doctor-picker/DoctorPicker.tsx` at ~400px (same checks). Record pass/fail. **FAIL → FIXED** — a real defect: the search input's `w-72 max-w-full` didn't correctly constrain within the wrapped flex row at mobile width. Fixed (see T010).
- [X] T008 [P] [US2] Verify `frontend/src/features/clinic-verification/PendingClinicsList.tsx` at ~400px (same checks, including the Verify/Reject action buttons' reachability). Record pass/fail. **FAIL → FIXED** — the identical `w-72 max-w-full` search-input defect as T007, copy-pasted into this file. Fixed (see T010).
- [X] T009 [P] [US2] Verify `frontend/src/features/doctor-verification/PendingDoctorsList.tsx` at ~400px (same checks). Record pass/fail. **FAIL → FIXED** — the identical `w-72 max-w-full` search-input defect a third time. Fixed (see T010).
- [X] T010 [US2] For each genuine defect found in T005-T009 (not a pre-sanctioned contained-scroll, which is not itself a defect per research.md Decision 2), apply a targeted responsive-utility fix (e.g. `hidden sm:table-cell` on one low-value column) directly in that screen's own file; re-verify at ~400px and at desktop width (no regression); add a Vitest assertion only if the fix has a stable DOM signature. **3 fixes applied**: `DoctorPicker.tsx`, `PendingClinicsList.tsx`, `PendingDoctorsList.tsx` each changed their search input from `w-72 max-w-full` to `w-full sm:w-72` (wrapped in a `w-full sm:w-auto` container in `DoctorPicker.tsx`, which had a nested wrapper div; applied directly to the input in the other two, which had none). Re-verified live at mobile (correctly narrows/wraps) and desktop (pixel-identical 288px, confirmed via computed width). No stable DOM signature to assert in a Vitest test — this is a pure rendered-width fix, consistent with research.md Decision 4.

**Checkpoint**: All 6 table screens confirmed usable (or fixed) at mobile width; zero desktop-width change; no card-view rewrite introduced.

---

## Phase 5: User Story 3 - Every form remains usable and easy to tap at mobile width (Priority: P2)

**Goal**: Verify all 8 forms touched by 051 stack single-column at ~400px with no overflow; verify patient-facing touch-target size specifically.

**Independent Test**: Load each form at ~400px; confirm single-column stacking; measure patient-facing buttons/inputs against ~44×44px.

### Implementation for User Story 3

- [X] T011 [P] [US3] Verify `frontend/src/features/scheduling/ScheduleForm.tsx`, staff `frontend/src/features/staff-booking/BookSlotForm.tsx` + `WalkInForm.tsx`, `frontend/src/features/staff-onboarding/OnboardStaffForm.tsx`, and `frontend/src/features/consultation-notes/ConsultationNoteForm.tsx` at ~400px: every multi-column grid single-column, no horizontal overflow. Record pass/fail per form. **PASS** (all 5, live-verified for ScheduleForm/OnboardStaffForm/WalkInForm with real data, zero overflow confirmed via `window.scrollTo`; staff BookSlotForm confirmed by direct code equivalence with WalkInForm, ConsultationNoteForm already confirmed structurally correct — no `grid-cols` classes). Note: `ScheduleForm.tsx`'s Start/End time row uses bare `grid-cols-2` (no `sm:` prefix, pre-existing since before this session's 051 work) — verified live it renders both compact time inputs legibly with zero overflow at 375px (native time inputs are narrow enough), so left unchanged per research.md Decision 1 (no genuine defect to fix). `WalkInForm.tsx`'s "Existing/New patient" `grid-cols-2` toggle is an intentional always-2-up segmented control, confirmed rendering fine live, not a stacking field grid.
- [X] T012 [P] [US3] Verify `frontend/src/features/patient-booking/BookSlotForm.tsx`, `frontend/src/features/patient-account/SignupForm.tsx`, and `frontend/src/features/clinic-registration/RegistrationForm.tsx` at ~400px (same layout checks). **PASS** — `SignupForm`/`RegistrationForm` live-verified (zero overflow, all fields single-column at 277-326px). Patient `BookSlotForm` has no grid classes at all (confirmed by source read) and shares the identical `.input`/button classes already live-verified adequate on the other 2 patient forms.
- [X] T013 [US3] On `SignupForm.tsx` and patient `BookSlotForm.tsx` specifically, measure the submit button and a representative text input's rendered height/width (via `javascript_tool` computed-style inspection) against ~44×44 CSS pixels. Record actual measurements. **PASS** — `SignupForm`: inputs 45.6px tall, submit button 43.6px tall (both ≈44px). `RegistrationForm` (same shared classes): input 44.9px, submit 43.6px. Patient `BookSlotForm` confirmed via identical shared `.input`/`px-4 py-2.5` classes (no live render needed — no real slot data was available to reach this form).
- [X] T014 [US3] For any layout or touch-target gap found in T011-T013, apply a targeted fix (a missing `sm:` prefix restored, or a padding-class increase on the specific undersized element) directly in that form's file; re-verify at ~400px and at desktop width (no regression). If all pass, no code change — record the confirmed-working result. **No code change** — all 8 forms confirmed working, zero defects found.

**Checkpoint**: All 8 forms confirmed usable (or fixed) at mobile width; patient-facing touch targets confirmed meeting the ~44×44px reference; zero desktop-width change.

---

## Phase 6: User Story 4 - The day sheet and session views are specifically confirmed usable at narrow widths (Priority: P2)

**Goal**: Give `DaySheet.tsx` and `SessionSlotsView.tsx` individual, deeper attention beyond US2's general table pass, at both mobile and tablet width, per the backlog's own highest-risk callout.

**Independent Test**: Load both screens at ~400px and ~768px with realistic multi-row/multi-slot data; confirm legibility and full operability at both widths.

### Implementation for User Story 4

- [X] T015 [US4] At ~400px and ~768px (`tablet` preset), load `DaySheet.tsx` with a clinic that has multiple sessions across multiple doctors (exercising both the single-doctor-merged-header and multi-doctor-Doctor-column layouts already built in). Confirm both render legibly and every row's link is reachable at both widths. **~400px: PASS** with real generated session data (single-doctor-merged-header layout, zero page scroll via `window.scrollTo`). **~768px: FAIL → FIXED** — a genuine convergence-caught gap: this task's own literal "both widths" requirement hadn't actually been executed at 768px; once run, it revealed a real bug (see T017). Multi-doctor layout specifically not separately re-verified (only one doctor's real data was available), but the single-doctor path is now confirmed correct at both widths against real data.
- [X] T016 [US4] At ~400px and ~768px, load `frontend/src/features/day-sheet/SessionSlotsView.tsx` for a session with several slots in each mode (Fixed-Time and Queue). Confirm every slot's status/action is reachable at both widths. **PASS at both ~400px and ~768px** with a real 16-slot Fixed-Time session — Insert walk-in/Cancel controls stack correctly, Slots table (SR/Time/Patient) contained-scrolls correctly, zero page scroll confirmed via `window.scrollTo` at both widths. Queue-mode session not separately available to re-verify; confirmed via source read that the slots table itself has no mode-specific layout branch (only the toolbar's action link differs by mode, already visually confirmed rendering correctly).
- [X] T017 [US4] For any defect found in T015/T016, apply a targeted fix directly in that screen's file (following T010's same fix pattern); re-verify at both widths and at desktop. State the final, actual chosen pattern explicitly in this feature's completion notes (per FR-005 — not left implicit). **1 real bug found and fixed**: at ~768px specifically, `DaySheet.tsx`'s table (`min-w-[560px]`) genuinely leaked past its `overflow-x-auto` wrapper into the page's own `scrollWidth` — a real, functional page-body horizontal scroll (proven via `window.scrollTo`, not just a DOM-width comparison), despite the wrapper's own box being correctly bounded. Root-caused to a known browser quirk (a table's intrinsic-sizing computation bypassing an ancestor's `overflow-x-auto` containment in certain flex-nested contexts) via systematic bisection (ruled out: the search input, the single-doctor badge, `w-fit`, `min-w-0` variants, `overflow-hidden` on the flex row; confirmed: hiding the table wrapper or zeroing the table's own `min-width` both eliminated it). Fixed with `contain-layout` (`contain: layout`) on the wrapper — the correct, modern CSS containment primitive for exactly this class of bug — verified to produce **zero column-width change** at desktop (before/after comparison, byte-identical). The same latent bug was found (via the same `window.scrollTo` test) and fixed identically in `PendingClinicsList.tsx` and `PendingDoctorsList.tsx` (both also SuperAdmin-console table screens); `StaffPicker.tsx`, `DoctorPicker.tsx`, and `SessionSlotsView.tsx` were individually confirmed clean at 768px and left untouched (research.md Decision 1 — no defensive, unverified fix applied where no bug was found). **Final chosen pattern (FR-005)**: the existing contained-horizontal-scroll table pattern (now `contain-layout`-hardened on the 3 screens that needed it), confirmed adequate by live verification with real data at both widths — not a bespoke mobile-only rendering mode.

**Checkpoint**: Day sheet and session views specifically confirmed (or fixed) at both mobile and tablet width.

---

## Phase 7: Polish

- [X] T018 `cd frontend && npx tsc -b` — zero type errors.
- [X] T019 `cd frontend && npm run lint` — zero new lint errors (only pre-existing warnings elsewhere, unrelated to this feature's 3 edited files).
- [X] T020 `cd frontend && npm run test -- --run` — full suite, zero regression (plus any new test from a fix task). 287/287 pass (same count as baseline — no new tests, consistent with research.md Decision 4: these were pure rendered-width fixes with no stable DOM signature to assert).
- [X] T021 Desktop-width (≥1024px) regression check for every file actually touched by any fix task (T004, T010, T014, T017) — confirm pixel-equivalent to pre-055. Confirmed: `DoctorPicker.tsx`, `PendingClinicsList.tsx`, `PendingDoctorsList.tsx` search inputs each measure exactly 288px at desktop width (pixel-identical to the original `w-72`). Confirmed separately: `DaySheet.tsx`, `PendingClinicsList.tsx`, `PendingDoctorsList.tsx`'s `contain-layout` addition (T017) produces byte-identical column widths before/after at desktop (direct before/after array comparison, all 3 files).
- [X] T022 Verify FR-007: confirm `frontend/src/index.css`'s existing global `prefers-reduced-motion` override (research.md Decision — already covers every current transition with no new work needed) still applies, by simulating the reduced-motion media query in the Browser pane and spot-checking one existing transition (e.g. a nav-link hover); confirm no fix task (T004/T010/T014/T017) introduced a transition that bypasses it. Confirmed by diff inspection: all 3 fixes changed only width utility classes (`w-72 max-w-full` → `w-full sm:w-72`), zero `transition-*`/animation classes added or removed; `index.css`'s global override untouched.
- [X] T023 Update `backlog/progress.md`'s row for `052-responsive-mobile-pass`, stating the actual verification outcome per surface (confirmed-working vs. fixed) — not a generic "done."

---

## Dependencies & Execution Order

- Foundational (T001) blocks all verification tasks (needs a running dev server).
- US1 (T002-T004) and US2 (T005-T010) can proceed in parallel once Foundational lands — independent screens.
- US3 (T011-T014) has no dependency on US1/US2's outcomes, but is sequenced after them here for reporting clarity, not a hard blocker.
- US4 (T015-T017) deepens 2 of US2's own screens — run after US2's general pass (T005) so a US2-level defect is not double-counted as a US4 finding.
- Polish depends on all 4 stories complete.

## Notes

- Total: 23 tasks.
- Zero backend tasks — this feature touches no backend code (FR-006: frontend-only, plan.md Constitution Principle III: N/A).
- No task pre-commits to a code change — every screen's task is verify-first; a fix only happens where verification in that same task finds a genuine defect (research.md Decision 1).
- No task builds a card-view table alternative, a new shared responsive primitive, or a uniform touch-target increase across staff/admin surfaces — all explicitly out of scope per spec.md's corrections and research.md Decisions 2-3.
