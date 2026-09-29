# Implementation Plan: Frontend Shared API Client

**Branch**: `046-frontend-api-client` | **Date**: 2026-09-15 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/046-frontend-api-client/spec.md`

## Summary

Build `frontend/src/lib/apiClient.ts`: one function that builds a request URL, injects a caller-supplied bearer token, and — on a non-2xx response — parses the body once and prefers the backend's specific message over a generic fallback (fixing the confirmed `defaultMessageFor(body) ?? body.message` dead-code bug). Migrate 4 representative features (smallest first, per research.md Decision 2), each verified before the next; the remaining ~27 `api.ts` files stay on their current pattern for a future pass, explicitly not claimed complete here.

## Technical Context

**Language/Version**: TypeScript / React 18 / Vite (unchanged).

**Primary Dependencies**: None new — `fetch()` stays the underlying mechanism, per the constitution's "no unnecessary new dependencies" and this codebase's existing zero-HTTP-library convention.

**Storage**: N/A.

**Testing**: Vitest (existing) — each migrated feature's own test file plus the full suite run after every migration.

**Target Platform**: Browser (unchanged).

**Project Type**: Existing web application; this feature adds `frontend/src/lib/` (new directory) and edits 4 `features/*/api.ts` files plus their test files where a test asserts the old (backwards) message-priority behavior.

**Performance Goals**: N/A.

**Constraints**: Zero change to any successful (2xx) request's URL/headers/body; the client must accept a caller-supplied token (not read one itself) since three independent JWT realms exist with no shared session state.

**Scale/Scope**: 1 new file (`apiClient.ts`) + 4 migrated features (`booking-detail`, `partial-session-cancellation`, `patient-booking`, `waitlist`) out of 31 total `api.ts` files.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Principle I (Test-First)**: Applied as "existing tests are the red/green baseline" — no new business behavior, but the message-priority fix is a real behavior change, so at least one test asserting the *new* (correct) priority MUST be added/updated per migrated feature, written to fail against the old code first where practical.
- **Principle II (Simplicity & YAGNI)**: PASS — no new HTTP library; the client's surface is exactly what the 31 existing files' common pattern already needs (URL, method, body, token, error-parsing), nothing speculative.
- **Principle III (Modular Architecture)**: N/A on the backend side; on the frontend, `lib/` as a new cross-feature seam is exactly the kind of shared infrastructure this principle's frontend analogue calls for (mirrors `components/` for UI, `lib/` for HTTP).
- **Principle IV (Data Privacy & Integrity)**: N/A.

No violations. No Complexity Tracking entries needed.

## Project Structure

### Documentation (this feature)

```text
specs/046-frontend-api-client/
├── plan.md
├── research.md
├── data-model.md    # N/A — no data model
├── quickstart.md
└── tasks.md
```

No `contracts/` — the shared client's "contract" is its own function signature, covered directly in research.md and verified by the migrated features' tests; no separate contract doc adds value here.

### Source Code (repository root)

```text
frontend/src/
├── lib/
│   └── apiClient.ts              # NEW
├── features/
│   ├── booking-detail/api.ts     # EDITED — migrated (smallest, proof of pattern)
│   ├── partial-session-cancellation/api.ts   # EDITED — migrated
│   ├── patient-booking/api.ts    # EDITED — migrated (the confirmed-bug file)
│   └── waitlist/api.ts           # EDITED — migrated
└── (27 other features/*/api.ts unchanged this pass)
```

**Structure Decision**: `frontend/src/lib/` is a new top-level directory alongside the existing `components/`/`features/` split — the natural home for cross-feature infrastructure that isn't UI (matching `components/`'s role for shared UI).

## Complexity Tracking

No Constitution Check violations — this section is not applicable.
