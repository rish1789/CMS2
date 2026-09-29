# Implementation Plan: Patient-Linking Same-Account Race

**Branch**: `claude/066-patient-linking-race` | **Date**: 2026-09-29 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/066-patient-linking-race/spec.md`

## Summary

Restore spec 009 FR-006 for race FR-005a. When the same Patient Account links or creates a Patient record at the same clinic concurrently, every call must succeed and share one record. Today the loser's conflicting INSERT aborts its PostgreSQL transaction, and the in-transaction re-read then fails (research.md R1).

**Approach**:
- `PatientLinkingService.findOrCreatePatient` takes the existing row lock on the Patient Account (`findWithLockById`, as used by the 060 booking-limit gate) instead of a plain `findById`.
- Concurrent same-account calls then run one after another. The second sees the first's committed record, or creates its own if the first rolled back, and never issues a conflicting INSERT.
- The broken catch-and-re-read block is removed. The unique index stays as the data-layer backstop.
- No schema change, and the fixed-time booking stays all-or-nothing (research.md R3).

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 3.3 (Spring Data JPA, Hibernate), Flyway (untouched)

**Storage**: PostgreSQL 16. Default READ COMMITTED isolation. Uses the existing partial unique index `uq_patient_clinic_account` (V5).

**Testing**: JUnit 5 + Testcontainers integration tests (`com.cms.patient.record.integration`, `com.cms.booking`) and Mockito unit tests. `./gradlew test`.

**Target Platform**: Linux server (Spring Boot backend)

**Project Type**: Web service. Backend-only change, with no API or frontend change.

**Performance Goals**: No change for different-account traffic. Same-account concurrent linking is serialized, so a second call waits at most one booking transaction.

**Constraints**:
- Fixed-time booking stays all-or-nothing (FR-003).
- The queue path's existing separate commit is unchanged.
- No native SQL and no transaction-manager configuration change.

**Scale/Scope**: One production method changed (`PatientLinkingService.findOrCreatePatient`), plus new tests.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle / Gate | Status | Evidence |
|---|---|---|
| I. Test-first | PASS | `PatientLinkingSameAccountRaceTest` is already red. New tests (research.md R4) are written and run before the change, and each is recorded red or green before implementing. |
| II. Simplicity & YAGNI | PASS | Reuses the existing `findWithLockById`. No new repository method, native SQL, configuration or abstraction. It removes code (the dead re-read). |
| III. Module boundaries | PASS | The change is inside `patient.record` (`PatientLinkingService`), using `patient.account`'s repository exactly as today. Booking services are untouched. |
| IV. Data privacy & integrity | PASS | The same-account duplicate race stays closed at the data layer (the unique index is unchanged, and the row lock is data-layer). No stray Patient record on a failed fixed-time booking (FR-003). There is no anonymization or retention impact. |
| Multi-tenancy | PASS | Queries remain clinic-scoped (`findByClinic_IdAndPatientAccount_Id`, phone match by clinic). The lock is on the global Patient Account, an explicit global entity. |
| Rationale note (booking-logic change) | PASS | Implements 009 FR-005a/FR-006 and SC-004. Preserves 060 research Decision 1, which takes the same lock. |
| Flyway review | N/A | No migration. |

**Post-design re-check (after Phase 1):** PASS, with no violations and nothing in Complexity Tracking. The design artifacts (data-model.md, contracts/, quickstart.md) add no schema, endpoint or dependency.

## Project Structure

### Documentation (this feature)

```text
specs/066-patient-linking-race/
├── spec.md
├── plan.md              # This file
├── research.md          # R1 root cause, R2 reachability, R3 design + alternatives, R4 tests
├── data-model.md        # No schema change; lock lifecycle
├── quickstart.md        # Validation commands
├── contracts/
│   └── patient-linking-service.md   # Delta on 009's service contract
├── checklists/
│   └── requirements.md
└── tasks.md             # Phase 2 (/speckit-tasks)
```

### Source Code (repository root)

```text
backend/src/main/java/com/cms/patient/record/service/
└── PatientLinkingService.java             # CHANGED: lock the account; remove broken re-read

backend/src/main/java/com/cms/patient/account/repository/
└── PatientAccountRepository.java          # UNCHANGED: findWithLockById reused (javadoc may note 066)

backend/src/test/java/com/cms/patient/record/integration/
├── PatientLinkingSameAccountRaceTest.java         # UNCHANGED (red → green)
└── (new) winner-rollback test                     # FR-004

backend/src/test/java/com/cms/booking/...          # (new) FR-003 atomicity + FR-007 queue and
                                                   #       fixed-time (booking limit off) concurrency tests
```

**Structure Decision**: Backend-only, in the existing package-per-feature layout (`backend/src/main/java/com/cms/<module>`). The tests follow the existing three-shape convention (CONTRIBUTING.md): Testcontainers integration tests for the concurrency and atomicity rules, plus a unit-test update only if an existing Mockito test stubs `findById` on this path.

## Complexity Tracking

No constitution violations, so no entries.

## Notes

- **Spec correction:** during planning, research.md R2 found that the queue booking path already commits the Patient record separately (022 design). Spec FR-003, User Story 2 and SC-003 were narrowed to the fixed-time path so that they state existing behaviour accurately; the scope is unchanged.
- **Agent context:** the `update-agent-context` script is PowerShell-only, and `pwsh` is not installed in this environment. It was not run, and no new technology was introduced.
