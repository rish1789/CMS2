# Quickstart: Nightly Rolling Session Generation (15-Day Horizon)

See [data-model.md](./data-model.md) and [contracts/session-generation.md](./contracts/session-generation.md).

## Prerequisites

- Backend running with PostgreSQL and Flyway migrations applied (through this feature's own `V8`).
- A Recurring Schedule (013/009) already created for a doctor at a clinic.
- Super Admin credentials configured (`SUPER_ADMIN_USERNAME`/`SUPER_ADMIN_PASSWORD`).

## Scenario 1 — First generation populates the full 15-day horizon

1. `POST /api/v1/admin/sessions/generate` with valid Super Admin credentials. **Expect**: `200`, `sessionsCreated` equal to the number of the schedule's applicable dates in the next 15 days.
2. Query the Sessions for that schedule directly (data layer). **Expect**: exactly one Session per applicable date, each with the schedule's current mode/time-range/slot-interval.

## Scenario 2 — Repeated generation is idempotent

1. Immediately call `POST /api/v1/admin/sessions/generate` again. **Expect**: `200`, `sessionsCreated: 0` — no duplicates.

## Scenario 3 — Fixed-Time vs Queue/Token snapshotting

1. Generate for a Fixed-Time schedule. **Expect**: each Session's `slotIntervalMinutes` matches the schedule's value.
2. Generate for a Queue/Token schedule. **Expect**: each Session's `slotIntervalMinutes` is absent/null.

## Scenario 4 — Independent per-schedule generation

1. Create two Schedules for two different doctors. Generate. **Expect**: each doctor's Sessions are generated independently; one schedule's Sessions never reference or depend on the other's.

## Scenario 5 — Manual trigger requires Super Admin

1. `POST /api/v1/admin/sessions/generate` with no credentials, or with a valid *staff* (ClinicAdmin/Doctor) bearer token instead of Super Admin Basic Auth. **Expect**: `401`.

## Scenario 6 — Nightly cron fires the same logic

1. Verify (by code inspection / a directly-invoked test of `NightlySessionGenerationTrigger`) that the scheduled trigger calls the identical `SessionGenerationService.generate(LocalDate.now())` the manual endpoint calls.

## Scenario 7 — Frontend trigger

1. As Super Admin in the UI, open the session-generation trigger (`frontend/src/features/session-generation/TriggerSessionGeneration.tsx`) and click generate. **Expect**: success message showing the count created.
