---

description: "Task list for Visual Design & Copy Quality Pass"
---

# Tasks: Visual Design & Copy Quality Pass

**Input**: Design documents from `/specs/056-design-copy-quality-pass/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, quickstart.md

**Tests**: New tests are added for the shell restructuring and the `HomePage.tsx` copy (both are observably new behavior). Existing tests for files this feature edits but doesn't restructure (`StaffOnboarding.test.tsx`, `MyClinicsList.test.tsx`, `ClinicToolsDashboard.test.tsx`) are the regression gate for those edits, per this project's established substitution (050-sidebar-navigation used the same approach for shells with no prior dedicated test file).

**Organization**: US1 (P1, layout) and US2 (P2, copy) are fully independent — disjoint file sets, no shared prerequisite — so there is no Foundational phase blocking either. Each starts directly at its own implementation phase.

## Phase 1-2: Setup / Foundational

No setup or foundational tasks. This feature adds zero new dependencies (research.md "Explicitly not adopted") and touches an existing app on its existing stack — there is nothing to initialize. US1 and US2 can proceed independently and in either order.

---

## Phase 3: User Story 1 - Fix confirmed layout/placement problems on shipped screens (Priority: P1) 🎯 MVP

**Goal**: Every screen under the staff console's shared shell (`StaffShell`/`ClinicShell`) shows its sidebar docked to the viewport edge with content filling the remaining width — no nested centered columns — while `MyClinicsList.tsx` (the sidebar-less `/staff` picker) and `PatientShell`/`AdminShell` stay pixel-unchanged.

**Independent Test**: Open any `/staff/clinics/:id/*` page at a normal desktop width and confirm the sidebar sits flush against the browser's left edge with no empty margin beside it, and the content area fills the remaining width up to one reading-width cap (quickstart.md step 7).

### Tests for User Story 1

- [x] T001 [P] [US1] Add `frontend/tests/staff/StaffShell.test.tsx`: renders its header and `<Outlet/>` content with no width-constraining wrapper around the outlet region (the cap moves to `MyClinicsList`/`ClinicShell`'s own content pane per research.md Decision 5); existing header/sign-out behavior unregressed.
- [x] T002 [P] [US1] Add `frontend/tests/staff/ClinicShell.test.tsx`: sidebar nav landmark (`nav[aria-label="Primary"]`) is present alongside the content region; breadcrumb and "Switch clinic" link behavior (existing, unedited logic) still render correctly; content region carries the new single reading-width class.

### Implementation for User Story 1

- [x] T003 [US1] Edit `frontend/src/routes/staff/StaffShell.tsx`: remove `mx-auto max-w-5xl` from `<main>` (keep `p-6 sm:p-8` and the existing header untouched) so the outlet region spans the full available width (research.md Decision 1/5).
- [x] T004 [US1] Edit `frontend/src/features/staff-clinics/MyClinicsList.tsx`: wrap its existing return in `mx-auto max-w-5xl` (or reuse the exact classes `StaffShell.tsx` previously applied) so removing `StaffShell`'s cap in T003 does not change this page's appearance — a preserve-only edit, not a redesign (research.md Decision 5). Confirm against `frontend/tests/staff-clinics/MyClinicsList.test.tsx` (existing, must still pass).
- [x] T005 [US1] Edit `frontend/src/routes/staff/ClinicShell.tsx`: restructure the `flex gap-6` row so it spans the full width (no ancestor cap after T003); make the sidebar column `sticky` at the top of the viewport (research.md Decision 1) instead of a plain flex item; give the content pane (`min-w-0 flex-1 space-y-6 px-4 py-3`) one explicit reading-width cap per `DESIGN.md`'s documented `max-w-3xl`–`max-w-4xl` convention (research.md Decision 2) — applied once, here, not per-page. Breadcrumb/"Switch clinic" logic (`useEffect`, `listMyClinics` call) is unchanged.
- [x] T006 [US1] Edit `frontend/src/features/staff-onboarding/OnboardStaffForm.tsx`: remove or loosen its own inner `mx-auto max-w-xl` on the `<form>` (line ~227) now that `ClinicShell`'s content pane (T005) already provides a reading-width cap — this was one of the two originally-named problem screens, so its own redundant wrapper is in scope (research.md Decision 2). Confirm the 2-column field grid and the conditional Doctor-details section still lay out correctly (quickstart.md step 11) and `frontend/tests/staff-onboarding/StaffOnboarding.test.tsx` (existing) still passes.
- [x] T007 [US1] Live-verify via `quickstart.md` steps 7-8: at ≥1280px, sidebar is flush to the viewport's left edge on at least one `ClinicShell` page (e.g. Clinic dashboard) and on `OnboardStaffForm`; scrolling a long page keeps the sidebar in view (sticky); nav active-highlight behavior unchanged.
- [x] T008 [US1] Live-verify via `quickstart.md` step 9: resize to ~400px — confirm `SidebarDrawer`'s existing hamburger/off-canvas behavior (open, list items, close on backdrop/Escape/selection) is completely unchanged (research.md Decision 3). Run `frontend/tests/components/SidebarDrawer.test.tsx` (existing) to confirm no regression.
- [x] T009 [US1] Live-verify via `quickstart.md` step 10: load the Patient portal and the Super Admin console — confirm both are pixel-identical to before this feature (FR-007). Patient portal confirmed live (screenshot, unchanged). `AdminShell.tsx`/`AdminDashboard.tsx` confirmed unedited by this feature this session (neither file was touched by any T001-T006 edit) — the repo's pre-existing uncommitted working tree (from prior sessions, unrelated to this feature) already shows both as modified, so `git status` alone can't distinguish; confirmed instead by this session's own edit history.
- [x] T009a [US1] Present the fixed layout to the product owner (screenshots or a live walkthrough of at least one `ClinicShell` page and `OnboardStaffForm`) and get explicit confirmation the wasted-space/placement problem is resolved (SC-003) — mirrors T010's copy sign-off gate. Do not consider User Story 1 done without this confirmation. **Confirmed 2026-09-21**: live-walked the product owner through the running dev server (ClinicShell dashboard for a freshly-registered clinic + `OnboardStaffForm`) — sidebar flush to the viewport edge, content filling the remaining width, form no longer double-capped. Product owner confirmed resolved.

**Checkpoint**: User Story 1 fully functional and independently testable — every staff-console screen inherits the fixed structure; nothing outside that scope changed; product owner has confirmed the fix.

---

## Phase 4: User Story 2 - Rebuild the landing page around real patient content and one clinic entry point (Priority: P2)

**Goal (expanded 2026-09-16, mid-implementation — see spec.md Clarifications and FR-009a/FR-010/FR-011)**: `HomePage.tsx` is rebuilt from the product-owner-supplied reference (`design/landing-page-reference.html`) — patient booking dominates the hero, using this project's own brand/copy/tokens and real routes; clinic-side access is exactly one "Clinic login" link (header + footer, → `/staff/login`); Super Admin auth and clinic registration are reachable only from behind that entry point, not as separate top-level homepage links.

**Independent Test**: Load `/` and confirm the hero, single Clinic login link, and absence of top-level `/register`/Super Admin links (quickstart.md step 6, expanded); load `/staff/login` and confirm it now offers a path to `/register`.

### Implementation for User Story 2

- [x] T010 [US2] ~~Draft replacement wording for the six confirmed elements~~ — superseded: instead of a 6-string swap, the product owner directed and confirmed (live, via chat) a full rebuild from `design/landing-page-reference.html` before this task's original draft was applied. See spec.md Clarifications (2026-09-16, continued).
- [x] T010a [US2] Save the product-owner-supplied reference design as `design/landing-page-reference.html` (persistent reference, per FR-009a).
- [x] T011 [US2] Rewrite `frontend/src/routes/HomePage.tsx`: patient-booking hero (headline, subheading, "Log in"/"Create account" actions → `/patient/login`/`/patient/signup`, a "Looking for a doctor?" link → `/discover`, a decorative illustration on `lg:`+ viewports), a 3-item trust/feature row naming real shipped capabilities (queue position tracking, one account across clinics, clinic verification), and exactly one "Clinic login" link (header + footer) → `/staff/login`. Uses this project's existing brand ("CMS2 Clinic Management"), `index.css` tokens (indigo/gray/cobalt, Figtree), and reused icon components (`ClockIcon`, `ClinicIcon`, `CheckIcon`, `IconBadge`) — not the reference's own brand/palette/font. No `/register` or Super Admin link remains on this page (FR-010).
- [x] T011a [US2] Edit `frontend/src/routes/staff/StaffLoginPage.tsx`: add a "New clinic? Register your clinic" link (→ `/register`) alongside the existing sign-in form, so clinic registration stays reachable from behind the "Clinic login" entry point (FR-011). No change to `StaffLoginForm`'s own submit/role-routing logic — `decideClinicPortalDestination` already sends Super Admins to `/super-admin-console` and staff to `/staff`.
- [x] T012 [P] [US2] Add `frontend/tests/routes/HomePage.test.tsx`: asserts the patient hero's links (`Log in`→`/patient/login`, `Create account`→`/patient/signup`, `Looking for a doctor?`→`/discover`); asserts exactly 2 "Clinic login" links, both →`/staff/login`; asserts no `/register`/Super Admin link exists at the top level; asserts the 3 trust-section headings render.
- [x] T012a [P] [US2] Add `frontend/tests/staff/StaffLoginPage.test.tsx`: asserts the "Register your clinic" link renders and points to `/register`, alongside the existing "Clinic sign in" form.

**Checkpoint**: User Story 2 fully functional and independently testable — `HomePage.tsx` rebuilt per the confirmed reference/scope; `/staff/login` now the sole clinic/admin entry point; no other screen touched.

---

## Phase 4a: User Story 2 extension - Rebuild `/patient/login` to match (per FR-012, added 2026-09-16 second pivot)

**Goal**: `/patient/login` matches `design/patient-login-reference.html`'s layout/copy structure using the same `BrandHeader`/`BrandFooter` chrome and tokens as the rebuilt `HomePage.tsx`, wired to the real `loginPatient` flow, with an honest (not fabricated) "Forgot password?" affordance.

**Independent Test**: Load `/patient/login`, confirm the branded header/footer, "Welcome back." card, and that submitting real credentials still calls `loginPatient`; click "Forgot password?" and confirm an in-app notice appears rather than a dead link or fake flow.

### Implementation

- [x] T019 Save the second product-owner-supplied reference as `design/patient-login-reference.html`.
- [x] T020 Add `frontend/src/components/BrandHeader.tsx` and `BrandFooter.tsx`: extracted from `HomePage.tsx`'s inline header/footer (now used by both `HomePage.tsx` and the rebuilt login page) so the brand chrome isn't duplicated a third time.
- [x] T021 Rewrite `frontend/src/features/patient-account/LoginForm.tsx`: card layout (`rounded-2xl border shadow-md`), "Welcome back." heading, "Forgot password?" as a `<button>` wired to `useToast()` with an honest unavailable-feature notice (FR-012 — no fabricated reset flow), a "Create an account" `<Link>` → `/patient/signup`, unchanged `loginPatient` submit logic and `loginEmail`/`loginPassword` field ids.
- [x] T022 Edit `frontend/src/routes/patient/PatientLoginPage.tsx`: use `BrandHeader`/`BrandFooter` instead of `PublicHeader`.
- [x] T023 Update `frontend/tests/patient-account/PatientAccountForms.test.tsx`: wrap `LoginForm` renders in `MemoryRouter`+`ToastProvider` (now required by the new `Link`/`useToast` usage); tighten the post-login assertion from a loose `/welcome back/i` regex to the exact interpolated-email text, since "Welcome back." is now also the pre-login heading; add coverage for the forgot-password toast and the signup link.

**Checkpoint**: `/patient/login` matches the second reference; full suite green (57/57 files, 303/303 tests); no fabricated password-reset flow.

---

## Phase 4b: User Story 2 extension - Rebuild `/patient` to match (per FR-013, added 2026-09-16 third pivot)

**Goal**: `/patient` matches `design/patient-dashboard-reference.html` — branded header with account menu, personalized greeting, conditional next-visit strip, three action cards — using the same tokens as the landing/login rebuilds, with an honest (not fabricated) display name.

**Independent Test**: Sign in as a patient with no upcoming booking - dashboard shows the greeting and three cards, no next-visit strip. Sign in as a patient with a real upcoming booking - the strip appears with that booking's doctor/clinic/time and a working "View details" link.

### Implementation

- [x] T024 Save the third product-owner-supplied reference as `design/patient-dashboard-reference.html`.
- [x] T025 Add `deriveDisplayNameFromEmail` to `frontend/src/features/patient-account/token.ts`: derives a first-name-like string from the email's local part, since `PatientAccount` has no name field anywhere in this system (FR-013).
- [x] T026 Edit `frontend/src/routes/patient/PatientShell.tsx`: header brand mark/wordmark/padding now match `BrandHeader.tsx`'s treatment; avatar initial and sign-out button restyled (bordered, matching the reference's quiet button); avatar initial now derived via T025 instead of the raw email's first character.
- [x] T027 Rewrite `frontend/src/routes/patient/PatientDashboard.tsx`: personalized "Welcome back, {name}." heading via T025; reordered tiles (Find a doctor, My bookings, My clinics per the reference's order); restyled action cards (border, rounded-2xl, hover lift, icon badge, `h2` with a `group-hover`-animated arrow) - not the shared `Card` component, since that's reused by unrelated staff/admin screens this rebuild doesn't touch.
- [x] T028 Restyle `frontend/src/features/patient-bookings/NextAppointmentCard.tsx` to the reference's bold teal "next visit" strip - the existing fetch/find-soonest-upcoming-ACTIVE-booking/render-nothing-if-none logic is unchanged (it already did exactly what FR-013 asks); only the JSX/classes changed, and the single "View details" link now matches the reference's structure (only that link is clickable, not the whole strip).
- [x] T029 [P] Add `frontend/tests/routes/patient/PatientShell.test.tsx`: brand text, derived avatar initial, sign-out behavior.
- [x] T030 [P] Add `frontend/tests/routes/patient/PatientDashboard.test.tsx`: personalized greeting (and its no-session fallback), the three tiles in order with correct hrefs.
- [x] T031 [P] Add `frontend/tests/patient-bookings/NextAppointmentCard.test.tsx`: renders nothing with no upcoming/only-cancelled bookings; renders the strip with correct doctor/clinic/time and a working details link when one exists.

**Checkpoint**: `/patient` matches the third reference; full suite green (60/60 files, 312/312 tests); next-visit strip's existing conditional logic reused, not reimplemented; no fabricated patient name.

---

## Phase 5: Polish & Cross-Cutting Concerns

- [x] T013 `cd frontend && npx tsc -b` — zero type errors. Confirmed clean.
- [x] T014 `npm run lint` — zero new lint errors. Confirmed: only pre-existing warnings elsewhere in the codebase, none in this feature's files.
- [x] T015 `npx vitest run tests/routes/HomePage.test.tsx tests/staff/StaffShell.test.tsx tests/staff/ClinicShell.test.tsx tests/staff/StaffLoginPage.test.tsx` — new tests pass. Confirmed (15/15 across these + StaffLoginForm/destination in the same run).
- [x] T016 `npx vitest run tests/staff/ClinicToolsDashboard.test.tsx tests/staff-onboarding/StaffOnboarding.test.tsx tests/staff-clinics/MyClinicsList.test.tsx tests/components/SidebarDrawer.test.tsx tests/components/Sidebar.test.tsx` — existing tests for every edited/adjacent file still pass unregressed. Confirmed (46/46).
- [x] T017 `npm run test -- --run` — full suite, zero regression. Confirmed: 57/57 files, 301/301 tests passing (baseline + this feature's 6 new test files: `StaffShell`, `ClinicShell`, `HomePage`, `StaffLoginPage`, ×N tests each).
- [x] T018 Update `backlog/progress.md`'s row for `053-visual-design-copy-quality-pass`. Set to "Implementing" (not yet "Converged" — `/speckit-converge` hasn't run against the final, pivoted scope yet).

---

## Dependencies & Execution Order

- No Foundational phase — US1 and US2 have no shared prerequisite.
- **US1 (T001-T009a)**: T001-T002 (tests) can be written alongside T003-T006 (implementation, same convention as 050). T003 → T004 (removing StaffShell's cap must happen before/with adding MyClinicsList's own). T003 → T005 (ClinicShell's full-width row needs StaffShell's cap gone). T005 → T006 (OnboardStaffForm's own cap is only removable once the content pane above it already caps width). T007-T009 (live verification) depend on T003-T006 complete. T009a (product-owner sign-off) depends on T007-T009 complete.
- **US2 (T010-T012a)**: T010a (save reference) before T011 (rebuild HomePage using it). T011 before T011a (StaffLoginPage's new link references the same "Clinic login" consolidation). T012/T012a (tests) alongside T011/T011a.
- US1 and US2 have no dependency on each other and were done in either order.
- Polish (T013-T018) depends on both US1 and US2 complete.

## Parallel Example: User Story 1

```bash
# T001 and T002 touch different new test files - parallelizable:
Task: "Add frontend/tests/staff/StaffShell.test.tsx"
Task: "Add frontend/tests/staff/ClinicShell.test.tsx"
```

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 3: User Story 1 (the layout fix — the more visually obvious complaint, per spec.md's stated priority order).
2. **STOP and VALIDATE**: Run quickstart.md steps 6-11 relevant to layout; confirm with the product owner.
3. Proceed to Phase 4 (copy fix) once layout is confirmed resolved.

### Incremental Delivery

1. User Story 1 (layout) → validate independently → present to the product owner (T009a).
2. User Story 2 (landing page) → live-directed rebuild from a supplied reference (superseding the original draft-then-approve copy flow) → validate independently.
3. Polish once both are in.

## Notes

- Total: 34 tasks (19 original + T010a/T011a/T012a for the landing-page pivot + T019-T023 for the patient-login pivot + T024-T031 for the patient-dashboard pivot).
- No backend task — this feature touches no backend code (plan.md Technical Context, Constitution Principle III: N/A).
- `PatientShell`/`AdminShell` and every screen under them: zero tasks — explicitly unchanged (FR-007).
- Every other staff-console page's own inner width wrapper (`ScheduleForm`, `BookSlotForm`, `WalkInForm`, `ConsultationNoteForm`, etc.): zero tasks — not named as a problem; automatically benefits from `ClinicShell`'s new content-pane cap (T005) without needing its own edit, per FR-003's "targeted, not a broader redesign."
- US2's original T010 (draft-6-strings-then-approve) was superseded mid-flight: the product owner gave a fully-specified rebuild instruction (reference design + explicit requirements) before that draft was applied, which is itself a form of explicit confirmation — see spec.md Clarifications (2026-09-16, continued) for why this counts as confirmed scope, not a guess.
