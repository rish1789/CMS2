# Research: Patient Account & Global Login

This is not the first feature planned — the project's technical foundation was established by 001-clinic-registration. No new stack-level decisions are needed here; this file only records the decisions specific to this feature.

## Decisions

### Reuse of established stack

- **Decision**: Same stack as 001 — Java 21 / Spring Boot 3.x / Gradle / Flyway / PostgreSQL (backend), React 18 / TypeScript / Tailwind CSS / Vite (frontend), BCrypt password hashing, JUnit 5 + Spring Boot Test + Testcontainers (backend tests), Vitest + React Testing Library (frontend tests).
- **Rationale**: 001's plan.md established these as the project baseline; nothing about this feature's requirements calls for a different choice.
- **Alternatives considered**: None — re-litigating the stack per-feature would violate Constitution Principle II (no unjustified new complexity/decisions).

### Module boundary: separate package from staff identity

- **Decision**: Patient Account lives in its own package, `com.cms.patient.account`, entirely separate from `com.cms.identity` (which holds Clinic/staff Account/RoleAssignment from 001). Its own database table (`patient_account`), no foreign keys to or from `account`.
- **Rationale**: Spec FR-004/FR-008 and Constitution Principle III both require these to be genuinely separate identity systems with "no shared tables, no shared authentication logic." A separate package makes that boundary structurally enforced, not just a convention. The constitution's own multi-tenancy section explicitly names Patient Account as one of the intentional global-entity exceptions to clinic-scoping — this package boundary keeps that exception explicit rather than accidental (per the constitution's own instruction: "MUST be treated as such explicitly, not by omission").
- **Alternatives considered**: A shared `Account` table with a `type` discriminator (staff vs. patient) — rejected; it would violate the spec's explicit "no shared tables" requirement and make the staff/patient boundary a runtime check instead of a structural one.

### Session/token mechanism

- **Decision**: Stateless JWT issued on successful login, carrying only a Patient Account identifier and a fixed, non-staff audience/scope claim (so a Patient Account token structurally cannot satisfy a staff-only endpoint's authorization check — implements FR-008's "no crossover" requirement at the token level, not just by convention).
- **Rationale**: Constitution doesn't mandate a specific session mechanism; JWT is the standard fit for a Spring Security REST backend with a separate SPA frontend (as already used implicitly by 001's public-then-authenticated split). Distinct signing/audience from any future staff-token scheme keeps the two systems structurally unable to cross over, which is the strongest way to satisfy FR-008.
- **Alternatives considered**: Server-side session cookies — viable, but JWT better fits a stateless REST API consumed by a separate SPA and avoids introducing session-store infrastructure not otherwise needed yet.
