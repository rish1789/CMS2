# 031 — Prescription + Items Creation

**Module:** Clinical Documentation
**Status:** Ready for spec-kit intake

## User Story
As a Doctor, I want to record a prescription with its line-item medications for a patient I personally treated in a specific booking, so that there is a lightweight, permanent clinical record of what was prescribed during that visit.

## Context
Clinical documentation in this system is intentionally lightweight — no file uploads, no editing, no complex EHR workflow. A Prescription is one of three documentation types (alongside Consultation Notes, feature 030, and External Record References, feature 032) that a doctor can attach to a completed booking. BDD §2 (in-scope), §3.5, §7 persona table (Doctor row).

## Business Rules
- A Prescription (with its Items — the individual medications/instructions) is **strictly immutable once created** — there is no edit function anywhere in the system.
- A correction to a prescribing error requires a fresh Prescription on a new visit/booking — never an edit to the original. This is stricter than "one per booking"; it is write-once, permanently.
- Only the **treating doctor for that specific booking** may create a Prescription for it. "Treating doctor" is traced by following the booking back to its slot's assigned doctor — there is no clinic-ownership override (a ClinicAdmin or another doctor at the same clinic cannot write it on the treating doctor's behalf).
- A Prescription belongs to exactly one booking; a booking may have zero or more Prescriptions (each immutable and independently created), consistent with Consultation Notes (030).
- No file/document upload capability exists for prescriptions — all content is structured, typed fields (medication name, dosage, frequency, duration, instructions, etc. as typed items), never an attached file.

## Acceptance Criteria
- Given a booking whose slot is assigned to Dr. A, when Dr. A creates a Prescription with one or more Items for that booking, then the Prescription and its Items are saved and immediately immutable.
- Given a Prescription already exists for a booking, when anyone attempts to edit or delete it (including the original prescribing doctor), then the system rejects the action — no update/delete endpoint exists for Prescriptions or their Items.
- Given a booking whose slot is assigned to Dr. A, when Dr. B (a different doctor, even at the same clinic) attempts to create a Prescription for that booking, then the system rejects the action.
- Given a ClinicAdmin or Operations staff member, when they attempt to create, edit, or delete a Prescription for any booking, then the system rejects the action — Prescription authorship is restricted to Doctors only, and only for their own treated bookings.
- Given Dr. A wants to correct a mistake in a previously written Prescription, when they act on it, then the only available path is creating a new Prescription (typically on a new booking/visit) — not editing the original.
- Given a Prescription Item is being created, when the doctor submits it, then only structured/typed fields are accepted — no file attachment field exists on the Item.

## Dependencies
- Depends on: 016-staff-assisted-fixed-time-booking, 017-patient-self-service-fixed-time-booking, 018-queue-token-booking — a Prescription always attaches to an existing booking.
- Depends on: 005-doctor-profile-auto-creation-license-queue — the authoring doctor must be traceable to the booking's assigned doctor via their Doctor Profile.
- Related: 030-consultation-note-creation, 032-external-record-reference — same immutability and treating-doctor-only authorship pattern; consider building all three documentation types on a shared authorization/immutability mechanism.
- Feeds into: 033-patient-immediate-anonymization, 034-monthly-retention-purge — Prescription content is clinical content subject to DPDP anonymization and retention purge.

## Explicitly Out of Scope
- Any edit, versioning, or amendment capability for an existing Prescription or its Items.
- File/document upload for prescriptions (e.g. scanned/attached prescription images) — content is typed data only.
- Prescription authorship by any role other than the treating Doctor, including ClinicAdmin override "on behalf of" a doctor.
- Digital signature, e-prescription regulatory formats, or pharmacy integration — not addressed by the source document.

## Source References
- BDD §2 (in-scope: "Lightweight, immutable clinical documentation: Consultation Note, Prescription + Items, External Record References")
- BDD §3.5 (Clinical Documentation)
- BDD §7 Persona table (Doctor row: "only role permitted to write clinical documentation... no clinic-ownership override")
