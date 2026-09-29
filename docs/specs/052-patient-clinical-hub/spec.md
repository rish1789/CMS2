# Feature Specification: Patient Context & Clinical History Hub

**Feature Branch**: `052-patient-clinical-hub`

**Created**: 2026-09-15

**Status**: Draft

**Input**: User description: "backlog/049-patient-context-clinical-history-hub.md" — a unified staff-side hub aggregating a patient's booking history, consultation notes, prescriptions, and external record references, reached from Patient Search, reusing existing per-booking data and authorization rather than inventing new clinical data or a new authorization model.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - One place to see a patient's history at this clinic (Priority: P1)

Clinic staff looking up a patient (via Patient Search) want a single hub page showing that patient's identity, their bookings at this clinic, and a navigable path to each booking's consultation note, prescription, and external record — instead of needing to already know a specific booking ID to reach any clinical detail page.

**Why this priority**: The core, named problem — today there is no way to even see a patient's booking history at all (verified: no endpoint, no screen exists), so nothing else in this feature is reachable without it first.

**Independent Test**: From Patient Search, open a patient with existing bookings and confirm their hub shows identity info and a real, correctly-attributed list of their bookings at this clinic.

**Acceptance Scenarios**:

1. **Given** a patient with existing bookings at a clinic, **When** staff open that patient's hub, **Then** an Overview section shows their current identity (name/phone) and a Bookings section lists their real bookings at this clinic (session date, doctor, status), newest first.
2. **Given** a patient's hub, **When** staff view the Consultations/Prescriptions/External Records sections, **Then** each lists that patient's relevant bookings as navigable rows into the existing, already-built per-booking pages (`bookings/:id/consultation-note`, `.../prescription`, `.../external-record`) — this feature does not duplicate or re-render clinical content inline, it makes it reachable.
3. **Given** a `PatientAccount` with relationships at multiple clinics (per 039), **When** staff at Clinic A view a patient's hub, **Then** only Clinic A's `Patient` record and its bookings are shown — never another clinic's data for the same person.

---

### User Story 2 - Anonymized patients show their real, current state (Priority: P2)

Staff opening the hub for a patient who has since been anonymized (033) want to see that accurately reflected, not a stale or silently-wrong identity.

**Why this priority**: A real, explicitly-required correctness guarantee (DPDP compliance) — secondary to the hub existing at all (P1), but a hub that silently mis-displays anonymization state would be a genuine compliance regression, not just a missing nicety.

**Independent Test**: Anonymize a patient via the existing flow, then open their hub and confirm it shows the current scrubbed state, not a cached pre-anonymization value.

**Acceptance Scenarios**:

1. **Given** a patient whose record has been anonymized, **When** staff open their hub, **Then** the Overview clearly shows the anonymized state (scrubbed name/phone, per 033's actual scrubbed values) — not a value read from anywhere else or cached from before anonymization.

---

### User Story 3 - No new edit capability, no authorization bypass (Priority: P2)

Staff and doctors using the hub must not gain any new capability the constitution forbids — no editing an existing consultation note or prescription, and no doctor seeing a colleague's patient's consultation note they aren't the treating doctor for.

**Why this priority**: A hard constitutional constraint (Principle IV, write-once clinical documentation) and an existing authorization guarantee (030's treating-doctor-only note access) — this feature must not weaken either, making this as important as the hub existing at all, even though it's a "don't build/don't break" requirement rather than new value delivered.

**Independent Test**: Confirm no edit/delete control exists anywhere for an existing consultation note or prescription; confirm a non-treating doctor who follows a hub link into a consultation note they don't own still gets the existing, unchanged authorization rejection.

**Acceptance Scenarios**:

1. **Given** a consultation note or prescription shown as reachable from the hub, **When** inspected, **Then** no UI control exists to edit or delete it — only to view it (by following the link into the existing, unmodified per-booking page) or start a new one for a new visit.
2. **Given** a doctor who is not the treating doctor for a specific booking, **When** they follow the hub's link into that booking's consultation note, **Then** they hit the same, unmodified `ForbiddenException` the existing per-booking page already enforces (030) — the hub does not pre-filter, re-implement, or relax this check.

---

### Edge Cases

- What happens for a patient with zero bookings at this clinic? The Bookings/Consultations/Prescriptions/External Records sections show a clear empty state, not an error.
- What happens when a booking has no consultation note/prescription/external record yet? The hub still lists the booking as a navigable row (since a note/prescription can be created for it); it does not pre-check existence before rendering the row — the destination page's own existing create-or-view behavior handles that, unchanged.
- What happens when a ClinicAdmin or Operations staff member (not a doctor) opens the hub? They see the same Bookings list (booking metadata itself has no treating-doctor gate), but following any Consultations/Prescriptions/External Records link takes them to the existing per-booking page, which enforces the same treating-doctor-only check it already does today (030) — they will see that page's existing `ForbiddenException` handling, unchanged by this feature.
- What happens to a `PatientAccount` active at multiple clinics? Only the current clinic's own `Patient` record (and its bookings) are shown — cross-clinic data is never aggregated (per the constitution's multi-tenancy rule and the explicit system-wide "no single global medical record" boundary).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Patient Search MUST provide a way to open a patient's hub (currently, selecting a patient has no destination beyond the anonymize flow).
- **FR-002**: The hub MUST show the patient's current identity (name, phone) and current anonymization state, both read live (not cached) from the patient's real record.
- **FR-003**: The hub MUST show the patient's bookings at the current clinic only — never a different clinic's relationship with the same underlying `PatientAccount`.
- **FR-004**: The hub MUST provide a navigable path from a booking to that booking's existing consultation note, prescription, and external record pages — reusing those pages unchanged, not re-implementing or duplicating their content or authorization inline.
- **FR-005**: The hub MUST NOT introduce any new create/edit/delete UI beyond what already exists on the pages it links to — it is a navigation/aggregation surface only.
- **FR-006**: The hub MUST NOT relax, duplicate, or bypass the existing treating-doctor-only authorization (030) for consultation notes (and, by the same reasoning, prescriptions/external records, which use the identical check) — every link the hub renders goes through the existing, unmodified authorization path.
- **FR-007**: The hub MUST be organized into exactly the 5 sections named in the backlog brief (Overview, Bookings, Consultations, Prescriptions, External Records) — no "Medical Information," "Lab Tests," or "Billing" section, since none of that data exists.
- **FR-008**: This feature MUST NOT change any existing per-booking page's own behavior or authorization (consultation-note/prescription/external-record/anonymize) — only add new ways to reach them.
- **FR-009**: This feature MUST NOT build a patient-facing equivalent, and MUST NOT build a cross-clinic aggregated record — both explicitly out of scope system-wide.

### Key Entities

- **Patient's booking list at a clinic**: Not a new entity — a new, minimal read query over the existing `Booking`/`Patient`/`Session` relationships, scoped by `patient.clinic.id` (a `Patient` row already belongs to exactly one clinic).
- **Patient anonymization state, as exposed to the frontend**: Not a new field on the entity (`Patient.anonymizedAt` already exists) — a currently-missing field on the existing patient-detail response, which today omits it entirely (verified: no frontend DTO for patient search/detail exposes `anonymizedAt`).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Staff can go from a Patient Search result to that patient's real booking history at the current clinic in one click, where today no such path exists at all.
- **SC-002**: Every booking shown in the hub belongs to the current clinic's own relationship with that patient — zero cross-clinic leakage, verified by test.
- **SC-003**: An anonymized patient's hub always reflects the current scrubbed identity — zero instances of a stale pre-anonymization value being shown.
- **SC-004**: Zero new edit/delete controls exist anywhere in the hub for consultation notes or prescriptions.
- **SC-005**: The full test suite (frontend, and backend for the new query/field) passes after this feature with zero regressions to 030/031/032/033's own existing tests, plus new tests for the hub's own aggregation/display logic.

## Assumptions

- The hub is a **navigation/aggregation surface**, not a content-aggregation surface: Consultations/Prescriptions/External Records sections list the patient's relevant bookings as links into the existing per-booking pages, rather than fetching and inlining each note/prescription/record's own content on the hub page itself. Verified during planning that no patient-level list endpoint exists for any of the three (all are strictly `bookingId`-scoped), and that building one would mean re-implementing the treating-doctor authorization check at a new aggregate layer — a real duplication risk FR-006 explicitly forbids. Linking into the existing, unmodified per-booking pages (the same "condensed list linking into an existing detail page" pattern 048's dashboard already established) satisfies "one coherent place to navigate from" without that risk.
- A new, minimal backend addition is required (consistent with 048's own precedent of adding one minimal query when a real capability genuinely doesn't exist): a clinic+patient-scoped booking-list query, since verified that no such capability exists anywhere today (frontend or backend) — the closest existing query (`findByPatient_PatientAccount_IdOrderByCreatedAtDesc`) is patient-self-service-scoped by `PatientAccount` across all clinics, not staff-facing and not clinic-scoped.
- The existing patient-detail response is extended with the already-existing `Patient.anonymizedAt` field (no new column, no migration) — currently omitted from every frontend-facing DTO.
- "Book appointment"/"start consultation"/"add prescription" quick actions (named in the backlog brief) link to their real existing entry points (Day Sheet for booking; the booking-row's own consultation-note/prescription link for those) — this feature does not add a reverse "start from patient, pick a slot" booking flow, which would be new capability beyond what the brief asks for ("these already have real destinations... this feature makes them reachable... it does not reimplement them").
