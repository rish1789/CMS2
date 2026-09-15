# Data Model: Fee Resolution & Locking at Booking Time

## `AppointmentType` (new entity)

| Field | Type | Notes |
|---|---|---|
| `id` | UUID, generated | |
| `doctorProfile` | `DoctorProfile`, `@ManyToOne`, required | |
| `name` | String, required | |
| `feeOverride` | `BigDecimal(10,2)`, nullable | Present iff this type has its own fee (FR-001) |
| `createdAt` | `Instant`, defaulted `now()` | |

## `DoctorDefaultFee` (new entity)

| Field | Type | Notes |
|---|---|---|
| `id` | UUID, generated | |
| `doctorProfile` | `DoctorProfile`, `@OneToOne`, required, unique | At most one per Doctor (spec Assumptions) |
| `amount` | `BigDecimal(10,2)`, required | |
| `updatedAt` | `Instant`, updated on every set | |

## `RoleAssignmentRepository` (extended — identity module)

`findByAccount_IdAndRoleAndActiveTrue(UUID accountId, RoleAssignment.Role role) -> List<RoleAssignment>` — every active clinic assignment an account holds for a given role; used here to find every clinic a target Doctor is actively staffed at.

## Service flow

### `FeeResolutionService.resolve(UUID doctorProfileId, UUID appointmentTypeId) -> BigDecimal`

1. Load `AppointmentType` by `appointmentTypeId`; if not found, or its `doctorProfile.id != doctorProfileId`, throw `AppointmentTypeNotFoundException` (FR-004).
2. If `feeOverride != null`, return it (FR-001).
3. Else look up `DoctorDefaultFeeRepository.findByDoctorProfile_Id(doctorProfileId)`; if present, return its `amount` (FR-002).
4. Else throw `NoFeeConfiguredException` (FR-003) — no fee of any kind is ever returned in this branch.

### `AppointmentTypeService` (`create`, `list`, `setDefaultFee` — all `@Transactional`)

1. Load `DoctorProfile` by id, or `DoctorProfileNotFoundException`.
2. `requireAuthorized(callerAccountId, doctorProfile)`:
   - Allow if `doctorProfile.getAccount().getId().equals(callerAccountId)`.
   - Otherwise, for each `RoleAssignment` in `roleAssignmentRepository.findByAccount_IdAndRoleAndActiveTrue(doctorProfile.getAccount().getId(), Doctor)`, allow if `roleAssignmentRepository.existsByAccount_IdAndClinic_IdAndRoleAndActiveTrue(callerAccountId, thatClinicId, ClinicAdmin)`.
   - Otherwise throw `ForbiddenException` (FR-006).
3. `create`: build and save a new `AppointmentType` (name + optional `feeOverride`).
4. `list`: `appointmentTypeRepository.findByDoctorProfile_Id(doctorProfileId)`.
5. `setDefaultFee`: upsert `DoctorDefaultFee` for the doctor (create if absent, update `amount`/`updatedAt` if present).

No method here, or in `FeeResolutionService`, ever references a Booking or payment-status concept (FR-009) — none exists in this codebase.

## Request/Response contract

See `contracts/fee-resolution.md`.
