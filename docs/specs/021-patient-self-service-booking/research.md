# Phase 0 Research: Patient Self-Service Fixed-Time Booking

## Decision: Patient JWT authentication filter

**Decision**: Add `PatientJwtAuthenticationFilter` (mirrors `com.cms.identity.account.StaffJwtAuthenticationFilter`
exactly in shape) reading a `Bearer` token, delegating to the existing
`com.cms.patient.account.JwtService.isPatientToken`/`.parse` (already checks `aud=patient`),
and populating `SecurityContextHolder` with the `PatientAccount` id as principal and a
`ROLE_PATIENT` authority. Add a matching `PatientAuthenticationEntryPoint` (mirrors
`StaffAuthenticationEntryPoint`) for a `{"error":"UNAUTHORIZED"}` 401 body instead of Spring
Security's default empty one. Both are deliberately **not** `@Component`s — same reasoning
`StaffJwtAuthenticationFilter`'s own javadoc documents: a `Filter`-typed bean auto-registers
globally and gets swept into unrelated `@WebMvcTest` slices. `com.cms.patient.account.SecurityConfig`
constructs it with `new` and wires it via `addFilterBefore(..., UsernamePasswordAuthenticationFilter.class)`,
exactly like the staff chain does.

**Rationale**: This is the first feature requiring an authenticated `/api/v1/patients/**`
endpoint — every prior endpoint on that chain (signup, login) is intentionally public. The
`JwtService` and its `PATIENT_AUDIENCE` structural separation already exist from 039
specifically so a future authenticated endpoint could build on them without inventing a new
token format. No alternative was seriously considered: reusing the staff filter/chain would
violate 039 FR-008's already-established "no shared authentication logic" boundary between
the two identity systems.

**Alternatives considered**: Session-based auth — rejected, inconsistent with the
already-decided stateless-JWT approach used everywhere else in this codebase (research.md of
002-patient-account-login already settled this for the patient identity system as a whole).

## Decision: New endpoints live under the existing `/api/v1/patients/**` prefix

**Decision**: `GET /api/v1/patients/clinics/{clinicId}/slots` (optional `doctorId` query
param) and `POST /api/v1/patients/clinics/{clinicId}/slots/{slotId}/book` are added as new
explicit matchers on `com.cms.patient.account.SecurityConfig`'s existing `@Order(2)` chain
(`securityMatcher("/api/v1/patients/**")`), not a new security chain.

**Rationale**: Every prior feature this session that added a new authenticated path added an
explicit matcher to whichever existing chain already owns that path prefix, rather than
opening a new one (013's schedule endpoints and 020's booking endpoint both extended
`com.cms.identity.account.SecurityConfig`; `com.cms.booking.BookingSecurityConfig` in turn
exists only because `/api/v1/doctors/**` genuinely didn't overlap any existing prefix). Here,
`/api/v1/patients/**` already exists and is exactly the right owner for a patient-authenticated
action — no new chain is needed. Without an explicit matcher here, the request would silently
fall through to this chain's own `anyRequest().permitAll()` and become a public, unauthenticated
endpoint — the exact bug class already caught and fixed twice this session (014, 020).

**Alternatives considered**: Reusing the staff `/api/v1/clinics/{clinicId}/slots/{slotId}/book`
path with a different HTTP method or query param to distinguish actors — rejected, conflates
two structurally distinct authentication systems on one path and one security chain, directly
contradicting 039's FR-008 "no shared authentication logic" boundary between staff and patient.

## Decision: `PatientBookingService` mirrors `StaffBookingService`, with two substitutions

**Decision**: New `PatientBookingService.bookSlot(patientAccountId, clinicId, slotId, appointmentTypeId, patientName)`
follows 016's `StaffBookingService.bookSlot` structure verbatim — find Slot (clinic-scoped),
check OPEN, resolve-and-lock fee via `FeeResolutionService` (the write-gate, before any create),
resolve the Patient, `bookingRepository.saveAndFlush(...)` inside try/catch for
`DataIntegrityViolationException` → `SlotAlreadyBookedException` (016's convergence-fixed race
closure, reused as-is), flip Slot to BOOKED. Two differences from 016: (1) no
authorization-role check — any authenticated Patient Account may book for themselves, so this
step is simply omitted, not replaced; (2) patient resolution calls
`PatientLinkingService.findOrCreatePatient(patientAccountId, clinicId, patientName)` instead of
016's existing-id-or-new-walk-in branch.

**Rationale**: 016's service is the proven, already-converged analog for this exact
fee-then-patient-then-booking ordering and race-closure mechanism; re-deriving it independently
would risk silently reintroducing the exact `save()` vs `saveAndFlush()` bug 016's own
convergence pass found and fixed. Constitution II (YAGNI) favors reusing a proven pattern over
inventing a parallel one.

**Alternatives considered**: Extracting a shared abstract base class or shared helper between
`StaffBookingService` and `PatientBookingService` — rejected per Constitution II: the two
services differ enough (authorization step present/absent, patient-resolution strategy) that a
shared abstraction would need parameterization for both differences, adding complexity to
save roughly a dozen lines of genuinely simple, already-tested logic. Three similar lines beats
a premature abstraction.

## Decision: Obtaining a name for a newly-created Patient record

**Decision**: `PatientAccount` (039) stores no name field — only email, mobile, password
hash, and notification opt-ins. `PatientLinkingService.findOrCreatePatient` requires a `name`
argument for its create-new-record branch (FR-004), so the patient booking request body
includes a required `patientName` field, used only when `findOrCreatePatient` actually needs
to create a new record (silently ignored when an existing link is reused or a walk-in record
is phone-matched, exactly like `findOrCreatePatient`'s own existing behavior for its `name`
parameter).

**Rationale**: This mirrors 016's own walk-in-patient flow, which likewise collects a name as
part of the booking request rather than assuming a name is available from account data alone
(016's `PatientAccount`-equivalent — the walk-in patient — never has a stored name either,
staff type it in at booking time). Modifying 039's `PatientAccount`/`SignupRequest` to add a
name field is out of scope: 039 is already converged, and this feature's dependency list
does not include "amend patient account signup."

**Alternatives considered**: Deriving a placeholder name from the account's email local-part —
rejected as poor UX and a data-quality regression (a clinical/visit record with a synthetic
name is worse than requiring one explicit input at the one moment it's actually needed).

## Decision: List-endpoint response omits fee amounts

**Decision**: `GET .../slots` returns, per open Slot: slot id, doctor id, doctor name, session
date, start/end time, and the doctor's available `AppointmentType`s (id + name only, no fee).
It does not call `FeeResolutionService` or expose `feeOverride`/default-fee amounts.

**Rationale**: Fee resolution and locking (015) is defined as happening exactly once, at
booking time, against a specific doctor+appointment-type pairing — computing and displaying a
"preview" fee for every appointment type across every open Slot in a list response would mean
either duplicating 015's resolution logic in a read path (Constitution II violation) or calling
it speculatively for combinations the patient may never select (wasteful, and subtly risks the
list response and the actual booking response disagreeing if a default fee changes between the
two calls — a staleness bug 015 was specifically designed to avoid by locking fee only at
booking time). The patient sees the authoritative, locked fee in the booking response itself
(`BookingResponse.lockedFee`), immediately after committing to a specific appointment type.

**Alternatives considered**: Eagerly resolving and showing a fee per appointment type per slot
— rejected for the staleness/duplication reasons above.
