# Implementation Plan: Readable Rate-Limit Errors (071)

**Spec**: [spec.md](spec.md)

## Approach

### Backend

Register Spring's own `org.springframework.web.filter.CorsFilter` as a servlet filter at `Ordered.HIGHEST_PRECEDENCE`, on the same four paths `RateLimitingConfig` throttles, built from the existing `CorsConfigurationSource` bean.
- For an allowed origin it adds the CORS headers and continues, so the limiter's 429 already carries them.
- For a disallowed origin it refuses with 403, as Spring Security's CORS did before; the limiter never sees the request.
- For a preflight it answers directly; the limiter only counts `POST`, so nothing changes there.
- Spring Security's later CORS step skips a response that already has an allow-origin header, so headers are not doubled.

The paths are shared from `RateLimitingConfig` so the two lists cannot drift.

`CorsConfig` adds `setExposedHeaders(List.of("Retry-After"))`.

Rejected alternatives:
- Adding CORS headers inside `RateLimitingFilter`: it would duplicate the origin policy.
- Moving the limiter after Spring Security: it would change which requests reach authentication before being throttled.

### Frontend

- `signupPatient` reads `Retry-After` on a non-OK response and puts it on the error as `retryAfterSeconds`.
- `SignupPatientErrorBody` gains `RATE_LIMIT_EXCEEDED` and a fallback shape for unknown codes.
- `SignupForm`:
  - shows a signup-specific too-many-attempts message, reusing `formatRetryAfter` from `lib/rateLimitMessage.ts`;
  - shows the server message for an unknown code, or a generic one;
  - shows a "could not reach the server" message for a `TypeError` from `fetch`.
- The changes stay inside `applyApiError` and the `catch` block, so they merge cleanly with #36 (spec 070), which edits other parts of the form.

## Tests (first, red)

- **Integration** `common/integration/RateLimitCorsIntegrationTest`: the full app with `max-attempts=3`. It covers the allowed origin on all four paths, the disallowed origin, preflight and the no-`Origin` case.
- **Vitest** `tests/patient-account/SignupErrors.test.tsx`: the form's four cases, and the API reading `Retry-After` from a mocked `fetch`.

## Constitution check

- Test-first: yes.
- No schema change and no new abstraction: it reuses Spring's `CorsFilter` and the existing policy bean.
- Tenant scoping: not affected; these are public endpoints.
