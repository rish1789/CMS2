# Feature Specification: Staff Operational Dashboard Enhancement

**Feature Branch**: `051-staff-dashboard-enhancement`

**Created**: 2026-09-15

**Status**: Draft

**Input**: User description: "backlog/048-staff-operational-dashboard.md" — enhance `ClinicToolsDashboard.tsx` (already showing 3 real live-count tiles) with a condensed "Today's sessions" list and a "Today's stats" (completed/no-show) tile, using only real backend data — confirmed via user decision to add one new minimal backend query rather than stay frontend-only, since walk-in/no-show/completed counts have no existing aggregate endpoint.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - See today's actual schedule at a glance (Priority: P1)

Clinic staff starting their day want to see today's sessions (which doctor, what time, how full) directly on the dashboard, instead of clicking into Day Sheet just to find out what's happening today.

**Why this priority**: The single most concrete, most-requested capability in the backlog brief, and the one with a clean existing data source (`listSessions`) requiring zero new backend work — the highest-value, lowest-risk item.

**Independent Test**: Load the dashboard for a clinic with real sessions scheduled today and confirm a "Today's sessions" list shows each session's doctor, time range, and booked/total slot fill, each linking into Day Sheet for that session.

**Acceptance Scenarios**:

1. **Given** a clinic with 1 or more sessions scheduled today, **When** the dashboard loads, **Then** a "Today's sessions" section lists each one (doctor name, time range, mode, booked/total slot count), ordered by start time.
2. **Given** a clinic with zero sessions scheduled today, **When** the dashboard loads, **Then** the section shows a clear empty state, not an error or a blank gap.
3. **Given** a session row in the list, **When** a staff member clicks it, **Then** they navigate directly into that session's Day Sheet detail view.

---

### User Story 2 - See today's completed/no-show counts (Priority: P2)

Clinic staff want to know, at a glance, how many of today's appointments have actually been completed versus marked as no-shows, without opening each session individually.

**Why this priority**: Real, requested operational value, but secondary to simply seeing the schedule (US1) — and the one metric in this feature requiring new (but minimal, verified) backend support, per the user's explicit decision to build it rather than stay frontend-only.

**Independent Test**: For a clinic with a mix of completed, no-show, and still-open slots today, confirm the dashboard's stats tile shows counts matching what a direct query against today's slots would return.

**Acceptance Scenarios**:

1. **Given** a clinic with some of today's slots marked Completed and some marked No-show, **When** the dashboard loads, **Then** a stats tile shows both counts, each traceable to a real backend query.
2. **Given** a clinic with no completed or no-show slots yet today, **When** the dashboard loads, **Then** the tile shows zero for both, not a hidden or broken tile.

---

### User Story 3 - Existing live tiles keep working, visually upgraded (Priority: P3)

Clinic staff who already rely on the existing inbox/day-sheet-count/waitlist-count tiles want them to keep working exactly as before, just visually consistent with the rest of the redesigned app.

**Why this priority**: Preservation of existing, already-real functionality — lower priority than the 2 new capabilities only because there is no new value to deliver here, just continuity plus a visual pass.

**Independent Test**: Confirm the inbox unclaimed-count, day-sheet session-count, and waitlist waiting-count tiles still show the same real counts as before, now styled via the 046 component library.

**Acceptance Scenarios**:

