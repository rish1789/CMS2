# Integration Gaps — Composition, Reachability, and App-Shell

This file covers architecture-level gaps: places where correct, tested code on both sides of the stack
has no path connecting them together into something a real user could actually exercise.

---

## `[HIGH] - [APP_SHELL] - [NO_ROUTING_INFRASTRUCTURE]` — the umbrella finding

- Evidence: `frontend/src/App.tsx` renders exactly one component (`RegistrationForm`) and nothing else.
  `frontend/src/main.tsx` mounts `<App />` with no wrapper.
- Evidence (stronger — checked so this isn't just an incomplete `App.tsx`): `frontend/package.json`
  dependencies are `{"react": "^18.3.1", "react-dom": "^18.3.1"}` only. **There is no router library of
  any kind** — no `react-router-dom`, no `@tanstack/react-router`, nothing — anywhere in the dependency
  tree.
- Scope: this affects all 23 feature folders under `frontend/src/features/`. Every component beyond
  `RegistrationForm` is implemented, individually unit-tested (72+ passing test files across the
  codebase), and — per the findings in `01`–`03` — largely wired correctly to its backend contract, but
  has **no way to be reached by a real user** through the application as it currently boots.
- This is a known, pre-existing, already-acknowledged limitation of this codebase's build process (each
  of the 39 backlog features shipped its own frontend component in isolation, by design, per that
  process's own documented scope) — it is restated here formally as the umbrella integration gap because
  every "orphaned component" and "unreachable workflow" finding below is a specific instance of it.
- Fix: introduce a router (e.g. `react-router-dom`), build a real app shell with authenticated
  route groups for staff/patient/super-admin, and mount each of the 23 feature components behind
  appropriate routes and role/session guards.

**STATUS: FIXED, 2026-09-07.** Added `react-router-dom`; built a real app shell under
`frontend/src/routes/` — `RequireStaffSession`/`RequirePatientSession` guards (redirect to the
respective login route when no session exists), a `StaffShell`/`PatientShell` (top nav + sign out)
each wrapping a role-scoped route tree, and an unguarded `AdminShell` (Super Admin authenticates
per-request with HTTP Basic, mirroring its own existing "caller owns where credentials live"
design, so no session guard applies there). Every one of the 23 feature folders' top-level
components is now mounted behind a route and reachable by a real user via a URL — `App.tsx` is now
the full route table instead of rendering only `RegistrationForm`. One real limitation, stated
plainly rather than papered over: no "list my clinics"/"list my bookings" endpoint exists anywhere
in this backend, so several dashboard pages (`StaffDashboard`, `ClinicToolsDashboard`,
`PatientDashboard`) are id-entry forms (type a clinic/booking/session id, then navigate), not full
list-and-pick UIs — the routing/reachability gap this finding describes is closed, but building
those list endpoints is separate, larger product work outside this diagnostic pass's scope. Full
frontend suite green (116/116); `tsc -b` and `vite build` both clean.

---

## Specific reachability gaps *beyond* the general routing absence

These are cases where, even if a router and shell existed today, the component still could not be used
as designed — because either no caller passes it the data it needs, or the two halves of a workflow are
never composed together anywhere in the codebase.

**[HIGH] - [WAITLIST_CLAIM] - [WORKFLOW_GAP]** *(full detail in `02_api_layer.md`)*
`ClaimOfferCard.tsx` requires a real `entryId` prop, and no endpoint anywhere lets a patient discover
their own waitlist entry's id. Adding a router would not fix this — a genuinely new backend endpoint is
needed first. Tracked in detail in `02_api_layer.md` since its root cause is a missing API surface, not
just missing composition. **STATUS: FIXED, 2026-09-07** — see `02_api_layer.md`.

**[MEDIUM] - [SCHEDULE_EDIT] - [ORPHANED_COMPONENT]** *(full detail in `02_api_layer.md`)*
The backend's schedule-edit endpoint has literally no frontend component that could call it — not a
routing gap, a missing-component gap. `editSchedule()` exists in `api.ts` with zero callers.
**STATUS: FIXED, 2026-09-07** — see `02_api_layer.md`.

**[LOW] - [SESSION_DELAY] - [ORPHANED_COMPONENT]**
- Frontend: `frontend/src/features/session-delay/DelayIndicator.tsx:8-9,31` — the `refreshKey` prop
  exists solely so a parent can force a re-fetch, per its own doc comment ("Bump this to re-fetch after
  a trigger point (e.g. a completion...) elsewhere on the page"). `CompleteSlotButton.tsx:8,25` fires an
  `onCompleted` callback on success.
- Break: a repo-wide grep confirms no file imports both components together, and no file passes
  `onCompleted` into anything that increments a `refreshKey` — not even within the `session-delay`
  feature folder's own test files. This is a narrower, independently-verifiable instance of the general
  app-shell gap: even setting routing aside, these two components have never once been composed with
  each other anywhere in the tree, so the "recalculation happens elsewhere, then the display refreshes"
  design this feature was built around is entirely unexercised code.
- Fix: when a composing parent/page is built, wire `CompleteSlotButton`'s `onCompleted` to increment a
  counter passed as `DelayIndicator`'s `refreshKey`. This is also the same underlying wiring
  `QueuePositionIndicator`'s missing polling (see `01_frontend_state.md`) could piggyback on, if the two
  components are ever composed on the same staff-facing session view.
- **STATUS: FIXED, 2026-09-07.** Added `session-delay/SessionOperationsPanel.tsx`, composing
  `DelayIndicator` + `CompleteSlotButton` exactly as designed (a `refreshKey` state bumped by
  `onCompleted`), wired at `/staff/clinics/:clinicId/sessions/:sessionId/operations` in the new app
  shell. `QueuePositionIndicator`'s missing polling was instead fixed independently with a
  `setInterval` (see `01_frontend_state.md`), since it's keyed by Booking, not Session — the two
  never needed to share this wiring after all.

**[HIGH] - [APPOINTMENT_TYPE_CONFIG] - [MISSING_UI]** *(full detail in `02_api_layer.md`)*
Not an orphaned component (none exists to be orphaned) — a genuinely missing screen. Listed here because
its absence is *why* three separate booking forms fall back to raw UUID text inputs, a composition-level
consequence worth reading alongside this file's other entries. **STATUS: PARTIALLY FIXED, 2026-09-07**
— see `02_api_layer.md`.

**[HIGH] - [WAITLIST_JOIN staff] - [MISSING_STAFF_UI]** *(full detail in `02_api_layer.md`)*
Same shape as the appointment-type gap: a real, tested backend capability (staff joining a patient to a
waitlist on their behalf) with zero frontend surface of any kind. **STATUS: FIXED, 2026-09-07** — see
`02_api_layer.md`.

---

## Summary of reachability status by feature

**STATUS as of 2026-09-07: table below is superseded.** A router (`react-router-dom`) and a real
app shell now exist (`frontend/src/routes/`, wired in `App.tsx`) — all 23 feature folders' top-level
components are mounted behind a route and reachable by a real user. Every row below whose gap was
"no router, no shell" is now reachable; the rows with a genuinely missing component now have one.
Table left as-is (not rewritten) as the historical record of what this scan found; see each finding's
own `STATUS:` line above for the specific fix.

| Feature | Backend complete? | Frontend component exists? | Actually reachable today? (as scanned) |
|---|---|---|---|
| Clinic registration | Yes | Yes | Yes — the only one wired into `App.tsx` |
| All other 22 feature folders | Yes | Yes (component built + tested) | **No — no router, no shell** |
| Schedule edit (`PATCH .../schedules/{id}`) | Yes | **No component exists at all** | No, and adding a router wouldn't help |
| Staff join-on-behalf-of-patient waitlist | Yes | **No component exists at all** | No, and adding a router wouldn't help |
| Appointment-type / default-fee config | Yes | **No component exists at all** | No, and adding a router wouldn't help |
| Patient claim waitlist offer | Yes | Yes, but needs an id no endpoint provides | No, even with a router and a new endpoint's UI, until that endpoint exists |
| `DelayIndicator` refresh-on-completion | Yes | Yes, both halves exist independently | Not exercised — the two components have never been composed |
