# Quickstart: Schedule Edit Non-Retroactivity

See [data-model.md](./data-model.md) and [contracts/schedule-edit.md](./contracts/schedule-edit.md).

## Prerequisites

- A Schedule created (013) for a doctor at a clinic, with Sessions already generated from it (015/011).

## Scenario 1 — Editing a schedule never touches already-generated Sessions

1. Note the generated Sessions' fields (time window, mode, slot-interval) for the Schedule.
2. `PATCH .../schedules/{id}` changing the time window. **Expect**: `200`.
3. Re-inspect the same Sessions at the data layer. **Expect**: every field unchanged from step 1.
4. Run generation (015) again for a new date. **Expect**: the newly-generated Session uses the *edited* time window.

## Scenario 2 — Rejected edit leaves the schedule unchanged

1. `PATCH` with an invalid combination (e.g. `QUEUE` mode with a `slotIntervalMinutes`). **Expect**: `400 INVALID_SCHEDULE`.
2. Re-fetch the Schedule. **Expect**: unchanged from before the attempted edit.

## Scenario 3 — Edit re-runs the overlap check, excluding itself

1. Create two non-overlapping Schedules for the same doctor.
2. `PATCH` the first one's time window to now overlap the second. **Expect**: `409 SCHEDULE_OVERLAP`.
3. `PATCH` the first one changing only its own time window, with no overlap against the second. **Expect**: `200` — never rejected for "overlapping itself."

## Scenario 4 — Authorization matches 009's create rule

1. `PATCH` as an unrelated staff member (neither this clinic's ClinicAdmin nor the schedule's own doctor). **Expect**: `403 FORBIDDEN`.
2. `PATCH` as the schedule's own doctor. **Expect**: `200`.
