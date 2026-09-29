# Research: Prescription + Items Creation

## R1: Extends 030's existing `com.cms.clinical` module — no new module

**Decision**: `Prescription`/`PrescriptionItem` and this feature's service/controller live in the
existing `com.cms.clinical` package.

**Rationale**: This is the second of the constitution's three named "clinical documentation"
types (030, 031, 032); the module already exists.

## R2: Extract `TreatingDoctorAuthorizationService` — a genuine, non-speculative refactor of 030

**Decision**: A new `TreatingDoctorAuthorizationService` in `com.cms.clinical` exposes
`findBookingInClinic(clinicId, bookingId): Booking` and
`requireTreatingDoctor(booking, callerAccountId): DoctorProfile` — extracted verbatim from
030's `ConsultationNoteService`'s own private methods. `ConsultationNoteService` is refactored to
call this shared service instead of its own now-deleted private copies; its own public behavior
(inputs, outputs, exceptions, HTTP contract) is unchanged.

**Rationale**: 030's authorization trace (Booking → Slot → Session → DoctorProfile, compare to
caller, no override) is byte-for-byte identical to what this feature independently needs — the
same logic would otherwise be duplicated a second time. Constitution II favors extraction once a
genuine second caller exists, not speculatively ahead of one; that caller now exists. This mirrors
032's own likely future need too (a third caller), but this feature only extracts for the two
callers that concretely exist now.

**Alternatives considered**: Duplicate the trace logic a second time in a new
`PrescriptionService`. Rejected — exactly the duplication Constitution II warns against, now that
a second real, concrete caller makes the shared extraction genuinely justified rather than
speculative.

## R3: No uniqueness constraint on `Prescription.booking_id`

**Decision**: `prescription.booking_id` is a plain, non-unique foreign key — the opposite of
030's `consultation_note.booking_id` `UNIQUE` constraint.

**Rationale**: Spec FR-003/Acceptance Scenario 3 explicitly states a booking may carry zero or
more Prescriptions — this is the one genuine cardinality difference from 030, stated directly in
the source material, not inferred.

## R4: `PrescriptionItem` is a child entity, created together with its parent `Prescription`

**Decision**: `Prescription` has a `@OneToMany` (cascade `PERSIST` only — never `MERGE`/`REMOVE`,
since nothing ever updates or deletes either) to `PrescriptionItem`; both are created together in
one request/transaction. At least one Item is required (FR-006) — validated in the service before
any write, not left to the database alone.

**Rationale**: The spec's own framing ("a prescription with its line-item medications") describes
one atomic creation act, not two separate steps; `PrescriptionItem` has no independent lifecycle
of its own (it's never created, read, or referenced outside its parent Prescription).

## R5: `PrescriptionItem` fields — `medicationName`/`dosage`/`frequency`/`duration` required, `instructions` optional

**Decision**: Four required `String` fields plus one nullable `String` (`instructions`).

**Rationale**: Spec Assumptions — the source material's own "medication name, dosage, frequency,
duration, instructions, etc." lists these as the concrete fields; "instructions" read as
elaboration, not a strictly required field for every entry (some medications are genuinely
self-explanatory, e.g. "as needed" already captured in frequency).

## R6: No status precondition, no broader read surface — mirrors 030 exactly

**Decision**: Same as 030's own R3 (no clinic-staffing re-check, identity trace only) and R5 (no
broader read access than the treating doctor's own).

**Rationale**: The source material states the identical rules for this feature as for 030;
reapplying an already-converged, already-justified decision rather than re-deriving it.

## R7: No new `SecurityConfig` chain — extends the existing staff chain

**Decision**: Both endpoints add matchers to the existing
`com.cms.identity.account.SecurityConfig`'s `/api/v1/clinics/**` staff chain, mirroring 030's own
identical extension.

**Rationale**: A doctor is a staff Account; this feature has no patient-facing surface at all.
