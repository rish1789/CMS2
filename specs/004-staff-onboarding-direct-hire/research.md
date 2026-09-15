# Research: Staff Onboarding (Direct-Hire)

Reuses the established stack (Java 21/Spring Boot/PostgreSQL/React/Tailwind, JWT sessions per 002's precedent). Only new decisions specific to this feature are recorded here.

## Decisions

### Minimal ClinicAdmin authentication (pulled forward from 003)

- **Decision**: Add a staff login endpoint now — `POST /api/v1/staff/login` (email + password only; the staff-code alternate identifier is 003's addition later) — issuing a JWT with a `STAFF` audience claim (structurally distinct from 002's `PATIENT` audience, mirroring that feature's approach) carrying only the Account ID. Authorization ("is this account a ClinicAdmin for clinic X?") is resolved by querying `RoleAssignmentRepository` at request time, not baked into the token — avoids needing to reissue tokens if a role changes, and keeps the token itself minimal.
- **Rationale**: spec.md's Assumptions section already documents why this is necessary — 004 needs a caller-authentication mechanism, but 003 (the formal login feature) is scoped after 004 in build order since it needs 004's generated credentials to test against. Building a real, working (if minimal) login here — rather than a stub — means 004 is genuinely usable end-to-end, and 003 later *adds* the staff-code identifier to the same mechanism rather than replacing it.
- **Alternatives considered**: Skip real auth and hardcode/stub a "ClinicAdmin" test identity — rejected; would make this feature's own authorization requirement (FR-002) untestable in any real sense, and Constitution Principle I requires proving invariants, not stubbing past them.

### Doctor Profile ownership split with 005

- **Decision**: This feature defines and creates the `doctor_profile` table/entity (specialization, license number, experience, `license_verified` defaulting `false`). 005-doctor-profile-auto-creation-license-queue will later add the verification *workflow* (an admin screen/endpoints to list pending doctors and verify them, structurally similar to 003-super-admin-clinic-verification's pattern) on top of this same table — it does not create a separate one.
- **Rationale**: Documented in spec.md's Assumptions; matches the backlog's own dependency framing ("005 depends on 004 — the trigger for profile creation").
- **Alternatives considered**: None — this ownership split is what the backlog already implies, just made explicit here since it affects this feature's data model.

### Module placement

- **Decision**: All new code lives in `com.cms.identity.*` (staff-code generation, onboarding, Doctor Profile, and the new staff login all belong to the same "staff identity" concern as 001's Clinic/Account/RoleAssignment) — specifically `com.cms.identity.account` (staff login/JWT), `com.cms.identity.staff` (onboarding service/controller), and `com.cms.identity.doctor` (Doctor Profile entity/repository).
- **Rationale**: Consistent with 001/003's placement; this is squarely staff-identity territory, not a new module boundary like 002's Patient Account was.
