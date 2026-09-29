# Implementation Plan: Self-Service Waitlist Claim

**Branch**: `032-self-service-waitlist-claim` | **Date**: 2026-09-04 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/032-self-service-waitlist-claim/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

Let a patient claim or decline their own `OFFERED` waitlist entry (028) directly, and have the
system automatically detect a lapsed 30-minute window and move on to the next-longest-waiting
eligible entry on its own — closing the "claiming happens by phone" gap the source material
explicitly calls out. Technical approach: extend 028's existing `com.cms.waitlist` module (no new
module) with a claim/decline action pair reusing 021's existing `PatientBookingService.bookSlot`
for the actual booking-creation step (never reinventing fee resolution or patient linking), a
shared "release and re-offer" core reused by both decline and a real `@Scheduled` expiry sweep
(mirroring 023's `NoShowDetectionService`/`NoShowDetectionTrigger` split), and a new
`offeredSlotId` field on `WaitlistEntry` recording which specific Slot an offer refers to — 028
itself never needed to remember that past the moment of notifying.

## Technical Context

**Language/Version**: Java 21 (backend), TypeScript/React (frontend) — matches every prior feature this session.

**Primary Dependencies**: Spring Boot, Spring Data JPA, Spring Security (patient JWT chain), Spring's `@Scheduled` task support (already in production use via 023's `NoShowDetectionTrigger`); React + Tailwind CSS on the frontend.

**Storage**: PostgreSQL via a new Flyway migration (`V18`) — adds `waitlist_entry.offered_slot_id`.

**Testing**: JUnit 5 + Spring Boot Test (`@WebMvcTest`/`@SpringBootTest` + Testcontainers) on the backend; Vitest/React Testing Library on the frontend — mirrors every prior feature.

**Target Platform**: Linux server (Spring Boot backend), browser (React frontend).

**Project Type**: Web application (`backend/` + `frontend/`, existing structure).

**Performance Goals**: No new performance target — the expiry sweep processes a small, per-clinic set of currently-lapsed offers, mirroring 023's own untimed sweep.

**Constraints**: A claim MUST re-verify the offered Slot is still open at the moment of claim (spec Clarifications) — never assume it's still available just because the offer exists. Exactly one of claim/decline/expiry ever succeeds per entry (FR-010, data-layer race-closure).

**Scale/Scope**: Two new patient-facing actions (claim, decline) on an existing entity, one new scheduled sweep, one new migration column, no new module.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. Test-First Development**: PASS. Tasks will include failing tests (unit + Testcontainers
  integration) for a successful claim, an ownership-violation claim, a claim against a lapsed/
  non-`OFFERED` entry, a claim that loses a race to an ordinary booking of the same slot, a
  decline that re-offers the next eligible entry, a decline with nothing left to offer, the
  expiry sweep's own detection and re-offer behavior, and a concurrent claim/decline/expiry race
  on the same entry.
- **II. Simplicity & YAGNI**: PASS. No new module; reuses `PatientBookingService.bookSlot` for
  booking creation and `WaitlistMatchingService.matchAndOffer` for re-matching rather than
  reimplementing either; decline and expiry share one core release method instead of two parallel
  implementations; no atomic-reschedule-style special-casing, no new notification mechanism
  beyond calling the existing `NotificationEventService.publish`.
- **III. Modular, Library-First Architecture**: PASS. `com.cms.waitlist` already depends on
  `com.cms.booking` as of 028 (consuming `BookingCancelledEvent`); reusing
  `PatientBookingService`/`BookingRepository`/booking exceptions from that same module is a
  natural extension of an already-established dependency direction, not a new one. No reach-through
  into `com.cms.notification`'s or `com.cms.scheduling`'s internals beyond what 028 already does.
- **IV. Data Privacy & Integrity by Design**: PASS. Claim, decline, and the expiry sweep all use
  data-layer-guarded conditional updates (`WaitlistEntryRepository.claimIfOffered`/
  `expireIfOffered`, mirroring 028's own convergence-fixed `offerIfWaiting`) — exactly one
  transition away from `OFFERED` per entry is ever durable (FR-010/SC-004), never a plain
  read-then-write.

No violations — Complexity Tracking table not needed.

**Post-Phase-1 re-check**: PASS, unchanged. Phase 1 design (data-model.md, contracts/,
quickstart.md) introduced nothing beyond what this gate already evaluated.

## Project Structure

### Documentation (this feature)

```text
specs/[###-feature]/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
backend/
├── src/main/java/com/cms/waitlist/
│   ├── WaitlistEntry.java                              # extend: + offeredSlot field
│   ├── WaitlistEntryStatus.java                        # extend: + CLAIMED, EXPIRED
│   ├── WaitlistEntryRepository.java                    # extend: + claimIfOffered, expireIfOffered, findByStatusAndOfferExpiresAtBefore
│   ├── WaitlistMatchingService.java                    # extend: also persists offeredSlot on a new match
│   ├── WaitlistClaimService.java                       # new: claim + decline (shared entry-lookup/ownership)
│   ├── WaitlistReleaseService.java                     # new: shared decline/expiry core ("release and re-offer")
│   ├── WaitlistExpirySweepService.java                 # new: finds lapsed OFFERED entries
│   ├── WaitlistExpirySweepTrigger.java                 # new: @Scheduled, mirrors NoShowDetectionTrigger
│   ├── WaitlistEntryNotFoundException.java             # new
│   ├── WaitlistOfferNotClaimableException.java         # new
│   ├── PatientWaitlistClaimController.java             # new: claim + decline endpoints
│   ├── WaitlistExceptionHandler.java                   # extend: + 2 new mappings
│   └── dto/
│       └── ClaimWaitlistRequest.java                   # new
├── src/main/resources/db/migration/
│   └── V18__waitlist_entry_offered_slot.sql             # new
└── src/test/java/com/cms/waitlist/integration/          # extend existing package
    └── (new test classes)

frontend/
├── src/features/waitlist/
│   └── api.ts                                          # extend: + claimOffer, declineOffer
│   └── ClaimOfferCard.tsx                              # new
└── tests/waitlist/
```

**Structure Decision**: Extends 028's existing `com.cms.waitlist` module in place — no new
module, no new `SecurityConfig` chain. The two new endpoints (`POST
/api/v1/patients/waitlist-entries/{entryId}/claim` and `.../decline`) fall under the existing
`/api/v1/patients/**` chain and are ownership-scoped (not clinic-path-scoped), mirroring 027/028's
own `/api/v1/patients/bookings/{bookingId}/...` precedent rather than 028's own clinic-nested
join endpoint — the access boundary here is "do you own this waitlist entry," not clinic
membership.

## Complexity Tracking

*No violations — this section is not applicable.*
