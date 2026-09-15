# 010 — Multi-Clinic Doctor Schedule Overlap Block

**Module:** Scheduling & Session Generation
**Status:** Ready for spec-kit intake

## User Story
As the system, I want to block a doctor's schedules from overlapping in day/time — whether across two different clinics or within the same clinic — so that a doctor can never be scheduled to be in two places at once, at least on paper.

## Context
BDD §7.6: "Multi-clinic doctor double-booking — confirmed implemented, with a known gap. Overlapping schedules across clinics are blocked on paper (day/time overlap check), but travel time between physical clinics is not accounted for — same-day back-to-back scheduling at two clinics is technically allowed even if it's not realistically possible." §8#10 carries this forward as an open question.

**Resolved scope decision for this build:** ship the overlap check only, with no travel-time buffer. This is a deliberate, accepted v1 limitation — not a bug to silently "fix" later without a fresh product decision.

## Business Rules
- Before saving a new Schedule (009) for a Doctor, the system checks all of that doctor's existing Schedules — across every clinic they work at — for day-of-week + time-range overlap.
- If any overlap is found with an existing Schedule, the new Schedule submission is rejected.
- This check is a pure day/time overlap check. It does **not** account for travel time between physically different clinics — two schedules that end and start at the exact same instant at two different clinics (e.g., 9–11am at Clinic A, 11am–1pm at Clinic B, same day) are allowed even though it may be operationally unrealistic.
- This is intentional v1 scope, not an oversight: no minimum-gap/travel-time configuration exists, and none should be added without a separate, explicit product decision.

## Acceptance Criteria
- Given a Doctor has a Schedule for Mon 9–11am at Clinic A, when a new Schedule for Mon 10am–12pm at Clinic B is submitted, then it is rejected (10–11am overlaps).
- Given a Doctor has a Schedule for Mon 9–11am at Clinic A, when a new Schedule for Mon 11am–1pm at Clinic B is submitted, then it is **allowed** — no time overlap exists, even though travel between clinics may be unrealistic in that gap.
- Given a Doctor has two Schedules at the *same* clinic, when a new one overlapping an existing one is submitted, then the same overlap block applies (not just cross-clinic).

## Dependencies
- Depends on: 009-recurring-schedule-definition — the Schedule entity this check runs against.

## Explicitly Out of Scope
- Travel-time buffer or minimum-gap enforcement between schedules at different clinics — explicitly accepted as a v1 limitation per product decision (not a missing capability to silently add later).

## Source References
- BDD §7.6 (confirmed implemented, with known gap)
- BDD §8 (#10 — resolved for this build: overlap-only, no travel-time check)
