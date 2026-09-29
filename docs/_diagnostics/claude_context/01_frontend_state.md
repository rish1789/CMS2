# Frontend State — Race Conditions, Staleness, UX Gating

Findings specific to component-local state management, effect cleanup, polling, and role-aware
rendering. Findings that are instances of the shared error-display patterns are documented in
`00_cross_cutting_patterns.md`, not repeated here.

---

**[MEDIUM] - [INBOX_SSE] - [SILENT_FAILURE]**
- Frontend: `frontend/src/features/inbox/api.ts:104-129` — `openInboxStream`'s reader loop `break`s on `done`; its `.catch()` (126-129) swallows any stream error with a comment "nothing to surface here." No retry/backoff exists anywhere in `features/inbox`. `InboxPage.tsx:17-48` never receives any error/close signal — `openInboxStream`'s return type is only `{ close: () => void }`.
- Backend: `InboxBroadcastService.java:22,27,32-34` — every `SseEmitter` has a hard 30-minute `TIMEOUT_MILLIS`; `onTimeout` only deregisters it, it does not keep the connection alive.
- Break: A staff member who leaves the Inbox tab open past 30 minutes (a plausible real front-desk scenario) silently stops receiving live updates for the rest of the session, with zero visual indication — directly contradicting spec SC-001 ("appears within 5 seconds, no manual refresh"), which then silently becomes false.
- Fix: have `openInboxStream` accept an `onDisconnect`/`onError` callback; `InboxPage` renders a "Live updates paused — reconnecting…" banner; implement a reconnect loop (immediate resubscribe is cheap since `listInboxItems` re-syncs full state on reconnect).
- **STATUS: FIXED, 2026-09-07.** `openInboxStream` now takes an `onStatusChange` callback and retries via `setTimeout(connect, 1000)` in a `.finally()` whenever the stream ends without an explicit `close()`. `InboxPage.tsx` renders the "Live updates paused — reconnecting…" banner via a new `live` state flag. Full frontend suite green (116/116).

**[MEDIUM] - [INBOX_SSE] - [MISSING_ERROR_HANDLING]**
- Frontend: `frontend/src/features/inbox/api.ts:104-106` — `openInboxStream`'s fetch handler never checks `response.ok`/`response.status` before treating the body as an SSE byte stream. Contrast `listInboxItems` (65-73), which correctly checks `!response.ok`.
- Backend: `InboxController.java:31-35` — the `stream` endpoint can return `403 {"error":"FORBIDDEN"}` (via `InboxExceptionHandler.java:13-16`) for an unauthorized caller, or any proxy/5xx error.
- Break: A non-2xx response's JSON error body gets parsed as SSE text — `split('\n\n')`/`startsWith('data:')` find nothing — and is silently dropped, indistinguishable from "no updates right now." This means even the *first* connection attempt can fail invisibly, independent of the 30-minute timeout issue above.
- Fix: check `response.ok` before starting the reader loop; on non-2xx, invoke an `onError` callback with the parsed error body.
- **STATUS: FIXED, 2026-09-07.** `openInboxStream` now checks `response.ok` before touching the reader at all and returns early (triggering the reconnect-loop fix above) on a non-2xx response, instead of feeding a JSON error body into the SSE frame parser.

**[MEDIUM] - [INBOX_LIST] - [RACE_CONDITION]**
- Frontend: `frontend/src/features/inbox/InboxPage.tsx:17-48` — `listInboxItems(...)` (21) and `openInboxStream(...)` (29) fire concurrently on mount with no coordination. The GET's `.then` (22-24) **unconditionally replaces the entire `items` array**; the SSE handler (29-42) does a per-item merge.
- Backend: `InboxItemResponse` (`dto/InboxItemResponse.java:17-24`) carries no version/sequence/timestamp field a client could use to order two concurrent snapshots.
- Break: If a broadcast for item X (e.g. a claim by another staff member) arrives over SSE before the slower initial GET resolves, the GET's stale snapshot (captured before the claim) can land afterward and wholesale-overwrite the array — silently reverting X to `UNCLAIMED` in the UI. The "Claim" button reappears for an item that is actually already claimed, self-correcting only if another broadcast happens to touch that item again later.
- Fix: merge the initial GET result using the same "replace-by-id, keep-newer" logic already used for SSE items, rather than a blind array replace; or sequence so the GET always completes before stream updates apply.
- **STATUS: FIXED, 2026-09-07.** `InboxPage.tsx` now tracks a `touchedIds` ref of every id the SSE stream has reported on since mount; the initial GET's result is merged (only additions for ids neither already-present nor already-touched by SSE), never a blind replace — so a claim/release/resolve broadcast that arrives before the slower initial GET resolves can never be silently reverted by that GET's now-stale snapshot.

