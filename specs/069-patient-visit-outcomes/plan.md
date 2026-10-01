# Implementation Plan: Patient Visit Outcomes and Valid Actions

**Branch**: `claude/069-patient-visit-outcomes` | **Date**: 2026-10-01 | **Spec**: [spec.md](spec.md)

## Summary

A small, read-only derivation sits in the `booking` module and feeds three patient endpoints and three patient screens. Nothing is stored and no migration is needed. The cancel endpoint reuses the same policy class, so the displayed eligibility and the enforced rules cannot drift apart.

## Technical Context

- **Stack:** Java 21, Spring Boot 4.1, React 18 + TypeScript, Vitest, Testcontainers Postgres 16.
- **Clock:** `SessionAvailabilityService.now()`. It is the existing injectable clock (065 FR-014) and runs in IST (#29).
- **Constraints:** additive response fields only; the cancel endpoint's behaviour must not change.

## Constitution Check

| Principle | Status |
|---|---|
| I. Test-first | Each behaviour gets a failing test first: unit tests for the derivation with a fixed clock, integration tests for the endpoints, Vitest for the screens. |
| II. Simplicity / YAGNI | One component (`PatientVisitOutcomes`) and three response fields. No visit entity and no new table. |
| III. Module boundaries | All in `booking`, which already reads `scheduling` slots and sessions in the same direction. |
| IV. Data-layer races | No new writes. The cancel race guard (`cancelIfActive`) is untouched. |
| Tenant / ownership | Every patient read is ownership-scoped. The new detail endpoint returns 404 for non-owners. |

## Design

**Backend (`com.cms.booking`):**
- `domain/VisitOutcome` (enum): SCHEDULED, CHECKED_IN, COMPLETED, NO_SHOW, CANCELLED, NOT_RECORDED.
- `domain/PatientCancellationRefusal` (enum): ALREADY_CANCELLED, VISIT_RESOLVED, QUEUE_BOOKING, WALK_IN, CUTOFF_PASSED.
- `service/PatientVisitOutcomes` (@Component):
  - `outcome(Booking)`
  - `eligibility(Booking)`: the display order from the spec table.
  - `requestRefusal(Slot)`: the cancel endpoint's existing order (queue, then walk-in, then cutoff). It also owns `CUTOFF_HOURS = 2`.
  - Time comes from `SessionAvailabilityService.now()`. A package-visible constructor takes a fixed `Supplier<LocalDateTime>` for unit tests.
- `dto/PatientBookingSummaryResponse`: adds `visitOutcome` and `cancellation`, with a new factory `of(Booking, VisitOutcome, CancellationEligibility)`.
- `PatientMyBookingsController`: maps each booking through `PatientVisitOutcomes`. It adds `GET /api/v1/patients/bookings/{bookingId}`, owner-only, otherwise `BookingNotFoundException` (404).
- `PatientBookingCancellationController`: replaces its inline queue, walk-in and cutoff checks with `requestRefusal`, mapped to the same exceptions in the same order.
- `PatientSessionLiveStatusController` and `PatientSessionLiveStatusResponse`: add `visitOutcome`. For a terminal outcome, the text comes from the outcome.

**Frontend:**
- `features/patient-bookings/api.ts`: types for `visitOutcome` and `cancellation`, and `getMyBooking(id)`.
- `features/patient-bookings/visitOutcome.ts`: the shared label map and `isUpcoming`.
- `NextAppointmentCard`: selects from `isUpcoming`, with no browser-date filtering.
- `MyBookings`: the outcome badge replaces Active/Cancelled.
- `PatientBookingDetailPage`: fetches the booking, shows an outcome header, and renders `CancelBookingButton` only if allowed. Otherwise it shows the reason text.

## Project Structure

```text
backend/src/main/java/com/cms/booking/{domain,service,dto,api}/…   (see Design)
backend/src/test/java/com/cms/booking/unit/PatientVisitOutcomesTest.java
backend/src/test/java/com/cms/booking/integration/PatientBookingOutcomeTest.java
frontend/src/features/patient-bookings/{api.ts,visitOutcome.ts,NextAppointmentCard.tsx,MyBookings.tsx}
frontend/src/routes/patient/PatientPages.tsx (detail page)
frontend/tests/patient-bookings/*.test.tsx, tests/patient-bookings/PatientBookingDetail.test.tsx
```

## Complexity Tracking

None.
