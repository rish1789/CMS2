# Research: Last Active ClinicAdmin Protection

Reuses the established stack and, specifically, 004's staff authentication (`StaffJwtService`, the `STAFF`-audience JWT, and the same ClinicAdmin-of-clinicId authorization pattern `StaffOnboardingService` already uses). No new decisions beyond placement.

## Decisions

### Module placement

- **Decision**: `StaffDeactivationService` and its controller live in `com.cms.identity.staff`, alongside 004's `StaffOnboardingService`/`StaffOnboardingController` — same package, same authorization pattern (caller's `STAFF` JWT resolved to an Account ID, checked against `RoleAssignmentRepository` for an active ClinicAdmin RoleAssignment at the target clinic).
- **Rationale**: This is the natural counterpart to onboarding (create vs. deactivate a staff Role Assignment) — same actors, same authorization rule, same entity. No reason to place it elsewhere.
- **Alternatives considered**: None.

### Last-active-ClinicAdmin check implementation

- **Decision**: `RoleAssignmentRepository.countByClinic_IdAndRoleAndActiveTrue(clinicId, ClinicAdmin)` — if the target Role Assignment is `role=ClinicAdmin` and this count is `1` (i.e., it's the only one), block the deactivation.
- **Rationale**: A simple, direct count query is sufficient and matches FR-008's per-clinic scoping naturally (the query is already clinic-scoped).
- **Alternatives considered**: Loading all ClinicAdmin RoleAssignments for the clinic and counting in application code — rejected as an unnecessary round-trip when the DB can do it directly.
