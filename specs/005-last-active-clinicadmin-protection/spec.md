# Feature Specification: Last Active ClinicAdmin Protection

**Feature Branch**: `005-last-active-clinicadmin-protection`

**Created**: 2026-09-02

**Status**: Draft

**Input**: User description: "Last Active ClinicAdmin Protection — a clinic's last active ClinicAdmin can never be deactivated or removed, so a clinic can never end up without an administrator. No override exists for any role, including Super Admin. This check runs at the API/data layer, not just the UI. (Full source: backlog/007-last-active-clinicadmin-protection.md)"

## Clarifications

### Session 2026-09-02

- Q: No prior feature builds any staff-deactivation capability at all — what does this feature actually guard? → A: This feature builds the (previously unscoped) staff Role Assignment deactivation action itself — a ClinicAdmin deactivating a Doctor/Operations/ClinicAdmin Role Assignment at their own clinic — with the last-active-ClinicAdmin protection as its core, non-overridable rule. Without this, the "protection" would have nothing to guard.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - ClinicAdmin Deactivates a Doctor or Operations Staff Member (Priority: P1)

A ClinicAdmin deactivates a Doctor's or Operations staff member's Role Assignment at their own clinic — the normal, unrestricted case.

**Why this priority**: This is the baseline capability the protection rule (US2) sits on top of — without a working deactivation action at all, there's nothing to protect.

**Independent Test**: As a ClinicAdmin, deactivate a Doctor or Operations Role Assignment at your clinic; confirm it becomes inactive and the affected Account can no longer authenticate for staff actions at that clinic.

**Acceptance Scenarios**:

1. **Given** an active Doctor or Operations Role Assignment at a clinic, **When** the clinic's ClinicAdmin deactivates it, **Then** the Role Assignment becomes inactive.
2. **Given** a non-ClinicAdmin (Doctor, Operations) or an unauthenticated request, **When** they attempt to deactivate any Role Assignment, **Then** the request is rejected as unauthorized.
3. **Given** a ClinicAdmin attempts to deactivate a Role Assignment belonging to a different clinic, **When** the request is made, **Then** it is rejected — a ClinicAdmin may only act on their own clinic's staff.

---

### User Story 2 - Last Active ClinicAdmin Cannot Be Deactivated (Priority: P1)

An attempt to deactivate a clinic's last remaining active ClinicAdmin is blocked, regardless of who attempts it.

**Why this priority**: This is the feature's entire reason for existing — the invariant that a clinic can never end up with zero active administrators. Equal priority to US1 since the two are inseparable in practice (given no other feature ever creates a second ClinicAdmin — see Assumptions — this is the scenario that will actually occur every time deactivation targets a ClinicAdmin).

**Independent Test**: Attempt to deactivate a clinic's sole active ClinicAdmin's Role Assignment; confirm the action is rejected with an explanatory error and the Role Assignment remains active.

**Acceptance Scenarios**:

1. **Given** a clinic with exactly one active ClinicAdmin, **When** anyone attempts to deactivate that ClinicAdmin's Role Assignment, **Then** the action is rejected with an explanatory error, and the Role Assignment remains active.
2. **Given** the last remaining active ClinicAdmin, **When** a Super Admin attempts the deactivation, **Then** it is still blocked — Super Admin has no override.
3. **Given** a clinic with two active ClinicAdmin Role Assignments (a state no current feature can actually produce, but not structurally forbidden), **When** one is deactivated, **Then** the action succeeds and the other remains active.

---

### Edge Cases

- What happens if the same deactivation request is submitted twice for a Role Assignment already inactive? → Idempotent success (already in the desired state); no error, no duplicate side effects.
- What happens when checking "is this the last active ClinicAdmin" — is the check scoped per clinic? → Yes; "last active ClinicAdmin" is evaluated per clinic, since ClinicAdmin Role Assignments are clinic-scoped (a person could in principle hold ClinicAdmin at multiple clinics, though no current feature creates that either).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST allow an authenticated ClinicAdmin to deactivate a Role Assignment (of any role: Doctor, Operations, or ClinicAdmin) belonging to their own clinic.
- **FR-002**: System MUST restrict deactivation to an authenticated ClinicAdmin acting on their own clinic's Role Assignments — any other role, an unauthenticated request, or a cross-clinic attempt MUST be rejected.
- **FR-003**: Before deactivating a `ClinicAdmin`-role Role Assignment, System MUST check whether it is the target clinic's last active ClinicAdmin.
- **FR-004**: System MUST reject the deactivation, with a clear, specific error, if it would leave the clinic with zero active ClinicAdmin Role Assignments — with no override for any role, including Super Admin.
- **FR-005**: System MUST allow deactivating one ClinicAdmin Role Assignment when at least one other active ClinicAdmin Role Assignment remains at that clinic afterward.
- **FR-006**: This protection check MUST run at the API/data layer — it MUST NOT be enforceable-bypassable by skipping a UI step.
- **FR-007**: System MUST treat deactivation as idempotent — deactivating an already-inactive Role Assignment MUST succeed without error and MUST NOT change state further.
- **FR-008**: The "last active ClinicAdmin" check MUST be scoped per clinic — a ClinicAdmin Role Assignment at one clinic MUST NOT be counted toward or against the protection at a different clinic.

### Key Entities

- **Role Assignment** (from 001/004): this feature adds the deactivation action (`active: true → false`) to this existing entity — no new fields.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of attempts to deactivate a clinic's last active ClinicAdmin are rejected, regardless of the caller's role — including Super Admin.
- **SC-002**: 100% of Doctor/Operations (and non-last ClinicAdmin) deactivation attempts by the clinic's own ClinicAdmin succeed.
- **SC-003**: 100% of cross-clinic or unauthorized deactivation attempts are rejected, with zero state changes.
- **SC-004**: Repeating a deactivation call against an already-inactive Role Assignment succeeds idempotently in 100% of attempts.

## Assumptions

- No other feature in this backlog creates a second ClinicAdmin Role Assignment for a clinic (004's onboarding flow explicitly restricts new hires to Doctor/Operations only). In practice, this means every clinic has exactly one ClinicAdmin, permanently, for the life of the system as currently scoped — this feature's protection rule (US2) will, in practice, block every ClinicAdmin-deactivation attempt that ever occurs. This is confirmed-intended per the backlog's own note, not a bug: if a clinic's founding admin needs to be replaced, that is handled entirely outside the system in v1.
- Deactivation is performed by the clinic's own ClinicAdmin (mirroring 004's onboarding authorization pattern) — the source material doesn't describe a Super Admin-initiated staff deactivation path, so none is built here.
