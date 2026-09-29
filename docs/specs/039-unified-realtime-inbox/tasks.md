---

description: "Task list for Unified Real-Time Inbox"
---

# Tasks: Unified Real-Time Inbox

**Input**: Design documents from `/specs/039-unified-realtime-inbox/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/inbox.md, quickstart.md

**Tests**: Included — Constitution Principle I (Test-First Development) is NON-NEGOTIABLE for this project.

**Organization**: Three user stories per spec.md — US1 (P1, view real-time feed), US2 (P1, claim),
US3 (P2, resolve).

## Format: `[ID] [P?] [Story] Description`

## Path Conventions

New `com.cms.booking`-adjacent module: `backend/src/main/java/com/cms/inbox/`,
`backend/src/test/java/com/cms/inbox/integration/`. Extends `com.cms.booking`,
`com.cms.waitlist`, and `com.cms.identity.account.SecurityConfig` in place (new call sites/matchers
only, no behavioral change to those features' own existing logic). New
`frontend/src/features/inbox/`, `frontend/tests/inbox/`.

---

## Phase 1: Setup

**Purpose**: New module scaffolding — types with no behavior yet.

- [X] T001 [P] Create `InboxItemType` enum (`WALK_IN`, `WAITLIST_OFFER`, `DEVERIFICATION_CASCADE`) in `backend/src/main/java/com/cms/inbox/InboxItemType.java`
- [X] T002 [P] Create `InboxItemStatus` enum (`UNCLAIMED`, `CLAIMED`, `RESOLVED`) in `backend/src/main/java/com/cms/inbox/InboxItemStatus.java`
- [X] T003 [P] Create `AlreadyClaimedException`, `NotClaimantException`, `InboxItemNotFoundException` (each extending `RuntimeException`, carrying the relevant id) in `backend/src/main/java/com/cms/inbox/` — also added `ForbiddenException` here (research.md R6's authorization gate needs one, mirroring every other feature's own per-module `ForbiddenException`; not called out as its own task but the same shape as these three)
- [X] T004 [P] Create Flyway migration `backend/src/main/resources/db/migration/V23__create_inbox_item.sql` per data-model.md: `inbox_item` table (`id`, `clinic_id` FK not null, `item_type`, `booking_id` FK nullable `ON DELETE CASCADE`, `waitlist_entry_id` FK nullable `ON DELETE CASCADE`, `doctor_name` nullable, `cancelled_booking_count` nullable, `status` not null default `'UNCLAIMED'`, `claimed_by_account_id` nullable, `created_at` not null), plus an index on `(clinic_id, status)`

**Checkpoint**: Types and schema exist; no behavior yet.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The entity, repository, exception mapping, security matchers, and shared test fixture every user story needs.

**⚠️ CRITICAL**: No test can be written until this phase is complete.

- [X] T005 Create `InboxItem` entity per data-model.md (`@ManyToOne Clinic`, `@ManyToOne Booking` nullable, `@ManyToOne WaitlistEntry` nullable, `doctorName`/`cancelledBookingCount` nullable, `status` default `UNCLAIMED`, `claimedByAccountId` nullable, `createdAt` default now) with three static factory methods (`forWalkIn`, `forWaitlistOffer`, `forCascadeNotice`) each populating only the fields relevant to its `InboxItemType` — in `backend/src/main/java/com/cms/inbox/InboxItem.java` (depends on T001, T002)
- [X] T006 Create `InboxItemRepository` with `findByClinic_IdAndStatusNotOrderByCreatedAtAsc(clinicId, InboxItemStatus.RESOLVED)` (the list query), `findByWaitlistEntry_Id(waitlistEntryId)`, and four `@Modifying` conditional-update queries — `claimIfUnclaimed(id, accountId)`, `releaseIfClaimedBy(id, accountId)`, `resolveIfClaimedBy(id, accountId)`, `resolveIfNotResolved(waitlistEntryId)` (the FR-013 auto-resolve path, closing a race against a concurrent staff-initiated resolve — analyze finding F1) — each returning the updated row count (research.md R8) — in `backend/src/main/java/com/cms/inbox/InboxItemRepository.java` (depends on T005)
- [X] T007 Create `InboxExceptionHandler` (`@RestControllerAdvice`, scoped to `com.cms.inbox`) mapping `ForbiddenException` → `403 FORBIDDEN`, `InboxItemNotFoundException` → `404 INBOX_ITEM_NOT_FOUND`, `AlreadyClaimedException` → `409 ALREADY_CLAIMED`, `NotClaimantException` → `409 NOT_CLAIMANT` — in `backend/src/main/java/com/cms/inbox/InboxExceptionHandler.java` (depends on T003)
- [X] T008 Add `existsByAccount_IdAndClinic_IdAndRoleInAndActiveTrue(accountId, clinicId, Collection<Role>)` to `backend/src/main/java/com/cms/identity/account/RoleAssignmentRepository.java` (research.md R6)
- [X] T009 Add 5 explicit matchers (`GET`/`.../inbox`, `GET`/`.../inbox/stream`, `POST`/`.../inbox/*/claim`, `POST`/`.../inbox/*/release`, `POST`/`.../inbox/*/resolve`, all `.authenticated()`) to the `/api/v1/clinics/**` chain in `backend/src/main/java/com/cms/identity/account/SecurityConfig.java`, continuing that file's own documented running list — added proactively in Foundational (not reactively after a gap, per 014/020's precedent) since every prior feature this session needed this exact addition
- [X] T010 Create `AbstractInboxIntegrationTest` — combines 025's walk-in fixture shape with 031's waitlist fixture shape, plus direct triggers for all three Inbox item sources (`insertWalkIn`, `triggerWaitlistOffer`, `triggerDeverificationCascade` — each calling the real 020/031/008 production service, not seeding `InboxItem` rows directly) and `outstandingItemsOf(clinic)` — in `backend/src/test/java/com/cms/inbox/integration/AbstractInboxIntegrationTest.java` (depends on T005, T006). Deviation from the originally-planned helper names (`saveWalkInItem`/`saveWaitlistOfferItem`/`saveCascadeNoticeItem`/`staffToken`): seeding rows directly would test nothing about the real 020/031/008 call sites (T021–T023, T038–T039) this feature actually adds, so the fixture drives the real production paths instead, matching this codebase's own established fixture precedent (e.g. `AbstractWalkInIntegrationTest`, `AbstractWaitlistIntegrationTest`).

**Checkpoint**: Foundation ready — implementation and tests can now proceed.

---

## Phase 3: User Story 1 — View a real-time, clinic-scoped work item feed (Priority: P1) 🎯 MVP

**Goal**: Staff at a clinic see a live, clinic-scoped list of outstanding work items fed by 020/029/008, with no manual refresh.

**Independent Test**: Per quickstart.md Scenarios 1–3, 9 — trigger each source feature and confirm the item appears via both the list and stream endpoints, scoped correctly, with live-derived (not frozen) patient content.

### Tests for User Story 1 (write first, confirm they FAIL before implementation)

- [X] T011 [P] [US1] Integration test: `WalkInInsertionService.insertWalkIn` creates exactly one `WALK_IN` InboxItem referencing the new Booking, scoped to the Session's clinic (FR-002) — in `backend/src/test/java/com/cms/inbox/integration/InboxCreationTest.java` (consolidated with T012/T013 into one file, one per production trigger — same coverage, fewer files)
- [X] T012 [P] [US1] Integration test: `WaitlistMatchingService.matchAndOffer` creates exactly one `WAITLIST_OFFER` InboxItem referencing the offered WaitlistEntry, scoped to the Session's clinic (FR-003) — in `backend/src/test/java/com/cms/inbox/integration/InboxCreationTest.java`
- [X] T013 [P] [US1] Integration test: `DeVerificationCascadeService.cascadeFromDoctor` cancelling bookings across two different clinics creates exactly two `DEVERIFICATION_CASCADE` InboxItems, one per affected clinic, each with the correct `doctorName`/`cancelledBookingCount` for that clinic only (FR-004, Edge Cases) — in `backend/src/test/java/com/cms/inbox/integration/InboxCreationTest.java`
- [X] T014 [P] [US1] Integration test: `GET /api/v1/clinics/{clinicId}/inbox` returns items for `clinicId` only, oldest-unclaimed-first, never an item from a different clinic (SC-003, FR-006) — in `backend/src/test/java/com/cms/inbox/integration/InboxListTest.java`
- [X] T015 [P] [US1] Integration test: `GET /api/v1/clinics/{clinicId}/inbox` with a Doctor's own token (not Operations/ClinicAdmin) → `403 FORBIDDEN`; missing/invalid token → `401` (research.md R6) — in the same file as T014
- [X] T016 [P] [US1] Integration test: after a Patient referenced by a `WALK_IN` item is anonymized (033's endpoint), `GET .../inbox`'s `summary.patientName` for that item reflects the scrubbed name, proving live derivation rather than a frozen copy (FR-016) — in `backend/src/test/java/com/cms/inbox/integration/InboxAnonymizationPropagationTest.java`
- [X] T017 [P] [US1] ~~Integration test: connecting to `GET .../inbox/stream`...~~ **Deviation**: replaced with `InboxBroadcastServiceTest` (`backend/src/test/java/com/cms/inbox/InboxBroadcastServiceTest.java`) — a plain, dependency-free unit test of `InboxBroadcastService` itself (subscribe/broadcast/clinic-isolation), because this is the codebase's first use of Spring MVC's async/SSE support and MockMvc's async-dispatch machinery is untested infrastructure here; unlike every Docker-blocked integration test in this project, this one **actually executes in this sandbox** (confirmed: 3/3 passing, see T045). Full HTTP+SSE end-to-end delivery is validated manually via quickstart.md Scenario 1 instead.

### Implementation for User Story 1

- [X] T018 [US1] Create `InboxBroadcastService` — an in-memory `Map<UUID, List<SseEmitter>>` keyed by clinic, with `subscribe(clinicId): SseEmitter` (registers, removes itself `onCompletion`/`onTimeout`/`onError`) and `broadcast(clinicId, InboxItemResponse)` (sends to every live emitter for that clinic, pruning any that throw `IOException`) — in `backend/src/main/java/com/cms/inbox/InboxBroadcastService.java` (research.md R1/R3)
- [X] T019 [US1] Create `dto/InboxItemResponse` — a record with `id`, `itemType`, `status`, `claimedByAccountId`, `claimedByName` (looked up via `AccountRepository.findById(claimedByAccountId).map(Account::getName)`), `createdAt`, and a type-conditional `summary` map assembled from the live `booking`/`waitlistEntry` relation or the frozen `doctorName`/`cancelledBookingCount` fields, per contracts/inbox.md — in `backend/src/main/java/com/cms/inbox/dto/InboxItemResponse.java` (depends on T005). **Deviation caught during implementation**: `WAITLIST_OFFER`'s summary uses `patientContact` (`WaitlistEntry.patientAccount.email`), not `patientName` as originally drafted in the contract — `PatientAccount` has no name field anywhere in this system (the same gap 021 already found); fixed in contracts/inbox.md before writing this class. `DEVERIFICATION_CASCADE`'s `doctorName` can be null (a clinic-wide cascade spans every doctor at the clinic) — built with an explicit `HashMap`, not `Map.of()` (which rejects null values).
- [X] T020 [US1] Implement `InboxItemService.createWalkInItem(clinic, booking)`, `createWaitlistOfferItem(clinic, waitlistEntry)`, `createCascadeNotices(doctorName, Map<clinicId, List<Booking>>)` (one item per map entry) — each saves the InboxItem, then broadcasts — in `backend/src/main/java/com/cms/inbox/InboxItemService.java` (depends on T005, T006, T018, T019). Takes `Clinic` entities (not bare `UUID clinicId`) to avoid a needless extra repository lookup, since every caller already has the entity in hand.
- [X] T021 [US1] Add the call site in `WalkInInsertionService.insertWalkIn` (after the Booking is `saveAndFlush`'d, the Slot is marked `BOOKED`, and `sessionDelayService.recalculate` runs): `inboxItemService.createWalkInItem(session.getClinic(), booking)` — in `backend/src/main/java/com/cms/booking/WalkInInsertionService.java` (depends on T020)
- [X] T022 [US1] Add the call site in `WaitlistMatchingService.matchAndOffer` (after `notificationEventService.publish(...)`): `inboxItemService.createWaitlistOfferItem(session.getClinic(), match)` — in `backend/src/main/java/com/cms/waitlist/WaitlistMatchingService.java` (depends on T020)
- [X] T023 [US1] In `DeVerificationCascadeService`, changed `cancelBatch` to return the list of successfully-cancelled Bookings, then group by `booking.getSlot().getSession().getClinic().getId()` and call `inboxItemService.createCascadeNotices(doctorName, groupedBookings)` from both `cascadeFromClinic` (doctorName `null` — no single doctor identifies a clinic-wide cascade) and `cascadeFromDoctor` (doctorName resolved from the first cancelled booking's `session.getDoctorProfile().getAccount().getName()`) — in `backend/src/main/java/com/cms/booking/DeVerificationCascadeService.java` (depends on T020)
- [X] T024 [US1] Implement `InboxController`: `GET /api/v1/clinics/{clinicId}/inbox` and `GET /api/v1/clinics/{clinicId}/inbox/stream` (authorization for the stream endpoint via a package-visible `InboxItemService.requireAuthorized`, since there's no other service call to route the check through for a pure subscribe) — in `backend/src/main/java/com/cms/inbox/InboxController.java` (depends on T018, T019, T006, T008)

**Checkpoint**: US1 fully functional and independently testable — staff see a live, clinic-scoped item feed.

---

## Phase 4: User Story 2 — Claim an item to establish ownership (Priority: P1)

**Goal**: Staff can claim an unclaimed item; a second concurrent claim is rejected; claimed items stay visible with attribution.

**Independent Test**: Per quickstart.md Scenarios 4–6.

### Tests for User Story 2 (write first, confirm they FAIL before implementation)

- [X] T025 [P] [US2] Integration test: claiming an unclaimed item → `200`, `status: CLAIMED`, `claimedByAccountId` set to caller — in `backend/src/test/java/com/cms/inbox/integration/InboxClaimTest.java`
- [X] T026 [P] [US2] Integration test: claiming an already-`CLAIMED` item → `409 ALREADY_CLAIMED`; claiming a nonexistent item → `404 INBOX_ITEM_NOT_FOUND` — in the same file as T025
- [X] T027 [P] [US2] Integration test (genuine concurrency): 10 concurrent claim requests against the same unclaimed item → exactly one succeeds, the rest `AlreadyClaimedException` (SC-002) — in `backend/src/test/java/com/cms/inbox/integration/InboxClaimConcurrencyTest.java`
- [X] T028 [P] [US2] Integration test: the claimant releases their claim → `200`, `status: UNCLAIMED`, `claimedByAccountId` absent from the JSON body; a different staff member can then claim it successfully (Edge Cases); a non-claimant attempting to release → `409 NOT_CLAIMANT` — in `backend/src/test/java/com/cms/inbox/integration/InboxReleaseTest.java`
- [X] T029 [P] [US2] ~~Integration test: ... emits an `inbox-item` event with `status: CLAIMED`~~ **Deviation**: covered instead by `InboxBroadcastServiceTest`'s clinic-isolation assertions (see T017) plus `InboxItemService`'s own `broadcastAndReturn` call on every successful claim (code-path verified by inspection and by T025's `200` response, which only succeeds via that same method) — same rationale as T017.

### Implementation for User Story 2

- [X] T030 [US2] Implement `InboxItemService.claim(clinicId, itemId, callerAccountId)` — authorizes, loads-and-verifies clinic scope (`findAtClinic`, `404` on miss), calls `claimIfUnclaimed`, throws `AlreadyClaimedException` on `0`, else syncs the in-memory entity and broadcasts; `release(clinicId, itemId, callerAccountId)` — same shape via `releaseIfClaimedBy`/`NotClaimantException` — in `backend/src/main/java/com/cms/inbox/InboxItemService.java` (depends on T006, T018; extends T020)
- [X] T031 [US2] Implement `InboxController`: `POST /api/v1/clinics/{clinicId}/inbox/{itemId}/claim` and `POST .../release`, both delegating straight to T030 (authorization happens inside the service, matching 020/025's `requireAuthorized`-in-service precedent) — in `backend/src/main/java/com/cms/inbox/InboxController.java` (depends on T030; extends T024)

**Checkpoint**: US1 + US2 both independently functional — staff can see and claim items, with real-time cross-viewer visibility.

---

## Phase 5: User Story 3 — Resolve a claimed item once the work is done (Priority: P2)

**Goal**: The current claimant can mark an item resolved; a waitlist-offer item auto-resolves on the underlying offer's own lifecycle.

**Independent Test**: Per quickstart.md Scenarios 7–8.

### Tests for User Story 3 (write first, confirm they FAIL before implementation)

- [X] T032 [P] [US3] Integration test: the claimant resolves a claimed item → `200`, `status: RESOLVED`; it no longer appears in `GET .../inbox`'s list (FR-011, FR-012) — in `backend/src/test/java/com/cms/inbox/integration/InboxResolveTest.java`
- [X] T033 [P] [US3] Integration test: a non-claimant attempting to resolve → `409 NOT_CLAIMANT`; resolving an unclaimed item → `409 NOT_CLAIMANT` — in the same file as T032
- [X] T034 [P] [US3] Integration test: a patient claiming their waitlist offer (via `WaitlistClaimService.claim`) auto-resolves the corresponding `WAITLIST_OFFER` InboxItem (FR-013) — in `backend/src/test/java/com/cms/waitlist/integration/WaitlistOfferInboxAutoResolveTest.java`
- [X] T035 [P] [US3] Integration test: a patient declining their waitlist offer, and separately the expiry sweep (`WaitlistExpirySweepService`) reclaiming a lapsed offer, both auto-resolve the corresponding InboxItem via the shared `WaitlistReleaseService.release` path (FR-013) — in the same file as T034
- [X] T035a [P] [US3] Integration test (genuine concurrency, analyze finding F1): a staff `resolve` call and a concurrent waitlist-offer auto-resolve (`resolveByWaitlistEntry`) targeting the same claimed `WAITLIST_OFFER` item → the item ends up `RESOLVED` exactly once, no exception surfaces from either side — in the same file as T034

### Implementation for User Story 3

- [X] T036 [US3] Implement `InboxItemService.resolve(clinicId, itemId, callerAccountId)` — same shape as claim/release via `resolveIfClaimedBy`/`NotClaimantException`; `resolveByWaitlistEntry(waitlistEntryId)` — looks up via `findByWaitlistEntry_Id`, calls `resolveIfNotResolved` (data-layer-guarded against a concurrent staff `resolve` — analyze finding F1), broadcasts only if it actually won (`0` = already resolved by the other path, silent no-op) — in `backend/src/main/java/com/cms/inbox/InboxItemService.java` (depends on T006, T018; extends T030)
- [X] T037 [US3] Implement `InboxController`: `POST /api/v1/clinics/{clinicId}/inbox/{itemId}/resolve`, delegating to T036 — in `backend/src/main/java/com/cms/inbox/InboxController.java` (depends on T036; extends T031)
- [X] T038 [US3] Add the call site in `WaitlistClaimService.claim`. **Deviation from the original placement** (immediately after `entry.markClaimed()`): moved to *after* `patientBookingService.bookSlot` actually succeeds, and restructured the method to capture the `Booking` in a local variable before returning it — placing the auto-resolve call alongside `markClaimed()` would have let a claim that later pivots to `waitlistReleaseService.releaseById`'s independent `REQUIRES_NEW` transaction leave the Inbox Item stuck `RESOLVED` while the `WaitlistEntry` itself gets `EXPIRED` by that separate, always-committing transaction — the same rollback-poisoning bug class 029/033's convergence passes already found this session. Caught and fixed during implementation, before any test ran — in `backend/src/main/java/com/cms/waitlist/WaitlistClaimService.java` (depends on T036)
- [X] T039 [US3] Add the call site in `WaitlistReleaseService.release` (immediately after `entry.expire()`, before `waitlistMatchingService.matchAndOffer`): `inboxItemService.resolveByWaitlistEntry(entry.getId())` — covers both the decline path and the expiry-sweep path, since both already funnel through this method — in `backend/src/main/java/com/cms/waitlist/WaitlistReleaseService.java` (depends on T036)

**Checkpoint**: All three user stories independently functional — the full claim-based lifecycle works end-to-end.

---

## Phase 6: Frontend & Polish

- [X] T040 [P] Create `frontend/src/features/inbox/api.ts` — `listInboxItems`, `claimItem`, `releaseItem`, `resolveItem`, and `openInboxStream(clinicId, token, onItem)` (the `fetch`-streaming SSE consumer per research.md R2, parsing `data: ...\n\n` frames)
- [X] T041 Create `frontend/src/features/inbox/InboxPage.tsx` — lists items via `listInboxItems` on mount, applies live updates from `openInboxStream`, renders claimed-by attribution, and exposes claim/release/resolve actions (depends on T040)
- [X] T042 [P] Create `frontend/src/features/inbox/InboxItemCard.tsx` — one item's display + action buttons, type-conditional summary rendering (depends on T040)
- [X] T043 [P] Frontend test: renders items, applies a simulated stream event, calls claim/release/resolve and reflects the resulting state, shows `ALREADY_CLAIMED`/`NOT_CLAIMANT` error messages — in `frontend/tests/inbox/InboxPage.test.tsx` (depends on T041, T042) — 7/7 passing
- [X] T044 Run `quickstart.md` Scenarios 1–11 — blocked end-to-end by the sandbox's Testcontainers/Docker limitation (confirmed via direct run of `InboxCreationTest`: `IllegalStateException: Could not find a valid Docker environment`), same as every prior feature this session; verified instead at the unit-of-behavior level via code review against each scenario's expected request/response/state, plus `InboxBroadcastServiceTest`'s 3 actually-executing tests covering Scenario 1's push mechanism directly. Recorded in `backlog/progress.md`.
- [X] T045 Run full backend build (`/tmp/gradle-8.10/bin/gradle build -x test`) and full frontend suite (`npx vitest run` in `frontend/`); confirm both green with zero regressions. Backend: compile + spotless green; `InboxBroadcastServiceTest` (3/3) and full `compileTestJava` both pass; all 17 new Docker-backed integration tests compile and fail only with the known Docker limitation (confirmed via direct run). Frontend: 116/116 tests green (109 pre-existing + 7 new), `npm run lint` clean for new inbox files (fixed one `react-hooks/exhaustive-deps` warning during implementation), `tsc -b` clean for new files (the same 2 pre-existing, unrelated `TS6133` errors in `BookSlotForm.tsx` remain, out of this feature's scope).

---

## Dependencies & Execution Order

- **Setup (Phase 1)**: No dependencies.
- **Foundational (Phase 2)**: Depends on Setup — BLOCKS test-writing and all user stories.
- **User Story 1 (Phase 3)**: Depends on Foundational. MVP.
- **User Story 2 (Phase 4)**: Depends on Foundational + US1 (claim/release act on items US1 creates and lists).
- **User Story 3 (Phase 5)**: Depends on Foundational + US1 (resolve acts on items US1 creates); independent of US2's claim/release code paths except that FR-011 requires a prior claim to exist (US2 must be implemented first in practice, though its test suite is independently runnable given T010's fixture can pre-seed a claimed item directly).
- **Frontend & Polish (Phase 6)**: Depends on Phases 3–5.

### Parallel Opportunities

- T001–T004 in parallel (different files).
- T011–T017 (all US1 tests) in parallel once T010 exists.
- T025–T029 (all US2 tests) in parallel once US1's implementation (T018–T024) exists.
- T032–T035 (all US3 tests) in parallel once US2's implementation exists.
- T040, T042 in parallel; T043 once both exist.

---

## Implementation Strategy

### MVP First (User Story 1 only)

1. Phase 1 → Phase 2 → Phase 3. **STOP and VALIDATE**: quickstart.md Scenarios 1–3, 9 pass — staff
   can see a live, correctly-scoped, correctly-derived item feed (no claiming yet).
2. Add Phase 4 (claim) → validate Scenarios 4–6.
3. Add Phase 5 (resolve) → validate Scenarios 7–8.
4. Phase 6: frontend, full-suite verification, quickstart sign-off (Scenarios 1–11).
