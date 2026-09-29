# 047 — Application Shell Sidebar Navigation

**Module:** Frontend / Application Shell
**Status:** Ready for spec-kit intake

## User Story
As a clinic staff member or Super Admin using CMS2 throughout the day, I want persistent, always-visible navigation to the areas I use repeatedly (day sheet, doctors, staff, patient search, inbox), so that I don't have to return to a tile-grid dashboard every time I want to switch tasks.

## Context
Verified current state (2026-09-15): `StaffShell.tsx`, `AdminShell.tsx`, and `PatientShell.tsx` are structurally identical — a single fixed top header (logo mark, avatar-initial circle, sign-out button) wrapping `<main className="mx-auto max-w-5xl p-6 sm:p-8"><Outlet/></main>`. **There is no sidebar anywhere in the application.** All navigation happens through tile grids inside each dashboard page (`ClinicToolsDashboard.tsx`'s 2-column tile grid of Day sheet/Doctors/Staff/Find a patient/Onboard staff/Join waitlist; `AdminDashboard.tsx`'s similar tile grid). This means every navigation between tasks round-trips through the dashboard — there's no way to jump from, say, the Day Sheet directly to Patient Search without going back to the tile grid first.

Per `PRODUCT.md`'s Design Principle 4 ("One system, three audiences... patient = reassurance, staff = speed/density, admin = utility"), this feature should NOT apply one identical sidebar to all three shells uniformly — staff/admin genuinely benefit from persistent navigation (they're task-switching all day per `PRODUCT.md`'s own user description: "Working fast, often mid-task... time-pressured"), while the patient shell's calmer, tile-based, reassurance-first pattern may be worth preserving as-is. This fork should be resolved explicitly during `/speckit-clarify`, not assumed.

## Business Rules
- The new sidebar (for whichever shell(s) the clarify step confirms need it) MUST use the shared component library from 046, not bespoke markup.
- Navigation items shown MUST reflect only routes that actually exist today (per the verified route list below) — no placeholder links to non-existent pages (e.g. no "Billing"/"Reports" sidebar entries, since those pages don't exist and aren't in scope).
- Verified current StaffShell/ClinicShell routes to surface as nav items (a subset, chosen for frequency of use, not necessarily all 22): Day sheet, Doctors, Staff, Find a patient, Onboard staff, Inbox, Join waitlist, Clinic tools home.
- Verified current AdminShell routes: Pending clinic verifications, Pending doctor verifications, Trigger session generation, Admin home.
- The sidebar MUST support an active/current-route highlighted state, and MUST collapse to an icon-only rail or an off-canvas drawer (not simply disappear) at mobile/tablet widths — full responsive behavior is finalized in 052, but this feature must ship a reasonable first-pass responsive behavior, not defer all of it.
- Role-based visibility: sidebar items must reflect what the signed-in staff member's role can actually do — reuse the existing role-assignment/authorization data already available to the frontend (do not introduce a new authorization mechanism; the backend already enforces this per-endpoint regardless of what the sidebar shows).
- This feature changes shell chrome only — it must not change what any existing page renders inside `<Outlet/>`, and it must not touch backend authorization logic (the constitution requires backend enforcement regardless of frontend UI — Principle IV / general security posture).

## Acceptance Criteria
- Given a signed-in staff member viewing any page within `ClinicShell`, when the page renders, then a persistent sidebar (or the clarify-confirmed equivalent for that shell) is visible with the current page highlighted.
- Given the sidebar, when a staff member clicks a different nav item, then they navigate directly to that page without returning to the dashboard tile grid first.
- Given a narrow viewport (~400px, per this project's responsive standard), when the sidebar is present, then it collapses to an accessible alternative (icon rail, hamburger-triggered drawer, or bottom nav) rather than breaking layout or disappearing with no way to navigate.
- Given the existing `ClinicToolsDashboard`/`AdminDashboard` tile grids, when this feature ships, then they either remain as a secondary "quick actions" surface on the dashboard itself (not removed outright) or are deliberately simplified — the plan must state which, since removing all navigation redundancy on day one could regress a currently-working flow.
- Given the full frontend test suite, when run after this feature, then all existing tests pass, plus new tests for the sidebar's navigation and responsive behavior.

## Dependencies
- Depends on 046 (shared UI component library) for its building blocks.
- Should precede or run alongside 048 (staff dashboard) and 049 (patient hub), since both will likely be reachable via this new sidebar.

## Explicitly Out of Scope
- Any sidebar navigation entry to Billing, Lab Tests, or Reports — none of these exist in CMS2 and are explicitly out of scope for this transformation (per the user's own decision this session).
- Global search in the sidebar/top bar — not part of this feature; evaluate separately if requested later, since no cross-entity search endpoint currently exists on the backend.
- Changing the PatientShell's navigation pattern, unless `/speckit-clarify` explicitly decides it should also get a sidebar.

## Source References
- `PRODUCT.md` Design Principle 4 (one system, three audiences, differentiated emphasis per audience)
- Verified against current repository state via direct inspection, 2026-09-15 (confirmed all three shells are header-only with no sidebar; confirmed 22 routes under ClinicShell, 4 top-level under AdminShell)
