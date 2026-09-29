# Quickstart: Recurring Schedule Definition

See [data-model.md](./data-model.md) and [contracts/schedule.md](./contracts/schedule.md).

## Prerequisites

- Backend running with PostgreSQL and Flyway migrations applied (through this feature's own `V7`).
- A clinic (001) verified (002), a Doctor onboarded (004/005/007) with an active Role Assignment at that clinic, and a ClinicAdmin's staff bearer token (login via 003/004) and/or the Doctor's own staff bearer token (003).

## Scenario 1 — ClinicAdmin creates a Fixed-Time schedule

1. `POST /api/v1/clinics/{clinicId}/doctors/{doctorProfileId}/schedules` with the ClinicAdmin's bearer token: `{"daysOfWeek":["MONDAY","WEDNESDAY","FRIDAY"],"startTime":"09:00","endTime":"13:00","mode":"FIXED_TIME","slotIntervalMinutes":15}`. **Expect**: `201`, response echoes the submitted pattern.
2. `GET` the same path. **Expect**: `200`, array containing the created schedule.

## Scenario 2 — ClinicAdmin creates a Queue/Token schedule

1. `POST` with `{"daysOfWeek":["TUESDAY","THURSDAY"],"startTime":"14:00","endTime":"17:00","mode":"QUEUE"}` (no `slotIntervalMinutes`). **Expect**: `201`, `slotIntervalMinutes: null`.

## Scenario 3 — Validation rejections

1. `POST` with `"mode":"QUEUE"` and `"slotIntervalMinutes":15` present. **Expect**: `400 INVALID_SCHEDULE`.
2. `POST` with `"mode":"FIXED_TIME"` and no `slotIntervalMinutes`. **Expect**: `400 INVALID_SCHEDULE`.
3. `POST` with `"startTime":"13:00","endTime":"09:00"`. **Expect**: `400 INVALID_SCHEDULE`.
4. `POST` with `"daysOfWeek":[]`. **Expect**: `400 INVALID_SCHEDULE`.

## Scenario 4 — Doctor defines their own schedule; cannot define another's

1. Log in as the Doctor (003). `POST` a schedule for their own `doctorProfileId` at a clinic they're actively assigned to, using their own token. **Expect**: `201`.
2. `POST` a schedule naming a *different* doctor's `doctorProfileId`, using the same Doctor's token. **Expect**: `403 FORBIDDEN`.

## Scenario 5 — Doctor not staffed at the clinic is rejected

1. `POST` naming a `doctorProfileId` with no active Role Assignment at `clinicId` (never assigned, or deactivated per 005), using a valid ClinicAdmin token for that clinic. **Expect**: `409 DOCTOR_NOT_STAFFED_AT_CLINIC`.

## Scenario 6 — Unrelated staff member forbidden

1. `POST`/`GET` using a valid staff bearer token belonging to neither this clinic's ClinicAdmin nor the named doctor (e.g. a ClinicAdmin of a *different* clinic). **Expect**: `403 FORBIDDEN`.

## Scenario 7 — Frontend form

1. Log in as a ClinicAdmin in the UI, open the Schedule form (`frontend/src/features/scheduling/ScheduleForm.tsx`), submit a Fixed-Time pattern. **Expect**: success confirmation, no page requiring re-login.
