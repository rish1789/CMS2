# Feature Specification: Per-Clinic Fees

**Feature Branch**: `claude/068-per-clinic-fees`

**Created**: 2026-10-01

**Status**: Draft

**Input**: User description: "SEC-03, owner decision B (2026-10-01): per-clinic fees. Appointment types stay shared per doctor, but each clinic sets its own prices for a doctor (default fee and per-type fee). Only the clinic's own ClinicAdmin may set that clinic's prices. When this ships, each doctor's current shared prices are copied to every clinic the doctor actively works at, so nothing changes on day one. Already-booked appointments keep their locked fee."

## Context

Today a doctor's appointment types **and their prices** belong to the doctor alone (spec 017, FR-005 to FR-007): one default fee and one optional fee per appointment type, shared by every clinic the doctor works at. Any active ClinicAdmin at any of those clinics may change them.

The product audit flagged this as **SEC-03**, a tenant-isolation gap: Clinic A's admin can change the prices patients pay when booking the same doctor at Clinic B. Clinic B never agreed to the change, and may not notice it. Each clinic is a separate business in this multi-tenant system, and the original backlog brief (015) already says "every clinic sets fees". On 2026-10-01 the owner chose per-clinic pricing (option B).

**What changes:** prices become clinic-specific, and only that clinic's admin may set them.
**What does not change:** the fee-resolution order, the hard block when no fee exists, the fee locking on bookings, and the doctor-level appointment types themselves.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - A clinic sets its own prices for a doctor (Priority: P1)

Dr. Rao works at Clinic A and Clinic B, which are separate businesses. Clinic A's admin sets Dr. Rao's consultation fee at Clinic A to ₹300. Clinic B's patients continue to pay Clinic B's price (₹500), unaffected.

**Why this priority**: This is the defect being fixed. One clinic must never change another clinic's prices.

**Independent Test**: Give a doctor different prices at two clinics. Book at each clinic and confirm each booking locks that clinic's price. Change the price at one clinic, then book at the other and confirm its price is unchanged.

**Acceptance Scenarios**:

1. **Given** a doctor staffed at Clinic A and Clinic B, each with its own default fee for the doctor (₹300 and ₹500), **When** a patient books the doctor at Clinic A, **Then** the booking's locked fee is ₹300, and a booking at Clinic B locks ₹500.
2. **Given** an appointment type with a type-specific price at Clinic A only, **When** it is booked at Clinic A, **Then** the type-specific price is locked; **When** it is booked at Clinic B, **Then** Clinic B's default fee for the doctor is locked.
3. **Given** Clinic A's admin changes the doctor's price at Clinic A, **When** the doctor is next booked at Clinic B, **Then** Clinic B's price is used, unchanged.
4. **Given** neither a type-specific price nor a default fee exists for the doctor **at the booking's clinic**, **When** a booking is attempted there, **Then** it is blocked with the existing "no fee configured" reason, even if the doctor has prices at another clinic.

---

### User Story 2 - Only the clinic's own admin can set its prices (Priority: P1)

A clinic's prices for a doctor may be set or changed only by an active ClinicAdmin **of that clinic**. An admin of a different clinic is refused, and so is the doctor.

**Why this priority**: Without this rule, the per-clinic prices in US1 could still be changed by an outsider, and SEC-03 would remain open.

**Independent Test**: Attempt to set a doctor's price at Clinic A as (a) Clinic A's admin, (b) Clinic B's admin where the doctor also works, (c) the doctor, (d) Clinic A's Operations staff. Only (a) succeeds.

**Acceptance Scenarios**:

