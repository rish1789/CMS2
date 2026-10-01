# Feature Specification: Duplicate Patient Phone in Staff Booking

**Feature Branch**: `claude/074-duplicate-phone`

**Created**: 2026-10-01

**Status**: Draft

**Input**: Phase 2B of `docs/NEXT_PHASES_ACTION_PLAN.md`; defects PB-001 and PB-002 in `docs/product-audit/07-BUG-AND-DEFECT-REGISTER.md` (audit item A-05).

## Context

At one clinic, two **unlinked** patient records (walk-ins with no patient account) may not share a phone number. The database enforces this with `uq_patient_clinic_phone_unlinked` (V5, 009 FR-005b).

Three staff paths can create a new unlinked patient, and none of them checks that rule first:

| Path | Endpoint | What happens today on a same-clinic phone collision |
|---|---|---|
| Staff fixed-time booking (020) | `POST /clinics/{id}/slots/{slotId}/book` | The patient INSERT flushes inside the slot-race handler, so the caller gets **409 `SLOT_ALREADY_BOOKED`**, which is wrong (PB-001). |
| Staff queue booking (022) | `POST /clinics/{id}/sessions/{sessionId}/queue-bookings` | Unhandled **500** (PB-002). |
| Front-desk walk-in (063) | `POST /clinics/{id}/walk-ins` | Unhandled **500** (PB-002). |

In every case staff learn nothing useful. They may retry other slots, or give up at the last step of the form.

## Decision: the conflict contract

The plan requires an explicit, agreed response. It also forbids silently merging identities on phone alone. This spec proposes the following; the PR records it for the owner to confirm.

- **Response:** `409 Conflict` with `error: "PATIENT_PHONE_ALREADY_REGISTERED"`, a human message, and `existingPatient: { id, name }` naming the clinic's unlinked patient with that phone **when it is known**.
  - Staff at this clinic can already find that record with patient search by phone (044), so naming it here exposes nothing new.
  - It names a patient only at the caller's own clinic.
- **Race case:** when two requests race and the loser hits the database constraint, `existingPatient` is `null`. The transaction is already aborted, and re-querying inside it is not allowed (066's lesson). The message still tells staff to search for the patient.
- **Recovery:** staff choose. They either book the existing patient (an explicit "Use this patient" action, sending `patientId` as today) or correct the phone. The server never links or merges by itself.
- **No partial writes:** a refused request leaves no patient, booking, slot or token, and no inbox item.
- **Unchanged:**
  - a linked patient (with a patient account) never conflicts, matching the index;
  - other clinics are independent;
  - a walk-in with no phone never conflicts;
  - 066 and 067 locking.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Staff see the real reason and can recover (Priority: P1)

**Acceptance Scenarios** (each for fixed-time booking, queue booking and walk-in):

1. **Given** clinic A has an unlinked patient "Asha" with phone 9876543210, **When** staff register a *new* patient with that phone, **Then** the response is 409 `PATIENT_PHONE_ALREADY_REGISTERED` naming Asha. No patient, booking, slot, token or inbox item is written, and the timed slot stays open.
2. **Given** that 409 in the form, **Then** the entered values remain, a message explains the conflict, and a **Use this patient** action switches to booking the existing patient.
3. **Given** the same phone exists only at clinic B, **When** staff at clinic A register it, **Then** booking succeeds.
4. **Given** the phone belongs to a *linked* patient at clinic A, **When** staff register a new unlinked patient with it, **Then** booking succeeds, as today, because the index covers only unlinked records.

### User Story 2 - Concurrent registrations keep the rule (Priority: P1)

**Acceptance Scenario**: **Given** two staff register the same new phone at the same clinic at the same moment, **Then** exactly one succeeds and the other gets 409 `PATIENT_PHONE_ALREADY_REGISTERED`. Only one patient with that phone exists, and the refused request leaves no booking, slot or token.

## Requirements *(mandatory)*

- **FR-001**: Before creating an unlinked patient, the three staff paths MUST check for an unlinked patient with the same clinic and phone. If one exists, they MUST refuse with the contract above, naming it.
- **FR-002**: The new-patient INSERT MUST be flushed immediately. A violation of `uq_patient_clinic_phone_unlinked` MUST map to the same 409 (with `existingPatient: null`) and MUST NOT be reported as `SLOT_ALREADY_BOOKED` or a 500. There MUST be no re-query after the failed INSERT.
- **FR-003**: Any other integrity violation MUST keep its current behaviour. In particular, the fixed-time slot race still reports `SLOT_ALREADY_BOOKED`.
- **FR-004**: A refused request MUST leave no persisted patient, booking, slot, token or inbox item.
- **FR-005**: The three staff forms MUST keep the entered values, show the conflict, and offer **Use this patient** when `existingPatient` is present. Otherwise they MUST suggest searching for the patient.
- **FR-006**: No schema change. The database index stays the backstop.

## Success Criteria *(mandatory)*

- **SC-001**: A same-clinic phone collision returns `SLOT_ALREADY_BOOKED` or 500 0 times across the three paths.
- **SC-002**: Concurrent duplicate registrations create 0 extra patients and 0 orphan bookings or tokens.
