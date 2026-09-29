# Research: Patient Record Auto-Creation & Phone-Based Linking

No `NEEDS CLARIFICATION` markers remain in Technical Context — the two open questions (Patient entity schema, HTTP endpoint or not) were resolved during Specify with documented reasoning, and the deeper privacy/race-safety question was resolved with the user during Clarify. This document records the resulting technical decisions.

## Decision: Two separate partial-unique constraints, not one flat `(clinic, phone)` constraint

**Rationale**: Resolved directly with the user during Clarify. A flat `UNIQUE(clinic_id, phone)` constraint would force every patient sharing a phone number at a clinic into a single row regardless of which account they belong to — a real privacy leak (Constitution Principle IV). Instead:
1. `UNIQUE (clinic_id, patient_account_id) WHERE patient_account_id IS NOT NULL` — guarantees one account never has two Patient records at the same clinic. This is what actually closes the race this feature's own writes can trigger (US3: the same account submitting two concurrent first-booking requests, both attempting to create a new, already-linked row).
2. `UNIQUE (clinic_id, phone) WHERE patient_account_id IS NULL` — guarantees two unlinked (walk-in) records never share a clinic+phone combination. This feature never itself creates unlinked rows (every row it writes is created already-linked), so this constraint is never actually triggered by this feature's own code — it exists for 016/018/020's future walk-in-creation flows, and for keeping the "find an unlinked match" read step (FR-003) well-defined (at most one unlinked candidate per clinic+phone). Defining it now, while the table itself is being created for the first time, is cheaper than adding it later once walk-in rows already exist.

**Alternatives considered**: A single flat constraint — rejected per the Clarify decision (privacy leak). Application-level-only uniqueness checks with no DB constraint — rejected per Constitution Principle IV, which explicitly names this exact scenario ("patient self-service booking ... MUST close duplicate-creation races at the data layer").

## Decision: `PatientLinkingService.findOrCreatePatient(UUID patientAccountId, UUID clinicId, String name)` is the entire public contract — no HTTP endpoint

**Rationale**: See spec.md's Assumptions. Restated here because it drives the module structure: `com.cms.patient.record` gets no controller, no DTOs, no `@RestController` at all in this feature. Its "contract" (Constitution Principle III's explicit alternative to a REST endpoint) is the service method's signature and behavior, documented in `contracts/patient-linking-service.md` as a Java-interface-shaped contract rather than an HTTP one.

**Alternatives considered**: A speculative `POST /api/v1/patient-accounts/me/clinics/{clinicId}/patient` endpoint — rejected; nothing calls it yet, and guessing its shape now risks building something 017/018 have to immediately change once they define what a real booking submission actually looks like.

## Decision: `name` is a required parameter to the service method, not derived from `PatientAccount`

**Rationale**: `PatientAccount` (039) has no name field — only `email`, `mobile`, `passwordHash`, `notificationOptIn`, `active`. A new Patient record needs a name (a walk-in's name is staff-entered; a self-service-created one has no other source), so the caller (a future booking flow, which will have collected it) must supply it. When the *existing-link* (FR-002) or *phone-match* (FR-003) paths apply, the supplied `name` parameter is ignored — the existing record's own name (a prior walk-in's staff-entered name, or a previously-created record's own name) is authoritative and is never overwritten by this call, since this feature has no "edit Patient" capability and out-of-scope-per-spec keeps it that way.

**Alternatives considered**: Making `name` optional/nullable, defaulting to something derived from the account's email — rejected; inventing a placeholder name (e.g., from the email's local part) is worse than requiring a real one from the one caller who actually has it.

## Decision: A lost race re-reads and returns the winning record, never surfaces as an error

**Rationale**: FR-006 requires this explicitly. Implementation pattern: `save()` inside a `try/catch (DataAccessException)`; on catching a violation of either constraint from research decision #1, re-run the FR-002 existing-link lookup (which will now find the row the concurrent winner just committed) and return it, exactly like `StaffOnboardingService`'s existing `uq_account_email` catch-and-recover pattern (004) and `DoctorProfileRepository`'s license-number equivalent (007) — this project's established pattern for closing an app-level-race with a DB constraint without exposing the loser to a hard failure they didn't cause.

**Alternatives considered**: Returning a `409`-shaped error to the loser and expecting the caller to retry — rejected; FR-006 explicitly requires the loser to succeed transparently, not retry, since (unlike an email-uniqueness conflict, which is a genuine user input problem) this race has no wrong party — both concurrent requests represent the same legitimate intent.

## Decision: New package `com.cms.patient.record`, sibling to `com.cms.patient.account`

**Rationale**: The clinic-scoped `Patient` is conceptually distinct from the global `PatientAccount` (the spec itself treats them as two different entities linked by a relationship, never merges them) — giving it its own package under the existing `com.cms.patient` bounded context keeps that distinction visible in the code structure, matching how `com.cms.identity.doctor` sits alongside `com.cms.identity.account` as a related-but-distinct concern.

**Alternatives considered**: Folding `Patient` into the existing `com.cms.patient.account` package — rejected; that package is scoped to account/login concerns (signup, login, JWT), and `Patient` is a different kind of thing (a clinical/visit record) that happens to relate to it.
