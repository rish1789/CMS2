# Feature Specification: Nightly Rolling Session Generation (15-Day Horizon)

**Feature Branch**: `015-nightly-session-generation`

**Created**: 2026-09-03

**Status**: Draft

**Input**: User description: "Nightly Rolling Session Generation — a background job that reads every Recurring Schedule (009) and materializes one Session per applicable calendar date within a rolling 15-day horizon (today through today+14), never duplicating a Session already generated for a given schedule+date. Runs nightly, and can be manually re-triggered by Super Admin only. Each Session snapshots its parent Schedule's mode/time-range/slot-interval at generation time, so it is a standalone record immune to a later Schedule edit (014, not yet built). Fixed-Time Slot pre-generation (012) and Queue/Token on-demand Slot creation (013) are separate, not-yet-built features this job feeds — this feature creates Session rows only, no Slot entity. (Full source: backlog/011-nightly-rolling-session-generation.md)"

## Scope Decisions Made During Drafting

No prior conversation turn resolved these — they're research-backed defaults made while writing this spec, not a `/speckit-clarify` session (which runs next and may revisit any of them).

- **This feature creates `Session` rows only — no `Slot` entity exists yet, and none is created here.** The source acceptance criteria describe Slot pre-generation (Fixed-Time) and on-demand Slot creation (Queue/Token) as happening "in the same step" / "later," but build-order.md places both of those as separate, later features (012, 013) that explicitly *need* this feature first. Building Slot logic now would mean guessing at 012/013's own not-yet-designed data model — the same reasoning this backlog has already applied repeatedly (e.g. 009 deferring 010's overlap check, 036 deferring its own trigger wiring). This feature's own job is exactly what its name says: Session generation.
- **A `Session` snapshots its parent Schedule's `mode`, `startTime`, `endTime`, and `slotIntervalMinutes` at the moment it's generated**, rather than holding only a live reference to the Schedule. This is what makes "generated Sessions are immune to later edits of the Recurring Schedule" (the source business rule, whose actual edit-rejection mechanics belong to 014, not yet built) structurally true from day one: a future Schedule edit can never retroactively change what an already-generated Session says about itself, because the Session doesn't re-derive those fields from the Schedule at read time.
- **"Active Recurring Schedule" means every Schedule that currently exists.** 009 (the only feature that creates Schedules so far) has no deactivate/delete/soft-delete capability of any kind — every Schedule row that exists is, by construction, the only kind of "active" this system currently has. If a future feature adds Schedule deactivation, that feature would need to extend this job's own query; it is out of this feature's scope to invent that mechanism speculatively now.
- **The 15-day horizon is 15 calendar dates starting with the run date itself** (run date, run date+1, ..., run date+14) — the source's own AC2 example ("Sessions generated through day 10 of the horizon... job runs on day 1... only the newly-in-range dates (11-15) are generated") numbers the horizon's days starting at 1, consistent with the run date being day 1.
- **Duplicate-generation for the same (Schedule, date) pair is prevented by a database-level uniqueness constraint**, not merely an application-level "check then create" — the same data-layer-closure standard this codebase applies to every other create-or-reuse race (Constitution Principle IV), here guarding against an overlapping manual Super Admin re-trigger racing the scheduled nightly run.
- **This feature ships a minimal Super Admin frontend trigger**, following the established `PendingClinicsList`/`PendingDoctorsList` precedent (a real, already-existing Super Admin actor and UI pattern in this codebase) — unlike 011/012 (notification pipeline/delivery stub)'s service-only precedent, which applied specifically because no caller existed at all yet.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - The System Keeps a 15-Day Rolling Horizon of Sessions Populated (Priority: P1)

Every night, the system looks at every Recurring Schedule and makes sure a Session exists for every date, within the next 15 days, that the schedule says it runs — without ever creating a duplicate for a date/schedule pair it already generated on a previous run.

**Why this priority**: This is the entire reason the feature exists — without it, Recurring Schedules stay abstract patterns with nothing concrete for staff or patients to book against.

**Independent Test**: Create a Recurring Schedule with no Sessions yet; invoke the generation job directly; confirm a Session exists for every date in the next 15 days matching the schedule's days-of-week, and running the job again immediately creates zero additional Sessions.

**Acceptance Scenarios**:

1. **Given** an active Recurring Schedule with no Sessions yet generated, **When** the generation job runs, **Then** a Session is created for every date in the next 15 days (starting today) whose day-of-week matches the schedule.
2. **Given** a Recurring Schedule that already has Sessions generated through day 10 of a 15-day horizon computed from an earlier run, **When** the job runs again on the original run date (or any date whose resulting horizon still overlaps those first 10 days), **Then** no duplicate Session is created for any of days 1–10, and any newly-in-range date is generated.
3. **Given** two Recurring Schedules for two different doctors (or the same doctor at two different clinics), **When** the job runs, **Then** each gets its own independently-generated set of Sessions, scoped to its own schedule.
4. **Given** a Fixed-Time Recurring Schedule, **When** its Session is generated, **Then** the Session records the Fixed-Time mode and the schedule's slot-interval value as they were at that moment.
5. **Given** a Queue/Token Recurring Schedule, **When** its Session is generated, **Then** the Session records the Queue/Token mode and no slot-interval value.

---

### User Story 2 - Super Admin Manually Re-Triggers Generation (Priority: P2)

A Super Admin can trigger the exact same generation logic on demand, outside the nightly schedule — e.g. to backfill immediately after configuring new Schedules, without waiting for the next nightly run.

