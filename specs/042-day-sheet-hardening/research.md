# Phase 0 Research: Day Sheet Hardening

No `NEEDS CLARIFICATION` markers remain in the Technical Context — this feature reuses the project's existing stack throughout. The entries below record the design decisions made for each finding, not open unknowns.

## R1: Session list pagination

**Decision**: Add standard offset-based `page`/`size` query params to `GET /api/v1/clinics/{clinicId}/sessions`, backed by Spring Data's `Pageable`/`Page<Session>`, returning a bounded page plus a total count.

**Rationale**: SC-002 requires the endpoint to stop returning every session in the 14-day window in one response. `Pageable` is the standard Spring Data idiom, needs only a repository method signature change (from `List<Session>` to `Page<Session>`), and keeps the change minimal.

**Alternatives considered**: Cursor-based pagination — rejected as more machinery than a 14-day-bounded, moderate-volume list needs, and there's no existing cursor-pagination precedent anywhere else in this codebase to match. Leaving it unbounded and only limiting client-side rendering — rejected, that's the status quo this feature exists to fix (finding #6).

## R2: Doctor filter, decoupled from pagination

**Decision**: Add an optional `doctorProfileId` query param to the same endpoint (mirroring its own existing optional `from`/`to` params) to filter the paginated results. Separately, the response always includes a `doctors: [{ doctorProfileId, name }]` list — the distinct doctors with at least one session in the current 14-day window — computed via one small `DISTINCT`-style query, independent of the current page or filter.

**Rationale**: Once the list is paginated, the frontend can no longer safely derive "which doctors have sessions in this window" from just the current page (a doctor might only appear on page 3) — the pattern used for Roster's specialization filter (derive options from already-fully-fetched data) doesn't carry over once the fetch itself is no longer complete. A small, separate, always-complete `doctors` list solves this without a new endpoint or N+1 client-side calls.

**Alternatives considered**: A dedicated `/clinics/{clinicId}/sessions/doctors` endpoint to drive the filter — rejected as an unjustified new endpoint for a small, cheap piece of data that belongs alongside the list it filters.

## R3: Per-session fullness (booked/total slot count)

**Decision**: Compute `bookedSlotCount`/`totalSlotCount` per session in the same list call via one bulk aggregate query keyed by session ID (covering every session on the current page), not a per-session call.

**Rationale**: Mirrors the exact "one bulk-loaded query, no N+1" pattern already used and proven this session for Roster's Doctor-profile enrichment (specialization/experience). No new entity or denormalized counter column — `Slot`/`Booking` already hold everything needed.

**Alternatives considered**: A per-session-card frontend fetch on render — rejected, turns one list load into N+1 network calls.

## R4: `ActionMenu` exclusivity + off-screen clipping

**Decision**: Apply the identical, already-proven fix from the Roster page's row-actions menu: native `<details name="staff-row-actions">`-style shared-name grouping for cross-instance exclusivity, plus rendering the popup through a `createPortal` to `document.body` with `position: fixed`, positioned from the trigger's `getBoundingClientRect()`.

**Rationale**: This is the identical bug, in the identical shape (a `<details>`-based disclosure popup with no exclusivity and no boundary awareness), already diagnosed and fixed once this session. The root cause there wasn't "which direction to flip" — it was that an `overflow-x: auto` ancestor forces the other overflow axis to `auto` too (per the CSS spec), clipping any same-subtree absolutely-positioned popup regardless of direction. A portal sidesteps that ancestor entirely. Re-deriving a new fix here would be strictly worse than reusing the proven one.

**Alternatives considered**: None seriously — same component class, same codebase, same session.

## R5: Session detail header data gap

**Decision**: Extend `SessionDaySheetResponse` with `doctorName` and `sessionDate`, populated from data the controller already loads (`Session` already has a `doctorProfile` association; the account name is reachable through it) — zero new queries.

**Rationale**: Cheapest possible fix for FR-010 — the data was already being loaded server-side for authorization checks, just never serialized into the response.

**Alternatives considered**: Continuing to rely on React Router navigation `state` — rejected, that's the bug.

## R6: Whole-session cancellation confirmation

**Decision**: Add a `phase` state (`idle` → `confirming` → `submitting`) directly inside `CancelSessionButton.tsx`, identical in shape to the confirm-step already used for staff deactivation and individual booking cancellation elsewhere in this codebase.

**Rationale**: Zero new interaction pattern — reuses a pattern already proven and already familiar to users of this staff console. Backend endpoint/contract is unchanged; this is a frontend-only change.

**Alternatives considered**: A browser-native `confirm()` dialog — rejected, inconsistent with every other confirm-gated action in this codebase, which all use an inline Cancel/Confirm step instead.

## R7: Visual consistency with Roster

**Decision**: Apply the same Tailwind vocabulary already established on the Roster page — `slate-*` palette, deterministic gradient initial-avatars, `rounded-lg`/`rounded-xl` cards with `shadow-sm`, `indigo-600` interactive accents, row/card hover transitions — to `DaySheet.tsx`, `SessionSlotsView.tsx`, and `ActionMenu.tsx`.

**Rationale**: FR-012 asks for consistency with what already exists, not a new design system.

**Alternatives considered**: None — inventing a third visual language would directly contradict FR-012.

## R8: Missing index

**Decision**: New Flyway migration adding `CREATE INDEX idx_session_clinic_id_session_date ON session (clinic_id, session_date);`, matching this project's existing `idx_` naming convention (e.g. `idx_role_assignment_account_id`, `idx_role_assignment_clinic_id` from V1).

**Rationale**: Directly supports the `clinic_id` + `session_date` range filter this list query already runs. Postgres does not auto-index foreign key columns, and no index currently covers this query at all (confirmed by reading V8's migration directly).

**Alternatives considered**: None needed — a standard, uncontroversial supporting index with no behavioral tradeoff.
