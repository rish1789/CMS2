# Feature Specification: Patient Record Auto-Creation & Phone-Based Linking

**Feature Branch**: `009-patient-record-phone-linking`

**Created**: 2026-09-02

**Status**: Draft

**Input**: User description: "Patient Record Auto-Creation & Phone-Based Linking — the first time a Patient Account holder books at a clinic, the system checks for an existing clinic-scoped Patient record at that clinic whose phone number exactly matches the account's phone number. If found, automatically link (no duplicate, prior visit history preserved); if not, create a new clinic-scoped Patient record and link it. Race-safe at the data layer (clinic+phone uniqueness or equivalent) — two simultaneous first-time bookings for the same phone at the same clinic must never create two records. Patient records stay clinic-scoped after linking — no cross-clinic merge. Matching is exact phone number only, no fuzzy/name matching. (Full source: backlog/019-patient-record-auto-creation-phone-linking.md)"

## Clarifications

### Session 2026-09-02

- Q: Should a Patient record already linked to a different Patient Account be excluded from phone-matching (so two different accounts sharing a phone never collide into one record), or should any exact phone match always win regardless of existing linkage? → A: Protect linked records — a record already linked to a *different* Patient Account is never matched again; a second account sharing that phone gets its own separate record at that clinic instead. Chosen over the simpler "any match wins" alternative because Constitution Principle IV requires privacy-by-design on identity-matching operations, and the source material's own race-condition framing is about the *same* real person double-booking, not about deliberately merging two different people's data. This means the data-layer uniqueness guarantee (FR-005) can't be one flat clinic+phone constraint — it needs to separately guarantee (a) one account never gets two records at the same clinic, and (b) two unlinked (walk-in) records never share a clinic+phone — see FR-005's revised wording below.

## Scope Decisions Made During Drafting

No prior conversation turn resolved these — they're research-backed defaults made while writing this spec, not a `/speckit-clarify` session (which runs next and may revisit any of them). Each is expanded in Assumptions below.

