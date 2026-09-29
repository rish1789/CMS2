# Feature Specification: Application Shell Sidebar Navigation

**Feature Branch**: `050-sidebar-navigation`

**Created**: 2026-09-15

**Status**: Draft

**Input**: User description: "backlog/047-application-shell-sidebar-navigation.md" — add persistent sidebar navigation to the Staff and Admin app shells, built from the 046 shared component library, so staff/admin users can jump directly between frequently-used tasks instead of round-tripping through a tile-grid dashboard every time. PatientShell's tile-based pattern stays unchanged (confirmed via user clarification: sidebar scope is Staff + Admin shells only, matching PRODUCT.md's differentiated-by-audience design principle).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Jump directly between tasks without returning to the dashboard (Priority: P1)

A clinic staff member working through their day (checking the day sheet, searching for a patient, reviewing the inbox, managing staff) wants a persistent, always-visible way to move between these frequently-used areas, instead of navigating back to `ClinicToolsDashboard`'s tile grid every time they finish one task and start another.

**Why this priority**: The core, named problem in the backlog brief — confirmed today, there is no sidebar anywhere in the app, and every task-to-task navigation round-trips through a dashboard tile grid. This is the single highest-value item; without it, nothing else in this feature has a reason to exist.

**Independent Test**: From any page inside the Staff (`ClinicShell`) or Admin (`AdminShell`) shell, use the sidebar to navigate directly to a different listed destination and confirm it works without returning to the dashboard first.

**Acceptance Scenarios**:

1. **Given** a signed-in staff member viewing any page within `ClinicShell`, **When** the page renders, **Then** a persistent sidebar is visible listing the shell's frequently-used destinations (Day sheet, Doctors, Staff, Find a patient, Onboard staff, Inbox, Join waitlist, Clinic tools home), with the current page's entry visually highlighted.
2. **Given** a signed-in Super Admin viewing any page within `AdminShell`, **When** the page renders, **Then** a persistent sidebar is visible listing its destinations (Pending clinic verifications, Pending doctor verifications, Trigger session generation, Admin home), with the current page's entry visually highlighted.
3. **Given** the sidebar, **When** a user clicks a different nav item, **Then** they navigate directly to that page without passing through the dashboard tile grid.
4. **Given** the existing `ClinicToolsDashboard`/`AdminDashboard` tile grids, **When** this feature ships, **Then** they remain in place as a secondary "quick actions" surface on the dashboard's own home page (not removed) — the sidebar adds a persistent path, it doesn't delete the existing one.

---

### User Story 2 - Sidebar stays usable on a narrow viewport (Priority: P2)

A staff or admin user on a tablet or narrow browser window wants the same navigation capability the sidebar provides on desktop, without it breaking the page layout or disappearing entirely.

**Why this priority**: A persistent sidebar that only works at desktop widths would regress usability for any narrower viewport — this project's own responsive standard (~400px) must be met from day one, even though full responsive polish across the whole app is 052's dedicated job.

**Independent Test**: Resize the viewport to ~400px width on a page within `ClinicShell` or `AdminShell` and confirm every sidebar destination is still reachable through a collapsed/alternative presentation.

**Acceptance Scenarios**:

1. **Given** a narrow viewport (~400px), **When** the sidebar is present, **Then** it collapses to an accessible alternative (icon-only rail, off-canvas drawer, or bottom navigation) rather than breaking the page layout or disappearing with no way to navigate.
2. **Given** the collapsed/alternative presentation, **When** a user opens it, **Then** every destination available in the desktop sidebar is still listed and reachable.

---

### User Story 3 - Sidebar reflects what the signed-in user can actually do (Priority: P2)

A staff member with a specific role (ClinicAdmin, Doctor, or Operations) wants the sidebar to show only navigation destinations relevant to what their role can actually do, so it doesn't present dead-end links to pages they can't use.

**Why this priority**: Directly serves usability and avoids a confusing "why can't I do this" moment — but is secondary to the sidebar existing at all (P1) and working responsively (P2), since role-based filtering refines an already-working nav rather than being required for it to deliver initial value.

**Independent Test**: Sign in as each of the three staff roles and confirm the sidebar's visible items differ according to what that role's existing (backend-enforced) permissions allow.

**Acceptance Scenarios**:

1. **Given** a signed-in staff member with a specific role, **When** the sidebar renders, **Then** it shows only the destinations that role can use, reusing the role/authorization data already available to the frontend session (no new authorization mechanism introduced).
2. **Given** the sidebar's role-based filtering, **When** compared to backend enforcement, **Then** the backend continues to independently enforce every permission regardless of what the sidebar shows or hides (the sidebar is a display convenience, never the source of authorization truth).

---

### Edge Cases

