# Feature Specification: Day Sheet Hardening

**Feature Branch**: `042-day-sheet-hardening`

**Created**: 2026-09-09

**Status**: Draft

**Input**: User description: "Day Sheet page (staff console) improvements — a defect/hardening pass on the already-shipped Day Sheet feature (041-staff-console-pickers), found during a manual UX/code/data audit: no confirmation on whole-session cancellation; destructive session actions visually equal to routine ones; the shared 'More' action menu has the same two bugs already fixed once on the Roster page (no cross-row exclusivity, off-screen clipping near the bottom of the viewport); the session detail header silently loses the doctor name/date on refresh or a direct link because it isn't in the API response; the page still uses the pre-redesign visual language while Roster has moved to a newer one; the session list has no pagination, no doctor filter, and no doctor grouping — confirmed that one doctor's schedule already produces 9 sessions in a 14-day window, so a clinic with many doctors would return everything in one flat, unfiltered, ungrouped response; the underlying `session` table has no index supporting the clinic+date query the list actually runs; and session cards carry no at-a-glance indication of how full a session is."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Confirm before cancelling a whole session (Priority: P1)

A ClinicAdmin or Operations staff member opens a session's detail page intending to cancel it, but wants a chance to back out of an irreversible, patient-facing action before it happens.

**Why this priority**: This is the only destructive, irreversible action anywhere in the staff console that currently has zero confirmation step. A single accidental click cancels every active booking in a session with no way to undo it and no chance to reconsider.

**Independent Test**: Can be fully tested by clicking "Cancel whole session," verifying a confirmation step appears with no booking yet cancelled, clicking Cancel on that step and verifying nothing changed, then repeating and clicking Confirm and verifying the cancellation proceeds exactly as before.

**Acceptance Scenarios**:

1. **Given** a session with active bookings, **When** staff click "Cancel whole session," **Then** the system shows a confirmation step describing the consequence before taking any action.
2. **Given** the confirmation step is showing, **When** staff click Cancel, **Then** no booking is cancelled and the session is unchanged.
3. **Given** the confirmation step is showing, **When** staff click Confirm, **Then** the cancellation proceeds exactly as it does today (same endpoint, same result, same success message).

---

### User Story 2 - Find one doctor's sessions in a busy, multi-doctor clinic (Priority: P1)

A staff member working with one specific doctor today needs to find that doctor's sessions without scanning past every other doctor's interleaved sessions in the same 14-day list.

**Why this priority**: Confirmed against real data that a single doctor's schedule already produces 9 sessions in the 14-day window; a clinic with more doctors compounds this into one long, unfiltered, chronologically-interleaved list with no way to narrow it down. This is the most severe usability gap found — it gets worse, not better, as the clinic's staff grows, which is exactly when the tool matters most.

**Independent Test**: Can be fully tested by loading the Day Sheet for a clinic with sessions from multiple doctors, selecting one doctor from a filter, and verifying only that doctor's sessions remain visible while the rest of the app is untouched.

**Acceptance Scenarios**:

1. **Given** a clinic with sessions from more than one doctor in the 14-day window, **When** staff open the Day Sheet, **Then** they can filter the list down to a single doctor.
2. **Given** the list is filtered to one doctor, **When** staff view the results, **Then** every visible session belongs to that doctor and the total count reflects only that doctor's sessions.
3. **Given** a large number of sessions across many doctors, **When** the list loads, **Then** the system does not require downloading every session in the clinic just to show the first page of results.

---

### User Story 3 - Predictable "More actions" menu on every slot (Priority: P2)

A staff member working through a session's slot list opens the "More" menu on one slot to reach Consultation note/Prescription/External record/Queue position, and expects it to behave predictably regardless of which row it's on or whether another menu is already open.

**Why this priority**: This exact bug class (multiple menus open at once; a menu rendering off-screen near the bottom of the list) was already found and fixed once this session on an equivalent component elsewhere in the staff console. Leaving the twin instance of the same bug unfixed is inconsistent and will resurface the identical complaint.

