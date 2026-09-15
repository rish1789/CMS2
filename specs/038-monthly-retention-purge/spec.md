# Feature Specification: Monthly Automatic Retention Purge

**Feature Branch**: `038-monthly-retention-purge`

**Created**: 2026-09-04

**Status**: Draft

**Input**: User description: "034 — Monthly Automatic Retention Purge: automatically and permanently delete clinical-record content (Consultation Notes, Prescriptions + Items, External Record References) whose booking is 3+ years old AND whose patient has already been anonymized. Runs monthly as a background job; Super Admin can manually re-trigger. Booking record and anonymized Patient shell are never purged — only the clinical content."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Automatic monthly purge of expired, anonymized-patient clinical content (Priority: P1)

As the System (Scheduler), the platform automatically deletes clinical-record content (Consultation Notes, Prescriptions/Items, External Record References) attached to a booking once that booking is 3+ years old AND the patient has already been anonymized, so the clinic doesn't retain personal clinical data beyond its retention window.

**Why this priority**: This is the core DPDP compliance obligation this feature exists to satisfy — the automatic monthly sweep is the primary mechanism; everything else (manual trigger) is a secondary operational safety valve.

**Independent Test**: Seed a booking whose `createdAt` is more than 3 years in the past, with a Consultation Note, Prescription (with items), and External Record Reference attached, and whose patient is anonymized. Trigger the sweep. Verify all three content types are gone for that booking, while the booking row and the Patient shell remain.

**Acceptance Scenarios**:

1. **Given** a booking whose `createdAt` is 3+ years in the past and whose patient is anonymized, **When** the monthly purge job runs, **Then** the Consultation Note, Prescription(s)/Items, and External Record Reference(s) attached to that booking are permanently deleted, while the booking record and the anonymized Patient shell remain.
2. **Given** a booking whose `createdAt` is 3+ years in the past but whose patient has NOT been anonymized, **When** the monthly purge job runs, **Then** that booking's clinical content is left untouched.
3. **Given** a booking whose patient is anonymized but whose `createdAt` is less than 3 years in the past, **When** the monthly purge job runs, **Then** that booking's clinical content is left untouched.
4. **Given** the purge has deleted a booking's clinical content, **When** that booking is later viewed, **Then** no Consultation Note, Prescription, or External Record Reference data is retrievable for it, but the booking's non-clinical metadata (date, slot, doctor) is still visible.
5. **Given** a booking with no clinical content attached at all, **When** the monthly purge job runs, **Then** the booking is simply skipped (no error).

---

### User Story 2 - Super Admin manual re-trigger (Priority: P2)

As a Super Admin, I want to manually re-run the retention purge job on demand, so that I can catch newly-eligible bookings or recover from a missed scheduled run without waiting for the next monthly cycle.

**Why this priority**: An operational safety valve, not the primary compliance mechanism — the system is still compliant without it as long as the automatic monthly job runs, but it's needed for recovery/testing per the source requirements.

**Independent Test**: As a Super Admin, call the manual-trigger endpoint after the automatic job already ran this month; verify it runs again and purges any newly-eligible bookings (e.g., one that crossed the 3-year mark since the last run).

**Acceptance Scenarios**:

1. **Given** the monthly job has already run this month, **When** a Super Admin manually re-triggers it, **Then** it runs again and purges any newly-eligible bookings.
2. **Given** a non-Super-Admin role (ClinicAdmin, Doctor, Operations, or an unauthenticated caller) attempts to manually trigger the purge job, **When** the request is made, **Then** the system rejects the action and no purge occurs.

---

### Edge Cases

