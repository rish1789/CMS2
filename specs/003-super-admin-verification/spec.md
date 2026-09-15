# Feature Specification: Super Admin Clinic Verification

**Feature Branch**: `003-super-admin-verification`

**Created**: 2026-09-02

**Status**: Draft

**Input**: User description: "Super Admin Clinic Verification — a Super Admin reviews and verifies (or un-verifies) a clinic, so only legitimate clinics become publicly discoverable. Verification is fully manual and off-platform (no document upload, no structured review workflow) — Super Admin just flips a verified flag via an admin screen listing pending clinics. Verified is enforced at the data-query level for discovery, not response filtering. Only Super Admin may perform this action. Un-verifying triggers a de-verification cascade (008) as a separate concern. (Full source: backlog/002-super-admin-clinic-verification.md)"

## Clarifications

### Session 2026-09-02

- Q: How is the Super Admin identity represented and authenticated, given no prior feature creates one and 001's `RoleAssignment` model requires a clinic? → A: Option C — no first-class `Account`/database identity for v1. A single Super Admin identity is bootstrapped from configuration (environment-provided credentials), not stored as a row in any table. Authenticated via HTTP Basic Auth checked against the configured credentials, re-validated on every request (stateless, no session/token).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Super Admin Verifies a Pending Clinic (Priority: P1)

A Super Admin reviews a newly registered clinic's legitimacy through external means, then marks it verified in an admin screen — making it eligible for public discovery.

**Why this priority**: This is the entire point of the feature and the gate every clinic must pass before it's discoverable at all — without it, 035-public-discovery-search has nothing legitimate to show.

**Independent Test**: Register a clinic (001), confirm it appears in a pending-verification list, mark it verified, and confirm `Clinic.verified` becomes `true`.

**Acceptance Scenarios**:

1. **Given** an unverified clinic exists, **When** the Super Admin opens the pending-verification list, **Then** the clinic appears with its registration details (name, address, contact info).
2. **Given** the Super Admin has reviewed a clinic externally and approves it, **When** they mark it verified, **Then** `Clinic.verified` becomes `true`.
3. **Given** a non-Super-Admin user (ClinicAdmin, Doctor, Operations), **When** they attempt to call the verify action, **Then** the request is rejected as unauthorized.

---

### User Story 2 - Super Admin Un-Verifies a Clinic (Priority: P2)

A Super Admin revokes a previously verified clinic's status — for example, after learning it's no longer legitimate.

**Why this priority**: Secondary to initial verification (a clinic must be verified before it can be un-verified), but necessary for the "act if a clinic later loses legitimacy" half of the feature's own user story.

**Independent Test**: Starting from a verified clinic, un-verify it and confirm `Clinic.verified` becomes `false`.

**Acceptance Scenarios**:

1. **Given** a verified clinic, **When** the Super Admin un-verifies it, **Then** `Clinic.verified` becomes `false` and it is immediately excluded from public discovery data queries (not just filtered from a response).
2. **Given** a clinic is un-verified, **When** the action completes, **Then** the de-verification cascade (008-deverification-cascade-auto-cancel-bookings) is triggered as a separate, independently-owned concern — this feature is responsible only for the toggle itself and for triggering that downstream process, not for the cascade's own logic.

---

### Edge Cases

- What happens when Super Admin attempts to verify a clinic that's already verified? → No-op / idempotent success (already in the desired state); no error.
- What happens when Super Admin attempts to un-verify a clinic that's already unverified? → Same — idempotent, no error.
- What happens when the verify/un-verify action is called by an authenticated user with no Super Admin role at all? → Rejected as unauthorized, with no state change.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST provide a way for a Super Admin to list clinics pending verification (`verified = false`), showing each clinic's registration details.
- **FR-002**: System MUST allow a Super Admin to mark a clinic as verified (`verified: false → true`).
- **FR-003**: System MUST allow a Super Admin to un-verify a previously verified clinic (`verified: true → false`).
- **FR-004**: System MUST restrict the verify/un-verify action (and the pending-list read) to requests authenticated with the configured Super Admin credentials — any other or missing credentials, including a valid staff Account (ClinicAdmin/Doctor/Operations, even for their own clinic), MUST be rejected as unauthorized. There is no database-backed Super Admin identity in v1 — see Clarifications.
- **FR-009**: The Super Admin credentials MUST be supplied via configuration/environment, never hardcoded in source, and MUST NOT be persisted to any database table.
- **FR-005**: System MUST enforce `Clinic.verified` at the data-query level for public discovery (035) — an unverified clinic MUST be excluded from the underlying query, not merely filtered out of a response after the fact.
- **FR-006**: System MUST NOT provide any document upload capability or structured review-queue workflow as part of verification — the action is a simple flag toggle following an entirely off-platform review.
- **FR-007**: System MUST treat verify and un-verify as idempotent — calling either action on a clinic already in the target state MUST succeed without error and MUST NOT change state further.
- **FR-008**: System MUST trigger the de-verification cascade (008-deverification-cascade-auto-cancel-bookings) whenever a clinic transitions from verified to unverified — this feature owns only the toggle and the triggering of that process, not the cascade's own behavior.

### Key Entities

- **Clinic** (from 001): this feature reads and updates its existing `verified` boolean field only — no new fields introduced.
- **Super Admin**: not a stored entity in v1 — a single configuration-bootstrapped credential pair, checked per-request via HTTP Basic Auth (see Clarifications). No table, no row, no relationship to `Account`.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of clinics with `verified = false` appear in the Super Admin's pending list until acted on.
- **SC-002**: 100% of verify/un-verify attempts by a non-Super-Admin actor are rejected, with zero state changes.
- **SC-003**: 100% of un-verified clinics are absent from public discovery data queries immediately after the action completes — verified by querying discovery data directly, not just through the discovery API surface.
- **SC-004**: Repeating a verify or un-verify action on a clinic already in that state succeeds idempotently in 100% of attempts, with no duplicate side effects (e.g., the de-verification cascade fires once per true→false transition, not on every repeated un-verify call).

## Assumptions

- This feature triggers the de-verification cascade (008) but does not implement its logic — 008 is a separate, dependent feature.
- "Data-query level" enforcement (FR-005) means the discovery feature's own queries filter on `verified = true` at the database layer; this feature is responsible for keeping the flag itself correct and does not implement the discovery query — that's 035's job.
- A single, shared Super Admin credential is sufficient for v1 (matches the source material's "Super Admin ... platform-wide" framing, with no mention of multiple distinct Super Admin operators or individual accountability between them). If multiple, individually-identifiable Super Admins are needed later, that's a follow-up decision — not addressed here.
- HTTP Basic Auth (stateless, credential re-sent every request) is an acceptable mechanism for this low-frequency, internal-only admin action; no session/token infrastructure is introduced for it.
