# 019 — Patient Record Auto-Creation & Phone-Based Linking

**Module:** Booking
**Status:** Ready for spec-kit intake

## User Story
As a Patient Account holder booking at a clinic for the first time, I want the system to automatically create (or correctly link to) my clinic-scoped Patient record — including recognizing a past walk-in visit under the same phone number — so that I don't end up with duplicate, disconnected records at that clinic, and my past walk-in history carries over.

## Context
Patient Account is a global login identity, separate from the clinic-scoped Patient record that actually holds visit/booking history (see 039-patient-account-global-login, BDD §5, §7.2). A person may have visited a clinic before as an unregistered walk-in (a Patient record created directly by staff, no login) and only later create a Patient Account and book online. This feature defines exactly how those two worlds connect — and closes a real race-condition risk called out in the doc's NFRs.

## Business Rules
- The first time a Patient Account holder books at a given clinic, the system checks for an existing clinic-scoped Patient record at that same clinic whose phone number exactly matches the Patient Account's phone number.
- If a matching walk-in Patient record is found, the system automatically links the Patient Account to that existing record — no new duplicate Patient record is created, and the existing record's visit history is preserved and now accessible under the Patient Account. This linking is fully automatic; it does not require staff confirmation.
- If no matching record is found, a new clinic-scoped Patient record is created and linked to the Patient Account.
- This check-and-link (or check-and-create) operation must be safe under concurrency: two simultaneous first-time bookings for the same real person at the same clinic must never result in two duplicate Patient records — this race condition is explicitly closed at the data layer (e.g. via a uniqueness constraint on clinic+phone, or equivalent).
- Patient records remain clinic-scoped after linking — a Patient Account holder who has visited three different clinics still has three separate clinic-scoped Patient records (this feature only prevents duplication *within* a single clinic), consistent with the deliberate no-global-medical-record design.
- Matching is by exact phone number only — no fuzzy/name-based matching in v1.

## Acceptance Criteria
- Given a walk-in Patient record already exists at Clinic A with phone number 9812345670, when a Patient Account holder with the same phone number books at Clinic A for the first time, then their booking is linked to that existing Patient record (not a new one), and its prior visit history is visible to them.
- Given no existing Patient record at Clinic A matches the booking patient's phone number, when they book there for the first time, then a new clinic-scoped Patient record is created and linked to their Patient Account.
- Given a Patient Account holder has already booked once at Clinic A (a Patient record already exists and is linked), when they book again at Clinic A, then no new Patient record is created — the existing linked record is reused.
- Given two simultaneous first-time booking requests arrive for the same phone number at the same clinic, when both are processed, then only one clinic-scoped Patient record exists afterward, not two.
- Given the same real person has separate Patient records at Clinic A and Clinic B (different clinics), when either record is viewed, then it shows only that clinic's visit history — the two records are never merged into one.

## Dependencies
- Depends on: 039-patient-account-global-login — the Patient Account identity that triggers this linking check.
- Blocks / feeds into: 017-patient-self-service-fixed-time-booking, 018-queue-token-booking — both self-service booking flows invoke this logic on a patient's first booking at a clinic.

## Explicitly Out of Scope
- Cross-clinic merge into a single global medical record — explicitly out of scope; each clinic keeps its own separate Patient record even for the same real person (confirmed v1 design, BDD §2, §7.2).
- Fuzzy/name-based matching, or matching on any field other than exact phone number.
- Manual staff-initiated merge of two records that were never phone-matched — not part of v1.

## Source References
- BDD §2 (Out-of-Scope: single global patient medical record)
- BDD §4 (Concurrency/uniqueness guarantee closing the duplicate-Patient-record race condition)
- BDD §5 (Patient Account 1─N Patient relationship; Patient auto-created on first booking)
- BDD §7.2 (patient identity duplication — clarified, not fully resolved; global login layered on fragmented per-clinic records)
