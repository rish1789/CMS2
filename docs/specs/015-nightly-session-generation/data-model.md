# Data Model: Nightly Rolling Session Generation (15-Day Horizon)

## `Session` (new entity)

| Field | Type | Notes |
|---|---|---|
| `id` | UUID, generated | |
| `schedule` | `Schedule`, `@ManyToOne`, required | Lineage reference only — none of the fields below are re-derived from it at read time |
| `clinic` | `Clinic`, `@ManyToOne`, required | Denormalized copy of `schedule.getClinic()` at generation time |
| `doctorProfile` | `DoctorProfile`, `@ManyToOne`, required | Denormalized copy of `schedule.getDoctorProfile()` at generation time |
| `sessionDate` | `LocalDate`, required | The calendar date this Session occurs on |
| `mode` | `ScheduleMode`, required | Snapshot of `schedule.getMode()` at generation time |
| `startTime` | `LocalTime`, required | Snapshot |
| `endTime` | `LocalTime`, required | Snapshot |
| `slotIntervalMinutes` | `Integer`, nullable | Snapshot; present iff `mode = FIXED_TIME` (FR-003/FR-004) |
| `createdAt` | `Instant`, defaulted `now()` | |

**DB constraint**: `UNIQUE (schedule_id, session_date)` — the data-layer guarantee behind FR-002/SC-002 (research.md).

## Service flow

### `SessionGenerationService.generate(LocalDate runDate) -> int` (not itself `@Transactional`)

1. For each `Schedule` in `scheduleRepository.findAll()`: call `generateForSchedule(schedule, runDate)`, summing the returned created-count. A failure generating for one Schedule (research.md's rare-race case) does not prevent attempting the remaining Schedules.

### `generateForSchedule(Schedule schedule, LocalDate runDate) -> int` (`@Transactional`, own transaction per call)

1. Compute the 15 candidate dates: `runDate` through `runDate.plusDays(14)`.
2. Filter to dates whose `DayOfWeek` is in `schedule.getDaysOfWeek()`.
3. Query `sessionRepository.findBySchedule_IdAndSessionDateIn(schedule.getId(), candidateDates)` to find which of those dates already have a Session (research.md's pre-check).
4. For each remaining (not-yet-generated) date, build and save a `Session` snapshotting `schedule`'s current `mode`/`startTime`/`endTime`/`slotIntervalMinutes`/`clinic`/`doctorProfile`.
5. Return the count created. A unique-constraint violation here (the rare true-race case) propagates as an exception, failing only this Schedule's transaction (research.md).

### `SessionGenerationController.trigger()` (`POST /api/v1/admin/sessions/generate`, Super Admin only)

Calls `sessionGenerationService.generate(LocalDate.now())`, returns `{ runDate, sessionsCreated }`.

### `NightlySessionGenerationTrigger` (`@Scheduled(cron = "0 0 2 * * *")`)

Calls the same `sessionGenerationService.generate(LocalDate.now())` — identical logic, different trigger (FR-006/FR-007's "same generation logic" requirement).

## Request/Response contract

See `contracts/session-generation.md`.