**Why this priority**: Named explicitly in the source material as a distinct actor capability, but it's the same underlying logic as User Story 1 exposed through a second trigger — additive, not foundational.

**Independent Test**: As Super Admin, call the manual-trigger endpoint; confirm it produces the identical result a nightly run would for the same run date. As a non-Super-Admin (or unauthenticated) caller, confirm the same request is rejected.

**Acceptance Scenarios**:

1. **Given** a Super Admin, **When** they call the manual re-trigger action, **Then** it runs the same generation logic as the nightly job, using the current date as the run date.
2. **Given** a non-Super-Admin (or unauthenticated) caller, **When** they attempt to call the manual re-trigger action, **Then** the request is rejected.

---

### Edge Cases

- What happens when a Recurring Schedule's days-of-week produce zero applicable dates within the 15-day horizon (impossible for any real schedule, since every day-of-week occurs within any 15-day span, but structurally: a schedule with an empty day set could never occur per 009's own FR-006 which already forbids that)? → Not reachable — 009 already guarantees every Schedule has at least one day of the week.
- What happens if the manual re-trigger is called on the same day the nightly job already ran? → Idempotent, per the same duplicate-prevention guarantee as any other same-day repeated run — no duplicate Sessions, only genuinely new dates (if any horizon change) are generated.
- What happens if two triggers (e.g. the nightly job and a concurrent manual re-trigger) run at effectively the same time? → The database-level uniqueness constraint ensures only one Session per (Schedule, date) is ever committed; a losing concurrent attempt for the same pair does not error the whole run — it simply contributes zero new rows for that pair (research.md details the exact mechanism).
- What happens to a Session generated from a Schedule that is later edited (once 014 exists)? → Out of scope for this feature to enforce (014's job), but structurally supported here: the Session's own snapshotted fields never change as a side effect of anything this feature does.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST provide a generation capability that, for a given run date, ensures a Session exists for every (Recurring Schedule, calendar date) combination where the date falls within the 15-day horizon starting at the run date and the date's day-of-week is one of the schedule's configured days.
- **FR-002**: System MUST NOT create a duplicate Session for a (Schedule, date) pair that already has one, regardless of how many times generation is invoked for overlapping horizons.
- **FR-003**: Each generated Session MUST record, independently of its parent Schedule's current state: the mode (Fixed-Time or Queue/Token), the start time, the end time, and — only when the mode is Fixed-Time — the slot-interval value, all as they were on the parent Schedule at the moment of generation.
- **FR-004**: A generated Session for a Queue/Token schedule MUST NOT record a slot-interval value.
- **FR-005**: System MUST generate Sessions independently per Recurring Schedule — one schedule's generation outcome MUST NOT affect or be affected by another schedule's.
- **FR-006**: System MUST run the generation capability automatically on a nightly basis with no manual intervention required.
- **FR-007**: System MUST allow an authenticated Super Admin to manually invoke the same generation capability on demand, using the current date as the run date.
- **FR-008**: System MUST reject a manual generation-trigger attempt from any caller who is not an authenticated Super Admin.
- **FR-009**: This feature MUST NOT create, modify, or reference any Slot entity — Slot pre-generation and on-demand creation are separate, later features' responsibility.

### Key Entities

- **Session** (new entity, first defined by this feature): one concrete, bookable occurrence materialized from a Recurring Schedule on a specific calendar date. Fields: the Schedule it was generated from, its Clinic and Doctor Profile (denormalized at generation time), the calendar date, mode, start time, end time, an optional slot-interval value (Fixed-Time only), a created timestamp.
- **Schedule** (from 009, read-only here): the recurring pattern this feature reads to decide what to generate; never written to by this feature.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of applicable (Schedule, date) combinations within a 15-day horizon result in exactly one Session, verified directly against seeded Schedule data.
- **SC-002**: 100% of repeated generation invocations (nightly, manual, or both) over an overlapping horizon produce zero duplicate Sessions for any (Schedule, date) pair already generated.
- **SC-003**: 100% of generated Sessions correctly reflect their parent Schedule's mode-appropriate fields (Fixed-Time: slot-interval present; Queue/Token: slot-interval absent) as of generation time.
- **SC-004**: 100% of manual-trigger attempts by a non-Super-Admin (or unauthenticated) caller are rejected; 100% of Super Admin attempts succeed and produce output identical in shape to a nightly run.
- **SC-005**: 100% of generation runs, across every tested scenario, create zero rows in any table other than `Session` itself.

## Assumptions

- **No Slot entity exists yet anywhere in this codebase** (012/013 are later in build order and unbuilt) — FR-009/SC-005 are a forward-looking guarantee about this feature's own scope, not something requiring an existing Slot table to verify against.
- **The nightly trigger's exact time-of-day is an implementation default, not a specified business rule** — the source material says "nightly" without naming a time; this feature picks one reasonable default (documented in plan.md) with no product significance attached to the specific hour.
- **"Active Recurring Schedule" currently means every Schedule row that exists**, since 009 has no deactivation mechanism (see Scope Decisions) — this feature's query is written against all Schedules, not a filtered "active" subset, because no such filter exists to apply.
- **The manual re-trigger's response reports what was generated (e.g. a count), not the full generated Session list** — the source material doesn't specify a response shape beyond "runs the same generation logic," and a count is the simplest, most directly verifiable signal per SC-004's "output identical in shape to a nightly run" (a nightly run has no HTTP caller to report a full list to in the first place).
