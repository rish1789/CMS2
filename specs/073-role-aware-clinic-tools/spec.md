# Feature Specification: Role-Aware Clinic Tools

**Feature Branch**: `claude/073-role-aware-tools`

**Created**: 2026-10-01

**Status**: Draft

**Input**: Phase 2R.5 of `docs/NEXT_PHASES_ACTION_PLAN.md`; finding 7 of `docs/LIVE_SOFTWARE_AUDIT_2026-10-01.md` ("Doctor dashboard exposes administrator-only tools").

## Context

The staff sidebar (050, 060, 063) already hides tools by the caller's role at the current clinic. The clinic dashboard tiles and the pages themselves do not:
- A Doctor sees the **Onboard staff** and **Booking protection** tiles.
- Opening `/staff/clinics/:id/onboard` gives a Doctor a complete, editable onboarding form. The backend refuses only on submit.
- The same is true of direct URLs for Booking protection, its limit override and the front-desk walk-in screen.

The audit established this as a UI mismatch, not a backend bypass. The services enforce the rules.

There is also a latent multi-role defect. `GET /api/v1/clinics/mine` returns **one row per role assignment**, but `ClinicShell` keeps only the first row it finds for the clinic. A user who is both Doctor and ClinicAdmin at one clinic could be treated as only a Doctor, or only an admin, depending on row order.

## Existing rules (enforced by the backend; this feature only mirrors them)

| Tool | Allowed roles at the current clinic | Backend enforcement |
|---|---|---|
| Onboard staff | ClinicAdmin | `StaffOnboardingService` |
| Booking protection (flags) | ClinicAdmin | `ClinicProtectionFlagService` |
| Booking protection: limit override | ClinicAdmin | `ClinicBookingLimitOverrideController` |
| Walk-in (front desk) | ClinicAdmin, Operations | `FrontDeskWalkInService` |
| Every other dashboard tile | every role | — |

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Tiles match the caller's roles (Priority: P1)

**Acceptance Scenarios**:

1. **Given** a Doctor at the clinic, **Then** the dashboard shows no Onboard staff and no Booking protection tile.
2. **Given** an Operations user, **Then** the same two tiles are hidden.
3. **Given** a ClinicAdmin, **Then** both tiles show.
4. **Given** a user who is both Doctor and ClinicAdmin at the clinic, **Then** both tiles show, in any row order.
5. **Given** the roles are still loading, **Then** no restricted tile is shown yet.

### User Story 2 - Direct URLs never show a restricted editable form (Priority: P1)

**Acceptance Scenarios**:

1. **Given** a Doctor opens `/onboard`, `/protection`, `/protection/limit-override` or `/walk-in` directly, **Then** they see an explanation of who can use the tool and a link back to the clinic dashboard. No form or form field is shown.
2. **Given** an Operations user, **Then** `/walk-in` works, and the three admin-only URLs show the explanation.
3. **Given** a ClinicAdmin, **Then** all four pages work.
4. **Given** the roles are still loading, **Then** a loading state shows and the form does not flash.
5. **Given** the role lookup fails, **Then** an error explains that access could not be confirmed. The restricted form is not shown.

### User Story 3 - Switching clinics uses the new clinic's roles (Priority: P1)

**Acceptance Scenario**: **Given** a user who is ClinicAdmin at clinic A and Doctor at clinic B, **When** they move from A to B, **Then** B's dashboard and pages follow the Doctor rules. A's admin rights never carry over, not even while B's roles load.

## Requirements *(mandatory)*

- **FR-001**: The shell MUST resolve **all** of the caller's roles at the current clinic, plus a status (`loading`, `ready` or `failed`), and give both to child pages.
- **FR-002**: The sidebar, the dashboard tiles and the page entry points MUST use one shared rule table, matching the table above.
- **FR-003**: A restricted tile MUST be hidden unless the roles are `ready` and allowed.
- **FR-004**: A restricted page MUST render its form only when the roles are `ready` and allowed. Otherwise it shows a loading, denied or failed state.
- **FR-005**: Roles MUST be scoped to the clinic in the URL. A clinic change starts in `loading`.
- **FR-006**: Backend authorization is unchanged and remains the enforcement boundary.
- **FR-007**: The existing single `role` in the shell context remains for current consumers. With several roles, it is the highest of ClinicAdmin, Doctor, Operations, which is deterministic instead of depending on row order.

## Success Criteria *(mandatory)*

- **SC-001**: A Doctor or Operations user reaches an editable admin-only form 0 times, by tile or by URL.
- **SC-002**: An active ClinicAdmin loses access to 0 tools.
