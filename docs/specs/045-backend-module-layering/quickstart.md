# Quickstart: Backend Module Layering & Security Posture Documentation

## Per-module verification (repeat after each module's move, per research.md Decision 3's order)

1. `cd backend && ./gradlew compileJava compileTestJava` — confirm the whole backend still compiles after the move (catches missed import fixes immediately).
2. `./gradlew test --tests "com.cms.identity.unit.*" --tests "*.contract.*"` — the Docker-independent subset; confirm it still passes (proves no behavior change reachable without Docker).
3. `./gradlew spotlessCheck` — confirm formatting (package-declaration changes are exactly the kind of edit Spotless normalizes).

## Actuator verification

4. Start the backend, `curl http://localhost:8080/actuator/health` — expect 200, no auth.
5. `curl http://localhost:8080/actuator/env` (or any other actuator path) — expect 404/403.

## SECURITY.md verification

6. Read `SECURITY.md` and cross-check each row against the actual `SecurityFilterChain` bean in each of the 5 named classes — confirm zero inaccuracies.

## Final full-suite check

7. `./gradlew test` — the full suite; expect the same pre-existing Docker/Testcontainers-only failures as before this feature (confirmed not a regression via step 2's subset passing after every module), full green confirmation deferred to a real CI/dev environment per this project's established pattern.
