# 015 — Fee Resolution & Locking at Booking Time

**Module:** Booking
**Status:** Ready for spec-kit intake

## User Story
As a patient or staff member creating a booking, I want the system to resolve the correct fee at the moment of booking and lock it permanently to that booking, so that the amount charged is predictable and never silently changes due to later fee updates.

## Context
Every clinic sets fees, potentially overridden per appointment type. BDD §3.2 specifies a strict resolution order and, notably, a hard block rather than a silent default when no fee can be resolved — this was previously unspecified and is now a confirmed hard rule.

## Business Rules
- Fee resolution order: (1) appointment-type fee override, if one exists for the booking's appointment type; (2) otherwise the doctor's default fee; (3) if neither exists, the booking is BLOCKED — it cannot be created.
- The resolved fee is snapshotted onto the booking at creation time (fee snapshotting).
- A later change to either the appointment-type override or the doctor's default fee never re-prices an already-made booking — the booking keeps its originally locked fee forever.
- Online payment collection is out of scope for v1: `paymentStatus` on a booking is a manual staff-flipped flag (e.g. PENDING/PAID) only — no payment gateway, refund, or reconciliation logic is part of this feature.
- Insurance, sliding-scale pricing, and discounting are out of scope for v1 — every resolved fee is a flat cash amount with no adjustment mechanism.

## Acceptance Criteria
- Given an appointment type with a fee override configured, when a booking is made for that appointment type, then the booking's locked fee equals the override amount.
- Given an appointment type with no override but the doctor has a default fee, when a booking is made, then the booking's locked fee equals the doctor's default fee.
- Given an appointment type with no override and the doctor has no default fee configured, when a booking is attempted, then the booking is rejected/blocked with a clear reason, and no booking record is created.
- Given a booking was created with a locked fee of ₹500, when the doctor's default fee is later changed to ₹700, then the existing booking's fee remains ₹500.
- Given a completed booking, when staff view it, then `paymentStatus` can be manually toggled (e.g. PENDING → PAID) with no gateway integration involved.

## Dependencies
- Depends on: 009-recurring-schedule-definition (appointment type / doctor default fee configuration lives alongside schedule setup, if modeled there) — or wherever Doctor default fee and Appointment Type entities are configured.
- Blocks / feeds into: 016-staff-assisted-fixed-time-booking, 017-patient-self-service-fixed-time-booking, 018-queue-token-booking — all booking creation flows must run this resolution/locking logic.

## Explicitly Out of Scope
- Online payment gateway integration, refunds, reconciliation — `paymentStatus` stays a manual staff-flipped flag for v1 (confirmed v1 scope decision).
- Insurance, sliding-scale pricing, discount codes — every fee is a flat cash amount for v1 (confirmed v1 scope decision).

## Source References
- BDD §2 (Out-of-Scope: online payment collection; insurance/discounting)
- BDD §3.2 (fee resolution order, hard block, fee locking)
