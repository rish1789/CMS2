# Feature Specification: Schedule Edit Non-Retroactivity

**Feature Branch**: `016-schedule-edit-non-retroactivity`

**Created**: 2026-09-03

**Status**: Draft

**Input**: User description: "Schedule Edit Non-Retroactivity — a ClinicAdmin or the Doctor themselves can edit an existing Recurring Schedule's days-of-week, time window, mode, and slot-interval, and the edit never modifies any Session already generated from it (011) — already-generated Sessions retain the exact configuration they were created with, permanently; only future nightly-job runs (011) pick up the edited pattern. The edit re-validates the same field rules 009's create endpoint already enforces, and re-runs 010's overlap check against the doctor's other schedules (excluding the one being edited) so an edit can never introduce an overlap that a fresh create would have been rejected for. (Full source: backlog/014-schedule-edit-non-retroactivity.md)"

## Scope Decisions Made During Drafting

No prior conversation turn resolved these — they're research-backed defaults made while writing this spec, not a `/speckit-clarify` session (which runs next and may revisit any of them).

- **Editable fields are days-of-week, start time, end time, mode, and slot-interval — not the Schedule's clinic or doctor.** The source business rules list "time window, mode, duration, doctor assignment, etc." as illustrative examples of what an edit might touch, but re-assigning *which* doctor or clinic a Schedule belongs to is architecturally equivalent to deleting one Schedule and creating a different one under a new identity — 009 has no such capability, and building one now would raise unresolved authorization questions (e.g., does the new doctor need to be staffed at the clinic too?) that are out of this feature's actual scope (a non-retroactivity guarantee on edits to a schedule's own configured pattern). The source material's AC4 (doctor-assignment edit leaving old Slots referencing the original doctor) is *already true by construction* regardless of this scope decision — 011 already snapshots `doctorProfile` onto every generated Session at generation time, immutably, independent of anything this feature does; see Assumptions.
- **The edit re-runs 010's overlap check, excluding the Schedule being edited from the comparison set.** Not stated explicitly in the source material, but necessary to preserve 010's own guarantee ("a doctor can never be scheduled to be in two places... at least on paper") — without this, a ClinicAdmin could trivially bypass 010's create-time check by creating two safely non-overlapping Schedules and then editing one to overlap the other. Excluding the Schedule being edited from its own comparison set is what makes "edit this schedule's own time window without changing anything else" not spuriously reject against its own prior state.
- **The edit re-validates the same field rules 009's create endpoint enforces** (non-empty days, start strictly before end, mode/slot-interval consistency) — an edited Schedule that would have been rejected as a fresh create is equally invalid as an edit; there's no reason for the two paths to accept different Schedules.
- **The edit never reads, writes, or references any Session or Slot row** — this is what makes non-retroactivity true structurally, not by convention: the edit's only write is to the `Schedule` row itself.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Editing a Schedule Never Touches Already-Generated Sessions (Priority: P1)

A ClinicAdmin or Doctor edits an existing Recurring Schedule's time window, mode, days, or slot-interval. Sessions already generated from that Schedule (by 011's nightly job) keep exactly the configuration they were generated with — the edit only changes what future nightly-job runs will generate.

**Why this priority**: This is the entire feature — the explicit guarantee that a patient who already booked against a generated Session never has their appointment silently changed by a later Schedule edit.

**Independent Test**: Generate Sessions from a Schedule (011); edit that Schedule's time window; confirm the already-generated Sessions are byte-for-byte unchanged, and a fresh generation run for a new date uses the edited pattern.

**Acceptance Scenarios**:

