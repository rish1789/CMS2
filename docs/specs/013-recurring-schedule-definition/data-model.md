# Data Model: Recurring Schedule Definition

## `ScheduleMode` (new enum)

`FIXED_TIME`, `QUEUE`.

## `Schedule` (new entity)

| Field | Type | Notes |
|---|---|---|
| `id` | UUID, generated | |
| `doctorProfile` | `DoctorProfile`, `@ManyToOne`, required | |
| `clinic` | `Clinic`, `@ManyToOne`, required | |
| `daysOfWeek` | `Set<DayOfWeek>`, `@ElementCollection` | Non-empty (FR-006); a repeated day in the request is de-duplicated by `Set` semantics before persistence |
| `startTime` | `LocalTime`, required | Strictly before `endTime` (FR-005) |
| `endTime` | `LocalTime`, required | |
| `mode` | `ScheduleMode`, required | |
| `slotIntervalMinutes` | `Integer`, nullable | Required & positive iff `mode = FIXED_TIME`; must be absent iff `mode = QUEUE` (FR-007/FR-008) |
| `createdAt` | `Instant`, defaulted `now()` | |

No relationship back from `Clinic`/`DoctorProfile` to `Schedule` — one-directional FK, matching this codebase's existing pattern (e.g. `NotificationEvent.patientAccount`) of not adding a bidirectional collection nothing yet needs.

## Service flow (`ScheduleService`, all methods `@Transactional`)

### `create(UUID callerAccountId, UUID clinicId, UUID doctorProfileId, CreateScheduleRequest request) -> Schedule`

1. Load `Clinic` by id, or throw `ClinicNotFoundException`.
2. Load `DoctorProfile` by id, or throw `DoctorProfileNotFoundException`.
3. **Authorization** (FR-001–FR-003): allow iff the caller is an active ClinicAdmin at `clinicId`, **or** the caller's account equals `doctorProfile.getAccount().getId()` **and** the caller has an active Doctor-role assignment at `clinicId`. Otherwise throw `ForbiddenException`.
4. **Request validation** (FR-005–FR-008), collected in one pass and surfaced as `InvalidScheduleException`:
   - `daysOfWeek` non-empty.
   - `startTime` strictly before `endTime`.
   - `mode = FIXED_TIME` ⇒ `slotIntervalMinutes` present and `> 0`.
   - `mode = QUEUE` ⇒ `slotIntervalMinutes` absent.
5. **Staffing gate** (FR-009): the named doctor must have an active Doctor-role assignment at `clinicId` (already computed in step 3 for the doctor-self path; re-checked/checked fresh for the ClinicAdmin path) — otherwise throw `DoctorNotStaffedAtClinicException`.
6. Build and save the `Schedule`. No Session/Slot table is touched anywhere in this method (FR-011).

### `list(UUID callerAccountId, UUID clinicId, UUID doctorProfileId) -> List<Schedule>`

Same authorization rule as step 3 above (FR-010), then `scheduleRepository.findByClinic_IdAndDoctorProfile_Id(clinicId, doctorProfileId)`.

## Request/Response contract

See `contracts/schedule.md` — `POST`/`GET /api/v1/clinics/{clinicId}/doctors/{doctorProfileId}/schedules`, both requiring a valid staff bearer token, both under the existing `/api/v1/clinics/**` chain.
