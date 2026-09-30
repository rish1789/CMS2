# Feature Specification: Queue Token Issuance Under Concurrent Requests

**Feature Branch**: `claude/067-queue-token-issuance-race`

**Created**: 2026-09-30

**Status**: Draft

**Input**: User description: "PB-003: queue token issuance fails under concurrent requests. When several queue bookings (patient self-service, staff-assisted) or front-desk walk-ins request a token for the same session at the same time, QueueSlotService.issueWithRetry computes max(token)+1 and inserts; a lost race on the unique index uq_slot_session_token is supposed to be retried, but the retry calls its own @Transactional method (self-invocation, so the proxy is bypassed), the attempt joins the caller's transaction when there is one, and it gives up after 5 attempts with TokenIssuanceFailedException. Result: a legitimate patient or walk-in can be refused a token purely because someone else booked at the same moment. Reproduced by QueueSlotIssuanceConcurrencyTest (20 concurrent calls, one fails) - seen in CI on PR #21. Goal: every concurrent request for a token in an accepting session receives exactly one distinct, sequential token (no gaps from failed attempts visible to patients, no duplicates), whether issued standalone or inside a booking/walk-in transaction; failure only for real reasons (session not accepting, wrong mode). Out of scope: changing token numbering rules, queue position logic, or the booking limit/rate limit."

## Context

Two existing specs already require this behaviour:

- **019-queue-slot-on-demand-generation**, FR-006 and SC-002: under concurrent token requests for the same Session, *every* attempt succeeds with a distinct, sequential token number, with no duplicates and no gaps.
- **022-queue-token-booking**, SC-004: concurrent queue bookings each receive a distinct, never-reused token.

The "no duplicates" half holds today, because the data layer refuses a second copy of the same token number. The "every attempt succeeds" half does not. When several requests for the same Session arrive together, a request that loses the race for a number is supposed to try again with the next one. In practice it gives up after a handful of tries and the caller is refused. The defect register records this as **PB-003** (`docs/product-audit/07-BUG-AND-DEFECT-REGISTER.md`). It was reproduced by `QueueSlotIssuanceConcurrencyTest`: 20 simultaneous requests, and one was refused. It was seen again in CI on 2026-09-30.

Tokens are issued on three paths:

1. **Patient self-service queue booking.** Issues the token, then creates the booking as a separate step.
2. **Staff-assisted queue booking.** Same shape as path 1.
3. **Front-desk walk-in registration.** Issues the token inside the registration's own all-or-nothing step. On this path, a lost race cannot be retried at all: the first collision spoils the whole registration.

This feature restores 019 FR-006/SC-002 and 022 SC-004 on all three paths. Per the 2026-09-30 clarification, it also makes queue bookings all-or-nothing, so a failed booking can no longer leave an orphan token. That orphan-token case is part of 019 SC-002's "no gaps" promise, but it had not been recorded as a defect.

## Clarifications

### Session 2026-09-30

- Q: On the patient and staff queue paths the token is issued first and the booking created second. If the booking then fails, the token stays as a numbered "waiting" place with no patient, and it inflates later patients' queue positions. Is guaranteeing that a failed booking leaves no token behind in scope? → A: Yes, include it in 067. A queue booking and its token become all-or-nothing on the patient and staff paths, as walk-in registration already is (FR-005, FR-008, User Story 4).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Simultaneous queue bookings for one session all get a token (Priority: P1)

Several patients book the same doctor's queue session at the same moment. This happens at popular sessions when a session opens, or when front-desk staff book on a patient's behalf while patients book themselves online. Each request must get its own token number, and none may be refused just because another request arrived at the same moment.

**Why this priority**: This is the core of the defect. A patient who did everything right is refused a place in the queue with an unexplained error, and the busier the session, the more likely it is.

**Independent Test**: Start many concurrent token requests for one accepting queue Session. Verify that every request succeeds, the tokens issued are exactly 1..N with no repeats and no missing numbers, and no request fails.

**Acceptance Scenarios**:

