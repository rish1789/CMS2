# Feature Specification: Responsive & Mobile Pass

**Feature Branch**: `055-responsive-mobile-pass`

**Created**: 2026-09-15

**Status**: Draft

**Input**: User description: "backlog/052-responsive-mobile-pass.md" — a verification-and-fix pass ensuring every screen built by features 046-051 (shell, tables, forms, day sheet) works intentionally at tablet/mobile widths, per this project's own ~400px-tested responsive convention.

## Corrections to the backlog brief

Verified during specification (not assumed):

1. **The sidebar shell (047) already has a working, previously live-verified mobile pattern.** `frontend/src/components/SidebarDrawer.tsx` already renders a hamburger-triggered, native-`<dialog>`-backed, left-edge drawer below the `sm:` breakpoint (640px), reusing the same free focus-trap/Escape-to-close browser behavior as 046's `Modal`. 047's own build already live-verified this at ~400px (hamburger opens the drawer, item-selection auto-closes it, Escape closes it) per `backlog/progress.md`'s 047 row. This feature's job for the shell is a **regression re-check** after 048-051's changes, not new construction.
2. **Every one of the 6 table-based surfaces already uses this project's own project-sanctioned contained-horizontal-scroll pattern, consistently.** `StaffPicker.tsx`, `PendingClinicsList.tsx`, `PendingDoctorsList.tsx`, `DoctorPicker.tsx`, `DaySheet.tsx`, and `SessionSlotsView.tsx` each wrap their `<table>` in its own `overflow-x-auto` container with a `min-w-[Npx]` (560-720px depending on column count) — exactly the exception this project's own cited standard explicitly grants ("contained horizontal scroll only for tables/code/diagrams... never the page body itself scrolling horizontally"). The backlog's literal ask for "a card-per-row layout instead of horizontal-scrolling a wide table" would mean hand-building 6 separate mobile-only rendering modes for 6 structurally different tables (a verification queue's action buttons, a roster, a per-row-linked day sheet, a picker) — a substantial net-new-capability undertaking that conflicts with this same feature's own stated constraint ("a verification-and-fix pass... not a feature that introduces new functionality") and Constitution Principle II (no speculative rebuild of something already working). **Corrected scope**: verify each of the 6 tables is genuinely legible and fully operable (every action reachable via the contained scroll, no text clipped, no page-body scroll) at ~400px — a live check, not a redesign — and fix only a genuine defect if one is found (e.g. a column that adds width without adding value at narrow screens can be hidden via a responsive utility class; that is a targeted fix, not a new capability).
3. **`051`'s forms already default to a single-column stack below the `sm:` breakpoint.** Every multi-column field grid this session built or touched (`OnboardStaffForm.tsx`, `ScheduleForm.tsx`, `RegistrationForm.tsx`, etc.) uses Tailwind's mobile-first `grid gap-4 sm:grid-cols-2` convention, which is already single-column by default and only widens at `sm:` (640px) and up. This feature's job for forms is a **verification pass** (confirm no exception to this convention slipped through, confirm real touch-target size), not a rewrite.
4. **`prefers-reduced-motion` is already enforced globally**, not per-component: `frontend/src/index.css` has a single blanket override (`@media (prefers-reduced-motion: reduce) { *, *::before, *::after { animation-duration: 0.01ms !important; ... } }`) that automatically covers every existing and future CSS transition/animation in the codebase. This feature adds no new JS-driven animation that would bypass it, so no new work is needed here beyond confirming this still holds.
5. **"Larger touch targets" is a patient-facing-surface-specific principle, not a blanket one.** `PRODUCT.md`'s Accessibility & Inclusion section scopes "larger touch targets" explicitly to "the patient-facing surface specifically," with WCAG 2.1 AA (which has no minimum target-size criterion — that is a 2.2 AA/2.1 AAA criterion) as the floor everywhere else. This feature's touch-target verification is therefore concentrated on patient-facing screens (signup, registration, patient booking, the patient shell), not applied as a uniform new minimum across staff/admin surfaces.

