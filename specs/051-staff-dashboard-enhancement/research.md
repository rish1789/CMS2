# Research: Staff Operational Dashboard Enhancement

## Decision 1: "Today's sessions" needs zero new backend work

**Decision**: Reuse `GET /api/v1/clinics/{clinicId}/sessions` (`listMyClinics`'s sibling, `ClinicSessionListController`) with `from=to=today` and a real page size, reading the full `SessionSummary[]` array instead of the dashboard's current `size: 1` count-only call.

**Rationale**: Verified by reading `ClinicSessionListController.java`, `SessionSummaryResponse.java`, and `DaySheet.tsx` (the exact component the backlog brief names as this data's source) in full. `SessionSummary`/`SessionSummaryResponse` already carries everything a condensed row needs — `doctorName`, `startTime`/`endTime`, `mode`, `bookedSlotCount`/`totalSlotCount` — and `DaySheet.tsx`'s own list view is built on exactly this endpoint, at exactly this granularity (per-session, not per-patient). The dashboard's existing `useEffect` already calls this endpoint for its count tile; widening `size` and reading `.sessions` instead of only `.totalCount` is the entire change.

**Alternatives considered**: A new "today's sessions" dedicated endpoint — rejected, `listSessions` already returns this exact shape; a per-patient row list — rejected, see Decision 3 (no endpoint returns that at this level without N+1).

## Decision 2: One new grouped-count query for completed/no-show today

**Decision**: Add `SlotRepository.countStatusByClinicAndDate(clinicId, date)`, mirroring the existing `countBySessionIdIn`'s grouped-projection shape exactly:

```java
@Query("SELECT s.status AS status, COUNT(s) AS count FROM Slot s "
    + "WHERE s.session.clinic.id = :clinicId AND s.session.sessionDate = :date GROUP BY s.status")
List<SlotStatusCount> countStatusByClinicAndDate(@Param("clinicId") UUID clinicId, @Param("date") LocalDate date);

interface SlotStatusCount {
    SlotStatus getStatus();
    long getCount();
}
```

A new `TodaySessionStatsController` (`@RestController`, `GET /api/v1/clinics/{clinicId}/sessions/today-stats`) calls it directly (no new service class) and maps the `COMPLETED`/`NO_SHOW` rows into a `TodaySessionStatsResponse(int completedCount, int noShowCount)`, defaulting to 0 for a status with no matching group.

**Rationale**: `countBySessionIdIn`'s own Javadoc already documents the exact reasoning this reuses: "Deliberately computed from `Slot.status` alone, not Booking... keeps the query inside `com.cms.scheduling`, which must never depend on `com.cms.booking`." The new query follows the identical shape (a `GROUP BY` projection on `Slot`, scoped by a `session.clinic`/`session.sessionDate` predicate already used elsewhere in `SlotRepository` — e.g. `findOpenFixedTimeSlotsOnDate`'s own `s.session.clinic.id = :clinicId ... s.session.sessionDate = :date` predicate pair). A dedicated controller, not an addition to `ClinicSessionListController`, matches this module's own established one-endpoint-per-concern granularity (`SessionDelayController`, `SlotCompletionController` are both single-purpose controllers already).

**Alternatives considered**: Extending `ClinicSessionListController.list()`'s response with these 2 extra counts — rejected, conflates a paginated list response with an unrelated same-day aggregate, and would force every list call (any date range) to also compute today's stats regardless of whether the caller wants them; a new `TodayStatsService` — rejected, no business logic beyond aggregation+authorization exists here, matching `ClinicSessionListController`'s own controller-calls-repository-directly shape (Principle II).

## Decision 3: "Today's sessions" is session-level, not patient-level — corrects the backlog brief's own wording

**Decision**: Rows show doctor/time/mode/slot-fill, not a per-patient appointment list.

**Rationale**: Verified `SessionSummaryResponse` (no patient field) vs. `SessionDaySheet`/`SlotDetail` (has `booking.patientName`, but only fetched per-session via `getDaySheet(clinicId, sessionId, token)` — one HTTP call per session). The backlog brief's acceptance criteria names "patient" as a row field, but also explicitly frames this as "a condensed version of [DaySheet.tsx's] same real data" — and `DaySheet.tsx` itself is the session-level list, not a patient-level one. Showing patient names on the dashboard would require either an N+1 fan-out (one `getDaySheet` call per today's session — poor at any real session volume) or a new aggregate endpoint beyond what "condensed" implies. Session-level rows satisfy the brief's own explicit data-source pointer without either cost.

**Alternatives considered**: N+1 `getDaySheet` fan-out for true patient-level rows — rejected, unbounded per-clinic cost, and the brief's own "not duplicate DaySheet's full functionality" framing argues against it; a new aggregate "today's appointments" endpoint returning flattened patient rows — rejected as unrequested scope beyond what user's "add one backend endpoint" decision approved (that decision was scoped to the completed/no-show stats tile, not a second new endpoint).

## Decision 4: "Walk-ins today" is not buildable even with a new query — genuinely excluded

**Decision**: Not built. Documented in spec.md Edge Cases as a real, reported gap.

**Rationale**: Verified by reading `Booking.java` and `Slot.java` in full, plus `WalkInInsertionService.java`'s call sites: neither entity persists any flag or enum distinguishing a walk-in-inserted booking from a patient-self-service or staff-assisted one. `Booking.overrideReason` is set only for priority-3 override walk-ins specifically (025), not walk-ins generally — most walk-in bookings look identical, at the data level, to any other booking. A query can only aggregate columns that exist; satisfying this would require a new persisted column (e.g. a `bookingSource` enum) plus a migration and a write-path change to `WalkInInsertionService` — categorically bigger than "one minimal query," and outside what the user's decision approved.

**Alternatives considered**: Inferring "walk-in" from `Booking.overrideReason IS NOT NULL` — rejected, that only ever flags priority-3 override walk-ins (025), silently undercounting every ordinary walk-in and producing a materially wrong number, worse than not showing the metric at all.

## Decision 5: "Recent Patients"/"Recent Activity" is not buildable from real per-clinic data

**Decision**: Not built. Documented in spec.md Edge Cases.

**Rationale**: The only candidate timestamp (`Patient.createdAt`, confirmed to exist per 009/019) is a platform-wide patient-registration timestamp, not a per-clinic visit/interaction signal — a patient can register once and then be seen at multiple clinics over time, so "recently created Patient records" would not answer "recent activity at *this* clinic" at all, and no other activity/audit-trail table exists anywhere in the schema (verified: no `audit_log`/`activity` table in any Flyway migration).

**Alternatives considered**: Deriving "recent activity" from recently-created `Booking` rows at this clinic — considered closer to genuinely real, but explicitly not requested by this feature's approved scope (the user's decision was scoped to the completed/no-show stats tile) and not named as a committed deliverable in spec.md's Functional Requirements; left as a documented non-goal rather than silently added.
