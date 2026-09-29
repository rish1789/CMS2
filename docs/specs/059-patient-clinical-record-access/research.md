# Phase 0 Research: Patient Clinical Record Access

## Decision 1: Reuse `Patient.patientAccount` as the ownership check, exactly as "My Bookings" does

**Decision**: A booking is "the patient's own" if and only if
`booking.getPatient().getPatientAccount().getId().equals(callerPatientAccountId)` — the identical
predicate `BookingRepository.findByPatient_PatientAccount_IdOrderByCreatedAtDesc` already
expresses for `GET /api/v1/patients/bookings`. A new derived-query method,
`findByIdAndPatient_PatientAccount_Id(bookingId, patientAccountId)`, applies the same predicate to
a single booking lookup.

**Rationale**: spec.md's own Assumptions section commits to this explicitly, to avoid a second,
possibly-inconsistent definition of "my booking" existing alongside the one this codebase already
shipped and has been running in production-shaped form since the patient-booking-flow-rebuild
work. A booking whose `Patient` has no linked `PatientAccount` (a walk-in/staff-created booking
under a name/phone only) simply never matches any patient account id — correctly invisible to
every patient, not just the one it happens to resemble.

**Alternatives considered**: Match by phone number or name instead of the account link, so a
walk-in visit could also surface in "my records" once a matching patient later creates an account.
Rejected — this is a materially different, riskier identity-matching policy (name/phone matching
is exactly the kind of fuzzy-identity problem this codebase's own patient-creation race-safety
work has treated carefully elsewhere) that the spec did not ask for and that would need its own
scoping, not a default assumption.

## Decision 2: Reuse the existing `findByBooking_Id` lookups unchanged — purge-safety is free

**Decision**: The three new patient-facing service methods call the exact same repository lookups
(`ConsultationNoteRepository.findByBooking_Id`, `PrescriptionRepository.findByBooking_Id`,
`ExternalRecordReferenceRepository.findByBooking_Id`) the existing treating-doctor `get`/`list`
methods already call. No new "is this purged" check is added anywhere.

**Rationale**: `RetentionPurgeService` (038) permanently **deletes** the row once a booking is
retention-eligible (research.md/javadoc confirms: "Permanently deletes... Never deletes the
Booking or Patient row itself"). A deleted row is indistinguishable, at the query level, from a
booking that never had a record — both a treating doctor and a patient calling the same lookup
get the same empty/absent result. This satisfies spec.md FR-009 with zero new code: there is no
soft-delete flag to check and no second code path that could ever drift out of sync with what
staff already see.

**Alternatives considered**: Add an explicit "is this booking's patient anonymized" guard to the
new patient-facing methods, defense-in-depth style. Rejected — there is nothing left to guard once
the row is physically gone; an extra check here would be dead code checking a condition the query
itself already makes impossible to observe.

## Decision 3: The bulk availability check lives in `clinical`, not `booking` — avoids a module cycle

**Decision**: A new `ClinicalRecordAvailabilityService` (in `com.cms.clinical.service`) exposes
`findBookingIdsWithAnyRecord(Collection<UUID> bookingIds, UUID patientAccountId) -> Set<UUID>`,
backed by three new bulk-`exists`-style repository queries (one per clinical repository, each
additionally scoped through `booking.patient.patientAccount.id` so a caller can never probe
another patient's booking ids for a yes/no signal). It's exposed as its own endpoint
(`GET /api/v1/patients/bookings/clinical-record-availability?bookingIds=...`) in a new
`com.cms.clinical.api.PatientClinicalRecordController`, called by the frontend as a second request
after "My Bookings" loads — not embedded into `PatientBookingSummaryResponse` itself.

**Rationale**: `clinical` already depends on `booking` (every existing service in this module
takes a `Booking`/uses `BookingRepository`-adjacent lookups via `TreatingDoctorAuthorizationService`).
Making `PatientBookingSummaryResponse.of(...)` (in `com.cms.booking.dto`) reach into `clinical` to
compute an availability flag would point a new dependency the other direction, creating a cycle —
exactly the kind of thing this codebase has deliberately routed around before (024's
`RiskBasedBufferSlotCalculator` living in `com.cms.booking` specifically to avoid a
`scheduling → clinical` cycle is the same structural move in reverse). Keeping the new capability
entirely inside `clinical`, as its own additive endpoint, means the already-shipped
`PatientMyBookingsController`/`PatientBookingSummaryResponse` need zero changes.

**Alternatives considered**: (a) Embed the flag directly in `PatientBookingSummaryResponse` via a
reverse dependency — rejected for the cycle reason above. (b) An event-driven "note created"
signal the `booking` module could subscribe to and cache — rejected as speculative infrastructure
for a same-request read, not the async cross-module side-effect pattern (booking → notification,
cancellation → waitlist) this constitution's event-driven guidance is actually for.

## Decision 4: Reuse the existing response DTOs unchanged for patient reads

**Decision**: `ConsultationNoteResponse`, `PrescriptionResponse`/`PrescriptionItemResponse`, and
`ExternalRecordReferenceResponse` (all already exist, built for the staff/treating-doctor GET
endpoints) are reused as-is for the new patient endpoints. No new "patient view" DTO is introduced.

**Rationale**: spec.md's own Assumptions: "A patient viewing these records sees the same content a
treating doctor sees... no separate, patient-specific simplified or redacted version is in scope."
Since the fields on these DTOs are already exactly the clinical content itself (note text,
medication/dosage/frequency/duration/instructions, record type/source/date/summary) with no
doctor-only internal metadata mixed in, there is nothing to redact and no reason to fork the shape.

**Alternatives considered**: A parallel `PatientConsultationNoteResponse` etc., in case a future
feature needs to add a patient-only field (e.g. a "seen" flag). Rejected — YAGNI; nothing in this
spec calls for it, and reusing the existing type keeps the contract for "what a consultation note
looks like over the wire" singular across both callers.

## Decision 5: 0-or-1 vs. 0-or-many response shape follows each entity's existing cardinality

**Decision**: The consultation-note endpoint returns a nullable single object (200 OK with a
`null` body when none exists); the prescriptions and external-record-references endpoints return a
possibly-empty JSON array. No endpoint ever returns a 404 for "no record exists" — that status is
reserved for "this booking id doesn't resolve to your account at all" (FR-005's refusal case).

**Rationale**: Mirrors each entity's own real-world cardinality exactly as the domain model already
enforces it (`ConsultationNote` has a `UNIQUE` constraint on `booking_id`; `Prescription`/
`ExternalRecordReference` are explicitly zero-or-more per booking, per their own service javadocs).
Distinguishing "booking not yours" (an error, 404) from "booking's yours, nothing written yet" (a
normal, successful empty result) directly satisfies spec.md's Acceptance Scenario 2 across all
three stories ("the system clearly shows no note exists, rather than an error").

**Alternatives considered**: Have every "no record" case also 404, relying on the frontend to
distinguish "not found because not yours" from "not found because empty" by inspecting an error
code. Rejected — conflates a security refusal with a completely normal, expected state, and forces
every frontend caller to special-case a 404 as non-error, which is exactly the fragile pattern
Decision 5 avoids by making "empty" a genuine 200.
