# Tasks: Readable Rate-Limit Errors (071)

**Input**: [spec.md](spec.md), [plan.md](plan.md)

- [X] T001 Branch from current `main`.
- [X] T002 Write `RateLimitCorsIntegrationTest` through the real chain (`max-attempts=3`), covering the allowed origin on all four throttled paths, the disallowed origin, preflight and the no-`Origin` case. Expect RED.
- [X] T003 Register `CorsFilter` at `HIGHEST_PRECEDENCE` on the throttled paths, built from `corsConfigurationSource`, and expose `Retry-After`. T002 should then be GREEN; the existing `RateLimitingIntegrationTest` and `RateLimitingFilterTest` should stay green.
- [X] T004 Write Vitest `SignupErrors.test.tsx`: a rate limit with and without `Retry-After`, an unknown code, a network failure, and the API reading the header. Expect RED.
- [X] T005 Update `api.ts` and `SignupForm.tsx`. T004 should then be GREEN.
- [X] T006 Gates: full backend suite with `spotlessCheck`; `tsc -b`, lint (24 baseline), Vitest and build.
- [X] T007 Runtime check: the jar plus Vite and headless Chromium. Exceed the signup limit from `http://localhost:5173` and confirm the page shows the rate-limit message.
- [X] T008 Docs: a `backlog/progress.md` row and the plan's 2R.3 status. Then commit, push and open the PR.

## Observed results (2026-10-01, IST)

- **T001:** branched from `main` @ `0a2ee68`.
- **T002 (red):** `RateLimitCorsIntegrationTest` failed 5 of 7.
  - All four throttled paths returned a 429 with no `Access-Control-Allow-Origin` header.
  - A disallowed origin got a **429** on its 4th attempt instead of a refusal, because the limiter ran before CORS.
  - Preflight and the no-`Origin` case were already green; they guard existing behaviour.
- **T003 (green):** 7/7. `RateLimitingIntegrationTest` (1/1) and `RateLimitingFilterTest` (4/4) still pass, and so do the booking rate-limit tests.
  - **Behaviour note:** a disallowed origin is now refused (403) on every attempt, before the limiter counts it. Previously it got 403 three times, then 429. It is still refused, and no allowed origin, limit or path changed.
- **T004 (red):** 6 of 6 failed.
- **T005 (green):** 6 of 6 pass.
  - The error union stays closed, because an open `string` member would break narrowing on the existing cases.
  - The `default` branch reads the message through a widened view.
- **T006:**
  - Backend: full suite **1,140 passed, 0 failed, 0 skipped** (1,133 + 7), with `spotlessCheck` green, in 23 min.
  - Frontend: `tsc` clean; lint exit 0 with the 24 baseline warnings (none new); Vitest **463/463 in 80 files**; build OK.
- **T007 (runtime):** fresh Postgres 16, the 071 jar on port 8090 with `APP_RATE_LIMIT_MAX_ATTEMPTS=3` (an isolated limit, not the shared default), Vite on 5173 and headless Chromium.
  - **Signup page:** attempts 1–3 returned 400 and showed the password-policy error. Attempt 4 returned **429** and showed "Too many signup attempts from this network. Try again in less than a minute."
  - **curl, clinic registration, allowed origin:** 400 ×3, then 429 with `Access-Control-Allow-Origin: http://localhost:5173`, `Access-Control-Expose-Headers: Retry-After` and `Retry-After: 59`. Each response has exactly one allow-origin header.
  - **curl, `Origin: http://evil.example`:** 403 ×4, with no allow-origin header.
  - **Preflight:** 200 with the allow-origin header.
  - **Unrelated failure:** the only failed browser request was Google Fonts, because the sandbox proxy's certificate is not trusted.
- **Live-audit status:** finding 5 (rate-limit errors unreadable) and the `SignupForm` fallback gap are **fixed**.
