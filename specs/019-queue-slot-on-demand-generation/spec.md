# Feature Specification: Queue/Token Session Slot-on-Booking Generation

**Feature Branch**: `019-queue-slot-on-demand-generation`

**Created**: 2026-09-03

**Status**: Draft

**Input**: User description: "Queue/Token Session Slot-on-Booking Generation — a Queue/Token Session (011) starts with zero Slots; each booking creates exactly one new Slot at booking time, carrying a monotonically-increasing token number that is never reused within that Session even after a cancellation. Since no Booking entity exists yet (018-queue-token-booking, this feature's only real trigger, is later in build order), this feature ships the token-issuance capability as a reusable, directly-callable service — the same 'generic capability now, wiring later' pattern already used by 011/012/036 in this backlog. No delay-figure tracking for queue-mode sessions (out of scope, queue position — 024, not yet built — is the analogous concept instead). (Full source: backlog/013-queue-mode-slot-on-demand-generation.md)"

## Scope Decisions Made During Drafting

No prior conversation turn resolved these — they're research-backed defaults made while writing this spec, not a `/speckit-clarify` session (which runs next and may revisit any of them).

- **This feature exposes token issuance as a directly-callable service capability, with no automatic trigger.** Unlike 012 (which had a real, already-existing trigger — 011's own generation job — to hook into), this feature's only real trigger is "a booking happens," and no booking-creation feature (016/017/018) exists yet anywhere in this backlog. This feature therefore ships `QueueSlotService.issueNextSlot(sessionId)` as the contract 018 will call once built — it is never invoked automatically by anything in this feature's own scope.
- **`Slot` (012) is extended in place to support Queue-mode's shape, not duplicated into a second entity.** A Queue-mode Slot has no fixed start/end time (009's own business rule: "tokens are sequential rather than time-sliced") but does have a token number; a Fixed-Time Slot is the reverse. Rather than two separate entities for what both later features (016/017/018, 020, 023, 024) will likely want to query uniformly as "the Slot for this booking," this feature adds a nullable `tokenNumber` field to the existing `Slot` entity and makes its existing `startTime`/`endTime` fields nullable — mirroring this backlog's established pattern of extending an entity in place when a later feature needs a new, mode-specific shape (e.g. 006/008 extending `DoctorProfile`).
- **Never-reused token numbers are guaranteed by tracking the highest token number ever issued for a Session, not a count of currently-active Slots.** The source business rule explicitly requires this to survive cancellation (a cancelled Slot's token is never reissued) — a live count would be wrong the moment anything is ever removed or cancelled; "one more than the highest ever issued" is correct regardless of what happens to earlier tokens afterward.
- **The race between two concurrent token-issuance attempts for the same Session is closed at the data layer with a retry loop**, mirroring 011's own already-proven pattern (a non-transactional outer retry loop calling a fresh-transaction-per-attempt inner method) — appropriate here because, unlike 011's astronomically rare schedule-generation race, two patients booking the same walk-in queue at nearly the same moment is a realistic scenario this feature must handle gracefully, not merely close with an ungraceful constraint failure.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Each Booking Issues Exactly One New Slot With the Next Unused Token (Priority: P1)

Every time something calls this feature's token-issuance capability for a Queue/Token Session, exactly one new Slot is created, carrying the next token number that has never been issued for that Session before — starting at 1 for the first call, incrementing by exactly 1 each time, regardless of what happened to any earlier-issued Slot.

**Why this priority**: This is the entire feature — the guarantee a future booking flow (018) depends on to give patients an accurate, gapless queue position.

**Independent Test**: Call the token-issuance capability repeatedly against a Queue/Token Session; confirm each call creates exactly one new Slot with a token number one higher than the previous call's, with no gaps and no repeats.

**Acceptance Scenarios**:

1. **Given** a Queue/Token Session with zero Slots, **When** the token-issuance capability is called for the first time, **Then** exactly one Slot is created with token number 1.
2. **Given** a Session already has Slots with token numbers 1–5, **When** the capability is called again, **Then** a new Slot is created with token number 6.
3. **Given** a Session's Slot with token number 3 is later marked in a way that represents cancellation (out of this feature's own scope to define, but its token row still exists and is simply no longer "active"), **When** a subsequent call is made, **Then** the newly created Slot receives the next unused token number counting from the highest ever issued (e.g. 6), never re-issuing token 3.
4. **Given** two concurrent calls to the capability for the same Session, **When** both are processed, **Then** both succeed, each receiving a distinct, sequential token number — never the same number twice, and no gap.
5. **Given** a call to the capability naming a Session whose mode is Fixed-Time, not Queue/Token, **When** it is made, **Then** it is rejected — this capability only ever issues tokens for Queue/Token Sessions.

