# 009 — Recurring Schedule Definition

**Module:** Scheduling & Session Generation
**Status:** Ready for spec-kit intake

## User Story
As a ClinicAdmin (or the Doctor themselves), I want to define a doctor's recurring weekly schedule at a clinic — which days, which time ranges, and whether it runs in Fixed-Time or Queue/Token mode — so that the system can automatically generate bookable sessions from it going forward.

## Context
BDD §2 describes the pipeline: "Recurring Schedules → nightly rolling Session generation (15-day horizon) → Fixed-Time or Queue/Token slots." A Schedule is the recurring pattern; it does not itself create bookable sessions — that happens in a separate nightly job (011-nightly-rolling-session-generation). Editing a Schedule later must never retroactively affect Sessions already generated from it (014-schedule-edit-non-retroactivity).

## Business Rules
- A Schedule defines a recurring day-of-week + time-range pattern for one specific Doctor at one specific Clinic.
- Each Schedule specifies a mode: **Fixed-Time** (individual slots at specific start times, e.g. 15-minute intervals) or **Queue/Token** (patients receive sequential tokens with no fixed slot start times).
- A Schedule is the input pattern consumed by nightly rolling Session generation (011) — creating a Schedule does not itself create any bookable Session or Slot.
- The same doctor may have multiple Schedules — across different clinics, or different day/time patterns at the same clinic — subject to the overlap block in 010-multi-clinic-doctor-schedule-overlap-block.
- Editing an existing Schedule (time range, mode, or otherwise) never retroactively touches Sessions/Slots already generated from a prior version of it (014) — only future nightly generation runs pick up the new pattern.

## Acceptance Criteria
- Given a Doctor and Clinic, when a ClinicAdmin defines a Fixed-Time Schedule (e.g., Mon/Wed/Fri 9am–1pm, 15-minute slots), then the Schedule is saved and becomes the source pattern for future nightly Session generation.
- Given a Doctor and Clinic, when a ClinicAdmin defines a Queue/Token Schedule (e.g., Tue/Thu 2pm–5pm), then the Schedule is saved with mode = Queue and no fixed slot-interval configuration, since tokens are sequential rather than time-sliced.
- Given a new Schedule that would overlap an existing Schedule for the same doctor in day/time (same clinic or a different clinic), when submitted, then it is rejected per 010's overlap rule.
- Given an existing Schedule with already-generated future Sessions, when the Schedule is edited, then none of the already-generated Sessions/Slots change as a result — only subsequent nightly generation runs use the new pattern (see 014).

## Dependencies
- Depends on: 005-doctor-profile-auto-creation-license-queue — the doctor must exist.
- Depends on: 001-clinic-registration — the clinic must exist.
- Feeds: 010-multi-clinic-doctor-schedule-overlap-block, 011-nightly-rolling-session-generation, 014-schedule-edit-non-retroactivity.

## Explicitly Out of Scope
- One-off / non-recurring schedule exceptions or single-day overrides — the source doc only describes recurring patterns feeding rolling generation; no ad-hoc single-day schedule mechanism is described.

## Source References
- BDD §2 (Recurring Schedules → rolling Session generation pipeline)
- BDD §3.2 (Schedule/Session/Slot behavior)
