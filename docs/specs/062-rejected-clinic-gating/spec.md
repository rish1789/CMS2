# Feature Specification: Rejected Clinics Stop Operating

**Feature Branch**: `062-rejected-clinic-gating`

**Created**: 2026-09-24

**Status**: Draft

**Input**: User description: "Rejected clinics stop operating. When a Super Admin rejects a clinic registration, that clinic must no longer be able to take appointments. Today nothing enforces this: verification only gates public discovery (backlog 001/002), so a rejected clinic's schedules keep generating Sessions in the nightly rolling session-generation job (backlog 011), and bookings are still accepted at it. Required: nightly session generation skips rejected clinics; every booking path refuses a rejected clinic with a clear error; Restore re-enables both. Pending clinics keep today's behavior. Out of scope: changing verification semantics for pending clinics; force-deleting rejected clinics with attached activity."

## Clarifications

### Session 2026-09-24

- Q: When a clinic is rejected, what happens to its future bookings that already exist? → A: Auto-cancel them, and the patient sees a message in their patient console explaining the clinic is no longer accepting appointments.
- Q: Can staff at a rejected clinic still sign in to the staff console? → A: Only the ClinicAdmin can sign in; Doctor and Operations staff of that clinic cannot.

## Background

Clinic verification (backlog 001/002) was designed to gate **public discovery only**: an unverified clinic can still onboard staff, build schedules and take bookings, it just isn't listed publicly. A later Super Admin console change added a third state, **Rejected** (with a reason code, reversible via Restore, eventually purgeable via the guarded permanent-delete). No backlog item defines what a rejected clinic may still do, and the code treats it exactly like a pending one.

Found 2026-09-24 in the development database: all 7 rejected clinics had Sessions generated *after* their rejection date (up to 30 per clinic), all 7 had future Sessions open for booking, and one had 5 bookings created after rejection. The product owner's stated rule is that a rejected clinic has no right to take appointments.

## Clinic States (for reference)

| State | Discoverable publicly | Takes appointments today | Takes appointments after this feature |
|---|---|---|---|
| Pending (unverified, not rejected) | No | Yes | Yes — unchanged |
| Verified | Yes | Yes | Yes — unchanged |
| Rejected | No | Yes (the bug) | **No** |

## User Scenarios & Testing *(mandatory)*

### User Story 1 - No appointment can be booked at a rejected clinic (Priority: P1)

As the platform operator, once I reject a clinic registration I need every way of booking an appointment there to refuse, so a clinic I've judged illegitimate can't take patients' appointments.

**Why this priority**: This is the rule the product owner stated and the direct harm — a patient holding an appointment at a clinic the platform has rejected.

**Independent Test**: Reject a clinic that has open Fixed-Time slots and a Queue session, then attempt each booking path (patient self-service fixed-time, patient queue token, staff-assisted, walk-in, waitlist claim). Every attempt is refused with a clear message and no booking is created.

**Acceptance Scenarios**:

1. **Given** a rejected clinic with an open Fixed-Time slot, **When** a patient tries to book it, **Then** the booking is refused with a message saying the clinic isn't accepting appointments, and no booking exists.
2. **Given** a rejected clinic with a Queue-mode session, **When** a patient tries to take a queue token, **Then** it is refused the same way.
3. **Given** a rejected clinic, **When** staff try a staff-assisted booking or a walk-in insertion, **Then** it is refused the same way.
4. **Given** a waitlist offer outstanding at a clinic that is then rejected, **When** the patient tries to claim it, **Then** the claim is refused the same way.
5. **Given** a pending (unverified, not rejected) clinic, **When** any of the above is attempted, **Then** it behaves exactly as today.
6. **Given** a clinic with upcoming bookings (still booked, today or later), **When** the Super Admin rejects it, **Then** every one of them is cancelled, no waitlist offer is made for the freed slots, and past or already-attended bookings are untouched (FR-009).
7. **Given** a patient whose booking was cancelled that way, **When** they open My bookings in the patient console, **Then** that booking shows as Cancelled with the line "This clinic is no longer accepting appointments." (FR-010).
8. **Given** open waitlist entries or outstanding offers at the clinic, **When** it is rejected, **Then** they are closed (FR-011).

---

### User Story 2 - A rejected clinic stops generating new sessions (Priority: P2)