---

### Edge Cases

- What happens when the named Session doesn't exist at all? → Rejected as not found.
- What happens to a Queue/Token Session's delay figure? → None is ever computed or stored by this feature — out of scope per the source material; queue position (024, not yet built) is the intended analogous concept for queue-mode Sessions, not something this feature computes either.
- What happens if token issuance is attempted many times in rapid succession for the same Session (not necessarily concurrent, just rapid sequential calls)? → Each call still receives the correct next number in order — the sequential case is simply the degenerate, always-successful-on-first-attempt case of the same guarantee that also covers genuine concurrency.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST provide a capability that, given a Queue/Token Session, creates exactly one new Slot for it and returns it.
- **FR-002**: Each newly-created Slot's token number MUST be exactly one greater than the highest token number ever issued for that Session, or `1` if none has been issued yet.
- **FR-003**: A token number, once issued for a Session, MUST NEVER be issued again for that same Session, regardless of what later happens to the Slot that received it.
- **FR-004**: System MUST reject a token-issuance attempt naming a Session whose mode is not Queue/Token.
- **FR-005**: System MUST reject a token-issuance attempt naming a Session that does not exist.
- **FR-006**: Under concurrent token-issuance attempts for the same Session, system MUST ensure every attempt succeeds with a distinct, correctly-sequential token number — never two attempts receiving the same number.
- **FR-007**: This feature MUST NOT compute, store, or expose any delay figure for a Queue/Token Session.
- **FR-008**: This feature MUST NOT create any Slot for a Session automatically — every Slot this feature creates is the direct result of an explicit call to its issuance capability.

### Key Entities

- **Slot** (from 012, extended here): gains a nullable `tokenNumber` field (populated only for Queue/Token-mode issuance); its existing `startTime`/`endTime` fields become nullable, since a Queue-mode Slot has neither.
- **Session** (from 011, read-only here): the Queue/Token Session a Slot is issued against; this feature reads its `mode` to enforce FR-004.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of token-issuance calls against a Session produce exactly one new Slot, with a token number exactly one higher than the previous highest for that Session (or `1` for the first).
- **SC-002**: 100% of concurrent token-issuance attempts against the same Session succeed, each with a distinct token number — zero duplicate tokens, zero gaps, verified directly at the data layer.
- **SC-003**: 100% of token-issuance attempts against a non-Queue/Token Session, or a nonexistent Session, are rejected — zero Slots created in either case.
- **SC-004**: 100% of Queue/Token Sessions, across every tested scenario, have zero delay-figure data associated with them (verified structurally: no delay-figure field or table exists anywhere in the codebase at the time this feature is built).

## Assumptions

- **No Booking entity exists yet** (016/017/018 are later in build order and unbuilt) — this feature's token-issuance capability has no automatic trigger and is exercised directly in its own tests, the same "generic capability, no premature wiring" pattern already used by 011/012/036.
- **"Marking a Slot as cancelled" is out of this feature's own scope to define** (spec Scope Decisions/Edge Cases) — this feature only guarantees that whatever happens to an already-issued token's Slot later, that token number itself is never reissued; the actual cancellation mechanism belongs to a future feature (e.g. 025/026/027).
- **No HTTP endpoint is introduced by this feature** — service-interface-only contract (Constitution Principle III), mirroring 009's precedent, since its only real caller (018) doesn't exist yet.
