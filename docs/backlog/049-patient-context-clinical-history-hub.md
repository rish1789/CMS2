# 049 — Patient Context & Clinical History Hub

**Module:** Frontend / Staff Experience
**Status:** Ready for spec-kit intake

## User Story
As clinic staff treating a patient, I want to see that patient's booking history, consultation notes, prescriptions, and external record references in one coherent place, so that I don't have to navigate through separate, disconnected pages to understand their history with the clinic.

## Context
CMS2 already has real, converged clinical data per patient — Consultation Notes (030), Prescriptions (031), External Record References (032), and Bookings (016/017/018/020) — but verified current state (2026-09-15) shows these are reached through separate, per-action, per-booking routes (`bookings/:id/consultation-note`, `bookings/:id/prescription`, `bookings/:id/external-record`, `patients/:id/anonymize`), not a unified patient view. `PatientContextHeader.tsx` and `BookingContextHeader.tsx` already exist as shared header components used across some of these pages, suggesting a "show patient context" pattern is already partially established and can be extended rather than invented from scratch.

This feature must NOT invent new clinical data types. It aggregates what already exists (030/031/032/016-020) into one navigable view. It must also account for the DPDP anonymization lifecycle (033/034) — an anonymized patient's identifying fields (`name`, `phone`) are scrubbed, and this hub must display that state accurately, not silently show stale cached identifying data.

## Business Rules
- The hub MUST be organized as tabs or sections covering only what's real: Overview (patient identity + booking summary), Bookings (past/upcoming, reusing 016-025's existing booking data), Consultations (030's notes, reusing its existing treating-doctor-only authorization), Prescriptions (031's items), External Records (032's references) — NOT "Medical Information," "Lab Tests," or "Billing" tabs, since none of that data exists.
- Consultation Notes and Prescriptions are write-once/immutable per constitution Principle IV — this hub is a read/navigation surface; it MUST NOT introduce any edit/update UI for these records. A "create new" action (for a new visit) is fine and already exists per 030/031's own contract; an edit action on an existing record must never be added.
- The hub MUST reuse existing authorization: Consultation Note viewing is already treating-doctor-only (030) via `TreatingDoctorAuthorizationService` (031's extraction) — this feature consumes that existing check, it does not relax or duplicate it with different logic.
- If the patient has been anonymized (033), the hub MUST display the current (scrubbed) state accurately — no code path should read or cache a pre-anonymization name/phone value.
- "Important actions should be obvious" (book appointment, start consultation, add prescription) — these already have real destinations per the existing route table (booking flows, `bookings/:id/consultation-note`, etc.); this feature makes them reachable as contextual actions from the patient hub, it does not reimplement them.
- Patients are clinic-scoped (per the constitution's multi-tenancy rule) — this hub, reached from within `ClinicShell`, must only ever show data for the current clinic's relationship with that patient, never cross-clinic data (a `PatientAccount` may exist across clinics per 039, but clinic-scoped `Patient` records per 019 do not span clinics).

## Acceptance Criteria
- Given a patient with existing bookings, consultation notes, prescriptions, and external records at a clinic, when staff navigate to that patient's hub, then all four categories are visible and correctly attributed, with no data leaking from a different clinic's relationship with the same `PatientAccount`.
- Given a Consultation Note or Prescription shown in the hub, when inspected, then no UI control exists anywhere to edit or delete it — only to view it or create a new one tied to a new visit.
- Given an anonymized patient (033), when their hub is viewed, then displayed identity fields reflect the current scrubbed state, not a stale pre-anonymization value.
- Given a doctor who is not the treating doctor for a given consultation note, when they view a patient's hub, then that specific note is not shown to them (existing 030 authorization is preserved, not bypassed by this new aggregation view).
- Given the full test suite, when run after this feature, then all existing tests for 030/031/032/033 pass unmodified (proving no regression to their authorization/immutability guarantees), plus new tests for the hub's own aggregation/display logic.

## Dependencies
- Depends on 046 (shared UI component library) and ideally 047 (sidebar nav, so the hub is reachable from persistent navigation).
- Reads from existing converged features: 019 (Patient record), 016-025 (bookings/cancellations), 030 (consultation notes), 031 (prescriptions), 032 (external records), 033 (anonymization).

## Explicitly Out of Scope
- Any "Medical Information," "Lab Tests," or "Billing" section — none of this data exists in CMS2 and building it is out of scope for this transformation per the user's own decision this session.
- A cross-clinic, single global patient medical record — explicitly out of scope system-wide (`backlog/README.md`); this hub stays clinic-scoped.
- Any edit/update capability for Consultation Notes or Prescriptions — constitution Principle IV forbids this permanently, not just for this feature.
- A patient-facing equivalent of this hub — this feature is staff-side only; the patient's own view of their history (via `MyBookings.tsx` etc.) already exists separately and is not modified here.

## Source References
- `.specify/memory/constitution.md` Principle IV (write-once clinical documentation, multi-tenant scoping)
- `backlog/030-consultation-note-creation.md`, `031-prescription-and-items-creation.md`, `032-external-record-reference.md`, `033-patient-immediate-anonymization.md`
- Verified against current repository state via direct inspection, 2026-09-15 (`PatientContextHeader.tsx`/`BookingContextHeader.tsx` confirmed to exist as partial precedent; no unified patient hub page currently exists)
