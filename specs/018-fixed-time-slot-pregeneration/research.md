# Research: Fixed-Time Session Slot Pre-Generation

## Decision: Regular slot times computed directly from the Session's own snapshotted `startTime`/`endTime`/`slotIntervalMinutes`

**Rationale**: 011 already snapshots these fields onto `Session` at generation time specifically so downstream logic never needs to re-derive them from a (possibly since-edited) `Schedule` — this feature is exactly the first downstream consumer of that design. Regular slot start times are `session.startTime`, `session.startTime + interval`, `session.startTime + 2*interval`, ... stopping at the last value where `slotStart + interval <= session.endTime` (spec Edge Cases: no partial trailing slot).

**Alternatives considered**: Re-reading the parent `Schedule` for slot timing — rejected; it would reintroduce exactly the retroactivity risk 014 closed, and there's no reason to when the Session already carries everything needed.

## Decision: `BufferSlotCalculator` interface, `ColdStartBufferSlotCalculator` its only v1 bean

**Rationale**: `int calculateBufferSlotCount(Session session)` — takes the whole `Session` (not just a doctor id) so a future implementation (022) has access to whatever it turns out to need (doctor, clinic, date) without an interface change. `ColdStartBufferSlotCalculator` always returns `1`, with a javadoc explicitly citing 022's own "fewer than 5 data points" rule and build-order.md's resolution — this isn't a magic number, it's a literal transcription of an already-specified rule from a sibling backlog item, made swappable per that same document's own stated intent ("have it upgrade 012's slot-generation logic in place").

**Alternatives considered**: Inlining `1` directly in `SlotGenerationService` — rejected; it would make 022's later work a search-and-replace inside slot-generation logic instead of a clean new `@Primary`/replacement bean, exactly the coupling the interface is meant to avoid.

## Decision: Buffer slots are selected by index from the already-generated regular-slot sequence, using an even-spacing formula

**Rationale**: `index(i) = floor((i + 0.5) * M / N)` for `i = 0..N-1` (a standard "N points evenly spaced among M items" formula) selects which of the `M` already-time-computed slots get `isBuffer = true`. For `N = 1` (v1's only real case), this reduces to `floor(0.5 * M)` — the middle slot — matching spec Edge Cases' "the only position that exists is, trivially, the middle" for `M = 1`, and generalizing correctly (without any code change) if a future `BufferSlotCalculator` ever returns `N > 1`.

**Alternatives considered**: A simpler "every Kth slot" modulo approach — rejected; it biases toward clustering near one end for small `M`/`N` combinations in a way the standard evenly-spaced-index formula doesn't, and the formula above is no more complex to implement.

## Decision: Slot generation is a direct hook inside `SessionGenerationService.generateForSchedule`, not a separate post-generation pass

**Rationale**: FR-001 requires Slots to be created "in the same step" as the Session — 011's `generateForSchedule` already runs one Schedule's Session creation inside its own transaction (research.md of 011); adding "if the just-created Session is Fixed-Time, also generate its Slots" as the next few lines of that same method keeps everything inside that same transaction boundary, so a Fixed-Time Session can never be observed (even by a concurrent reader) with zero Slots. The hook only fires for a Session this call *just created* (not one that already existed and was skipped by 011's own duplicate-prevention pre-check) — a repeat generation run never re-enters this code path for an already-generated Session at all, which is what makes FR duplicate-prevention (SC-004) trivially true by construction rather than requiring its own separate check.

**Alternatives considered**: A separate `@Scheduled`/manually-triggered slot-backfill pass reading all Fixed-Time Sessions with zero Slots — rejected; it would reintroduce exactly the "slots appearing incrementally" outcome the source business rules explicitly rule out, and adds an eventual-consistency window with no benefit.

## Decision: `SlotStatus` enum defined with only `OPEN` for now

**Rationale**: This feature's own scope never transitions a Slot to any other state — inventing `BOOKED`/`CANCELLED`/`NO_SHOW` now would be guessing at state machines owned by unbuilt features (016/017/018/020/021/025+). Constitution Principle II: extend in place exactly when a real feature needs the next value, matching this backlog's own established pattern for `ScheduleMode`/`NotificationEventStatus`.

**Alternatives considered**: A `boolean booked` flag instead of a status enum — rejected; the source business rule explicitly uses state language ("starts in an OPEN state"), and an enum with one current value costs nothing extra while correctly signaling "more values are coming" to the next feature that touches this entity.
