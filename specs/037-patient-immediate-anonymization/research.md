# Research: Patient Immediate Anonymization

## R1: Everything lives in `com.cms.patient.record`, controller included

**Decision**: `Patient`'s extension, the two new exceptions, `PatientAnonymizationService`, and
`StaffPatientAnonymizationController` all live in `com.cms.patient.record`.

**Rationale**: This package already owns `Patient`/`PatientRepository`. This session's dominant
placement pattern keeps a feature's controller beside its own service (`com.cms.clinical`,
`com.cms.waitlist`) rather than splitting them across packages; `com.cms.identity.admin`'s own
existing controllers (`ClinicVerificationController`/`DoctorVerificationController`) are
authenticated via a completely different mechanism (Super Admin HTTP Basic Auth, not the staff
JWT chain this feature actually uses), so placing this feature's controller there would be
inconsistent with what that package's own contents actually mean.

## R2: "Active future booking" reuses 033/deverification-cascade's own Slot-status-based definition

**Decision**: `BookingRepository` gains `existsActiveFutureBookingForPatient(UUID patientId):
boolean` — `status = ACTIVE AND slot.status = BOOKED`, scoped by `patient.id` instead of
clinic/doctor, no calendar-date comparison.

**Rationale**: 033-deverification-cascade already established this exact definition
("not-yet-occurred" = the Slot hasn't reached a resolved outcome) for the identical underlying
concept — reapplying it here, scoped to one patient, is simpler and more consistent than
re-deriving a parallel date/time boundary a third time.

## R3: New `PatientNotFoundException` in `com.cms.patient.record` — not reused from `com.cms.booking`

**Decision**: A fresh exception class, distinct from `com.cms.booking.PatientNotFoundException`.

**Rationale**: `com.cms.booking` already depends on `com.cms.patient.record` (`Booking` →
`Patient`), not the reverse. Reusing `com.cms.booking`'s exception from `com.cms.patient.record`
would point that dependency backward — the same "distinct exception per module, even with an
identical name" precedent already established for `SlotNotFoundException`
(`com.cms.booking`/`com.cms.scheduling`).

## R4: `Patient` gains `anonymizedAt` (nullable `Instant`) and an `anonymize()` mutator

**Decision**: A new nullable `anonymized_at` column. `Patient.anonymize()` sets `name` to a fixed
placeholder (`"Anonymized Patient"`), sets `phone` to `null`, and stamps `anonymizedAt = now()` —
only when `anonymizedAt` is still null (idempotent, FR-007); a repeated call is a no-op that
changes nothing, including the timestamp.

**Rationale**: Spec FR-004/FR-007 — a durable, queryable marker 034 can check directly, doubling
as an audit record of when the action happened; the idempotent guard on the mutator itself (not
just the service layer) makes "already anonymized" structurally safe to call twice, mirroring
`ClinicVerificationService.unverify`'s own idempotent-transition shape.

## R5: No data-layer race-closure needed for the block-on-active-booking check

**Decision**: `PatientAnonymizationService.anonymize` reads
`existsActiveFutureBookingForPatient` and rejects synchronously if `true`; no conditional
`@Modifying` update guards this specific check.

**Rationale**: This is a read-then-write within a single transaction, not a duplicate-creation or
double-transition race the way `cancelIfActive`/`offerIfWaiting` guard against — the worst case
of a genuine concurrent race (a booking created in the same instant anonymization runs) is an
extremely narrow window with no stated requirement to close it at the data layer, and no existing
precedent in this codebase treats "read one boolean precondition, then write" as needing a
conditional-update guard when the write itself has no competing writer to race against (unlike
`WaitlistEntry.offerIfWaiting`, where two listeners could race the *same* row).

## R5a: The block-check itself is not raced against a concurrent booking-creation transaction (accepted limitation)

**Decision**: No lock or data-layer guard closes the narrow window between
`existsActiveFutureBookingForPatient`'s read and the anonymize write — a booking created for this
same patient in a separate, concurrent transaction between those two steps could theoretically
result in an anonymized patient who also holds a brand-new active booking.

**Rationale**: Distinct from R5 (the anonymize-vs-anonymize race, which is harmless either way).
This one is a genuine, if extremely narrow, race — but closing it would require locking the
Patient row (or the patient's booking set) for the duration of an unrelated booking-creation flow
across two different features, a materially larger cross-cutting change with no stated
requirement to justify it, and a real-world likelihood (a staff member choosing to anonymize
*exactly* as a wholly unrelated booking for that same patient is independently created,
within the same sub-second window) low enough that no existing feature in this codebase has ever
closed an analogous cross-feature timing gap without an explicit spec requirement to do so.
Documented here rather than silently ignored, consistent with this session's practice of
surfacing — not necessarily closing — every race considered.

## R6: Placeholder name is a fixed constant, `"Anonymized Patient"`

**Decision**: A single hardcoded string, not a templated or per-patient-unique placeholder.

**Rationale**: The spec's own wording ("a placeholder value") doesn't require uniqueness or any
particular format; a fixed constant is the simplest choice that satisfies "the name is no longer
identifying" (Constitution II).

## R7: Operations-or-ClinicAdmin write-action gate — mirrors this session's own standard

**Decision**: `StaffPatientAnonymizationController` requires an active `Operations` or
`ClinicAdmin` role at the clinic — the same gate 016/020/025/029/030's own write actions use.

**Rationale**: Spec Assumptions — "ClinicAdmin (or authorized staff member)" doesn't name a
different gate; this reapplies the session's own consistent precedent rather than inventing a
new authorization scheme for one feature.

## R8: No new `SecurityConfig` chain

**Decision**: Extends the existing `com.cms.identity.account.SecurityConfig`'s
`/api/v1/clinics/**` staff chain with one new matcher.

**Rationale**: A staff Account with an Operations/ClinicAdmin role is already what every matcher
on this chain authenticates — no reason for a new chain.