1. **Given** an active ClinicAdmin of Clinic A, **When** they set a doctor's default fee or a type-specific price at Clinic A for a doctor actively staffed there, **Then** it is saved.
2. **Given** an active ClinicAdmin of Clinic B, **When** they try to set a price at Clinic A, **Then** the request is refused as forbidden and nothing changes.
3. **Given** the doctor themselves, or a non-admin staff member of Clinic A, **When** they try to set a price at Clinic A, **Then** the request is refused as forbidden.
4. **Given** a doctor who is not actively staffed at Clinic A, **When** Clinic A's admin tries to set that doctor's price at Clinic A, **Then** the request is refused.

---

### User Story 3 - Nothing changes on the day this ships (Priority: P1)

When this feature is deployed, every clinic keeps charging exactly what it charges today. Each doctor's current shared default fee and type-specific prices are copied to every clinic where the doctor is actively staffed.

**Why this priority**: Clinics and patients must not see a price change, or a sudden "cannot book", just because the data model changed.

**Independent Test**: Before the upgrade, record the price each (doctor, clinic, appointment type) resolves to. After the upgrade, every one resolves to the same amount.

**Acceptance Scenarios**:

1. **Given** a doctor with a shared default fee of ₹500, actively staffed at Clinics A and B, **When** the upgrade runs, **Then** both A and B have a default fee of ₹500 for that doctor.
2. **Given** a shared type-specific price of ₹800 for "Procedure", **When** the upgrade runs, **Then** each of the doctor's active clinics has an ₹800 price for "Procedure".
3. **Given** a doctor with no default fee today, **When** the upgrade runs, **Then** no default fee is created at any clinic, so the existing hard block is unchanged.
4. **Given** any booking made before the upgrade, **When** the upgrade runs, **Then** its locked fee is unchanged.

---

### User Story 4 - Staff and patients see the clinic's own prices (Priority: P2)

Wherever prices or booking readiness are shown, they reflect the clinic in question:
- the price-configuration screen;
- the "booking setup incomplete" warning;
- the appointment types offered when booking at a clinic.

**Why this priority**: Correct resolution (US1) protects the money. This story keeps what people *see* consistent with what they will be charged.

**Independent Test**: For a doctor priced differently at two clinics, view the appointment types offered for booking at each clinic and the readiness of the doctor at each clinic. Each shows that clinic's prices and that clinic's readiness.

**Acceptance Scenarios**:

1. **Given** a doctor with prices at Clinic A but none at Clinic B, **When** staff at each clinic view booking readiness, **Then** Clinic A shows the doctor ready and Clinic B shows "booking setup incomplete".
2. **Given** a patient booking a doctor at Clinic A, **When** the appointment types are listed, **Then** any prices shown are Clinic A's.
3. **Given** Clinic A's admin opens price configuration for a doctor, **When** the screen loads, **Then** it shows and edits Clinic A's prices only.

---

### Edge Cases

