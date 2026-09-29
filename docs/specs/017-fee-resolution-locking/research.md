# Research: Fee Resolution & Locking at Booking Time

## Decision: A new `/api/v1/doctors/**` `SecurityFilterChain`, `@Order(6)`

**Rationale**: This codebase's `FilterChainProxy` dispatches each request to the first declared `SecurityFilterChain` whose `securityMatcher` matches its path. A path matching *none* of them bypasses Spring Security's filter chain entirely — not "permitAll," but genuinely unfiltered — which is exactly the silent-gap risk `com.cms.identity.account.SecurityConfig`'s own javadoc has repeatedly flagged for every extension to its `/api/v1/clinics/**` chain. Since `AppointmentType`/`DoctorDefaultFee` are doctor-scoped, not clinic-scoped (spec Scope Decisions), their natural path (`/api/v1/doctors/{doctorProfileId}/...`) doesn't overlap `/api/v1/clinics/**` at all, so extending that chain isn't an option — a new, genuinely separate chain is required, mirroring `com.cms.identity.account.SecurityConfig`'s own `staffAuthFilterChain` (`/api/v1/staff/**`, `@Order(4)`) precedent for "a non-overlapping path gets its own chain." Reuses the exact same `StaffJwtAuthenticationFilter`/`StaffJwtService`/`StaffAuthenticationEntryPoint` beans (already registered, injectable from any package) rather than a new auth mechanism.

**Alternatives considered**: Nesting these endpoints under `/api/v1/clinics/{clinicId}/doctors/{doctorProfileId}/...` (reusing the existing chain) — rejected; the authorization rule itself ("ClinicAdmin at *any* clinic this doctor works at," not one specific clinic) doesn't naturally fit a single-clinic-scoped path, and forcing a `clinicId` into the URL for a resource that isn't actually clinic-scoped would be a misleading contract.

## Decision: `AppointmentType`/`DoctorDefaultFee` authorization reuses `RoleAssignmentRepository`, extended with one new "any clinic" query

**Rationale**: FR-005/FR-006 require "ClinicAdmin at *any* clinic where the Doctor is actively staffed," not a single named clinic (unlike 009/013's clinic-anchored authorization). This needs one new repository method, `findByAccount_IdAndRoleAndActiveTrue(UUID accountId, Role role)`, to first find every clinic the target Doctor is actively assigned to, then check (via the existing `existsByAccount_IdAndClinic_IdAndRoleAndActiveTrue`) whether the caller is an active ClinicAdmin at any one of them. Both steps reuse existing/minimally-extended repository infrastructure — no new authorization abstraction.

**Alternatives considered**: A single custom `@Query` doing both steps in one round-trip (a join between the caller's ClinicAdmin assignments and the doctor's Doctor assignments on a shared clinic) — rejected as premature optimization (Principle II) for a check that runs once per request against a small, per-doctor clinic count; the two-query version is more obviously correct and easier to test in isolation.

## Decision: `FeeResolutionService.resolve` requires a non-null `appointmentTypeId`

**Rationale**: Spec Scope Decisions — every acceptance scenario in the source material is phrased "for that appointment type"; there is no described "no appointment type" scenario. Making it required now avoids inventing an unspecified "what happens with no appointment type" behavior that 016/017/018 would then have to work around or reinterpret once they're built.

**Alternatives considered**: An optional `appointmentTypeId`, falling straight to the doctor's default fee when absent — rejected as speculative; nothing in the source material describes a booking with no appointment type at all.

## Decision: `BigDecimal(precision=10, scale=2)` for every monetary field

**Rationale**: The standard, correct Java representation for currency — exact decimal arithmetic, no floating-point rounding error. No monetary field exists anywhere else in this codebase yet, so this feature establishes the pattern other features (016/017/018, and 015's own "locked fee" once a Booking entity exists) are expected to follow.

**Alternatives considered**: An integer minor-unit (paise) column — rejected; while also correct, `BigDecimal` is the more common, more directly-readable convention for a "flat cash amount" field with no unit-conversion need described anywhere in the source material, and avoids every call site needing to remember a ×100/÷100 convention.

## Decision: New top-level `com.cms.booking` module

**Rationale**: The backlog source file itself labels this feature's module as "Booking," distinct from "Scheduling & Session Generation" (009-016) — and Constitution III separately names "booking" as its own worked-example module boundary. Reads `DoctorProfile`/`RoleAssignment` as read-only cross-module references, exactly like every other module boundary decision made so far in this backlog (discovery/notification/scheduling all read `identity` read-only).

**Alternatives considered**: Folding this into `com.cms.scheduling` (since it's adjacent to booking-adjacent concerns) — rejected; the backlog's own module taxonomy and Constitution III both treat "booking" as distinct from "scheduling," and this feature's entities (`AppointmentType`, `DoctorDefaultFee`) have nothing to do with recurring time patterns.
