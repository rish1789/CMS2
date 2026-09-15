# 022 — Buffer Slot Capacity Sizing

**Module:** Day-of Operations
**Status:** Ready for spec-kit intake

## User Story
As the scheduling system, I want to size the number of buffer slots in each generated session based on a doctor's trailing no-show history, so that clinics can absorb walk-ins and late arrivals without over- or under-provisioning capacity.

## Context
Buffer slots exist to give clinics realistic slack for the walk-in insertion flow (020). The sizing formula was an open item in the design-phase BDD and is now a concrete, resolved formula — but the specific constants are explicitly still open to future tuning (BDD §7.3), so v1 keeps them as global constants rather than exposing them to clinics.

## Business Rules
- Lookback window: trailing 90 days of no-show history per doctor.
- Minimum sample size: 5 data points. Below that threshold, a flat single-slot default buffer (1 buffer slot) applies instead of a computed value.
- With sufficient data, a risk score is computed from the no-show history, capped, and converted to up to 20% of a session's total slots.
- Absolute cap: 3 buffer slots per session, regardless of what the 20% calculation would otherwise produce.
- Buffer slots are spread evenly through the session's timeline — never clustered at the end.
- These constants (90-day window, 5-sample floor, 20% ceiling, 3-slot cap) are hardcoded, system-wide, and NOT clinic-configurable in v1 — an explicit product decision, since the constants themselves are acknowledged as unvalidated (BDD §7.3, open question §8.8).

## Acceptance Criteria
- Given a doctor has fewer than 5 no-show data points in the trailing 90 days, when a session is generated for them, then exactly 1 buffer slot is allocated.
- Given a doctor has 5+ no-show data points in the trailing 90 days, when a session is generated, then the computed risk score determines buffer count as up to 20% of the session's slots.
- Given the 20%-of-slots calculation would produce more than 3 buffer slots, when applying the cap, then only 3 buffer slots are allocated.
- Given N buffer slots are allocated for a session, when they are placed, then they are distributed evenly across the session's slots rather than grouped at the end.
- Given a clinic wants to change the lookback window or caps, when they look for a setting to do so, then none exists in v1 — the constants are global and code-level only.

## Dependencies
- Depends on: 021-automatic-no-show-detection — source of the no-show history this formula consumes
- Depends on: 011-nightly-rolling-session-generation — buffer slots are computed as part of session generation
- Feeds into: 020-walk-in-priority-insertion — buffer slots are priority #1 in the walk-in fallback order

## Explicitly Out of Scope
- Per-clinic configurability of the lookback window, sample floor, or caps (deliberate v1 decision — centrally tuned constants only).
- Any UI for clinics to view or adjust the formula's constants.

## Source References
- BDD §4 (Buffer capacity sizing formula — resolved)
- BDD §7.3 (Trailing no-show window for buffer sizing — resolved)
- BDD §8.8 (open question re: configurability — resolved as "no" for v1)
