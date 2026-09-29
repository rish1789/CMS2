# Research: Clinic Registration

This feature is the first one planned in the project. The constitution already fixes part of the stack (Java 21 / Spring Boot / Gradle / Flyway backend, Tailwind CSS frontend, multi-tenant). The gaps below — frontend framework, database engine, hosting target — are not fixed anywhere yet, so the decisions made here become the project's de facto baseline for every feature that follows.

## Decisions

### Frontend framework

- **Decision**: React 18 (with Tailwind CSS) as a single-page application consuming the backend's REST API.
- **Rationale**: The constitution mandates Tailwind CSS for styling but names no JS framework. The broader system architecture (per the source Business Design Document) exposes a REST/WebSocket/SSE surface from the Spring Boot backend — implying a client-rendered SPA rather than server-rendered pages, since later features (Unified Inbox, live delay tracking, self-service waitlist claim) need real-time, stateful UI. React is the most common, best-supported pairing with Tailwind and has the largest ecosystem for the component-heavy staff/patient UI this system will need across many future features.
- **Alternatives considered**: Thymeleaf (server-rendered) — rejected; doesn't fit the REST/WebSocket/SSE architecture and would need a second approach later for real-time features. Vue/Svelte — comparably good Tailwind support, but no advantage over React for this scope; no reason to prefer them.
- **Project-wide precedent**: yes — flagged for user review.

### Database engine

- **Decision**: PostgreSQL.
- **Rationale**: The constitution requires Flyway migrations and, via Principle IV, DB-level uniqueness constraints that close race conditions (this feature's FR-012/FR-013 require platform-wide-unique email and staff code). PostgreSQL is the standard, mature choice for Spring Boot + Flyway + Spring Data JPA, with solid support for unique/partial indexes.
- **Alternatives considered**: MySQL — comparable, but PostgreSQL's stricter constraint semantics fit this domain better. H2 — fine for fast unit tests only, not production or integration tests (which must exercise real constraint behavior).
- **Project-wide precedent**: yes — flagged for user review.

### Target platform / hosting

- **Decision**: Linux container (Docker), deployable to any standard container host.
- **Rationale**: Portable default for a Spring Boot service; nothing in the spec or constitution points elsewhere.
- **Alternatives considered**: None seriously evaluated — no stated constraint favors a different target.

### Password hashing

- **Decision**: BCrypt via Spring Security's `PasswordEncoder`.
- **Rationale**: Constitution Principle IV requires integrity-by-design for identity records. BCrypt is natively supported by Spring Security and is the standard choice; nothing in this feature's scope justifies a heavier alternative.
- **Alternatives considered**: Argon2 — stronger, but an unjustified extra dependency for this scope (Constitution Principle II: no speculative complexity).

### Testing frameworks

- **Decision**: JUnit 5 + Spring Boot Test + Testcontainers (PostgreSQL) for the backend; Vitest + React Testing Library for the frontend.
- **Rationale**: Standard for the chosen stack. Testcontainers specifically lets integration tests exercise the real atomicity and uniqueness constraints this feature depends on — Constitution Principle I requires a test proving an invariant holds *and* proving the prior violating state (e.g. a duplicate email) is rejected, which an in-memory mock can't verify.
- **Alternatives considered**: Mock-only unit tests without Testcontainers — rejected; can't verify DB-level invariants.

## Resolved Technical Context

All `NEEDS CLARIFICATION` markers from the plan's Technical Context are resolved by the decisions above.
