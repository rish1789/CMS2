# 044 — Backend Security & Scale Hardening

**Module:** Cross-Cutting / Backend Reliability
**Status:** Ready for spec-kit intake

## User Story
As a clinic operator relying on CMS2 in production, I want the public search endpoint protected from unbounded result sets, patient contact information kept out of application logs by default, and existing safety mechanisms (rate limiting) verified to actually cover what they're meant to cover, so that the system behaves safely under real-world load and doesn't leak sensitive data through logs.

## Context
A prior session's audit (`PRODUCTION_ROADMAP.md` Phase 5, and this session's direct verification on 2026-09-15) identified several small, independent hardening gaps:

1. **Discovery search is fully unbounded.** `DiscoveryController.search()` returns `List<DiscoveryResult>` with no page/size parameters, and `DiscoveryResultRepository.search(...)` has no `Pageable`/`Page<>`/LIMIT anywhere — a public, unauthenticated endpoint (035-public-discovery-search) that returns every matching row regardless of how many clinics/doctors exist.
2. **`LoggingNotificationSender` logs PII unconditionally at INFO level.** `LoggingNotificationSender.send()` logs `recipient` and `message` content directly (`LoggingNotificationSender.java:19`) with no debug-only gate — this is 037's log-only notification stub, and in a real deployment this means patient contact info and message bodies land in application logs by default.
3. **A duplicated magic number.** `30 * 60` (the waitlist-offer expiry window in seconds) is hardcoded independently in both `WaitlistEntry.java:134` and `WaitlistMatchingService.java:62` with no shared named constant — a future change to this window requires remembering to update both places.
4. **Rate limiting exists but its coverage should be verified and documented.** `RateLimitingFilter`/`RateLimitingConfig` (`com.cms.common`) already implement a hand-rolled fixed-window limiter registered via `FilterRegistrationBean` for exactly `/api/v1/staff/login`, `/api/v1/patients/login`, `/api/v1/patients/signup`, `/api/v1/clinics/register`, with a test asserting a 4th rapid attempt returns 429. It is deliberately not wired into any `SecurityFilterChain` (registered at the servlet-filter level instead, which still applies regardless of security chain). This appears functionally correct but was built in an undocumented session with no HANDOFF.md record — this feature should add test coverage confirming it actually engages end-to-end against a running app (not just the existing unit test) and document why it's a `FilterRegistrationBean` rather than part of a `SecurityFilterChain`.

## Business Rules
- The discovery search endpoint MUST accept `page`/`size` query parameters (or equivalent) with a hard-capped maximum page size, defaulting to a sane page size if none is specified — this must not break existing callers of 035's contract in a way that changes its currently-tested response shape without a documented, deliberate decision (check `DiscoverySearch.tsx` frontend usage and 035's existing contract tests before finalizing the shape).
- `LoggingNotificationSender`'s recipient/message logging MUST be gated behind an explicitly off-by-default flag (e.g. a Spring property), so production deployments don't leak PII into logs unless a developer deliberately opts in for local debugging.
- The `30 * 60` literal MUST be consolidated into one named constant in `com.cms.waitlist`, referenced by both `WaitlistEntry` and `WaitlistMatchingService` — no behavior change, purely a maintainability fix.
- The rate limiter's existing behavior (which 4 endpoints it covers, its window/threshold values, why `FilterRegistrationBean` not `SecurityFilterChain`) MUST be documented (in 042's security posture doc, if that feature has landed, or inline as Javadoc/comments if not) and covered by at least one additional test proving it engages against the real running application context (not just the isolated filter unit test that already exists).

## Acceptance Criteria
- Given a discovery search matching more results than the page-size cap, when queried without pagination parameters, then the response returns only the capped/default page size, not every matching row.
- Given `LoggingNotificationSender` with the new flag left at its default (off), when a notification is sent, then no recipient contact info or message body appears in application logs — confirmed via a test asserting the log output at INFO level excludes that content when the flag is unset.
- Given the waitlist-expiry constant, when `WaitlistEntry` and `WaitlistMatchingService` are inspected after this change, then both reference the same single named constant — no literal `30 * 60` (or equivalent) remains duplicated.
- Given 5 rapid login attempts against `/api/v1/staff/login` in a real (non-unit-test-mocked) Spring context, when the 5th attempt is made within the configured window, then it is rejected with 429 — proving the filter engages in a realistic integration scenario, not just its existing isolated unit test.

## Dependencies
- Builds on 035-public-discovery-search (existing, converged) — must not regress its existing contract tests.
- Builds on 036/037 (notification pipeline/delivery stub, existing, converged) — must not regress their existing tests.
- Builds on 028/029 (waitlist matching/claim, existing, converged) — must not regress their existing tests.
- Independent of 040-043; can run in parallel with them, but logically belongs in the same hardening phase per the user's chosen sequencing.

## Explicitly Out of Scope
- Introducing Bucket4j or replacing the existing hand-rolled rate limiter — it already works and has a passing test; this feature only adds verification/documentation, not a rewrite (per constitution Principle II, no unjustified new complexity).
- Rate-limiting any endpoint beyond the 4 already covered — expanding coverage is a separate, explicitly-scoped decision if ever needed.
- Full structured/centralized logging infrastructure (e.g. ELK, log aggregation) — only the one PII-logging gap in `LoggingNotificationSender` is in scope.

## Source References
- `PRODUCTION_ROADMAP.md` §3 Phase 5 (items 1-4), §4 (OWASP Top 10 — A04, A09)
- Verified against current repository state via direct inspection, 2026-09-15 (confirmed unbounded discovery query, confirmed unconditional PII logging, confirmed duplicated literal, confirmed rate limiter exists and is registered via FilterRegistrationBean for the 4 named paths with a passing 429-on-4th-attempt test)
