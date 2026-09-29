# Research: Tables & Lists Consistency Pass

## Decision 1: No new `Table` component — corrects the backlog brief

**Decision**: Confirmed (not assumed) via 046's own `research.md`: "`Table`... is deliberately not rebuilt — `PaginationControls`/`SortableColumnHeader`/`FilterSelect` already exist and serve this need." This feature does not build one either.

**Rationale**: The backlog brief's Business Rule referencing "any new shared `Table` component from 046" describes something that was never built, by 046's own explicit, already-converged decision. Building one now would be new, unrequested scope this feature's own spec doesn't call for, and would contradict 046's reasoning (no genuine duplicated pattern existed to extract into a `Table`).

**Alternatives considered**: Build a `Table` component now, retroactively — rejected, no new evidence has emerged since 046 that changes its own reasoning; the real inconsistency (per exhaustive verification) is component *adoption*, not a missing primitive.

## Decision 2: Scope — DaySheet (mandatory) + StaffPicker (best second candidate) + a 7-file empty/loading sweep

**Decision**: `DaySheet.tsx` → `PaginationControls` (FR-001, backlog-mandated). `StaffPicker.tsx`'s bespoke `SortHeader` → shared `SortableColumnHeader` (FR-002, the closest real equivalent to the backlog's "adopt table-like consistency" intent, since it already has real backend-supported sort just not the shared header). Empty/loading-state markup swapped onto `EmptyState`/`LoadingState` across all 7 verified files (FR-003/FR-004): `DaySheet.tsx`, `StaffPicker.tsx`, `DoctorPicker.tsx`, `PendingClinicsList.tsx`, `PendingDoctorsList.tsx`, `ClinicToolsDashboard.tsx`'s Today's Sessions, `PatientHubPage.tsx`.

**Rationale**: Exhaustive codebase search (grep for `Previous`/`Next` button text and `totalPages`/`Math.ceil(totalCount` patterns app-wide) confirmed `DaySheet.tsx` is the *only* remaining hand-rolled pagination footer — every other list-with-pagination already uses `PaginationControls` (11 files, verified). `StaffPicker.tsx` already has backend-supported sort (`sortBy`/`sortDir` params confirmed in `staff-picker/api.ts`) but through a local `SortHeader` component rather than the shared `SortableColumnHeader` — a genuine, fixable inconsistency `DoctorPicker.tsx` doesn't share (it has no sort at all, and no backend support for one). The 7-file empty/loading sweep is a direct, low-risk, zero-behavior-change application of already-existing 046 components (`EmptyState`/`LoadingState`) that even the codebase's own "reference" files (`PendingClinicsList`/`PendingDoctorsList`) predate and don't yet use — closing that gap serves the backlog's own acceptance criteria ("uses the shared `EmptyState` component... not a bespoke wrapper") directly.

**Alternatives considered**: A whole-codebase sweep of every list, including ones already fully consistent (`MyClinicsList.tsx`, `MyBookings.tsx`, etc.) — rejected, no inconsistency exists there to fix (Principle II — don't touch what isn't broken); adding sort to `DoctorPicker.tsx` — rejected, no backend support exists today and none is named as a requirement (FR-005); converting `StaffPicker`/`DoctorPicker` to a non-table shape, or `PatientSearch`'s card grid into a table — rejected, both are already the right shape for their data (FR-007, and the backlog's own explicit "don't blanket-apply" caution).

## Decision 3: `ClinicToolsDashboard.tsx`'s Today's Sessions and `PatientHubPage.tsx`'s clinical tabs stay uncapped-behavior-unchanged

**Decision**: Only their empty/loading *markup* is swapped onto the shared components (Decision 2) — no pagination is added to either.

**Rationale**: `ClinicToolsDashboard.tsx`'s Today's Sessions section is a deliberately **capped** (not paginated) dashboard glance widget — 051's own `research.md`/code comment explicitly documents `MAX_SESSIONS_SHOWN = 10` as "a condensed glance, not the full Day Sheet," with a "+N more in Day Sheet" link as its own designed escape hatch to the real, paginated list. `PatientHubPage.tsx`'s Consultations/Prescriptions/External Records tabs render a client-side filter of the Bookings tab's *already-paginated* single page (052's own design) — they are a derived view of one page, not an independent list needing its own pager. Adding pagination to either would directly contradict a prior feature's own explicit, documented design decision, not fix an inconsistency.

**Alternatives considered**: Paginating Today's Sessions independently of the dashboard's cap — rejected, reverses 051's own reasoned decision without new evidence that it's wrong; paginating the Patient Hub's clinical tabs separately from Bookings — rejected, would mean 3 separate fetches/pagers over what's fundamentally one list, adding real complexity (Principle II) for a need not demonstrated at this system's scale.
