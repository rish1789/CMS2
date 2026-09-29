# Data Model: Application Shell Sidebar Navigation

No backend data model changes — this feature is frontend-only and introduces no new entity, table, or API contract.

## Frontend-only shape: `SidebarNavItem`

Not a persisted entity — a plain in-memory array literal defined per shell (`ClinicShell.tsx`, `AdminShell.tsx`), passed as a prop to the new `Sidebar` component.

| Field | Type | Notes |
|-------|------|-------|
| `to` | `string` | Route path, e.g. `/staff/clinics/${clinicId}/day-sheet` (staff) or `/super-admin-console/clinics` (admin) — matches an existing, verified route from `App.tsx` (FR-005). |
| `label` | `string` | Visible nav text, e.g. `"Day sheet"`. |
| `icon` | `ReactNode` | One of the existing `staffIcons.tsx`/`adminIcons.tsx` exports — no new icon component. |
| `roles?` | `StaffRole[]` | Optional allowlist (`'ClinicAdmin' \| 'Doctor' \| 'Operations'`). Omitted = visible to all 3 roles. Only `"Onboard staff"` sets this (`['ClinicAdmin']`), per research.md Decision 4's verified backend restriction. Admin items never set this — `AdminShell` has only one role (Super Admin), no filtering needed. |

## Source of the role value used for filtering

`ClinicShell`'s existing `listMyClinics(session.token, { size: ALL_MEMBERSHIPS_PAGE_SIZE })` call (today used only to resolve the breadcrumb's clinic name) is extended to also read the matching `ClinicMembership.role` for the current `:clinicId` and pass it to `Sidebar`. No new fetch, no new field stored anywhere — read once per `ClinicShell` mount, same lifecycle as the existing breadcrumb lookup.
