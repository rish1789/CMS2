# Feature Specification: Doctor Profile Auto-Creation & License Verification Queue

**Feature Branch**: `007-doctor-profile-license-queue`

**Created**: 2026-09-02

**Status**: Draft

**Input**: User description: "Doctor Profile Auto-Creation & License Verification Queue — a single global Doctor Profile per Account (not per clinic) enters an awaiting-license-verification queue the moment someone is onboarded as a Doctor (004), so Super Admin has a worklist and the doctor cannot appear publicly until verified. A separate public-visibility toggle exists independent of licenseVerified. Verification is manual/off-platform: Super Admin reviews credentials externally, then flips licenseVerified via an admin screen — the same pattern as clinic verification (002/003). A doctor onboarded at a second clinic must reuse their existing global profile, not get a duplicate. (Full source: backlog/005-doctor-profile-auto-creation-license-queue.md)"

## Clarifications

### Session 2026-09-02

- Q: 004's onboarding form always creates a brand-new Account per submission — it has no "search for an existing doctor" step. When a ClinicAdmin onboards someone who is actually already a Doctor at another clinic, how does the system recognize this is the same doctor rather than create a duplicate Account + Doctor Profile? → A: Match by the license number already collected on 004's onboarding form. If an existing Doctor Profile has that license number, cross-check the submitted specialization against it — matching specialization means the same doctor, so reuse the existing Account + Doctor Profile (new Role Assignment only, no new credentials, since staff login is already Account-scoped, not clinic-scoped — see FR-002/FR-010). A mismatched specialization on an otherwise-matching license number is treated as a conflict and rejected, rather than silently overwritten, since the profile is shared globally across every clinic that doctor works at.
- Q: When an existing Doctor Profile is reused for a second clinic (FR-002), does its `licenseVerified` value carry over unchanged, or does linking a new clinic reset it back to `false` pending re-verification for that specific clinic? → A: Carries over unchanged — a verified doctor stays verified at every clinic they're linked to, since verification is a property of the doctor's global professional identity, not of any one clinic relationship. Only an explicit license edit (006-doctor-license-edit-reverification-reset) resets it; re-onboarding at a new clinic never does. This also reinforces the specialization-match rule (FR-002a): a doctor licensed and verified in one specialization (e.g. ENT) cannot be onboarded elsewhere under a different specialization (e.g. Radiology) using the same license number — that submission is rejected as a conflict, not treated as "the same doctor branching into a second specialty," since the profile carries exactly one specialization value shared globally.
- Q: Specialization is free text with no fixed list (per 004's existing `DoctorDto.specialization` field). Should the FR-002/FR-002a specialization match be an exact literal comparison, or tolerate whitespace/case differences? → A: Case-insensitive, trimmed comparison ("ent" = "ENT" = " ENT ") — the same doctor's professional identity shouldn't be blocked from a legitimate second-clinic onboarding by a different clinic staff member's capitalization or whitespace habits when typing free text.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Super Admin Reviews the License-Verification Queue (Priority: P1)

A Super Admin opens a worklist of doctors awaiting license verification, reviews each one's credentials externally, then marks a doctor's license verified — the gate that (together with visibility and clinic verification) allows the doctor to become publicly discoverable.

**Why this priority**: This is the entire point of the feature — without a working queue and verification action, doctors onboarded via 004 sit in `licenseVerified=false` forever with no way for Super Admin to act on them.

**Independent Test**: Onboard a Doctor (004), confirm they appear in the Super Admin's pending-license-verification list with their profile details (specialization, license number, experience), mark the license verified, and confirm `DoctorProfile.licenseVerified` becomes `true`.

**Acceptance Scenarios**:

1. **Given** a Doctor Profile with `licenseVerified = false`, **When** the Super Admin opens the pending-license-verification list, **Then** the profile appears with its specialization, license number, and experience.
2. **Given** the Super Admin has reviewed a doctor's credentials externally and approves them, **When** they mark the license verified, **Then** `DoctorProfile.licenseVerified` becomes `true`.
3. **Given** a non-Super-Admin actor (ClinicAdmin, Doctor, Operations, or unauthenticated), **When** they attempt to call the verify action or read the pending list, **Then** the request is rejected as unauthorized.
4. **Given** a Doctor Profile whose license is already verified, **When** Super Admin calls verify again, **Then** it succeeds idempotently with no error and no duplicate side effects.

---

### User Story 2 - Doctor Profile Is Created Automatically at Onboarding (Priority: P2)

The moment a ClinicAdmin onboards a new Doctor (004), a Doctor Profile exists automatically in the awaiting-verification state — no separate manual "create profile" step, and never more than one profile per Account globally.

**Why this priority**: This is the trigger condition that populates US1's queue in the first place; without correct, non-duplicating creation, the queue either stays empty or accumulates duplicate rows for the same doctor.

**Independent Test**: Onboard a Doctor via 004 and confirm exactly one Doctor Profile row exists with `licenseVerified = false`. Onboard a second Doctor submission with the same license number and matching specialization at a different clinic, and confirm no second Doctor Profile or Account is created — only a new Role Assignment. Repeat with a mismatched specialization and confirm the submission is rejected.

**Acceptance Scenarios**:

1. **Given** a ClinicAdmin onboards a new Doctor (004) whose license number matches no existing Doctor Profile, **When** the Doctor Role Assignment is created, **Then** a new Account and Doctor Profile are created in the same transaction with `licenseVerified = false` and public visibility defaulting to `true` (see Assumptions), and new login credentials (staff code + temporary password) are generated as today.
2. **Given** a doctor already has a Doctor Profile from onboarding at another clinic, **When** a ClinicAdmin at a second clinic onboards a Doctor submission with the same license number and matching specialization, **Then** no second Account or Doctor Profile is created — the existing global profile is reused, a new Role Assignment links the existing Account to the second clinic, and no new login credentials are generated (the response indicates the doctor already has an account and states their existing staff code).
3. **Given** a doctor onboarding submission's license number matches an existing Doctor Profile but the submitted specialization does not match that profile's specialization, **When** the submission is processed, **Then** it is rejected with a validation error and no Account, Doctor Profile, or Role Assignment is created — the profile is shared globally across every clinic that doctor works at, so a mismatch is surfaced rather than silently overwritten or ignored. For example, a license number already on file for an ENT specialist submitted with specialization "Radiology" is rejected — the same license number cannot be reused to onboard the holder under a different specialty.
4. **Given** a doctor's Doctor Profile is already `licenseVerified = true` from a prior clinic, **When** they are onboarded (matched via license number + specialization) at a second clinic, **Then** the reused profile's `licenseVerified` remains `true` — the new Role Assignment does not reset it, and no re-verification queue entry is created for this doctor.

---

### User Story 3 - Discovery Is Gated by License Verification, Visibility, and Clinic Verification Together (Priority: P3)

A Doctor Profile is excluded from public discovery unless three independent conditions all hold: the license is verified, the doctor's own visibility toggle is on, and the doctor's clinic is verified (002).

**Why this priority**: This is a data-integrity guarantee that 035-public-discovery-search depends on, but it only matters once US1/US2 exist to produce verified profiles — it's the enforcement rule, not the primary workflow.

**Independent Test**: Query discovery-eligible doctors directly at the data layer for a Doctor Profile with `licenseVerified = false` (visibility on, clinic verified) and confirm it is absent; flip `licenseVerified` to `true` and confirm it becomes eligible.

**Acceptance Scenarios**:

1. **Given** a Doctor Profile with `licenseVerified = false`, **When** a discovery-eligibility query is run, **Then** this doctor does not appear regardless of the visibility toggle's value or the clinic's verification status.
2. **Given** a Doctor Profile with `licenseVerified = true` but visibility toggled off, **When** a discovery-eligibility query is run, **Then** this doctor does not appear.
3. **Given** a Doctor Profile with `licenseVerified = true`, visibility on, but the doctor's clinic has `verified = false`, **When** a discovery-eligibility query is run, **Then** this doctor does not appear.
4. **Given** a Doctor Profile with `licenseVerified = true`, visibility on, and clinic `verified = true`, **When** a discovery-eligibility query is run, **Then** this doctor appears.
5. **Given** a Doctor Profile with `licenseVerified = true`, visibility on, and a clinic `verified = true`, but the doctor's Role Assignment at that clinic has been deactivated (staff deactivation, 007-last-active-clinicadmin-protection's mechanism), **When** a discovery-eligibility query is run, **Then** this doctor does not appear via that clinic.

