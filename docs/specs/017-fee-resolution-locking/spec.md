# Feature Specification: Fee Resolution & Locking at Booking Time

**Feature Branch**: `017-fee-resolution-locking`

**Created**: 2026-09-03

**Status**: Draft

**Input**: User description: "Fee Resolution & Locking at Booking Time — resolves the correct fee for a doctor's appointment type in strict order (appointment-type fee override, else the doctor's default fee, else hard-block with no booking created), as a reusable capability a future booking-creation feature (016/017/018, not yet built) will call and snapshot onto its own Booking record. This feature itself has no Booking entity to lock a fee onto or toggle payment status on — those are the future booking features' own scope, per build-order.md's placement of them strictly after this one. This feature defines the doctor-scoped AppointmentType (name + optional fee override) and a doctor's optional default fee, and the resolution algorithm over them. (Full source: backlog/015-fee-resolution-and-locking.md)"

## Scope Decisions Made During Drafting

No prior conversation turn resolved these — they're research-backed defaults made while writing this spec, not a `/speckit-clarify` session (which runs next and may revisit any of them).

- **No `Booking` entity exists yet, and this feature does not create one.** build-order.md places 016/017/018 (the booking-creation features that will actually call this feature's resolution capability and snapshot its result) strictly *after* this feature. The source acceptance criteria describing "the booking's locked fee" and "a completed booking's `paymentStatus`" describe those *future* features' own responsibility — this feature builds exactly the resolution algorithm and its supporting configuration data those features will call, the same "build the generic capability now, wire it in later" pattern already applied repeatedly in this backlog (e.g. 036's notification pipeline, 011's session generation).
- **A doctor's default fee and appointment-type fee overrides are new configuration data owned by this feature, in a new `com.cms.booking` module** (the backlog's own listed module for this feature, distinct from `com.cms.scheduling`) — reading `DoctorProfile` (005) as a read-only cross-module reference, never writing to it, so this new module owns all of its own mutable state (Constitution Principle III).
- **An Appointment Type is scoped to a Doctor (global), not to a Clinic** — mirroring `DoctorProfile`'s own existing global (not per-clinic) scoping (007), since nothing in the source material ties an appointment type or its fee to a specific clinic, and a doctor's own naming of their appointment types (e.g. "Follow-up," "New Patient") is a property of the doctor, not of any one clinic they happen to work at.
- **A booking is assumed to always specify which Appointment Type it's for.** Every acceptance criterion in the source material is phrased "for that appointment type" — there is no scenario describing a booking with no appointment type at all. This feature's resolution capability therefore requires an Appointment Type id as input, not an optional one.
- **This feature ships no frontend UI.** Unlike 013 (a clearly-named actor doing a clearly-described action with obvious UI placement), the source material names no specific actor or screen for configuring appointment types/default fees, and the real shape of that configuration UI is more sensibly designed once an actual booking flow (016/017/018) exists to show what "appointment type" needs to look like end-to-end. This feature ships a service-and-endpoint-only contract those future features (or a dedicated later configuration feature) will build a UI against.
- **Configuration (creating Appointment Types, setting a doctor's default fee) is authorized the same way 009/013 already authorize doctor/clinic-scoped scheduling actions**: the Doctor themselves, or an active ClinicAdmin at any clinic where that doctor currently holds an active Role Assignment — reusing the existing `RoleAssignmentRepository` signal rather than inventing a new authorization concept, since no clinic is intrinsic to this global, doctor-scoped configuration data.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Fee Resolves in Strict Order, With a Hard Block When Nothing Is Configured (Priority: P1)

Given a doctor and an appointment type, the system resolves the fee that would apply: the appointment type's own fee override if it has one, otherwise the doctor's default fee, otherwise a hard block (no fee, no booking possible) — never a silent default or a guessed amount.

**Why this priority**: This is the entire reason the feature exists — the exact, business-critical resolution order and the hard-block behavior (previously unspecified, now a confirmed rule) that every future booking-creation feature must get right.

**Independent Test**: Configure a doctor with various combinations of appointment-type overrides and a default fee (or its absence); call the resolution capability directly for each appointment type; confirm the returned fee (or block) matches the documented order exactly.

**Acceptance Scenarios**:

1. **Given** an Appointment Type with a fee override configured, **When** the fee is resolved for that appointment type, **Then** the resolved fee equals the override amount, regardless of whether the doctor also has a default fee.
2. **Given** an Appointment Type with no override, and the doctor has a default fee, **When** the fee is resolved, **Then** the resolved fee equals the doctor's default fee.
3. **Given** an Appointment Type with no override, and the doctor has no default fee, **When** the fee is resolved, **Then** the resolution is rejected/blocked with a clear reason — no fee is returned.
4. **Given** an Appointment Type belonging to a different doctor than the one named, **When** the fee is resolved, **Then** it is rejected as not found — an appointment type is never resolved against the wrong doctor.

---

### User Story 2 - A Doctor's Fee Configuration Is Managed Explicitly (Priority: P2)

A Doctor (or a ClinicAdmin at a clinic they work at) creates named Appointment Types for that doctor, each with an optional fee override, and sets or updates the doctor's own default fee.

**Why this priority**: Without a way to configure this data at all, User Story 1's resolution logic has nothing real to resolve against — but the resolution algorithm itself (US1) is the feature's actual value; this is the supporting configuration surface.

**Independent Test**: As the doctor (or their clinic's ClinicAdmin), create an Appointment Type with a fee override; separately set the doctor's default fee; list the doctor's Appointment Types and confirm both are retrievable exactly as configured.

**Acceptance Scenarios**:

1. **Given** an authenticated Doctor, **When** they create an Appointment Type for themselves with a name and a fee override, **Then** it is saved and appears in their list of Appointment Types.
2. **Given** an authenticated ClinicAdmin at a clinic where a Doctor is actively staffed, **When** they create an Appointment Type for that doctor, **Then** it succeeds identically to the doctor creating it themselves.
3. **Given** an authenticated Doctor, **When** they set their own default fee, **Then** it is saved and is used by resolution for any of their Appointment Types with no override.
4. **Given** a staff member who is neither the named doctor nor an active ClinicAdmin at any clinic that doctor works at, **When** they attempt to create an Appointment Type or set the default fee for that doctor, **Then** the request is rejected as forbidden.

---

### Edge Cases

- What happens when a doctor's default fee is set, then later changed? → Only affects future resolutions from that point forward — this feature has no Booking to retroactively re-price, so "locking" (the source material's own framing) is entirely a property of how a future booking feature uses this feature's output, not of anything this feature stores or mutates itself.
- What happens when an Appointment Type's fee override is removed after being set? → A future resolution for that Appointment Type falls through to the doctor's default fee (or blocks, per the normal order) — the override is simply a nullable field being cleared, not a special case.
- What happens when a doctor has multiple Appointment Types, some with overrides and some without? → Each is resolved independently, according to its own override presence/absence — no cross-appointment-type interaction.
- What happens when the named doctor doesn't exist at all? → Rejected as not found, identically to how 009's create/edit endpoints reject an unknown doctor.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST provide a fee-resolution capability that, given a Doctor and one of that Doctor's Appointment Types, returns the Appointment Type's fee override if one is set.
- **FR-002**: If the Appointment Type has no fee override, the resolution capability MUST return the Doctor's default fee, if one is set.
- **FR-003**: If neither a fee override nor a default fee is available, the resolution capability MUST reject the request with a clear reason — it MUST NOT return a fee of any kind (zero, null-as-free, or otherwise).
- **FR-004**: The resolution capability MUST reject a request naming an Appointment Type that does not belong to the named Doctor.
- **FR-005**: System MUST allow an authenticated Doctor to create Appointment Types (name + optional fee override) for themselves, and MUST allow an active ClinicAdmin at any clinic where that Doctor currently holds an active Role Assignment to do the same on that Doctor's behalf.
- **FR-006**: System MUST reject an Appointment Type creation (or default-fee update) attempt from a caller who is neither the named Doctor nor an active ClinicAdmin at any clinic that Doctor is actively staffed at.
- **FR-007**: System MUST allow the same two actor types (FR-005) to set or update a Doctor's own default fee.
- **FR-008**: System MUST provide a way to list a Doctor's Appointment Types.
- **FR-009**: This feature MUST NOT create, modify, or reference any Booking or payment-status record — those belong to a separate, not-yet-built booking-creation feature.

### Key Entities

- **AppointmentType** (new entity, first defined by this feature): a Doctor-scoped named category of appointment (e.g. "Follow-up"). Fields: the Doctor it belongs to, a name, an optional fee override amount.
- **DoctorDefaultFee** (new entity, first defined by this feature): a Doctor's own fallback fee, used when an Appointment Type has no override of its own. Fields: the Doctor it belongs to (one per Doctor), an amount.
- **DoctorProfile** (from 005/007, read-only here): the Doctor this feature's configuration data is scoped to.
- **RoleAssignment** (from 004, read-only here): supplies the authorization signal (FR-005/FR-006) — whether the caller is the named Doctor or an active ClinicAdmin at a clinic that Doctor is staffed at.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of fee resolutions for an Appointment Type with an override return exactly that override amount, regardless of any default fee also present.
- **SC-002**: 100% of fee resolutions for an Appointment Type with no override, where the Doctor has a default fee, return exactly that default fee.
- **SC-003**: 100% of fee resolutions where neither an override nor a default fee exists are rejected — zero resolutions silently succeed with a fabricated or zero amount.
- **SC-004**: 100% of Appointment Type/default-fee configuration attempts by an actor outside the two authorized types are rejected as forbidden.
- **SC-005**: 100% of this feature's operations, across every tested scenario, create zero rows in any table other than `AppointmentType` and `DoctorDefaultFee`.

## Assumptions

- **No Booking entity exists yet anywhere in this codebase** (016/017/018 are later in build order and unbuilt) — FR-009/SC-005 are a forward-looking guarantee about this feature's own scope.
- **Fee amounts are flat cash values with no currency-conversion, insurance, or discount logic** — explicitly out of scope per the source material.
- **No frontend is built by this feature** (see Scope Decisions) — its contract is a service/REST surface for 016/017/018 (or a dedicated future configuration UI feature) to build against.
- **A doctor has at most one default fee at a time** — setting it again replaces the prior value; there is no history or per-clinic variation of a doctor's default fee in this feature's scope.
