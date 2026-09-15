# 024 — Queue Position Tracking (Queue-Mode Only)

**Module:** Day-of Operations
**Status:** Ready for spec-kit intake

## User Story
As a patient or staff member in a queue/token-mode session, I want to know a booking's current position in the queue, so that patients have a sense of how long until they're seen.

## Context
Queue-mode sessions don't have per-booking scheduled times, so a minutes-based delay figure (as used for fixed-time sessions, 023) doesn't apply. Instead, the system tracks a live position count. BDD §3.3, §6.3.

## Business Rules
- Applies ONLY to queue/token-mode sessions. Fixed-time sessions use delay tracking (023) instead — the two are mutually exclusive by session mode.
- Queue position = count of active bookings ahead of this booking in token order, + 1.
- "Active" means bookings ahead that are not yet completed, cancelled, or no-show.
- No delay-in-minutes figure exists for queue mode — position (a count, not a time estimate) is the equivalent signal.

## Acceptance Criteria
- Given a queue-mode session with tokens 1-5 booked, tokens 1-2 completed and token 3 in progress, when the patient holding token 5 checks their position, then the system reports position = (active bookings ahead: tokens 3, 4) + 1 = 3.
- Given a booking ahead in the queue is cancelled or marked no-show, when queue position is next queried for later tokens, then their position recalculates to reflect one fewer active booking ahead.
- Given a queue-mode session, when checked for a delay-in-minutes figure, then none exists — only position is available.
- Given a fixed-time session, when queue position is requested for one of its bookings, then it is not applicable — fixed-time sessions use delay tracking (023) instead.

## Dependencies
- Depends on: 013-queue-mode-slot-on-demand-generation, 018-queue-token-booking
- Mutually exclusive with: 023-session-delay-tracking

## Explicitly Out of Scope
- Converting queue position into an estimated wait time in minutes — the source doc only specifies a raw position count, not a time estimate.

## Source References
- BDD §3.3 (Day-of Operations: queue position as the queue-mode equivalent of delay)
- BDD §6.3 (Session Day Delay Tracking flow, step 3)
