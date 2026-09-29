# Feature Specification: Booking Protection / Appointment Abuse Prevention

**Feature Branch**: `060-booking-abuse-prevention`

**Created**: 2026-09-22

**Status**: Draft

**Input**: User description: "Booking Protection / Appointment Abuse Prevention — reduce fake or abusive appointment bookings that can unnecessarily block clinic availability, while avoiding overly aggressive restrictions on legitimate patients. Three capabilities: (1) a configurable maximum number of active/future appointments a patient can hold, (2) server-side rate limiting on booking attempts with configurable thresholds and a temporary (not permanent) cooldown, (3) admin flagging of potentially suspicious booking behavior for human review, never auto-classifying a patient as malicious from a single signal." Scoped through pre-specification research and four architectural decisions confirmed with the requester: limits apply globally across every clinic (via the patient's account) with an optional stricter per-clinic supplement; only self-service patients (not staff-created walk-ins) are covered; flag review happens in each clinic's own staff console, scoped to that clinic's own evidence; and every threshold is live-editable by an administrator rather than fixed at deploy time.

## Confirmed Existing Behavior vs. Proposed New Behavior

This feature sits on top of substantial existing booking infrastructure. To avoid re-litigating or accidentally duplicating what already exists, this section draws the line explicitly.

**Confirmed existing, unchanged by this feature:**

- A booking has exactly two states: active, or cancelled. There is no separate "completed" state on a booking — a visit's completion is tracked elsewhere, on the appointment slot itself, not on the booking record.
- A patient's self-service account is a single identity that spans every clinic they've ever visited — their existing "My Bookings" view already lists appointments across all clinics from one account, not clinic-by-clinic.
- A walk-in patient (added to the system by clinic staff, e.g. at the front desk) is a fundamentally different, staff-mediated record with no self-service login of its own.
- The system already prevents the exact same appointment slot from being double-booked by two people at once.
- An unrelated, narrower rate limiter already exists today, but only on login/signup/clinic-registration pages, only by network address (not by account), and does not persist a history — it cannot be reused as-is for this feature's needs and this feature does not modify it.
- A prior feature in this system briefly used a patient's no-show history to automatically resize appointment availability; that mechanism was later deliberately removed as unwanted product behavior. This feature does not use no-show history to change availability, resize schedules, or influence booking in any automatic way — no-show history is used here strictly as an admin-visible review signal, nothing more.
- There is currently no mechanism anywhere in the system for an administrator to change a configurable value while the system is running — every existing configurable value requires a deployment to change.
- There is currently no general-purpose audit-log of administrative actions anywhere in the system.

**Proposed new behavior (this feature):**

- A limit on how many active appointments a self-service patient may hold at once, enforced at the moment a new booking is attempted.
- A server-side check that throttles how often a patient's account can attempt to create a booking, independent of whether those attempts succeed.
- A record of booking attempts, kept for a rolling period, that both enforces the throttle above and doubles as evidence for admin review.
- A set of signals, evaluated automatically but never acted on automatically, that surface a patient's booking activity for a human to look at.
- A place for clinic staff to review and close out those flagged signals, using only their own clinic's evidence.
- The system's first runtime-editable administrative settings, scoped narrowly to the values this feature needs.

## Clarifications

### Session 2026-09-22

- Q: When a single booking attempt fails both the active-appointment limit and the rate limit at the same time, which message does the patient see? → A: The rate limit is checked first — a patient currently in cooldown always sees the cooldown message, regardless of whether the booking would also have failed the appointment limit.
- Q: If a patient keeps attempting to book while already serving a rate-limit cooldown, does each attempt push the cooldown's end time further out, or does it always end at its originally-calculated time? → A: Fixed once — the cooldown end time is set when the threshold is first exceeded and does not move regardless of further attempts made during it.
- Q: Should "unusually high booking-attempt volume" and "repeated rate-limit violations" be two distinct flagging signals, or the same signal merged into one? → A: Distinct — high volume counts raw attempts (successful or not) in a short window; repeated rate-limit violations counts how many times the patient has actually been placed in cooldown, a separate, later-stage signal.
- Q: If the same condition keeps re-triggering a signal (e.g. a patient stays above a threshold continuously), should the system create a fresh flag every time, or suppress repeat flags for the same ongoing condition? → A: Suppress duplicates — at most one outstanding flag per (patient, clinic, signal type) combination; once resolved, the signal is free to fire again if the condition still holds.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - A patient is stopped from over-booking, and understands why (Priority: P1)

As a self-service patient, when I already hold as many active appointments as the clinic system allows, and I try to book another one, I am told clearly that I've reached my current appointment limit — not that I'm suspected of anything — so I understand what happened and what my options are (e.g. cancel an existing appointment first).

**Why this priority**: This is the most visible, most frequently encountered part of the whole feature, and the one most likely to frustrate a legitimate patient if done badly. Getting the limit check correct and the message non-accusatory is the foundation everything else builds on.

**Independent Test**: As a patient with several active appointments already booked (at or above the configured limit), attempt to book one more appointment anywhere in the system and confirm the attempt is refused with a clear, specific, non-accusatory explanation, while a patient below the limit can book normally.

**Acceptance Scenarios**:

1. **Given** a patient's number of active appointments across every clinic equals the currently configured limit, **When** they attempt to book another appointment, **Then** the booking is refused and they see a clear message stating they've reached their appointment limit, with no implication of wrongdoing.
2. **Given** a patient's number of active appointments is below the configured limit, **When** they attempt to book another appointment, **Then** the booking proceeds exactly as it does today, with no new friction.
3. **Given** a patient is at their limit because of appointments at other clinics, **When** they view the limit message at a new clinic, **Then** the message does not reveal specific appointment details from those other clinics — only that a limit has been reached.
4. **Given** a patient cancels one of their active appointments so they are now below the limit, **When** they attempt to book a new appointment, **Then** the booking is allowed (a cancelled appointment never counts toward the limit).
5. **Given** a clinic has additionally configured its own stricter local limit, **When** a patient at that specific clinic would exceed the *clinic's* limit even though they are still under their global limit, **Then** the booking is still refused, with a message that does not need to distinguish which of the two limits applied.

---

### User Story 2 - Repeated rapid booking attempts are throttled, not blocked forever (Priority: P1)

As a self-service patient, if I (or something acting as me) attempt to create bookings unusually rapidly in a short period, further attempts are temporarily paused with a clear message telling me when I can try again — I am never permanently locked out because of this.

**Why this priority**: This is the feature's core defense against automated or scripted abuse, and it must hold even when a patient is deliberately trying to work around the booking limit above by attempting many bookings quickly. Equal priority to User Story 1 because the booking limit alone is easy to probe and defeat without this.

**Independent Test**: As a patient, attempt to create bookings in rapid succession past the configured attempt threshold within the configured window, and confirm further attempts are refused with a clear temporary cooldown message including when the cooldown ends, then confirm a normal attempt succeeds again once the cooldown has elapsed.

**Acceptance Scenarios**:

1. **Given** a patient has attempted to create bookings more times than the configured threshold within the configured time window, **When** they attempt another booking, **Then** the attempt is refused with a message stating they must wait, including approximately how long.
2. **Given** a patient is currently in a cooldown period, **When** the cooldown period elapses, **Then** their very next booking attempt is evaluated normally (not still blocked).
3. **Given** a booking attempt fails for an unrelated reason (e.g. the slot was already taken by someone else, or the patient was over their booking limit from User Story 1), **When** counting toward the rate-limit threshold, **Then** that failed attempt still counts — the throttle counts attempts, not only successes.
4. **Given** a patient submits a booking request through any means other than the normal patient interface (e.g. a direct request to the system bypassing the usual screens), **When** that request arrives, **Then** it is still subject to the same throttle — the protection cannot be bypassed by skipping the normal user interface.
5. **Given** two booking attempts from the same patient account arrive at effectively the same instant, **When** both are evaluated, **Then** the throttle's count is still accurate (neither attempt is silently uncounted because of the timing).

---

### User Story 3 - Clinic staff review and resolve flagged suspicious activity (Priority: P2)

As clinic staff with administrative access, I can see a list of my clinic's patients whose booking behavior has been flagged as worth a look, see why each one was flagged and the relevant activity behind it, and mark a flag as reviewed once I've looked into it — all without the system ever telling me a patient is "confirmed" to be abusive on its own.

**Why this priority**: This is where the value of the first two capabilities becomes actionable for the clinic, but it depends on activity signals (attempts, cancellations, no-shows) already being tracked, so it naturally follows behind them. Still core to the feature's stated purpose, hence P2 rather than P3.

**Independent Test**: As clinic staff, generate flag-worthy activity for a test patient at your clinic (e.g. several cancellations in a short period), confirm a flag appears in your clinic's review list with the correct reason and supporting activity, mark it reviewed, and confirm it no longer appears as outstanding.

**Acceptance Scenarios**:

1. **Given** a patient's activity at a clinic matches one of the configured suspicion signals (e.g. repeated cancellations within the configured window), **When** that signal is detected, **Then** a flag is recorded with a timestamp and a specific, human-readable reason.
2. **Given** a patient has triggered more than one distinct flag over time, **When** clinic staff view that patient's flags, **Then** each flag is shown individually with its own reason and timestamp — the system does not collapse them into a single overall "risk score" or verdict.
3. **Given** a flag exists for a patient at Clinic A, **When** staff at Clinic B (a different clinic) look for flags, **Then** they do not see Clinic A's flag or its supporting evidence, with one narrow exception: if the patient has currently reached their *global* appointment limit, that bare fact is visible to any clinic reviewing that patient, without exposing the other clinics' specific appointment details.
4. **Given** a flag is outstanding, **When** clinic staff open it, **Then** they can see the patient's relevant booking activity at that clinic (recent bookings, cancellations, no-shows, and rate-limit history) needed to make an informed judgment, before taking any action.
5. **Given** clinic staff have reviewed a flag and are satisfied no action is warranted, **When** they mark it resolved, **Then** the flag is recorded as reviewed, by whom, and when, and no longer appears in the outstanding list, while remaining visible in the patient's flag history.
6. **Given** a single suspicious signal has fired exactly once for a patient, **When** the system processes that signal, **Then** it only ever creates a flag for staff review — it never itself restricts, blocks, or otherwise limits that patient's ability to book.

---

### User Story 4 - A platform administrator configures the protection thresholds (Priority: P2)

As a platform (super) administrator, I can view and change the system-wide settings this feature uses — the global appointment limit, the rate-limiting thresholds and cooldown, the suspicion thresholds, and whether each protection is currently active at all — and see those changes take effect immediately, without anyone needing to redeploy the system.

**Why this priority**: Every other user story depends on these values being right for the clinic network as a whole, and getting a threshold wrong (too strict or too loose) needs to be correctable quickly. Not P1 because the feature ships with safe defaults that work without any configuration having been touched yet.

**Independent Test**: As a platform administrator, change the global appointment limit to a new value, confirm it is enforced immediately for the next booking attempt anywhere in the system without any deployment step, and confirm turning a protection off stops it from being enforced.

**Acceptance Scenarios**:

1. **Given** no administrator has configured any setting yet, **When** the system evaluates any of this feature's protections, **Then** it uses documented default values rather than failing or refusing all bookings.
2. **Given** a platform administrator changes the global appointment limit, **When** the change is saved, **Then** the very next booking attempt anywhere in the system is evaluated against the new value.
3. **Given** a platform administrator turns off one specific protection (for example, rate limiting) while leaving the others on, **When** a patient's activity would have triggered only that disabled protection, **Then** it is not enforced, while the remaining active protections continue to apply.
4. **Given** someone without platform-administrator access attempts to view or change these settings, **When** that request is made, **Then** it is refused.

---

### User Story 5 - A clinic administrator adds a stricter local appointment limit (Priority: P3)

As a clinic administrator, I can optionally set a lower appointment limit that applies only to my own clinic, on top of the platform-wide global limit, if my clinic wants to be more conservative than the network default.

**Why this priority**: Genuinely optional and narrow — most clinics are expected to rely on the global default alone. Lowest priority because the feature is fully functional and meets its stated purpose without it.

**Independent Test**: As a clinic administrator, set a per-clinic limit lower than the current global limit, and confirm a patient who is still under the global limit but would exceed the clinic's own limit is refused when booking at that specific clinic (while still being allowed to book normally at other clinics).

**Acceptance Scenarios**:

1. **Given** a clinic administrator has not set a per-clinic limit, **When** patients book at that clinic, **Then** only the global limit applies.
2. **Given** a clinic administrator sets a per-clinic limit, **When** a patient at that clinic would exceed it (even while still under the global limit), **Then** the booking at that specific clinic is refused, while the same patient can still book normally at other clinics up to their global limit.
3. **Given** someone without that clinic's administrator access attempts to change that clinic's local limit, **When** that request is made, **Then** it is refused.

---

### Edge Cases

- **New patient with no booking history yet**: has zero active bookings and zero attempt history — never affected by any of these protections until they actually start booking.
- **Patient exactly at the limit cancels and immediately rebooks**: cancellation must be recorded and counted before the next booking attempt is evaluated, so a legitimate cancel-then-rebook is never incorrectly refused.
- **Two devices, same account, simultaneous booking attempts**: both the appointment limit and the attempt-rate throttle must produce the same correct outcome as if the attempts had happened one after another — no race lets a patient slip past either check by acting at the same instant from two places.
- **A patient's cooldown period spans a threshold configuration change**: if a platform administrator changes the cooldown duration while a patient is already serving one, the patient is not left in an inconsistent state (e.g. never unblocked, or blocked longer than any cooldown that has ever existed) — the currently-serving cooldown's own original end time still applies.
- **A clinic is de-verified or a doctor is revoked while a flag involving them is outstanding**: an outstanding flag remains reviewable and resolvable by that clinic's remaining staff; this feature does not add any new interaction with clinic/doctor verification status.
- **A patient's flag history grows very large over time**: staff reviewing a patient's flags see them in a clearly ordered, filterable, searchable view rather than an unbounded undifferentiated list.
- **All protections are turned off**: booking behaves exactly as it does today, with no limit, no throttle, and no flags — turning protections off must not have any other side effect.
- **A patient is flagged, but every flag they've ever had is eventually marked resolved**: this is a completely normal, expected outcome for the majority of flagged patients (most flags are expected to resolve as "nothing to worry about") and must not itself change how that patient is treated by the booking limit or rate limiter, which operate independently of flag/review status.
- **Suspicious-activity signals overlapping in time**: a single burst of unusual activity might independently satisfy more than one suspicion signal at once (e.g. a rapid string of cancellations that also trips the attempt-rate threshold); each qualifying signal records its own separate flag rather than one signal suppressing or merging into another.

## Requirements *(mandatory)*

### Functional Requirements

**Booking limit**

- **FR-001**: The system MUST enforce a configurable maximum number of active appointments a self-service patient may hold at one time, evaluated across every clinic the patient has an account with.
- **FR-002**: The system MUST refuse a new booking attempt that would cause a patient to exceed their currently configured active-appointment limit.
- **FR-003**: Only a patient's currently active appointments MUST count toward the limit; a cancelled appointment MUST NOT count, regardless of when it was cancelled.
- **FR-004**: The system MUST support an additional, optional, per-clinic limit that a clinic may configure to be stricter than the platform-wide limit for bookings at that specific clinic; a patient must satisfy both the platform-wide limit and any applicable per-clinic limit to book at that clinic.
- **FR-005**: When a booking is refused for exceeding a limit, the system MUST show the patient a clear, specific, non-accusatory message that identifies the limit was reached, without implying suspicion of wrongdoing and without exposing the details of appointments held at other clinics.
- **FR-006**: The booking-limit check MUST produce a correct result even when two booking attempts for the same patient are evaluated at effectively the same time (see Edge Cases).

**Rate limiting**

- **FR-007**: The system MUST enforce a configurable limit on how many booking attempts a self-service patient's account may make within a configurable rolling time window. The rate-limit check MUST be evaluated before the booking-limit check (FR-001–FR-006); a patient currently in a rate-limit cooldown MUST always see the cooldown message (FR-011), even if the attempt would also have been refused for exceeding an appointment limit.
- **FR-008**: The attempt count MUST include every booking attempt, whether it ultimately succeeds or is refused for any reason (limit reached, slot unavailable, validation failure, or any other cause).
- **FR-009**: Enforcement MUST happen on the server regardless of which client or interface the request arrives through; it MUST NOT be possible to bypass this throttle by skipping the normal patient-facing screens.
- **FR-010**: Once a patient exceeds the attempt threshold, the system MUST refuse further booking attempts from that account for a configurable cooldown period, after which normal attempts MUST be evaluated again — this restriction MUST always be temporary, never permanent. The cooldown's end time MUST be fixed at the moment the threshold is first exceeded; further attempts made while the cooldown is active MUST NOT extend or reset it.
- **FR-011**: When a patient is refused due to the rate limit, the system MUST show a clear message indicating they must wait, including approximately how much longer.
- **FR-012**: The system MUST retain a record of booking attempts (at minimum: which patient account, when, and outcome) for long enough to support both the rate-limit calculation and the admin-flagging signals that read attempt history (FR-016 and FR-020 below).

**Admin flagging**

- **FR-013**: The system MUST evaluate a defined set of suspicious-activity signals for self-service patients on an ongoing basis (see FR-016–FR-020 for the specific signals).
- **FR-014**: Each time a signal's configured threshold is met, the system MUST create a distinct flag recording the reason, the specific signal that triggered it, and when it was detected — unless an outstanding (not yet resolved) flag already exists for that same patient, clinic, and signal type, in which case no duplicate flag is created; once that flag is resolved, the signal MUST be free to create a new flag again if the underlying condition still holds.
- **FR-015**: The system MUST NOT take any automatic action against a patient's account or booking ability as a result of a flag being created — a flag is visibility only, never enforcement, and this MUST hold true regardless of how many flags a patient has accumulated.
- **FR-016**: The system MUST support a signal for an unusually high volume of raw booking attempts (successful or not) by a patient within a configurable window, measured independently of whether any of those attempts actually triggered a rate-limit cooldown (see FR-020, a separate, later-stage signal).
- **FR-017**: The system MUST support a signal for repeated appointment cancellations by a patient within a configurable rolling window.
- **FR-018**: The system MUST support a signal for repeated appointment no-shows by a patient within a configurable rolling window; this signal MUST be used only to inform this flagging feature and MUST NOT influence appointment scheduling, availability, or slot generation in any way.
- **FR-019**: The system MUST support a signal for a patient holding multiple active appointments whose scheduled dates and times overlap with each other.
- **FR-020**: The system MUST support a signal for a patient having been rate-limited (FR-007–FR-011) repeatedly within a configurable window.
- **FR-021**: Clinic staff with administrative access MUST be able to view outstanding flags for patients who have activity at their own clinic, along with each flag's reason, timestamp, and the patient's relevant booking activity at that clinic needed to review it.
- **FR-022**: Clinic staff at one clinic MUST NOT be able to see another clinic's flags or that other clinic's supporting booking evidence for a shared patient, with the single exception that whether a patient currently sits at their global appointment limit MAY be shown as a bare fact to any clinic, without further detail.
- **FR-023**: Clinic staff with administrative access MUST be able to mark an outstanding flag as reviewed/resolved, and the system MUST record who resolved it and when.
- **FR-024**: A resolved flag MUST remain visible in the patient's flag history rather than being deleted.
- **FR-025**: The system MUST provide clinic staff a way to filter and search the flags relevant to their clinic (at minimum, by outstanding vs. resolved status, and by patient).

**Configuration**

- **FR-026**: A platform administrator MUST be able to view and change, without requiring a system redeployment: the platform-wide active-appointment limit, the rate-limiting attempt threshold/window/cooldown, and the threshold *and* window for each suspicion signal in FR-016–FR-020 (a signal with no natural window, such as the overlapping-appointments count, has only a threshold).
- **FR-027**: A platform administrator MUST be able to turn each of the three protections (booking limit, rate limiting, admin flagging) on or off independently.
- **FR-028**: A clinic administrator MUST be able to view and change only their own clinic's optional supplementary appointment limit (FR-004); they MUST NOT be able to view or change any platform-wide setting or another clinic's setting.
- **FR-029**: If no administrator has ever configured a given setting, the system MUST apply a documented default value rather than failing, refusing all bookings, or silently disabling the protection.
- **FR-030**: A change to any setting MUST take effect for the next booking attempt evaluated after the change is saved, without requiring a deployment or restart.
- **FR-031**: Only a platform administrator MUST be able to view or change platform-wide settings; only that clinic's own administrator MUST be able to view or change that clinic's supplementary limit. Any other caller attempting either MUST be refused.

**Cross-cutting**

- **FR-032**: This feature MUST apply only to bookings made by a self-service patient through their own account; it MUST NOT apply to a walk-in appointment created on a patient's behalf by clinic staff.
- **FR-033**: Every administrative action taken under this feature (resolving a flag, changing a setting) MUST be attributable to the specific administrator who took it and when, in a way that can be reviewed later.

### Key Entities *(include if feature involves data)*

- **Booking Attempt Record**: one row per attempt by a self-service patient to create a booking, whatever the outcome. Captures the patient's account, when the attempt happened, and whether it succeeded or was refused (and, if refused, why — limit reached, rate-limited, slot unavailable, or other). Feeds both the rate-limit calculation (FR-007–FR-012) and the high-attempt-volume and repeated-rate-limit-violation signals (FR-016, FR-020). Retained for a bounded rolling period, not indefinitely (see Security & Privacy Requirements).
- **Suspicious Activity Flag**: one row per triggered signal episode for a patient. Captures which signal fired, the specific reason in human-readable form, when it was detected, which clinic's activity it relates to (or "cross-clinic" for the global-limit fact in FR-022), its current status (outstanding or resolved), and — once resolved — who resolved it and when. Never carries a numeric "risk score"; each row is one independent piece of evidence. At most one outstanding flag exists per (patient, clinic, signal type) at a time (FR-014) — a signal that keeps re-matching while a flag is already outstanding does not create duplicates, but is free to create a new flag once the existing one is resolved and the condition still holds.
- **Protection Setting**: a small, named set of platform-wide values administrators can view and change at runtime — the global appointment limit, the rate-limit attempt threshold/window/cooldown, each suspicion signal's threshold/window, and an on/off toggle per protection. This is new: today every configurable value in the system requires a deployment to change.
- **Clinic Supplementary Limit** (called `ClinicBookingLimitOverride` in the data model): an optional, per-clinic override value that, when set, tightens (never loosens) the platform-wide appointment limit for bookings at that one clinic.
- *(Existing, unchanged)* **Patient Account**: the self-service patient identity this feature's limit, throttle, and flags are all attached to — already spans every clinic the patient has visited.
- *(Existing, unchanged)* **Booking**: the appointment record whose active/cancelled status this feature counts and whose cancellation history feeds the repeated-cancellation signal.

## Non-Functional Requirements

- **NFR-001 (Availability of protection)**: None of this feature's checks may become a new single point of failure for booking — if a non-critical part of this feature (e.g. flag detection) is temporarily unavailable, a patient's ability to book a legitimate appointment must not be blocked as a side effect; the booking-limit and rate-limit checks themselves are the exception, since they are the intended gate.
- **NFR-002 (Latency)**: Every check this feature adds to the booking path must add no perceptible delay to a patient completing a normal booking (the existing booking flow's responsiveness must be preserved).
- **NFR-003 (Correctness under concurrency)**: The booking limit and the rate limit must both produce the same result under simultaneous/concurrent attempts as they would under sequential attempts — see Edge Cases and FR-006.
- **NFR-004 (Configuration takes effect live)**: See FR-030 — no restart or deployment required for a setting change to apply.
- **NFR-005 (Non-accusatory patient experience)**: Patient-facing messaging for both the booking limit and the rate limit must state the fact plainly (limit reached / temporary cooldown) and never use language implying fraud, abuse, or suspicion — that framing is for the admin-facing side only.
- **NFR-006 (No internal detail leakage to patients)**: A patient must never see which specific signal, threshold, or flag (if any) applies to them — only the two plain outcomes in User Stories 1 and 2.

## Business Rules

- **BR-001**: A cancelled appointment never counts toward the booking limit, no matter how recently it was cancelled.
- **BR-002**: A booking attempt counts toward the rate limit whether it succeeds or fails, and whatever the reason for failure.
- **BR-003**: A flag is evidence for a human to review, never itself a restriction — no flag, or combination of flags, automatically limits, blocks, or bans a patient. (The only enforcement mechanisms in this feature are the booking limit and the rate limit, both of which apply the same way to every patient regardless of flag history.)
- **BR-004**: Flag visibility is scoped to the clinic where the underlying activity happened, except for the single cross-clinic fact of whether a patient currently sits at their global appointment limit.
- **BR-005**: The optional per-clinic appointment limit can only ever be equal to or stricter than the platform-wide limit for that clinic's own bookings — a clinic cannot use its local setting to be more permissive than the global limit.
- **BR-006**: This feature governs self-service patient bookings only; walk-in bookings created by staff are entirely outside its scope.
- **BR-007**: No-show history used for the admin-flagging signal (FR-018) must never feed back into scheduling, slot generation, or availability calculations — it is read-only, review-facing data.

## Security & Privacy Requirements

- **SEC-001**: Every check and record created by this feature is enforced and stored server-side; no enforcement may depend on trusting data supplied by the client.
- **SEC-002**: Only an authenticated platform administrator may view or change platform-wide settings (FR-026, FR-027); only an authenticated administrator of a specific clinic may view or change that clinic's own supplementary limit (FR-028) — enforced on every request, not only hidden from the UI.
- **SEC-003**: Only authenticated clinic staff with administrative access to a given clinic may view that clinic's flags and supporting evidence; a request for another clinic's data must be refused the same way this system already refuses any other cross-clinic data request.
- **SEC-004**: Booking Attempt Records and Suspicious Activity Flags must store only the minimum data needed to enforce the throttle and support review (which patient, when, outcome, and — for flags — a human-readable reason) — no unrelated personal data is newly collected by this feature. A flag's supporting evidence (the specific bookings/attempts behind its reason) is computed live from existing records when a reviewer opens it, rather than duplicated onto the flag itself — nothing is gained by storing a second copy of data the system already has, and it keeps the flag row itself minimal.
- **SEC-005**: Booking Attempt Records must not be retained indefinitely; they are retained only as long as needed to support the rate-limit window and the flagging signals that read them, after which they age out.
- **SEC-006**: Patient-facing responses (User Stories 1 and 2) must never reveal internal threshold values, signal names, or flag status to the patient.

## Audit Requirements

- **AUD-001**: Every time a flag is marked reviewed/resolved, the system records who did it and when (FR-023), and this record is never deleted (FR-024).
- **AUD-002**: Every time a platform administrator changes a system-wide setting, the system MUST record a full history of that change — the setting, its prior value, its new value, who changed it, and when — not only its current state. These are security-relevant thresholds; a quiet weakening of one over time (by an abuser with admin access, or by mistake) MUST be reconstructable later, not just visible as "whoever set it last."
- **AUD-003**: Every time a clinic administrator changes their clinic's supplementary limit, the system MUST record the same full change history as AUD-002 (prior value, new value, who, when).
- **AUD-004**: Administrative audit records (AUD-001–AUD-003) are viewable by administrators with the appropriate access but are not exposed to patients.

## Testing Requirements

- Booking-limit enforcement must be verified both for the platform-wide limit alone and for the combination of platform-wide plus an active per-clinic supplementary limit, including the case where a patient is under one but over the other.
- Rate-limit enforcement must be verified counting both successful and failed booking attempts, and must verify the cooldown expires correctly and normal attempts resume.
- Concurrency behavior for both the booking limit and the rate limit must be verified under simultaneous/near-simultaneous attempts from the same patient account (see Edge Cases and NFR-003) — this is a business-rule guarantee, not an implementation detail, and must hold under real concurrent load, not just sequential calls.
- Each admin-flagging signal (FR-016–FR-020) must be verified to trigger correctly at its threshold and not before it, and to never trigger any automatic restriction (BR-003).
- Flag visibility scoping (FR-021, FR-022, BR-004) must be verified so that one clinic never sees another clinic's flags or supporting detail, other than the single global-limit fact.
- Configuration changes (FR-026–FR-031) must be verified to take effect on the next relevant request without a restart, and default values must be verified to apply correctly when nothing has ever been configured.
- Authorization must be verified for every new endpoint this feature introduces: a platform-only setting refused to a non-platform-administrator, a clinic's own setting/flags refused to a different clinic's administrator, and both refused entirely to a patient.
- The existing, unrelated login/signup rate limiter and the previously-removed no-show-driven scheduling mechanism must both be verified as untouched by this feature (regression coverage confirming this feature does not reintroduce the latter or modify the former).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of booking attempts that would push a patient's active-appointment count over their applicable limit(s) are refused, with zero cases of a patient successfully exceeding a configured limit.
- **SC-002**: 100% of booking attempts beyond the configured rate-limit threshold within the configured window are refused during the cooldown, and 100% of legitimate attempts made after a cooldown expires are evaluated normally (zero patients left blocked past their cooldown's end).
- **SC-003**: A patient refused for either reason can, without contacting support, understand from the message shown why the booking didn't go through and what "temporary" means for their situation, in under 10 seconds of reading.
- **SC-004**: 0% of flags result in any automatic change to a patient's ability to book — verified across every signal type.
- **SC-005**: Clinic staff can go from opening the flag review area to seeing a specific flagged patient's relevant activity in 2 actions or fewer.
- **SC-006**: A platform administrator can change any of this feature's settings and see the new value enforced on the very next relevant request, with zero deployment steps in between.
- **SC-007**: Zero cross-clinic data leakage: across a representative set of test scenarios, no clinic administrator is ever able to view another clinic's flag detail or supporting booking evidence for a shared patient.
- **SC-008**: Under concurrent/simultaneous booking attempts from the same patient account, the booking limit and rate limit each hold correctly in 100% of tested race scenarios (zero cases of a limit being exceeded due to a timing race).

## Assumptions

- **Default values** (all independently changeable by a platform administrator at any time via FR-026 — none of the following are fixed):
  - **Global active-appointment limit: 15.** Reasoning: a real patient managing several ongoing concerns (e.g. a follow-up, a specialist referral, a dental visit, a physio course) across more than one clinic might reasonably hold half a dozen or more active appointments at once; 15 comfortably covers that with headroom, while still bounding the worst case of a single account being used to hold dozens of slots hostage across the network.
  - **Per-clinic supplementary limit: unset/disabled by default** (a clinic must opt in). Reasoning: the global limit already provides baseline protection; requiring an explicit clinic opt-in for a stricter local rule keeps the default experience the least restrictive one, consistent with the instruction to avoid unnecessarily blocking legitimate patients.
  - **Rate-limit threshold: 8 booking attempts per rolling 10-minute window.** Reasoning: booking creation is a heavier, more consequential action than a login attempt (it touches real appointment inventory and clinic-facing data), so the threshold is deliberately tighter than the existing login rate limiter's default; 8 within 10 minutes comfortably allows a legitimate patient who hits a couple of already-taken slots while browsing to keep trying, while still stopping a rapid scripted loop.
  - **Rate-limit cooldown: 15 minutes.** Reasoning: long enough to meaningfully interrupt an automated retry loop, short enough that a legitimate patient who tripped it by mistake isn't meaningfully inconvenienced.
  - **Repeated-cancellation signal: 4 or more cancellations within a rolling 30 days.** Reasoning: occasional cancellations are a completely normal part of legitimate patient behavior (plans change); a monthly count in the low single digits is where a pattern becomes worth a human glance, not proof of anything.
  - **Repeated-no-show signal: 3 or more no-shows within a rolling 90 days.** Reasoning: a no-show is more consequential to a clinic than a cancellation (capacity is wasted with no warning), so a lower count justifies a flag, but the longer 90-day window avoids flagging a patient over a single bad week.
  - **High-attempt-volume signal (FR-016): 10 or more raw booking attempts (successful or not) within a rolling 60-minute window.** Reasoning: measures burst activity directly, independent of whether the rate limiter itself was ever triggered — a patient could stay just under the rate-limit threshold (FR-007) while still showing an unusually bursty pattern worth a look; 10 within an hour is well above what any legitimate patient browsing for a slot would generate.
  - **Repeated-rate-limit-violations signal (FR-020): 3 or more rate-limit cooldowns triggered within a rolling 24 hours.** Reasoning: a distinct, later-stage signal from the one above — one cooldown is easily an impatient but legitimate patient; being cooled down three separate times in a single day is a much stronger pattern that the earlier volume signal alone might miss (e.g. three separate short bursts spread across the day, each under the volume threshold).
  - **Overlapping-appointments signal: 3 or more active appointments whose scheduled date and time overlap each other.** Reasoning: one accidental double-booking is common and benign (patients regularly hold a backup option); three or more simultaneous overlapping holds starts to look like deliberate slot-hoarding worth a look.
- **Booking Attempt Record retention**: retained on a rolling basis long enough to cover the longest window any active signal or the rate limiter itself currently uses (e.g. if the longest configured window is 90 days, records are kept at least that long), then aged out — exact retention mechanics are a planning-stage decision, not fixed here.
- **Concurrency mechanism**: this specification requires that the booking limit and rate limit both hold correctly under concurrent attempts (FR-006, FR-007–FR-012, NFR-003) but deliberately does not prescribe the specific locking or database mechanism used to guarantee it — that is a planning-stage technical decision, informed by (but not identical to) this system's existing approach to preventing the same appointment slot from being double-booked.
- **This feature does not add any new way for an administrator to suspend, ban, or otherwise restrict a specific patient's account** — the only two administrative actions in scope are reviewing/resolving a flag (User Story 3) and changing the shared settings (User Stories 4 and 5). A dedicated per-patient restriction capability, if wanted, is a natural follow-on feature, not part of this one.
- **The Super Admin (platform) side of this feature does not get its own flag-review screen** — only clinic staff review flags (User Story 3); the platform administrator's role in this feature is limited to configuring the shared settings (User Story 4).
- Mobile/responsive presentation for both the patient-facing messages and the new admin screens follows this system's existing design conventions; no new device-specific requirement is introduced.