1. **Given** an accepting queue Session with no tokens yet, **When** 20 token requests for it arrive at the same moment, **Then** all 20 succeed and the tokens issued are exactly 1 to 20, each exactly once.
2. **Given** the same Session, **When** patient self-service bookings and staff-assisted bookings for it are submitted at the same moment, **Then** every booking is confirmed with its own distinct token.
3. **Given** a Session that already has tokens 1 to 5, **When** further simultaneous requests arrive, **Then** they receive 6, 7, 8... in some order, each exactly once.

---

### User Story 2 - Simultaneous walk-in registrations are never refused because of each other (Priority: P1)

At a busy front desk, two staff members register walk-ins for the same session at the same moment. Each walk-in must be registered with their own place in the line. Today, on this path a single collision fails the whole registration, with no retry at all.

**Why this priority**: This is the same defect on the front-desk path, where it is worse: there is no retry at all, and a refused walk-in is a patient physically waiting at the desk.

**Independent Test**: Start concurrent walk-in registrations for one session. Verify every registration succeeds, each with a distinct place in the walk-in line.

**Acceptance Scenarios**:

1. **Given** a session accepting walk-ins, **When** several walk-in registrations for it are submitted at the same moment, **Then** every registration succeeds and each walk-in has a distinct, sequential place in the line.
2. **Given** a walk-in registration that fails for a real reason, such as the duplicate walk-in rule, **When** it is submitted alongside other registrations, **Then** only that registration is refused, with its own reason, and the others still succeed.

---

### User Story 3 - Refusals only for real reasons (Priority: P2)

When a token request is refused, the reason must be a real one: the session is not accepting, it is the wrong kind of session, or the booking itself is not allowed. It must never be refused just because other requests were in progress.

**Why this priority**: This keeps the fix honest. It must not hide real refusals, or turn them into something else, while removing the false ones.

**Independent Test**: Submit token requests to a session that is not accepting and to a session of the wrong type, both alone and alongside concurrent valid requests. Verify the invalid ones are refused with their existing reasons and the valid ones succeed.

**Acceptance Scenarios**:

1. **Given** a session that is cancelled, past, or otherwise not accepting, **When** a token is requested, **Then** it is refused with the existing "not accepting" reason, whether or not other requests are in progress.
2. **Given** a fixed-time session, **When** a queue token is requested for it, **Then** it is refused with the existing wrong-mode reason.

---

### User Story 4 - A failed queue booking leaves no token behind (Priority: P2)

A patient or staff member books a queue session, and the booking fails after the token number was reserved. For example, the chosen appointment type is removed at that moment, or the patient record cannot be resolved. The patient sees the booking refused, as today. But no numbered "waiting" place may be left behind, because every later patient's queue position would count it as someone ahead of them.

**Why this priority**: This is rarer than the concurrency failure, but it silently distorts other patients' queue positions, and it is the same "no gaps" guarantee as 019 SC-002. It touches exactly the code the concurrency fix changes.

**Independent Test**: Make a queue booking fail after token issuance by forcing a failure in the booking step. Verify that no token remains for it, that the next successful booking takes the number the failed one would have had, and that queue positions are unaffected.

**Acceptance Scenarios**:

1. **Given** an accepting queue session with tokens 1 to 3, **When** a patient self-service booking fails after a token was reserved for it, **Then** no token 4 remains, and the next successful booking receives token 4.
2. **Given** the same situation on the staff-assisted path, **When** the booking fails, **Then** the same holds.
3. **Given** a patient holding token 5 while a booking that would have been token 4 failed, **When** they view their queue position, **Then** it counts only real waiting patients ahead of them.

---

### Edge Cases