**[MEDIUM] - [INBOX_CLAIM] - [STALE_STATE]**
- Frontend: `frontend/src/features/inbox/InboxPage.tsx:54-65` — the shared `act()` helper's `.catch((e) => setError(...))` only sets a generic error string; it never refetches or updates the affected item in local `items` state.
- Backend: `InboxItemService.java:84-93` / `InboxItemRepository.java:25-28` — a lost claim race throws `AlreadyClaimedException` (409); the response to the loser carries no item data.
- Break: After a losing claim/release/resolve attempt, local `items` state still reflects pre-conflict data (e.g. `status: 'UNCLAIMED'`), so the "Claim" button stays visible and clickable, letting the user retry into the same 409 repeatedly. This compounds with the two SSE findings above — if the stream has silently died, this stale button can persist indefinitely.
- Fix: on any 409 (`ALREADY_CLAIMED`/`NOT_CLAIMANT`), trigger a targeted refetch of that item (or the full list) to reconcile local state instead of relying solely on an eventually-consistent broadcast.
- **STATUS: FIXED, 2026-09-07.** `InboxPage.tsx`'s `act` helper now calls `listInboxItems` again (full-list reconciliation) whenever the rejection is `ALREADY_CLAIMED`/`NOT_CLAIMANT`, replacing local state with the fresh snapshot. Existing `InboxPage.test.tsx` cases for both codes updated to assert the reconciled (now-correct) button/status, not just the error message.

**[MEDIUM] - [QUEUE_POSITION] - [NO_POLLING]**
- Frontend: `frontend/src/features/queue-position/QueuePositionIndicator.tsx:15-42` — the `useEffect` fetches exactly once per mount/prop-change; there is no `setInterval` anywhere in the file. The component's only refresh path is an externally-supplied `refreshKey` prop that nothing in the codebase currently bumps.
- Backend: `QueuePositionService.java:10-17` (doc comment: "Computed fresh on every call, never stored") and `:38-43` — the value shifts continuously as walk-ins/bookings/completions happen elsewhere in the session.
- Break: A patient or staff member watching this indicator sees a number that is correct only at the instant of page load and then goes stale indefinitely with no way to know it's stale, unless something happens to bump `refreshKey` (nothing does — see `04_integration_gaps.md`).
- Fix: add a `setInterval` (15-30s) inside the effect with matching cleanup in the return function.
- Note (verified correct): the existing `cancelled`-flag guard around both `.then`/`.catch` `setPosition` calls correctly prevents a post-unmount state-update warning — the bug is the *absence* of periodic refresh, not a leak in what already runs.
- **STATUS: FIXED, 2026-09-07.** Added a 20-second `setInterval` inside the effect (extracted the fetch into a named `fetchPosition` function, called once immediately and then on the interval), with `clearInterval` in the effect's cleanup alongside the existing `cancelled` guard.

**[MEDIUM] - [STAFF_WORKFLOWS] - [ROLE_MISMATCH]**
- Frontend: `frontend/src/features/staff-login/token.ts:11-15` — `StoredStaffSession { token, accountId, email }` carries no role field at all. `frontend/src/features/staff-booking/WalkInForm.tsx:83-89`, `frontend/src/features/session-cancellation/CancelSessionButton.tsx:37-39`, `frontend/src/features/partial-session-cancellation/CancelFromCutoffForm.tsx:39-41` each gate rendering only on `if (!session)`.
- Backend: `WalkInInsertionService.java:187-197`, `SessionCancellationController.java:48-58`, `SessionPartialCancellationController.java:53-63` all restrict to Operations-or-ClinicAdmin, explicitly excluding Doctor — vs. `StaffBookingCancellationController.java:33-36`, which allows any active role including Doctor.
- Break: A Doctor sees fully-interactive walk-in/session-cancel/partial-cancel controls that will always 403, with no proactive indication the action isn't available to them. (Severity kept MEDIUM, not HIGH: each flow's `defaultMessageFor` already renders a specific, correctly-worded `FORBIDDEN` message on the reactive path, e.g. "Only front-desk Operations staff or a ClinicAdmin can cancel this session." — reactive-but-clear, not broken.)
- Fix: have staff login return the caller's role(s) per clinic so these components can conditionally hide/disable themselves for a Doctor.

