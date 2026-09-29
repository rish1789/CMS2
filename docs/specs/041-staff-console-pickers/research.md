# Phase 0 Research: Clinic Staff Console — Browse & Pick Instead of Type-an-ID

## R1: Module placement for each new list endpoint

**Decision**: Each new list/search endpoint lives in the module that already owns the entity being listed, reusing that module's already-established dependency direction rather than reaching across a boundary:

| Endpoint | Module | Why |
|---|---|---|
| `GET /api/v1/clinics/mine` (FR-001) | `identity.account` | Reads `RoleAssignment`+`Clinic`, both already owned here. |
| `GET /api/v1/clinics/{clinicId}/sessions` (FR-003) | `scheduling` | Reads `Session` only — no Booking/Patient data needed at this level. |
| `GET /api/v1/clinics/{clinicId}/sessions/{sessionId}/day-sheet` (FR-004/FR-005) | `booking` | Needs `Slot` (scheduling) + `Booking`+`Patient` (booking/patient.record) together. `com.cms.booking` already legitimately depends on both `com.cms.scheduling` and `com.cms.patient.record` (`Booking.slot`, `Booking.patient`) — this endpoint composes existing repositories from a module that's already allowed to see all three, rather than having `scheduling` (which must never depend on `booking`, per 022's own established rule) reach forward. |
| `GET /api/v1/clinics/{clinicId}/doctors` (FR-006) | `identity.doctor` | Reads `DoctorProfile`, joined through `RoleAssignment` — mirrors `DoctorProfileRepository.findDiscoveryEligible()`'s existing join shape exactly. `identity.doctor` already depends on `identity.account` (`DoctorProfile.account`). |
| `GET /api/v1/clinics/{clinicId}/staff` (FR-006a) | `identity.staff` | Reads `RoleAssignment`+`Account`, mirrors where `StaffDeactivationController`/`StaffOnboardingController` already live. |
| `GET /api/v1/clinics/{clinicId}/patients/search` (FR-007) | `patient.record` | Reads `Patient` only, already owned here. |

