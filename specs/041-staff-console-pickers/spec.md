# Feature Specification: Clinic Staff Console — Browse & Pick Instead of Type-an-ID

**Feature Branch**: `041-staff-console-pickers`

**Created**: 2026-09-08

**Status**: Draft

**Input**: User description: "Build browse/picker views for the clinic staff console, replacing the raw-UUID 'type an ID and click Go' pattern found across the staff surface. [...] the goal is every staff tool that currently asks for a typed ID to instead let staff pick from a real list, with typing an ID no longer required anywhere in the staff console for entities this feature covers."

## Codebase Context *(carried forward from a frontend/backend audit performed immediately before this spec)*

Today, a clinic staff member's entire experience after logging in is a sequence of "type a raw ID, click Go" forms, with no way to discover any ID through the interface:

- The very first screen after staff login (`StaffDashboard`) asks for a **Clinic ID** typed by hand — there is no list of the clinics that staff member actually belongs to.
- Once "inside" a clinic, the clinic tools dashboard is 16 near-identical launcher cards, most requiring a hand-typed **Slot ID**, **Session ID**, **Booking ID**, **Patient ID**, **Doctor Profile ID**, or staff **Account ID** before anything can be done.
- A backend audit confirmed this isn't just a missing frontend view — the underlying list queries mostly don't exist yet. Today the backend can only list: a Super Admin's pending clinics/doctors (Super-Admin-only), a clinic's Inbox items, a doctor's own defined schedules, and a patient's own bookings/waitlist (patient-facing only). There is no endpoint to list clinics a staff member belongs to, sessions for a clinic, slots for a session (staff-side), bookings for a clinic/session, doctors staffed at a clinic, or patients at a clinic.

This feature closes that gap: it adds the missing backend list queries and the frontend browse/picker views on top of them, so a staff member never has to already know an ID before they can act on it.

## Clarifications

### Session 2026-09-08

- Q: Should buffer slots (the capacity-reserve slots from the no-show buffer feature) appear as normal bookable rows in the day sheet, or be visually distinguished/excluded from direct booking? → A: Buffer slots show a distinct visual marker (e.g. "Reserved capacity") and offer no direct book action — staff still use the existing "Insert a walk-in" tool (which already implements the correct buffer → no-show-freed → regular priority order) to fill them.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Pick a clinic instead of typing its ID (Priority: P1)

A staff member logs in and sees a list of the clinics they actually work at (by name, not ID), and clicks one to enter that clinic's workspace — instead of the current blank "Clinic ID" box.

**Why this priority**: This is the very first screen every staff member hits after login, every single day. It's the single highest-friction point in the whole console, and removing it alone makes every previously-blocked path reachable via manual URL editing become reachable via a real click. Independently valuable and independently shippable.

**Independent Test**: Log in as a staff member with role assignments at 2+ clinics; confirm both clinic names appear in a list; click one and land in that clinic's workspace.

**Acceptance Scenarios**:

1. **Given** a staff member with an active role at one or more clinics, **When** they land on the dashboard after login, **Then** they see each of those clinics listed by name (not by typing an ID).
2. **Given** a staff member clicks a listed clinic, **When** the click is handled, **Then** they enter that clinic's workspace exactly as if they had typed its ID today.
3. **Given** a staff member has no active role at any clinic, **When** they land on the dashboard, **Then** they see a clear message explaining there's nothing to show, not an empty or broken list.

---

### User Story 2 - Browse a clinic's day sheet to find and act on a session, slot, or booking (Priority: P1)

Inside a clinic, a staff member sees a list of upcoming sessions (by doctor and date, not by typing a Session ID), opens one to see its slots and any patient already booked into each, and can act directly from there (book, cancel, mark a walk-in, view queue position, complete a slot, add a consultation note/prescription/external record) — instead of separately typing a Session ID, Slot ID, or Booking ID into 8+ different launcher cards.

**Why this priority**: This is where staff spend most of their actual working time (booking, walk-ins, cancellations, queue management, clinical documentation) and where the "type an ID" pattern is most acute (8 of the 16 current tool cards need a Session, Slot, or Booking ID). Tied at P1 with User Story 1 because together they cover the console's two biggest friction points; sequenced after US1 only because a session list is naturally reached by first picking a clinic.

**Independent Test**: Inside a clinic with at least one generated session, confirm the session list shows real sessions (doctor, date, mode); open one and confirm its slots show status and, where booked, the patient's name; trigger one action (e.g. cancel a booking) directly from that view.

**Acceptance Scenarios**:

1. **Given** a clinic has one or more sessions, **When** a staff member views that clinic's day sheet, **Then** each session is listed with its doctor, date, and mode (Fixed-Time or Queue/Token) — no Session ID typed.
2. **Given** a staff member opens a Fixed-Time session, **When** the session's slots load, **Then** each slot shows its time, status (open, booked, completed, no-show), and — if booked — the patient's name, with no Slot ID or Booking ID typed to see this. A buffer slot is shown as reserved capacity, not as an ordinary bookable open slot.
3. **Given** a staff member opens a Queue/Token session, **When** its slots load, **Then** each issued token is listed with its status and, if booked, the patient's name.
4. **Given** a staff member is viewing a booked slot, **When** they choose an available action for it (cancel, queue position, consultation note, prescription, external record, mark complete), **Then** that action opens already knowing the Booking ID — never asking the staff member to type it.
5. **Given** a clinic has no sessions yet, **When** a staff member views the day sheet, **Then** they see a clear empty state, not a broken or blank list.

---

### User Story 3 - Pick a patient instead of typing their ID (Priority: P2)

A staff member searches for a patient by name or phone number (not a Patient ID) when they need to act on a specific patient record directly (e.g. anonymize on request) rather than through a booking they're already looking at.

**Why this priority**: Lower-frequency than day-to-day booking (User Story 2 already surfaces the patient tied to a specific booking) — this covers the one remaining tool (anonymize) that targets a patient directly rather than through a booking.

**Independent Test**: Search for a known patient by partial name or phone at a clinic; confirm they appear in results; select them and confirm the anonymize action opens already knowing the Patient ID.

**Acceptance Scenarios**:

1. **Given** a staff member types part of a patient's name or phone number, **When** results load, **Then** matching patients at that clinic are shown by name (not ID).
2. **Given** no patient matches the search, **When** results load, **Then** a clear "no matches" state is shown, not an error.

---

### User Story 4 - Pick a doctor or staff member instead of typing their ID (Priority: P3)

A staff member sees a list of doctors staffed at their clinic (to define a schedule or manage appointment types) or a list of active staff accounts at their clinic (to deactivate one), instead of typing a Doctor Profile ID or Account ID.

**Why this priority**: Lowest frequency of the four stories — schedule definition and staff deactivation are occasional administrative actions, not day-to-day operational ones. Included for completeness (0 typed IDs left anywhere in the console) but least urgent to ship first.

**Independent Test**: At a clinic with 2+ staffed doctors, confirm both appear by name in a list for "Define a schedule"; at a clinic with 2+ active staff, confirm both appear by name for "Deactivate staff".

**Acceptance Scenarios**:

1. **Given** a clinic has one or more staffed doctors, **When** a staff member opens "Define a schedule" or "Manage appointment types", **Then** they pick the doctor by name and specialization, not by typing an ID.
2. **Given** a clinic has one or more active staff accounts, **When** a staff member opens "Deactivate staff", **Then** they pick the staff member by name and role, not by typing an ID.

### Edge Cases