- No feature anywhere in the backlog defines the clinic-scoped `Patient` entity's schema — this is genuinely the first feature that needs one to exist, since it's earlier in build order than every feature that will *also* write to it later (016, 018, 020, each independently creating walk-in Patient records). This spec defines the minimal schema this feature itself needs.
- No feature (017, 018 — this feature's only two consumers) exists yet to call this logic from an actual booking flow. Per Constitution Principle III ("a clear contract — service interface **or** REST endpoint"), this feature ships as a service-layer contract only, with no premature HTTP endpoint — matching the precedent set by 007's discovery-eligibility guarantee (data/service-layer correctness, consumed later by 035).
- `PatientAccount.mobile` (039) is optional, not required at signup. When it's absent, no match is attempted (there is nothing to match against) — a new Patient record is simply created, consistent with "matching is by exact phone number only."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - First Booking Reuses a Matching Walk-In Record (Priority: P1)

A Patient Account holder books at a clinic for the first time, and the system finds an existing walk-in Patient record at that clinic with the same phone number — it links the two automatically, so the patient's prior visit history is immediately available under their account, with no duplicate record and no staff involvement.

**Why this priority**: This is the core value of the feature — the specific compliance/data-integrity gap the source material calls out (a real patient with prior walk-in history ending up invisible to their own new online account).

**Independent Test**: Create a clinic-scoped Patient record directly (simulating a prior walk-in) with a known phone number; then invoke the linking logic for a Patient Account holding that same phone number, at that same clinic; confirm the existing Patient record is returned (no new one created) and is now associated with that Patient Account.

**Acceptance Scenarios**:

1. **Given** a walk-in Patient record already exists at Clinic A with phone number 9812345670, **When** a Patient Account holder with the same phone number is checked/linked for Clinic A for the first time, **Then** the existing Patient record is reused (not duplicated) and becomes associated with that Patient Account, with its prior visit history intact.
2. **Given** the same real person has separate Patient records at Clinic A and Clinic B (matched independently at each), **When** either record is viewed, **Then** it reflects only that clinic's own visit history — the two are never merged into one.
3. **Given** a Patient record at Clinic A with phone number 9812345670 is already linked to Patient Account X, **When** a *different* Patient Account Y, also holding phone number 9812345670, is checked/linked for Clinic A for the first time, **Then** Account Y is not linked to that existing record — a new, separate Patient record is created for Account Y instead.

---

### User Story 2 - First Booking With No Match Creates a New Record (Priority: P1)

A Patient Account holder books at a clinic for the first time, and no existing Patient record at that clinic matches their phone number — the system creates a new clinic-scoped Patient record and links it to their account.

**Why this priority**: Equally core — this is the "no match" half of the same first-booking flow US1 covers, and without it, a first-time patient at a clinic with no prior walk-in history could never get a Patient record at all.

**Independent Test**: Invoke the linking logic for a Patient Account and clinic with no existing Patient record matching that phone number; confirm a new Patient record is created, scoped to that clinic, and linked to the account.

**Acceptance Scenarios**:

1. **Given** no existing Patient record at Clinic A matches the Patient Account holder's phone number, **When** they are checked/linked for Clinic A for the first time, **Then** a new clinic-scoped Patient record is created and linked to their Patient Account.
2. **Given** a Patient Account holder has already been linked to a Patient record at Clinic A (from a prior first booking there), **When** the check/link logic runs again for Clinic A, **Then** no new Patient record is created — the already-linked record is reused directly (the account-to-clinic link itself is checked first, before any phone re-matching).

---

### User Story 3 - Concurrent First Bookings for the Same Account Never Duplicate a Record (Priority: P2)

The *same* Patient Account submits two first-time booking attempts at the same clinic at (almost) the same moment — e.g. a double-tap or a retried request — and only one Patient record ever exists afterward, with both attempts ending up linked to that single record. (Two genuinely *different* Patient Accounts that happen to share a phone number are a separate case, resolved by the linked-record protection in User Story 1 AC3 — each gets its own record, by design, not a race to prevent.)

**Why this priority**: This is the race-condition guarantee the business rules explicitly call out as a named NFR — critical for correctness, but it's a hardening of US1/US2's behavior under concurrency rather than a separate capability.

**Independent Test**: Issue two concurrent invocations of the check/link logic for the same Patient Account, phone number, and clinic (no pre-existing record); confirm exactly one Patient record exists afterward, and both invocations return that same record's identity.

**Acceptance Scenarios**:

1. **Given** the same Patient Account submits two simultaneous first-time booking requests at the same clinic (no pre-existing record), **When** both are processed, **Then** only one clinic-scoped Patient record exists afterward, and neither request fails — the one that loses the creation race transparently attaches to the record the other created.

---

### Edge Cases

- What happens when the Patient Account holder has no phone number on file (`mobile` is optional per 039)? → No matching is attempted (there is nothing to match against) — a new Patient record is created directly, with a `null` phone. It can never be found by a future walk-in phone-match, which is an accepted consequence of phone being the sole matching key.
- What happens if two different Patient Accounts happen to share the same phone number (e.g. family members) and the second account's phone matches a record already linked to the first? → Per Clarifications, the already-linked record is protected — the second account does not get linked to it (that would leak the first account's clinical history to an unrelated account). Instead, a new, separate Patient record is created for the second account at that clinic, coincidentally sharing the same phone value with the first account's record.
- What happens to the walk-in Patient record's own data (name, prior visit history) when it gets linked? → Unchanged and preserved — linking only associates the existing record with the Patient Account; it does not modify, rename, or merge any of the record's existing fields or history.
- What happens if the same Patient Account books at a third clinic, Clinic C, where they've never been (no walk-in match)? → Independent of Clinics A/B — evaluated fresh against Clinic C's own records only, per the clinic-scoping rule.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST provide a way to, for a given Patient Account and clinic, find or create a clinic-scoped Patient record and ensure the Patient Account is linked to it — the entry point future booking features (017, 018) will call on a patient's first booking at a clinic.
- **FR-002**: If the Patient Account is already linked to a Patient record at the given clinic, system MUST return that existing record directly — no phone re-matching, no new record.
- **FR-003**: Otherwise, if the Patient Account has a phone number on file, and a Patient record at that clinic exists with the exact same phone number and is not yet linked to a *different* Patient Account, system MUST link the Patient Account to that existing record (setting the link, changing no other field) and return it — no new record is created.
- **FR-004**: Otherwise (no phone number on file, or no matching record found), system MUST create a new Patient record scoped to that clinic, linked to the Patient Account, and return it.
- **FR-005**: System MUST close, at the data layer (database-level constraints, not merely an application-level check), both of the following concurrent-duplicate races: (a) two simultaneous linking/creation attempts by the *same* Patient Account at the *same* clinic MUST never result in that account having two Patient records at that clinic (US3); (b) two simultaneous attempts to create a new, not-yet-linked Patient record for the same clinic+phone MUST never result in two unlinked records sharing that clinic+phone. Neither constraint is a flat, unconditional clinic+phone uniqueness — FR-003's linked-record protection means two *linked* rows (belonging to different accounts) may legitimately share a clinic+phone combination (Clarifications).
- **FR-006**: When either race in FR-005 is lost (the request's insert is rejected because a concurrent request just committed the conflicting row first), system MUST NOT fail the request — it MUST re-read the now-existing record and complete the link against it, so both concurrent callers succeed and end up pointing at the same single record.
- **FR-007**: System MUST NOT merge, aggregate, or cross-reference a Patient Account holder's Patient records across different clinics — each clinic-scoped Patient record remains independently visible only within its own clinic's context.
- **FR-008**: System MUST match exclusively on exact phone number equality — no partial, fuzzy, or name-based matching of any kind.

### Key Entities

- **Patient** (new entity — first defined by this feature; see Assumptions for schema rationale): a clinic-scoped clinical/visit record. Fields: clinic (required, scopes the record), phone (optional, the sole matching key), name (required — provided by whichever flow creates the row, since `PatientAccount` itself carries no name field), an optional link to a `PatientAccount` (null for a walk-in-only record never claimed by a self-service booking), created timestamp.
- **PatientAccount** (from 039, read-only here): supplies the phone number (`mobile`, optional) used as the matching key, and is the identity this feature links a Patient record to.
- **Clinic** (from 001, read-only here): scopes every Patient record; unchanged by this feature.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of first-time linking attempts that match an existing walk-in record by exact phone number result in that same record being reused, with its prior visit history intact and zero duplicate records created.
- **SC-002**: 100% of first-time linking attempts with no matching record result in exactly one new, correctly clinic-scoped, correctly linked Patient record.
- **SC-003**: 100% of repeated linking attempts for a Patient Account already linked at a given clinic return the same existing record, creating zero additional rows.
- **SC-004**: Under concurrent first-time linking attempts by the *same* Patient Account for the same new clinic+phone combination, 100% of attempts succeed (none fail/error), and exactly one Patient record exists afterward, verified directly at the data layer.
- **SC-005**: 100% of a Patient Account holder's Patient records across different clinics remain independently scoped — querying one clinic's record never returns or references another clinic's visit data for the same person.
- **SC-006**: 100% of linking attempts whose matched phone number belongs to a record already linked to a *different* Patient Account result in a new, separate record for the requesting account — zero cases of one account gaining access to another account's already-linked Patient record.

## Assumptions

- **No prior feature defines the `Patient` entity's schema.** This feature is the first to need one (it's earlier in build order than 016/018/020, which will each also create rows in this same table later for their own walk-in flows). The schema above (clinic, phone, name, optional PatientAccount link, created timestamp) is scoped to exactly what *this* feature's own matching/linking logic requires — deliberately minimal (Constitution Principle II), not a speculative full patient-record shape. Later features may need to extend it; that's their concern, not this one's.
- **No HTTP endpoint is introduced by this feature.** Its only two consumers (017, 018) don't exist yet, and inventing a booking-shaped endpoint now — before either feature defines what a booking submission actually looks like — would be speculative surface this feature has no way to get right. Constitution Principle III explicitly permits a service-interface-only contract ("a clear contract (service interface or REST endpoint)"); this feature is exercised and tested at that layer, with 017/018 wiring it into their own endpoints when they're built.
- **The `name` on a newly-created Patient record must come from the caller, not from `PatientAccount`.** `PatientAccount` (039) has no name field at all (only email/mobile/password) — so a future booking flow calling this feature's service must supply the name to use for a new record (e.g., collected on the booking form itself). This feature's own scope stops at requiring that input; it doesn't decide how a future booking flow collects it.
- A Patient record matched and linked per FR-003 is never un-linked or re-linked to a different account by this feature — the link, once set, is treated as permanent within this feature's scope (no un-linking mechanism is described anywhere in the source material).