---

### Edge Cases

- What happens when Super Admin attempts to verify a license for a Doctor Profile that doesn't exist? → Rejected with a not-found error; no state change.
- What happens if a Doctor holds Role Assignments at multiple clinics and one of those clinics later becomes unverified? → That specific clinic-linked visibility is unaffected here (out of scope — this feature only enforces the AND-condition per query; cross-clinic aggregation behavior belongs to 035/008); this feature's own guarantee is simply that `licenseVerified` and `visible` live on the single global profile, not per clinic.
- What happens if a doctor's own visibility toggle is flipped by someone other than the doctor? → Out of scope for this feature to define who may flip it beyond noting it exists as a field; the toggle's own read/write access control is deferred to whichever feature builds the doctor-facing profile-editing screen (not yet in the backlog at time of writing).
- What happens when the same Doctor Profile is queried for discovery eligibility concurrently with a Super Admin verifying it? → Standard read-committed transaction isolation applies; no special handling required since this is a simple boolean flag flip, not a multi-step invariant.
- What happens if the submitted specialization differs from the existing profile's only in case or surrounding whitespace (e.g. "ent" vs "ENT")? → Treated as a match, not a conflict — the comparison is case-insensitive and trimmed (see Clarifications).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST create a Doctor Profile automatically, in the same transaction as the Doctor Role Assignment, whenever a Doctor onboarding submission's license number matches no existing Doctor Profile — with `licenseVerified` starting `false`.
- **FR-002**: At Doctor onboarding, system MUST look up whether a Doctor Profile already exists with the submitted license number. If one exists and the submitted specialization matches that profile's specialization under a case-insensitive, trimmed comparison, system MUST NOT create a second Account or Doctor Profile — it MUST reuse the existing Account and Doctor Profile, creating only a new Role Assignment linking that Account to the onboarding clinic.
- **FR-002a**: If an existing Doctor Profile's license number matches the submission but its specialization does not match under the case-insensitive, trimmed comparison (FR-002), system MUST reject the onboarding submission with a validation error and MUST NOT create any Account, Doctor Profile, or Role Assignment.
- **FR-002b**: When onboarding reuses an existing Account per FR-002, system MUST NOT generate new login credentials (staff code or password) for that Account — login remains Account-scoped (see FR-010) and unaffected by how many clinics reference it. The onboarding response MUST indicate that the doctor already has an account and MUST return their existing staff code (not a new one).
- **FR-002c**: When onboarding reuses an existing Account per FR-002, the reused Doctor Profile's `licenseVerified` value MUST carry over unchanged (verification is global per doctor, not per clinic) — reusing the profile for a new clinic MUST NOT reset `licenseVerified` to `false`. Only an explicit license edit (006-doctor-license-edit-reverification-reset) resets it.
- **FR-003**: System MUST expose a public-visibility toggle field on the Doctor Profile, independent of `licenseVerified`, defaulting to `true` at profile creation (see Assumptions).
- **FR-004**: System MUST provide a way for a Super Admin to list Doctor Profiles pending license verification (`licenseVerified = false`), showing each profile's specialization, license number, and experience.
- **FR-005**: System MUST allow a Super Admin to mark a Doctor Profile's license as verified (`licenseVerified: false → true`).
- **FR-006**: System MUST restrict the verify action (and the pending-list read) to requests authenticated with the configured Super Admin credentials (the same mechanism as 002/003) — any other or missing credentials MUST be rejected as unauthorized.
- **FR-007**: System MUST treat the verify action as idempotent — calling it on a Doctor Profile already `licenseVerified = true` MUST succeed without error and MUST NOT change state further or produce duplicate side effects.
- **FR-008**: System MUST enforce discovery eligibility as the conjunction of `licenseVerified = true` AND visibility toggle `= true` AND the doctor holding an *active* Role Assignment at a clinic with `verified = true`, evaluated at the data-query level (not response-level filtering) — a deactivated Role Assignment at an otherwise-verified clinic MUST NOT count, since that doctor no longer actually works there. This feature is responsible for keeping the underlying fields/relationship correct and queryable together; the discovery query itself belongs to 035.
- **FR-009**: System MUST enforce a database-level uniqueness constraint ensuring at most one Doctor Profile exists per Account (already present as of 004's implementation: `uq_doctor_profile_account`) — this feature MUST NOT weaken or remove that constraint.
- **FR-010**: System MUST continue to rely on staff login (003/006) being Account-scoped, not clinic-scoped — a doctor's single staff code + password already authenticates them regardless of how many Role Assignments (clinics) their Account holds. This feature introduces no changes to login/token issuance; FR-002b's "no new credentials on reuse" follows directly from this existing property.

### Key Entities

- **Doctor Profile** (from 004; extended here): one global row per Account. This feature adds a `visible` (public-visibility toggle) field and owns the verification-queue *workflow* (list + verify action) around the existing `licenseVerified` field that 004 already creates as `false`.
- **Account** (from 001/004): unchanged here; the `account_id` unique constraint on Doctor Profile is what enforces the one-profile-per-Account invariant.
- **Clinic** (from 001/002): read-only dependency here — its existing `verified` field is one of the three AND-conditions for discovery eligibility (FR-008), not modified by this feature.
- **Super Admin**: not a stored entity (per 002/003's precedent) — the same configuration-bootstrapped credential pair used there.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of newly onboarded Doctors (via 004) appear in the Super Admin's pending-license-verification list immediately, with `licenseVerified = false`.
- **SC-002**: 100% of verify attempts by a non-Super-Admin actor are rejected, with zero state changes.
- **SC-003**: At most one Doctor Profile row ever exists per Account, enforced at the database layer, in 100% of cases including concurrent Role-Assignment creation attempts.
- **SC-004**: 100% of discovery-eligibility queries correctly exclude a Doctor Profile when any one of the four conditions (license verified, visibility on, clinic verified, an active Role Assignment linking the doctor to that clinic) is false, verified by querying the data layer directly.
- **SC-005**: Repeating the verify action on a Doctor Profile already verified succeeds idempotently in 100% of attempts, with no duplicate side effects.
- **SC-006**: 100% of Doctor onboarding submissions whose license number matches an existing Doctor Profile with matching specialization result in zero new Accounts, zero new Doctor Profiles, and zero newly generated credentials — only a new Role Assignment.
- **SC-007**: 100% of Doctor onboarding submissions whose license number matches an existing Doctor Profile with a different specialization are rejected, with zero Account, Doctor Profile, or Role Assignment rows created.
- **SC-008**: 100% of already-verified Doctor Profiles remain `licenseVerified = true` after being reused for an additional clinic — reuse never re-enters a doctor into the pending-verification queue.

## Assumptions

- The public-visibility toggle (FR-003) defaults to `true` at profile creation: the source material describes it as an independent gate rather than an extra opt-in step, and `licenseVerified = false` already fully blocks discovery on its own until Super Admin acts — defaulting visibility off as well would add a second, undocumented manual step with no described trigger to turn it on. This default can be revisited if a later feature (e.g. a doctor-facing profile screen) introduces an explicit opt-in flow.
- Who may read/write the visibility toggle after creation (e.g. a doctor-facing settings screen) is not yet a built capability anywhere in the backlog and is out of scope here; this feature only establishes the field and its default.
- Account-matching across multiple clinics (FR-002/FR-002a) uses license number as the sole match key, cross-checked against specialization, because both are already collected on 004's existing onboarding form — no new UI/search step is introduced. Mobile number was considered as an alternative match key but rejected: it's optional on the onboarding form (per 004's FR-008), so it can't reliably identify every submission, whereas license number is mandatory and inherently unique per real-world doctor.
- Experience years is deliberately excluded from the match/conflict check — a doctor's experience naturally increases over time and across re-onboardings, so requiring it to match would create false conflicts; only license number (identity) and specialization (a comparatively stable professional attribute) are checked.
- In-app document upload for license proof and automated license validation against an external registry remain explicitly out of scope, per the source backlog entry.
- The verification action's authorization mechanism reuses 002/003's configuration-bootstrapped Super Admin credential pattern rather than introducing a new one, for consistency with the one precedent this project already has for manual/off-platform verification actions.
