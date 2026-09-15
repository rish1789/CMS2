# Research: External Record Reference

## R1: Extends 030/031's existing `com.cms.clinical` module — no new module, no changes to either

**Decision**: `ExternalRecordReference` and this feature's service/controller live in the existing
`com.cms.clinical` package. Unlike 031's own extraction work, this feature makes zero edits to
`ConsultationNoteService`/`PrescriptionService` or the migration files those own.

**Rationale**: This is the third of the constitution's three named "clinical documentation"
types; the module and its shared authorization helper already exist and already generalize
cleanly to a third caller with no changes needed.

## R2: Reuses `TreatingDoctorAuthorizationService` unchanged, as a third caller

**Decision**: `ExternalRecordReferenceService` calls the same `findBookingInClinic`/
`requireTreatingDoctor` methods 030 and 031 already share (extracted during 031).

**Rationale**: The identical authorization trace this feature needs already exists as shared
infrastructure — Constitution II's own reuse principle applies even more directly here than it
did for 031 (which had to extract it first; this feature simply calls what's already there).

## R3: Flat entity, no child collection — unlike 031

**Decision**: `ExternalRecordReference` is a single entity with four required scalar fields
(`recordType`, `sourceProvider`, `recordDate`, `summary`) — no `@OneToMany` line-item collection.

**Rationale**: The source material describes flat, typed fields directly on the record itself
("record type, source/provider name, date, summary text"), not a repeatable line-item structure
the way 031's medications are — there is nothing here analogous to a Prescription's multiple
Items.

## R4: All four fields required — no optional field, unlike 031's `instructions`

**Decision**: `recordType`, `sourceProvider`, `recordDate`, `summary` are all `NOT NULL`.

**Rationale**: Spec Assumptions — unlike 031's own source text, which explicitly hedges
"instructions" as elaboration, nothing in this feature's source material suggests any field here
is optional; all four are listed as the concrete fields a doctor records.

## R5: No file-attachment field — structural absence, not a validated exclusion

**Decision**: No field of any kind on `ExternalRecordReference`, its DTOs, or its migration
represents a file/document attachment (no `fileUrl`, no `attachmentId`, nothing).

**Rationale**: FR-006's own wording ("MUST NOT have any file/document attachment field anywhere")
mirrors 030/031's identical "immutability is structural, not merely unexposed" resolution — this
system has no file-upload capability anywhere at all (an explicit, system-wide out-of-scope
boundary the constitution itself names), so there is nothing to specifically reject at
request-validation time; the absence of the field is the entire guarantee.

## R6: No status precondition, no broader read surface, no `SecurityConfig` chain — mirrors 030/031 exactly

**Decision**: Same as 030's/031's own identical R3/R5/R6 (or R6/R7) decisions: no
clinic-staffing re-check beyond the identity trace, only the treating doctor's own read access,
both endpoints extend the existing staff `SecurityConfig` chain.

**Rationale**: The source material states the identical rules for this feature as for its two
siblings; reapplying already-converged, already-justified decisions rather than re-deriving them
a third time.
