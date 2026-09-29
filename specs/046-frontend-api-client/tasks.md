---

description: "Task list for Frontend Shared API Client"
---

# Tasks: Frontend Shared API Client

**Input**: Design documents from `/specs/046-frontend-api-client/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md (N/A), quickstart.md

**Tests**: Per Constitution Principle I, the message-priority fix (US1) is real behavior change and needs new tests proving the fixed priority (research.md Decision 4) — written per migrated feature that has message-driven errors.

**Organization**: US2 (the shared client itself) is foundational for US1 (the fix only exists once the client exists) — built first as Phase 2, then each of the 4 migrated features is its own phase under US1.

## Phase 1: Setup

Not needed — no new dependency, just a new source directory.

## Phase 2: Foundational — the shared client (blocks all migrations)

**Purpose**: `apiClient.ts` must exist before any feature can be migrated onto it.

- [X] T001 Create `frontend/src/lib/apiClient.ts` per research.md Decision 1: `ApiError` class (status, message, body) and `apiRequest<T>(path, options)` function — builds URL from a single `API_BASE_URL` constant, injects `Authorization` when `token` is supplied, parses non-2xx response bodies once, resolves the error message per research.md Decision 2 (backend `message` → caller fallback → generic).
- [X] T002 Added `frontend/tests/lib/apiClient.test.ts` (6 tests) covering: successful 2xx parse; backend `message` field wins; caller fallback used when no backend message; unparseable body falls back to generic status message; `Authorization` header present only when a token is supplied; `ApiError` carries status+body. All 6 pass.

**Checkpoint**: The shared client exists, is tested in isolation, and is ready for features to migrate onto.

---

## Phase 3: User Story 1 - See the backend's actual error message (Priority: P1) 🎯 MVP

**Goal**: 4 representative features migrated onto the shared client, with the message-priority bug fixed and demonstrated.

### M1: booking-detail (37 lines, simplest — status-only error today)

- [X] T003 [US1] Migrated `frontend/src/features/booking-detail/api.ts`'s `getBookingDetail` to call `apiRequest`. Decision: replaced `BookingDetailApiError` with the shared `ApiError` directly (only one test file imported it by name; the component itself didn't) — updated that one construction site rather than keeping a redundant single-purpose subclass.
- [X] T004 [US1] Ran `frontend/tests/booking-detail/BookingContextHeader.test.tsx` — passes (2/2), with the one expected import/construction update from T003.

### M2: partial-session-cancellation (69 lines)

- [X] T005 [US1] Migrated `frontend/src/features/partial-session-cancellation/api.ts` to call `apiRequest`; removed `PartialCancellationApiError`, kept `defaultMessageFor` as the fallback-message function passed via `fallbackMessage`. Updated `CancelFromCutoffForm.tsx`'s `instanceof` check and import (it did consume the class) to use the shared `ApiError`.
- [X] T006 [US1] Ran `frontend/tests/partial-session-cancellation/CancelFromCutoffForm.test.tsx` — passes (7/7), with 2 error-construction sites updated to `ApiError`.

### M3: patient-booking (the confirmed-bug file)

- [X] T007 [US1] Migrated all 3 exported functions (`listQueueSessions`, `listOpenSlots`, `bookSlot`) in `frontend/src/features/patient-booking/api.ts` to call `apiRequest`; deleted `BookSlotApiError` and the `defaultMessageFor(body) ?? body.message` dead-code line entirely — `defaultMessageFor` survives only as the fallback function. Updated `BookSlotForm.tsx`'s `instanceof` check/import to the shared `ApiError`.
- [X] T008 [US1] Added the proof-of-fix test to `BookSlotForm.test.tsx`: constructs `ApiError` with both an `error` code AND a distinct, specific `message` ("This slot was booked by another patient 3 seconds ago.") and asserts that exact text renders — passes.
- [X] T009 [US1] Ran `frontend/tests/patient-booking/*.test.tsx` (8 files) and `frontend/tests/staff-booking/*.test.tsx` — all 32 tests pass; confirmed `staff-booking/api.ts`'s own separate, same-named `BookSlotApiError`/`WalkInApiError` classes are untouched (different file, not imported from patient-booking, correctly out of scope).

### M4: waitlist (multi-endpoint)

- [X] T010 [US1] Migrated all 6 exported functions (`joinWaitlist`, `listMyWaitlistEntries`, `staffJoinWaitlist`, `claimOffer`, `getWaitlistCount`, `declineOffer`) in `frontend/src/features/waitlist/api.ts`; deleted `WaitlistJoinApiError`/`WaitlistClaimApiError`, kept both `defaultMessageFor`/`defaultClaimMessageFor` as fallback functions. Updated 3 components (`ClaimOfferCard.tsx`, `JoinWaitlistForm.tsx`, `StaffJoinWaitlistForm.tsx`) that imported the removed classes by name; confirmed `ClinicToolsDashboard.tsx`'s `getWaitlistCount` usage is unaffected (silently `.catch()`'d, no class check).
- [X] T011 [US1] Added the proof-of-fix test to `ClaimOfferCard.test.tsx` (the file with 2 pre-existing error-message tests) — asserts a specific backend message ("Someone else claimed this offer 2 seconds ago.") renders over the generic default — passes.
- [X] T012 [US1] Ran all 3 `frontend/tests/waitlist/*.test.tsx` files — 16/16 pass, including 5 updated error-construction sites and the new T011 case.

### Live verification

- [X] T013 [US1] Verified live per `quickstart.md` steps 5-6, through the real running app (not a mock): signed up a fresh patient, booked a real slot at Star Clinic, then deliberately raced a second booking of the same slot via a direct `fetch()` to simulate a concurrent booking, then submitted the real `BookSlotForm` UI for that now-already-booked slot. **The alert rendered the backend's exact message — "Slot a5aad93b-053c-4e57-8dfb-48fc992301ff is already booked" — not the old generic "This slot is no longer available." fallback.** This is the direct, live, end-to-end proof of SC-002.

**Checkpoint**: All 4 migrated features pass their own tests; the fix is demonstrated live on a real request, not just asserted in a mock.

---

## Phase 4: User Story 2 - One place to fix a client-side HTTP bug (Priority: P2)

Structurally delivered by Phase 2 + Phase 3 together — no separate tasks; US2's acceptance criteria (no direct `fetch()`, no local error class in migrated files) are verified as part of T003-T012 above.

---

## Phase 5: Polish

- [X] T013a Grepped all 4 migrated files for `fetch(` and `class \w*ApiError` — zero matches in both, confirmed clean.
- [X] T014 `npx tsc -b` — zero type errors.
- [X] T015 `npm run lint` — zero new errors (pre-existing warnings only, unrelated to this feature).
- [X] T016 [P] `npm run test -- --run` — 244/244 passed (236 pre-existing baseline + 6 new apiClient tests + 2 new proof-of-fix tests), zero reduction anywhere.
- [X] T017 Updated `backlog/progress.md`'s row for `043-frontend-shared-api-client`.

---

## Dependencies & Execution Order

- Phase 2 (T001-T002) blocks all of Phase 3 — the client must exist before any migration.
- M1-M4 (T003-T012) are independent of each other (different files) but are sequenced smallest-first per research.md Decision 3 — each verified before the next, not because of a technical dependency.
- T013 (live verification) depends on at least M3 being done (it's the file with the observable bug).
- Phase 5 depends on all of Phase 3 being complete.

## Notes

- Total: 18 tasks (17 + T013a, added during `/speckit-analyze` to close a coverage gap on SC-001).
- The 27 unmigrated `api.ts` files are explicitly out of scope for this pass — not silently dropped, stated in spec.md Assumptions, plan.md Scope, and T017's progress-tracking note.