- What happens to a nav item whose destination page doesn't exist yet or was removed? The sidebar MUST NOT show placeholder links to non-existent pages (e.g., no "Billing"/"Reports" entries) — every listed item corresponds to a route verified to exist today.
- What happens when a staff member's role changes mid-session (e.g., re-assigned)? Out of scope for this feature — the sidebar reads the same role/session data every other role-gated UI in this app already reads, and follows its existing refresh behavior.
- What happens to `PatientShell`? Explicitly unchanged by this feature — it keeps its current tile-based, reassurance-first navigation pattern (confirmed via clarification), per `PRODUCT.md`'s differentiated-by-audience design principle.
- What happens to the existing dashboard tile grids (`ClinicToolsDashboard`, `AdminDashboard`)? They remain as a secondary "quick actions" surface on each shell's own dashboard page — this feature adds a persistent navigation path, it does not remove the existing one (see User Story 1, Acceptance Scenario 4).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST provide a persistent, always-visible sidebar within the Staff shell (`ClinicShell`), surfacing its frequently-used destinations (Day sheet, Doctors, Staff, Find a patient, Onboard staff, Inbox, Join waitlist, Clinic tools home).
- **FR-002**: The system MUST provide a persistent, always-visible sidebar within the Admin shell (`AdminShell`), surfacing its destinations (Pending clinic verifications, Pending doctor verifications, Trigger session generation, Admin home).
- **FR-003**: The Patient shell (`PatientShell`) MUST NOT be changed by this feature — its existing tile-based navigation pattern stays as-is.
- **FR-004**: The sidebar MUST be built using the shared component library from Feature 046, not new bespoke markup duplicating an existing pattern.
- **FR-005**: The sidebar MUST show only navigation items corresponding to routes that exist and function today — no placeholder entries for capabilities that don't exist (e.g., Billing, Lab Tests, Reports).
- **FR-006**: The sidebar MUST visually indicate which navigation item corresponds to the currently active page.
- **FR-007**: The sidebar's visible items MUST reflect what the signed-in staff member's role can actually do, reusing the role/authorization data already available to the frontend session — this feature MUST NOT introduce a new authorization mechanism, and backend enforcement remains authoritative regardless of what the sidebar displays.
- **FR-008**: On a narrow viewport (~400px, this project's established responsive standard), the sidebar MUST collapse to an accessible alternative (icon-only rail, off-canvas drawer, or bottom navigation) rather than disappearing or breaking the page layout.
- **FR-009**: This feature MUST NOT change what any existing page renders inside its shell's content outlet — only the shell chrome (header + new sidebar) changes.
- **FR-010**: This feature MUST NOT modify any backend authorization logic — role-based sidebar visibility is a frontend display concern layered on existing data, with the backend's existing per-endpoint enforcement unchanged.
- **FR-011**: The existing `ClinicToolsDashboard` and `AdminDashboard` tile grids MUST remain in place as a secondary "quick actions" surface on their respective dashboard pages — this feature adds a persistent navigation path without removing the existing tile-grid entry points.

### Key Entities

N/A — no data model changes; this is frontend navigation chrome reusing existing route and role/session data.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: From any page within the Staff or Admin shell, a user can reach any other sidebar-listed destination in that shell in a single click/tap, without passing through the dashboard tile grid first.
- **SC-002**: At a ~400px viewport width, 100% of the sidebar's destinations remain reachable through its collapsed/alternative presentation.
- **SC-003**: Every sidebar navigation entry corresponds to a route that exists and functions today — zero placeholder or dead links.
- **SC-004**: No existing page's rendered content changes as a result of this feature — only the surrounding shell chrome (Staff and Admin shells) changes.
- **SC-005**: The full frontend test suite passes after this feature with zero regressions and an increased test count covering the new navigation and its responsive behavior.

## Assumptions

- Sidebar scope is the Staff (`ClinicShell`) and Admin (`AdminShell`) shells only — resolved via user clarification during specification, matching `PRODUCT.md`'s Design Principle 4 (staff = speed/density, admin = utility, patient = reassurance) and the backlog brief's own verified route lists for both shells. `PatientShell` is unchanged.
- The exact subset and ordering of `ClinicShell`'s sidebar items (a curated subset of its 22 routes, not all of them, per the backlog brief) is a planning-time decision, chosen for frequency of use from the verified route list in the backlog brief's Business Rules section — not exhaustively enumerated in this spec beyond the named items.
- Role-based visibility (FR-007) reuses the existing staff session's role/role-assignment data already available to the frontend (the same data `RoleBadge` and existing role-gated UI already read) — no new backend endpoint or authorization mechanism is introduced.
- `DESIGN.md`'s existing tokens and the Feature 046 component library are the source of all new sidebar visuals — no new color, radius, shadow, or type-scale value is introduced by this feature.
- This feature ships a reasonable first-pass responsive behavior (FR-008/SC-002); further responsive polish across the whole app remains 052's dedicated scope.
