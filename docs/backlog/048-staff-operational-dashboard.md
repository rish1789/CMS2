# 048 — Staff Operational Dashboard Enhancement

**Module:** Frontend / Staff Experience
**Status:** Ready for spec-kit intake

## User Story
As clinic staff (ClinicAdmin, Doctor, or Operations) starting my day in CMS2, I want a dashboard that immediately answers "what's happening in my clinic today," using only real data the system already has, so that I don't need to click into five different tools to understand today's workload.

## Context
Verified current state (2026-09-15): `ClinicToolsDashboard.tsx` already fetches and displays real, live data — an inbox unclaimed-count badge, a day-sheet "N sessions today" badge, and a waitlist "N waiting" badge — each independently fetched per tile with pulse-skeleton loading and silent-fail-per-tile behavior, wrapped in a 2-column tile grid (Day sheet, Doctors, Staff, Find a patient, Onboard staff, Join waitlist). This is a reasonable foundation, not a blank slate — this feature enhances it, it does not replace it wholesale.

Per the constitution and this project's own "do not fake functionality" standard (and the user's explicit instruction this session), **every metric shown MUST come from a real backend query.** If a genuinely useful metric doesn't have a backend endpoint yet (e.g. "today's completed consultations count"), the correct move is to add that specific, minimal query — not to fabricate a number or hide the fact that it's not backed by real data.

## Business Rules
- Existing real-data tiles (inbox count, day-sheet session count, waitlist count) MUST be preserved and can be visually upgraded (using 046's component library) but not replaced with fabricated equivalents.
- Any new metric proposed (e.g. "today's completed consultations," "today's no-shows," "walk-ins today") MUST be traced to data that already exists in the schema (Session/Slot/Booking status fields, all of which exist per 011/012/013/020/021/023) — if a genuinely new backend query is needed, this feature's plan MUST specify exactly which repository method is added and why, not hand-wave "compute this somehow."
- A "Today's sessions" or "Today's appointments" list (patient, doctor, time, status, quick action) is explicitly requested by the user and maps directly to existing Session/Slot/Booking data already surfaced by `DaySheet.tsx` — this feature should surface a condensed version of that same real data on the dashboard itself, not duplicate DaySheet's full functionality.
- No "Recent Patients" or "Recent Activity" widget may be added unless a real, existing data source supports it — check whether Patient's `createdAt` (009/019) and any existing activity/audit trail (there may be none — verify during planning) can genuinely back these before committing to them in the plan.
- Quick actions shown MUST link to routes that already exist (per 047's verified route inventory) — e.g. "Onboard staff," "Find a patient," "Join waitlist" already have real destinations; do not add a quick action for a page that doesn't exist.
- This feature applies to the Staff/ClinicAdmin/Operations dashboard specifically — the Super Admin dashboard (`AdminDashboard.tsx`) and Patient dashboard (`PatientDashboard.tsx`) are out of scope for this particular feature (their own polish, if wanted, would be separate future features).

## Acceptance Criteria
- Given the enhanced staff dashboard, when a staff member loads it, then every number/metric displayed traces to a real, verifiable backend query — no hardcoded or placeholder values anywhere.
- Given a clinic with real sessions scheduled today, when the dashboard loads, then a "Today's sessions/appointments" section shows real patient/doctor/time/status data drawn from existing endpoints, with an obvious action per row (e.g. link into Day Sheet for that session).
- Given the existing inbox/day-sheet/waitlist count tiles, when this feature ships, then they are visually upgraded (via 046's components) but functionally unchanged or improved, not regressed or removed.
- Given a metric that would require fabricated data because no backend support exists, when found during planning, then the plan explicitly states it will not be built (or names the specific minimal backend addition required) — the final implementation must contain zero fake statistics.
- Given the full test suite (frontend, and backend if any new query was added), when run after this feature, then all tests pass, including new tests for any new backend query and new dashboard component tests.

## Dependencies
- Depends on 046 (shared UI component library).
- Benefits from 047 (sidebar navigation) landing first so dashboard "quick actions" and sidebar navigation are coherent, not duplicative — but can be sequenced either way if needed.
- Reads from existing converged features: 011/012/013 (sessions/slots), 016/017/018 (bookings), 020 (walk-ins), 021 (no-shows), 023 (session delay/completion), 038 (inbox).

## Explicitly Out of Scope
- Revenue, billing, or payment-related metrics of any kind — no payment data model exists in CMS2 (explicitly out of scope system-wide, per `backlog/README.md`).
- Charts/trend graphs (e.g. "appointment trends over time") — CMS2 has no historical-analytics query layer today; if genuinely wanted, that's a separate, explicitly-scoped future feature requiring new aggregation queries, not something to bolt onto this one.
- The Super Admin and Patient dashboards — separate scope, not touched by this feature.

## Source References
- Verified against current repository state via direct inspection, 2026-09-15 (`ClinicToolsDashboard.tsx` confirmed to already show 3 real live-count tiles; no chart/trend infrastructure exists anywhere in the backend)
- Constitution Principle II (Simplicity/YAGNI) and this session's explicit "do not fake functionality" instruction
