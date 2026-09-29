# Research: Public Discovery Search

No `NEEDS CLARIFICATION` markers remain in Technical Context — every open question was resolved during Specify with documented reasoning (spec.md's Scope Decisions / Assumptions). This document records the resulting technical decisions.

## Decision: New JPQL query joins 007's `findDiscoveryEligible()` set against `RoleAssignment`, not a re-derivation of doctor eligibility

**Rationale**: `DoctorProfileRepository.findDiscoveryEligible()` (007) already returns exactly the doctor-side-eligible set (`licenseVerified = true AND visible = true AND` an active Role Assignment at a `verified` clinic exists), enforced as a query predicate with four passing integration tests. But it returns bare `DoctorProfile` rows — it doesn't tell the caller *which* verified clinic made a multi-clinic doctor eligible, which this feature needs (a doctor can hold Role Assignments at more than one clinic; only the verified ones should produce a result row, and the response must carry that specific clinic's identity). Rather than modifying `findDiscoveryEligible()`'s existing, tested contract (used unchanged by 007's own admin worklist and by three of its own gate tests), this feature adds one new query in its own module that expresses the same three-condition doctor gate plus the clinic-verified condition plus the optional text filter, joined through `RoleAssignment` to produce one row per eligible (doctor, clinic) pair:

```java
@Query("""
    SELECT new com.cms.discovery.DiscoveryResult(
        dp.id, a.name, dp.specialization, c.id, c.name, c.address)
    FROM RoleAssignment ra
    JOIN ra.account a
    JOIN ra.clinic c
    JOIN DoctorProfile dp ON dp.account = a
    WHERE ra.active = true
      AND ra.role = com.cms.identity.account.RoleAssignment.Role.Doctor
      AND c.verified = true
      AND dp.licenseVerified = true
      AND dp.visible = true
      AND (:q IS NULL OR :q = ''
           OR LOWER(dp.specialization) LIKE LOWER(CONCAT('%', :q, '%'))
           OR LOWER(a.name) LIKE LOWER(CONCAT('%', :q, '%'))
           OR LOWER(c.name) LIKE LOWER(CONCAT('%', :q, '%'))
           OR LOWER(c.address) LIKE LOWER(CONCAT('%', :q, '%')))
    """)
List<DiscoveryResult> search(@Param("q") String q);
```

This is the *same* logical conjunction `findDiscoveryEligible()` encodes (verified doctor-role check duplicated intentionally, not extracted into a shared fragment — Spring Data JPQL has no query-fragment reuse mechanism, and duplicating a four-line WHERE clause is far simpler than introducing a Specification/Criteria-API abstraction layer for a single caller, per Constitution Principle II), driven from the `RoleAssignment` side so the clinic identity is naturally part of the projection.

**Alternatives considered**:
- *Extend `findDiscoveryEligible()` to also return clinic identity* — rejected; it's an existing, tested, differently-shaped contract consumed by 007's admin worklist (which wants bare doctors, not clinic pairings); changing its return type is an unrelated-feature regression risk for no benefit, since this feature can express what it needs as its own query.
- *Fetch `findDiscoveryEligible()`'s doctors, then loop and query Role Assignments per doctor in application code* — rejected; that's response-stage assembly across N+1 queries, weaker than one data-layer query and explicitly against Principle IV's data-layer-enforcement requirement.
- *A Spring Data `Specification`/Criteria-API builder for the text filter* — rejected as premature abstraction (Principle II) for a single, simple OR-of-four-LIKEs filter with no plans for additional structured filters in this feature's scope.

## Decision: Doctor-centric result rows (one per eligible doctor+clinic pair), not clinic-grouped

**Rationale**: Per spec.md's Scope Decisions — every acceptance criterion is phrased as "that clinic/doctor" as a unit, and a doctor is only meaningful in the context of the specific verified clinic they're actually assigned to. A flat list of `DiscoveryResult` rows (doctor + their clinic's identity, denormalized per row) is the simplest shape that satisfies every acceptance scenario, including the multi-clinic edge case (US1 AC5's variant in Edge Cases: same doctor at two clinics, one verified — exactly one row for the verified clinic).

**Alternatives considered**: Clinic-grouped response (`[{clinic, doctors: [...]}]`) — rejected as unnecessary structural complexity (Principle II) for a v1 with no pagination/sorting; nothing in the spec requires grouping, and a flat list is trivially re-groupable client-side if a future feature needs that.

## Decision: New top-level `com.cms.discovery` module, own `SecurityFilterChain` at `@Order(5)`

**Rationale**: This is genuinely new territory — not an extension of `com.cms.identity` (which owns clinic/account/admin concerns) or `com.cms.patient` (patient identity/records) — and it reads across both `identity.clinic`/`identity.doctor`/`identity.account` tables without belonging to any single existing module, matching Constitution Principle III's "cohesive, independently testable modules with clear boundaries." Existing `SecurityFilterChain`s are `@Order(1)` (`/api/v1/clinics/**`), `@Order(2)` (`/api/v1/patients/**`), `@Order(3)` (`/api/v1/admin/**`), `@Order(4)` (`/api/v1/staff/**`) — `@Order(5)` for `/api/v1/discovery/**` is the next free slot, keeping every module's path prefix owned by exactly one explicit chain (no reliance on a shared `anyRequest().permitAll()` fallback), consistent with the existing per-module pattern.

**Alternatives considered**: Adding `/api/v1/discovery/**` as another `permitAll()` matcher inside the existing `/api/v1/clinics/**` chain — rejected; discovery is not clinic-registration/verification concern, and the existing chain is explicitly `securityMatcher("/api/v1/clinics/**")`-scoped, so a `/api/v1/discovery/**` path wouldn't even match it — a new chain is required, not optional.

## Decision: Frontend ships a search page in this feature, unlike 009's service-only precedent

**Rationale**: Unlike `009-patient-record-phone-linking` (whose only consumers, 017/018, don't exist yet, justifying a service-only contract under Principle III), this feature's consumer — an unauthenticated visitor using a search box — exists *right now*: nothing later needs to be built first for a person to use Discovery Search directly. Every other genuinely public-facing feature already shipped (`clinic-registration`'s `RegistrationForm`, `patient-account`'s `SignupForm`/`LoginForm`) ships its own frontend page in the same feature, and this is the platform's actual public entry point (spec User Story 1's "why this priority") — shipping only a backend contract here would leave the feature undemonstrable to an actual visitor.

**Alternatives considered**: Backend-only, deferring the UI to whichever future feature first needs to link into a booking flow (016/017/018) — rejected; discovery has direct standalone user value (browsing/searching) independent of booking, and the spec's own User Story 1/2 acceptance scenarios describe a public user searching, not a booking flow calling an internal API.