- A booking has some but not all content types attached (e.g., only a Consultation Note, no Prescription) — the purge deletes whatever content types are present and simply has nothing to do for the absent ones.
- A booking has multiple Prescriptions or multiple External Record References — all of them are deleted, not just the first.
- The patient becomes anonymized in between two monthly runs, after their booking already crossed the 3-year mark — the next run (scheduled or manual) picks it up; there is no dependency on which condition became true first.
- The manual trigger is invoked twice in immediate succession — the second run is a safe no-op for bookings already purged by the first (nothing left to delete for them) and still processes any other eligible bookings.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST run an automatic background job on a monthly schedule that identifies bookings eligible for clinical-content purge.
- **FR-002**: A booking is eligible for purge only when BOTH hold: (a) the booking's retention date (booking creation time + 3 years, see Assumptions) has passed, AND (b) the booking's patient has already been anonymized.
- **FR-003**: For each eligible booking, the system MUST permanently delete that booking's Consultation Note (if present), all Prescriptions and their Items (if present), and all External Record References (if present).
- **FR-004**: The system MUST NOT delete the booking record itself, nor the anonymized Patient record, under any circumstance in this feature.
- **FR-005**: The system MUST NOT purge clinical content for a booking whose patient has not been anonymized, regardless of the booking's age.
- **FR-006**: The retention window MUST be a fixed, hardcoded 3-year constant, not configurable per clinic.
- **FR-007**: System MUST provide a manual-trigger action that runs the identical purge logic used by the automatic monthly job, on demand.
- **FR-008**: Only the Super Admin role MAY invoke the manual-trigger action; all other roles (ClinicAdmin, Doctor, Operations) and unauthenticated callers MUST be rejected.
- **FR-009**: A booking with no attached clinical content of a given type MUST be treated as a no-op for that type (not an error).
- **FR-010**: After a purge, viewing a booking MUST continue to expose its non-clinical metadata (date, slot, doctor) while returning no data for any purged Consultation Note, Prescription, or External Record Reference.
- **FR-011**: The purge operation MUST be idempotent — re-running it (scheduled or manual) after a prior run MUST NOT error and MUST correctly process any newly-eligible bookings without re-attempting already-purged ones.

### Key Entities

- **Consultation Note**: Existing entity (feature 034/030); purge target — permanently deleted when its booking becomes eligible.
- **Prescription** (and its **Prescription Items**): Existing entities (feature 035/031); purge target — a Prescription and all its Items are permanently deleted together when its booking becomes eligible.
- **External Record Reference**: Existing entity (feature 036/032); purge target — permanently deleted when its booking becomes eligible (a booking may have more than one).
- **Booking**: Existing entity — read to determine retention-date eligibility (via its creation time) and to reach the associated Patient; never deleted by this feature.
- **Patient**: Existing entity (feature 037/033); read to determine anonymization eligibility (`anonymizedAt` populated); never deleted or modified by this feature.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of bookings meeting both purge conditions (3+ years old, patient anonymized) have their clinical content removed within one monthly cycle of becoming eligible.
- **SC-002**: 0% of bookings failing either purge condition have any clinical content removed, verified across repeated monthly runs.
- **SC-003**: A Super Admin can force an out-of-cycle purge and see its effect (newly-eligible bookings purged) immediately upon completion.
- **SC-004**: 100% of non-Super-Admin manual-trigger attempts are rejected with no data change.
- **SC-005**: A booking's non-clinical metadata (date, slot, doctor) remains 100% visible and unaffected after its clinical content is purged.

## Assumptions

- **Retention-date anchor**: The booking's own `createdAt` timestamp is the anchor for its 3-year retention clock, applied uniformly across both Fixed-Time and Queue-mode bookings. Rationale: every booking already has this single well-defined timestamp regardless of mode, avoiding the mode-specific "time proxy" branching used elsewhere (e.g., feature 027/030) for a different, more time-precise concern (has this individual slot passed). A 3-year window's own coarse granularity makes day-level precision immaterial, so a possible few-day gap between booking creation and actual visit date has no practical effect on compliance outcomes.
- **Scope of "clinical content"**: Limited to the three entity types explicitly named in the source material (Consultation Notes, Prescriptions + Items, External Record References). No other data (e.g., booking metadata, Patient fields) is in scope for deletion.
- **No notification on purge**: Deleting clinical content is a silent background operation; no notification, event, or audit-log entry beyond normal application logging is required by the source material, so none is added.
- **Manual trigger runs synchronously**: The Super Admin manual-trigger endpoint runs the purge inline and returns once complete (mirroring no stated requirement for async/job-queue behavior); given expected data volumes for a clinic system, this is not expected to cause request-timeout issues.
- **Reporting on purge results**: The manual-trigger endpoint response includes at minimum a count of bookings whose content was purged, sufficient to confirm the action had an effect; no other reporting detail is specified as required.
