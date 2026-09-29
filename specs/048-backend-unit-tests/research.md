# Research: Backend Unit-Test Backfill

## Correction to prior assumption

`backend/src/test/java/com/cms/identity/unit/` exists as a directory but is **empty** (verified directly) — and `patient` has no `unit/` folder at all. The "identity/patient already have real unit-test content" belief carried in earlier features' progress notes this session was incorrect; what those features' "Docker-independent subset" runs actually executed was `*.contract.*`-pattern (`@WebMvcTest`) tests, which are genuinely Docker-independent but are not the same thing as pure-Mockito `unit/` tests. This doesn't invalidate any of this session's prior verification (the contract tests are real and did pass), but it means **this feature creates the first real unit-test content in this codebase**, not a continuation of an existing pattern with content — the `unit/` folder *naming convention* is documented in `CONTRIBUTING.md`, but no prior feature actually populated one.

## Decision 1: Entity construction strategy

**Decision**: Mock JPA entities directly with Mockito (`mock(AppointmentType.class)`, `mock(DoctorProfile.class)`, `mock(Booking.class)`, `mock(Slot.class)`, `mock(Session.class)`, `mock(Schedule.class)`) wherever the test needs to control what `getId()`/a relationship getter returns, rather than constructing real instances via their public constructors.

**Rationale**: Every entity's `id` field is `private`, Hibernate-generated, with no public setter — a real `new AppointmentType(doctorProfile, name, feeOverride)` would have `getId()` return `null` always, but the business logic under test repeatedly compares IDs (`appointmentType.getDoctorProfile().getId().equals(doctorProfileId)`). Mocking lets each test stub exactly the getter values the logic under test actually reads, which is both simpler and more precise than fighting reflection to set a private field on a real entity. None of these classes are `final`, so Mockito can mock them directly with no interface extraction needed.

**Alternatives considered**: Reflection-based ID injection (e.g. a test utility setting the private field via `Field.setAccessible`) — rejected as more machinery than four small test classes justify (Constitution Principle II); real entity construction plus accepting `getId() == null` — rejected, it would make the tests unable to actually exercise the ID-comparison branches the business logic depends on.

## Decision 2: Mocking `EntityManager`'s native query call (ScheduleService)

**Decision**: `ScheduleService.create`/`edit` call `lockDoctorForOverlapCheck`, which uses `entityManager.createNativeQuery(...).setParameter(...).getSingleResult()`. Mock `EntityManager` and a mocked `jakarta.persistence.Query`: `when(entityManager.createNativeQuery(anyString())).thenReturn(mockQuery); when(mockQuery.setParameter(anyString(), any())).thenReturn(mockQuery);` (`getSingleResult()`'s return value is never read by production code, no stub needed).

**Rationale**: This lets `create`/`edit` proceed past the (inherently real-database) advisory-lock call so the surrounding validation/overlap logic — the actual thing under test — still executes. The lock call's own real behavior (does Postgres actually serialize concurrent callers) is correctly left to the existing integration test suite, per spec.md's Edge Cases.

**Alternatives considered**: Extracting the lock call behind a separate, injectable interface just to make it mockable more cleanly — rejected as unjustified restructuring of already-converged, working code for this feature's narrow test-writing purpose (Principle II).

## Decision 3: `Slot`/`Session` construction for `NoShowDetectionService`

**Decision**: Mock `Slot` and `Session`, stubbing `slot.getSession()`, `session.getSessionDate()`, `slot.getStartTime()` to produce a controlled `LocalDateTime` relative to a fixed "now" — rather than trying to control real wall-clock time.

**Rationale**: `detectAndMarkNoShows` calls `LocalDateTime.now()` directly (not an injected `Clock`) — the test can't freeze "now", so instead it constructs slot/session mock data with a scheduled time far enough in the past/future *relative to the actual current time* to unambiguously land on either side of the 10-minute grace period (e.g. "2 hours ago" for the marked case, "2 minutes ago" for the not-yet-eligible case) — robust regardless of when the test actually runs.

**Alternatives considered**: Refactoring `NoShowDetectionService` to accept an injectable `Clock` (as `RateLimitingFilter` already does elsewhere in this codebase) purely to make this test cleaner — rejected per FR-006 (no behavior change to the class under test unless a genuine bug is found; this is a test-writing convenience, not a bug).
