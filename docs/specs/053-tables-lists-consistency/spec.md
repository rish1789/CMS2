# Feature Specification: Tables & Lists Consistency Pass

**Feature Branch**: `053-tables-lists-consistency`

**Created**: 2026-09-15

**Status**: Draft

**Input**: User description: "backlog/050-tables-lists-consistency-pass.md" — bring the app's inconsistent list views (search/filter/sort/pagination/empty/loading behavior) onto the already-proven pattern from `PendingClinicsList.tsx` and 046's shared components, starting with the one confirmed inconsistency (`DaySheet.tsx`'s hand-rolled pagination footer).

## Correction to the backlog brief

Verified during specification (not assumed): **046 explicitly decided not to build a new shared `Table` component** — its own `research.md` states "`Table`... is deliberately not rebuilt — `PaginationControls`/`SortableColumnHeader`/`FilterSelect` already exist and serve this need." The backlog brief's Business Rule "any new shared `Table` component from 046 MUST be adopted" refers to a component that does not exist and was never built. This feature instead brings lists onto fuller, more consistent use of the shared components that **do** exist (`PaginationControls`, `SortableColumnHeader`, `EmptyState`, `LoadingState`) — the same substance the backlog brief intended, corrected to match what 046 actually shipped.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Day Sheet paginates the same way every other list does (Priority: P1)

Staff paginating through a clinic's sessions on the Day Sheet want the same pagination control (page indicator, record range, jump-to-page) already used on every admin verification queue and staff picker, not a plain "Page X of Y" with bare Previous/Next buttons.

**Why this priority**: The one concretely identified, backlog-mandated inconsistency — verified via exhaustive search to be the **only** remaining hand-rolled pagination footer anywhere in the app (every other list already uses the shared `PaginationControls`). Fixing it closes the last real gap of its kind.

**Independent Test**: Load Day Sheet with more sessions than fit one page and confirm pagination looks and behaves identically to `PendingClinicsList.tsx`'s.

**Acceptance Scenarios**:

1. **Given** Day Sheet with more sessions than one page, **When** staff paginate, **Then** they use the shared `PaginationControls` component (page indicator, record range, jump-to-page), not the current hand-rolled Previous/Next footer.
2. **Given** Day Sheet's existing doctor filter and session data, **When** this feature ships, **Then** filtering behavior is unchanged — only the pagination footer's component changes.

---

### User Story 2 - Staff roster sorts through the same shared column-header control as every admin list (Priority: P2)

A ClinicAdmin sorting the staff roster by name, experience, or join date wants the same clickable, direction-indicating column header already used on the Super Admin verification queues, not a differently-styled bespoke one.

**Why this priority**: `StaffPicker.tsx` already has real sort capability (backend-supported) but through a locally-defined header component instead of the shared one — a real, fixable inconsistency, though lower-stakes than US1 since the underlying capability already works correctly today.

**Independent Test**: Sort the staff roster by each sortable column and confirm the header control's appearance and interaction matches `PendingClinicsList.tsx`'s sortable columns exactly.

**Acceptance Scenarios**:

1. **Given** the staff roster's sortable columns (name, experience, joined date), **When** staff click a column header, **Then** it uses the shared `SortableColumnHeader` component, with identical sort/direction-toggle behavior to before.

---

### User Story 3 - Every migrated list shows empty/loading states the same way (Priority: P2)

Staff and Super Admins looking at any of the lists this feature touches want a consistent "no results" and "loading" presentation, not a different hand-rolled message and skeleton per file.

**Why this priority**: Directly required by the backlog's acceptance criteria for any list this feature migrates — secondary to US1/US2 since it's a presentational consistency pass with zero behavior change, but still a real, verifiable requirement, not optional polish.

**Independent Test**: Trigger an empty result and a loading state on each list this feature touches and confirm both use the shared `EmptyState`/`LoadingState` components with list-specific wording.

**Acceptance Scenarios**:

1. **Given** any list this feature migrates (`DaySheet.tsx`, `StaffPicker.tsx`, `DoctorPicker.tsx`, `PendingClinicsList.tsx`, `PendingDoctorsList.tsx`, `ClinicToolsDashboard.tsx`'s Today's Sessions, `PatientHubPage.tsx`), **When** it has zero results, **Then** it shows the shared `EmptyState` component with that list's own specific wording (e.g. "No sessions scheduled in the next 14 days" vs. "No matching patients"), not a bespoke `<p>` wrapper.
2. **Given** any list this feature migrates, **When** it is loading, **Then** it shows the shared `LoadingState` component (list variant, wrapping the existing `ListSkeleton`), not a directly-rendered `ListSkeleton` or a hand-rolled pulse skeleton.

---

### Edge Cases

- What happens to `ClinicToolsDashboard.tsx`'s "Today's sessions" section? It stays a deliberately **capped** list (not paginated) — 051's own research.md documented this as intentional (a dashboard "glance" widget capped at 10, linking to the full Day Sheet for more, not a second full pager). This feature only brings its empty/loading *markup* onto the shared components — it does not add pagination there, which would contradict 051's own explicit design decision.
- What happens to `DoctorPicker.tsx`'s lack of sort? It stays as-is — verified its backend endpoint has no `sort`/`sortBy` parameter today, and adding one would be new backend capability not named as a requirement here (out of scope per the backlog's own "search/filter/sort/pagination MUST only be added where the backend already supports it" rule).
- What happens to `PatientHubPage.tsx`'s Consultations/Prescriptions/External Records tabs? They stay unpaginated (they render a client-side filter of the Bookings tab's single already-paginated page, per 052's own design) — only the Bookings tab's existing `PaginationControls` usage and the shared loading-state swap are in scope.
- What happens to a list not named in this spec (e.g. `MyClinicsList.tsx`, `MyBookings.tsx`)? Out of scope — they already use `PaginationControls` correctly (verified), and this feature does not do a whole-codebase sweep beyond the lists it explicitly names.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: `DaySheet.tsx`'s pagination footer MUST be migrated to the shared `PaginationControls` component, with unchanged filtering/data behavior.
- **FR-002**: `StaffPicker.tsx`'s locally-defined sort-header component MUST be replaced by the shared `SortableColumnHeader` component, with unchanged sort behavior.
- **FR-003**: `DaySheet.tsx`, `StaffPicker.tsx`, `DoctorPicker.tsx`, `PendingClinicsList.tsx`, `PendingDoctorsList.tsx`, `ClinicToolsDashboard.tsx`'s Today's Sessions section, and `PatientHubPage.tsx` MUST show their empty-result state via the shared `EmptyState` component, each with its own existing, list-specific wording preserved.
- **FR-004**: The same 7 files' loading state MUST render via the shared `LoadingState` component (`variant="list"`), not a directly-rendered `ListSkeleton` or a bespoke pulse skeleton.
- **FR-005**: This feature MUST NOT add any new sort/filter/search capability to a list whose backend doesn't already support it (`DoctorPicker.tsx` sort stays out of scope, per verified backend capability).
- **FR-006**: This feature MUST NOT add pagination to `ClinicToolsDashboard.tsx`'s Today's Sessions section or to `PatientHubPage.tsx`'s Consultations/Prescriptions/External Records tabs — both are deliberately capped/derived views by their own prior features' design.
- **FR-007**: This feature MUST NOT build a new `Table` component (046 already decided against one) and MUST NOT convert any list-rendered picker into a literal `<table>` (or vice versa) purely for consistency's own sake.

