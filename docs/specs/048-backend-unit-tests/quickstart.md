# Quickstart: Backend Unit-Test Backfill

1. `cd backend && ./gradlew compileTestJava` — confirm the 4 new test classes compile.
2. `./gradlew test --tests "com.cms.booking.unit.*" --tests "com.cms.scheduling.unit.*"` — confirm all new tests pass with no Docker running.
3. `./gradlew test --tests "com.cms.identity.unit.*" --tests "*.contract.*" --tests "com.cms.discovery.unit.*" --tests "com.cms.notification.unit.*" --tests "com.cms.booking.unit.*" --tests "com.cms.scheduling.unit.*"` — the full Docker-independent subset this session has built up, confirming zero regression anywhere.
4. `./gradlew compileJava compileTestJava` (full backend) — confirm zero regression to the rest of the codebase, including the Testcontainers-backed integration suite (compiles even though it can't execute here).
