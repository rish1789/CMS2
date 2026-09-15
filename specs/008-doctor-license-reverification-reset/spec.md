# Feature Specification: Doctor License Edit Triggers Re-Verification Reset

**Feature Branch**: `008-doctor-license-reverification-reset`

**Created**: 2026-09-02

**Status**: Draft

**Input**: User description: "Doctor License Edit Triggers Re-Verification Reset — editing a verified doctor's license number automatically resets licenseVerified to false, in the same transaction as the edit, so a changed license number can never silently retain a stale verification. Editing any other Doctor Profile field (specialization, experience, contact info, visibility toggle) does NOT trigger this reset. The reset only affects discoverability — it does not cascade to cancel existing bookings/schedules (that's 008-deverification-cascade-auto-cancel-bookings' separate, deliberate mechanism, triggered only by an explicit Super Admin revoke action). (Full source: backlog/006-doctor-license-edit-reverification-reset.md)"

## Clarifications

### Session 2026-09-02

- Q: No prior feature (001–005) builds any Doctor Profile edit capability at all — there's no endpoint or screen anywhere that lets any actor change a Doctor Profile's fields after onboarding. This feature's entire premise is "when the license number is edited" — so who can perform that edit, and how? → A: Super Admin only. This feature builds the edit capability itself (as a new action alongside 005's existing pending-verification worklist/verify action, same admin surface, same configuration-bootstrapped credential authorization as 002/003/005) — no ClinicAdmin or Doctor self-service edit path exists. This keeps every license-sensitive change behind the one actor who already manually reviews credentials, avoids the multi-clinic ownership ambiguity a ClinicAdmin-edits model would create (the profile is global, not clinic-scoped), and requires no new Doctor-facing UI.
- Q: Should Super Admin's edit action also let them toggle the `visible` field (added by 007, currently unwritable by anyone)? → A: Yes — Super Admin's edit action covers specialization, license number, experience years, and `visible` together. This closes 007's deliberately-deferred "who can write `visible`" gap using the actor already established for this feature, rather than leaving the field permanently stuck at its default.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Super Admin Edits a Verified Doctor's License Number, Resetting Verification (Priority: P1)

Super Admin edits a Doctor Profile whose license is currently verified, changing the license number field, and the system automatically resets `licenseVerified` back to `false` in that same save — so the doctor's old verification can never silently carry over to a new, unreviewed license number.

**Why this priority**: This is the entire point of the feature — the precise, narrow invariant that closes a real compliance gap (a stale verification surviving a license-number change).

**Independent Test**: As Super Admin, starting from a Doctor Profile with `licenseVerified = true`, edit its license number and save; confirm `licenseVerified` is `false` immediately after, in the same transaction as the edit.

**Acceptance Scenarios**:

