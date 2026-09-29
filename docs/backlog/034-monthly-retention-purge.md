# 034 — Monthly Automatic Retention Purge

**Module:** DPDP Compliance
**Status:** Ready for spec-kit intake

## User Story
As the System (Scheduler), I want to automatically and permanently delete clinical-record content whose retention period has expired for already-anonymized patients, so that the clinic doesn't retain personal clinical data beyond its legally/policy-driven retention window.

## Context
This is the second tier of DPDP compliance (alongside 033's immediate anonymization). It is a background job, not a user-triggered action — the System (Scheduler) is treated as a first-class actor per the persona table. Only the Super Admin role can manually re-trigger background jobs. BDD §2, §3.6, §7 persona table.

## Business Rules
- Retention window is **fixed at 3 years, as a hardcoded system-wide constant** — it is NOT clinic-configurable in v1 (resolved product decision; the source doc flagged this as open, this build resolves it to a fixed constant).
- The retention date is tracked **per booking** (each booking's own clinical content has its own 3-year clock, not a single clinic-wide or patient-wide date).
- The monthly sweep permanently deletes clinical-record **content** (Consultation Notes, Prescriptions + Items, External Record References) for a booking only when BOTH conditions hold:
  1. That booking's individual 3-year retention date has passed, AND
  2. The patient was already anonymized (see 033) — if the patient has not been anonymized, the content is retained regardless of age.
- The **booking record itself and the anonymized Patient shell are never purged** — only the clinical content (notes/prescriptions/external records) attached to the booking is permanently deleted. The booking's existence (date, doctor, slot, etc.) persists as a shell for reporting/audit purposes.
- This runs on a monthly schedule as an automatic background job.
- Super Admin is the only role that can manually re-trigger this (or other) background jobs — e.g. for testing or catching up a missed run.

## Acceptance Criteria
- Given a booking whose clinical content is 3+ years old and whose patient has been anonymized, when the monthly purge job runs, then the Consultation Notes, Prescriptions/Items, and External Record References attached to that booking are permanently deleted, while the booking record and the anonymized Patient shell remain.
- Given a booking whose clinical content is 3+ years old but whose patient has NOT been anonymized, when the monthly purge job runs, then that booking's clinical content is left untouched (both conditions must hold).
- Given a booking whose patient is anonymized but whose clinical content is less than 3 years old, when the monthly purge job runs, then that booking's clinical content is left untouched.
- Given the monthly job has already run this month, when a Super Admin manually re-triggers it, then it runs again (e.g. to catch newly-eligible bookings or recover from a missed scheduled run) — no other role can trigger it.
- Given a non-Super-Admin role (ClinicAdmin, Doctor, Operations) attempts to manually trigger the purge job, then the system rejects the action.
- Given the purge has deleted a booking's clinical content, when that booking is later viewed, then no Consultation Note, Prescription, or External Record Reference data is retrievable for it, but the booking's non-clinical metadata (date, slot, doctor) is still visible.

## Dependencies
- Depends on: 033-patient-immediate-anonymization — purge eligibility requires the patient to already be anonymized.
- Depends on: 030-consultation-note-creation, 031-prescription-and-items-creation, 032-external-record-reference — these are the content types being purged.
- Related: all booking features (015-018) — the booking record itself is explicitly preserved (not purged), only its attached clinical content is.

## Explicitly Out of Scope
- Clinic-configurable retention windows — the 3-year window is a fixed, hardcoded system constant for v1, not a per-clinic setting.
- Deleting the booking record or the anonymized Patient shell — only clinical content is purged; the shell records persist indefinitely.
- Purging content for a booking whose patient has not been anonymized, regardless of age — anonymization is a hard precondition, not just a preference.
- Any self-service or ClinicAdmin-level manual trigger — only Super Admin can manually re-trigger background jobs.

## Source References
- BDD §2 (in-scope: "DPDP two-tier deletion... monthly automatic purge of retention-expired clinical content (3-year window)")
- BDD §3.6 (DPDP Compliance)
- BDD §7 Persona table (Super Admin: "only role that can manually re-trigger background jobs"; System (Scheduler) persona)
- BDD §8 item 1 (retention window resolved to fixed 3-year constant per this conversation's scope decision)
