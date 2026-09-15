# Feature Specification: Waitlist Matching (Longest-Waiting, Doctor/Specialization)

**Feature Branch**: `031-waitlist-matching-longest-waiting`

**Created**: 2026-09-04

**Status**: Draft

**Input**: User description: "028 — Waitlist Matching (Longest-Waiting, Doctor/Specialization): As the system, I want to select the correct waitlist entry to offer when a fixed-time slot opens up, so that the longest-waiting, best-matching patient gets the first opportunity to claim it. Matching is a strict two-tier priority: (1) doctor-match tier — among entries specifying the same doctor, offer the longest-waiting one; (2) specialization-only tier — only if tier 1 has zero eligible entries, consider entries specifying the same specialization with no doctor preference, offer the longest-waiting one among those. A specialization-only entry never outranks a doctor-match entry regardless of wait time. Only ever invoked by 025's individual voluntary cancellation trigger — no other event calls this matching logic."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - System Matches and Offers the Correct Waitlist Entry on Cancellation (Priority: P1) 🎯 MVP

When an individual booking cancellation (025) releases a fixed-time slot, the system automatically
selects the single best-matching, longest-waiting waitlist entry and offers that slot to them —
so patients don't have to keep checking back themselves.

**Why this priority**: This is the feature's entire named purpose — the matching algorithm itself.

**Independent Test**: With a doctor-match waitlist entry that has waited less time than an
eligible specialization-only entry, trigger an individual cancellation for that doctor's slot and
confirm the doctor-match entry — not the longer-waiting specialization-only one — is the one
offered.

**Acceptance Scenarios**:

1. **Given** a doctor-match waitlist entry waiting 1 hour and a specialization-only entry (no
   doctor preference) waiting 3 days, both otherwise eligible, **When** a slot opens for that
   doctor via an individual cancellation, **Then** the doctor-match entry is offered, even though
   it has waited less time — doctor-match is an absolute priority tier, never a tiebreaker
   candidate against specialization-only.
2. **Given** multiple entries are eligible within the same tier (either multiple doctor-match, or
   multiple specialization-only), **When** a slot opens, **Then** the entry that joined the
   waitlist earliest within that tier is offered.
3. **Given** no waitlist entry matches either tier's criteria, **When** a slot opens via
   cancellation, **Then** no offer is made and the slot simply remains open for regular booking.
4. **Given** a whole-day (026) or partial (027) session cancellation, or an automatic no-show
   release (021), **When** slots are released by any of those, **Then** this matching logic is
   never invoked — only 025's individual voluntary cancellation ever triggers it.
5. **Given** a matching waitlist entry is found, **When** it is offered, **Then** it is marked
   `OFFERED` and the matching patient is notified (036/037) — the actual claim action and its
   time window belong to a later feature; this feature's own responsibility ends at selecting and
   marking the offer.

---

### User Story 2 - A Patient Joins the Waitlist for a Doctor or Specialization (Priority: P1) 🎯 Necessary Prerequisite

A patient (or staff on their behalf) joins a clinic's waitlist for either a specific doctor or any
doctor with a given specialization, so there is something for User Story 1's matching to actually
select from.

**Why this priority**: No prior feature in this backlog defines any "join the waitlist" action or
a Waitlist Entry at all — without this, User Story 1's matching logic would have nothing to ever
match against. Tied at P1 with User Story 1 for the same reason 026's two stories were tied: each
is only meaningfully demonstrable together with the other.

**Independent Test**: Join the waitlist for a specific doctor at a clinic, and separately for a
specialization with no doctor preference, and confirm both entries are recorded with a join
timestamp establishing first-come-first-served order.

**Acceptance Scenarios**:

1. **Given** an authenticated patient, **When** they join a clinic's waitlist specifying a
   specific doctor, **Then** a waitlist entry is recorded for that doctor, timestamped at the
   moment of joining.
2. **Given** an authenticated patient, **When** they join a clinic's waitlist specifying a
   specialization with no particular doctor, **Then** a waitlist entry is recorded with no doctor
   preference, timestamped at the moment of joining.
3. **Given** an authorized staff member, **When** they join the waitlist on a patient's behalf
   (e.g. the patient called in), **Then** the same outcome applies as a patient joining directly.

---

### Edge Cases

- What happens to a waitlist entry belonging to a Patient Account, once matched, if that patient
  has no way to be notified (Assumptions: every entry requires a linked Patient Account, so this
  case cannot occur — unlike a walk-in Booking, joining the waitlist is inherently
  account-required)?
- What happens if the same patient joins the waitlist for the same doctor (or specialization)
  more than once? Out of scope for this feature to prevent — duplicate entries are simply two
  separate entries, each with its own join time; deduplication is not a stated requirement.