1. **Given** a Doctor Profile with `licenseVerified = true`, **When** Super Admin changes its license number and saves, **Then** `licenseVerified` becomes `false` in the same transaction as the edit.
2. **Given** a Doctor Profile with `licenseVerified = false` already, **When** Super Admin changes its license number and saves, **Then** `licenseVerified` remains `false` (no-op on the already-false state, not an error).
3. **Given** the reset has occurred, **When** public discovery is queried, **Then** the doctor no longer appears until Super Admin re-verifies the new license number (005's queue).

---

### User Story 2 - Editing Any Other Field Never Resets Verification (Priority: P2)

Super Admin edits a verified Doctor Profile's specialization, experience, or visibility toggle — none of these trigger the reset; only the license number field does.

**Why this priority**: This is the boundary condition that keeps the rule narrow and precise, as the source material insists — without it, the feature would either be untestable as "only license number" or would risk over-triggering on unrelated edits.

**Independent Test**: As Super Admin, starting from a Doctor Profile with `licenseVerified = true`, edit its experience-years field only and save; confirm `licenseVerified` is still `true` afterward.

**Acceptance Scenarios**:

1. **Given** a Doctor Profile with `licenseVerified = true`, **When** Super Admin edits any field other than license number (specialization, experience, visibility) and saves, **Then** `licenseVerified` is unchanged.

---

### Edge Cases

- What happens if a license-number edit is submitted with the exact same value as the current one (a no-op edit)? → No reset — the rule fires on the field's *value changing*, not on the field being present in the edit request.
- What happens to future bookings, schedules, or patient notifications when this reset occurs? → Nothing — explicitly out of scope. Only discoverability changes; no cascade to bookings (that is 008-deverification-cascade-auto-cancel-bookings' separate, narrower-triggered mechanism, fired only by an explicit Super Admin revoke action, not by this edit-triggered reset).
- What happens if the edit changes both the license number and another field (e.g., specialization) in the same save? → The reset still fires, because the license number changed — the presence of other simultaneous field changes doesn't suppress or alter the rule.
- What happens if Super Admin submits an edited license number that already belongs to a different Doctor Profile? → Rejected with a validation error (the same global-uniqueness invariant 007 already enforces for onboarding) — no reset, no partial write.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST provide a way for Super Admin to edit an existing Doctor Profile's specialization, license number, experience years, and `visible` toggle — the sole authorized actor for this action (see Clarifications). No ClinicAdmin or Doctor self-service edit path is in scope.
- **FR-001a**: System MUST restrict this edit action to requests authenticated with the configured Super Admin credentials (the same mechanism as 002/003/005) — any other or missing credentials MUST be rejected as unauthorized.
- **FR-002**: System MUST compare the submitted license number against the Doctor Profile's current license number as part of processing any edit.
- **FR-003**: If the license number value is changing (submitted value differs from the current stored value) AND the profile's `licenseVerified` is currently `true`, system MUST reset `licenseVerified` to `false` in the same transaction as the field update.
- **FR-004**: System MUST NOT reset `licenseVerified` when the license number is not changing (including a resubmission of the identical value), regardless of whether other fields in the same edit are changing.
- **FR-005**: System MUST NOT reset `licenseVerified` when only non-license-number Doctor Profile fields (specialization, experience years, visibility toggle) change.
- **FR-006**: System MUST NOT trigger, call into, or otherwise cause any side effect belonging to 008-deverification-cascade-auto-cancel-bookings (booking cancellation, schedule changes, patient notification) as a result of this reset — the two mechanisms remain fully independent.
- **FR-007**: System MUST enforce the same global-uniqueness expectation on an edited license number that onboarding already enforces (007's `uq_doctor_profile_license_number` constraint) — an edit MUST NOT be allowed to produce two Doctor Profiles sharing one license number.

### Key Entities

- **Doctor Profile** (from 004/007): this feature adds no new fields — it reads and conditionally resets the existing `licenseVerified` field as a side effect of an edit to the existing `licenseNumber` field.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of license-number-changing edits to a previously-verified Doctor Profile result in `licenseVerified = false` immediately after the edit.
- **SC-002**: 100% of edits that do not change the license-number value (whether resubmitting the same value or editing only other fields) leave `licenseVerified` unchanged.
- **SC-003**: 100% of license-number-reset events are observably isolated from booking/schedule state — zero bookings are cancelled or flagged as a result of this reset alone, verified by comparing booking state immediately before and after.
- **SC-004**: A doctor whose verification was just reset by this rule is absent from discovery-eligibility queries (007's data-layer guarantee) in 100% of cases, immediately after the reset.
- **SC-005**: 100% of edit attempts by a non-Super-Admin actor are rejected, with zero state changes.

## Assumptions

- Contact info (email/mobile) lives on `Account`, not `DoctorProfile` (004/007's data model) — it is out of scope for this feature's edit action, which only ever touches `DoctorProfile`'s own fields (specialization, license number, experience years, visibility).
- This feature resolves 007's deliberately-deferred question of who may write the `visible` toggle after creation — the answer is Super Admin, via this feature's edit action, rather than a separate doctor-facing settings screen.
- This feature does not introduce any new notification to the doctor or Super Admin when a reset occurs — the source material describes only the reset itself, not an alerting mechanism; Super Admin already has 005's pending-verification worklist as the discovery mechanism for profiles awaiting (re-)verification.
- "Same transaction as the edit" (FR-003) means the edit's field update and the `licenseVerified` reset are committed atomically — if either fails, neither is persisted, consistent with this project's existing atomicity pattern for other multi-field writes (e.g., 004's onboarding transaction).