These corrections don't shrink this feature's real value — they redirect it from "rebuild several screens' layouts" (unwarranted, given 3 of the 4 named surfaces already work) to "verify every surface at real narrow widths, fix the specific, genuine gaps that verification finds" — which is what a "hardening/completeness pass" (the backlog's own framing) actually means.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Every screen's navigation works at mobile width (Priority: P1)

A staff member or admin on a tablet or phone needs to reach every part of the application a desktop user can reach, using a clear, discoverable navigation mechanism — not a sidebar that's simply cut off or overlapping content at a narrow width.

**Why this priority**: Without working navigation, no other part of the application is reachable at all — this is the floor everything else depends on.

**Independent Test**: Load the Staff and Admin shells at a ~400px-wide viewport; open navigation via its mobile mechanism (hamburger/drawer), select an item, and confirm it navigates and the drawer closes; confirm no navigation element is obscured or requires horizontal scrolling to reach.

**Acceptance Scenarios**:

1. **Given** the Staff (`ClinicShell`) or Admin (`AdminShell`) shell at a ~400px viewport, **When** the page loads, **Then** the sidebar collapses to its off-canvas drawer form with a visible, reachable hamburger trigger — not a squeezed or overlapping full sidebar.
2. **Given** the drawer is open, **When** the user selects a navigation item, **Then** the app navigates to that route and the drawer closes automatically.
3. **Given** any shell at desktop width (≥640px), **When** compared to its pre-055 appearance, **Then** the layout is pixel-identical — this feature changes nothing at desktop width.

---

### User Story 2 - Every table and list remains fully usable at mobile width (Priority: P1)

A staff member checking the day sheet, staff roster, or a verification queue on a phone needs every row's content legible and every action (open, verify, reject, sort) reachable, without the whole page shifting sideways to do it.

**Why this priority**: Equal in importance to navigation — these are the actual work surfaces staff and admins use daily; a table that's illegible or has unreachable actions makes the screen useless on a phone, regardless of whether navigation works.

**Independent Test**: Load each of the 6 table-based screens (Day Sheet, Session Slots, Staff Picker, Doctor Picker, Pending Clinics, Pending Doctors) at ~400px; confirm the table's own horizontal scroll (not the page body) is what scrolls, every action button is reachable within that scroll area, and text is legible without zooming.

**Acceptance Scenarios**:

1. **Given** any of the 6 table screens at ~400px, **When** viewed, **Then** the page body itself never scrolls horizontally — only the table's own contained scroll area does, and it is visually obvious (via a scroll affordance) that more columns exist.
2. **Given** a table row's action (e.g. "Verify", "Open", sort-column header), **When** reached via the table's horizontal scroll, **Then** it is fully visible and clickable, not clipped or partially hidden.
3. **Given** a genuine narrow-width defect is found during verification (e.g. a low-value column that only adds scroll width), **When** fixed, **Then** the fix is a targeted responsive-visibility change (e.g. hiding that one column below a breakpoint), not a parallel card-view rewrite of the whole table.

---

### User Story 3 - Every form remains usable and easy to tap at mobile width (Priority: P2)

A patient booking an appointment from their phone, or a staff member onboarding a new hire from a tablet, needs form fields to stack into a single readable column and buttons/inputs to be comfortably tappable — not squeezed into desktop-width columns or requiring precise taps on small targets.

**Why this priority**: Forms are already verified (spec correction #3) to default to single-column below `sm:` — this story exists to catch any exception that slipped through and to verify real touch-target size, particularly on the patient-facing surface per `PRODUCT.md`'s explicit guidance, not to rebuild working layout.

**Independent Test**: Load each of the 8 forms touched by 051 at ~400px; confirm every field renders in a single column, confirm no field or button is clipped, and measure representative patient-facing interactive elements against a reasonable minimum tap-target size.

**Acceptance Scenarios**:

1. **Given** any of the 8 forms at ~400px, **When** viewed, **Then** every field-grid that is multi-column at desktop width renders as a single column, with no horizontal overflow.
2. **Given** a patient-facing form (`SignupForm`, patient `BookSlotForm`) at ~400px, **When** its buttons and inputs are measured, **Then** each meets a reasonable minimum comfortable tap size; if any does not, it is enlarged as a targeted fix.
3. **Given** a staff/admin-facing form at ~400px, **When** compared to its patient-facing counterpart, **Then** it still meets the WCAG 2.1 AA floor (no comparably-scoped "larger target" requirement applies, per correction #5).

---

### User Story 4 - The day sheet and session views are specifically confirmed usable at narrow widths (Priority: P2)

A doctor or staff member glancing at today's schedule between consultations on a phone needs the day sheet and per-session slot grid to be genuinely readable and operable, not just technically non-broken — this is explicitly named in the backlog as the highest-risk pattern.

**Why this priority**: Called out specifically in the backlog because dense schedule-grid UIs are historically the hardest to get right at narrow widths, even when using an otherwise-acceptable pattern (contained table scroll) — this story ensures that verification actually happens rather than being assumed.

**Independent Test**: Load `DaySheet.tsx` and `SessionSlotsView.tsx` at ~400px and ~768px; confirm the session list and the slot grid within a session are both legible and every interactive element (row link, slot action) is reachable.

**Acceptance Scenarios**:

1. **Given** `DaySheet.tsx` at ~400px, **When** viewed, **Then** the chosen pattern (this feature's plan states explicitly: the existing contained-scroll table, confirmed adequate by live verification, or a specific targeted fix if verification finds a gap) is what actually renders — not an unverified assumption.
2. **Given** `SessionSlotsView.tsx` at ~400px, **When** a session with many slots is viewed, **Then** every slot's status and action remains reachable within the contained scroll area.

---

### Edge Cases

- What happens to a table/form screen already fixed by 046-051 that turns out to have a genuine, previously-unnoticed narrow-width defect (not related to this feature's own changes)? Documented and fixed as part of this pass — that is exactly this feature's stated purpose (a hardening pass across 046-051's surfaces).
- What happens at an intermediate tablet width (e.g. 768px), between the `sm:` mobile breakpoint and full desktop? Verified as part of this pass alongside the ~400px floor, since `PRODUCT.md`/the backlog both name tablet explicitly, not just phone.
- What happens if a genuine defect requires more than a one-line responsive-utility-class fix to resolve? Fixed as a responsive-layout correction scoped to that one defect, never as an excuse to redesign the screen's desktop-width appearance (explicitly out of scope).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The Staff (`ClinicShell`) and Admin (`AdminShell`) shells' existing mobile drawer navigation (`SidebarDrawer.tsx`) MUST be re-verified at ~400px after all of 048-051's changes, with any regression fixed; no new navigation mechanism is built.
- **FR-002**: Each of the 6 table-based screens (`DaySheet.tsx`, `SessionSlotsView.tsx`, `StaffPicker.tsx`, `DoctorPicker.tsx`, `PendingClinicsList.tsx`, `PendingDoctorsList.tsx`) MUST be verified at ~400px to confirm the page body never scrolls horizontally (only the table's own contained scroll area does) and every action within it remains reachable; any genuine defect found MUST be fixed with a targeted responsive change, not a parallel card-view rebuild.
- **FR-003**: Each of the 8 forms touched by 051 MUST be verified at ~400px to render every multi-column field-grid as a single column with no horizontal overflow; any exception found MUST be fixed.
- **FR-004**: Patient-facing interactive elements (buttons, inputs on `SignupForm.tsx` and patient `BookSlotForm.tsx`) MUST be verified against a reasonable minimum comfortable tap-target size at mobile width; any element found too small MUST be enlarged.
- **FR-005**: `DaySheet.tsx` and `SessionSlotsView.tsx` MUST be specifically, individually verified (not assumed adequate merely because they share the general table pattern) at both ~400px and ~768px, with the actual verification outcome and any resulting fix stated explicitly in this feature's plan.
- **FR-006**: This feature MUST NOT change any screen's desktop-width (≥640px) layout or introduce any new functionality — every code change must be a responsive-behavior fix at a narrower width, verified not to alter desktop rendering.
- **FR-007**: `prefers-reduced-motion` MUST continue to be respected by any new or modified CSS transition this feature adds (verified: the existing global override in `index.css` already covers this with no new work required, per correction #4).

### Key Entities

N/A — no data model changes; this is a frontend layout/CSS verification-and-fix pass.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Every screen touched by 046-051 (shell, tables, forms, day sheet) renders with zero page-body horizontal scroll at a ~400px-wide viewport, verified by live browser check.
- **SC-002**: Every interactive element on every verified screen remains reachable and clickable at ~400px, verified by live browser check.
- **SC-003**: The full frontend automated test suite passes with zero regressions after this feature, and any new test added for a fixed defect passes.
- **SC-004**: Zero desktop-width (≥640px) visual regression across every screen touched, verified by a before/after comparison at desktop width for each screen this feature actually modifies.
- **SC-005**: Every genuine narrow-width defect found during this feature's live verification pass is either fixed or explicitly documented as a deliberate, justified exception (mirroring correction #2's table-pattern reasoning) — none silently left broken.

## Assumptions

- "~400px" is this project's own already-established minimum tested width (per its own artifact/responsive conventions referenced in the backlog and used throughout this session's own live verification of 047/050), not a newly invented target.
- "Tablet width" is treated as approximately 768px, the same preset already used by this project's browser-preview responsive-testing workflow.
- No new dependency, CSS framework, or design token is introduced — this pass uses only Tailwind utility classes already established in `frontend/src/index.css`'s `@theme` block.
- A screen this feature's verification finds already fully correct requires no code change at all — this spec does not mandate touching every named screen's source code, only verifying each one.
