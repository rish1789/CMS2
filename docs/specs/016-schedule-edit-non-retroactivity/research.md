# Research: Schedule Edit Non-Retroactivity

## Decision: Non-retroactivity is structural (no code path to `Session`), not a runtime check

**Rationale**: The strongest possible guarantee that an edit never touches a Session isn't "check that it doesn't" — it's "make it impossible to." `ScheduleService.edit()` (and everything it calls) never accepts, autowires, or references `SessionRepository`/`Session` at all. This is verifiable by code inspection alone (no import, no field, no method call), matching how 011's own `SessionGenerationService` never references anything Slot-related — the same "make the boundary structural" pattern already established in this codebase.

**Alternatives considered**: A runtime assertion/test-only check that no Session row changed — rejected as a weaker guarantee than simply never writing the code path that could touch one; a structural guarantee needs no test to keep holding as the codebase evolves, while a runtime check only proves it held for the cases actually tested.

## Decision: Validation and the overlap check both extracted as reusable, shared logic between `create()` and `edit()`

**Rationale**: FR-003 explicitly requires edit to enforce "exactly the same field-validation rules" create does — the only way to guarantee that stays true as the rules evolve is to have both call the *same* method, not two independently-maintained copies. `validate(CreateScheduleRequest)` (009) is already a private, stateless method with no dependency on whether a Schedule already exists — reused verbatim. The overlap check (010's `requireNoOverlap`) gains one new parameter: the id of the Schedule to exclude from its own candidate set (`null` for create, the edited Schedule's own id for edit) — a minimal signature change, not a duplicated method.

**Alternatives considered**: A separate `validateForEdit`/`requireNoOverlapForEdit` — rejected; the actual rules are identical (Principle II: no duplication for behavior that's supposed to be identical, since that's exactly the kind of duplication that silently drifts).

## Decision: `PATCH`, full-payload replacement (not a partial patch), mirroring `POST`'s request shape exactly

**Rationale**: Spec Assumptions — 009's `CreateScheduleRequest` already has exactly the five fields this feature edits; reusing that same DTO for the edit body means the client always sends its complete intended configuration, avoiding the added complexity (and edit-vs-create rule drift risk) of a partial-field-merge PATCH semantics this codebase has no other precedent for (every other edit in this backlog — 008's Doctor Profile edit — is also full-payload replacement, not partial).

**Alternatives considered**: A JSON Merge Patch-style partial update — rejected as inconsistent with 008's own established full-payload-replacement precedent for an "edit" endpoint in this codebase, and unnecessary complexity for a five-field form.

## Decision: `ScheduleNotFoundException` is new, distinct from `ClinicNotFoundException`/`DoctorProfileNotFoundException`

**Rationale**: The edit path has three distinct "not found" cases (unknown clinic, unknown doctor, unknown/mismatched schedule) that should surface as distinct, debuggable error codes, matching this codebase's existing convention (e.g. 013 already has separate `ClinicNotFoundException`/`DoctorProfileNotFoundException`) rather than a single generic "not found."

**Alternatives considered**: Reusing a generic `NoSuchElementException` — rejected; every other not-found case in `com.cms.scheduling` already has its own named exception type, and matching that consistency is cheap.

## Decision: A mismatched (schedule, clinic, doctor) triple in the path is treated as not-found, not a separate error code

**Rationale**: If `scheduleId` exists but doesn't actually belong to the `clinicId`/`doctorProfileId` named in the path, the simplest, most secure response is the same `404 SCHEDULE_NOT_FOUND` a genuinely-unknown id would get — it doesn't leak whether the schedule exists elsewhere, and it's exactly the shape 004/013's existing not-found handling already produces for analogous path-variable mismatches.

**Alternatives considered**: A distinct `409`/`400` for "schedule exists but doesn't belong to this clinic/doctor" — rejected as unnecessary distinction with no source-material justification, and a minor information-disclosure improvement over a generic 404 for free.
