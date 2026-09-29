# Research: Waitlist Matching (Longest-Waiting, Doctor/Specialization)

## R1: New `com.cms.waitlist` module

**Decision**: `WaitlistEntry` and every service/controller this feature adds live in a new
`com.cms.waitlist` package, depending on `com.cms.scheduling` (Session/Slot/Clinic/DoctorProfile
reads) and `com.cms.booking` (listening to `BookingCancelledEvent`) and `com.cms.notification`
(publishing offer notifications) — never the reverse.

**Rationale**: The project constitution names "waitlist" as one of this system's own intended
module boundaries, alongside scheduling, booking, clinical documentation, notifications, and
discovery (Principle III). This is the first feature to actually need that module to exist.

## R2: `WaitlistEntry.specialization` is only stored for specialization-only entries

**Decision**: `specialization` is nullable — populated only when `doctorProfile` is null
(specialization-only tier). For a doctor-match entry, the effective specialization for display or
any future need is `doctorProfile.getSpecialization()`, read live, not duplicated onto the entry.

**Rationale**: Storing a doctor's specialization redundantly on every doctor-match entry risks
staleness if a `DoctorProfile`'s specialization is ever edited (006's own existing edit path) and
serves no purpose the matching algorithm itself needs — tier 1 compares `doctorProfile` directly,
never `specialization`. Constitution II: no data stored beyond what a stated requirement needs.

**Alternatives considered**: Always store `specialization` on every entry, snapshotted at join
time (mirroring `Session`'s own snapshot-at-generation-time precedent, 015). Rejected — that
precedent exists specifically because a `Schedule` edit must never retroactively change an
already-generated `Session` (014's own non-retroactivity requirement); no analogous requirement
exists here, so the extra field would be pure redundancy with no stated need to snapshot against.

## R3: Matching triggers via `@TransactionalEventListener(phase = AFTER_COMMIT)`, mirroring 037

**Decision**: `WaitlistBumpListener.onBookingCancelled(BookingCancelledEvent)` is a
`@TransactionalEventListener(phase = AFTER_COMMIT)`, not a plain `@EventListener` — it only runs
once 025's cancellation transaction has actually committed, in its own separate transaction.

**Rationale**: Exactly 037's own precedent and reasoning for consuming `NotificationEventPublishedEvent`
the same way: matching-and-offering is a separate concern that shouldn't be coupled to the
cancellation transaction's own success/failure, and must only ever run against a durably-committed
cancellation — never a slot release that might still roll back.

**Alternatives considered**: A plain synchronous `@EventListener`, running inside 025's own
transaction. Rejected — would tie an unrelated concern's success to the cancellation's own commit,
and would search for a match against a Slot whose `OPEN` status isn't yet durable.

## R4: Matching query — two ordered lookups, first non-empty wins

**Decision**: `WaitlistEntryRepository` gains two derived queries:
`findFirstByClinic_IdAndDoctorProfile_IdAndStatusOrderByJoinedAtAsc` (tier 1) and
`findFirstByClinic_IdAndSpecializationAndDoctorProfileIsNullAndStatusOrderByJoinedAtAsc` (tier 2,
called only if tier 1 returns empty, with the newly-available slot's doctor's own
`specialization` as the search value).

**Rationale**: A direct, literal implementation of FR-004/FR-005/FR-006's strict tier order — tier
2 is never even queried unless tier 1 already returned nothing, structurally guaranteeing a
specialization-only entry can never outrank a doctor-match one (SC-001), rather than merging both
into one query with an `ORDER BY` that could subtly get the priority wrong.

## R5: The 30-minute claim window's expiry is owned by `WaitlistEntry` itself, not by `NotificationEvent`

**Decision**: `WaitlistEntry` gains `offeredAt` and `offerExpiresAt` (both nullable, set together
when matched: `offerExpiresAt = offeredAt.plus(30, MINUTES)`). The notification published
alongside it is informational only — 029 (the future claim feature) reads `WaitlistEntry`'s own
fields to enforce the window, not anything on `NotificationEvent`.

**Rationale**: The 30-minute claim window is this feature's own stated business fact (from 025's
original description: "offers it with a 30-minute claim window"), not a notification-delivery
concern. Coupling 029's future claim-window enforcement to `com.cms.notification`'s data would
invert this session's own established module-dependency direction (notifications are a sink,
consumed by upstream modules, never a source other modules read business state from).

## R6: Join validation — clinic and (if given) doctor-staffing existence checks only

**Decision**: `WaitlistJoinService.join(...)` verifies the `Clinic` exists and, when a
`doctorProfileId` is given, that the doctor is actively staffed there (reuses the same
`RoleAssignmentRepository` check 009/015 already established) — nothing more (no verification
status check, no duplicate-entry prevention).

**Rationale**: The spec's own Edge Cases explicitly leave duplicate joins unaddressed ("not a
stated requirement") and says nothing about clinic verification status for this action.
Constitution II: validate only what's actually required, not every check a stricter design might
someday want.

## R7: Two thin controllers (patient self-service, staff-on-behalf) over one shared join method

**Decision**: `PatientWaitlistController` (`POST /api/v1/patients/clinics/{clinicId}/waitlist`)
and `StaffWaitlistController` (`POST /api/v1/clinics/{clinicId}/waitlist`, taking an explicit
`patientAccountId`) both call the identical `WaitlistJoinService.join(...)`.

**Rationale**: Mirrors this session's now-consistent shape for every dual-audience action (027's
`QueuePositionService`, 028's `BookingCancellationService`) — one shared core, thin
audience-specific controllers handling only auth/identity resolution.