- What happens to a `WAITING` entry for a doctor/specialization at a clinic the patient never
  actually visits before matching occurs? No constraint — matching only reads a fixed-time
  Session's doctor and clinic at the moment of cancellation and searches entries at that same
  clinic; nothing about the entry itself is validated beyond that.
- What happens to an entry that's already `OFFERED` — is it still eligible to be matched again by
  a later cancellation before it's claimed or expires? No — once `OFFERED`, an entry is no longer
  eligible for either tier; only `WAITING` entries are considered (claim/expiry mechanics for an
  `OFFERED` entry belong to a later feature, not this one).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST allow an authenticated patient to join a clinic's waitlist,
  specifying either a specific doctor or a specialization with no doctor preference — never both
  in a way that's ambiguous about which tier the entry belongs to.
- **FR-002**: The system MUST allow an authorized staff member to join the waitlist on behalf of a
  specific patient, with the identical outcome as the patient joining directly.
- **FR-003**: Every waitlist entry MUST record its clinic, its doctor (or null, if
  specialization-only), its specialization, its associated Patient Account, and its join
  timestamp.
- **FR-004**: When — and only when — an individual voluntary booking cancellation (025) releases a
  fixed-time slot, the system MUST search that same clinic's `WAITING` waitlist entries in strict
  tier order: first every entry naming that slot's doctor, then — only if none exist — every entry
  naming that doctor's specialization with no doctor preference.
- **FR-005**: Within whichever tier has at least one eligible entry, the system MUST select the
  entry with the earliest join timestamp.
- **FR-006**: A specialization-only entry MUST NOT be selected while any doctor-match entry
  exists, regardless of relative wait time.
- **FR-007**: If no entry is eligible in either tier, the system MUST make no offer, leaving the
  slot open for regular booking.
- **FR-008**: A selected entry MUST transition from `WAITING` to `OFFERED`, and the matching
  Patient Account MUST be notified (036/037) — this feature's responsibility ends there; the
  actual claim action, its time window, and what happens if it lapses belong to a later feature.
- **FR-009**: This matching logic MUST NEVER be invoked by any trigger other than 025's individual
  voluntary cancellation — not by no-show release (021), whole-day cancellation (026), or partial
  cutoff cancellation (027).
- **FR-010**: Only a `WAITING` entry is ever eligible for matching — an already-`OFFERED` entry is
  excluded from both tiers.

### Key Entities

- **WaitlistEntry** *(new)*: Represents one patient's place in line for either a specific doctor
  or a specialization at a clinic. Fields: clinic, patient account, doctor (nullable — presence
  determines tier), specialization, status (`WAITING`/`OFFERED`), join timestamp. First defined by
  this feature — no prior feature in this backlog creates one.
- **Session/Slot/Booking** *(existing, from 011/012/016/025)*: Read-only for this feature — the
  cancelled Booking's Slot/Session supply the doctor, specialization, and clinic that drive the
  match search; nothing about them is written by this feature.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A doctor-match entry is always offered ahead of any specialization-only entry for
  the same newly-available slot, regardless of relative wait time.
- **SC-002**: Within a tier, the longest-waiting eligible entry is always the one offered.
- **SC-003**: 100% of slot releases from no-show, whole-day, or partial-cutoff cancellation never
  trigger a match attempt.
- **SC-004**: 100% of cancellations with no eligible waitlist entry in either tier result in no
  offer and no state change to any entry.
- **SC-005**: Every successful match results in exactly one entry transitioning to `OFFERED` and
  exactly one notification to that entry's Patient Account.

## Assumptions

- Every waitlist entry requires a linked Patient Account (FR-003) — unlike a walk-in Booking,
  there is no "contact-less" waitlist join; the system must be able to notify whoever is matched,
  and 036/037's notification pipeline is account-based only (026/027's own established
  limitation).
- Both a patient (for themselves) and staff (on a patient's behalf) can join the waitlist (FR-001,
  FR-002) — mirrors this codebase's consistent dual-access pattern for every other booking-lifecycle
  action this session (016/017, 020/021, 025).
- `WaitlistEntry` and its matching/joining logic live in their own module, distinct from
  `com.cms.booking`/`com.cms.scheduling` — the project constitution names "waitlist" as one of
  this system's own intended module boundaries, alongside scheduling, booking, clinical
  documentation, notifications, and discovery.
- Matching runs synchronously as part of handling 025's `BookingCancelledEvent` (an
  `@EventListener`), not a separate polling sweep — mirroring how 037 already consumes 036's own
  event the same way.
- Introducing the `OFFERED` status now (with no claim/expiry mechanics attached yet) mirrors this
  session's own established precedent of building the infrastructure a stated business rule
  requires ahead of the feature that will fully act on it (021's `Slot.onHold`, 036's
  notification opt-in fields before any real caller existed).
