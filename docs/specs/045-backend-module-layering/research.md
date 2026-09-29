# Research: Backend Module Layering & Security Posture Documentation

## Decision 1: Reorganization threshold and module list

**Decision**: Modules with more than 10 files at their package root get reorganized; modules at or below that stay flat. Direct audit (2026-09-15) of `backend/src/main/java/com/cms/**` root file counts (excluding already-separated `dto/` subpackages):

| Module | Root files | Action |
|---|---|---|
| booking | 60 | Reorganize |
| scheduling | 41 | Reorganize |
| identity/admin | 26 | Reorganize |
| clinical | 21 | Reorganize |
| waitlist | 18 | Reorganize |
| patient/record | 15 | Reorganize |
| identity/staff | 15 | Reorganize |
| identity/account | 15 | Reorganize |
| patient/account | 14 | Reorganize |
| inbox | 12 | Reorganize |
| notification | 11 | Reorganize |
| identity/clinic | 8 | Leave flat |
| common | 8 | Leave flat |
| identity/doctor | 5 | Leave flat |
| discovery | 5 | Leave flat |
| identity/api | 2 | Leave flat |
| patient/api | 2 | Leave flat |

**Rationale**: 10 is a round, defensible cutoff — every "leave flat" module is comfortably below it (≤8), every "reorganize" module comfortably above (≥11), so there's no borderline case the exact number would flip.

**Alternatives considered**: A fixed threshold of 15 — rejected, would leave `notification` (11) and `inbox` (12) flat despite both already exceeding what the roadmap's own `booking`-focused complaint ("scanning filenames by suffix") describes; a percentage-of-largest-module threshold — rejected as needless complexity for a one-time classification decision.

## Decision 2: File-classification rules (which subpackage a file goes to)

**Decision**, in priority order (first match wins), derived from a direct annotation/signature audit of all 330 candidate files:

1. `@RestController` → `api/`
2. `@Service` → `service/`
3. `extends JpaRepository`/`CrudRepository`, or `@Repository` → `repository/`
4. `@Entity`, or a plain `enum` → `domain/`
5. `extends RuntimeException`/`Exception`, or `@ControllerAdvice`/`@RestControllerAdvice` → `exception/`
6. The module's `SecurityConfig` class itself, plus its paired `JwtService`, `*JwtAuthenticationFilter`, `*AuthenticationEntryPoint` → `config/` (grouped together, since CLAUDE.md already documents these as a "SecurityConfig/JwtService pair" per realm — putting them in one folder directly serves this feature's own stated goal of "see the entire authentication posture in one place")
7. A plain POJO named `*Event` (a domain event) → `domain/` (it's domain data, not behavior)
8. Everything else `@Component` (validators, ID/code/password generators, `@Scheduled` trigger classes, event `@EventListener` classes, calculator/sender interfaces and their implementations) → `service/` (business logic/utility, the plan's own definition for that folder)

**Rationale**: Matches the plan's target structure exactly, resolves every one of the 330 files audited with no leftover ambiguous case (0 files needed the spec's "stays in package root" escape hatch).

**Alternatives considered**: A dedicated `security/` subpackage for JWT/filter/entry-point classes instead of folding them into `config/` — rejected; the plan's own structure (from `PRODUCTION_ROADMAP.md` §2) only names 6 subpackages, and inventing a 7th for ~13 files project-wide is unjustified complexity (Constitution Principle II) when `config/` already fits.

## Decision 3: Execution order

**Decision**: Smallest reorganized module first, `booking` last — `notification`(11) → `inbox`(12) → `waitlist`(18) → `clinical`(21) → `identity/account`(15) → `identity/staff`(15) → `patient/account`(14) → `patient/record`(15) → `identity/admin`(26) → `scheduling`(41) → `booking`(60).

**Rationale**: Directly follows `PRODUCTION_ROADMAP.md` §3 Phase 3's own explicit sequencing suggestion ("smallest first... working up to booking last") — validates the mechanical process on low-risk modules before applying it to the two largest and most business-critical ones.

## Decision 4: Actuator exposure mechanism

**Decision**: Add `spring-boot-starter-actuator`; set `management.endpoints.web.exposure.include: health` and `management.endpoint.health.show-details: never` in `application.yml`; since actuator's `/actuator/**` base path doesn't match any of the 5 existing `SecurityConfig` chains' path patterns, it would otherwise fall through to `anyRequest().authenticated()` or similar in whichever chain has last-priority — explicitly verified during implementation which chain (if any) currently governs unmatched paths, and add an explicit `permitAll()` matcher for `/actuator/health` plus an explicit deny-all for any other unmatched actuator path, rather than relying on default fallthrough behavior.

**Rationale**: `management.endpoints.web.exposure.include: health` is Spring Boot's own standard mechanism for actuator surface control — no custom filter needed. Explicit `SecurityConfig` matchers avoid the exact "fell through to permitAll by omission" bug class this project has already found and fixed twice before (014, 016).

**Alternatives considered**: Exposing more endpoints (`info`, `metrics`) — explicitly out of scope per the spec (only `/health`).

## Decision 5: SECURITY.md content

**Decision**: One table — Security Config class | Path pattern(s) governed | Realm/JWT audience | Public or authenticated — populated by reading each of the 5 `SecurityFilterChain` beans directly, not from memory/documentation, at write time.

**Rationale**: The whole point is accuracy against live code; the plan explicitly forbids treating a discovered gap (e.g. an unintentionally-public path) as something to silently fix inside this "just documentation" feature — any such finding gets flagged separately.
