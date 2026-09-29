# Research: Backend Security & Scale Hardening

## Decision 1: Discovery pagination shape

**Decision**: `DiscoveryController.search` gains optional `page` (default 0) and `size` (default 20, hard-capped at 50) request params; `DiscoverySearchService.search` clamps `size` to `[1, 50]` and builds `PageRequest.of(page, cappedSize, sort)`; `DiscoveryResultRepository.search`'s `@Query` method signature changes its trailing `Sort sort` parameter to `Pageable pageable` (Spring Data JPA applies LIMIT/OFFSET automatically for a `@Query` method with a `Pageable` parameter returning `List<T>` — no `Page<T>`/count-query overhead needed since total count isn't used anywhere).

**Rationale**: Keeps `DiscoveryResult[]` as the response shape (verified: `frontend/src/features/discovery/api.ts`'s `searchDiscovery` returns `Promise<DiscoveryResult[]>`, no pagination metadata read anywhere) — zero frontend changes needed, directly satisfying the constraint that this fix stays a safety cap, not a UX change nobody asked for.

**Alternatives considered**: Returning `Page<DiscoveryResult>` with metadata — rejected, would break the existing frontend contract for no stated benefit (no pagination UI was requested); a hard `LIMIT` in raw SQL instead of `Pageable` — rejected, `Pageable` is Spring Data's idiomatic mechanism and composes cleanly with the existing `Sort`.

## Decision 2: Notification PII log gate

**Decision**: A new `app.notification.log-pii` boolean property (default `false`, documented in `.env.example` as `NOTIFICATION_LOG_PII`), injected into `LoggingNotificationSender` via `@Value`. When `false`, the log line omits `recipient`/`message`, logging only `channel` and a redacted placeholder; when `true`, the existing full line is unchanged.

**Rationale**: Matches FR-002/SC-002 exactly — off by default, a real opt-in still available for local debugging. Mirrors this project's own established pattern for other opt-in/fail-safe flags (JWT secrets, Super Admin credentials — unset means the safe behavior, not the convenient one).

**Alternatives considered**: Removing the recipient/message logging entirely — rejected, the debugging value is real (037's own design intent is "log-only" as its entire purpose) and the spec's own acceptance scenario 2 requires the capability to still exist when explicitly enabled.

## Decision 3: Waitlist expiry constant location

**Decision**: `public static final long OFFER_WINDOW_SECONDS = 30 * 60;` defined once in `WaitlistEntry` (the domain entity that owns the offer-expiry concept and already has the first of the two call sites), referenced by `WaitlistMatchingService` as `WaitlistEntry.OFFER_WINDOW_SECONDS`.

**Rationale**: `WaitlistEntry` is the natural owner (the constant describes an invariant of the entity's own lifecycle, not the matching service's logic) — matches this codebase's general pattern of domain entities owning their own business constants.

**Alternatives considered**: A new dedicated constants class — rejected as unjustified complexity (Principle II) for one constant with exactly two call sites, both already in the `waitlist` module.

## Decision 4: Rate limiter verification approach

**Decision**: Live-verify via `preview_start` (this sandbox's established real-backend-with-real-Postgres workaround, already used successfully in 045/046) — make 5 rapid `POST /api/v1/staff/login` requests with invalid credentials and confirm the 5th (exceeding the default `max-attempts: 30`... ) — actually: since the default threshold is 30/60s, verification will temporarily lower it via an env override for the live check only (`RATE_LIMIT_MAX_ATTEMPTS=3`), confirming the 4th rapid request returns 429 with a `Retry-After` header, then restore normal operation. This is real, non-mocked, non-unit-test-isolated proof (FR-005/SC-004) — stronger than a Testcontainers integration test this sandbox couldn't run anyway, and doesn't require waiting for a real CI environment.

**Rationale**: `RateLimitingFilterTest` (the existing isolated filter unit test) already proves the filter's own logic works given direct method calls; what's never been proven is that it actually engages when wired into the real running Spring Boot application via `FilterRegistrationBean` — exactly the gap FR-005 names. A live HTTP-level test against the real app closes that gap directly.

**Alternatives considered**: Writing a `@SpringBootTest` integration test — still valuable for CI regression protection, so it's added anyway (see tasks.md), but per this project's own established, repeatedly-confirmed limitation, it cannot be *executed* in this sandbox (needs a full app context wired against a real datasource) — the live verification is what actually proves FR-005 today, the automated test is what proves it stays true in future CI runs.

## Decision 5: SECURITY.md expansion content

**Decision**: Expand the existing "Rate limiting" section with: the fixed-window-per-client-IP mechanism (already summarized), the explicit "why `FilterRegistrationBean` not `SecurityFilterChain`" reasoning (transcribed from `RateLimitingFilter`'s own Javadoc: avoids auto-registration into unrelated `@WebMvcTest` slices and avoids touching the already-delicate `SecurityConfig` bean-naming history), and a note on its single-instance/in-memory scope limitation (documented in the same Javadoc as a known, accepted constraint for the current single-backend-instance deployment shape).

**Rationale**: All the source material already exists in code comments — this is a transcription/consolidation task, not new design work, matching FR-004's actual ask ("documented in one place").
