# Data Model: Last Active ClinicAdmin Protection

## No new entity

This feature introduces no new table. It adds a deactivation action (`active: true → false`) to 001's existing `RoleAssignment` entity, guarded by a check against other `RoleAssignment` rows at the same clinic.

## State Transitions (in scope for this feature)

- `RoleAssignment.active`: `true → false` (deactivation) — allowed for any role except when it's a `ClinicAdmin`-role row that is the clinic's *only* active ClinicAdmin Role Assignment, in which case the transition is blocked entirely (FR-004).
- Idempotent: calling deactivate on an already-`false` row is a no-op success (FR-007).

## Query Shape

`RoleAssignmentRepository.countByClinic_IdAndRoleAndActiveTrue(clinicId, Role.ClinicAdmin)` — used to decide whether a given ClinicAdmin deactivation would drop the count to zero (research.md).

## Out of Scope for This Data Model

- Any new field on `RoleAssignment` — this feature only toggles the existing `active` boolean.
- Re-activation of a previously deactivated Role Assignment — not described anywhere in the source material.
