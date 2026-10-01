# Implementation Plan: Role-Aware Clinic Tools (073)

**Spec**: [spec.md](spec.md). Frontend only.

## Changes

- **New `routes/staff/clinicRoles.ts`:**
  - `CLINIC_TOOL_ROLES`: the rule table (onboard, protection and walk-in);
  - `hasAnyRole(roles, allowed)`;
  - `primaryRole(roles)`, which returns ClinicAdmin, then Doctor, then Operations.
- **`ClinicShell`:**
  - collects every membership row for the clinic, not only the first;
  - keeps the result keyed by `clinicId`, so a different clinic in the URL reads as `loading` until its own lookup resolves (FR-005). This needs no synchronous reset in an effect.
  - Context: `{ role, roles, rolesStatus }`.
  - The sidebar's `roles` come from the rule table.
- **`Sidebar`:** an optional `activeRoles` prop next to `activeRole`. An item is shown when any active role is allowed.
- **`ClinicToolsDashboard`:** the Onboard and Protection tiles carry `roles` and are filtered with the context. They are hidden while roles are loading or when there is no context.
- **`ClinicToolPages`:** a `RequireClinicRole` gate, with loading, denied and failed states, wraps `OnboardStaffPage`, `ProtectionFlagsPage`, `ClinicLimitOverridePage` and `FrontDeskWalkInRoutePage`. The legacy walk-in redirect lands on the gated page.

Not changed:
- `SessionSlotsView` and `SessionOperationsPage` keep reading `role === 'Doctor'`.
- `AppointmentTypesPage` keeps reading `role === 'ClinicAdmin'`.

They now receive the deterministic primary role. Widening them to multiple roles is out of scope.

## Tests (first, red)

New Vitest `tests/routes/staff/RoleAwareTools.test.tsx`, rendering the real `ClinicShell` with child routes and a mocked `listMyClinics`. It covers:
- Doctor, Operations and ClinicAdmin tiles;
- multi-role users in both row orders;
- the loading state shows no restricted tile;
- direct URLs for each role, including the loading and failed states;
- a clinic switch from an admin clinic to a doctor clinic.

## Constitution check

- Test-first: yes.
- No backend change.
- No new authorization policy: this mirrors the existing server rules.