- **Doctor newly onboarded at a clinic after the upgrade:** the clinic has no prices for them yet, so booking there is blocked with the existing "no fee configured" reason, and readiness shows "setup incomplete" until that clinic's admin sets prices. Another clinic's prices are never used as a fallback.
- **Doctor's role at a clinic is deactivated:** that clinic's prices for the doctor remain stored but are unused, because booking there is already refused. Reactivation brings them back unchanged.
- **New appointment type created after the upgrade:** it has no price at any clinic, so it books at each clinic's default fee for the doctor until that clinic sets a type-specific price.
- **Appointment type renamed:** the shared name changes for all clinics, as today. Prices are unaffected.
- **Two admins of the same clinic set the same price at the same moment:** one value wins, and there is never more than one current default fee per (doctor, clinic), or more than one current price per (appointment type, clinic).
- **Clinic B's admin tries to read Clinic A's price configuration:** refused. Prices are clinic-scoped data.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST store a doctor's default fee **per clinic**: at most one per (doctor, clinic).
- **FR-002**: The system MUST store type-specific prices **per clinic**: at most one per (appointment type, clinic).
- **FR-003**: Fee resolution for a booking at a clinic MUST use that clinic's prices only, in the existing order: (1) the clinic's type-specific price for the booked appointment type, else (2) the clinic's default fee for the doctor, else (3) block the booking with the existing "no fee configured" reason. This applies to every booking path: patient fixed-time, patient queue, staff fixed-time, staff queue, front-desk walk-in, and waitlist claim.
- **FR-004**: Fee resolution MUST NEVER fall back to another clinic's prices, or to a doctor-wide price.
- **FR-005**: Only an active ClinicAdmin **of the clinic** MAY set or change that clinic's default fee or type-specific prices for a doctor, and only for a doctor actively staffed at that clinic. Everyone else, including the doctor and other clinics' admins, MUST be refused as forbidden, with no change made.
- **FR-006**: Reading a clinic's price configuration MUST be limited to that clinic's active staff and the doctor concerned.
- **FR-007**: Appointment types remain doctor-level. Creating, renaming and listing them keeps its existing authorization and behaviour, except that **a newly created appointment type carries no price**: prices are set per clinic under FR-005.
- **FR-008**: The upgrade MUST copy each doctor's existing default fee and existing type-specific prices to every clinic where the doctor holds an active role assignment, so that every (doctor, clinic, appointment type) resolves to the same amount after the upgrade as before it.
- **FR-009**: The locked fee of every existing booking MUST remain unchanged, and fee locking for new bookings MUST behave as today (backlog 015).
- **FR-010**: Booking readiness ("booking setup incomplete") MUST be evaluated per clinic: a doctor is fee-ready at a clinic only if that clinic has a default fee for them, or a type-specific price for every one of their appointment types.
- **FR-011**: Appointment types offered to a patient or staff member for booking at a clinic MUST show, where prices are shown, that clinic's prices.
- **FR-012**: The doctor-wide default-fee and type-price fields MUST stop being used to resolve fees once the upgrade has copied them (FR-008). They MUST NOT be silently edited any more through the old doctor-wide path.

### Key Entities

- **Appointment Type** (existing, doctor-level): the doctor's named service, such as "Consultation" or "Procedure". It no longer carries a price itself.
- **Clinic Default Fee** (new): the default price for a doctor **at a clinic**. At most one per (doctor, clinic).
- **Clinic Appointment Type Price** (new): the price for one appointment type **at a clinic**. At most one per (appointment type, clinic).
- **Booking** (existing): keeps its locked fee, resolved at creation from the booking's clinic.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: In 100% of tested cases, a price change by one clinic's admin leaves every other clinic's resolved fees for the same doctor unchanged.
- **SC-002**: 100% of attempts to set a clinic's prices by anyone other than that clinic's active ClinicAdmin are refused, with zero changes stored.
- **SC-003**: Immediately after the upgrade, 100% of (doctor, active clinic, appointment type) combinations resolve to the same fee as before the upgrade, and 100% of existing bookings keep their locked fee.
- **SC-004**: Every booking path resolves fees from the booking's own clinic, with zero cases of cross-clinic fallback.
- **SC-005**: For any doctor priced at one clinic and not at another, readiness shows "ready" at the first and "setup incomplete" at the second.

## Assumptions

- **Appointment type names stay shared per doctor.** Only prices are clinic-specific, as the owner was told when choosing option B.
- **Who manages appointment types:** creating and renaming them keeps today's rule (the doctor, or an admin of any of the doctor's clinics). The name is not a price, so it is outside SEC-03's money concern.
- **Inactive role assignments:** copying in FR-008 covers active role assignments only. A doctor's inactive clinics get no copied prices, which has no effect because they cannot be booked there. If the role is reactivated, that clinic sets its prices like a newly onboarded clinic.
- **Out of scope:** price history and an audit log of price changes, beyond what the system records today; currency (still flat INR cash amounts per 015); insurance, discounts and payments.
- **Migrations:** the change requires Flyway migrations (new tables and the copy). The constitution's test-first rule for invariant-enforcing migrations applies, and shipped migrations are not edited.