**Rationale**: This is a direct, deliberate carry-forward of the Analyze-stage Constitution III fix from `040-super-admin-rbac-login` (a module reaching into another module's internals instead of through its own dependency direction) — applying the lesson proactively here instead of finding it reactively again.

**Alternatives considered**: One new `com.cms.staffconsole` module aggregating all six endpoints — rejected; none of these six things are a cohesive new domain concept, they're six independent "list what I already own" queries that belong with their existing owners (Constitution II, no new module without a real need).

## R2: Day sheet's two-level shape (session list, then per-session detail) instead of one flat query

**Decision**: `GET /api/v1/clinics/{clinicId}/sessions?from=&to=` returns lightweight session summaries (id, doctor name, date, mode) for the fixed 14-day window (FR-008); opening one calls `GET .../sessions/{sessionId}/day-sheet` for that session's full slot+booking+patient detail. Two round trips, not one giant nested payload.

**Rationale**: A clinic's 14-day window can hold many sessions (multiple doctors × 14 days), but a staff member only ever looks at one session's slots at a time (Acceptance Scenario 2/3) — fetching every session's full slot list up front would be wasted work matching neither the UI nor FR-008's own "bounded, near-term" performance intent.

## R3: Batch booking lookup per session, not per slot

**Decision**: `BookingRepository` gains `findBySlot_Session_IdAndStatus(UUID sessionId, BookingStatus status)` — one query returning every ACTIVE booking for a whole session — instead of calling the existing `findBySlot_IdAndStatus` once per slot. `SessionDaySheetController` builds a `Map<slotId, Booking>` from that single result and merges it with `SlotRepository.findBySession_Id(sessionId)`.

**Rationale**: Avoids an N+1 query pattern (one Fixed-Time session can have a dozen-plus slots) for a view whose entire purpose is to load quickly.

## R4: Patient search query shape — and why anonymized patients are excluded

**Decision**: New `PatientRepository` method matches a clinic-scoped, case-insensitive partial match on `name` OR an exact-or-partial match on `phone`, **excluding already-anonymized patients**:
```java
@Query("SELECT p FROM Patient p WHERE p.clinic.id = :clinicId AND p.anonymizedAt IS NULL AND "
    + "(LOWER(p.name) LIKE LOWER(CONCAT('%', :term, '%')) OR p.phone LIKE CONCAT('%', :term, '%'))")
List<Patient> search(@Param("clinicId") UUID clinicId, @Param("term") String term);
```
Capped at a reasonable result count (e.g. 20) at the query/service layer — no pagination needed for v1, since a single clinic's patient roster searched by a specific name/phone fragment is not expected to return more than a handful of matches in practice.

**Rationale**: Matches the exact clinic-scoping `Patient` already enforces everywhere else in this codebase (009's own linking logic); simplest query that satisfies FR-007 without inventing a search-ranking system YAGNI doesn't call for. The `anonymizedAt IS NULL` filter is not optional: `Patient.anonymize()` (037) sets `name` to the **fixed literal string** `"Anonymized Patient"` (confirmed by reading the actual code) rather than a random or blank value — without this filter, searching that term (or any clinic with several anonymized patients) would surface a pile of identical, action-less results, directly undermining 037's own anonymization intent (Constitution IV). There is nothing a staff member can usefully do with an anonymized patient's record through this or any other tool, so excluding them from discovery entirely is correct, not merely tidy.

## R5: Doctor and staff list queries reuse the existing "active role assignment" join shape

**Decision**: `DoctorProfileRepository` gains `findByClinicStaffed(UUID clinicId)`, mirroring `findDiscoveryEligible()`'s existing `EXISTS (... RoleAssignment ...)` join shape but scoped to one clinic and without the `licenseVerified`/`visible` public-discovery filters (this is an internal staff tool, not the public-facing discovery feature — every doctor staffed at the clinic should be pickable regardless of verification status, since schedule definition itself has no such precondition). `RoleAssignmentRepository` gains `findByClinic_IdAndActiveTrue(UUID clinicId)` for the staff list (FR-006a), returning every active role (ClinicAdmin/Doctor/Operations) at that clinic — matching `StaffDeactivationController`'s own existing scope (any role is deactivatable, not just Operations).

**Rationale**: Reuses established query idioms instead of inventing new ones; keeps the doctor/staff pickers' eligibility rules exactly as permissive as the actions they feed (FR-010 — no new authorization concept).

## R6: Buffer slots are visibly marked, not filtered out or made directly bookable (Clarify resolution)

**Decision**: The day sheet's slot list includes buffer slots (`Slot.isBuffer`) like any other slot, but the frontend renders them with a distinct "Reserved capacity" marker and omits the "Book" action for that row specifically — every other row-level action (viewing status) still applies. Backend-wise, `isBuffer` is already a field on `Slot`; no new field/migration needed, `SessionDaySheetController`'s response DTO simply includes it.

**Rationale**: Direct implementation of the Clarify-stage resolution — prevents the picker from becoming a second, competing way to fill buffer capacity that bypasses `WalkInInsertionService`'s existing 3-tier priority logic (buffer → no-show-freed → regular).

## R7: Pickers navigate to existing routes; no destination-page changes

**Decision**: Every picker/list view's row action is a plain client-side navigation to the exact route `ClinicToolPages.tsx` already defines (e.g. clicking a booked slot's "Cancel" opens `/staff/clinics/{clinicId}/bookings/{bookingId}/cancel`, the same route "Cancel a booking"'s tool card already navigated to). No destination page, form, or backend action endpoint changes at all (FR-010) — this feature only changes how the identifiers on those URLs get filled in.

**Rationale**: Directly satisfies FR-010 and keeps the change surface to exactly "new ways to arrive at an unchanged destination," the smallest possible diff that satisfies the requirement (Constitution II).

## R10: A real, pre-existing bug in `011-nightly-rolling-session-generation`, found live while verifying this feature

**Discovery**: Verifying the day-sheet endpoint (FR-004) live required a real generated Session, which required actually running `SessionGenerationService.generate()` against a real Spring context for what turned out to be the first time ever (every prior feature's tests were blocked by the sandbox's Testcontainers/Docker limitation, so this code path had never actually executed anywhere). It returned `sessionsCreated: 0` for a brand-new schedule that should have produced 24. The server log showed `LazyInitializationException: failed to lazily initialize a collection of role: Schedule.daysOfWeek`.

**Root cause**: `generate()` called `this.generateForSchedule(...)` - a plain self-invocation within the same class. `generateForSchedule` was `@Transactional`, but self-invocation bypasses Spring's AOP proxy entirely, so the annotation never took effect - the exact bug class this codebase already hit twice before (020, 022), just never caught here because nothing had ever run this code for real.

**Fix** (out of this feature's own spec, but directly blocking its own live verification): extracted `generateForSchedule` into a new, genuinely separate bean (`ScheduleSessionGenerator`), taking a `scheduleId` and re-fetching the `Schedule` fresh inside its own transaction (the `Schedule` reference `generate()`'s loop holds is itself detached, so passing it through would still fail even with a real transaction wrapping the call). `SessionGenerationService.generate()` now calls this bean via a genuine cross-bean call, so `@Transactional` actually applies. One test call site (`EditScheduleTest`) that called the old method directly was updated to use the new bean. Fixed live: re-verified `sessionsCreated: 24` afterward, with zero errors in the server log, and confirmed the full downstream chain (session list → day-sheet → real booking → patient name shown) end-to-end.

## R9: A new `NotStaffedAtClinicException` per module, never reusing an existing feature-specific `ForbiddenException`

**Discovery**: `com.cms.patient.record.ForbiddenException`'s own Javadoc documents a real, already-fixed bug: that module used to reuse `com.cms.scheduling.ForbiddenException` directly, whose hardcoded message ("Not authorized to manage schedules for this doctor at this clinic") leaked verbatim to the frontend for an unrelated action (patient anonymization). Found while implementing this feature: three of the five clinic-scoped endpoints (session list, day-sheet, staff list) were about to repeat the exact same mistake by reusing `scheduling.ForbiddenException`, `booking.ForbiddenException`, and `identity.staff.ForbiddenException` respectively - each carrying a message about a completely different action (managing schedules; appointment types/fees; onboarding staff).

**Decision**: Each affected module gets its own new `NotStaffedAtClinicException` ("Not an active staff member at this clinic"), mapped to `403 FORBIDDEN` alongside that module's existing `ForbiddenException` in its existing handler - never reusing an existing feature-specific exception's message for this feature's different "active role at this clinic" check.

**Rationale**: Directly applies a lesson this codebase already paid for once (`patient.record`'s own documented fix) instead of reintroducing the same bug class three more times in the same feature.

## R8: Frontend list-view pattern reuses `InboxPage`/`PendingClinicsList`'s established shape

**Decision**: Every new list view (`MyClinicsList`, `DaySheet`, `DoctorPicker`, `StaffPicker`, `PatientSearch`) follows the same shape already proven in this codebase: a feature folder with `api.ts` (typed fetch calls using the stored staff bearer token) + one component handling loading/empty/error states explicitly (never assuming happy-path), each as its own `useEffect`-driven fetch — the same shape `InboxPage.tsx` and `PendingClinicsList.tsx` already use, both cited approvingly in the audit that produced this spec.

**Rationale**: No new frontend pattern to invent; directly reuses what the audit identified as already working well in this codebase.
