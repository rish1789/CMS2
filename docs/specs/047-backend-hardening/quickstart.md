# Quickstart: Backend Security & Scale Hardening

## Automated verification

1. `cd backend && ./gradlew compileJava compileTestJava` — confirm clean compile.
2. `./gradlew test --tests "com.cms.identity.unit.*" --tests "*.contract.*"` — Docker-independent subset (existing convention this session, since full-suite Testcontainers tests can't run here).
3. Run any new unit tests added for the discovery cap and notification log-gate.

## Live verification (rate limiter, US4/FR-005/SC-004)

4. Start the backend via `preview_start` with `RATE_LIMIT_MAX_ATTEMPTS=3` set (temporarily lowered for a fast, observable check).
5. Send 4 rapid `POST /api/v1/staff/login` requests with any credentials (via the browser's `fetch`, per this session's established live-verification pattern).
6. Confirm the first 3 return normal auth-failure responses (401) and the 4th returns 429 with a `Retry-After` header.
7. Restart the backend with default (unset) `RATE_LIMIT_MAX_ATTEMPTS` before any further live testing in this feature or the next.

## Live verification (discovery cap, US1)

8. Query `GET /api/v1/discovery/search?size=200` (an excessively large requested size) against the live backend and confirm the response is capped at the maximum (50), not 200.

## Live verification (notification log gate, US2)

9. Trigger any existing notification-publishing flow (e.g. a booking cancellation, per 037's real production caller) with the log-pii flag unset and inspect `preview_logs` for the resulting log line — confirm no recipient/message content appears.
