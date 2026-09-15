# Contributing to CMS2

Thanks for considering a contribution. This is a fairly opinionated, spec-driven codebase — the notes below will save you a round-trip.

## Before you start

- Read [`README.md`](README.md) for setup and [`.specify/memory/constitution.md`](.specify/memory/constitution.md) for the project's architectural and process rules — it's short and governs what gets merged.
- For anything beyond a small fix (new behavior, a new endpoint, a schema change), the project's convention is spec-first: write a spec, then a plan, then tasks, *before* implementation (see the constitution's Development Workflow section and the `.claude/skills/speckit-*` tooling if you're using an agent that supports it). A PR that skips this for a non-trivial change will likely get asked to back up a step.
- Check [`backlog/build-order.md`](backlog/build-order.md) if you're picking up a backlog feature — it's the dependency-resolved build order, not just a rough module grouping.

## Branching

- `main` is always deployable — CI (`.github/workflows/ci.yml`) runs on every push to `main` and every pull request.
- Branch off `main` per change, named `<type>/<short-description>`, e.g. `fix/booking-race-condition`, `feat/waitlist-notification`, `docs/readme-quickstart`.
- Keep PRs scoped to one logical change. A PR touching both a bug fix and an unrelated refactor is harder to review and to revert if something's wrong.
- Rebase on `main` before opening a PR rather than merging `main` in, where practical — keeps history readable.
- Squash-merge is preferred for the default merge strategy, so `main`'s history reads as one commit per logical change.

## Running the app locally

See the README's [Quickstart](README.md#quickstart) — `./dev.sh` is the one-command path.

## Running tests

```bash
# Backend — unit + integration (Testcontainers needs Docker running)
cd backend && ./gradlew test

# Backend — just formatting/lint check, no tests
cd backend && ./gradlew spotlessCheck

# Frontend — full suite
cd frontend && npm run test

# Frontend — lint + type-check
cd frontend && npm run lint
cd frontend && npx tsc -b
```

All four of these are exactly what CI runs — if they're green locally, CI should be green too. `gradlew` also auto-formats Java sources on every `compileJava` (via Spotless) — if `spotlessCheck` fails in CI, run `./gradlew compileJava` locally first (or `./gradlew spotlessApply` directly) and commit the result.

### A note on backend test types

- **Unit tests** (e.g. `StaffAuthServiceTest`, `PatientAccountServiceTest`) — pure Mockito, no Spring context, no database. Fast, always runnable.
- **Contract/web-layer tests** (e.g. `RegisterClinicContractTest`, `StaffAuthControllerTest`) — `@WebMvcTest` with the service layer mocked. Exercise the real controller, validation, and exception handling, still no database.
- **Integration tests** (under each module's `integration/` package) — full Spring context against a real, ephemeral Postgres instance via Testcontainers. These need Docker available in your environment; they're what CI actually runs to prove end-to-end behavior, including real database constraints.

New business logic should get a unit test; a new endpoint should get contract-test coverage for its success and failure responses; a change to a documented business rule (see a `backlog/*.md` feature file) should have integration coverage proving the rule actually holds against a real database.

## Code style

- **Backend**: Spotless enforces formatting automatically (4-space indent, no unused imports, trailing whitespace trimmed) — you don't need to think about it, just run a build.
- **Frontend**: oxlint (`npm run lint`) — includes accessibility (jsx-a11y), React, and import-hygiene rules.
- Follow existing naming/package conventions within whichever module you're touching rather than introducing a new pattern for one file.

## Commit messages

Describe *why*, not just *what* — the diff already shows what changed. If a change implements or changes a documented business rule, reference which one (see how existing commits/comments cite feature numbers like `025-walk-in-priority-insertion`).

## Reporting issues

Open a GitHub issue with: what you expected, what happened instead, and repro steps. For anything security-related, please don't open a public issue — see below.

## Security

If you find a security issue, please report it privately rather than opening a public issue (Docker credentials, JWT secrets, cross-tenant data access, etc. are all in scope for this project's threat model — see the constitution's Data Privacy & Integrity principle).
