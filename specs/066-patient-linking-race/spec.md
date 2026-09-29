# Feature Specification: Patient-Linking Same-Account Race

**Feature Branch**: `claude/066-patient-linking-race`

**Created**: 2026-09-29

**Status**: Draft

**Input**: User description: "Patient-linking same-account race: when the same patient account makes two bookings at the same moment at a clinic where it has no Patient record yet, both bookings must succeed and share exactly one Patient record (the second caller reuses the winner's record) instead of one failing with a server error. Today the losing call hits the uq_patient_clinic_account unique index, PostgreSQL aborts its transaction, and the re-read of the winner's record fails ("current transaction is aborted"), so the booking returns 500. Behaviour already required by spec 009 FR-005a/FR-006 and test PatientLinkingSameAccountRaceTest; this feature restores it. Scope: PatientLinkingService.findOrCreatePatient as called by PatientBookingService and PatientQueueBookingService. Must keep the booking all-or-nothing (no orphan Patient record if the booking fails), must not change walk-in phone-match linking (FR-003) or the separate uq_patient_clinic_phone_unlinked rule. Out of scope: other races (claim/decline, rate limit), PB-001/PB-002."

## Context

Spec `009-patient-record-phone-linking` already requires this behaviour. It closes the same-account duplicate-creation race at the data layer (FR-005a). When a concurrent request loses that race, the loser must re-read the winner's record and succeed (FR-006, SC-004). Constitution Principle IV requires the same.

The data-layer half holds today: a second record is never created. The FR-006 half does not. The losing request fails with a server error instead of reusing the winner's record, and its booking is lost. The existing integration test `PatientLinkingSameAccountRaceTest` fails for this reason (recorded in `docs/product-audit/07-BUG-AND-DEFECT-REGISTER.md`, "Patient linking race").

This feature introduces **no new behaviour**. It restores 009 FR-006 for the same-account race on the patient self-service booking paths.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Two simultaneous first bookings at a new clinic both succeed (Priority: P1)

A patient books two appointments at a clinic they have never visited. For example, they book two slots in quick succession from two browser tabs, or a double-tap sends the request twice. The patient has no Patient record at that clinic yet, so each booking must first create one. The two requests arrive at the same moment.

Both bookings must succeed and point to the same single Patient record at that clinic. Today one of them fails with a generic error, and the patient loses that booking.

**Why this priority**: This is the whole feature. A patient who did everything right loses a booking and sees an unexplained error, and staff see an unexplained server failure in their monitoring.

**Independent Test**: Start two concurrent first-time linking calls for the same Patient Account and the same clinic, with no pre-existing Patient record. Verify that both succeed, both return the same Patient record, and exactly one Patient record exists for that account at that clinic.

**Acceptance Scenarios**:

1. **Given** a Patient Account with no Patient record at clinic C, **When** two first-time linking requests for that account at clinic C run concurrently, **Then** both succeed, both return the same Patient record, and exactly one Patient record exists for that account at clinic C.
2. **Given** the same starting state, **When** two patient self-service bookings for different open slots at clinic C are submitted concurrently by that account, **Then** both bookings are confirmed and both reference the same single Patient record.
3. **Given** the same starting state, **When** the concurrent linking requests race, **Then** the patient never sees a server error caused by the race.

---

### User Story 2 - A failed booking leaves no stray patient record (Priority: P2)

When a patient's first booking at a clinic fails for an ordinary reason after the Patient record step (for example the slot was just taken, or a booking limit applies), nothing from that attempt may remain. In particular, no Patient record created by that attempt may remain. Fixing the race must not weaken this existing all-or-nothing behaviour.

**Why this priority**: This guards against a regression. An obvious way to fix the race would commit the Patient record separately from the booking, which breaks this guarantee. The guarantee also matters for data privacy: patient-identifying data must not be kept without a visit.

**Independent Test**: Make a first-time booking at a clinic fail after the Patient record step. Verify that no Patient record exists for that account at that clinic afterwards.

**Acceptance Scenarios**:

