# Quickstart: Validating 066 (Patient-Linking Same-Account Race)

## Prerequisites

- Java 21. Docker running, for Testcontainers PostgreSQL 16.
- Run from `backend/`. Use `-x spotlessApply` so formatting is checked, not rewritten.

## 1. The previously failing test passes (SC-001, SC-002, SC-005)

```bash
./gradlew test -x spotlessApply --tests "*PatientLinkingSameAccountRaceTest"
```

**Expected:** PASSED, with the test file unchanged. Both concurrent calls return the same Patient id, and exactly one row exists.

## 2. New 066 tests (FR-001, FR-003, FR-004, FR-007)

```bash
./gradlew test -x spotlessApply --tests "*PatientLinking*" --tests "*066*"
```

The class names are fixed in `tasks.md`. **Expected**, all passing:

- **Winner rollback (FR-004):** the waiting call succeeds and exactly one Patient row exists.
- **Failed fixed-time booking (FR-003):** it leaves zero Patient rows.
- **Queue path (FR-007):** two concurrent queue bookings by one account at a new clinic both confirm and share one Patient record.
- **Fixed-time path, booking limit disabled (FR-007):** two concurrent bookings for different slots both confirm and share one Patient record.

## 3. No regressions (SC-004)

```bash
./gradlew spotlessCheck test -x spotlessApply --tests "com.cms.patient.*" --tests "com.cms.booking.*"
```

**Expected:** no new failures compared with `main` `715738b`. The known unrelated failures on `main` are listed in PR #16's description; they are not in these packages except `BookingRateLimitConcurrencyTest`.

## 4. Full suite

```bash
./gradlew spotlessCheck test -x spotlessApply
```

**Expected:** `PatientLinkingSameAccountRaceTest` has left the failure list, and no new failures appear.
