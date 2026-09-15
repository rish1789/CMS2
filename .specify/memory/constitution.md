<!--
Sync Impact Report
- Version change: (template, unratified) → 1.0.0
- Modified principles: none (initial ratification)
- Added principles:
  - I. Test-First Development (NON-NEGOTIABLE)
  - II. Simplicity & YAGNI
  - III. Modular, Library-First Architecture
  - IV. Data Privacy & Integrity by Design
- Added sections: Technology & Platform Constraints; Development Workflow & Quality Gates; Governance
- Removed sections: none
- Deferred/TODO placeholders: none — all template tokens resolved
- Templates requiring follow-up: none (plan/spec/tasks/checklist templates read this
  file at runtime; no edits made here per command scope)
-->

# Clinic Management System (CMS) Constitution

## Core Principles

### I. Test-First Development (NON-NEGOTIABLE)
Tests MUST be written and reviewed before implementation code for any new behavior,
following Red-Green-Refactor: write a failing test, get it approved, then implement
the minimum code to pass it. This applies to backend logic (Spring Boot services,
scheduling/booking rules, background jobs) and to any Flyway migration that changes
data shape or constraints — a migration that enforces a new invariant (e.g. a
uniqueness guarantee, a status transition) MUST have a test proving the invariant
holds and proving the prior violating state is rejected.
Rationale: This system encodes many precise, easy-to-regress business rules (fee
locking at booking time, no-show grace windows, immutable clinical documentation,
waitlist bump eligibility). Untested changes to these rules silently corrupt
clinical and scheduling data that clinics depend on operationally.

### II. Simplicity & YAGNI
Start with the simplest design that satisfies the current, confirmed requirement.
Do not add configurability, abstraction layers, or speculative extension points for
features that are not yet decided (see the Pending Clarifications list in the
project's business design document — e.g. configurable retention windows, billing,
insurance). Complexity (a new service, a new abstraction, a new dependency) MUST be
justified in the PR/plan description against a real, current requirement, not a
hypothetical future one.
Rationale: The as-built system already carries deliberate, documented scope cuts
(no payments, no file uploads, no atomic reschedule). Speculative generality works
against that discipline and makes the codebase harder to reason about than the
business it models.

### III. Modular, Library-First Architecture
Functionality MUST be organized into cohesive, independently testable modules with
clear boundaries (e.g. scheduling/session generation, booking, waitlist, clinical
documentation, notifications, discovery) rather than a single undifferentiated
service layer. Each module MUST expose a clear contract (service interface or REST
endpoint) and MUST be testable in isolation from unrelated modules. Cross-module
communication for asynchronous effects (e.g. cancellation → waitlist bump,
booking → notification event) MUST go through explicit, event-driven interfaces,
not direct reach-through into another module's internals.
Rationale: The existing system already relies on event-driven decoupling (e.g. the
notification pipeline, waitlist bump on cancellation). Preserving clean module
boundaries keeps that decoupling real instead of accidental, and keeps modules like
notifications (currently stubbed) replaceable without touching booking logic.

### IV. Data Privacy & Integrity by Design
Any feature touching patient-identifying or clinical data MUST account for DPDP
compliance (anonymization eligibility, the 3-year clinical-content retention
window, and the immediate-anonymization/monthly-purge lifecycle) at design time,
not as an afterthought. Clinical documentation (Consultation Notes, Prescriptions)
MUST remain write-once/immutable — no edit path may be added; corrections happen
via a new record on a new visit. Concurrency-sensitive operations that create or
match identity records (e.g. patient self-service booking) MUST close duplicate-
creation races at the data layer, not merely at the application layer.
Rationale: This is a healthcare-adjacent system under Indian DPDP obligations,
already built around hard invariants (immutability, anonymization gating, race-safe
patient creation). Weakening any of these is a compliance and clinical-record
integrity risk, not just a code-quality concern.

## Technology & Platform Constraints

**Backend:** Java 21, Spring Boot, Gradle as the build tool. Database schema
changes MUST go through Flyway migrations — no manual/out-of-band schema changes.
Migrations are forward-only in production; a mistaken migration is corrected by a
new migration, not by editing or deleting a shipped one.

**Frontend:** Tailwind CSS for styling.

**Multi-tenancy:** The system is multi-tenant across clinics. Any new query or
endpoint touching clinic-scoped data (Patients, Sessions, Bookings, Waitlist
Entries, Inbox Items) MUST be scoped to the requesting clinic; global entities
(Patient Account, Doctor Profile, Super Admin) are the only intentional exceptions
and MUST be treated as such explicitly, not by omission.

**Out-of-scope boundaries stand until explicitly changed:** no online payment
processing, no file/document uploads, no single global patient medical record, no
live notification delivery integration, no true atomic reschedule. Introducing any
of these requires a constitution amendment or an explicit, documented product
decision referenced in the relevant spec — not an incidental implementation choice.

## Development Workflow & Quality Gates

- Every feature begins with a spec (`/speckit-specify`) and a plan
  (`/speckit-plan`) before implementation tasks are generated.
- Tests for the affected module(s) MUST pass, and new behavior MUST have new
  tests, before a change is considered done (see Principle I).
- Changes to scheduling, booking, cancellation, waitlist, or clinical-
  documentation logic MUST include a rationale note describing which documented
  business rule (see the project's Business Design Document) they implement or
  change, since these rules are precise and easy to silently regress.
- Code review MUST verify: test coverage for the change, adherence to module
  boundaries (Principle III), no unjustified new complexity (Principle II), and
  no weakening of privacy/immutability guarantees (Principle IV) without an
  explicit, documented decision.
- Flyway migrations MUST be reviewed for irreversibility and multi-tenant safety
  before merge.

## Governance

This constitution supersedes other informal practices for the CMS project.
Amendments require: (1) a documented rationale for the change, (2) a version bump
per the policy below, and (3) review of whether dependent templates or workflows
need follow-up (tracked outside this document, per the spec-kit workflow).

**Versioning policy** (semantic versioning for this document):
- MAJOR: Backward-incompatible governance changes — removing or redefining a
  principle in a way that reverses its prior guarantee.
- MINOR: A new principle or materially expanded section added.
- PATCH: Wording clarifications, typo fixes, non-semantic refinements.

**Compliance review:** Pull requests and implementation plans MUST be checked
against this constitution's principles before merge/approval. Any exception MUST
be explicitly justified in the PR/plan description; silent deviation is not
permitted.

**Version**: 1.0.0 | **Ratified**: 2026-09-02 | **Last Amended**: 2026-09-02
