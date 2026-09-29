# Feature Specification: Self-Service Waitlist Claim

**Feature Branch**: `032-self-service-waitlist-claim`

**Created**: 2026-09-04

**Status**: Draft

**Input**: User description: "029 — Self-Service Waitlist Claim: As a waitlisted patient, I want to claim or decline an offered slot myself within the claim window, so that I don't have to wait for a phone call from the clinic to secure it. Offer window is 30 minutes from the moment 028's matching offers it. Claiming within the window atomically converts the Waitlist Entry into a confirmed Booking, with the fee resolved and locked at the moment of claim per normal booking-time rules (015). Declining releases the offer immediately and triggers the same cascade as an expiry. If the window expires with no claim or decline (or the patient explicitly declines), the system automatically re-runs 028's matching against the remaining waitlist and offers the slot to the next-longest-waiting eligible entry, with a fresh 30-minute window — repeating until an entry claims the slot or the eligible waitlist is exhausted, at which point the slot becomes available for regular booking."

## Clarifications

### Session 2026-09-04

- Q: While a waitlist entry's 30-minute offer is outstanding, should the slot stay bookable
  through the ordinary booking flow (016/017/018), with a claim attempt simply falling through to
  the next-longest-waiting entry if it discovers the slot was already taken that way? → A: Leave
  the slot bookable and recheck at claim time — no new Slot state. If an ordinary booking wins the
  slot first, a subsequent claim attempt fails gracefully and the system immediately re-matches to
  the next eligible entry, the same "lost race → move on" pattern already used elsewhere in this
  codebase (028's own concurrency fix, 026, 027).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Patient Claims an Offered Slot (Priority: P1) 🎯 MVP

A patient whose waitlist entry has been offered a slot (028) claims it themselves, within the
30-minute window, converting their place in line directly into a confirmed booking — no phone
call to the clinic required.

**Why this priority**: This is the feature's entire named purpose — the self-service claim
action that replaces the phone-based workaround the source material explicitly calls out as a
gap.

**Independent Test**: With a waitlist entry in `OFFERED` status and its 30-minute window still
open, claim it as that entry's patient and confirm a new confirmed Booking now exists for that
slot, with the entry's status reflecting the claim.

**Acceptance Scenarios**:

1. **Given** a waitlist entry is `OFFERED` a slot, **When** the owning patient claims it within
   the 30-minute window, **Then** the entry is converted into a confirmed Booking atomically and
   the fee is resolved and locked at that moment, per the normal booking-time rules (015) — not
   at the original join time.
2. **Given** a waitlist entry is `OFFERED`, **When** someone other than the entry's own patient
   attempts to claim it, **Then** the claim is rejected.
3. **Given** a waitlist entry's 30-minute window has already lapsed, **When** its patient
   attempts to claim it, **Then** the claim is rejected — the entry has already moved on per User
   Story 3.

---

### User Story 2 - Patient Declines an Offered Slot (Priority: P2)

A patient who doesn't want the slot they've been offered explicitly declines it, immediately
freeing it up for the next-longest-waiting eligible patient rather than making them wait out the
full 30 minutes.

**Why this priority**: A real but secondary path — most of the feature's value is delivered by
claiming (P1) working at all; declining is a courtesy that speeds up an already-correct fallback
(User Story 3) rather than introducing new capability the system couldn't eventually reach
without it.

**Independent Test**: With a waitlist entry in `OFFERED` status, decline it as that entry's
patient and confirm the entry no longer holds the offer, and — with another eligible waiting
entry present — that entry is now `OFFERED` the same slot with a fresh window.

**Acceptance Scenarios**:

1. **Given** a waitlist entry is `OFFERED` a slot, **When** the owning patient declines it,
   **Then** the offer is released immediately and 028's matching logic re-runs against the same
   slot, offering it to the next-longest-waiting eligible entry with a fresh 30-minute window.
2. **Given** a waitlist entry is `OFFERED` a slot and no other eligible entry exists, **When** the
   patient declines it, **Then** no further offer is made and the slot becomes available for
   regular booking.

---

### User Story 3 - Offer Automatically Expires and Re-Offers (Priority: P2)

When an offered patient neither claims nor declines within the 30-minute window, the system
notices on its own and moves on to the next-longest-waiting eligible entry, so a single
unresponsive patient never permanently blocks the slot from reaching anyone else.

**Why this priority**: The correctness backstop that makes User Story 1/2 safe to rely on — without
it, an unresponsive patient's offer would never resolve. Ranked alongside declining since both
share the same underlying "release and re-offer" mechanism, just triggered differently.

**Independent Test**: With a waitlist entry `OFFERED` a slot and its window already lapsed (per a
controllable, testable clock), run the expiry sweep and confirm that entry no longer holds the
offer and — with another eligible entry present — it is now `OFFERED` the same slot with a fresh
window; repeat with no further eligible entries and confirm the slot is left available for
regular booking.

**Acceptance Scenarios**:

1. **Given** a waitlist entry is `OFFERED` a slot and 30 minutes elapse with no claim or decline,
   **When** the expiry sweep runs, **Then** the offer lapses and 028's matching logic re-runs
   against the same slot, offering it to the next-longest-waiting eligible entry with a fresh
   30-minute window.
2. **Given** every eligible waitlist entry for a slot has, in turn, declined or let its offer
   expire, **When** the last one lapses, **Then** the slot becomes available for regular booking
   with no further waitlist offers made.
3. **Given** a waitlist entry's window has not yet lapsed, **When** the expiry sweep runs,
   **Then** that entry is left untouched.

---

### Edge Cases

- What happens if the same entry's claim and the expiry sweep are attempted at nearly the same
  moment? Whichever wins the underlying state transition succeeds; the other is rejected as
  no-longer-`OFFERED` — no double-booking, no double-re-match (Constitution IV data-layer
  race-closure, mirroring 028's own convergence fix for the identical class of race).
- What happens if the slot an offer refers to gets booked through the ordinary booking flow
  (016/017/018) by someone else while the offer is still outstanding? The slot is never reserved
  or held during the offer window (Clarifications) — a claim attempt re-verifies the slot is
  still open at the moment of claim; if it was already taken ordinarily, the claim fails
  gracefully and the system immediately re-matches to the next-longest-waiting eligible entry,
  the same cascade a decline triggers.
- What happens to a `WaitlistEntry` that was already `CLAIMED` or `EXPIRED` if a stale client
  retries the same claim/decline request? Rejected the same way an already-non-`OFFERED` entry
  always is (User Story 1, Acceptance Scenario 3) — idempotent by rejection, not by silently
  succeeding twice.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST allow the patient who owns an `OFFERED` waitlist entry to claim it,
  within its 30-minute window, atomically converting it into a confirmed Booking for the offered
  slot.
- **FR-002**: A claim MUST resolve and lock the fee at the moment of claim, using the same
  fee-resolution-and-locking rules (015) applied to any other booking — never a fee snapshotted
  at the original join time.
- **FR-003**: The system MUST reject a claim attempt by anyone other than the entry's own patient.
- **FR-004**: The system MUST reject a claim attempt against an entry that is not currently
  `OFFERED` (already claimed, already expired, already declined, or still merely `WAITING`).
- **FR-004a**: The offered slot is never reserved or held during the 30-minute window — it
  remains open to ordinary booking (016/017/018) throughout. A claim attempt MUST re-verify the
  slot is still open at the moment of claim; if it was already booked ordinarily in the meantime,
  the claim MUST fail gracefully and trigger the same next-entry re-matching cascade as FR-006.
- **FR-005**: The system MUST allow the patient who owns an `OFFERED` waitlist entry to explicitly
  decline it within its 30-minute window.
- **FR-006**: A decline MUST immediately release the offer and trigger the same re-matching
  cascade as FR-007.
- **FR-007**: The system MUST detect `OFFERED` entries whose 30-minute window has lapsed with no
  claim or decline, and for each, release the offer and re-run 028's matching logic against the
  same slot's clinic/doctor, offering it to the next-longest-waiting eligible entry with a fresh
  30-minute window.
- **FR-008**: The release-and-re-offer cascade (FR-006/FR-007) MUST repeat, entry by entry, until
  either an entry claims the slot or no further eligible `WAITING` entry exists for that slot.
- **FR-009**: When the eligible waitlist for a slot is exhausted with no claim, the system MUST
  make no further offer, leaving the slot available for regular booking.
- **FR-010**: The system MUST prevent two concurrent actions (a claim, a decline, or an expiry
  sweep) on the same entry from both succeeding — exactly one transition away from `OFFERED` per
  entry is ever durable (Constitution IV).

### Key Entities

- **WaitlistEntry** *(existing, from 028)*: Gains two states this feature introduces —
  `CLAIMED` and `EXPIRED` (declining reuses `EXPIRED`, since both mean "this particular offer is
  over, without a claim") — plus a field recording which specific Slot it was offered, needed
  here for the first time since 028 itself never had to act on that information again.
- **Booking** *(existing, from 016/017)*: What a successful claim creates — via the same
  creation path and fee-resolution rules any other booking uses, not a special-cased shortcut.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Every successful claim results in exactly one new confirmed Booking, with its fee
  resolved and locked at the moment of claim.
- **SC-002**: 100% of claim attempts by a patient who doesn't own the offered entry are rejected.
- **SC-003**: 100% of decline or expiry events result in either the next-longest-waiting eligible
  entry being offered the same slot, or — if none remain — the slot being left available for
  regular booking with no further offer made.
- **SC-004**: Under concurrent claim/decline/expiry attempts against the same entry, exactly one
  ever succeeds.
- **SC-005**: A patient can go from receiving an offer to holding a confirmed booking in a single
  self-service action, with no clinic phone call involved.

## Assumptions

- `WaitlistEntry` gains an `offeredSlotId` field (nullable, set only when `OFFERED`) — 028 never
  needed to persist which specific Slot an offer referred to (it only notified once), but this
  feature is the first to need to act on that information again, both to convert a claim into a
  Booking for the *right* slot and to re-run matching against that *same* slot on decline/expiry.
- The expiry sweep runs on a real, periodic schedule, mirroring the no-show detection feature's
  own `NoShowDetectionService`/`NoShowDetectionTrigger` split (a service holding the sweep logic,
  a thin `@Component` firing it via Spring's `@Scheduled(cron = ...)`) — Spring's scheduler
  already exists and runs in this codebase for exactly this class of "notice a time-based
  condition on its own" concern, so this feature reuses that established mechanism rather than
  introducing a different one.
- Claiming reuses 016/017's existing booking-creation path and 015's fee-resolution-and-locking
  logic exactly — this feature does not reimplement booking creation, only the trigger that calls
  into it from a waitlist entry instead of a fresh slot pick.
- Both a patient (for themselves) and staff acting on a patient's behalf are *not* both supported
  here — unlike 028's join action, claiming/declining is patient-self-service only; staff have no
  stated need to claim on a patient's behalf (the source material's own framing is explicitly
  about removing the phone-call workaround, not preserving a staff-mediated path for this
  specific action).
- Declining and expiring are modeled as the same terminal `EXPIRED` state — the source material
  itself describes them as triggering "the same cascade behavior," and no acceptance criterion
  asks the system to distinguish *why* an offer lapsed after the fact.