- **Very large burst for one session.** Every request still succeeds, possibly waiting briefly for its turn. The only acceptable failure is an extreme, bounded wait. If that ever happens, the caller gets a clear "please try again" refusal, never a silent failure or a duplicate token.
- **Requests for different sessions at the same moment.** They must not slow each other down or interfere. Each session numbers its tokens independently, as today.
- **Token reserved, then the booking fails for an unrelated reason** (for example, the appointment type is removed at that moment). No token remains, and the number is used by the next successful booking (FR-008, User Story 4).
- **A failed booking's number is taken by a concurrent request.** Only committed bookings hold numbers, so the sequence of committed tokens stays unbroken (FR-002).
- **Walk-in lines in fixed-time sessions** (W1, W2...) use the same numbering mechanism as queue tokens. They get the same guarantee.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: When several token requests for the same accepting session arrive at the same moment, the system MUST give every one of them a token. None may be refused because of the others. (Restores 019 FR-006.)
- **FR-002**: Every token issued for a session MUST be distinct, and together they MUST form an unbroken sequence (1, 2, 3...), whatever the order in which concurrent requests are served. (Restores 019 SC-002, 022 SC-004.)
- **FR-003**: FR-001 and FR-002 MUST hold on all three issuing paths: patient self-service queue booking, staff-assisted queue booking, and front-desk walk-in registration. On the walk-in path this includes fixed-time sessions' walk-in lines.
- **FR-004**: A token request MUST still be refused, with its existing reason, when the session does not exist, is not accepting, or is the wrong kind of session. The fix MUST NOT change any existing refusal or its message.
- **FR-005**: Walk-in registration MUST stay all-or-nothing: a registration that fails for any reason leaves no walk-in, booking or token behind, as today.
- **FR-008**: Patient self-service and staff-assisted queue bookings MUST be all-or-nothing in the same way: a booking that fails for any reason after a token was reserved leaves no token behind. A token exists only for a confirmed booking or walk-in registration.
- **FR-006**: Token requests for different sessions MUST NOT block or slow each other.
- **FR-007**: If a request cannot be served within a bounded time under extreme load, it MUST be refused with a clear "please try again" response and MUST NOT leave a partial token or booking behind. Under normal and busy load (see SC-002) this MUST NOT occur.

### Key Entities

- **Session**: one doctor's sitting on one date, either queue mode or fixed-time. Each session owns its own token sequence.
- **Token (queue place)**: a numbered place in a session's queue, or in a fixed-time session's walk-in line. Each number appears at most once per session.
- **Booking / Walk-in registration**: the patient's claim on a token. Created by the three paths above.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of concurrent token requests to one accepting session succeed, with zero refusals caused by other requests. Verified with at least 20 simultaneous requests, repeated across at least 10 consecutive runs with no failure.
- **SC-002**: Across those runs, the tokens issued for each session are exactly 1..N, with zero duplicates and zero missing numbers.
- **SC-003**: The same holds for simultaneous walk-in registrations for one session: every registration succeeds, with distinct, sequential places.
- **SC-004**: A single, uncontended booking or walk-in takes no noticeably longer than today.
- **SC-005**: Every existing refusal (not accepting, wrong mode, duplicate walk-in, booking limit, rate limit) still occurs with the same reason and message. All existing tests for these still pass.
- **SC-006**: The test `QueueSlotIssuanceConcurrencyTest` passes consistently. It is currently intermittent.
- **SC-007**: After any failed queue booking, on either path, zero tokens exist without a confirmed booking or walk-in, and later patients' queue positions count only real waiting patients.

## Assumptions

- **No change to numbering rules.** Tokens still start at 1 per session and go up by one. Walk-in places (W1, W2...) use the same counter. Queue position logic, the booking limit and the rate limit are unchanged.
- **Waiting briefly is acceptable.** Under a burst, it is acceptable for requests for one session to be served one after another, with a short wait, rather than all at once. A few hundred milliseconds of extra wait at most under a 20-request burst fits the existing latency expectations (060 NFR-002 treats the booking path as needing "no perceptible delay" for normal bookings).
- **No schema change is expected**, although the plan may conclude otherwise. The existing one-number-per-session rule at the data layer stays as the safety net.
- **A session cancelled at the same moment as a token is issued** may still receive that token before the cancellation takes effect, exactly as today. Serializing issuance with session cancellation is out of scope (owner decision 2026-09-30, from analysis F1). The token's booking is then subject to the normal cancellation outcomes.
- **Scope is the three issuing paths named above**, plus the all-or-nothing guarantee for queue bookings (FR-008, per the 2026-09-30 clarification). Other races, and the patient self-cancel refusal for queue bookings, are out of scope.
