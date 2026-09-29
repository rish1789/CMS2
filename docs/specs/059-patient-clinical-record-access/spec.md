# Feature Specification: Patient Clinical Record Access

**Feature Branch**: `059-patient-clinical-record-access`

**Created**: 2026-09-22

**Status**: Draft

**Input**: User description: "Give patients read-only access to their own clinical records — consultation notes, prescriptions, and external record references — that a treating doctor created for a visit they attended. A patient must be able to see, for each of their own past bookings, the consultation note, any prescription(s), and any external record references tied to that visit, through their own patient account. This is purely additive: the treating doctor keeps their existing full create/view access unchanged, and no other staff role (ClinicAdmin, Operations, a non-treating doctor) gains any new access — the existing staff-side treating-doctor-only authorization is untouched. Patient access is strictly read-only (matches the existing write-once/immutable nature of these records — there is no edit path for anyone). A patient may only ever see their own records, never another patient's, and only within the same DPDP-governed retention window these records already live under (a record that's been anonymized/purged per the existing lifecycle is no longer visible to the patient either, exactly as it's no longer visible to staff). Scope is limited to viewing — no download/export/print capability is being requested here."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Reading the consultation note from a past visit (Priority: P1)

As a patient, after a visit is over, I want to open that visit from my own account and read the consultation note the doctor wrote, so that I can remember what was discussed and decided without having to ask the clinic or rely on my own memory.

**Why this priority**: This is the core value this feature exists to deliver — today a patient has no way to see what a doctor documented about their own visit, even though the record already exists. Everything else in this feature is secondary to this being possible at all.

**Independent Test**: Sign in as a patient with at least one past booking that has a consultation note, find that visit from the patient's own booking history, open it, and confirm the note's content is shown exactly as the doctor wrote it, with no way to edit it.

**Acceptance Scenarios**:

1. **Given** a patient has a past booking with a consultation note written by the treating doctor, **When** the patient opens that visit from their own account, **Then** the consultation note's content is displayed to them.
2. **Given** a patient has a past booking with no consultation note (the doctor never wrote one), **When** the patient opens that visit, **Then** the system clearly shows no note exists, rather than an error or a blank/broken page.
3. **Given** a patient is viewing a consultation note, **When** they look for any way to edit, delete, or correct it, **Then** no such control exists anywhere on the page.
4. **Given** a patient tries to view a visit's clinical record by guessing or altering an identifier that belongs to a different patient's booking, **When** that request is made, **Then** it is refused — never another patient's data.

---

### User Story 2 - Reading prescriptions from a past visit (Priority: P2)

As a patient, I want to see any prescription(s) written for me during a past visit, so that I can recall what was prescribed — for example, when a pharmacist or a different doctor asks what I was previously given.

**Why this priority**: Directly useful and requested alongside consultation notes, but the clinic's own build order treated consultation notes as the foundational document type; prescriptions build on the same visit-viewing mechanism User Story 1 establishes.

**Independent Test**: Sign in as a patient with a past booking that has one or more prescriptions, open that visit, and confirm every prescription and its items are shown, with none belonging to another patient ever appearing.

**Acceptance Scenarios**:

1. **Given** a patient has a past booking with one or more prescriptions, **When** the patient opens that visit, **Then** every prescription tied to that visit, and its individual items, is displayed to them.
2. **Given** a patient has a past booking with no prescription, **When** the patient opens that visit, **Then** the system clearly shows none exist, rather than an error.
3. **Given** a patient is viewing a prescription, **When** they look for any way to edit or delete it, **Then** no such control exists anywhere on the page.

---

### User Story 3 - Reading external record references from a past visit (Priority: P3)

As a patient, I want to see any external record references a doctor logged during a past visit (for example, a note that a prior scan or lab result from elsewhere was reviewed), so that I have the complete picture of what informed that visit's care.

**Why this priority**: Least frequently populated of the three document types in practice, and lowest-differentiated value on its own — but completes the same visit view the other two stories establish, at no extra mechanism cost.

**Independent Test**: Sign in as a patient with a past booking that has one or more external record references, open that visit, and confirm each reference's details are shown, with none belonging to another patient ever appearing.

**Acceptance Scenarios**:

1. **Given** a patient has a past booking with one or more external record references, **When** the patient opens that visit, **Then** each reference is displayed to them.
2. **Given** a patient has a past booking with no external record reference, **When** the patient opens that visit, **Then** the system clearly shows none exist, rather than an error.

---

### Edge Cases

- What happens if a record (consultation note, prescription, or external record reference) has already been anonymized or purged under the existing DPDP retention lifecycle by the time the patient looks for it? It is not shown to the patient, exactly as it is no longer shown to staff — the patient sees the same "none exist" state as a visit that never had one.
- What happens if a patient tries to view a visit that was cancelled, or that belongs to a booking made under a walk-in/guest identity rather than their own self-service account? Only visits resolvable to the patient's own account, under the same identity-matching rule the patient's existing booking history already uses, are ever shown — a visit that doesn't resolve to their account is treated as not theirs, refused the same way a different patient's visit would be.
- What happens while a patient is mid-visit (booking exists, but the visit hasn't happened yet or is still in progress)? Whatever clinical records already exist at the time of viewing are shown; there's no separate "too early to view" restriction beyond the record simply not existing yet.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: A patient MUST be able to view the consultation note for a specific one of their own past bookings, when one exists.
- **FR-002**: A patient MUST be able to view every prescription (and its individual items) tied to a specific one of their own past bookings, when any exist.
- **FR-003**: A patient MUST be able to view every external record reference tied to a specific one of their own past bookings, when any exist.
- **FR-004**: A patient MUST be able to tell, from their own booking history, which past visits have a clinical record available to view, without having to open each one individually to find out.
- **FR-005**: The system MUST NOT allow a patient to view any consultation note, prescription, or external record reference tied to a booking that is not their own, under any circumstance — including a guessed, altered, or otherwise manipulated reference to another patient's data.
- **FR-006**: Patient viewing of these records MUST be strictly read-only — no edit, delete, correction, or any other write capability may be exposed to a patient, matching these records' existing write-once/immutable nature for every role.
- **FR-007**: The existing staff-side treating-doctor-only authorization for creating and viewing these records MUST remain completely unchanged — this feature only adds a patient's own read access, on top of the existing rules, not a replacement for them.
- **FR-008**: No staff role other than the treating doctor (ClinicAdmin, Operations, a non-treating doctor) MUST gain any new access as a result of this feature.
- **FR-009**: A record that has been anonymized or purged under the existing DPDP retention lifecycle MUST NOT be visible to the patient, exactly as it is no longer visible to staff once that lifecycle applies.
- **FR-010**: The system MUST NOT provide any download, export, or print capability for these records as part of this feature.

### Key Entities

- **Consultation Note, Prescription, External Record Reference** (existing entities, unchanged): this feature adds a new *read path* to each, scoped to the patient who owns the underlying booking — it does not add, remove, or modify any field on these entities.
- **Patient's own visit**: a past booking that resolves to the signed-in patient's own account, under the same identity-matching rule already governing what appears in that patient's existing booking history. A visit's clinical records (note, prescriptions, external records) are only ever reachable through a visit that resolves this way.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of a patient's own past visits that have a consultation note, prescription, or external record reference correctly display that record when the patient opens the visit — zero false "none exist" results for records that actually exist.
- **SC-002**: 0% of requests for another patient's clinical record ever succeed, regardless of how the request is constructed.
- **SC-003**: A patient can go from their own booking history to reading a past visit's full clinical record (note, prescriptions, external records together) in 2 actions or fewer.
- **SC-004**: 100% of anonymized/purged records are absent from patient view, with zero cases of a purged record still being visible.

## Assumptions

- "A patient's own past bookings" is resolved the same way the patient's existing booking-history feature already resolves it (the identity-matching rule that determines what shows up in a patient's own booking list is reused unchanged here, not redefined) — this feature does not introduce a new or different notion of ownership.
- A patient viewing these records sees the same content a treating doctor sees when they view it today — no separate, patient-specific simplified or redacted version is in scope.
- This feature does not change when a consultation note, prescription, or external record reference is created, or by whom — only who else (the patient) can subsequently read it.
- This feature does not introduce any notification (e.g. alerting a patient that a new record is available) — the patient must go look for it themselves, matching how their existing booking history already works.
- Mobile/responsive presentation follows this system's existing patient-facing design conventions; no new device-specific requirement is introduced.
