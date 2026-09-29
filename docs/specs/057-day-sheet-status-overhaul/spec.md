# Feature Specification: Day Sheet Smart Status Flow

**Feature Branch**: `057-day-sheet-status-overhaul`

**Created**: 2026-09-21

**Status**: Draft

**Input**: User description: "Day Sheet slot status overhaul — replace the 'Mark complete' button with a smart status flow to reduce human error and give doctors direct visibility/control. New 'Appeared' status between Booked and Completed; automatic completion once a slot's scheduled time ends for an Appeared slot; No-Show detection unchanged in timing but now conditioned on whether the slot was marked Appeared; doctors gain access to mark a slot Completed but must not see or access Appeared/No-Show; per-row inline Cancel is replaced by a checkbox-based select-one/select-multiple/select-all cancellation UI that must reuse the existing cancellation business rules (individual, whole-day, partial cutoff-based)."

## Clarifications

### Session 2026-09-21

- Q: Once the automatic No-Show sweep has already marked a slot NO_SHOW, can staff still correct it (e.g. mark it Appeared/Completed) if the patient genuinely showed up late? → A: Correctable — staff can still mark a No-Show slot Appeared (and it then follows the normal Appeared → Completed flow) if the patient does show up after the sweep already fired.
- Q: What does "cancel all" mean in the new checkbox selection UI? → A: A bulk-select convenience only — "select all" checks every eligible box on the current Day Sheet view, and each selected slot is cancelled under the exact same per-slot rule as a single cancellation (FR-012). It does not invoke the separate, existing whole-day-cancellation feature or that feature's distinct rules.
- Q: Can Doctors use the new checkbox-based cancel-selection UI at all? → A: No — cancellation (in any form, including the new selection UI) stays ClinicAdmin/Operations-only, unchanged from today. Doctors' access remains limited to marking their own Appeared slots Completed (User Story 2).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Slots complete themselves once the visit is over (Priority: P1)

As a ClinicAdmin or Operations staff member running the front desk, once I've marked a patient as having arrived and gone in to see the doctor, I don't want to have to remember to come back and click "Mark complete" after their slot's scheduled time passes — the system should do that for me, so a forgotten click never leaves a slot stuck showing as still in progress.

**Why this priority**: This is the core "reduce human error" driver behind this feature — it's the change that removes a manual step staff currently must remember to perform for every single visit, all day, every day.

**Independent Test**: Mark a Booked slot as Appeared, wait until (or simulate) its scheduled end time passing, and confirm it transitions to Completed with no manual action, while a slot never marked Appeared still follows the existing automatic No-Show behavior instead.

**Acceptance Scenarios**:

1. **Given** a Booked Fixed-Time slot whose patient has arrived, **When** front-desk staff mark the slot "Appeared", **Then** the slot's status changes to Appeared and it stops being eligible for automatic No-Show detection.
2. **Given** a slot marked Appeared, **When** the slot's scheduled time has passed, **Then** the system automatically marks it Completed without any staff action, and any delay/lateness figures for that session update the same way they do for a manual completion today.
3. **Given** a Booked slot whose patient never arrives, **When** the existing automatic No-Show grace period elapses, **Then** the slot is marked No-Show exactly as it is today — this feature does not change that timing or behavior for a slot that was never marked Appeared.
4. **Given** a slot already marked Completed (whether manually or automatically), **When** the automatic completion sweep next runs, **Then** it does not touch that slot again.

---

### User Story 2 - Doctors can confirm a visit is done from their own screen (Priority: P2)

As a treating doctor, I want to be able to mark a visit Completed myself once I've finished with a patient, without needing a ClinicAdmin or Operations staff member to do it for me, so my own consultations don't sit "in progress" longer than necessary — but I don't need or want to see front-desk-only actions like Appeared or No-Show cluttering my screen.

**Why this priority**: Directly requested to smooth day-to-day operations, but depends on User Story 1's status model existing first (a doctor completing a slot early is a manual override of the same status this feature introduces).

**Independent Test**: Sign in as a treating doctor and confirm the Completed action is available on their own booked/appeared slots, while Appeared and No-Show controls or labels are absent anywhere in their view; sign in as ClinicAdmin/Operations and confirm nothing they can do today has been removed.