### Key Entities

N/A — no data model changes; this is a frontend consistency pass reusing existing components and existing backend query parameters.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Zero hand-rolled pagination footers remain anywhere in the app — `DaySheet.tsx` is the confirmed last one.
- **SC-002**: Every sortable list in the app uses the same sortable-column-header component and interaction pattern.
- **SC-003**: Every one of the 7 named lists shows its empty and loading states via the shared components, with zero change to the underlying search/filter/sort/pagination behavior itself.
- **SC-004**: The full frontend test suite passes after this feature with zero regressions to actual list functionality (search still filters, sort still sorts, pagination still paginates) — component-level test updates for the new shared markup are expected and fine.

## Assumptions

- "Any new shared `Table` component from 046 MUST be adopted" (backlog brief) is corrected per the note above — no such component exists; this feature's real scope is fuller adoption of `PaginationControls`/`SortableColumnHeader`/`EmptyState`/`LoadingState`, which do exist.
- The 7 files named in FR-003/FR-004 were chosen as the concrete, verified scope: the one backlog-mandated fix (`DaySheet.tsx`), the one other list benefiting most from full shared-component adoption (`StaffPicker.tsx`, which already has sort but via a bespoke header), and the remaining lists already using `PaginationControls` correctly but still using ad hoc empty/loading markup — including the two most recently shipped features' own list surfaces (048, 049), per the backlog's own suggestion to cover them if already landed.
