# 011 — Nightly Rolling Session Generation (15-Day Horizon)

**Module:** Scheduling & Session Generation
**Status:** Ready for spec-kit intake

## User Story
As the System (Scheduler), I want to automatically generate Sessions from each active Recurring Schedule on a nightly job, so that a 15-day rolling horizon of bookable Sessions always exists without any manual staff intervention.

## Context
Recurring Schedules describe a repeating pattern (e.g. "Dr. X, Mondays, 9am-1pm, fixed-time"), but patients and staff book against concrete Sessions on specific calendar dates, not against the abstract pattern. A background job materializes that pattern into real Sessions, always keeping a fixed 15-day window populated. BDD §2 (In-Scope), §3.2.

## Business Rules
- The job runs nightly and extends the generated-Session horizon to 15 days out from the run date.
- Only Super Admin may manually re-trigger this background job (BDD §2 User Personas — System (Scheduler) / Super Admin note).
- Generation reads active Recurring Schedules and produces one Session per applicable calendar date within the horizon.
- Each generated Session is created as either Fixed-Time or Queue/Token mode, per its parent Recurring Schedule's configured mode (see 009-recurring-schedule-definition).
- Generation must not duplicate a Session for a date/schedule pair that was already generated on a prior run.
- Generated Sessions are immune to later edits of the Recurring Schedule (see 014-schedule-edit-non-retroactivity) — once a Session exists, it is a standalone record.

## Acceptance Criteria
- Given an active Recurring Schedule with no Sessions yet generated, when the nightly job runs, then Sessions are created for every applicable date within the next 15 days.
- Given a Recurring Schedule that already has Sessions generated through day 10 of the horizon, when the nightly job runs on day 1, then only the newly-in-range dates (11-15) are generated, with no duplicates for days 1-10.
- Given a Fixed-Time Recurring Schedule, when its Session is generated, then all of that Session's Slots are pre-generated in the same step (see 012-fixed-time-slot-pregeneration).
- Given a Queue/Token Recurring Schedule, when its Session is generated, then no Slots are pre-created — Slots are created later, one at a time, on booking (see 013-queue-mode-slot-on-demand-generation).
- Given a Super Admin, when they manually re-trigger the job, then it runs the same generation logic on demand.
- Given a non-Super-Admin user, when they attempt to manually trigger the job, then the action is rejected.

## Dependencies
- Depends on: 009-recurring-schedule-definition — Sessions are generated from active Recurring Schedules.
- Blocks / feeds into: 012-fixed-time-slot-pregeneration — Fixed-time Slot creation happens as part of this job.
- Blocks / feeds into: 013-queue-mode-slot-on-demand-generation — defines the deferred Slot-creation path for queue-mode Sessions this job creates.
- Blocks / feeds into: 016-staff-assisted-fixed-time-booking, 017-patient-self-service-fixed-time-booking, 018-queue-token-booking — booking requires Sessions/Slots to already exist.

## Explicitly Out of Scope
- Session-level live status (IN_PROGRESS/COMPLETED) — v1 tracks status at the Slot level only; this job does not set or manage any session-level state machine.
- Any UI for staff to manually create individual Sessions outside the recurring-schedule + nightly-job mechanism.

## Source References
- BDD §2 (In-Scope: "Recurring Schedules → nightly rolling Session generation (15-day horizon)")
- BDD §2 (User Personas: Super Admin re-trigger privilege; System (Scheduler) persona)
- BDD §3.2 (Fixed-time vs queue-mode slot generation)
