# Implementation Plan: Clinic Staff Console — Browse & Pick Instead of Type-an-ID

**Branch**: `041-staff-console-pickers` | **Date**: 2026-09-08 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/041-staff-console-pickers/spec.md`

## Summary

Replace every raw-UUID "type an ID and click Go" entry point in the clinic staff console with a real browse/pick flow, backed by new list/search endpoints the backend doesn't currently expose. Six new read endpoints across four existing modules (identity.account, identity.doctor, identity.staff, scheduling, booking, patient.record — each staying within that module's already-established dependency direction, per research.md) feed five new frontend views: a clinic picker (replacing `StaffDashboard`'s ID box), a clinic "day sheet" (session list → per-session slot/booking view, replacing 8 of the tool-launcher cards), a doctor picker, a staff picker, and a patient search — all navigating to the same, unchanged destination routes/forms that already exist.

## Technical Context

**Language/Version**: Java 21 (backend), TypeScript/React (frontend) — unchanged.

**Primary Dependencies**: Spring Data JPA (new repository query methods, no new dependency), React Router (new routes/views, already in use).

**Storage**: PostgreSQL via Flyway — **no new migration**; every field these lists need already exists on `Clinic`, `RoleAssignment`, `Session`, `Slot`, `Booking`, `Patient`, `DoctorProfile`, `Account` (data-model.md).

**Testing**: JUnit 5 + Spring Boot Test + Testcontainers (backend); Vitest + React Testing Library (frontend) — same sandbox Docker/Testcontainers limitation as every prior feature this session applies to the new backend integration tests.

**Target Platform**: Same Spring Boot server + browser SPA.

**Project Type**: Web application (`backend/` + `frontend/`, existing structure).

**Performance Goals**: The day sheet's session list is bounded to a fixed 14-day window (FR-008) specifically so it stays a small, fast query — no pagination needed for v1.

**Constraints**: Zero new authorization concept (FR-010) — every new list endpoint's access check reuses the exact "active role at this clinic" pattern (`RoleAssignmentRepository.existsByAccount_IdAndClinic_IdAndActiveTrue`, already used by 6+ existing endpoints) rather than inventing a new one. No change to any existing action endpoint's behavior — pickers only navigate to routes that already exist.

**Scale/Scope**: 6 new GET endpoints, ~6 new/extended repository query methods, 2 new Spring Security matchers groups (all under the existing `/api/v1/clinics/**` staff chain), 5 new frontend views, rework of 2 existing frontend files (`StaffDashboard.tsx` replaced; `ClinicToolsDashboard.tsx` reduced to only the 2 tool cards this feature doesn't cover), no new module, no migration.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design below.*

- **I. Test-First Development**: PASS (planned) — every new endpoint and view gets a failing test first, same established pattern.
- **II. Simplicity & YAGNI**: PASS — reuses the existing "active role at this clinic" authorization check verbatim (no new auth concept); reuses existing repositories/entities with additive query methods only; the day sheet's fixed 14-day window (not a general date-range picker) is deliberately the simplest thing that satisfies the current, confirmed requirement (FR-008's own resolution).
- **III. Modular, Library-First Architecture**: PASS — each new list endpoint is placed in the module that already owns the entity it lists (research.md R1), preserving the codebase's own established one-way dependency direction (`booking` → `scheduling`/`patient.record`/`identity.*`, never the reverse) rather than reaching backwards across it — this was a deliberate lesson carried forward from the Analyze-stage fix in `040-super-admin-rbac-login`.
- **IV. Data Privacy & Integrity by Design**: PASS — patient search (FR-007) is clinic-scoped only (never cross-clinic, matching `Patient`'s existing scoping everywhere else); no new patient-identifying data is exposed beyond what staff can already see once they know a booking's ID today; anonymized patients' names remain whatever `033`'s existing anonymization already produces (this feature doesn't change that).
- **Technology & Platform Constraints**: PASS — no migration, no new module, no scope-boundary item touched.

No violations. Complexity Tracking table omitted.

## Project Structure

### Documentation (this feature)

```text
specs/041-staff-console-pickers/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md         # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/
│   └── staff-console-pickers.md
└── tasks.md             # Phase 2 output (/speckit-tasks)
```

### Source Code (repository root)

```text
backend/src/main/java/com/cms/
├── identity/account/
│   ├── RoleAssignmentRepository.java     # MODIFIED: + findByAccount_IdAndActiveTrue, findByClinic_IdAndActiveTrue
│   └── StaffClinicController.java         # NEW: GET /api/v1/clinics/mine (FR-001)
├── identity/doctor/
│   ├── DoctorProfileRepository.java       # MODIFIED: + findByClinicStaffed(clinicId)
│   └── ClinicDoctorController.java        # NEW: GET /api/v1/clinics/{clinicId}/doctors (FR-006)
├── identity/staff/
│   └── ClinicStaffController.java         # NEW: GET /api/v1/clinics/{clinicId}/staff (FR-006a) - reuses RoleAssignmentRepository
├── scheduling/
│   ├── SessionRepository.java              # MODIFIED: + findByClinic_IdAndSessionDateBetween
│   └── ClinicSessionListController.java   # NEW: GET /api/v1/clinics/{clinicId}/sessions?from&to (FR-003)
├── booking/
│   ├── BookingRepository.java              # MODIFIED: + findBySlot_Session_IdAndStatus (batch, avoids N+1)
│   └── SessionDaySheetController.java     # NEW: GET /api/v1/clinics/{clinicId}/sessions/{sessionId}/day-sheet (FR-004/FR-005) - composes SlotRepository (scheduling) + BookingRepository/PatientRepository (booking/patient.record), the module already legitimately depending on both
└── patient/record/
    ├── PatientRepository.java              # MODIFIED: + search(clinicId, term)
    └── ClinicPatientSearchController.java # NEW: GET /api/v1/clinics/{clinicId}/patients/search?q= (FR-007)
backend/src/main/java/com/cms/identity/account/SecurityConfig.java  # MODIFIED: + 6 new GET matchers
backend/src/test/java/com/cms/...          # NEW/MODIFIED: one integration test class per new controller

frontend/src/
├── routes/staff/
│   ├── StaffDashboard.tsx                  # REPLACED: clinic picker (was: type-Clinic-ID form)
│   ├── ClinicToolsDashboard.tsx             # MODIFIED: reduced to the 2 tool cards not covered by this feature ("Onboard staff", "Join a patient to the waitlist") + a link into the new Day Sheet
│   └── ClinicToolPages.tsx                  # UNCHANGED: every existing destination page/route stays exactly as-is; pickers navigate to these same routes with real ids
├── features/
│   ├── staff-clinics/
│   │   ├── api.ts, MyClinicsList.tsx        # NEW (FR-001/US1)
│   ├── day-sheet/
│   │   ├── api.ts, DaySheet.tsx, SessionSlotsView.tsx  # NEW (FR-003/FR-004/FR-005, US2)
│   ├── doctor-picker/
│   │   ├── api.ts, DoctorPicker.tsx         # NEW (FR-006, US4)
│   ├── staff-picker/
│   │   ├── api.ts, StaffPicker.tsx          # NEW (FR-006a, US4)
│   └── patient-search/
│       ├── api.ts, PatientSearch.tsx        # NEW (FR-007, US3)
└── App.tsx                                   # MODIFIED: new routes for the day sheet and 3 pickers
frontend/tests/                               # NEW: one test file per new feature folder
```

**Structure Decision**: Existing `backend/` + `frontend/` web-application layout, unchanged. No new backend module — every new controller/repository-method lands inside the module that already owns its entity (research.md R1). Frontend gets 5 new feature folders (one per picker/list view) plus a route-wiring change in `App.tsx`, following the same "feature folder + thin route page" shape every prior feature has used.
