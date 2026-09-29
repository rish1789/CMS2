# 10 — Product Improvement Backlog

**Scope:** derived **only** from documents 00–09. Nothing here is implemented.

**Ordering:** no scores. Dependencies and a logical order are given where they are clear.

**Process:** per `.specify/memory/constitution.md`, anything that introduces new behaviour must go through `/speckit-specify` → `/speckit-plan` → `/speckit-tasks` before implementation. Items that touch the constitution's explicit out-of-scope boundaries (reschedule, payments, notification delivery) first need a documented product decision.

---

## Phase 1 status (`specs/065-phase1-stabilization`, 2026-09-29)

| Item | Status |
|---|---|
| B-01 | Done (fail-closed chains + regression tests) |
| B-02 | Done (no secrets in launch config; never committed) |
| B-03 | Blocked: product decision (specified behaviour) |
| B-04 | Creation half done (active role required); booking-state precondition open |
| A-10 / H-02 | Root-cause mitigation applied; see 09 |
| H-03 | Partly done: the fail-closed tests assert the default rule for unmapped paths, so no per-endpoint allowlist exists to fall out of sync |
| A-01, A-02, A-03, C-01 | Done (V41 `session_cancellation` record, one bookability rule across all 5 booking paths, listings and waitlist matching; see 07 BUG-002/003/004) |
| A-04 | Done (elapsed = `start_time < now` in listings and fixed-time booking; see 07 BUG-005) |
| A-08 | Partly done: every booking path now uses the central rule and refuses past-dated and cancelled sessions (spec 065 FR-006, FR-013). Queue tokens for a session that ended earlier today are still accepted (walk-ins after the end are allowed by design, spec 063); that part of PB-004 stays open |
| A-07 | Unchanged: the new rules use an injectable `Clock` in the server zone; the zone decision is still open |
| H-01 | Partly done: executed once with Docker on 2026-09-29, 1,046 tests with 847 passing (see 07, "Full backend run with Docker"). The 199 failures are pre-existing: about 192 are test-harness decay or stale assertions, and 7 are real defects (PB-003 plus new unhandled duplicate-key and race cases). None come from 065. Still open: fix the harness (shared-context/stopped-container reuse, teardown order, fixtures) so plain `./gradlew test` in CI is meaningful. |

## Suggested logical order (where dependencies make it clear)

1. **Protect what exists** (no behaviour change): I-01, H-01, H-02, B-02.
2. **Close confirmed correctness and security defects:** B-01, A-01/A-02/A-03 (they share C-01), A-04, A-05, A-06, A-07.
3. **Lifecycle model:** C-01 → C-02 → E-01…E-04, K-02, K-03.
4. **Cross-cutting consolidation:** D-01, D-02, J-01…J-04, E-07.
5. **Scale and operations:** F-01…F-04.
6. **New capabilities** after product decisions: K-01, K-04…K-07.

---

## A. Bugs / correctness

| ID | Problem | Evidence | Area | Depends on | Suggested direction | Risk if ignored |
|---|---|---|---|---|---|---|
| A-01 | A cancelled whole session remains bookable | BUG-002; 03 W12 | scheduling, booking | C-01 | Represent session cancellation explicitly, and exclude cancelled sessions and slots from every listing and booking path | Patients booked with an absent doctor |
| A-02 | A partial-cancellation range remains bookable | BUG-003 | same | C-01 | Mark the cancelled range (a slot state or a session cutoff) instead of reopening it | Same, for the afternoon or evening |
| A-03 | An unbooked session cannot be cancelled | BUG-004 | scheduling | C-01 | Allow "doctor unavailable" on empty sessions; a precise error when truly already cancelled | Empty sessions stay open |
| A-04 | Elapsed same-day slots offered and bookable | BUG-005 | patient booking | — | Filter on date **and** time in the listing queries and in the booking-time checks (`PatientBookingService.doBookSlot`) (clinic-local time, see A-07) | Instant no-shows; false abuse flags |
| A-05 | New-patient phone collision gives the wrong error or a 500 | PB-001, PB-002 | staff booking, walk-in | — | One server-side patient-resolution path for staff flows with an explicit duplicate-phone response | Front-desk confusion; duplicate or failed registrations |
| A-06 | Token issuance retry ineffective inside transactions | PB-003 | scheduling | — | Verify with a concurrency test first (H-01), then fix the transaction boundaries or locking | Lost walk-ins at busy times |
| A-07 | Time rules use the JVM zone | PB-005 | all time logic | — | Decide on a clinic time zone (per-clinic or system-wide); inject a `Clock` consistently (already done in `SessionLiveStatusService`) | Wrong no-shows, cutoffs and auto-completion when deployed in UTC |
| A-08 | No date or state guards on staff, queue and walk-in booking APIs | PB-004 | booking | A-07 | Central "session is bookable now" rule used by every booking path | Past-dated bookings via stale tabs or the API |
| A-09 | Cancelling an APPEARED visit reopens its slot | PB-007 | cancellation | C-02 | Decide whether a checked-in visit can be "cancelled" or should be a different outcome | Lost visit history; orphan untimed slots |
| A-10 | Flaky frontend tests | BUG-006 | frontend tests | — | Raise per-test timeouts or reduce per-test cost; make CI deterministic | Intermittent red CI |

