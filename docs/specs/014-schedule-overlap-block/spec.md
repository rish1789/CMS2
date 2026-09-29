# Feature Specification: Multi-Clinic Doctor Schedule Overlap Block

**Feature Branch**: `014-schedule-overlap-block`

**Created**: 2026-09-03

**Status**: Draft

**Input**: User description: "Multi-Clinic Doctor Schedule Overlap Block — before a new Schedule (009) is saved for a Doctor, check all of that doctor's existing Schedules across every clinic they work at for day-of-week + time-range overlap, and reject the submission if any overlap is found — whether against a Schedule at a different clinic or the same one. Pure day/time overlap only, no travel-time buffer (a deliberate, accepted v1 limitation, not an oversight to silently fix). (Full source: backlog/010-multi-clinic-doctor-schedule-overlap-block.md)"

## Scope Decisions Made During Drafting

No prior conversation turn resolved these — they're research-backed defaults made while writing this spec, not a `/speckit-clarify` session (which runs next and may revisit any of them).

- **This feature extends 009's `ScheduleService.create()` directly, adding one more check step, rather than introducing a separate endpoint or service.** The check is intrinsically part of "is this a valid Schedule to save" — the same place 009 already enforces FR-005–FR-009's other validation/staffing rules. Nothing in the source material suggests overlap checking is a separately-invokable capability.
- **Overlap uses the standard half-open interval definition: two time ranges overlap iff each one's start is strictly before the other's end.** This is what makes the source material's own AC2 true (9–11am and 11am–1pm do *not* overlap — one ends exactly when the other starts) while still catching any genuine time intersection, including a range fully containing another.
- **The check compares the new Schedule against every one of that doctor's existing Schedules, at every clinic they work at — with no exception for the same clinic.** The source acceptance criteria explicitly test both the cross-clinic case and the same-clinic case, and business rules state "whether across two different clinics or within the same clinic."
- **No travel-time or minimum-gap buffer is added, and no configuration for one is introduced.** Explicitly named as a deliberate, accepted v1 limitation in the source material, not a gap to close opportunistically while building this feature.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - A Genuinely Overlapping Schedule Is Rejected, Anywhere the Doctor Works (Priority: P1)

When a ClinicAdmin or Doctor submits a new recurring Schedule, the system checks it against every one of that doctor's existing Schedules — at this clinic and at every other clinic they work at — and rejects the submission if it would put the doctor in two places on paper at the same day and time.

**Why this priority**: This is the entire feature — a single, well-defined guarantee layered onto 009's existing create path. There is no smaller independently-valuable slice.

**Independent Test**: Create a Schedule for a doctor at Clinic A for Mon 9–11am; attempt to create a second Schedule for the same doctor at Clinic B for Mon 10am–12pm; confirm the second submission is rejected while the first remains the only Schedule on record. Repeat with both Schedules at the same clinic.

**Acceptance Scenarios**:

1. **Given** a Doctor has a Schedule for Mon 9–11am at Clinic A, **When** a new Schedule for Mon 10am–12pm at Clinic B is submitted for the same doctor, **Then** it is rejected (10–11am overlaps) and no new Schedule is persisted.
2. **Given** the same existing Schedule, **When** a new Schedule for Mon 11am–1pm at Clinic B is submitted, **Then** it is allowed — the ranges touch but do not overlap.
3. **Given** a Doctor has two Schedules at the *same* clinic, **When** a new Schedule overlapping one of them (same clinic) is submitted, **Then** it is rejected identically to the cross-clinic case.
4. **Given** a Doctor has an existing Schedule covering Mon/Wed, **When** a new Schedule covering Tue/Thu at an overlapping time is submitted, **Then** it is allowed — no shared day, so no overlap regardless of the time range.
5. **Given** a Doctor has an existing Schedule covering Mon 9am–5pm, **When** a new Schedule for Mon 10am–11am (fully contained within the existing range) is submitted, **Then** it is rejected.
6. **Given** a Doctor has no existing Schedules anywhere, **When** their first Schedule is submitted, **Then** it is allowed (nothing to overlap against).

---

### Edge Cases

- What happens when the new Schedule shares a day with an existing one, but at a different clinic, and the time ranges are identical? → Rejected — identical ranges are the maximal case of overlap.
- What happens when the new Schedule spans multiple days, only one of which collides with an existing Schedule's day/time? → Rejected — any single colliding day is enough to reject the whole submission (a Schedule is one atomic unit per 009's own data model; it is not partially accepted).
- What happens to 009's own validation/staffing-gate rejections when the request would *also* have overlapped? → Those checks still run first (per 009's existing ordering) and their own rejection (400/404/409 as before) is returned; the overlap check is evaluated only once the request has already passed those, so a request can only be rejected for one reason at a time, whichever is checked first.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST, before persisting a new Schedule, check it against every existing Schedule belonging to the same doctor, at every clinic that doctor works at (not limited to the clinic named in the new submission).
- **FR-002**: System MUST reject the new Schedule submission if it shares at least one day of the week with an existing Schedule AND their time ranges overlap (each range's start strictly before the other's end) — regardless of whether the existing Schedule is at the same clinic or a different one.
- **FR-003**: System MUST allow a new Schedule whose time range starts exactly when an existing (same-day) Schedule's range ends, or ends exactly when one starts — touching, not overlapping.
- **FR-004**: System MUST allow a new Schedule that shares no day of the week with any existing Schedule, regardless of time range.
- **FR-005**: System MUST NOT persist any part of a rejected Schedule submission.
- **FR-006**: System MUST NOT apply any travel-time, minimum-gap, or other buffer beyond the exact time range comparison — two touching-but-not-overlapping ranges at different clinics are always allowed, with no configuration to change this.

### Key Entities

- **Schedule** (from 009, read/write here): this feature reads every existing Schedule for the target doctor (across all clinics) to check against, and is itself the entity a rejected submission never becomes.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of submitted Schedules that share a day and an overlapping time range with any existing Schedule for that doctor (same clinic or different) are rejected, with zero rows persisted.
- **SC-002**: 100% of submitted Schedules that share a day with an existing Schedule but whose time ranges only touch (no overlap) are accepted.
- **SC-003**: 100% of submitted Schedules sharing no day with any existing Schedule for that doctor are accepted regardless of time range.
- **SC-004**: A doctor's very first Schedule (no existing Schedules anywhere) is always accepted (assuming it otherwise passes 009's own validation/staffing checks).

## Assumptions

- **This feature only changes `create()`'s behavior — 009's `list()` capability, response shape, and every other validation/staffing rule are unchanged.** Nothing in the source material describes any change to reading schedules.
- **The overlap check runs after 009's existing validation and staffing-gate checks, immediately before the save.** This preserves 009's existing error precedence (a structurally invalid or unstaffed-doctor submission still fails for that reason first) and only adds a new terminal check at the point 009 already commits to persisting.

