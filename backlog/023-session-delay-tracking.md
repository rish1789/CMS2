# 023 — Session Delay Tracking (Fixed-Time Only)

**Module:** Day-of Operations
**Status:** Ready for spec-kit intake

## User Story
As front-desk staff or a doctor, I want to see how delayed a fixed-time session currently is, so that I can set expectations with waiting patients and manage the rest of the day.

## Context
Fixed-time sessions run on a schedule that can slip during the day (late starts, walk-in insertions). The system needs a delay figure that's cheap to compute and doesn't require a live ticking clock. Queue-mode sessions have no notion of "scheduled time slippage" — they use queue position (024) instead. BDD §3.3, §6.3.

## Business Rules
- Applies ONLY to fixed-time sessions. Queue-mode sessions never carry a delay figure at all.
- NOT a live/continuous timer. Delay is recalculated at exactly two trigger points: (a) a slot being marked completed, or (b) a walk-in being inserted into the session.
- Delay figure = minutes between "now" (at the trigger point) and the scheduled time of the earliest still-unresolved slot (status open/booked, not completed) whose scheduled time has already passed.
- No background recalculation happens between the two trigger points — the figure is stale-but-correct-as-of-last-trigger by design.

## Acceptance Criteria
- Given a fixed-time session with slots at 9:00, 9:15, 9:30 and the 9:00 slot is still open/booked at 9:20, when a trigger point occurs (e.g. the 9:15 slot is marked completed), then delay recalculates as minutes between now and the 9:00 slot's scheduled time.
- Given no slot is completed and no walk-in is inserted, when 20 minutes pass with no trigger, then the displayed delay figure does NOT change — it is not a ticking timer.
- Given a walk-in is inserted into a fixed-time session, when the insertion happens, then delay recalculates immediately as one of the two defined trigger points.
- Given a session is queue-mode, when its delay is queried, then no delay figure exists — only queue position (024) applies to that session.
- Given all slots whose scheduled time has passed are marked completed, when recalculating, then the delay figure reflects no outstanding delay (zero or absent).

## Dependencies
- Depends on: 012-fixed-time-slot-pregeneration — delay tracking only applies to fixed-time sessions
- Depends on: 020-walk-in-priority-insertion — walk-in insertion is one of the two recalculation triggers
- Mutually exclusive with: 024-queue-position-tracking — a session is one mode or the other; never both signals apply

## Explicitly Out of Scope
- Live/continuously-updating delay timers.
- Delay tracking for queue-mode sessions (see 024 instead).

## Source References
- BDD §3.3 (Day-of Operations: delay recalculation rule)
- BDD §6.3 (Session Day Delay Tracking flow)
