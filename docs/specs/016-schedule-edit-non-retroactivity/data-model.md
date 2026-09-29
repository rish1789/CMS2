# Data Model: Schedule Edit Non-Retroactivity

## `Schedule` (extends 009's existing entity — no schema change)

New setters (mirroring `PatientAccount`/`DoctorProfile`'s existing edit-mutator precedent): `setDaysOfWeek`, `setStartTime`, `setEndTime`, `setMode`, `setSlotIntervalMinutes`. No new field, no new column — `createdAt` has no setter and is never touched by edit (spec Edge Cases).

## Service flow

### `ScheduleService.edit(UUID callerAccountId, UUID clinicId, UUID doctorProfileId, UUID scheduleId, CreateScheduleRequest request) -> Schedule`

1. Load `Clinic` by `clinicId`, or `ClinicNotFoundException`.
2. Load `DoctorProfile` by `doctorProfileId`, or `DoctorProfileNotFoundException`.
3. Load `Schedule` by `scheduleId`; if not found, or its `clinic.id`/`doctorProfile.id` don't match the path's `clinicId`/`doctorProfileId`, throw `ScheduleNotFoundException` (research.md).
4. `requireAuthorized(callerAccountId, clinicId, doctorProfile)` — identical to `create()` (FR-002).
5. `validate(request)` — identical to `create()` (FR-003). No field is mutated on the loaded `Schedule` yet.
6. `requireNoOverlap(doctorProfileId, request, /* excludeScheduleId */ scheduleId)` — 010's check, now excluding the Schedule being edited from its own comparison set (FR-004).
7. Only now, apply all five submitted fields to the loaded `Schedule` via its setters, and save. Never at any point does this method (or anything it calls) reference `SessionRepository` or `Session` (FR-006).

### `requireNoOverlap` (extended — 010, now takes an optional exclude-id)

```text
for each existing in findByDoctorProfile_Id(doctorProfileId):
    if excludeScheduleId != null AND existing.id == excludeScheduleId:
        continue   // skip comparing the schedule against its own pre-edit state
    ... same day/time-overlap predicate as before ...
```

`create()` calls this with `excludeScheduleId = null` (unchanged behavior); `edit()` passes the Schedule's own id.

## Request/Response contract

See `contracts/schedule-edit.md` — `PATCH /api/v1/clinics/{clinicId}/doctors/{doctorProfileId}/schedules/{scheduleId}`, same request/response shape as 009's `POST`.
