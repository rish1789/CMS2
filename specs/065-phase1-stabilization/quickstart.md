# Quickstart / Validation: 065 Phase 1 Stabilization

## Prerequisites

- **Java 21**, and the Gradle distribution used on this machine (`C:\Users\risha\AppData\Local\Temp\gradle-8.10\bin\gradle.bat`).
- **Node 20+**.
- **Local Postgres**, configured through `.env`.
- **Docker** for integration tests. It is **unavailable on the audit machine**, so integration tests are reported "Not executed — Docker unavailable". CI (ubuntu, Docker present) runs them.

## 1. Static and unit/contract gates

```bash
gradle -p backend spotlessCheck
gradle -p backend test -x spotlessApply --tests "*Test" --continue   # unit + @WebMvcTest pass; integration classes fail to initialise without Docker
cd frontend && npx tsc -b && npm run lint && npm run test
```

## 2. Targeted regression tests (names in tasks.md)

| Story | Test (backend) | Kind |
|---|---|---|
| US1 | `StaffChainFailClosedContractTest`, `PatientChainFailClosedContractTest` | `@WebMvcTest` (runs locally) |
| US2/US3/US4 | `SessionAvailabilityServiceTest` (fixed clock) | unit (runs locally) |
| US2/US3 | `SessionCancellationServiceTest`, `SessionPartialCancellationServiceTest` | unit |
| US2/US4 | `PatientBookingServiceAvailabilityTest`, `StaffBookingServiceAvailabilityTest`, `QueueAndWalkInAvailabilityTest` | unit |
| US2/US3/US4 | `SessionCancellationBookabilityIntegrationTest`, `ElapsedSlotListingIntegrationTest`, plus updated `SessionCancellationRejectionTest` | integration (Docker) |
| US5 | `TreatingDoctorAuthorizationServiceTest`, plus updated clinical service tests | unit |
| US6 | 3 consecutive `npm run test` full runs | frontend |

## 3. Runtime smoke (local backend restarted with the change)

1. Anonymous `curl` to each of the 7 audit endpoints → **401**. An unmapped `/api/v1/clinics/{id}/x` → **401**. `POST /api/v1/clinics/register` with `{}` → **400** validation (not 401).
2. Anonymous `GET /api/v1/discovery/cities` → **200**. `/actuator/health` → **200**.
3. Backend starts. This is Flyway V41 plus Hibernate `validate`, and JPQL parsing of the modified `@Query` methods.

## 4. Launch configuration

- `.claude/launch.json` contains no secret values (`grep -c` for the argument names → 0).
- `preview_start backend` still boots, with Super Admin login working, from the `.env` values.
