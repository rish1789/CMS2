# Quickstart: Validating 068 (Per-Clinic Fees)

## Prerequisites

- Java 21 and Docker (Testcontainers), for the backend.
- Node 24, for the frontend.
- Always pass `-x spotlessApply`.

## 1. Tests

```bash
cd backend && ./gradlew test -x spotlessApply --tests "*ClinicFee*" --tests "*FeeResolution*" --tests "*PerClinic*"
cd backend && ./gradlew spotlessCheck test -x spotlessApply      # full suite: no regressions
cd frontend && npx tsc -b && npm run lint && npx vitest run
```

## 2. Runtime walk-through (fresh database)

1. **Start the app** against an empty Postgres. Flyway must report V43.
2. **Before the upgrade:** register two clinics, A and B. Onboard the same doctor at both, then add a type and a doctor-wide fee. Both must be done before V42/V43 to exercise the copy, so seed them with the pre-068 build or with SQL.
3. **After the upgrade:** `GET /clinics/A/doctors/{d}/fees` and `GET /clinics/B/doctors/{d}/fees` both show the copied prices (US3).
4. **US1:**
   - As A's admin, `PUT /clinics/A/doctors/{d}/fees/default {amount: 300}`.
   - Book at A: the locked fee is 300.
   - Book at B: the locked fee is unchanged.
5. **US2:** as B's admin, `PUT` on clinic A's fees returns 403, and A's fee is unchanged.
6. **FR-012:** `PUT /api/v1/doctors/{d}/default-fee` returns 410 `FEE_MOVED_TO_CLINIC`.
7. **US4:** readiness at a clinic with no prices for the doctor shows "booking setup incomplete".