As the platform operator, I need the nightly session generation to skip rejected clinics, so their schedules don't keep producing bookable sessions while the clinic is rejected.

**Why this priority**: Booking refusal (P1) already prevents harm; this removes the pile-up of dead sessions and keeps staff screens honest.

**Independent Test**: Reject a clinic with an active recurring schedule, run the session-generation job (nightly or its manual trigger), and confirm no new sessions or slots are created for it while other clinics' generation is unaffected.

**Acceptance Scenarios**:

1. **Given** a rejected clinic with an active recurring schedule, **When** the nightly job runs, **Then** no new session is created for that clinic.
2. **Given** the same job run, **When** it processes other pending or verified clinics, **Then** their sessions are generated exactly as today.
3. **Given** the Super Admin manually triggers session generation, **When** it runs, **Then** rejected clinics are skipped the same way.

---

### User Story 3 - Restoring a rejected clinic resumes normal operation (Priority: P3)

As the platform operator, if I restore a clinic I rejected by mistake, it should go straight back to operating like any pending clinic.

**Why this priority**: Rejection is reversible by design; without this the restore action would leave a clinic half-disabled.

**Independent Test**: Reject then restore a clinic; confirm bookings are accepted again and the next session-generation run creates its sessions (catching up the rolling window).

**Acceptance Scenarios**:

1. **Given** a clinic that was rejected and then restored, **When** a booking is attempted, **Then** it is accepted as for any pending clinic.
2. **Given** the same clinic, **When** the next session-generation run happens, **Then** its sessions are generated for the full rolling window, including days skipped while rejected.

---

### User Story 4 - Only the clinic admin keeps access to a rejected clinic (Priority: P2)

As the platform operator, once a clinic is rejected I want its doctors and operations staff to lose access to it, while its ClinicAdmin can still sign in to see the clinic's records.

**Why this priority**: Stops day-to-day operation at the staff level, per the 2026-09-24 clarification; booking refusal (P1) already prevents the direct harm.

**Independent Test**: Reject a clinic, then sign in as its Doctor (refused with a clear message), reuse the Doctor's earlier session on a clinic page (refused), and sign in as its ClinicAdmin (succeeds, clinic still listed, booking still refused).

**Acceptance Scenarios**:

1. **Given** a Doctor or Operations user whose only clinic is rejected, **When** they sign in, **Then** sign-in is refused with "Your clinic is not currently active".
2. **Given** such a user still holding a session from before the rejection, **When** they open any page of that clinic, **Then** the request is refused the same way.
3. **Given** the clinic's ClinicAdmin, **When** they sign in, **Then** they get in, see the clinic in their clinic list, and can view its records.
4. **Given** a doctor who also works at a normal clinic, **When** they sign in, **Then** they get in and can use the normal clinic, but not the rejected one.
5. **Given** the clinic is restored, **When** its Doctor signs in, **Then** access works as before.

---

### Edge Cases