1. **Given** the existing inbox/day-sheet/waitlist tiles, **When** this feature ships, **Then** each still shows its real, live count exactly as before, restyled using 046's `Card`/`Badge` components.
2. **Given** the existing quick-action tiles (Day sheet, Doctors, Staff, Find a patient, Onboard staff, Join waitlist), **When** this feature ships, **Then** every one still links to its real, existing route (per 047's verified route inventory) — none removed, none pointing at a non-existent page.

---

### Edge Cases

- What happens to "walk-ins today" (named in the backlog brief as a candidate metric)? **Explicitly not built** — verified during planning that no schema field anywhere (`Slot`, `Booking`) distinguishes a walk-in-created booking from any other booking origin; building it would require a new persisted column and a migration, not "one minimal query," which is a materially bigger change than what was scoped. Documented here as a real, reported gap per the backlog's own required behavior for exactly this situation, not fabricated or silently dropped.
- What happens to a "Recent Patients"/"Recent Activity" widget (named in the backlog brief as something to consider)? **Explicitly not built** — verified that no clinic-scoped recent-activity/audit data source exists; `Patient.createdAt` is a platform-wide registration timestamp, not a per-clinic visit/interaction signal, so it would not faithfully answer "recent activity at this clinic."
- What happens if a session's `getDaySheet` per-slot detail hasn't loaded yet when the dashboard's "Today's sessions" list renders? N/A — the list uses `listSessions`' existing session-summary data directly (doctor/time/mode/slot-fill-count), not per-session slot detail, so there is nothing further to wait on.
- What happens when the new "today's stats" query's underlying fetch fails? Same silent-fail-per-tile behavior as the 3 existing tiles (already established: independent fetches, one failing never blocks the others) — the tile shows its loading state indefinitely rather than a page-wide error.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The dashboard MUST show a "Today's sessions" list drawn from the existing session-list endpoint, each row showing doctor name, time range, mode, and booked/total slot count, ordered by start time.
- **FR-002**: Each row in the "Today's sessions" list MUST link directly into that session's existing Day Sheet detail view.
- **FR-003**: An empty "Today's sessions" list (zero sessions today) MUST show a clear empty state, not an error or an unexplained blank area.
- **FR-004**: The dashboard MUST show a "Today's stats" tile with 2 real counts: completed slots today and no-show slots today, both traced to a new, minimal backend query (research.md/data-model.md name the exact repository method) — not fabricated, not client-side-estimated.
- **FR-005**: The existing inbox unclaimed-count, day-sheet session-count, and waitlist waiting-count tiles MUST be preserved with unchanged real-data behavior, visually restyled using the 046 shared component library.
- **FR-006**: Every existing quick-action tile (Day sheet, Doctors, Staff, Find a patient, Onboard staff, Join waitlist) MUST continue linking to its real, existing route — none removed, none added pointing to a non-existent page.
- **FR-007**: This feature MUST NOT introduce any metric not traceable to a real, existing or newly-added-and-justified backend query — "walk-ins today" and any "Recent Activity" widget are explicitly excluded per the Edge Cases above.
- **FR-008**: This feature MUST NOT touch the Super Admin (`AdminShell`/`AdminDashboard`) or Patient (`PatientShell`/`PatientDashboard`) dashboards — staff-dashboard scope only.
- **FR-009**: The new backend query MUST reuse the existing "any active role at this clinic" authorization gate already used by the sibling session-list endpoint (`ClinicSessionListController`) — no new authorization mechanism.
- **FR-010**: This feature MUST NOT add any revenue/billing metric or any chart/trend-over-time visualization — both explicitly out of scope per the backlog brief and this system's system-wide no-payments boundary.

### Key Entities

- **Today's session-stats query result**: Not a persisted entity — a small, in-memory aggregation (completed count, no-show count) computed from existing `Slot.status` values for a clinic's sessions on a given date. No new table, no new column.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A staff member can see every one of today's sessions (doctor, time, fill level) without leaving the dashboard or clicking into Day Sheet first.
- **SC-002**: A staff member can see today's completed and no-show counts without opening any individual session.
- **SC-003**: Every number shown anywhere on the dashboard traces to a real, verifiable backend query — zero hardcoded or fabricated values, confirmed by code review against this spec's Functional Requirements.
- **SC-004**: The 3 pre-existing live tiles and all 6 quick-action links continue working exactly as before this feature.
- **SC-005**: The full test suite (frontend, plus backend for the one new query) passes after this feature with zero regressions and an increased test count covering the new query and new dashboard sections.

## Assumptions

- "Today's sessions" is a condensed list of *sessions* (doctor/time/mode/slot-fill), not individual *patient appointments* — the backlog brief's own acceptance criteria named "patient" as a row field, but verified during planning that the existing session-list endpoint (and `DaySheet.tsx`, which the brief explicitly points to as the data source) operates at the session level, with per-patient detail only available one level down (`getDaySheet`, per-session). Building a patient-level list would mean either an N+1 fan-out across every session or a new aggregate endpoint beyond "condensed version of DaySheet's existing list" — corrected to match what `listSessions` actually returns.
- The new backend query lives in `com.cms.scheduling` (same module as the sibling `ClinicSessionListController`/`SlotRepository`), following that module's existing pattern of a controller calling repositories directly for a sufficiently simple, authorization-only read endpoint (no new service class, consistent with `ClinicSessionListController`'s own established shape).
- `DESIGN.md`'s tokens and the 046 component library are the source of all new/restyled dashboard visuals — no new color, radius, shadow, or type-scale value introduced.
