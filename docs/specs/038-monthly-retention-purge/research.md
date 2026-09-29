# Phase 0 Research: Monthly Automatic Retention Purge

## R1: Retention-date anchor

**Decision**: Use `Booking.createdAt` (existing field, `Instant`, set at construction) as the sole anchor for the 3-year retention clock, uniform across Fixed-Time and Queue-mode bookings.

**Rationale**: Every `Booking` already carries this single well-defined timestamp regardless of mode, so no mode-specific branching (the kind 027/030 needed for "has this individual slot passed") is required here. That earlier fork existed for a fine-grained, time-of-day-sensitive question; retention eligibility is a coarse, multi-year question where a few days' difference between booking-creation and actual-visit date has no practical compliance effect. Confirmed reasonable under auto-mode guidance rather than raised as an interactive clarification, and recorded as an Assumption in spec.md.

**Alternatives considered**:
- Clinical content's own `createdAt` (per-entity) — rejected: would require three separate eligibility clocks (one per content type) instead of one per booking, contradicting the source material's explicit "tracked per booking" framing, and adds complexity with no compliance benefit.
- `Session.sessionDate` (visit date, Fixed-Time only) — rejected: not available uniformly for Queue-mode bookings without re-deriving the same mode-fork 027/030 already resolved for a different concern; `Booking.createdAt` is simpler and adequate at 3-year granularity.

## R2: Deletion mechanism — no new repository methods needed

**Decision**: Call `JpaRepository`'s already-inherited `deleteById`/`delete` directly from the new `RetentionPurgeService`, for `ConsultationNoteRepository`, `PrescriptionRepository`, and `ExternalRecordReferenceRepository`. No new repository methods are added to any of the three.

**Rationale**: None of the three repositories currently expose delete methods beyond `JpaRepository`'s own inherited capability (never previously called, since 030/031/032 are read/create-only). This is genuinely available infrastructure, not a gap to fill.

## R3: `Prescription`'s cascade must be extended to include REMOVE

**Decision**: Change `Prescription.items`' `@OneToMany` mapping from `cascade = CascadeType.PERSIST` to `cascade = {CascadeType.PERSIST, CascadeType.REMOVE}`.

**Rationale**: `PrescriptionItem` (035/031) deliberately has no independent repository — items have no lifecycle of their own apart from their parent `Prescription` (031 research.md R4). Deleting a `Prescription` via `prescriptionRepository.deleteById(...)` must cascade-delete its `PrescriptionItem` rows, or the delete will fail on the FK constraint. 031's original `PERSIST`-only cascade was justified by "nothing ever updates or deletes either" — a justification this feature is the first, foreseen exception to (031's own Dependencies section already named 034/038 as a downstream consumer). Adding `REMOVE` is minimal and behavior-preserving for 031's own existing scope (031 itself never calls delete, so nothing about its current behavior changes) — it only enables this feature's new, legitimate need. The alternative (a raw JDBC bulk-delete of `prescription_item` rows before deleting the parent `Prescription`, as the test fixtures already do for cleanup) is a workaround appropriate for test teardown, not production code, when idiomatic JPA cascade configuration is available and correct.

**Alternatives considered**:
- Raw JDBC/JPQL bulk delete of `prescription_item` by `prescription_id` before deleting `Prescription` — rejected for production code: works, but reintroduces manual ordering logic that JPA cascade already solves cleanly; reserved as the existing test-fixture pattern only.

## R4: Eligible-bookings query

**Decision**: Add one new derived/JPQL query to `BookingRepository`: `findRetentionEligibleBookings(Instant retentionCutoff): List<Booking>`, matching `b.patient.anonymizedAt IS NOT NULL AND b.createdAt < :retentionCutoff` — no status/slot filtering (unlike 033/037's "not yet occurred" queries), since retention eligibility has nothing to do with whether the booking is still active or in the future; a cancelled or completed booking's clinical content is just as subject to the retention window.

**Rationale**: Mirrors the existing `BookingRepository` query style (033/037's `findActiveFutureBookingsByClinic`/`existsActiveFutureBookingForPatient`), reached via `Booking.patient` (an already-established relationship — no new module dependency edge, since `com.cms.booking` already depends on `com.cms.patient.record`).

## R5: 3-year cutoff computation

**Decision**: Compute the retention cutoff as `LocalDate.now(clock).minusYears(3).atStartOfDay(ZoneOffset.UTC).toInstant()`, using `LocalDate.minusYears` (correct calendar-based arithmetic, handles leap years) rather than a raw `day * 1095` approximation.

**Rationale**: Correct and simple; the constant is defined once in `RetentionPurgeService` as `RETENTION_PERIOD = Period.ofYears(3)`, matching FR-006's "fixed, hardcoded system-wide constant, not clinic-configurable" requirement.

## R6: Purge service placement

**Decision**: `RetentionPurgeService` lives in `com.cms.clinical`, reading `com.cms.booking.BookingRepository` (already an established read dependency for this module, per 030/031/032) and `com.cms.patient.record.Patient` (via `Booking.getPatient()`, not a new direct repository dependency) for its own precondition checks, then deleting via the three clinical-content repositories it already owns.

**Rationale**: The module that owns what's being mutated/deleted is the natural home for the mutating logic, matching the session's established "service beside its data" convention. `com.cms.clinical → com.cms.patient.record` is consistent with the existing transitive dependency shape (`clinical → booking → patient.record`), not a reversal.

## R7: Automatic trigger — monthly `@Scheduled` cron

**Decision**: `RetentionPurgeTrigger` (`com.cms.clinical`), `@Scheduled(cron = "0 0 0 1 * *")` — midnight on the 1st of each month — mirroring `WaitlistExpirySweepTrigger`'s service/trigger split (032 research.md R6), just at monthly instead of per-minute cadence.

**Rationale**: Established, working pattern in this codebase for background sweeps; no reason to deviate.

## R8: Manual-trigger controller placement — Super Admin, `com.cms.identity.admin`

**Decision**: `RetentionPurgeController` (`POST /api/v1/admin/retention-purge/run`) lives in `com.cms.identity.admin`, alongside `ClinicVerificationController`/`DoctorVerificationController`, behind the existing `SuperAdminSecurityConfig` HTTP Basic Auth chain (`/api/v1/admin/**`).

**Rationale**: This is the opposite call from feature 037's own placement correction. 037's manual action was actually staff-JWT (Operations/ClinicAdmin) gated, so it was mistakenly first drafted in `com.cms.identity.admin` and corrected to `com.cms.patient.record`. This feature's manual trigger genuinely is Super-Admin-only per its own explicit business rule ("Super Admin is the only role that can manually re-trigger background jobs"), so `com.cms.identity.admin`'s existing Super-Admin-Basic-Auth chain is the *correct*, matching authentication mechanism here — confirmed deliberately, not by surface-level analogy to either prior case.

## R9: No event needed

**Decision**: No new domain event is published by the purge. It's a batch sweep with no cross-module side effect beyond the deletions themselves (no notification, no waitlist interaction, nothing else observes it).

**Rationale**: Constitution II (Simplicity & YAGNI) — an event with no consumer is unjustified complexity.

## R10: Response shape for the manual-trigger endpoint

**Decision**: `RetentionPurgeResultResponse { int purgedBookingCount }` — the count of bookings whose clinical content was purged by this run.

**Rationale**: Sufficient to confirm the action had an effect (SC-003), matching spec.md's Assumptions section; no richer reporting is required by the source material.