## B. Security

| ID | Problem | Evidence | Area | Depends on | Suggested direction | Risk if ignored |
|---|---|---|---|---|---|---|
| B-01 | Fail-open filter chains; 7 endpoints fall through | SEC-01, BUG-001 | security config | — | Make `/clinics/**` and `/patients/**` deny or authenticate by default, with an explicit public list (register, login, signup); add an allowlist test (H-03) | A future endpoint ships public |
| B-02 | Secrets in the tracked `.claude/launch.json` working copy | SEC-02 | repo hygiene | — | Keep secrets out of tracked files (env or an untracked local file) **before** the next commit; rotate if ever pushed | Credential and JWT-key disclosure |
| B-03 | Cross-clinic fee and appointment-type writes | SEC-03, DM-09 | booking config | product decision | Decide whether fees are per clinic-doctor; if so, scope the data and the authorization by clinic | One tenant alters another's pricing |
| B-04 | Ex-staff doctor clinical access; documents on non-visits | SEC-06, PB-008 | clinical | C-02 | Require an active role plus an eligible visit state | Clinical record integrity and privacy |
| B-05 | Account enumeration at login | SEC-07 | auth | product decision (it was deliberately split) | Re-evaluate the trade-off; if kept, add per-account throttling | Targeted credential attacks |
| B-06 | Rate limiter: in-memory, per IP, no eviction | SEC-05, PB-006 | common | F-03 | Bounded storage, proxy-aware client key, per-account limits on login | Memory growth; shared-IP lockouts |
| B-07 | Token lifecycle: 12 h, no revocation, `sessionStorage`; `account.active` / `patient_account.active` unchecked | SEC-04 | auth | — | Decide on expiry, refresh and logout semantics; check account-level active flags | Stale sessions after offboarding |
| B-08 | Super Admin is a single shared credential | SEC-08 | admin | product decision | Per-person admin identities and an audit trail | No accountability for verification actions |
| B-09 | Public Swagger; dependency scanning skipped without `NVD_API_KEY` | SEC-09, SEC-10 | config, CI | — | Environment-specific exposure; configure the key | Information disclosure; unscanned CVEs |

## C. Data integrity

| ID | Problem | Evidence | Area | Depends on | Suggested direction | Risk if ignored |
|---|---|---|---|---|---|---|
| C-01 | Session has no lifecycle state | DM-02 | schema | — | Add an explicit session state (e.g. scheduled / cancelled / closed) with a forward-only migration and test-first coverage | Root cause of A-01…A-03 |
| C-02 | Visit state on `slot`, appointment state on `booking` | DM-01 | schema, domain | — | Design an explicit visit or appointment lifecycle (booked → checked-in → in consultation → completed / no-show / cancelled) owned by one entity | Ambiguous reporting; the UX-05/06 problems persist |
| C-03 | No CHECK constraints on status and enum columns | DM-04 | schema | C-01, C-02 (so the enum sets are final) | Add CHECKs for the persisted enums | Silent bad data |
| C-04 | Missing FK indexes | DM-07 | schema | — | Index the listed FK and filter columns; verify with `EXPLAIN` on real data | Slowdowns with volume |
| C-05 | Mixed timestamp types; FK-less actor columns; no `updated_at` | DM-05, DM-06, DM-10 | schema | K-05 (audit) | Normalise to `timestamptz`; reference actors by id; add modification timestamps | Weak auditability |
| C-06 | Dead fields (`on_hold`, `payment_status`, opt-ins without a writer) | DM-08 | schema | K-01, K-04 decisions | Either implement their owners or remove them via migration | Misleading model |
| C-07 | Clinic and doctor lifecycles as two booleans | DM-03 | schema | — | A single status or a CHECK against invalid combinations | Inconsistent states |
| C-08 | Clinical immutability not enforced in the DB | DM-11 | schema | — | Consider a DB-level guard (trigger or privileges) | Undetectable edits |

