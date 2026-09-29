# Feature Specification: Backend Module Layering & Security Posture Documentation

**Feature Branch**: `045-backend-module-layering`

**Created**: 2026-09-15

**Status**: Draft

**Input**: User description: "backlog/042-backend-module-layering-security-posture.md" — reorganize flat backend module packages into api/service/repository/domain/exception/config subpackages, add spring-boot-starter-actuator with only /health public, and document the 5 existing SecurityConfig classes in one file. Third of the 040-052 production-hardening/redesign wave.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Find "the service that owns X" by browsing, not grepping (Priority: P1)

A backend developer working in a large module (e.g. `com.cms.booking`, 60+ files at its package root) wants to find "the service responsible for cancelling a booking" by opening a `service/` folder, not by scanning 60 alphabetically-sorted filenames for one whose suffix happens to be `Service`.

**Why this priority**: This is the concrete, measured pain point the prior architect review identified, and the reorganization itself (moving files into `api/`/`service/`/`repository/`/`domain/`/`exception/`/`config/` subpackages) is the bulk of this feature's actual work.

**Independent Test**: Open any reorganized module and confirm each subpackage contains only files of its stated kind (controllers in `api/`, JPA entities in `domain/`, etc.), and that the full backend test suite still passes with zero behavior change.

**Acceptance Scenarios**:

1. **Given** a module with more than ~10 files at its package root, **When** this feature is complete, **Then** its files are organized into `api/`, `service/`, `repository/`, `domain/`, `exception/`, and `config/` subpackages as applicable (a module with no controller has no `api/` folder, etc.) — `dto/` (already a subpackage in every module that has one) is left as-is.
2. **Given** the full backend test suite passing before this feature starts, **When** each module's reorganization completes, **Then** the suite still passes with zero test-file content changes beyond updated package declarations/imports.
3. **Given** a small module (roughly 10 files or fewer at its root), **When** audited, **Then** it is deliberately left flat rather than fragmented into six near-empty subpackages.

---

### User Story 2 - A load-balancer-checkable liveness endpoint (Priority: P2)

An operator deploying CMS2 wants a standard `/health` endpoint a load balancer or uptime monitor can poll, without exposing any other internal application state.

**Why this priority**: A real, currently-missing operational capability (no `spring-boot-starter-actuator` dependency exists at all today) — independent of the file-reorganization work, lower priority only because it's smaller in scope.

**Independent Test**: Start the backend and confirm `GET /actuator/health` returns 200 with no authentication, while every other `/actuator/**` path is unreachable.

**Acceptance Scenarios**:

1. **Given** the backend running, **When** `GET /actuator/health` is requested, **Then** it returns a 200 health status with no authentication required.
2. **Given** the backend running, **When** any other `/actuator/**` path is requested (e.g. `/actuator/env`, `/actuator/beans`), **Then** it is rejected (404/403), never exposing internal application state.

---

### User Story 3 - A single map of the API's authentication/authorization posture (Priority: P3)

A reviewer auditing CMS2's security posture wants one document listing every `SecurityConfig` class, what path pattern it governs, and which identity realm (patient/staff/super-admin/public) applies — instead of opening 5 separate files and cross-referencing them by hand.

**Why this priority**: Pure documentation, zero code/behavior risk — lowest priority, done last.

**Independent Test**: Read the new document and cross-check each of its claims against the actual current `SecurityFilterChain` bean in each of the 5 `SecurityConfig` classes.

**Acceptance Scenarios**:

1. **Given** the new security posture document, **When** a reviewer reads it, **Then** it lists all 5 `SecurityConfig` classes (`patient/account/SecurityConfig`, `identity/account/SecurityConfig`, `identity/admin/SuperAdminSecurityConfig`, `booking/BookingSecurityConfig`, `discovery/DiscoverySecurityConfig`), the path pattern(s) each governs, and which realm/public status applies — each claim verifiable against the actual current code.

---

### Edge Cases

- What happens to a file that doesn't cleanly fit one category (e.g. a class that's both a small value type and referenced only by one entity)? Classify by its primary role/annotation; a genuinely ambiguous file stays in the module's package root rather than forcing a bad fit — noted explicitly if this occurs, not silently guessed.
- What happens if a module's internal (non-test) code references another class in the same module using a bare (same-package) reference that a subpackage split would break? Every such reference needs its import statement added/corrected as part of the move — this is exactly what "run the full test suite after each module" is for.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Every backend module with more than approximately 10 files at its package root MUST be reorganized into `api/`, `service/`, `repository/`, `domain/`, `exception/`, `config/` subpackages as applicable to that module's actual file kinds; `dto/` subpackages already present MUST be left unchanged.
- **FR-002**: Modules at or below that size threshold MUST be left flat — this feature does not mandate six subpackages for a 3-file module.
- **FR-003**: This reorganization MUST NOT change any class's behavior, public API contract, or HTTP-visible response shape — package declarations and import statements are the only permitted content changes.
- **FR-004**: The full backend test suite MUST pass after each module's reorganization, verified before moving to the next module.
- **FR-005**: The backend MUST expose `GET /actuator/health` with no authentication required.
- **FR-006**: Every other `/actuator/**` endpoint MUST be unreachable (never exposed).
- **FR-007**: A single document MUST list all 5 existing `SecurityConfig` classes, their governed path patterns, and their realm/public status, verified accurate against the current code at the time it's written.

### Key Entities

N/A — no data model changes; this feature reorganizes existing code and adds operational tooling.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A developer can locate "the service responsible for X" in a reorganized module by browsing its `service/` folder alone, without needing to scan the whole module's file list.
- **SC-002**: The full backend test suite (`gradle test`) passes after this feature with zero regressions, verified incrementally after each module's move.
- **SC-003**: `GET /actuator/health` is reachable with no auth; every other actuator path returns 404/403.
- **SC-004**: A reviewer can determine the entire application's authentication/authorization surface by reading one document, with zero inaccuracies against the live `SecurityConfig` code.

## Assumptions

- This is a mechanical, behavior-preserving refactor (package/file moves + import fixes), not a redesign of any module's actual logic — the existing (converged, tested) business logic in every module is the correctness baseline throughout.
- The exact list of modules crossing the "~10 files" reorganization threshold is finalized during planning via a direct file-count audit, not guessed here (see plan.md/research.md).
- Test execution in this sandbox is subject to the project's confirmed Docker/Testcontainers limitation — the "full test suite passes" criterion is verified via compilation success plus the Docker-independent unit/contract test subset locally, with full confirmation deferred to a real CI/dev environment, consistent with every other feature in this project.
