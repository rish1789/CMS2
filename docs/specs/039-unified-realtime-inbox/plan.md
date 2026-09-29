# Implementation Plan: Unified Real-Time Inbox

**Branch**: `039-unified-realtime-inbox` | **Date**: 2026-09-05 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/039-unified-realtime-inbox/spec.md`

## Summary

Front-desk staff (ClinicAdmin/Operations) need one live, claim-based list of outstanding clinic
work items — walk-in insertions (020), waitlist offers (029), and de-verification cascade notices
(008) — scoped to their own clinic, so two staff never duplicate the same piece of work. This
plan adds a new `com.cms.inbox` module: an `InboxItem` entity with a data-layer-guarded
claim/release/resolve lifecycle (mirroring this codebase's established conditional-`@Modifying`-
update pattern), fed by direct synchronous calls from 020/029/008's already-converged services
(the same "sink module, direct call" shape 036/037 established), and delivered live via
Server-Sent Events over the existing staff-JWT auth chain (no new dependency — `SseEmitter` is
built into Spring MVC).

## Technical Context

**Language/Version**: Java 21 (backend, matches every prior feature), TypeScript/React (frontend)

**Primary Dependencies**: Spring Boot (Web MVC's `SseEmitter` for push — no new dependency), Spring
Data JPA, Flyway. No new library is introduced.

**Storage**: PostgreSQL via Flyway migration (new `inbox_item` table).

**Testing**: JUnit 5 + Spring Boot Test + Testcontainers (backend, same as every prior feature —
execution blocked in this sandbox by the documented Docker/docker-java limitation, re-verify in a
real dev/CI environment); Vitest + React Testing Library (frontend).

**Target Platform**: Existing Spring Boot backend + React/Vite frontend (Linux/containerized deploy, unchanged).

**Project Type**: Web application (existing `backend/` + `frontend/` structure).

**Performance Goals**: New/updated items reach a viewing staff member within 5 seconds (SC-001) —
comfortably met by a push (not poll) delivery model.

**Constraints**: No new runtime dependency (Constitution II); real-time delivery must reuse the
existing staff JWT auth chain unchanged, since a browser's native `EventSource` cannot set an
`Authorization` header and adding a token-in-URL fallback would leak tokens into server access
logs — resolved by having the frontend consume the SSE stream via `fetch`'s streaming response
body (which does support custom headers) instead of `EventSource` (see research.md R2).

**Scale/Scope**: Per-clinic item volume is small (a handful of concurrently outstanding items per
clinic at any time, matching this system's existing single-clinic-at-a-time staff UX). Single
backend instance in this deployment (see research.md R3 for the accepted multi-instance limitation).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. Test-First Development**: New behavior (claim race-closure, clinic scoping, auto-resolve on
  waitlist lifecycle transitions, SSE broadcast on state change) gets integration tests written
  before implementation, following this session's established pattern. PASS.
- **II. Simplicity & YAGNI**: SSE via Spring's built-in `SseEmitter` (zero new dependency) chosen
  over introducing a message broker or a new WebSocket dependency; item summaries are derived live
  from existing relations rather than a new denormalized-then-synchronized copy. PASS.
- **III. Modular, Library-First Architecture**: New `com.cms.inbox` module, a pure sink like
  `com.cms.notification` — 020/029/008 call into it directly (all three already exist in the same
  build, mirroring 026's session-delay direct-call precedent, research.md R5 there), never the
  reverse. PASS.
- **IV. Data Privacy & Integrity by Design**: Patient-identifying content in Inbox Items is derived
  live from the referenced `Booking`/`WaitlistEntry` (never a frozen copy), so 033's anonymization
  and 034's retention purge propagate automatically (spec FR-016, Clarifications). The claim
  race-closure uses a data-layer-guarded conditional update, not read-then-write. PASS.

No violations — Complexity Tracking is not needed.

## Project Structure

### Documentation (this feature)

```text
specs/039-unified-realtime-inbox/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md         # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/
│   └── inbox.md         # Phase 1 output
└── tasks.md             # Phase 2 output (/speckit-tasks — not created here)
```

### Source Code (repository root)

```text
backend/src/main/java/com/cms/inbox/
├── InboxItem.java
├── InboxItemType.java
├── InboxItemStatus.java
├── InboxItemRepository.java
├── InboxItemService.java              # create*/claim/release/resolve, all @Transactional
├── InboxBroadcastService.java         # in-memory per-clinic SseEmitter registry + push
├── InboxController.java               # GET list, GET stream (SSE), POST claim/release/resolve
├── dto/InboxItemResponse.java
├── AlreadyClaimedException.java
├── NotClaimantException.java
├── InboxItemNotFoundException.java
└── InboxExceptionHandler.java

backend/src/main/java/com/cms/booking/
├── WalkInInsertionService.java        # + 1 call site to InboxItemService.createWalkInItem
└── DeVerificationCascadeService.java  # + per-clinic grouping + InboxItemService.createCascadeNotices

backend/src/main/java/com/cms/waitlist/
├── WaitlistMatchingService.java       # + 1 call site to InboxItemService.createWaitlistOfferItem
├── WaitlistClaimService.java          # + 1 call site to InboxItemService.resolveByWaitlistEntry (claim path)
└── WaitlistReleaseService.java        # + 1 call site to InboxItemService.resolveByWaitlistEntry (decline + expiry-sweep path)

backend/src/main/resources/db/migration/
└── V23__create_inbox_item.sql

backend/src/main/java/com/cms/identity/account/
└── SecurityConfig.java                # + 5 new matchers for the inbox endpoints (research.md R6)

frontend/src/features/inbox/
├── InboxPage.tsx
├── InboxItemCard.tsx
├── useInboxStream.ts                  # fetch-based SSE stream consumer (research.md R2)
└── inbox.test.tsx
```

**Structure Decision**: Follows the existing `backend/src/main/java/com/cms/<module>` + one
`frontend/src/features/<feature>` layout used by every prior feature this session — no new
top-level structure introduced. `com.cms.inbox` is a new module (a pure sink, consumed by nobody,
consuming from three existing modules via direct calls per Constitution III).

## Complexity Tracking

*No Constitution Check violations — this section intentionally left empty.*