## D. Backend / API

| ID | Problem | Evidence | Area | Depends on | Suggested direction | Risk if ignored |
|---|---|---|---|---|---|---|
| D-01 | Authorization duplicated (16 methods, 37 calls) and spread into 26 controllers | TD-02, TD-03 | all modules | B-01 | One clinic-authorization component; move controller logic (e.g. the patient cancellation cutoff) into services | Inconsistent rules; the next endpoint forgets a check |
| D-02 | Duplicate exception classes and 10 unscoped advices | TD-04 | all modules | D-01 | Shared common exceptions and a single error mapping | Inconsistent status codes |
| D-03 | Inconsistent list envelopes; 16 unvalidated request bodies | API-02, API-03 | API | — | One pagination envelope; bean validation on all request DTOs (additive, versioned if clients break) | Client complexity |
| D-04 | Hand-built JSON payloads | API-11, DM-13 | notification | — | Serialise with Jackson | Escaping bugs if non-UUID data is added |
| D-05 | Missing operations | API-09 | API | product decisions | Staff reactivation, appointment-type deletion, manual no-show, single-session time edit (each specced) | Workarounds via deletion or cancellation |

## E. Frontend / UX

| ID | Problem | Evidence | Area | Depends on | Suggested direction | Risk if ignored |
|---|---|---|---|---|---|---|
| E-01 | Inconsistent visit-state vocabulary; DB terms shown to users | UX-01, UX-02 | day sheet, walk-in, patient pages | C-02 | One glossary for Check in / With doctor / Completed / No-show; apply it everywhere (see `DESIGN.md` and `PRODUCT.md` voice) | Training burden, errors |
| E-02 | A cancelled session is not visible as cancelled | UX-03 | day sheet | C-01, A-01 | Show the session state and block actions | Bookings into absences |
| E-03 | No manual no-show; completion unreliable | UX-04, UX-06 | day sheet, walk-in | C-02 | Explicit actions with confirmation and undo | Misleading live status |
| E-04 | Consultation split across 3 routes | UX-07 | doctor | C-02 | A single consultation workspace per visit | Slow doctor workflow |
| E-05 | Modal and route duplication; blank-page case | UX-08 | staff booking | — | One interaction pattern; handle missing parameters | Inconsistency |
| E-06 | Session expiry not handled | UX-11, PB-010 | all shells | E-07 | Global 401 handling → re-login with return path | Confusing failures |
| E-07 | Two HTTP layers (73 raw fetches, 30 `API_BASE_URL`) | TD-05 | frontend | — | Migrate all features to `lib/apiClient.ts` (backlog 043's stated goal) | Divergent error handling |
| E-08 | Staff with no clinic sees an empty dashboard; Super Admin on the clinic login screen | UX-14, UX-20 | login | — | Explicit messaging and routing | Confusion |
| E-09 | Generic error copy; 25-row day-sheet paging | UX-19, UX-18 | multiple | — | Actionable messages; review paging for the operational table | Slower front desk |
| E-10 | Browser-computed time rules | UX-21 | day sheet | A-07 | Derive "can act now" from server-provided state | UI and server disagree |

## F. Performance

| ID | Problem | Evidence | Area | Depends on | Suggested direction | Risk if ignored |
|---|---|---|---|---|---|---|
| F-01 | Per-minute full scans (no-show, auto-complete) over all dates | TD-07 | scheduling | A-07 | Bound the queries to the relevant time window, at database level | Growing CPU and DB load every minute |
| F-02 | Default EAGER loading on 40 relations | TD-06 | JPA | H-01 (integration safety net) | Measure, then selectively make relations lazy with fetch joins | N+1 and over-fetching on lists |
| F-03 | Single-instance components (SSE, rate limiter, schedulers) | TD-08 | infrastructure | deployment decision | Decide the deployment target; if more than one instance, externalise state and lock jobs | Duplicate jobs, missing SSE events |
| F-04 | 20 s polling in 4 places | UX-10 | frontend | F-03 | Consider extending the push channel, or a shared polling hook | Staleness and redundant requests |

## G. Accessibility

| ID | Problem | Evidence | Area | Depends on | Suggested direction | Risk if ignored |
|---|---|---|---|---|---|---|
| G-01 | Runtime accessibility unverified (lint only) | UX-22 | frontend | — | Keyboard and screen-reader audit of the core flows (walk-in, day sheet, booking) | Excludes some staff and patients |
| G-02 | Color-coded status badges (`STATUS_BADGE_CLASS`) | `SessionSlotsView.tsx` | day sheet | E-01 | Verify contrast and non-color cues | Status misread |

## H. Testing

| ID | Problem | Evidence | Area | Depends on | Suggested direction | Risk if ignored |
|---|---|---|---|---|---|---|
| H-01 | 249 integration classes never executed | 09 §3 | backend | I-01 | Run in CI (Docker available on ubuntu runners) and fix whatever fails before building further | "Converged" features may be broken |
| H-02 | Timing-flaky frontend tests | BUG-006 | frontend | — | Same as A-10 | Unreliable gate |
| H-03 | No allowlist-completeness test | 09 §4 | security | B-01 | A test that walks all request mappings and asserts unauthenticated → 401/403 | Repeat of BUG-001 |
| H-04 | Missing regression tests for the defects in 07 | 09 §4 | booking, scheduling | fixes | Test-first per defect (constitution) | Regressions |
| H-05 | No end-to-end tests of cross-role flows | 09 §5 | whole stack | H-01 | A small E2E suite for walk-in → inbox → send in → complete → patient live status | Integration drift |
| H-06 | No architecture tests | 09 §5 | backend | D-01 | Module-boundary and layering checks | Coupling grows (01 §2.3) |

## I. Documentation

| ID | Problem | Evidence | Area | Depends on | Suggested direction | Risk if ignored |
|---|---|---|---|---|---|---|
| I-01 | About 835 uncommitted changes; CI never ran on the current code | TD-13 | repository | B-02 | Commit in coherent, reviewed chunks after removing secrets | Loss of work; no history |
| I-02 | Documentation drift | 00 §14 | docs | — | Reconcile `README`, `PRODUCTION_ROADMAP`, `CLAUDE.md`, `HANDOFF` with the code | Misleading onboarding |
| I-03 | No end-user or operator documentation | 00 §14 | docs | E-01 | Staff guide (daily clinic flow), deployment and configuration guide | Adoption friction |
| I-04 | Duplicate spec numbering | TD-17 | specs | — | Resolve the two `003-*` directories | Traceability confusion |

## J. Maintainability

| ID | Problem | Evidence | Area | Depends on | Suggested direction | Risk if ignored |
|---|---|---|---|---|---|---|
| J-01 | Cyclic module dependencies (booking↔identity/waitlist/inbox/patient) | 01 §2.3 | backend | D-01 | Define the allowed dependency direction; extend event use where the constitution intends it | Changes ripple across modules |
| J-02 | Very large components (`PendingDoctorsList` 849, `PendingClinicsList` 770 lines) | TD-17 | frontend | — | Decompose along existing sub-features | Hard to change safely |
| J-03 | 24 lint warnings; 2 moderate advisories | TD-16 | frontend | — | Resolve the warnings; upgrade react-router within its major version | Warning fatigue |
| J-04 | Stale comments referencing removed features (buffer) | TD-09 | backend | — | Remove | Misleading readers |

## K. Missing product capabilities

| ID | Problem | Evidence | Area | Depends on | Suggested direction | Risk if ignored |
|---|---|---|---|---|---|---|
| K-01 | Notifications: 5 event types, stub delivery, no preferences, no event for staff cancellation or confirmation | 02 #42, UX-12 | notification | product decision (constitution boundary) | Decide the delivery scope; at minimum emit events for staff cancellation and confirmation; add a preference API | Patients uninformed |
| K-02 | Doctor availability exceptions (leave, holiday, ad-hoc session) | 02 #14 | scheduling | C-01 | Specify an availability-exception model | Workarounds via cancellation |
| K-03 | Check-in versus in-consultation distinction; manual no-show | UX-04, UX-05 | visit lifecycle | C-02 | Part of the lifecycle redesign | Inaccurate queue and live status |
| K-04 | Patient password reset (and staff self-service) | 02 #45, UX-13 | identity | K-01 (if email or SMS is required) | Decide the recovery channel (clinic-assisted versus self-service) | Permanent patient lockout |
| K-05 | Operational and clinical audit trail | 02 #46 | all | C-05 | Record actor and time for check-in, complete, cancel, verify and clinical creation | No accountability |
| K-06 | Reschedule | 02 #43, UX-09 | booking | product decision (constitution boundary) | If adopted: a hold-then-swap flow that does not expose the slot to the waitlist | Patients lose slots |
| K-07 | Payments | 02 #44 | booking | product decision (constitution boundary) | Keep out of scope, or specify it; either way resolve the dead `payment_status` (C-06) | Misleading data |
| K-08 | Standalone patient registration and staff-side dedupe | 03 W3 | patient | A-05 | A "register patient" task with duplicate detection | Duplicate records |
