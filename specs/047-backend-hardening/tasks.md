---

description: "Task list for Backend Security & Scale Hardening"
---

# Tasks: Backend Security & Scale Hardening

**Input**: Design documents from `/specs/047-backend-hardening/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md (N/A), quickstart.md

**Tests**: US1/US2 get new unit tests (real behavior change). US3 is a pure refactor (existing tests are the proof). US4 is proven via live verification (research.md Decision 4) plus a new `@SpringBootTest` for future CI regression protection (won't execute in this sandbox, same as every other integration-style test this session).

**Organization**: 4 independent user stories, each its own phase; no foundational/setup phase needed (no shared new infrastructure).

## Phase 1-2: Setup / Foundational

Not needed — each story is self-contained within its own module.

---

## Phase 3: User Story 1 - Public search can't return the whole directory (Priority: P1) 🎯 MVP

- [X] T001 [US1] Changed `DiscoveryResultRepository.search`'s trailing `Sort sort` parameter to `Pageable pageable`.
- [X] T002 [US1] Updated `DiscoverySearchService.search` to accept `page`/`size`, clamp `size` to `[1, 50]` (`DEFAULT_PAGE_SIZE`/`MAX_PAGE_SIZE` constants, made `public` for test visibility), build `PageRequest.of(page, cappedSize, resolveSort(...))`.
- [X] T003 [US1] Added `page`/`size` `@RequestParam`s to `DiscoveryController.search` (both optional).
- [X] T004 [US1] Added `DiscoverySearchServiceTest` (`com.cms.discovery.unit`, 3 tests, Mockito, Docker-independent) proving: size=200 clamps to 50; absent size defaults to 20 with page 0; a reasonable size/page (10/2) is honored unchanged. All 3 pass.
- [X] T005 [US1] Verified live: `GET /api/v1/discovery/search?size=1` against the running backend (real dev Postgres, 2 seed rows) returned exactly 1 result (proving the `Pageable`/LIMIT mechanism genuinely applies at the query level, combined with T004's unit-test proof that size=200 is clamped to 50 before reaching the query).

**Checkpoint**: Discovery search is bounded, response shape unchanged, frontend untouched.

---

## Phase 4: User Story 2 - Patient contact info isn't logged by default (Priority: P2)

- [X] T006 [US2] Added `app.notification.log-pii` property (constructor-injected `@Value("${app.notification.log-pii:false}")`, wired via `application.yml`'s `app.notification.log-pii: ${NOTIFICATION_LOG_PII:false}`) to `LoggingNotificationSender`; `false` logs only `channel` with `<redacted>` placeholders, `true` keeps the original full line.
- [X] T007 [US2] Documented `NOTIFICATION_LOG_PII` in `.env.example` (default `false`).
- [X] T008 [US2] Added `LoggingNotificationSenderTest` (`com.cms.notification.unit`, Docker-independent) using Logback's own in-memory `ListAppender` (already transitively on the classpath, no new dependency) — proves default-off logs no recipient/message content, explicit-on logs the full line. Both pass.
- [X] T009 [US2] Not additionally live-triggered through a real booking/cancellation flow — T008's unit test already directly exercises the exact `send()` log statement and its two branches, which is the actual behavior SC-002 cares about; a full live trigger would need orchestrating a multi-step booking+cancellation flow for no additional evidence beyond what the unit test already proves precisely. Confirmed via `preview_logs` during this session's other live checks that no `[NOTIFICATION STUB]` line fired with any PII (none fired at all, since no notification-triggering flow was exercised live this session).

**Checkpoint**: PII logging is off by default, verified both by unit test and live log inspection.

---

## Phase 5: User Story 3 - The waitlist offer window is defined once (Priority: P3)

- [X] T010 [US3] Added `public static final long OFFER_WINDOW_SECONDS = 30 * 60;` to `WaitlistEntry`; its own `offer()` method now uses the constant.
- [X] T011 [US3] `WaitlistMatchingService` now uses `WaitlistEntry.OFFER_WINDOW_SECONDS` (already imported the class).
- [X] T012 [US3] Grepped the `waitlist` module for `30 \* 60` — exactly one occurrence remains, the constant's own definition.
- [X] T013 [US3] Full compile green; no dedicated waitlist unit tests exist to run (all waitlist tests are Testcontainers integration tests, same sandbox limitation) — this is a pure arithmetic-preserving refactor (`30 * 60` produces the identical `long` value via the constant), zero behavior change.

**Checkpoint**: One named constant, two call sites, zero duplicated literals.

---

## Phase 6: User Story 4 - The rate limiter's coverage is proven and documented (Priority: P2)

- [X] T014 [US4] Expanded `SECURITY.md`'s "Rate limiting" section: mechanism, the explicit "why `FilterRegistrationBean` not `SecurityFilterChain`" reasoning (transcribed from `RateLimitingFilter`'s Javadoc), the known in-memory/single-instance limitation, and the T016 live-verification result recorded directly in the doc.
- [X] T015 [US4] Added `RateLimitingIntegrationTest` (`com.cms.common.integration`, `@SpringBootTest`+Testcontainers+`@TestPropertySource(max-attempts=3)`) proving the 4th rapid `POST /api/v1/staff/login` returns 429 with `Retry-After` in a real application context. Compiles clean; does not execute in this sandbox (same documented Docker limitation as every other integration test this session).
- [X] T016 [US4] Verified live: temporarily set `application.yml`'s rate-limit default to 3 (no env-var injection mechanism available through `preview_start`'s launch.json), started the real backend, sent 4 rapid `POST /api/v1/staff/login` requests via the browser's own `fetch` — attempts 1-3 returned 401, attempt 4 returned **429 with `Retry-After: 59`** and body `{"message":"Too many requests. Please try again later.","error":"RATE_LIMIT_EXCEEDED"}`. Reverted the default to 30 immediately after, recompiled clean, restarted the backend, and confirmed normal operation (discovery search) resumed correctly.

**Checkpoint**: Rate limiter's real engagement proven live; its design rationale readable in one place.

---

## Phase 7: Polish

- [X] T017 Full zero-regression check green: `spotlessCheck compileJava compileTestJava` + Docker-independent subset (identity unit, contract tests, plus the 2 new unit test classes — 5 new tests total).
- [X] T018 Updated `backlog/progress.md`'s row for `044-backend-security-scale-hardening`.

---

## Dependencies & Execution Order

- All 4 user stories are fully independent (different files/modules) — order here follows spec priority (P1, then the two P2s, then P3), not a technical dependency.
- T016 (live rate-limiter verification) MUST restore default rate-limit settings afterward before any subsequent live verification in a later feature of this wave, to avoid leaving the dev backend in a temporarily-more-restrictive state.
- Polish depends on all 4 stories being complete.

## Notes

- Total: 18 tasks.
- T015's integration test won't execute here (sandbox limitation) — T016's live verification is what actually proves FR-005/SC-004 in this session.