**[MEDIUM] - [PATIENT_ANONYMIZATION] - [RACE_CONDITION]**
- Frontend: `frontend/src/features/patient-anonymization/AnonymizePatientButton.tsx:65-71` — the "Cancel" button has no `disabled={submitting}` (unlike "Confirm" at line 60), so it stays clickable while a confirm request is in flight. `handleConfirm` (18-37) has no cancellation token — its `.then`/`.catch` run unconditionally regardless of what the user does afterward.
- Backend: `PatientAnonymizationService.java:29-46`, `Patient.java:101-109` — once the request reaches the server it executes and cannot be undone by a client-side cancel.
- Break: If a user clicks "Confirm" then quickly clicks "Cancel" before the response returns, the UI reverts to the initial button (implying nothing happened), but the in-flight request still completes server-side. When the response lands, the component unconditionally flips to "Patient anonymized." or shows an error, contradicting the screen state the user believes they backed out of.
- Fix: disable "Cancel" while `submitting` is true (it cannot actually cancel the server-side effect once fired), or track a cancelled flag so a post-cancel resolution doesn't mutate visible state.
- **STATUS: FIXED, 2026-09-07.** Added `disabled={submitting}` to the "Cancel" button, matching "Confirm"'s existing guard.
- Note (mitigating, verified clean): backend-side double submission is safe regardless — `Patient.anonymize()` is idempotent and the service short-circuits on an already-anonymized patient. This is a UI-state bug, not a data-integrity risk.

**[LOW] - [CLINIC_VERIFICATION / DOCTOR_VERIFICATION] - [RACE_CONDITION]**
- Frontend: `frontend/src/features/clinic-verification/PendingClinicsList.tsx:90-110`; `frontend/src/features/doctor-verification/PendingDoctorsList.tsx:103-119,121-137,150-176` — all handlers share one `actioningId` state and unconditionally `setActioningId(null)` in `finally`.
- Backend: `ClinicVerificationController.java:34-44`, `DoctorVerificationController.java:38-56` — independent per-row async endpoints.
- Break: Triggering an action on row B while row A's request is still in flight — whichever resolves first clears the shared busy-flag, re-enabling row B's button while its own request is still pending, allowing a duplicate submit for B.
- Fix: track in-flight state per-row (e.g. a `Set<string>` of ids) instead of one shared id.
- Note (mitigating, verified clean): every backend action here (verify/unverify/revoke) is idempotent — a duplicate call is a harmless no-op, not data corruption. Cosmetic/UX severity only.

---

## Verified Clean (no race/staleness defect found)

- **Discovery search debounce**: `DiscoverySearch.tsx:12-40` — a genuinely correct, race-free implementation: a `cancelled` flag *and* a cleared `setTimeout` on every keystroke means a stale in-flight response can never overwrite newer results.
- **SSE frame parsing mechanics**: `inbox/api.ts:108-124` correctly buffers partial chunks across `reader.read()` calls, uses `TextDecoder`'s streaming mode (safe for multi-byte UTF-8 split across chunks), and wraps `JSON.parse` in try/catch so a malformed frame can't throw uncaught.
- **Account-identity sourcing for Inbox claim/release/resolve gating**: traced end-to-end from JWT issuance (`StaffJwtService.java:40`) through login response (`StaffLoginResponse.java:5`) through `sessionStorage` (`StaffLoginForm.tsx:32-37`) to the comparison site (`InboxItemCard.tsx:26`, `item.claimedByAccountId === currentAccountId`) — all four points reference the identical account ID; the client-side "is this mine" check is reliable.
- **Double-submit guards**: all booking forms (staff and patient, fixed-time and queue) and the anonymization Confirm button correctly set `submitting=true` synchronously before the network `await` and bind `disabled={submitting}`, closing the practical double-click race.
- **`useEffect` cleanup for fast tab-switch/credential-change**: clinic-verification and doctor-verification's list-fetching effects both correctly guard with a `cancelled` flag.
