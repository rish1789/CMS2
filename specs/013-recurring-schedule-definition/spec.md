# Feature Specification: Recurring Schedule Definition

**Feature Branch**: `013-recurring-schedule-definition`

**Created**: 2026-09-03

**Status**: Draft

**Input**: User description: "Recurring Schedule Definition — a ClinicAdmin (or the Doctor themselves) defines a doctor's recurring weekly schedule at a clinic: which days of the week, a start/end time range, and a mode (Fixed-Time with a slot interval, or Queue/Token with no slot interval). A Schedule is only the input pattern for a separate, not-yet-built nightly Session-generation job (011) — creating one does not itself create any bookable Session or Slot. Multi-clinic overlap blocking (010) and non-retroactive editing (014) are separate, not-yet-built features this one feeds; this feature itself only creates and lists Schedules. (Full source: backlog/009-recurring-schedule-definition.md)"

## Scope Decisions Made During Drafting

No prior conversation turn resolved these — they're research-backed defaults made while writing this spec, not a `/speckit-clarify` session (which runs next and may revisit any of them).

- **Overlap blocking (the source acceptance criterion "a new Schedule that would overlap an existing one is rejected") is explicitly out of scope for this feature.** The source material itself attributes that rule to a separate, later backlog item (010-multi-clinic-doctor-schedule-overlap-block, which build-order.md places immediately after this one, needing this feature's own Schedule entity to exist first). Building a fabricated overlap check now would duplicate work 010 is specifically scoped to own, and risks getting the rule's exact semantics (same clinic vs. cross-clinic, what counts as "overlap") wrong ahead of that feature's own dedicated design pass — the same reasoning this backlog's own build-order.md documents for other forward-referencing features (e.g. 036 deferring wiring to features that don't exist yet).
- **Editing an existing Schedule is out of scope for this feature.** The source business rules describe edit's *non-retroactivity guarantee*, but that guarantee is itself a separate backlog item (014-schedule-edit-non-retroactivity), which needs both this feature's Schedule entity AND 011's Session-generation output to exist before its own non-retroactivity behavior is even meaningful to build or test. This feature ships create + read (list) only.
- **A Schedule requires the named Doctor to actually have an active Doctor-role Role Assignment at the named Clinic.** Not stated explicitly in the source acceptance criteria, but implied by "a doctor's recurring weekly schedule **at a clinic**" — a schedule for a doctor with no staffing relationship to that clinic would be meaningless and would corrupt every downstream feature (010's overlap check, 011's generation, discovery) that assumes a Schedule's clinic/doctor pairing reflects a real assignment. This mirrors 007's existing discovery-eligibility gate's use of the same active-Role-Assignment signal.
- **A Schedule groups multiple days of the week sharing one time range and mode into a single record**, not one record per day. The source example ("Mon/Wed/Fri 9am–1pm") describes one coherent pattern; modeling it as one row keeps "editing a Schedule" (a future feature's concern) atomic across all its days, and matches "a Schedule defines a recurring day-of-week + time-range pattern" (singular pattern, plural days) in the source business rules.
- **A Queue/Token Schedule that supplies a slot interval is rejected, not silently ignored.** The source material says a Queue/Token Schedule has "no fixed slot-interval configuration, since tokens are sequential" — treated here as a validation rule (a slot interval is meaningless in that mode, so submitting one is treated as a mistake worth surfacing) rather than a value this feature would silently discard.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - ClinicAdmin Defines a Doctor's Recurring Schedule (Priority: P1)

A ClinicAdmin sets up a recurring weekly pattern for one of their clinic's doctors — which days, what time range, and whether the doctor sees patients in Fixed-Time slots or Queue/Token order — so that future nightly Session generation (011, not yet built) has a real pattern to work from.

**Why this priority**: This is the entire reason the feature exists — without a way to define a Schedule at all, nothing downstream (session generation, booking) has anything to generate from.

**Independent Test**: As an authenticated ClinicAdmin for a clinic, submit a Fixed-Time schedule (days, time range, slot interval) for a doctor actively assigned to that clinic; confirm it's persisted with exactly the submitted pattern and is retrievable via the list endpoint.

**Acceptance Scenarios**:

1. **Given** a Doctor with an active Role Assignment at a Clinic, **When** the Clinic's ClinicAdmin submits a Fixed-Time schedule (e.g. Mon/Wed/Fri, 9:00–13:00, 15-minute slots), **Then** it is saved exactly as submitted and appears in that doctor's schedule list for that clinic.
2. **Given** the same setup, **When** the ClinicAdmin submits a Queue/Token schedule (e.g. Tue/Thu, 14:00–17:00, no slot interval), **Then** it is saved with mode Queue/Token and no slot-interval value.
3. **Given** a Queue/Token schedule submission that includes a slot interval anyway, **When** it is submitted, **Then** it is rejected as invalid.
4. **Given** a Fixed-Time schedule submission with no slot interval (or a non-positive one), **When** it is submitted, **Then** it is rejected as invalid.
5. **Given** a submission with a start time not earlier than its end time, **When** it is submitted, **Then** it is rejected as invalid.
6. **Given** a submission naming a Doctor who has no active Role Assignment at the named Clinic, **When** it is submitted, **Then** it is rejected.
7. **Given** an authenticated staff member who is neither a ClinicAdmin for the named clinic nor the named doctor themselves, **When** they attempt to submit a schedule, **Then** the request is rejected as forbidden.

---

### User Story 2 - The Doctor Defines Their Own Schedule (Priority: P2)

A Doctor, logged in with their own staff credentials, defines their own recurring schedule at a clinic they're actively assigned to — without needing a ClinicAdmin to do it on their behalf.

**Why this priority**: Explicitly named in the source user story ("a ClinicAdmin (or the Doctor themselves)") as a second legitimate actor for the same action — same underlying capability as User Story 1, just a different authorized caller, so it's additive rather than foundational.

**Independent Test**: Authenticated as the Doctor's own staff account (with an active Role Assignment at the clinic), submit a schedule for themselves at that clinic; confirm it succeeds identically to User Story 1's ClinicAdmin path.

**Acceptance Scenarios**:

1. **Given** a Doctor account with an active Role Assignment at a Clinic, **When** that Doctor submits a schedule for themselves at that clinic, **Then** it succeeds and is saved identically to a ClinicAdmin-submitted one.
2. **Given** a Doctor account, **When** they attempt to submit a schedule naming a *different* doctor, **Then** the request is rejected as forbidden (a Doctor may only define their own schedule, never another doctor's).

---

### Edge Cases

- What happens when a Schedule is submitted for a doctor/clinic pair where the doctor's Role Assignment exists but is inactive (deactivated per 005)? → Rejected, identically to a doctor with no Role Assignment at that clinic at all — an inactive assignment carries no current staffing relationship.
- What happens when the same doctor already has other Schedules at the same or a different clinic? → No overlap check is performed by this feature (out of scope, see Scope Decisions) — the new Schedule is simply created alongside the existing ones.
- What happens when `daysOfWeek` is submitted empty? → Rejected as invalid — a schedule with no days is not a recurring pattern.
- What happens when a duplicate day is submitted twice in the same `daysOfWeek` list? → Treated as the same day once (de-duplicated), not rejected — the intent ("this schedule runs on Monday") is unambiguous even if the list has a repeat.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST allow an authenticated ClinicAdmin of a given clinic to create a recurring weekly Schedule for any doctor with an active Role Assignment at that same clinic.
- **FR-002**: System MUST allow an authenticated Doctor to create a recurring weekly Schedule for *themselves* at a clinic where they hold an active Role Assignment, and MUST reject an attempt by a Doctor to create a Schedule naming a different doctor.
- **FR-003**: System MUST reject a Schedule creation request from any authenticated staff member who is neither an active ClinicAdmin at the named clinic nor the named doctor themselves.
- **FR-004**: A Schedule MUST record: one or more days of the week, a start time, an end time, and a mode (Fixed-Time or Queue/Token).
- **FR-005**: System MUST reject a Schedule whose start time is not strictly earlier than its end time.
- **FR-006**: System MUST reject a Schedule with zero days of the week; a day repeated within the submission MUST be treated as a single occurrence, not rejected.
- **FR-007**: A Fixed-Time Schedule MUST record a positive slot-interval duration; a submission in Fixed-Time mode with no slot interval, or a non-positive one, MUST be rejected.
- **FR-008**: A Queue/Token Schedule MUST NOT record a slot-interval value; a submission in Queue/Token mode that supplies one MUST be rejected.
- **FR-009**: System MUST reject a Schedule naming a doctor who has no *active* Role Assignment at the named clinic (inactive or absent both rejected identically).
- **FR-010**: System MUST provide a way to list the Schedules that exist for a given doctor at a given clinic, viewable by the same two actor types as creation (that clinic's ClinicAdmin, or the named doctor themselves).
- **FR-011**: Creating a Schedule MUST NOT itself create, modify, or reference any Session or Slot — a Schedule is only ever an input pattern for a separate, future generation process.

### Key Entities

- **Schedule** (new entity, first defined by this feature): the recurring weekly pattern. Fields: the Doctor Profile and Clinic it belongs to, a set of days of the week, a start time, an end time, a mode (Fixed-Time or Queue/Token), an optional slot-interval duration (present only in Fixed-Time mode), a created timestamp.
- **DoctorProfile** (from 005, read-only here): the doctor a Schedule is defined for.
- **Clinic** (from 001, read-only here): the clinic a Schedule is scoped to.
- **RoleAssignment** (from 004, read-only here): supplies both the active-staffing-relationship gate (FR-009) and the authorization check (FR-001–FR-003).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of valid Fixed-Time and Queue/Token schedule submissions, across every tested field combination, are persisted exactly as submitted and are retrievable via the list capability immediately afterward.
- **SC-002**: 100% of submissions violating any single validation rule (bad time range, zero days, mode/slot-interval mismatch, inactive/absent doctor-clinic relationship) are rejected, with zero partial/malformed Schedule rows ever persisted.
- **SC-003**: 100% of creation/listing attempts by an actor who is neither the named clinic's ClinicAdmin nor the named doctor are rejected as forbidden.
- **SC-004**: 100% of Schedule creations result in zero Session or Slot rows being created, modified, or referenced (verified directly: no Session/Slot entity or table exists anywhere in the codebase at the time this feature is built).

## Assumptions

- **No Session or Slot entity exists yet anywhere in this codebase** — 011 (nightly rolling Session generation) is later in build order and hasn't been built. FR-011/SC-004 are stated as a forward-looking guarantee about this feature's own scope, not something requiring an existing Session table to verify against.
- **The "which actor may act" rule reuses the existing `RoleAssignmentRepository.existsByAccount_IdAndClinic_IdAndRoleAndActiveTrue` signal** (already used by 004's onboarding authorization) for both the ClinicAdmin check and the Doctor-self check — no new authorization mechanism is introduced.
- **No HTTP field exists for "which staff member is creating this on the doctor's behalf" beyond the caller's own authenticated identity** — the caller is either the clinic's ClinicAdmin or the doctor themselves; there is no third delegated-creator concept in the source material.