**Acceptance Scenarios**:

1. **Given** a slot in Appeared status for their own patient, **When** a treating doctor views their Day Sheet, **Then** they can mark that slot Completed directly.
2. **Given** any slot in any status, **When** a treating doctor views their Day Sheet, **Then** no "Appeared" or "No-Show" action or label is visible or reachable anywhere in their UI.
3. **Given** a ClinicAdmin or Operations staff member, **When** they view the Day Sheet, **Then** they retain every action available to them today (marking Appeared, marking Completed) — nothing is removed from their access.

---

### User Story 3 - Cancelling slots without a cluttered per-row button (Priority: P3)

As a ClinicAdmin or Operations staff member, I want to select one, several, or all eligible slots on the Day Sheet and cancel them together in one action, instead of a "Cancel" link repeated on every single booked row, so the Day Sheet reads cleaner and cancelling several slots at once (e.g. a doctor calling in sick) doesn't mean clicking Cancel over and over.

**Why this priority**: Explicitly requested, but lowest priority of the three — it's a workflow/UI cleanup rather than the error-reduction goal driving User Story 1, and it doesn't block either of the other two stories.

**Independent Test**: Select a single eligible slot via its checkbox and cancel it; select several eligible slots and cancel them together; confirm the same underlying cancellation rules (e.g. a waitlist offer being triggered) apply exactly as they do for today's single-slot cancellation, and that a completed, no-showed, or already-cancelled slot cannot be selected.

**Acceptance Scenarios**:

1. **Given** the Day Sheet view, **When** staff view a row for a Booked or Appeared slot, **Then** a selection checkbox is available and the old inline per-row "Cancel" action is gone.
2. **Given** one or more eligible slots selected via checkbox, **When** staff confirm cancellation, **Then** every selected slot is cancelled following this system's existing cancellation rules for that kind of slot (e.g. a resulting waitlist offer where one would already be triggered today).
3. **Given** a slot that is Completed, No-Show, or already Cancelled, **When** staff view the Day Sheet, **Then** that row offers no selection checkbox.

---

### Edge Cases

