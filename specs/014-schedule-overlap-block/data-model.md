# Data Model: Multi-Clinic Doctor Schedule Overlap Block

No new entity, no new migration. This feature adds one query and one check step over the existing `Schedule` entity (009).

## `ScheduleRepository.findByDoctorProfile_Id(UUID doctorProfileId) -> List<Schedule>` (new)

Every Schedule belonging to the doctor, across every clinic — not scoped by clinic, unlike 009's existing `findByClinic_IdAndDoctorProfile_Id`.

## `ScheduleService.create()` (extended — one new step, after the existing staffing gate, before save)

```text
for each existing in findByDoctorProfile_Id(doctorProfileId):
    sharedDay = !Collections.disjoint(existing.daysOfWeek, request.daysOfWeek)
    timeOverlap = request.startTime < existing.endTime AND existing.startTime < request.endTime
    if sharedDay AND timeOverlap:
        throw ScheduleOverlapException
```

Runs against every existing Schedule regardless of which clinic it's at (FR-001). A doctor's very first Schedule (empty list) trivially passes (FR-004/SC-004, spec AC6).

## `ScheduleOverlapException` (new)

Carries the conflicting existing Schedule's id (for a useful error message) and the doctor/new-request context. Maps to `409 Conflict`, `SCHEDULE_OVERLAP`.

## Request/Response contract

See `contracts/schedule-overlap.md` — no new endpoint; extends 009's existing `POST .../schedules` contract with one new possible error response.
