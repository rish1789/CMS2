# Research: Super Admin Clinic Verification

Reuses the established stack from 001/002 (Java 21/Spring Boot/PostgreSQL/React/Tailwind). Only new decisions specific to this feature are recorded here.

## Decisions

### Super Admin authentication mechanism

- **Decision**: A dedicated Spring Security filter chain (`@Order(3)`, after 001's `/api/v1/clinics/**` and 002's `/api/v1/patients/**`) guarding `/api/v1/admin/**`, using HTTP Basic Auth checked against a single credential pair read from configuration (`admin.super-admin.username` / `admin.super-admin.password`, sourced from environment variables — never hardcoded, never persisted to any table, per the spec's Clarifications and FR-009).
- **Rationale**: Directly implements the spec's resolved decision (Option C — no database-backed identity). Basic Auth is the simplest stateless mechanism that satisfies "re-validated every request, no session/token infrastructure."
- **Alternatives considered**: None — the mechanism was already decided during Specify; this just names the concrete Spring Security shape.

### Module placement

- **Decision**: New package `com.cms.identity.admin` (not a new top-level module) — reuses 001's existing `ClinicRepository` directly, since this feature only reads/writes `Clinic.verified`, a field already owned by 001's `Clinic` entity.
- **Rationale**: Unlike Patient Account (002), there's no separate stored identity here requiring a module boundary (FR-004/FR-008 don't apply to this feature). This is administrative behavior on top of an existing entity, so it belongs alongside it rather than forcing a new top-level module for two endpoints.
- **Alternatives considered**: A separate `com.cms.admin` top-level module — rejected as unjustified extra structure (Constitution Principle II) for a feature with no entity of its own.

### De-verification cascade trigger (FR-008)

- **Decision**: Publish a Spring `ApplicationEvent` (`ClinicDeVerifiedEvent`, carrying the clinic ID) when `verified` transitions `true → false`. This feature has no listener for it — 008-deverification-cascade-auto-cancel-bookings will add its own `@EventListener` later.
- **Rationale**: Directly implements the constitution's Principle III guidance: "Cross-module communication for asynchronous effects ... MUST go through explicit, event-driven interfaces, not direct reach-through into another module's internals." This is the same shape the spec itself describes: "this feature owns only the toggle and the triggering of that process, not the cascade's own behavior."
- **Alternatives considered**: A direct call into a (not-yet-existing) cascade service — rejected; would create a forward dependency on unbuilt code and couple this feature to 008's internals.
