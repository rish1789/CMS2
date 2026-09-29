# Feature Specification: Frontend Shared API Client

**Feature Branch**: `046-frontend-api-client`

**Created**: 2026-09-15

**Status**: Draft

**Input**: User description: "backlog/043-frontend-shared-api-client.md" — build one shared HTTP client (`frontend/src/lib/apiClient.ts`) that every feature's `api.ts` calls instead of hand-rolling `fetch()`/auth-header/error-class logic, fixing a confirmed shipped bug where the backend's specific error message is unreachable dead code. Fourth of the 040-052 production-hardening/redesign wave.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - See the backend's actual error message, not a generic fallback (Priority: P1)

A user (patient or staff) submits a form that the backend rejects with a specific reason (e.g. "Fee override must be non-negative," "This slot is no longer available"). Today they see a hardcoded, hand-guessed generic message instead, because of a confirmed bug: every feature's error class always prefers its own generic message over the real one from the backend.

**Why this priority**: This is a real, already-shipped bug affecting every feature that hand-rolls this pattern (18 of 31 `api.ts` files) — it directly harms the "never leave users wondering whether their action worked" standard already established in this project. Fixing the underlying cause (via the shared client) is the single highest-leverage change in this feature.

**Independent Test**: Submit a request that the backend rejects with a specific message (e.g. an invalid fee override) and confirm the UI shows that exact message, not a generic one.

**Acceptance Scenarios**:

1. **Given** a backend response with a specific error message in its body, **When** a migrated feature's request fails, **Then** the UI displays that specific message.
2. **Given** a backend response with no message field (a genuinely generic failure), **When** a migrated feature's request fails, **Then** a sensible fallback message is shown — this is the one case where a generic message is still correct.

---

### User Story 2 - One place to fix a client-side HTTP bug, not 31 (Priority: P2)

A frontend developer needs to fix or improve how requests are made (e.g. a header, a URL base change, an error-parsing tweak) without hunting down and editing every feature's own copy-pasted implementation.

**Why this priority**: The structural fix behind User Story 1 — consolidation is what makes the bug fixable in one place instead of 18, and prevents the same bug class recurring in a 32nd future feature.

**Independent Test**: Inspect any migrated feature's `api.ts` and confirm it contains no direct `fetch()` call and no locally-defined error class — only a thin wrapper around the shared client.

**Acceptance Scenarios**:

1. **Given** the shared client, **When** any migrated feature needs to call the backend, **Then** it does so through the shared client's function, not its own `fetch()`.
2. **Given** a change to how the shared client builds a request or parses an error, **When** that change is made once, **Then** every migrated feature picks it up with no per-feature edit.

---

### Edge Cases

- What happens for a feature whose current error handling is genuinely different from the common pattern (e.g. a non-JSON response, a network-level failure with no response body at all)? The shared client's error parsing must handle "no parseable body" gracefully (a connectivity/timeout message), not crash trying to read JSON that isn't there.
- What happens to the three separate JWT realms (patient/staff/super-admin), each with its own token storage? The shared client is a mechanism, not a session store — it must accept whichever token the calling feature already resolves from its own realm, not assume one global token.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: A single shared function MUST exist that every migrated feature calls to make an authenticated HTTP request, replacing that feature's own direct `fetch()` call.
- **FR-002**: The shared function MUST inject the `Authorization: Bearer <token>` header when a token is supplied by the caller, without assuming which of the three JWT realms it belongs to.
- **FR-003**: On a non-2xx response, the shared function MUST parse the response body once and prefer the backend's own specific message over any generic fallback — inverting the current, confirmed-backwards priority.
- **FR-004**: A generic fallback message MUST still be used when the backend response has no usable message (network failure, non-JSON body, or a message-less error body).
- **FR-005**: Each migrated feature's `api.ts` MUST have its old, locally-defined `*ApiError` class body removed once migrated — the old and new error-handling paths MUST NOT coexist for a given feature past its own migration.
- **FR-006**: Migration MUST proceed one feature at a time, each verified (its own tests plus the full suite) before the next, starting with a low-traffic feature and finishing with high-traffic ones.

### Key Entities

N/A — no data model changes; this is a client-side HTTP/error-handling consolidation.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Every migrated feature's `api.ts` contains zero direct `fetch()` calls and zero locally-defined error classes.
- **SC-002**: A backend-specific error message is visible to the user for at least one real, previously-broken case (verified live in-browser, not just via a test).
- **SC-003**: The full frontend test suite passes after every feature's migration, with no reduction in test count.
- **SC-004**: No behavior change to any successful (2xx) request — only the error-message-priority behavior changes, and only in the intended direction.

## Assumptions

- Verified 2026-09-15: 31 `api.ts` files exist under `frontend/src/features/**`; 28 independently define their own `*ApiError` class; at least one (`patient-booking/api.ts`) was directly confirmed to exhibit the exact documented bug (`defaultMessageFor(body) ?? body.message` — `defaultMessageFor` always returns a string via its own `default:` case, so `body.message` is unreachable dead code even though the backend does send a real message in that field).
- This feature does not introduce a new HTTP library (`fetch()` stays the underlying mechanism) — per the constitution's "no unnecessary new dependencies," and consistent with the existing codebase having zero HTTP-library dependencies today.
- Migrating all 31 files in one pass is impractical to verify carefully; the plan may scope an initial representative subset (per the backlog's own "start low-traffic, finish high-traffic" guidance) rather than claiming all 31 in a single implementation pass — the exact subset is a planning-time decision.
