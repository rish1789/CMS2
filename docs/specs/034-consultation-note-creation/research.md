# Research: Consultation Note Creation

## R1: New `com.cms.clinical` module

**Decision**: `ConsultationNote` and every service/controller this feature adds live in a new
`com.cms.clinical` package, depending on `com.cms.booking` (Booking, read-only) and
`com.cms.identity.doctor`/`com.cms.identity.account` (DoctorProfile/Account identity trace,
read-only) — never the reverse, and no event published (nothing downstream needs to react to a
note being written).

**Rationale**: The project constitution names "clinical documentation" as one of this system's
own intended module boundaries. This is the first feature to actually need that module to exist.

## R2: One-note-per-booking enforced by a data-layer uniqueness constraint, not an application check

**Decision**: `consultation_note.booking_id` carries a `UNIQUE` constraint. `ConsultationNoteService
.create` attempts `consultationNoteRepository.saveAndFlush(...)` inside a try/catch for
`DataIntegrityViolationException`, translating a constraint violation into
`ConsultationNoteAlreadyExistsException` — mirroring 021's own `PatientBookingService.bookSlot`
race-closure shape (`saveAndFlush` + catch, for an *insert*-shaped invariant) rather than
`cancelIfActive`'s conditional-`UPDATE` shape (which doesn't apply here — there's no existing row
to conditionally transition, only a new row to conditionally allow).

**Rationale**: Constitution IV requires closing this exact class of race at the data layer, not
merely with an "exists already?" read-then-write check in application code — two concurrent
creation attempts against the same booking must never both succeed.

**Alternatives considered**: An application-level existence check (`findByBooking_Id` then
reject if present) with no database constraint. Rejected — exactly the read-then-write race
Constitution IV rules out; the database constraint is the actual guarantee, the pre-check (if any)
would only be a friendlier-error-message optimization, not the source of correctness.

## R3: Authorization traced via `Booking → Slot → Session → DoctorProfile`, no clinic-staffing re-check

**Decision**: `ConsultationNoteService` resolves the booking's treating doctor as
`booking.getSlot().getSession().getDoctorProfile()`, and authorizes by comparing
`treatingDoctor.getAccount().getId()` to the caller's own JWT-derived Account id — mirroring
013's `ScheduleService.requireAuthorized`'s "doctor themselves" half exactly, but *without* the
ClinicAdmin-override half that method also has (spec FR-004 explicitly forbids any such override
here).

**Rationale**: FR-005 states this trace is sufficient and no separate active-staffing check is
required — a stricter check here would be an unstated, unjustified addition (Constitution II).

## R4: Content is a single free-text field

**Decision**: `ConsultationNote.content` is a single `TEXT` column, required, no structure.

**Rationale**: Spec Assumptions — the source material describes "a permanent clinical record of
the visit" with no stated sub-fields, and this feature's siblings (031/032) are explicitly
separate entities for more structured data, so there's no reason to anticipate structure here.

## R5: Only a `GET` for the treating doctor's own note — no broader read surface

**Decision**: `GET /api/v1/clinics/{clinicId}/bookings/{bookingId}/consultation-notes` is
authorized the identical way as creation (treating-doctor-only) — not opened to any other staff
role, ClinicAdmin, or patient.

**Rationale**: Spec Assumptions explicitly scope read access to the treating doctor only for this
feature; a broader read surface (e.g. patient-facing, or clinic-staff-wide) is a future feature's
decision to make, not this one's to anticipate (Constitution II).

## R6: No new `SecurityConfig` chain — extends the existing staff chain

**Decision**: Both endpoints add matchers to the existing
`com.cms.identity.account.SecurityConfig`'s `/api/v1/clinics/**` staff chain.

**Rationale**: A doctor is a staff Account (with a `Doctor` `RoleAssignment`); this feature has no
patient-facing surface at all, so there's no reason to touch
`com.cms.patient.account.SecurityConfig` — mirrors every prior staff-only feature's identical
extension pattern (016/020/025/026/027/029/030 etc.).

## R7: Immutability is structural, not merely unexposed

**Decision**: No `update`/`edit`/`delete` method exists anywhere in `ConsultationNoteService`,
`ConsultationNoteRepository`, or any controller — not "exists but unauthorized," genuinely absent
code.

**Rationale**: FR-002's own wording ("no update or delete action MUST exist... anywhere") is a
constraint on the implementation's shape, not just its exposed behavior — Constitution IV's
write-once/immutable requirement for clinical documentation is explicit about this exact class of
entity.