1. **Given** a Recurring Schedule that has already generated Sessions, **When** the ClinicAdmin (or the Doctor themselves) edits that schedule's time window, **Then** every already-generated Session's own time window is unchanged.
2. **Given** the same edited Schedule, **When** the next generation run (011) extends the horizon, **Then** newly generated Sessions reflect the updated time window.
3. **Given** a Fixed-Time Schedule edited to change its slot-interval, **When** existing generated Sessions are inspected, **Then** their originally-recorded slot-interval is preserved unchanged.
4. **Given** a Schedule edit request, **When** it is submitted, **Then** the edit's own write touches only the Schedule row — no Session row is read, created, modified, or deleted as a result.
5. **Given** an edit is submitted with an invalid field combination (e.g. Queue/Token mode with a slot-interval, or a start time not before the end time), **When** it is submitted, **Then** it is rejected identically to how 009's create endpoint would reject the same combination, and the Schedule's prior configuration is left unchanged.
6. **Given** an edit would make the Schedule overlap another of the same doctor's Schedules (at any clinic), **When** it is submitted, **Then** it is rejected per 010's overlap rule, and the Schedule's prior configuration is left unchanged.
7. **Given** an edit only changes a Schedule's own time window without touching any other field, **When** it is submitted, **Then** it is never rejected merely for "overlapping" its own prior configuration.

---

### Edge Cases

- What happens when an edit changes only one field (e.g. just the end time) and leaves the rest unchanged? → Treated as a full-payload replacement, matching 009's create contract shape — the caller supplies the complete intended configuration, not a partial patch.
- What happens to a Schedule's `createdAt` timestamp on edit? → Unchanged — it continues to reflect when the Schedule was originally created, not when it was last edited.
- What happens if an edit is submitted by a staff member who is neither the clinic's ClinicAdmin nor the named doctor? → Rejected as forbidden, identically to 009's create authorization rule.
- What happens if the edited Schedule (or clinic/doctor id in the path) doesn't exist? → Rejected as not found, identically to 009's create behavior.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST allow an authenticated ClinicAdmin of a Schedule's clinic, or the Schedule's own Doctor, to edit that Schedule's days-of-week, start time, end time, mode, and slot-interval.
- **FR-002**: System MUST reject an edit attempt from any caller who is neither the Schedule's clinic's active ClinicAdmin nor the Schedule's own Doctor.
- **FR-003**: An edit MUST be rejected under exactly the same field-validation rules 009's create endpoint enforces (non-empty days-of-week, start strictly before end, Fixed-Time requires a positive slot-interval, Queue/Token forbids one).
- **FR-004**: An edit MUST be rejected if the edited configuration would overlap (shared day-of-week, overlapping time range) any of the same doctor's *other* Schedules, at any clinic — the Schedule being edited MUST be excluded from its own comparison set.
- **FR-005**: A rejected edit (for any reason) MUST leave the Schedule's stored configuration completely unchanged.
- **FR-006**: An accepted edit MUST NOT read, create, modify, or delete any Session (or, once it exists, Slot) row — its only write is to the Schedule row itself.
- **FR-007**: Every Session already generated from a Schedule before an edit MUST remain unchanged in every field after that edit, permanently.
- **FR-008**: A generation run that occurs after an edit MUST use the edited Schedule's configuration for any newly-generated Session.

### Key Entities

- **Schedule** (from 009, read/write here): the entity this feature edits.
- **Session** (from 011, read-only *by omission* — never touched — here): the entity whose immutability-under-edit this feature's entire guarantee is about.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of Sessions generated before a Schedule edit remain byte-for-byte identical in every field after that edit, across every tested scenario.
- **SC-002**: 100% of edits that would fail 009's own create-time validation rules are rejected identically, with zero partial writes to the Schedule row.
- **SC-003**: 100% of edits that would create a new overlap with another Schedule for the same doctor are rejected; 100% of edits that only change a Schedule's own fields without creating a genuine new overlap are never rejected merely for "overlapping itself."
- **SC-004**: 100% of edit attempts by a caller who is neither the clinic's ClinicAdmin nor the Schedule's own Doctor are rejected as forbidden.
- **SC-005**: 100% of successful edits result in zero Session/Slot table writes of any kind (verified directly at the data layer).

## Assumptions

- **Re-assigning a Schedule's clinic or doctor is out of scope for this feature** (see Scope Decisions) — the source material's "doctor assignment" acceptance criterion is already satisfied structurally by 011's existing Session-snapshot design (`Session.doctorProfile` is copied at generation time and never re-derived from `Schedule`), independent of whether this feature ever allows reassigning a Schedule's doctor.
- **The edit endpoint's request/response shape mirrors 009's create endpoint's** (full-payload replacement, same field set) rather than a partial-patch shape — consistent with 009's own contract and with Edge Cases' resolution above.
