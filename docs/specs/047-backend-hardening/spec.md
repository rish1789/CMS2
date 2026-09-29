# Feature Specification: Backend Security & Scale Hardening

**Feature Branch**: `047-backend-hardening`

**Created**: 2026-09-15

**Status**: Draft

**Input**: User description: "backlog/044-backend-security-scale-hardening.md" — cap the public discovery search endpoint's page size, gate `LoggingNotificationSender`'s PII logging behind an off-by-default flag, consolidate a duplicated waitlist-expiry magic number, and verify/document the existing rate limiter's real coverage. Fifth of the 040-052 production-hardening/redesign wave.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Public search can't be used to pull the entire directory at once (Priority: P1)

An operator running CMS2 in production wants the unauthenticated public discovery endpoint to never return every clinic/doctor in one response, regardless of how the platform grows or how a caller queries it.

**Why this priority**: The only one of the four items with a real, unbounded scale risk today — everything else in this feature is either a log-hygiene or maintainability fix with no user-facing failure mode.

**Independent Test**: Query discovery search with no pagination parameters against a dataset larger than the default page size and confirm only a bounded, capped number of results return.

**Acceptance Scenarios**:

1. **Given** more matching results exist than the maximum allowed page size, **When** a search is made with no explicit page size, **Then** the response contains only the default page size, never the full result set.
2. **Given** a caller requests an excessively large page size, **When** the request is made, **Then** the response is capped at the maximum allowed page size, not the requested one.

---

### User Story 2 - Patient contact info isn't written to logs by default (Priority: P2)

An operator wants confidence that running CMS2 in production doesn't put patient names, phone numbers, or message content into application logs unless someone deliberately turns that on for local debugging.

**Why this priority**: A real, currently-true privacy gap (confirmed: `LoggingNotificationSender` logs recipient + message unconditionally at INFO) — high real-world importance, but not urgent in the sense of an active exploit, since this is the notification *stub* (037), not a production delivery provider.

**Independent Test**: Send a notification with the debug flag left at its default (off) and confirm no recipient contact info or message body appears in the log output.

**Acceptance Scenarios**:

1. **Given** the debug logging flag is unset (default), **When** a notification is sent, **Then** the log output contains no recipient contact info or message body.
2. **Given** the debug logging flag is explicitly enabled, **When** a notification is sent, **Then** the existing full log line still appears (the capability isn't removed, only defaulted off).

---

### User Story 3 - The waitlist offer window is defined in exactly one place (Priority: P3)

A developer changing how long a waitlist offer stays claimable wants to change one value, not remember to find and update a second, independent copy of the same number elsewhere in the codebase.

**Why this priority**: Pure maintainability — zero current user-facing risk (both copies are correct and consistent today), lowest priority.

**Independent Test**: Grep the codebase for the literal `30 * 60` (or equivalent) in the waitlist module and confirm it appears in exactly one place, a named constant, referenced everywhere else.

**Acceptance Scenarios**:

1. **Given** the waitlist module's offer-expiry logic, **When** inspected, **Then** exactly one named constant defines the window, referenced by both the entity and the matching service.

---

### User Story 4 - The rate limiter's real coverage is documented and proven, not just believed (Priority: P2)

A reviewer auditing CMS2's brute-force protection wants confidence the existing rate limiter actually engages against the real running application, not just its own isolated unit test, and wants to understand in one place why it's built the way it is.

**Why this priority**: The limiter already exists and has a passing unit test — this is a verification/documentation gap, not a missing-capability gap, but real enough to matter for anyone auditing this system's actual security posture.

**Independent Test**: Make repeated rapid requests against a rate-limited endpoint in a real (non-mocked) application context and confirm the limiter actually rejects requests past the threshold.

**Acceptance Scenarios**:

1. **Given** more than the configured maximum attempts within the configured window, **When** requests are made against a rate-limited endpoint in a real application context (not an isolated filter unit test), **Then** the request past the threshold is rejected with 429.
2. **Given** a reviewer reading the security documentation, **When** they look for why the rate limiter is a `FilterRegistrationBean` rather than part of a `SecurityFilterChain`, **Then** the reasoning is documented in one place.

---

### Edge Cases

- What happens to discovery search's existing frontend caller (`DiscoverySearch.tsx`) once pagination is added — does its current unbounded assumption break? The response shape change must be handled on the frontend side too if the current contract assumes a flat array with no pagination metadata (check before changing the response shape).
- What happens if the debug logging flag is enabled in a real production deployment by mistake? That's an operator's explicit choice (the flag defaults off) — this feature does not need to prevent someone from opting into logging PII if they explicitly choose to.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The public discovery search endpoint MUST accept page/size parameters and MUST cap the effective page size at a maximum, regardless of what a caller requests.
- **FR-002**: `LoggingNotificationSender`'s recipient/message logging MUST be gated behind an off-by-default configuration flag.
- **FR-003**: The waitlist offer-expiry duration MUST be defined as exactly one named constant, referenced everywhere it's currently duplicated.
- **FR-004**: The rate limiter's actual behavior (which paths, window, threshold, and its `FilterRegistrationBean`-not-`SecurityFilterChain` design choice) MUST be documented in the project's existing security posture reference.
- **FR-005**: The rate limiter MUST be proven to engage in a real (non-unit-test-isolated) application context, not just its existing isolated filter test.
- **FR-006**: None of these changes may regress any existing, converged feature's test suite (035 discovery, 036/037 notification, 028/029 waitlist).

### Key Entities

N/A — no new data model; FR-001 adds request parameters to an existing endpoint, FR-002 adds a configuration flag, FR-003 is a constant consolidation.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Discovery search never returns more than the capped page size, regardless of query parameters.
- **SC-002**: With the debug flag unset, zero recipient contact info or message content appears in log output for a sent notification.
- **SC-003**: Zero duplicate literal defining the waitlist expiry window remains in the codebase.
- **SC-004**: A real (in-application-context, not isolated-unit-test) request proves the rate limiter rejects the Nth rapid attempt.

## Assumptions

- SECURITY.md (written by 045-backend-module-layering, converged) already exists as the natural home for FR-004's documentation — this feature extends it, doesn't create a new document.
- The exact discovery page-size default/maximum values are a planning-time decision (no existing precedent to match, since discovery has never been paginated) — reasonable web-application defaults apply absent a stated requirement.
- `DiscoverySearch.tsx`'s current contract assumption (a flat unbounded array) will need verification/adjustment as part of this feature if the response shape changes — determined at planning time by reading the actual current frontend code, not assumed here.
