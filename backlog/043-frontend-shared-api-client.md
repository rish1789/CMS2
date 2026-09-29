# 043 — Frontend Shared API Client

**Module:** Cross-Cutting / Frontend Architecture
**Status:** Ready for spec-kit intake

## User Story
As a frontend developer working in CMS2, I want one shared HTTP client that every feature calls instead of each hand-rolling its own `fetch()`, auth-header injection, and error-parsing class, so that a bug fix or behavior change to how API calls work happens in one file instead of needing to be repeated across ~28 near-identical implementations.

## Context
Verified current state (2026-09-15): `frontend/src/lib/` does not exist at all. Every one of the ~28 `features/*/api.ts` files independently re-declares `const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'`, re-implements `fetch(...)` with a manually attached `Authorization: Bearer ${token}` header, and re-implements its own `*ApiError` class to parse a non-2xx response body (confirmed by sampling `patient-booking/api.ts`, `waitlist/api.ts`, `staff-booking/api.ts` — each defines its own `BookSlotApiError`/`WaitlistJoinApiError`/`WalkInApiError` etc.).

This duplication already caused a real, currently-shipped bug (found during a prior session's audit, documented in `HANDOFF.md` Part 2 and `PRODUCTION_ROADMAP.md` §1.3): every copy-pasted `*ApiError` class's fallback logic makes the backend's specific validation message unreachable dead code in all files following the pattern, because the shared helper each one copy-pastes (`defaultMessageFor`) always has a `default:` branch that fires before the real message is read. Fixing this is a byproduct of consolidation, not a separate task.

## Business Rules
- `frontend/src/lib/apiClient.ts` MUST be the single place that: builds the full request URL from `API_BASE_URL` + a path, injects the `Authorization: Bearer <token>` header when a token is provided, and parses a non-2xx response body exactly once, surfacing the backend's specific validation/error message (not swallowing it behind a generic default).
- Migration MUST happen one feature at a time, starting with a low-traffic feature to prove the pattern (per the roadmap's own suggestion) before migrating high-traffic ones (e.g. `patient-booking`, `staff-booking`). After each feature's migration, both that feature's own tests and the full frontend suite MUST pass before moving to the next.
- Each feature's per-file `*ApiError` class body MUST be deleted once that feature is migrated — the old and new error-handling paths must not coexist for a given feature past its own migration step (per-feature atomicity, not "migrate the client, leave old classes as unused dead code everywhere").
- Three JWT realms exist (patient/staff/super-admin) with no shared session state between them — `apiClient.ts` must accept whichever token the calling feature already resolves (from its own realm's existing token-storage mechanism) rather than assuming a single global "the" token; it is a shared HTTP mechanism, not a shared auth-state store.
- This is a behavior-preserving refactor for every *successful* API call — the actual HTTP requests/responses/URLs must be unchanged. The one intentional behavior change is that error messages become correct (the dead-code bug fix) — this must be called out explicitly, not silently bundled as if it were a no-op.

## Acceptance Criteria
- Given `frontend/src/lib/apiClient.ts` after this feature, when any migrated feature's `api.ts` file is read, then it contains no direct `fetch()` call and no locally-defined `*ApiError` class — only a thin wrapper calling the shared client.
- Given a backend endpoint that returns a specific validation error message (e.g. "Fee override must be non-negative"), when a migrated feature's form submits invalid data and receives that response, then the UI displays the backend's actual specific message, not a generic fallback — demonstrated by re-testing at least one of the specific error cases this bug class affected.
- Given the full frontend test suite, when run after each feature's migration and after the full migration completes, then all tests pass with no reduction in test count (i.e., no tests were deleted to work around a migration difficulty).
- Given a manual spot-check in-browser of at least 2-3 migrated features' error paths, when an invalid request is submitted, then the specific backend message renders correctly — this is called out in the plan as a user-visible behavior change worth eyeballing, not just trusting the test suite (per the roadmap's own verification guidance).

## Dependencies
- None of the 39 converged features block this.
- Should happen before or alongside 046 (shared UI component library) since both are foundational frontend infrastructure the later UI redesign features (047-052) will build on — but there's no strict ordering requirement between 043 and 046 themselves.

## Explicitly Out of Scope
- Any change to which endpoints exist or what data they return — this is purely a client-side request/error-handling consolidation.
- Introducing a new HTTP library (e.g. axios, react-query) — the existing `fetch()`-based approach is kept, just consolidated into one shared implementation, per the constitution's "no unnecessary new dependencies" guidance (Principle II).
- Retry logic, request caching, or offline support — not currently present anywhere and not part of this feature's scope.

## Source References
- `PRODUCTION_ROADMAP.md` §1.3, §2 (Target Architecture — Frontend), §3 Phase 4
- `HANDOFF.md` Part 2 (original discovery of the `defaultMessageFor` dead-code bug, deferred as Minor at the time)
- Verified against current repository state via direct inspection, 2026-09-15 (no frontend/src/lib/ directory exists; 3 sampled api.ts files each independently hand-roll fetch/headers/error classes)