**Independent Test**: Can be fully tested by opening the "More" menu on one slot, then opening it on a different slot, and verifying the first one closes; and by opening the menu on the last visible slot in a long list and verifying the menu is fully visible without scrolling.

**Acceptance Scenarios**:

1. **Given** the "More" menu is open on one slot, **When** staff open the "More" menu on a different slot, **Then** the first menu closes automatically.
2. **Given** a slot near the bottom of the visible list, **When** staff open its "More" menu, **Then** the entire menu renders fully visible, flipping upward if needed.

---

### User Story 4 - Session detail page survives a refresh or direct link (Priority: P2)

A staff member refreshes the session detail page, opens it from a bookmark, or is sent a direct link to it, and expects to see which doctor and date it's for — not a generic, unlabeled screen.

**Why this priority**: Confirmed this data is fully available server-side (the Session record already stores its own doctor and date) but simply isn't returned by the endpoint the page calls. This is a data completeness gap with a small, contained fix, not a design question.

**Independent Test**: Can be fully tested by opening a session detail page directly by URL (not by clicking through from the list) and verifying the doctor name and date are shown.

**Acceptance Scenarios**:

1. **Given** a valid session detail URL, **When** it is opened directly (no prior navigation state), **Then** the page shows the correct doctor name and session date.
2. **Given** the page is refreshed while viewing a session's detail, **When** the page reloads, **Then** the doctor name and date remain visible, unchanged.

---

### User Story 5 - Visual consistency and at-a-glance session load (Priority: P3)

A staff member moving between the Roster page and the Day Sheet page experiences one consistent visual language, and can tell how full a session is without opening it.

**Why this priority**: Lower urgency than the safety, scale, and bug items above — this is polish and orientation, not a defect that blocks or corrupts work. Still valuable: the console currently looks like two different products depending on which page you're on, and staff currently have no way to gauge session load without opening every session.

**Independent Test**: Can be fully tested by visually comparing the Day Sheet and Roster pages side by side for a consistent look, and by confirming each session card shows a booked-vs-total indicator matching what's inside that session's detail page.

**Acceptance Scenarios**:

1. **Given** the Day Sheet and Roster pages, **When** viewed back to back, **Then** they share the same visual language (palette, card treatment, avatar style, interaction states).
2. **Given** a session card on the list, **When** staff view it, **Then** it shows how many of its slots are booked out of the total, matching the detail page's own slot list.

---

### Edge Cases

