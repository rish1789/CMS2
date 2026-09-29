# 033 — Patient Immediate Anonymization

**Module:** DPDP Compliance
**Status:** Ready for spec-kit intake

## User Story
As a ClinicAdmin (or authorized staff member), I want to immediately anonymize a patient's identifying information on request, so that the clinic can honor a data-deletion/erasure request under DPDP without losing the underlying booking/clinical history structure.

## Context
This is the first of DPDP's two-tier deletion model — an on-demand, immediate action (as opposed to feature 034's automatic monthly sweep). It replaces identifying data with a placeholder rather than deleting the patient record outright, because bookings and clinical content still need to reference *a* patient record structurally. BDD §2, §3.6.

## Business Rules
- Anonymization replaces the patient's name with a placeholder value and clears **all other identifying/contact/safety-form fields** (e.g. phone number, address, emergency contact, any safety/allergy-form free text — anything identifying or contact-related).
- Anonymization is **blocked if any active future booking exists** for that patient — the action must be refused (not silently skipped or queued) while a future booking is still active.
- Anonymization acts on the clinic-scoped **Patient** record (not the global Patient Account) — consistent with the system's clinic-scoped clinical/booking model (see 019, 039).
- Anonymization does not delete the Patient record itself or any bookings/clinical content — it only scrubs identifying fields on the Patient record. Deletion of clinical *content* is handled separately by the monthly purge (034), which additionally requires the patient to already be anonymized.
- This action is presumably staff-initiated (e.g. by a ClinicAdmin) in response to a patient's request — the source document does not describe a patient self-service trigger for this, only the effect and its precondition.

## Acceptance Criteria
- Given a Patient record with no active future bookings, when a ClinicAdmin triggers anonymization for that patient, then the patient's name is replaced with a placeholder and all other identifying/contact/safety-form fields are cleared, and the change is immediate (not queued).
- Given a Patient record that has at least one active future booking, when a ClinicAdmin attempts to trigger anonymization, then the system rejects the action and does not modify any fields.
- Given a patient has been anonymized, when their historical bookings and clinical documentation (Consultation Notes, Prescriptions, External Record References) are viewed, then those records remain intact and still reference the (now-anonymized) Patient record — only the identifying fields on the Patient record itself are affected, not clinical content.
- Given a patient has been anonymized, when the monthly retention purge (034) later runs, then this patient's already-anonymized status is a precondition it checks for.
- Given a patient's future booking is cancelled after an anonymization attempt was blocked, when anonymization is retried, then it now succeeds (no more active future bookings).

## Dependencies
- Depends on: 025-individual-booking-cancellation-waitlist-trigger, 026-whole-day-session-cancellation, 027-partial-cutoff-session-cancellation — anonymization's precondition check needs an accurate notion of "active future booking," which these cancellation features affect.
- Blocks / feeds into: 034-monthly-retention-purge — the purge requires the patient to already be anonymized before it will delete clinical content for their bookings.
- Related: 019-patient-record-auto-creation-phone-linking — operates on the same clinic-scoped Patient entity.

## Explicitly Out of Scope
- Deleting the Patient record, booking records, or clinical documentation outright — anonymization only scrubs identifying fields; content deletion is handled by 034 on a separate schedule/precondition.
- A patient self-service "delete my data" endpoint — the source document describes only the effect and precondition, not who/how it's triggered; treat as staff-initiated unless clarified otherwise during spec-kit intake.
- Any cross-clinic anonymization — this acts on one clinic-scoped Patient record at a time, consistent with the system's clinic-scoped patient model (no single global medical record).

## Source References
- BDD §2 (in-scope: "DPDP two-tier deletion: immediate anonymization + monthly automatic purge...")
- BDD §3.6 (DPDP Compliance)
