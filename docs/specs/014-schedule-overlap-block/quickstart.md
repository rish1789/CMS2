# Quickstart: Multi-Clinic Doctor Schedule Overlap Block

See [data-model.md](./data-model.md) and [contracts/schedule-overlap.md](./contracts/schedule-overlap.md). Extends 013's existing `POST .../schedules` flow — same prerequisites.

## Scenario 1 — Cross-clinic overlap rejected

1. Create Schedule Mon 9–11am for Doctor D at Clinic A. **Expect**: `201`.
2. Create Schedule Mon 10am–12pm for Doctor D at Clinic B. **Expect**: `409 SCHEDULE_OVERLAP`.

## Scenario 2 — Touching ranges allowed

1. With Scenario 1 step 1's Schedule still in place, create Schedule Mon 11am–1pm for Doctor D at Clinic B. **Expect**: `201`.

## Scenario 3 — Same-clinic overlap rejected

1. Create Schedule Mon 9–11am for Doctor D at Clinic A. **Expect**: `201`.
2. Create Schedule Mon 10–11:30am for Doctor D, also at Clinic A. **Expect**: `409 SCHEDULE_OVERLAP`.

## Scenario 4 — Disjoint days allowed regardless of time

1. Create Schedule Mon 9–11am for Doctor D at Clinic A. **Expect**: `201`.
2. Create Schedule Tue 9–11am for Doctor D at Clinic B (same time, different day). **Expect**: `201`.

## Scenario 5 — First schedule never rejected

1. A doctor with no existing Schedules anywhere submits their first Schedule. **Expect**: `201`.