- What happens when a doctor has zero sessions in the current 14-day window — they do not appear in the doctor filter (see Assumptions: the filter is derived only from doctors with at least one session currently in view, matching this codebase's existing precedent for filters elsewhere in the staff console).
- What happens when a session has zero slots at all (e.g. none generated yet) — the fullness indicator must show something meaningful, not a broken fraction like "0 of 0."
- What happens when the whole-session-cancellation confirmation step is showing and the underlying session is cancelled by another staff member in a different browser tab first — the existing cancellation endpoint's own error handling covers this; the confirmation step must surface that error the same way the current one-step button does today.
- What happens when a slot's "More" menu is open and the page is paginated/filtered out from under it — the menu must close rather than pointing at a slot that's no longer on screen.

## Requirements *(mandatory)*

### Functional Requirements

**Safety**
- **FR-001**: The system MUST require an explicit confirmation step before cancelling a whole session — a single click MUST NOT be sufficient.
- **FR-002**: The confirmation step MUST clearly state the consequence (that active bookings in the session will be cancelled) before the action is taken.
- **FR-003**: The session detail page MUST visually distinguish destructive session-level actions (cancel from a cutoff, cancel whole session) from routine ones (insert a walk-in), so they are not equal-weight, adjacent choices.

**Scalability**
- **FR-004**: Staff MUST be able to filter the session list to a single doctor.
- **FR-005**: The session list MUST support retrieving results in bounded pages rather than requiring the full clinic-wide, window-wide result set in one response.
- **FR-006**: Each session list result MUST continue to be scoped to the requesting clinic and to the caller's own sessions only when the caller's sole active role at that clinic is Doctor (unchanged from current behavior).
- **FR-007**: Answering a single page or a doctor-filtered request MUST NOT require scanning every session in the clinic's window — the cost of retrieving one page MUST scale with what's requested, not with the total number of sessions the clinic has generated. (Complements FR-005/SC-002, which bound what's *returned*; this bounds what's *scanned* to compute it.)

**Bug fixes**
- **FR-008**: Only one "More actions" menu MUST be open at a time across a session's slot list.
- **FR-009**: A "More actions" menu MUST render fully within the visible viewport regardless of the slot's position in the list, flipping to open upward when there isn't room below.
- **FR-010**: The session detail page MUST display the correct doctor name and session date when loaded directly by URL or after a page refresh, without depending on how the page was navigated to.

**Orientation & consistency**
- **FR-011**: Each session in the list MUST show how many of its slots currently have an active booking, out of its total slot count.
- **FR-012**: The Day Sheet list and session detail pages MUST use the same visual language (palette, card styling, avatar treatment, interaction states) already established on the Roster page.

### Key Entities

- **Session** (existing): unchanged in structure; its already-stored doctor, clinic, and date become part of what the detail endpoint returns, and its slots' booking status becomes part of what a per-session "fullness" figure is derived from. No new entity.
- **Session list result** (existing response, extended): gains a doctor identifier/name for filtering, a booked-vs-total slot count per session, and support for retrieving it in bounded pages instead of all at once.
- **Session detail result** (existing response, extended): gains the doctor name and session date it was previously missing.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A staff member can find a specific doctor's next session at a busy, multi-doctor clinic without scrolling or paging through any other doctor's sessions.
- **SC-002**: Opening the Day Sheet at a clinic with a large number of staffed doctors does not require downloading every session in the clinic's 14-day window up front — only the first page of results.
- **SC-003**: Cancelling a whole session always requires two distinct user actions (an initial choice and a separate confirmation), with zero possibility of a single click completing the cancellation.
- **SC-004**: A session detail page opened directly by URL shows the same doctor name and date as one reached by clicking through from the list — 100% of the time, not only when navigated a specific way.
- **SC-005**: At no point can two "More actions" menus be visibly open at the same time, and a menu opened on the last visible row is never partially or fully off-screen.
- **SC-006**: A staff member can tell whether a session still has open capacity without opening it, for every session shown in the list.

## Assumptions

- This is a hardening/defect pass on an already-shipped, already-converged feature (041-staff-console-pickers) — no new user-facing capability is being introduced beyond what's listed above; scope is deliberately bounded to the 12 findings this spec documents.
- The per-session slot list (inside a single session's detail page) is naturally bounded by that session's own slot count and does not need the same bounded-paging treatment as the clinic-wide session list; its existing "reveal more" behavior is left as-is.
- Doctor self-scoping (a caller whose only active role at the clinic is Doctor sees only their own sessions) is an existing, already-tested behavior and is preserved unchanged by every requirement above — none of them alter who is authorized to see what, only how much is returned per request and how it's presented.
- Bringing this page's visual language in line with the Roster page (FR-012) means matching that page's already-established treatment (palette, card/avatar styling, interaction states) — it does not mean introducing a new, third visual language of its own.
- The whole-session-cancellation confirmation step (FR-001/FR-002) reuses this codebase's own existing confirm-step pattern (already used for staff deactivation and individual booking cancellation) rather than introducing a new interaction style.
- The doctor filter (FR-004) is populated only from doctors who have at least one session in the current 14-day window — not every doctor staffed at the clinic — matching this codebase's own precedent (the Roster page's specialization filter is likewise derived only from values actually present, so it never offers an option that's guaranteed to return zero results).
- "How full" a session is (FR-011) is expressed as booked-slot-count out of total-slot-count (e.g. "6 of 10") — a plain, unambiguous fraction requiring no new visual language of its own. A session with zero slots generated yet shows as such explicitly (e.g. "No slots yet") rather than a "0 of 0" fraction.
