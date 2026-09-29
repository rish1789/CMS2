# Feature Specification: De-Verification Cascade (Auto-Cancel Future Bookings)

**Feature Branch**: `033-deverification-cascade-auto-cancel`

**Created**: 2026-09-04

**Status**: Draft

**Input**: User description: "008 — De-Verification Cascade (Auto-Cancel Future Bookings): As a Super Admin, when I un-verify a clinic or explicitly reject/revoke a doctor's license, I want the system to automatically cancel their future bookings, so that patients aren't left holding appointments against a provider who has just lost verification. Trigger 1: Super Admin un-verifies a previously verified Clinic. Trigger 2: Super Admin explicitly rejects or revokes a Doctor's license via an intentional admin action (distinct from the automatic edit-triggered reset in 006, which does NOT trigger this cascade). On either trigger, every future (not-yet-occurred) booking tied to the affected clinic or doctor is automatically cancelled, reusing normal individual-booking cancellation mechanics (025) — for fixed-time bookings this means the standard waitlist-bump flow fires. Each affected patient generates a cancellation notification event (036/037). The cascade never touches past/completed bookings. Re-verifying later does not restore cancelled bookings."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Un-Verifying a Clinic Cancels Its Future Bookings (Priority: P1) 🎯 MVP

A Super Admin un-verifies a clinic that's since been found to have a problem, and every
not-yet-occurred booking at that clinic — across every doctor — is automatically cancelled, so no
patient shows up to an appointment the clinic can no longer be trusted to honor.

**Why this priority**: The more consequential of the two triggers (an entire clinic, not one
doctor) and the one already structurally ready to consume — `ClinicDeVerifiedEvent` already exists
and already fires on every genuine un-verify, with no listener yet.

**Independent Test**: Un-verify a clinic with several future bookings across multiple doctors and
confirm every one of them is cancelled, with fixed-time ones triggering a waitlist bump wherever a
matching waitlist entry exists.

**Acceptance Scenarios**:

1. **Given** a verified Clinic with 5 future bookings across various doctors, **When** Super Admin
   un-verifies it, **Then** all 5 future bookings are automatically cancelled.
2. **Given** one of those cancelled bookings was a fixed-time booking with a matching `WAITING`
   waitlist entry, **When** it's cancelled by this cascade, **Then** the standard waitlist-bump
   flow (028) fires for it, exactly as if it had been cancelled individually (025).
3. **Given** the same clinic also has past/completed bookings, **When** the cascade runs, **Then**
   those are left completely untouched.
4. **Given** the clinic is later re-verified, **When** checked, **Then** the bookings this cascade
   cancelled remain cancelled — they are not restored.

---

### User Story 2 - Explicitly Revoking a Doctor's License Cancels Their Future Bookings (Priority: P1)

A Super Admin explicitly revokes a doctor's license (a deliberate admin action, not the automatic
reset that already happens when a doctor edits their own license number), and every one of that
doctor's not-yet-occurred bookings — across every clinic they're staffed at — is automatically
cancelled.

**Why this priority**: The other stated trigger, and — unlike Trigger 1 — the admin action itself
doesn't exist in the system yet at all; this feature is the first to need it, so it must build it
as a necessary prerequisite (mirrors this session's now-consistent "the feature that first needs
missing infrastructure builds it" pattern).

**Independent Test**: Explicitly revoke a verified doctor's license and confirm every one of their
future bookings, across every clinic, is cancelled — while a routine license-number edit that
merely resets verification automatically triggers nothing.

**Acceptance Scenarios**:

1. **Given** a Doctor with `licenseVerified = true` and 3 future bookings, **When** Super Admin
   explicitly revokes their license, **Then** those 3 future bookings are auto-cancelled.
2. **Given** a Doctor whose `licenseVerified` resets purely because they edited their license
   number (006, not an explicit Super Admin action), **When** that happens, **Then** no cascade
   fires — only discoverability is affected, exactly as 006 already established.
3. **Given** a Doctor whose license is already unverified, **When** Super Admin calls the revoke
   action again, **Then** it's a no-op — no second cascade fires (mirrors Trigger 1's own
   idempotent un-verify behavior).

---

### Edge Cases

- What happens to a booking whose Slot has already reached a resolved outcome (`NO_SHOW`,
  `COMPLETED`) by the time the cascade runs? Left untouched — only a Booking whose Slot is still
  `BOOKED` (i.e., genuinely still pending an outcome) is ever cancelled by this cascade; a Slot
  that's already resolved is not "future" regardless of its calendar date.
- What happens to a Queue-mode booking, which has no fixed date/time the way a fixed-time booking
  does? See Assumptions — cancelled the same way, but through a different mechanical path than
  fixed-time bookings, since 025's own cancellation service is fixed-time-only.
- What happens if the same clinic is un-verified twice in a row without ever being re-verified in
  between? The second call is a no-op (already established by 003's existing idempotent
  `unverify`) — no second cascade run, no duplicate cancellations.