1. **Given** a Patient Account with no Patient record at clinic C, **When** its first booking at clinic C is rejected after the Patient record step, **Then** no Patient record exists for that account at clinic C afterwards.
2. **Given** two concurrent first-time requests where the one that created the Patient record then fails and is rolled back, **When** the other request completes, **Then** the other request still succeeds, and exactly one Patient record exists at clinic C, owned by the successful request.

---

### Edge Cases

- **Winner rolls back.** The request that created the Patient record fails later in its booking, so its Patient record never persists. The other, waiting request must then create the record itself and succeed. It must not fail, and it must not reference a record that does not exist.
- **More than two concurrent requests** for the same account and clinic: all succeed, and exactly one Patient record exists.
- **Already linked.** The account already has a Patient record at the clinic. Nothing changes: the existing record is returned directly (009 FR-002).
- **Walk-in phone match.** An unlinked walk-in record with the account's phone exists at the clinic. Nothing changes: it is linked and returned (009 FR-003). This feature does not alter phone-match linking.
- **Different accounts** booking at the same clinic at the same time are unaffected: each gets its own record, as today.
- **Unexpected failures.** A concurrency failure that is *not* the same-account duplicate (any other data error) still fails the request as today. It must not be silently turned into a success.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: When two or more requests concurrently link or create a Patient record for the same Patient Account at the same clinic, and no such record existed before, every request MUST succeed and return the same single Patient record. (Restores 009 FR-006 for race FR-005a.)
- **FR-002**: The guarantee that one Patient Account never has two Patient records at the same clinic MUST continue to be enforced at the data layer, not only by an application-level check. (009 FR-005a; Constitution Principle IV.)
- **FR-003**: The Patient-record step MUST remain part of the booking's all-or-nothing outcome. If the booking that created the record does not complete, that record MUST NOT persist.
- **FR-004**: If the concurrent request that would have created the record fails and its record does not persist, a waiting request MUST still succeed, creating the record itself if needed.
- **FR-005**: Existing linking behaviour MUST be unchanged: the already-linked reuse path (009 FR-002), walk-in phone-match linking (009 FR-003) and the rule that unlinked walk-in records never share a clinic and phone (009 FR-005b).
- **FR-006**: Only the same-account duplicate conflict may be treated as "reuse the winner's record". Any other data failure during the Patient-record step MUST still fail the request.
- **FR-007**: This behaviour MUST hold for both patient self-service booking paths that create Patient records: fixed-time slot booking and queue booking.

### Key Entities

- **Patient Account**: the global patient identity (login). Not clinic-scoped.
- **Patient record**: the clinic-scoped record of a patient at one clinic. At most one per Patient Account per clinic. Created on the account's first booking at that clinic, or linked from an existing walk-in record.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: With concurrent first-time requests by the same Patient Account at the same clinic, 100% of the requests succeed and none returns an error. Verified with at least 2 concurrent requests, as in 009 SC-004.
- **SC-002**: After any such concurrent run, exactly one Patient record exists for that account at that clinic, checked directly against stored data.
- **SC-003**: After a first-time booking that fails, zero Patient records created by that attempt remain.
- **SC-004**: All existing patient-linking and patient-booking tests continue to pass, including the already-linked, phone-match and walk-in-uniqueness cases.
- **SC-005**: The previously failing `PatientLinkingSameAccountRaceTest` passes, with its assertions unchanged.

## Assumptions

- The only callers that create Patient records from a Patient Account are the two patient self-service booking paths: fixed-time slot booking and queue booking. Staff and walk-in flows create unlinked records through other code and are out of scope. This includes PB-001 and PB-002, the phone-collision issues in staff booking and walk-in.
- A losing request waiting briefly for the winner's outcome is acceptable. The wait is bounded by the winner's own booking time, which is short.
- Race FR-005b (two concurrent *unlinked* walk-in records with the same phone) is covered by 009, and this feature does not change it. Only race FR-005a is in scope.
- No database schema change is expected. The existing data-layer uniqueness guarantee is kept as it is. If planning finds a schema change necessary, it must follow the constitution's test-first rule for migrations.
- Out of scope: the claim/decline race, the booking rate-limit margin, PB-003, and the other items in audit doc 07.
