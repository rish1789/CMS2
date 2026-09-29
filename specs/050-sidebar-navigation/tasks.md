---

description: "Task list for Application Shell Sidebar Navigation"
---

# Tasks: Application Shell Sidebar Navigation

**Input**: Design documents from `/specs/050-sidebar-navigation/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, quickstart.md

**Tests**: New tests for `Sidebar`/`SidebarDrawer` (genuinely new behavior — active-state, role filtering, drawer open/close). No dedicated test files exist today for `ClinicShell.tsx`/`AdminShell.tsx` themselves (verified: none found under `frontend/tests/`) — the full suite plus live `quickstart.md` verification is the regression gate for those 2 edited files, same substitution 046 used for `AdminDashboard`/`PatientDashboard`.

**Organization**: Foundational builds the 2 new shared components (both stories need them to exist first). US1 (P1, persistent nav) wires them into both shells with real data — the MVP. US2 (P2, responsive collapse) is delivered by the same foundational component, so its own task is the shell-level verification that the already-built collapse behavior actually works end-to-end once wired. US3 (P2, role filtering) extends `ClinicShell` to resolve and pass the real role, completing the one verified real restriction (`Onboard staff`).

## Phase 1-2: Setup / Foundational

- [x] T001 Create `frontend/src/components/Sidebar.tsx`: nav list from an `items: SidebarNavItem[]` prop (`data-model.md`), active-item highlighting via react-router `NavLink`, optional `activeRole` prop filtering out any item whose `roles` allowlist excludes it.
- [x] T002 Create `frontend/src/components/SidebarDrawer.tsx`: responsive wrapper — renders `Sidebar` inline at desktop widths; below the `sm:` breakpoint (research.md Decision 3), renders a hamburger toggle **built on 046's `Button`** (`variant="secondary"`, icon-only — reusing the shared component library per FR-004, not hand-rolled button CSS) that opens a fixed-position, off-canvas drawer (slide-in panel + backdrop, own component, not the 046 `Modal`) containing the same `Sidebar`, closing on backdrop click, Escape, or item selection. Added one new icon (`HomeIcon` in `adminIcons.tsx`) — no existing icon covered "home"/dashboard, and reusing `ClinicIcon` for it would have duplicated visually with "Pending clinic verifications" in the same Admin sidebar list.
- [x] T003 [P] Add `frontend/tests/components/Sidebar.test.tsx`: renders all items by default; highlights the item matching the current route; hides an item whose `roles` allowlist excludes the given `activeRole`; shows it when `activeRole` is included or the item has no allowlist. 6 tests, all passing.
- [x] T004 [P] Add `frontend/tests/components/SidebarDrawer.test.tsx`: at desktop width renders `Sidebar` inline with no hamburger button; at narrow width renders a hamburger button that opens the drawer, lists every item, and closes on backdrop click, Escape, and item selection. 6 tests, all passing.

**Checkpoint**: `Sidebar`/`SidebarDrawer` exist, fully tested standalone, ready to wire into real shells.

---

## Phase 3: User Story 1 - Jump directly between tasks without returning to the dashboard (Priority: P1) 🎯 MVP

**Goal**: A persistent sidebar is visible on every page within the Staff and Admin shells, listing each shell's real destinations, with direct navigation and active-item highlighting.

**Independent Test**: From any page inside `ClinicShell` or `AdminShell`, use the sidebar to navigate directly to a different listed destination without returning to the dashboard first.

### Implementation for User Story 1

- [x] T005 [US1] Edit `frontend/src/routes/staff/ClinicShell.tsx`: render `SidebarDrawer` with the 8 real staff nav items (Day sheet, Doctors, Staff, Find a patient, Onboard staff, Inbox, Join waitlist, Clinic tools home — each `to` a real, verified `/staff/clinics/${clinicId}/...` route from `App.tsx`, icon reused from `staffIcons.tsx`/`adminIcons.tsx`), alongside the existing breadcrumb + `Outlet` (breadcrumb/switch-clinic behavior unchanged). Restructured the return into a `flex gap-6` row (sidebar + content); `StaffShell.tsx` itself untouched (research.md Decision 1).
- [x] T006 [US1] Edit `frontend/src/routes/admin/AdminShell.tsx`: render `SidebarDrawer` with the 4 real admin nav items (Pending clinic verifications, Pending doctor verifications, Trigger session generation, Admin home — real `/super-admin-console/...` routes, icons reused from `adminIcons.tsx`), alongside the existing header + `Outlet`. Widened the outer container from `max-w-5xl` to `max-w-6xl` to fit the sidebar alongside content without cramming either.
- [x] T007 [US1] Confirm `ClinicToolsDashboard.tsx`/`AdminDashboard.tsx` are unedited by T005/T006 (FR-011) — their tile grids remain the dashboard's own content, now simply reachable via both the sidebar's "home" entry and the shell's index route, same as today. Confirmed via `git status` — neither file touched by this feature (AdminDashboard.tsx's own diff is 046's earlier, unrelated Card/Button migration).

**Checkpoint**: Staff and Admin users have persistent, direct navigation; dashboards unchanged.

---

## Phase 4: User Story 2 - Sidebar stays usable on a narrow viewport (Priority: P2)

**Goal**: The sidebar wired into real shells in US1 collapses to an accessible drawer at ~400px, with every destination still reachable.

**Independent Test**: Resize to ~400px on a page within `ClinicShell` or `AdminShell` and confirm every sidebar destination is still reachable through the collapsed presentation.

### Verification for User Story 2

- [x] T008 [US2] Live-verify (quickstart.md step 8) that `ClinicShell`'s and `AdminShell`'s now-wired `SidebarDrawer` (T005/T006) actually collapses correctly end-to-end at ~400px in the real app — not just `SidebarDrawer.test.tsx`'s isolated unit coverage (T004) — confirming the real nav item lists, real icons, and real route data all render correctly inside the drawer, not only the component's synthetic test props. **Verified live** via the dev server at a 375px viewport: desktop sidebar collapsed to a hamburger button, opening it showed all 7 (Doctor-role-filtered) items in the drawer with correct active highlighting, and clicking "Day sheet" navigated and auto-closed the drawer.

**Checkpoint**: Sidebar navigation is fully usable at both desktop and narrow widths on the real shells.

---

## Phase 5: User Story 3 - Sidebar reflects what the signed-in user can actually do (Priority: P2)

**Goal**: `ClinicShell`'s sidebar hides "Onboard staff" for any role other than ClinicAdmin, using the caller's real, already-fetched clinic-membership role.

**Independent Test**: Sign in as each of the 3 staff roles and confirm the sidebar's visible items differ according to role — specifically, "Onboard staff" is ClinicAdmin-only.

### Implementation for User Story 3

- [x] T009 [US3] Edit `frontend/src/routes/staff/ClinicShell.tsx`: extend the existing `listMyClinics` result handling (currently only reads `.name` for the breadcrumb) to also capture the matching membership's `.role`, and pass it as `Sidebar`'s `activeRole` prop (data-model.md) — no new fetch.
- [x] T010 [US3] Add `roles: ['ClinicAdmin']` to the "Onboard staff" item in `ClinicShell.tsx`'s nav item list (T005) — the one item with a verified backend restriction (research.md Decision 4, `StaffOnboardingService`). Every other item (staff and admin) ships with no `roles` allowlist.
- [x] T011 [US3] Live-verify (quickstart.md steps 5-6): sign in as ClinicAdmin — "Onboard staff" visible; sign in as Doctor or Operations at the same clinic — "Onboard staff" hidden, every other item unchanged. **Verified live** via the dev server with a mocked `/api/v1/clinics/mine` response (no real backend running): ClinicAdmin role showed all 8 items including "Onboard staff"; re-mounting with Doctor role showed exactly 7, "Onboard staff" correctly absent, every other item and the active highlight unchanged.

**Checkpoint**: All 3 user stories independently functional; sidebar shows only what each role can actually do.

---

## Phase 6: Polish

- [x] T012 `npx tsc -b` — zero type errors. Confirmed clean.
- [x] T013 `npm run lint` — zero new errors. Confirmed: 0 errors; only pre-existing warnings elsewhere in the codebase, none in this feature's files.
- [x] T014 `npm run test -- --run` — full suite, zero regression, count increases by the new `Sidebar`/`SidebarDrawer` tests. Confirmed: 263/263 passing across 51 files (251 baseline + 12 new: 6 `Sidebar.test.tsx` + 6 `SidebarDrawer.test.tsx`).
- [x] T015 Manual live verification per `quickstart.md` steps 5-11. Steps 5-9 live-verified with a mocked `/api/v1/clinics/mine` response (see T008/T011) — ClinicAdmin sees all 8 staff items, Doctor sees 7 (no "Onboard staff"), direct navigation with active-highlighting confirmed, ~400px drawer collapse/open/auto-close-on-selection confirmed on both the mocked staff route and the real (unmocked) Admin console. Steps 10-11 (`/staff` picker and `PatientShell` show no sidebar; dashboards unchanged) confirmed by code inspection rather than a live screenshot — `StaffShell.tsx`/`PatientShell.tsx`/`ClinicToolsDashboard.tsx`/`AdminDashboard.tsx` are untouched by this feature (git status), so there is nothing new to render there.
- [x] T016 Update `backlog/progress.md`'s row for `047-application-shell-sidebar-navigation`.

---

## Dependencies & Execution Order

- Foundational (T001-T004) blocks every user story — `Sidebar`/`SidebarDrawer` must exist before either shell can be wired.
- US1 (T005-T007) is the MVP and has no dependency on US2/US3.
- US2 (T008) depends on US1's shell wiring (T005/T006) existing to verify against — it validates behavior already built in Foundational, applied to real shells.
- US3 (T009-T011) depends on US1's `ClinicShell` wiring (T005) existing (extends the same nav item list and adds the role-resolution code next to it) but is otherwise independent of US2.
- Polish depends on all 3 stories complete.

## Notes

- Total: 16 tasks.
- No backend task — this feature touches no backend code (plan.md Technical Context, Constitution Principle III: N/A).
- `PatientShell` has zero tasks — explicitly unchanged (FR-003).