- What happens to a walk-in booking (no linked Patient Account) affected by this cascade? It's
  still cancelled, but generates no notification event — there's no account to notify (mirrors
  026/027/029's identical existing handling of unlinked walk-in patients).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: When a Clinic transitions from verified to un-verified, the system MUST
  automatically cancel every currently-active booking at that clinic whose Slot has not yet
  reached a resolved outcome (still `BOOKED`), regardless of which doctor it's with.
- **FR-002**: The system MUST provide a Super Admin action to explicitly revoke a Doctor's
  verified license — distinct from the automatic reset that already occurs when a doctor edits
  their license number (006), which MUST continue to never trigger this cascade.
- **FR-003**: When a Doctor's license transitions from verified to unverified specifically via
  the explicit revoke action (FR-002), the system MUST automatically cancel every currently-active
  booking with that doctor, across every clinic they're staffed at, whose Slot has not yet reached
  a resolved outcome.
- **FR-004**: For a fixed-time booking cancelled by this cascade, the system MUST trigger the same
  waitlist-bump behavior as an individual voluntary cancellation (025/028) — exactly as if a
  patient or staff member had cancelled that one booking on its own.
- **FR-005**: For a Queue-mode booking cancelled by this cascade, the system MUST release its Slot
  the same way an individual cancellation does, without triggering a waitlist bump (028's matching
  logic remains exclusively fixed-time, per 025's own established scope).
- **FR-006**: The system MUST generate one cancellation notification event (036) for each affected
  booking's linked Patient Account; a booking with no linked Patient Account (a walk-in) generates
  no notification.
- **FR-007**: The cascade MUST NOT touch any booking whose Slot has already reached a resolved
  outcome (`NO_SHOW`, `COMPLETED`), any already-cancelled booking, or any clinical documentation.
- **FR-008**: Re-verifying a clinic or re-verifying (i.e., verifying again) a doctor whose license
  was revoked MUST NOT restore any booking this cascade previously cancelled.
- **FR-009**: A repeated de-verification action against an already-unverified clinic or an
  already-unverified (via revoke) doctor MUST be a no-op — no cascade fires a second time.

### Key Entities

- **Clinic** *(existing, 001)*: Read-only trigger source for this feature (Trigger 1) — its
  existing `verified` flag and de-verification event are reused unchanged.
- **DoctorProfile** *(existing, 004)*: Gains, for the first time, an explicit Super-Admin-driven
  path from verified to unverified (Trigger 2) — distinct from 006's automatic edit-triggered
  reset, which remains cascade-exempt.
- **Booking** *(existing, 016/017/018/025)*: What this cascade acts on — read and transitioned to
  cancelled, never created or otherwise modified by this feature.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of a de-verified clinic's or revoked doctor's not-yet-occurred bookings are
  cancelled automatically, with no manual staff action required.
- **SC-002**: 100% of past/completed bookings for a de-verified clinic or revoked doctor are left
  entirely untouched.
- **SC-003**: 100% of cancelled fixed-time bookings with an eligible waiting entry produce exactly
  one waitlist offer, identical to an individually-cancelled booking.
- **SC-004**: 100% of cancelled bookings with a linked Patient Account produce exactly one
  cancellation notification event.
- **SC-005**: A routine, automatic license-number-edit-triggered verification reset (006) never
  produces a cascade — 0% false-positive cascade triggers from that path.

## Assumptions

- "Not-yet-occurred" is defined structurally, not by comparing calendar dates: a Booking still
  qualifies once its Slot's status is anything other than `BOOKED` (i.e., `NO_SHOW` or
  `COMPLETED` already resolved it, or it was already cancelled) — reusing this codebase's own
  existing Slot-lifecycle state rather than re-deriving a separate date/time boundary condition
  that 021's no-show sweep and 026's completion tracking already own.
- Queue-mode bookings cancelled by this cascade use a direct cancellation path mirroring 029/030's
  own established pattern for non-025 bulk cancellations (release the Slot, notify the linked
  Patient Account, no `BookingCancelledEvent`/waitlist-bump) — `BookingCancellationService` (025)
  itself is fixed-time-only and cannot be reused as-is for Queue-mode. Fixed-time bookings use
  025's actual cancellation service directly, preserving the real waitlist-bump trigger.
- The explicit doctor-license-revoke action (FR-002) needs no request body or reason field — the
  source material states no such requirement, unlike 020's walk-in override (which had an explicit,
  stated audit-trail need); adding one without a stated requirement would violate Constitution II.
- `com.cms.identity.admin.ClinicDeVerifiedEvent` (003) is consumed as-is for Trigger 1 — this
  feature adds its own listener, the first one it's ever had, exactly as that event's own
  documentation already anticipated. Trigger 2 needs an analogous new event, published only on a
  genuine verified→unverified transition via the new revoke action, mirroring `unverify`'s
  existing idempotent shape exactly.