- A staff member's role at a clinic is deactivated while they're viewing that clinic's day sheet — the next action they take against it must be rejected the same way it already is today (existing authorization checks are unchanged by this feature); this feature only affects how the ID was found, not what's authorized once found.
- A session, slot, or booking is cancelled/modified by someone else between when the staff member loaded the list and when they act on a specific row — the existing per-action error handling (e.g. "already booked", "already cancelled") is unchanged; the list may briefly show stale data until refreshed, which is acceptable.
- A clinic has a very large number of past sessions accumulated over time — the day sheet must not attempt to load an unbounded history by default (see Assumptions).
- A staff member has an active role at a clinic that has since been de-verified (008) — whether that clinic still appears in their clinic list is governed by whether their role assignment is still active, not by the clinic's verification status (verification and staffing are independent, per the existing de-verification cascade feature).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST let a staff member see, immediately after login, the list of clinics where they currently have an active role — by clinic name, without typing a clinic identifier.
- **FR-002**: Selecting a clinic from that list MUST take the staff member into that clinic's workspace, equivalent to today's manual clinic-ID entry.
- **FR-003**: Inside a clinic, the system MUST let a staff member see a list of that clinic's sessions (doctor, date, mode) without typing a session identifier.
- **FR-004**: Opening a session from that list MUST show its slots, each slot's status, and — for a booked slot — the booked patient's name, without typing a slot or booking identifier. A buffer slot MUST be visibly marked as reserved capacity, distinct from an ordinary open slot.
- **FR-005**: From a slot/booking shown in that view, the system MUST let a staff member launch any of the existing booking-related actions (book, cancel, walk-in insertion, queue position, mark complete, consultation note, prescription, external record reference) already carrying the correct session/slot/booking identifiers, without the staff member typing any of them. A buffer slot MUST NOT offer a direct "book" action — filling it remains only reachable through the existing walk-in insertion tool.
- **FR-006**: The system MUST let a staff member see a list of doctors currently staffed at their clinic (name and specialization) without typing a Doctor Profile ID, for the "Define a schedule" and "Manage appointment types" tools.
- **FR-006a**: The system MUST let a staff member see a list of staff accounts active at their clinic (name and role) without typing an Account ID, for the "Deactivate staff" tool.
- **FR-007**: The system MUST let a staff member search for a patient at their clinic by name or phone number and select a match, without typing a Patient ID, for tools that act on a patient directly (not via an already-open booking). An already-anonymized patient (033) MUST NOT appear in search results — there is nothing a staff member can act on once a record is anonymized.
- **FR-008**: The clinic's session list (the "day sheet") MUST default to a fixed near-term window — today through the next 14 days — matching the existing nightly session-generation horizon; this feature does not add a way to browse further back or forward.
- **FR-009**: All list/picker views MUST show a clear loading state while fetching and a clear empty state when there is genuinely nothing to show, distinct from an error state.
- **FR-010**: This feature MUST NOT change what any existing action is authorized to do or who it's authorized for — it only changes how the identifiers that action needs are discovered beforehand.
- **FR-011** *(post-convergence follow-up, user-requested)*: A staff member whose only active role at a clinic is Doctor MUST see only their own sessions in the session list and day sheet (FR-003/FR-004) — never another doctor's. A staff member who also holds ClinicAdmin or Operations at that clinic is unaffected and continues to see every doctor's sessions. This is a view-only visibility scope, not a change to any write-action's authorization (FR-010 still holds).

### Key Entities

- **Clinic membership (existing, newly surfaced)**: an active role a staff member holds at a clinic — already modeled, just not currently listable by the staff member who holds it.
- **Session (existing, newly surfaced)**: a clinic's scheduled block for a doctor on a date, already carrying its own mode (Fixed-Time / Queue) — not currently listable by clinic.
- **Slot (existing, newly surfaced)**: a bookable unit within a session, already carrying its status and, when booked, its Booking — not currently listable by session.
- **Patient (existing, newly surfaced)**: a clinic-scoped patient record, already carrying name and phone — not currently searchable by clinic.
- **Doctor staffing (existing, newly surfaced)**: an active Doctor role at a clinic — not currently listable by clinic.
- **Staff account (existing, newly surfaced)**: an active staff role (ClinicAdmin/Doctor/Operations) at a clinic, tied to an Account — not currently listable by clinic.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A staff member can go from login to viewing a specific booked patient's slot inside a real session, using only clicks (no typed identifier), in under 30 seconds for a clinic with a normal day's sessions.
- **SC-002**: 100% of the identifier-typing fields named in FR-001 through FR-007 (clinic, session, slot, booking, doctor, staff account, patient) are replaced by a pick-from-a-list interaction; 0% regression in what those underlying actions can do once reached.
- **SC-003**: A staff member with no clinics, no sessions, or no matching patient search results always sees a clear, specific empty state — 0% of these cases render a blank or broken screen.
- **SC-004**: Every existing action reachable via a typed ID today (book, cancel, walk-in, queue position, mark complete, consultation note, prescription, external record, anonymize) remains fully functional when reached via its new picker — 0 regressions in existing, already-converged booking/scheduling/clinical-documentation features.

## Assumptions

- This feature adds new backend list/search endpoints; it does not change any existing entity's schema (no new Flyway migration expected) — every field these lists need (session date/mode/doctor, slot status/booking/patient, patient name/phone, role assignment) already exists.
- Authorization for every new list endpoint mirrors the authorization already enforced on the corresponding action endpoints (e.g., a clinic's session list is visible to the same staff who can already act within that clinic) — this feature adds no new authorization concept.
- The day sheet's session list defaults to a bounded, near-term window (today + 14 days, FR-008) rather than an unbounded history — an unbounded list would grow without limit as the nightly job keeps generating sessions.
- Patient search matches on partial name and/or phone, clinic-scoped only (never across clinics) — consistent with `Patient` being a clinic-scoped entity everywhere else in this system.
- This feature does not add a way to create a new clinic, session, or patient from within these list views — it only makes existing records discoverable; creation flows (staff onboarding, schedule definition, walk-in patient creation) are unchanged.