- **Rejection while a booking is in flight**: a booking request that is being processed at the moment of rejection may complete; the refusal applies to requests that start after the clinic is rejected. No partial booking is ever left behind.
- **Existing future bookings at the moment of rejection**: auto-cancelled (FR-009); the patient sees why in their console (FR-010). The freed slots are not offered to the waitlist, since the clinic can't take them.
- **Restore after an auto-cancel**: bookings cancelled by a rejection stay cancelled — restoring the clinic does not reinstate them (same rule as the 008 de-verification cascade); patients rebook from scratch.
- **Waitlist entries at a rejected clinic**: open waitlist entries and outstanding offers at the clinic are closed when it is rejected, so no one keeps waiting for a clinic that can't serve them.
- **A person with roles at more than one clinic**: the sign-in rule (FR-007) applies per clinic — a doctor who also works at a normal clinic can still sign in and use that clinic; they only lose access to the rejected one.
- **Past and completed bookings** are never touched by this feature.
- **A doctor who works at several clinics**: rejecting one clinic affects only that clinic's sessions and bookings; the doctor's schedules at other clinics are unaffected.
- **A clinic that was verified and is later rejected**: rejection is only allowed from Pending (a verified clinic must be un-verified first, which already fires the 008 cascade), so this path doesn't arise.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST refuse to create a booking at a rejected clinic through every booking path: patient self-service fixed-time booking (017), patient queue-token booking (018), staff-assisted booking (016), walk-in insertion (020), and waitlist-offer claim (029).
- **FR-002**: A refusal under FR-001 MUST tell the caller, in plain language, that the clinic is not accepting appointments, and MUST be distinguishable from "slot no longer available" or validation errors.
- **FR-003**: The check in FR-001 MUST be enforced by the server for every request, not only by hiding options in the user interface.
- **FR-004**: The nightly rolling session generation (011), and its manual trigger, MUST skip every rejected clinic and MUST NOT create sessions or slots for it.
- **FR-005**: When a rejected clinic is restored, FR-001 and FR-004 MUST stop applying immediately; the next session-generation run MUST fill that clinic's full rolling window.
- **FR-006**: Pending and verified clinics MUST behave exactly as before this feature on every path it touches.
- **FR-007**: At a rejected clinic, only the ClinicAdmin role keeps staff-console access (still subject to FR-001 — they cannot book). Doctor and Operations roles at that clinic grant no access: every request they make scoped to that clinic is refused, and a staff member whose only role anywhere is Doctor/Operations at rejected clinics is refused at sign-in with a plain-language message that their clinic is not currently active. Access returns automatically on restore.
- **FR-009**: When a clinic is rejected, every future booking at it (appointment time after the moment of rejection, status active) MUST be cancelled automatically in the same action. Past and completed bookings MUST NOT be touched. Cancellations caused by rejection MUST NOT trigger waitlist offers.
- **FR-010**: A patient whose booking was cancelled by a rejection MUST see, on that booking in their patient console, a plain-language message that it was cancelled because the clinic is no longer accepting appointments — distinct from a patient- or staff-initiated cancellation.
- **FR-011**: When a clinic is rejected, its open waitlist entries and any outstanding waitlist offers MUST be closed.
- **FR-008**: Every clinic-scoped check introduced by this feature MUST be tenant-scoped — it reads only the clinic the request is about.

### Key Entities

- **Clinic** *(existing)*: already carries `verified` and `rejected` (with reason, detail, who rejected and when). This feature reads `rejected`; it adds no new stored data.
- **Session / Slot** *(existing)*: generation is skipped for a rejected clinic; existing rows are handled per the clarification on existing future bookings.
- **Booking** *(existing)*: creation is refused at a rejected clinic; future bookings are cancelled on rejection with a distinct cancellation reason the patient can see.
- **Role Assignment** *(existing)*: Doctor/Operations assignments at a rejected clinic grant no access while it stays rejected; ClinicAdmin assignments keep access.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 0 bookings can be created at a rejected clinic through any of the 5 booking paths, verified by attempting each one.
- **SC-002**: 0 sessions are generated for a rejected clinic by any session-generation run after its rejection.
- **SC-003**: After a restore, the clinic accepts its first booking without any extra admin step, and its rolling session window is fully regenerated by the next run.
- **SC-005**: After a rejection, 100% of that clinic's future active bookings are cancelled, and each affected patient's console shows the rejection message on that booking.
- **SC-006**: A Doctor or Operations user whose only clinic is rejected cannot sign in; the ClinicAdmin of that clinic can.
- **SC-004**: Pending and verified clinics show no change: every existing automated test covering booking and session generation passes unchanged.

## Assumptions

- Rejection can only be applied to a pending clinic (the existing Reject action already refuses verified clinics), so no verified-clinic cascade interaction is needed.
- Schedules, doctors, staff accounts, and appointment types at a rejected clinic are left as they are; only booking and session generation are gated. Editing a schedule while rejected is allowed but produces no sessions until restore.
- Public discovery already excludes rejected clinics (they are not verified), so discovery needs no change.
- The existing guarded permanent-delete of rejected clinics stays as it is; force-deleting a rejected clinic with attached activity remains out of scope.
- Rejection auto-cancels publish a notification event through the existing pipeline (036/037) with its own event type, `BOOKING_CANCELLED_CLINIC_REJECTED`, mirroring 008's `BOOKING_CANCELLED_DEVERIFICATION`; no new delivery channel is added.

## Out of Scope

- Changing what pending (unverified) clinics can do — verification continues to gate discovery only.
- Force-deleting rejected clinics that have bookings, sessions, or clinical records attached.
- Automatically rejecting clinics, or any new review workflow.
