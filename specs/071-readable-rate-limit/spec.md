# Feature Specification: Readable Rate-Limit Errors

**Feature Branch**: `claude/071-readable-429`

**Created**: 2026-10-01

**Status**: Draft

**Input**: Phase 2R.3 of `docs/NEXT_PHASES_ACTION_PLAN.md`; finding 5 of `docs/LIVE_SOFTWARE_AUDIT_2026-10-01.md`, plus the related `SignupForm` fallback gap.

## Context

`RateLimitingFilter` (047) throttles four public endpoints by client IP: staff login, patient login, patient signup and clinic registration. It is a servlet filter at `HIGHEST_PRECEDENCE + 1`, so it runs **before** Spring Security's CORS handling. When it refuses a request it writes the 429 itself and never passes the request on. That 429 therefore has no `Access-Control-Allow-Origin` header.

In a browser on a different origin (the Vite app on `:5173` calling the API on `:8080`), the browser hides that response. The page sees a network failure, not the server's "Too many requests" message. The audit reproduced this: requests 1–30 returned 400 with the allow-origin header, and request 31 returned 429 with `Retry-After: 59` but no allow-origin header.

Separately, `SignupForm` has no branch for `RATE_LIMIT_EXCEEDED` or any other unrecognised error code. Once the 429 is readable, a throttled signup would show **no error at all**.

**What changes:**
- CORS headers are applied, by the existing origin policy, before the limiter can short-circuit.
- `Retry-After` is exposed to browser scripts.
- Signup shows a visible message for a rate limit, for unknown error codes and for network failures.

**What does not change:**
- The allowed origins, the limit (30 per 60 s by default), the window, the throttled paths and the 429 body.
- Disallowed origins stay refused.
- Preflight behaviour.
- The Phase 3C limiter storage and proxy decisions.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - A throttled browser client can read the 429 (Priority: P1)

A patient on the web app retries signup many times in a minute. The next attempt is throttled, and the app can read the server's reason and how long to wait.

**Independent Test:** Through the real filter chain, with a test limit of 3, send 4 signups from the allowed origin. The 4th is 429 and carries `Access-Control-Allow-Origin` for that origin, the `RATE_LIMIT_EXCEEDED` body and `Retry-After`, with `Retry-After` listed in `Access-Control-Expose-Headers`.

**Acceptance Scenarios**:

1. **Given** an allowed origin over the limit, **When** it calls any of the four throttled endpoints, **Then** the 429 carries the allow-origin header for that origin, `Access-Control-Allow-Credentials: true`, the JSON body and `Retry-After`, and `Retry-After` is exposed.
2. **Given** a disallowed origin, **When** it calls a throttled endpoint, **Then** it is refused (403) without an allow-origin header, exactly as before.
3. **Given** a preflight `OPTIONS` from the allowed origin, **When** it is sent, **Then** it succeeds with the usual CORS headers and is not counted by the limiter.
4. **Given** requests with no `Origin` header (same-origin or non-browser), **When** they exceed the limit, **Then** they still get 429 with `Retry-After`.

### User Story 2 - Signup always shows why it failed (Priority: P1)

**Independent Test:** Make the signup call fail with `RATE_LIMIT_EXCEEDED`, an unknown code, and a network failure. Each shows a visible alert.

**Acceptance Scenarios**:

1. **Given** a 429 `RATE_LIMIT_EXCEEDED` with `Retry-After: 59`, **When** signup is submitted, **Then** the alert says there were too many attempts and to try again in less than a minute.
2. **Given** a 429 without a readable `Retry-After`, **When** signup is submitted, **Then** the alert gives the too-many-attempts message without a wait time.
3. **Given** an error code the form does not know, **When** signup is submitted, **Then** the alert shows the server's message, or a generic message if there is none.
4. **Given** the request cannot reach the server, **When** signup is submitted, **Then** the alert says the server could not be reached and to check the connection.

### Edge Cases

- Spring Security's own CORS processing runs later on the same request. It must not add a second allow-origin header; Spring skips responses that already carry one.
- Non-throttled paths are unchanged: their CORS is still handled by each security chain.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: On every throttled path, CORS MUST be processed with the existing `corsConfigurationSource` bean, before the rate limiter.
- **FR-002**: `Retry-After` MUST be in `Access-Control-Expose-Headers` for allowed origins.
- **FR-003**: Allowed origins, methods, credentials, limits, windows, paths and the 429 body MUST be unchanged. No wildcard origin.
- **FR-004**: The signup client MUST read `Retry-After` on a 429 and pass it to the form.
- **FR-005**: `SignupForm` MUST show a visible message for `RATE_LIMIT_EXCEEDED` (with the wait when known), for unknown error codes, and for network failures.

## Success Criteria *(mandatory)*

- **SC-001**: A throttled allowed-origin request is readable by the browser: 0 of the four throttled endpoints return a 429 without CORS headers to an allowed origin.
- **SC-002**: A failed signup shows no error 0 times.
