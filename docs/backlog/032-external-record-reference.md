# 032 — External Record Reference

**Module:** Clinical Documentation
**Status:** Ready for spec-kit intake

## User Story
As a Doctor, I want to record a typed summary reference to an external clinical record (e.g. a lab result, an imaging report, a prior diagnosis from another provider) for a patient I personally treated in a specific booking, so that relevant outside context is captured without requiring any file handling.

## Context
External Record References are the third of the three lightweight clinical documentation types (alongside Consultation Notes, 030, and Prescriptions, 031). The system deliberately never accepts file/document uploads anywhere — this feature exists specifically to let a doctor note that an external record exists and summarize it in structured, typed fields, rather than attaching the actual document. BDD §2, §3.5, §7 (Doctor persona).

## Business Rules
- External Record References are **typed summaries only — never file uploads**. There is no attachment/upload field anywhere on this entity.
- Like Consultation Notes and Prescriptions, an External Record Reference is **strictly immutable once created** — no edit function; a correction requires a fresh reference entry, not a modification of the original.
- Only the **treating doctor for that specific booking** may create an External Record Reference for it, traced via the booking's slot's assigned doctor — no clinic-ownership override.
- An External Record Reference belongs to exactly one booking; a booking may have zero or more, each independently created and immutable.

## Acceptance Criteria
- Given a booking whose slot is assigned to Dr. A, when Dr. A creates an External Record Reference with typed fields (e.g. record type, source/provider name, date, summary text) for that booking, then it is saved and immediately immutable.
- Given an External Record Reference creation form, when a doctor attempts to attach a file (image, PDF, scan), then no such field/capability exists — only structured typed fields are accepted.
- Given an External Record Reference already exists for a booking, when anyone (including the original author) attempts to edit or delete it, then the system rejects the action.
- Given a booking whose slot is assigned to Dr. A, when Dr. B attempts to create an External Record Reference for that booking, then the system rejects the action.
- Given a ClinicAdmin or Operations staff member, when they attempt to create an External Record Reference for any booking, then the system rejects the action — authorship is restricted to the treating Doctor only.

## Dependencies
- Depends on: 016-staff-assisted-fixed-time-booking, 017-patient-self-service-fixed-time-booking, 018-queue-token-booking — an External Record Reference always attaches to an existing booking.
- Depends on: 005-doctor-profile-auto-creation-license-queue — authoring doctor traceability.
- Related: 030-consultation-note-creation, 031-prescription-and-items-creation — shared immutability and treating-doctor-only authorship pattern.
- Feeds into: 033-patient-immediate-anonymization, 034-monthly-retention-purge — this content is clinical content subject to DPDP anonymization and retention purge.

## Explicitly Out of Scope
- File/document upload of any kind (scans, PDFs, images) for external records — this feature is explicitly typed-summary-only, by design, system-wide (no upload capability exists anywhere in the system).
- Any edit, versioning, or amendment capability once created.
- Authorship by any role other than the treating Doctor.
- Integration with external lab/imaging systems to auto-populate the reference — this is a manually-typed summary only.

## Source References
- BDD §2 (in-scope: "External Record References (typed summaries only, never file uploads)")
- BDD §2 (out-of-scope: "Any file/document upload — external records are typed summaries only")
- BDD §3.5 (Clinical Documentation)
- BDD §7 Persona table (Doctor row)