- What happens if staff mark a slot Appeared after its automatic No-Show grace period has already elapsed and the sweep has already run? Per Clarifications, this is allowed — the slot moves to Appeared and then follows the same automatic-completion behavior as any other Appeared slot.
- What happens if the automatic completion sweep and a manual "mark Completed" click (from staff or doctor) happen at nearly the same time for the same slot? The slot must end up Completed exactly once, never in an inconsistent or duplicate state.
- What happens to a slot's automatic completion if its parent session or schedule is edited or deleted after the slot was marked Appeared but before its scheduled end time? The existing non-retroactive schedule-edit and session-deletion rules already govern this and are not changed by this feature.
- What happens if staff attempt to select a slot that another staff member cancels or completes in the moment between selection and confirming the bulk cancel? The action must fail safely for that slot (reporting which slots succeeded and which did not) rather than silently cancelling a slot in an unexpected state.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST support a new "Appeared" status for a Booked Fixed-Time slot, entered only by explicit staff action (ClinicAdmin or Operations), marking that the patient has arrived and is with the doctor.
- **FR-002**: A slot marked Appeared MUST no longer be eligible for automatic No-Show detection, exactly as leaving Booked status already does today.
- **FR-003**: A Booked Fixed-Time slot that is never marked Appeared MUST continue to be automatically marked No-Show under the exact same timing rule that applies today, unchanged by this feature.
- **FR-003a**: A slot already marked No-Show MUST still be correctable to Appeared by staff (e.g. the patient arrived late, after the automatic sweep already ran) — No-Show is not a locked, final state under this feature.
- **FR-004**: The system MUST automatically mark an Appeared slot Completed once that slot's own scheduled time has passed, with no staff action required.
- **FR-005**: Automatic completion (FR-004) MUST produce the same downstream effects a manual completion produces today (e.g. the session's delay/lateness figure is recalculated the same way).
- **FR-006**: A treating doctor MUST be able to mark their own Appeared slot Completed directly, in addition to ClinicAdmin/Operations retaining that same ability.
- **FR-007**: The "Appeared" and "No-Show" actions and labels MUST NOT be visible or reachable anywhere in a doctor's Day Sheet view.
- **FR-008**: ClinicAdmin and Operations staff MUST retain every action available to them today (marking a slot Appeared, marking a slot Completed) — this feature is additive to their access, not a reduction of it.
- **FR-009**: The Day Sheet MUST offer a selection checkbox on every Booked or Appeared slot row, allowing ClinicAdmin/Operations staff to select one, several, or all eligible slots at once for cancellation. A "select all" control MUST simply check every eligible box currently visible on the Day Sheet — it is a bulk-select convenience, not an invocation of this system's separate whole-day-cancellation feature and its distinct rules.
- **FR-010**: The existing inline per-row "Cancel" action on a Booked slot MUST be removed in favor of the checkbox-based selection described in FR-009.
- **FR-011**: A slot that is Completed, No-Show, or already Cancelled MUST NOT offer a selection checkbox — only slots eligible for cancellation today may be selected.
- **FR-012**: Cancelling a selection of one or more slots MUST apply this system's existing cancellation business rules to each affected slot (the same rules and side effects — such as a resulting waitlist offer — that apply to a single-slot cancellation today), rather than introducing a separate, simplified cancellation path.
- **FR-013**: This feature applies to Fixed-Time slots only, matching the existing scope of the manual completion and automatic No-Show actions it replaces or extends — Queue/Token-mode slots are unaffected.
- **FR-014**: If a slot in a selected cancellation batch can no longer be cancelled by the time the action is processed (e.g. another staff member already completed or cancelled it), the system MUST report that specific failure without silently skipping it or blocking the slots that could still be cancelled.
- **FR-015**: The cancel-selection UI (FR-009) MUST NOT be visible or reachable in a doctor's Day Sheet view — cancellation in any form remains ClinicAdmin/Operations-only, matching today's authorization.

### Key Entities

- **Slot (Fixed-Time)**: Gains "Appeared" as a new state in its existing status lifecycle, sitting between Booked and Completed. Its existing scheduled start/end time continues to drive both the (unchanged) No-Show grace window and the new automatic-completion timing.
- **Day Sheet selection**: A transient, staff-chosen set of one or more eligible slots on the currently viewed Day Sheet, used only to apply the existing cancellation action to each member of the set — it does not introduce a new persisted concept beyond the slots themselves.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A slot marked Appeared reaches Completed status with zero manual "mark complete" clicks required, in 100% of cases where its scheduled time passes without staff intervening first.
- **SC-002**: Doctors can complete their own visits from their own screen without asking front-desk staff to do it on their behalf, for 100% of their Appeared slots.
- **SC-003**: A doctor's Day Sheet view never displays an Appeared or No-Show action or label, verified across every screen a doctor can reach.
- **SC-004**: Staff can cancel 5 slots in one action in under 15 seconds, compared to 5 separate individual cancellations today.
- **SC-005**: Every cancellation performed through the new selection UI produces identical downstream effects (e.g. waitlist offers) to today's single-slot cancellation, with zero cases of a cancellation silently skipping an existing business rule.

## Assumptions

- The automatic No-Show grace period (currently 10 minutes past a slot's scheduled start) is unchanged by this feature — it now simply also depends on whether the slot was marked Appeared in that window, rather than only on its Booked status.
- Automatic completion is evaluated against each slot's own already-recorded scheduled end time, the same per-slot time data the existing automatic No-Show sweep already reads.
- Only ClinicAdmin/Operations staff mark a slot Appeared (matching FR-007's doctor restriction) — this reflects a front-desk check-in workflow, with the doctor's own action limited to Completed. Cancellation (FR-015) follows the same ClinicAdmin/Operations-only pattern.
- The Day Sheet's new checkbox selection, including "select all", is scoped to the slots visible in the currently viewed day, consistent with how the Day Sheet already presents one day at a time — it is a UI convenience over the existing per-slot cancellation rule, not a trigger for the separate whole-day-cancellation feature.
- A bulk cancellation still requires whatever justification/reason today's single-slot cancellation requires, applied per selected slot — this feature does not relax that requirement.
- This feature does not change consultation notes, prescriptions, or any clinical documentation behavior; it only changes slot status transitions and the Day Sheet's cancellation UI.
