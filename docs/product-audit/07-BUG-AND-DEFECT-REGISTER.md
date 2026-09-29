# 07 — Bug and Defect Register

Labels: [RUNTIME] = observed while running · [CODE] = read in source · [INFERRED] = drawn from code or docs, not proven · [UNKNOWN] = could not be determined.

**Categories:**
- **CONFIRMED BUG** — reproduced at runtime, *or* a deterministic code path with no alternative branch. The latter is marked "code-traced", meaning the path was proven by reading, not by execution.
- **POTENTIAL BUG** — code strongly suggests the defect, but it depends on data, concurrency or deployment conditions not reproduced here.
- **DESIGN/UX ISSUE** — see [06](06-UI-UX-AUDIT.md) (UX-01…UX-22). Summarised in §3.
- **TECHNICAL DEBT** — §4.

Severity: Critical / High / Medium / Low. No bugs were invented. Anything unproven is POTENTIAL.

## Remediation status — Phase 1 (spec `specs/065-phase1-stabilization`, 2026-09-29)

| ID | Status | Root cause | Fix | Tests | Verification |
|---|---|---|---|---|---|
| BUG-001 / SEC-01 | **CONFIRMED FIXED** | Staff and patient chains ended in `anyRequest().permitAll()`; unlisted endpoints were anonymous | Both chains are fail-closed: explicit public endpoints (`POST /clinics/register`, `POST /patients/signup`, `/login`), then `anyRequest().authenticated()` | `StaffChainFailClosedContractTest` (11), `PatientChainFailClosedContractTest` (4). The 10 failures reproduced BUG-001 before the fix. | Unit and contract tests green. Runtime: the 7 endpoints plus unmapped paths → 401; public endpoints → 400 (validation); discovery and health → 200; CORS preflight → 200. |
| PB-008 (creation half) / SEC-06 | **CONFIRMED FIXED** | `TreatingDoctorAuthorizationService` compared identity only; spec 034 had scoped out a staffing re-check | New `requireActiveTreatingDoctor` (treating doctor plus an active Doctor role at the booking's clinic), used by the 3 `create` paths. Reads unchanged. | `TreatingDoctorAuthorizationServiceTest` (4), one create-refusal case in each of 3 clinical service tests, integration `ConsultationNoteDeactivatedDoctorTest` | Unit tests green. Integration test: see the 2026-09-29 Docker run under the table. |
| PB-008 (booking-state half) | OPEN — out of Phase 1 scope | — | — | — | — |
| PB-005 | **INVESTIGATED — NO CHANGE IN PHASE 1**; broader fix **BLOCKED — REQUIRES DECISION** | The product targets Indian clinics only. The repository defines no deployment that sets a non-IST JVM zone. All rules use the JVM default zone consistently, so the defect appears only on a non-IST host, which no configuration defines. | New rules (planned for the V41-dependent work) use an injectable `Clock` in the same zone | — | Decision needed: pin `-Duser.timezone=Asia/Kolkata` in deployment, or model a clinic time zone |
| BUG-006 | **MITIGATED — intermittent, root cause addressed** | user-event's per-keystroke `setTimeout(0)` yield plus re-render cost over about 60 typed characters. Under full-suite parallel load the tests slowed about 4× past the 5 s default. Timers, awaits, mocks and cleanup were checked and excluded (09 §4a). | `userEvent.setup({ delay: null })` in 6 typing-heavy test files; global timeout unchanged | 6 test files changed; no assertions altered | 4 consecutive full runs at 436/436; affected tests 2–3× faster in isolation |
| BUG-002 | **CONFIRMED FIXED** | Spec 029 deliberately returned cancelled slots to `OPEN` and had no session-level record, so no listing or booking path could tell "cancelled" from "never booked" | Migration V41 adds the insert-only `session_cancellation` table (whole record: `from_time` NULL). `SessionAvailabilityService` is the one bookability rule; all 5 booking paths (patient and staff fixed-time, patient and staff queue, walk-in) refuse a whole-cancelled session with 409 `SESSION_NOT_ACCEPTING_BOOKINGS`. Patient slot and queue-session listings exclude it (`NOT EXISTS`). Waitlist matching skips it. A session with a record cannot be deleted, so the nightly job cannot regenerate it. | `SessionAvailabilityServiceTest`, `SessionCancellationServiceTest`, `BookingPathsAvailabilityTest`; integration `SessionAvailabilityIntegrationTest` (9 cases), updated `SessionCancellationRejectionTest` | Unit tests green. **Integration tests executed with Docker (2026-09-29): 9/9 pass.** Runtime on a fresh database: whole-cancelled day lists 0 patient slots; repeat cancel → 409 `SESSION_ALREADY_CANCELLED`; delete → 409 `SESSION_DELETION_BLOCKED` |
| BUG-003 | **CONFIRMED FIXED** | Spec 030 kept the `[cutoff, toTime)` range only as a request parameter; nothing persisted it | Each partial cancellation writes a range record (even with 0 qualifying bookings). Ranges are cumulative. Fixed-time slots starting inside a range, and queue tokens or walk-ins requested inside it on the session date, are refused and unlisted. | `SessionPartialCancellationServiceTest`, `BookingPathsAvailabilityTest`, `SessionAvailabilityIntegrationTest` | Unit and integration tests green. Runtime: after cancelling 14:00–16:00, the patient listing omits 14:00 and 15:00 only; staff booking of the 14:00 slot → 409 `SESSION_NOT_ACCEPTING_BOOKINGS`; the day sheet marks both rows "Cancelled" with no Book link |
| BUG-004 | **CONFIRMED FIXED** | `cancelSession` treated "no BOOKED slot" as "already cancelled"; the frontend button also pre-blocked it | "Already cancelled" now means a whole record exists. An empty session (or one with only APPEARED/NO_SHOW visits) cancels with 0 bookings and a record. `CancelSessionButton` no longer pre-blocks; the day sheet shows a cancelled banner and hides booking actions. Spec 065 reverses spec 029's rule; `SessionCancellationRejectionTest` updated accordingly. | `SessionCancellationServiceTest`, `CancelSessionButton.test.tsx`, `SessionSlotsView.test.tsx`, `SessionCancellationRejectionTest`, `SessionAvailabilityIntegrationTest` | Unit, frontend and integration tests green. Browser (Playwright, staff login): "Cancel entire session" on an empty session → "Session cancelled. No active bookings needed cancelling."; banner persists after reload |
| BUG-005 | **CONFIRMED FIXED** | Listings filtered on date only; `doBookSlot` checked date only; staff booking had no date check | A timed slot is elapsed when `start_time < now` (a start equal to now stays bookable, owner decision 5). Listing JPQL adds `(sessionDate > :from OR startTime > :nowTime)`. Patient and staff fixed-time booking refuse elapsed and past slots with `SLOT_DATE_IN_THE_PAST`; queue and walk-in refuse past dates. Time comes from an injectable `Clock` (same JVM zone as the rest of the server, see PB-005). | `SessionAvailabilityServiceTest` (fixed clock), `BookingPathsAvailabilityTest`, `SessionAvailabilityIntegrationTest` | Unit and integration tests green. Runtime at 08:02 server time: today's patient listing starts at 09:00 (06:00–08:00 hidden); staff booking of the 06:00 slot → 409 `SLOT_DATE_IN_THE_PAST` |

---

## 1. Confirmed bugs (6)

### BUG-001 — Staff endpoints missing from the security allowlist return 500 when unauthenticated

- **Severity:** High
- **Area:** Security / API
- **Confidence:** Confirmed [RUNTIME]

**Reproduction:** with the backend running, call without an `Authorization` header:
- `GET /api/v1/clinics/{clinicId}/bookings/{id}`
- `GET /api/v1/clinics/{clinicId}/doctors/booking-readiness`
- `GET /api/v1/clinics/{clinicId}/waitlist/count`
- `POST /api/v1/clinics/{clinicId}/staff/{id}/reset-password`
- `POST /api/v1/clinics/{clinicId}/staff/{id}/set-password`
- `DELETE /api/v1/clinics/{clinicId}/sessions/{id}`
- `DELETE /api/v1/clinics/{clinicId}/doctors/{d}/schedules/{s}`

**Expected:** 401 UNAUTHORIZED, as neighbouring endpoints return.
**Actual:** 500 `INTERNAL_SERVER_ERROR`.

**Root cause:**
- `identity/account/config/SecurityConfig.java:143-276` ends with `anyRequest().permitAll()`, and these paths are not in its explicit `authenticated()` list.
- The request reaches the controller, where `SecurityConfig.currentAccountId()` throws `IllegalStateException` (lines 297-305).

**Impact:**
- No data exposure today, because every one of these controllers extracts the caller first.
- Monitoring sees 500s.
- Any future controller that does not extract the caller first would be **publicly reachable**.

**Files:** the security config above; `BookingDetailController`, `DoctorBookingReadinessController`, `StaffWaitlistController`, `StaffPasswordResetController`, `SessionDeletionController`, `ScheduleDeletionController`.

### BUG-002 — Whole-session cancellation leaves the session bookable

- **Severity:** High
- **Area:** Scheduling / Booking
- **Confidence:** Confirmed, code-traced

**Reproduction (logical):**
1. Staff cancel a session with bookings through `POST /clinics/{c}/sessions/{s}/cancel`.
2. Patient lists slots through `GET /patients/clinics/{c}/slots`.

**Expected:** the cancelled session no longer accepts bookings. Backlog 026: "Cancels every slot/booking within a full session".

**Actual:**
- `SessionCancellationService.cancelSession` sets each formerly BOOKED slot to `SlotStatus.OPEN`.
- `session` has no status column.
- `SlotRepository.findOpenFixedTimeSlots[OnDate]` and `PatientBookingService.doBookSlot` accept OPEN slots with no session-state filter.

**Impact:** new bookings can be made for a doctor who is known to be absent. Queue sessions are affected the same way through the token APIs.

**Files:** `booking/service/SessionCancellationService.java`, `scheduling/repository/SlotRepository.java:146-200`, `booking/service/PatientBookingService.java` (`doBookSlot`).

### BUG-003 — Partial (from-cutoff) cancellation reopens the cancelled range

- **Severity:** High
- **Area:** Scheduling / Booking
- **Confidence:** Confirmed, code-traced

**Actual:** `SessionPartialCancellationService.java:85` sets each cancelled slot to `OPEN`. Same consequence as BUG-002 for the range after the cutoff.

**Expected:** the range after the cutoff is unavailable.

### BUG-004 — A session with no BOOKED slots cannot be cancelled ("already cancelled")

- **Severity:** Medium
- **Area:** Scheduling
- **Confidence:** Confirmed, code-traced

**Actual:** `SessionCancellationService.cancelSession` throws `SessionAlreadyCancelledException` whenever the BOOKED filter is empty. That includes a never-booked future session, and a session whose visits are all APPEARED or NO_SHOW.

**Expected:** the ability to mark a doctor unavailable for an empty session. Today the only option is deletion, which is ClinicAdmin-only.

**Impact:** a misleading error; an empty session stays open for booking.

### BUG-005 — Patients are offered, and can book, same-day slots whose start time has passed

- **Severity:** Medium
- **Area:** Patient booking
- **Confidence:** Confirmed, code-traced

**Evidence:**
- `SlotRepository.findOpenFixedTimeSlots` filters on `sessionDate >= :from` (today) with no time filter.
- `PatientBookingService.doBookSlot` rejects only `sessionDate.isBefore(LocalDate.now())`.
- OPEN slots are never moved to another status by any job.

**Expected:** elapsed slots are hidden and rejected.

**Actual:** the slot is listed and bookable. `NoShowDetectionService` then marks the new booking NO_SHOW within about 1 minute, because start + 10 min is already past. That no-show may feed `REPEATED_NO_SHOWS` protection flags [INFERRED].

### BUG-006 — Frontend tests time out under full-suite load

- **Severity:** Low
- **Area:** Test infrastructure
- **Confidence:** Confirmed [RUNTIME]

**Reproduction:** `cd frontend && npm run test` on this machine → 6 failed / 430 passed, all `Test timed out in 5000ms`, in:
- `tests/clinic-registration/RegistrationForm.test.tsx` (4)
- `tests/scheduling/ScheduleForm.test.tsx` (1)
- `tests/external-record-references/ExternalRecordReferenceForm.test.tsx` (1)

The same 3 files alone: 16/16 pass.

**Root cause [INFERRED]:** Vitest's default 5 s timeout is too tight for `user-event`-heavy form tests when the full suite runs in parallel (reported environment time: 546 s).

**Impact:** a CI red/green that depends on machine load. HANDOFF.md's "436 tests pass" is load-dependent.

---

## 2. Potential bugs (10)

| ID | Sev. | Area | Description | Evidence / root cause | Expected | Impact | Confidence |
|---|---|---|---|---|---|---|---|
| PB-001 | Medium | Staff booking | A new patient whose phone equals an existing *unlinked* patient's phone at the same clinic is reported as **"slot already booked"** | `StaffBookingService.resolveOrCreatePatient` uses `patientRepository.save` (INSERT deferred until flush, UUID generator). The flush happens at `bookingRepository.saveAndFlush`, inside `catch (DataIntegrityViolationException) → SlotAlreadyBookedException` (`StaffBookingService.java:92-104`). The violation comes from `uq_patient_clinic_phone_unlinked` (V5). | "This phone belongs to an existing patient" | Wrong error; staff retry other slots | High (code-traced; not executed) |
| PB-002 | Medium | Walk-in, staff queue booking | The same phone collision in `FrontDeskWalkInService.register` and `StaffQueueBookingService` has **no handler** | No `DataIntegrityViolationException` catch in either; `ApiErrorController` → 500 | Specific 409 | Generic failure at the last step (UX-17) | High (code-traced) |
| PB-003 | Medium | Queue tokens / walk-ins | The retry-on-collision loop is ineffective when called inside an outer transaction | `QueueSlotService.issueWithRetry` calls `this.attemptIssueSlot` (a self-invocation, so `@Transactional` is not applied). `slotRepository.save` joins the caller's transaction, and the INSERT flushes later at `booking saveAndFlush`, outside the loop. After a constraint error the outer transaction is rollback-only anyway. | Concurrent token requests are serialised or retried | Two simultaneous walk-ins or queue bookings for one session: one fails | Medium. `QueueSlotIssuanceConcurrency` exists but is unexecuted here, and may call the service outside a transaction. |
| PB-004 | Medium | Booking APIs | No date or state guard on `StaffBookingService`, `StaffQueueBookingService`, `PatientQueueBookingService` or `FrontDeskWalkInService` | No `sessionDate` or `LocalDate` reference in those services [CODE grep]. The UI lists only upcoming or today's sessions. | Bookings refused for past or ended sessions | Direct API calls or stale tabs create past bookings, which the no-show job then processes | High (code), Low (UI likelihood) |
| PB-005 | High (if deployed in UTC) | Time handling | All time rules use the JVM default zone | `LocalDateTime.now()` (17 call sites), `ZoneId.systemDefault()` (`SessionPartialCancellationService:112,125`); no clinic time zone, no `user.timezone` config | Clinic-local evaluation | On a UTC server: no-show marking, the 2 h cancellation cutoff, auto-completion and "not yet started" shift by 5 h 30 min | Medium (deployment-dependent) |
| PB-006 | Low | Rate limiting | Per-IP window map never evicts; behind a proxy all clients share one key | `RateLimitingFilter.java:47` `ConcurrentHashMap`, no removal; key `request.getRemoteAddr()` (line 70) | Bounded memory; real client IP | Memory growth under many IPs; a shared-IP lockout of all users behind a reverse proxy | Medium |
| PB-007 | Low | Cancellation | Cancelling an APPEARED booking resets the slot to OPEN | `BookingCancellationService.doCancel` allows BOOKED or APPEARED → OPEN | A seen patient's visit history is preserved | A timed slot reopens (bookable if not elapsed — see BUG-005); an untimed walk-in slot becomes an orphan OPEN row; `appeared_at` stays on the slot (DM-01) | High (code) |
| PB-008 | Medium | Clinical records | Notes, prescriptions and external references accepted for cancelled, future or no-show bookings, and by a doctor no longer active at the clinic | `TreatingDoctorAuthorizationService.requireTreatingDoctor` compares account id only; `ConsultationNoteService.create` has no status check | Documents only for real visits by active staff | Write-once clinical records attached to visits that never happened; ex-staff access | Medium (intent not documented either way) |
| PB-009 | Medium | Verification | Unverified clinics and licence-revoked doctors remain bookable through the patient API | `PatientBookingService.requireClinicAcceptingAppointments` checks only `isRejected()`; no `licenseVerified` check anywhere in booking | After a de-verification cascade, no new bookings | The cascade cancels bookings, then new ones can be made immediately | Medium (verification is documented as discovery-only; the post-cascade behaviour is unaddressed) |
| PB-010 | Low | Frontend session | Expired tokens are not detected | Guards check only `sessionStorage` presence; `apiClient` has no 401 hook; 29 files use raw `fetch` | Redirect to login | Error text instead of re-authentication (UX-11) | High (code) |

---

## 3. Design / UX issues (22)

Full detail in [06-UI-UX-AUDIT.md](06-UI-UX-AUDIT.md). By ID:

| ID | Issue |
|---|---|
| UX-01 | Visit-state vocabulary inconsistent |
| UX-02 | Internal concepts surfaced to users |
| UX-03 | Cancelled sessions are not representable |
| UX-04 | No manual no-show |
| UX-05 | Check-in and consultation conflated |
| UX-06 | Completion unreliable (skippable check-in, auto-complete) |
| UX-07 | Clinical documents split across three routes |
| UX-08 | Modal versus route duplication; possible blank page |
| UX-09 | No reschedule |
| UX-10 | 20 s polling |
| UX-11 | Session expiry not handled |
| UX-12 | No patient notifications for staff actions |
| UX-13 | Forgot-password dead end |
| UX-14 | Empty dashboard for staff with no clinic |
| UX-15 | Waitlist offers for de-verified providers |
| UX-16 | Elapsed slots offered |
| UX-17 | Advisory-only duplicate phone check |
| UX-18 | 25-row day-sheet paging |
| UX-19 | Generic error copy |
| UX-20 | Super Admin on the clinic sign-in screen |
| UX-21 | Browser versus server time |
| UX-22 | Runtime accessibility unverified |

---

## 4. Technical debt (17)

| ID | Item | Evidence |
|---|---|---|
| TD-01 | Fail-open allowlist pattern in the staff and patient filter chains (root cause of BUG-001) | `SecurityConfig` ×2 |
| TD-02 | Business rules and authorization in 26 controllers that inject repositories | 01 §2.2 |
| TD-03 | 16 copy-pasted role-check methods; 37 role-existence calls | grep |
| TD-04 | Duplicate exception types (7 `ForbiddenException`, 5 `NotStaffedAtClinicException`, 4 `InvalidPasswordException`, 3 `InvalidMobileNumberException`, 3 `DoctorProfileNotFoundException`, 2 `PasswordPolicyValidator`, 2 `ErrorResponse`); 12 advices, 10 unscoped | `find` |
| TD-05 | Two frontend HTTP styles (12 files `apiRequest` vs 73 raw `fetch` calls in 29 files); `API_BASE_URL` ×30 | grep |
| TD-06 | Default EAGER fetch on all 40 JPA relations | grep |
| TD-07 | Per-minute full scans (no-show, auto-complete, waitlist expiry) with Java-side filtering | 01 §2.5 |
| TD-08 | Single-instance assumptions: in-memory SSE registry, in-memory rate limiter, unlocked `@Scheduled` jobs | 01 §8 |
| TD-09 | Dead or unused fields: `payment_status`, `slot.on_hold`, opt-in flags; stale "buffer" comments | 04 DM-08 |
| TD-10 | Missing DB CHECKs, FK indexes, `updated_at`; mixed timestamp types | 04 §5 |
| TD-11 | Visit state stored on `slot` | 04 DM-01 |
| TD-12 | Documentation drift (README, PRODUCTION_ROADMAP, CLAUDE.md, HANDOFF) | 00 §14 |
| TD-13 | About 835 uncommitted working-tree changes (`git status`: 326 M, 239 RM, 245 untracked, 16 D, 9 RD) | [RUNTIME] |
| TD-14 | 249 integration test classes never executed in the available environments | [RUNTIME] 09 |
| TD-15 | 16/35 request bodies without bean validation; inconsistent list envelopes | 05 §2 |
| TD-16 | 24 oxlint warnings (for example `react(set-state-in-effect)` in `ProtectionSettingsPage.tsx:43`, `VisitRecordSection.tsx:30`, `ProtectionFlagsList.tsx:204`, `StaffPicker.tsx:81`); 2 moderate npm advisories (react-router) | [RUNTIME] |
| TD-17 | Duplicate spec numbering (`specs/003-super-admin-clinic-verification` and `specs/003-super-admin-verification`); two very large components (`PendingDoctorsList.tsx` 849, `PendingClinicsList.tsx` 770 lines) | `ls`, `wc` |
