# Research: Recurring Schedule Definition

## Decision: Reuse `RoleAssignmentRepository.existsByAccount_IdAndClinic_IdAndRoleAndActiveTrue` for both authorization and the staffing gate

**Rationale**: This method (004) already generalizes over `role`, so it directly answers both questions this feature needs: "is the caller an active ClinicAdmin at this clinic?" (FR-001/FR-003) and "does the named doctor have an active Doctor-role assignment at this clinic?" (FR-009) — no new repository method, no new authorization abstraction. `ScheduleService.create()` mirrors `StaffOnboardingService.onboard()`'s existing ordering: check the caller's own standing first (`ForbiddenException`, 403), then validate the request body, then check the target doctor's staffing relationship.

**Alternatives considered**: A new `@PreAuthorize`/method-security-based check — rejected; this codebase has no method-security infrastructure anywhere (every existing per-path-variable authorization check is a plain `if` in the service layer, per `identity.account.SecurityConfig`'s own javadoc explaining why: "a per-path-variable authorization rule isn't expressible as a static security-config matcher"), so introducing one now would be inconsistent with the established pattern for no added benefit (Principle II).

## Decision: `daysOfWeek` as an `@ElementCollection<DayOfWeek>` with its own join table

**Rationale**: A `Schedule` legitimately has a one-to-many relationship with days (spec Scope Decisions: one record, multiple days). `java.time.DayOfWeek` is a natural fit for the "day of the week" concept and needs no custom enum. `@ElementCollection` (not a full child entity) is the right weight — days have no independent identity or fields of their own, just a set membership, exactly what `@ElementCollection`/`@CollectionTable` is for.

**Alternatives considered**: A comma-separated `VARCHAR` column — rejected; ad-hoc string encoding of structured, queryable data (a future feature may need to query "which schedules run on Monday") is worse than a real join table for equivalent effort, and this codebase doesn't use string-encoded collections anywhere else. A `Set<DayOfWeek>` stored as a Postgres array/`bit` column — rejected as a less portable, less conventional Hibernate mapping than the standard element-collection table for no benefit here.

## Decision: `slotIntervalMinutes` is a nullable `Integer` column, enforced by application-level validation, not a DB constraint

**Rationale**: The FIXED_TIME-requires-value / QUEUE-forbids-value rule (FR-007/FR-008) is a cross-column conditional invariant depending on `mode`. Postgres supports this via a `CHECK` constraint, but this codebase's existing convention for similar mode-dependent field rules (e.g. 007's `licenseVerified`/`visible` conjunction, evaluated in `findDiscoveryEligible()`, not a DB constraint) is to enforce cross-field business rules in the service layer with a dedicated exception, reserving DB-level constraints (`UNIQUE`, `NOT NULL`, partial indexes) specifically for concurrency-sensitive/race-prone invariants (Constitution Principle IV's actual scope: "concurrency-sensitive operations that create or match identity records"). This rule isn't concurrency-sensitive — a single request either satisfies it or doesn't, with no race to close — so a service-layer check (`InvalidScheduleException`, 400) is the appropriately-weighted choice (Principle II), while still being enforced before any write per plan.md's Constraints.

**Alternatives considered**: A Postgres `CHECK` constraint mirroring the service-layer rule — rejected as redundant belt-and-suspenders for a non-concurrency-sensitive, single-request-scoped rule; every other similarly-shaped conditional field rule in this codebase (007/008) is enforced the same way, at the service layer only.

## Decision: New top-level `com.cms.scheduling` module

**Rationale**: Constitution Principle III names "scheduling/session generation" as its own worked example of a module boundary, distinct from booking/waitlist/clinical-documentation/notifications/discovery — this is the first feature to need it, so it's created fresh, reading `Clinic`/`DoctorProfile`/`RoleAssignment` as read-only cross-module references exactly like `com.cms.discovery` and `com.cms.notification` already do.

**Alternatives considered**: Folding `Schedule` into `com.cms.identity` (since it references `Clinic`/`DoctorProfile`/`RoleAssignment` so heavily) — rejected; `identity` is about who accounts/clinics/doctors *are*, not the scheduling patterns built on top of them, and Constitution III explicitly calls out scheduling as its own bounded context.

## Decision: This feature ships a frontend form, unlike 009's/011's service-only precedent

**Rationale**: Its two callers (a ClinicAdmin, or a Doctor) are real, already-logged-in actors today (`StaffLoginForm`/`OnboardStaffForm` already exist) — unlike 009 (whose only consumers, 017/018, don't exist yet) or 011 (whose only consumers are future booking/waitlist features). Every other feature in this backlog with a real, already-authenticated caller has shipped a matching frontend page (`RegistrationForm`, `PendingClinicsList`, `PendingDoctorsList`, `OnboardStaffForm`/`DeactivateStaffAction`) — following that established pattern here, rather than 009's exception-case reasoning, is the consistent choice.

**Alternatives considered**: Backend-only, deferring the UI to 011/017/018 — rejected; those features consume *generated Sessions*, not the Schedule-definition act itself, so deferring the UI to them would leave the actual feature (a ClinicAdmin/Doctor defining a schedule) undemonstrable, unlike 009/011 where deferring genuinely had no better alternative.
