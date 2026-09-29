# 050 — Tables & Lists Consistency Pass

**Module:** Frontend / Design System Application
**Status:** Ready for spec-kit intake

## User Story
As clinic staff or a Super Admin working with lists of data (day sheets, staff rosters, doctor/patient pickers, pending verification queues), I want every table/list in CMS2 to behave the same way (search, filter, sort, pagination, empty/loading states), so that learning one list's behavior means I already know how every other list works.

## Context
Verified current state (2026-09-15): behavior is inconsistent across the app's list views. `PendingClinicsList.tsx` (Super Admin) is fully-featured — server-side debounced search, a reason filter, sortable columns via the shared `SortableColumnHeader`, tabs, bulk row-select/actions, and the shared `PaginationControls` component. `DaySheet.tsx` (staff) has a doctor filter and pagination but uses its **own hand-rolled Previous/Next footer** instead of the shared `PaginationControls` — a direct inconsistency with the admin list doing the same job differently. `StaffPicker.tsx`/`DoctorPicker.tsx` are list-rendered (not `<table>`) with debounced search but no sort and no explicit pagination beyond a page-size fetch.

This feature does not invent new list behavior — it takes the *already-proven* pattern from `PendingClinicsList.tsx` (the most complete example in the codebase) and the shared components from 046, and brings the inconsistent lists (starting with `DaySheet.tsx`) onto that same pattern.

## Business Rules
- `DaySheet.tsx` MUST be migrated to use the shared `PaginationControls` component instead of its hand-rolled footer — this is the one concretely identified, confirmed inconsistency and MUST be fixed as part of this feature, not left as a "nice to have."
- Any new shared `Table` component from 046 MUST be adopted by at least `DaySheet.tsx` and one other list (e.g. `StaffPicker.tsx` or `DoctorPicker.tsx`, whichever benefits most from becoming table-shaped vs. staying list-shaped — decide per data type at planning time, not by blanket converting every list into a `<table>` regardless of fit).
- Search/filter/sort/pagination MUST only be added to a list where the underlying backend endpoint already supports the needed parameter (e.g. server-side search requires the backend query to accept a search term) — if a list would benefit from a capability the backend doesn't yet support, that's a named, explicit backend addition in the plan, not a client-side-only fake filter over an unbounded fetch.
- Tables/lists on smaller screens MUST use a responsive alternative (per this project's existing responsive standard) rather than forcing horizontal scroll on a wide table — final responsive polish is 052's job, but this feature should not ship a new table pattern that's mobile-hostile by construction.
- Empty and loading states for every migrated list MUST use 046's shared `EmptyState`/loading components, replacing the currently-inconsistent per-file wording and markup.

## Acceptance Criteria
- Given `DaySheet.tsx` after this feature, when a staff member paginates through slots, then it uses the same `PaginationControls` component and interaction pattern as `PendingClinicsList.tsx`.
- Given any list migrated in this feature, when it has more results than fit on one page, then pagination behaves identically in interaction pattern to every other paginated list in the app.
- Given an empty result set on any migrated list, when displayed, then it uses the shared `EmptyState` component with list-specific wording (e.g. "No slots yet for this session" vs. "No matching patients"), not a bespoke wrapper.
- Given the full frontend test suite, when run after this feature, then all existing tests for migrated components pass (updated as needed for the new shared components) with no regression in actual list functionality (search still filters correctly, sort still sorts correctly, etc.).

## Dependencies
- Depends on 046 (shared UI component library) for `Table`/`PaginationControls`/`EmptyState` primitives (or 046's plan may determine `PaginationControls` is already good enough as-is and just needs wider adoption — a real design decision to make during 046 or 050's planning, not assumed here).
- Benefits from 048/049 landing first (both introduce new list-like surfaces on the dashboard/patient hub) so this pass can cover them too, but is not strictly blocked by either.

## Explicitly Out of Scope
- Any new list/table for data that doesn't exist yet (e.g. no billing/invoice table, no lab-results table).
- Converting every list-rendered picker (`StaffPicker`, `DoctorPicker`, `PatientPicker`) into a literal `<table>` regardless of whether that's the right shape for that data — evaluate per-component, don't blanket-apply.
- Full virtualized/infinite-scroll rendering for very large datasets — not a currently-demonstrated need in this system's scale.

## Source References
- Verified against current repository state via direct inspection, 2026-09-15 (confirmed `DaySheet.tsx`'s hand-rolled pagination footer vs. `PendingClinicsList.tsx`'s use of the shared `PaginationControls` component; confirmed `StaffPicker`/`DoctorPicker` lack sort/explicit pagination)
