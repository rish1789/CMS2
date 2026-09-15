# Research: Multi-Clinic Doctor Schedule Overlap Block

## Decision: In-memory overlap scan over `findByDoctorProfile_Id`, not a DB-level overlap query

**Rationale**: A doctor's total Schedule count is small (bounded by realistic clinic/day/shift combinations, not a growing transactional table), so fetching all of a doctor's Schedules and comparing in Java is simpler and equally correct compared to a Postgres range-overlap query (`tsrange`/`&&` operator), which would require restructuring `daysOfWeek`/`startTime`/`endTime` into a range-comparable column shape not otherwise needed anywhere else in this codebase (Principle II — no speculative schema complexity for a small-N comparison).

**Alternatives considered**: A Postgres `EXCLUDE` constraint or `tsrange && tsrange` query — rejected; `Schedule`'s day-of-week set (not a single date range) doesn't map cleanly onto Postgres range types without new columns/types this feature doesn't otherwise need, for a comparison this codebase can just as correctly do in application code at this data volume.

## Decision: Overlap predicate — shared day AND `newStart < existingEnd && existingStart < newEnd`

**Rationale**: This is the standard half-open-interval overlap test, and it's exactly what makes spec AC2 (9–11am then 11am–1pm, no overlap) and AC5 (a range fully containing another) both correct: touching ranges fail the strict `<` test (11:00 is not `<` 11:00), while any genuine intersection (including full containment) satisfies it. `Set.retainAll`/a non-empty intersection check for the day comparison mirrors how `daysOfWeek` is already stored as a `Set<DayOfWeek>` (009).

**Alternatives considered**: An inclusive (`<=`) comparison — rejected; it would incorrectly reject the exact touching case AC2 requires to be allowed.

## Decision: `ScheduleOverlapException` → `409 Conflict`, `SCHEDULE_OVERLAP`

**Rationale**: Matches this codebase's existing convention of `409` for "the request is well-formed and the referenced entities exist, but the resulting state would violate a business invariant" (e.g. 009's own `DOCTOR_NOT_STAFFED_AT_CLINIC`, 007's `SPECIALIZATION_MISMATCH`) — distinct from `400` (malformed request shape, 009's `INVALID_SCHEDULE`) and `404` (missing entity).

**Alternatives considered**: `400 Bad Request` — rejected; the request itself is well-formed (every field valid on its own), the conflict is with *other data* (existing Schedules), which is `409`'s established meaning in this codebase, not `400`'s.

## Decision: The check runs last, after 009's existing validation and staffing gate

**Rationale**: Preserves 009's existing error precedence unchanged (spec Edge Cases) — a request that's both structurally invalid *and* would overlap still gets `400 INVALID_SCHEDULE` (the cheaper, more fundamental problem), not a `409`. Only a request that's otherwise entirely valid reaches the new overlap check.

**Alternatives considered**: Running the overlap check first (cheapest-first ordering by query cost) — rejected; error precedence (which single error a multiply-invalid request reports) is a user-facing detail spec Edge Cases pins down explicitly, and validation-before-persistence-conflict is the existing, already-converged precedent (e.g. 009 checks staffing gate after body validation, not before) this feature extends consistently.
