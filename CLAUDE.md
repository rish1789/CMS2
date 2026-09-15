# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

CMS2 is a multi-tenant clinic management system (Spring Boot backend + React/TS frontend). Three roles share one system: patients (book/manage appointments), clinic staff (ClinicAdmin/Doctor/Operations — scheduling, walk-ins, queues, cancellations, clinical documentation), and a Super Admin layer (clinic/doctor verification). See [`README.md`](README.md) for the full architecture diagram and tech stack, [`PRODUCT.md`](PRODUCT.md) for brand/UX intent, and [`DESIGN.md`](DESIGN.md) for the current color/type/motion tokens (these change independently of the code — read the file, don't assume from memory).

**This is a spec-driven codebase.** [`.specify/memory/constitution.md`](.specify/memory/constitution.md) governs *all* work, not just backlog features — read it before any non-trivial change. Its Development Workflow section requires a spec (`/speckit-specify`) and a plan (`/speckit-plan`) before implementation tasks for new behavior; skipping this for anything beyond a small fix is a constitution violation, not a style preference. Key non-negotiables from it: test-first (red-green-refactor) for new backend behavior and any schema-changing migration; no speculative abstraction (YAGNI) beyond the current confirmed requirement; every clinic-scoped query/endpoint must be tenant-scoped; Consultation Notes and Prescriptions are write-once/immutable (corrections are a new record on a new visit, never an edit); out-of-scope boundaries (payments, file uploads, a single global medical record, live notification delivery, atomic reschedule) stand until an explicit, documented product decision changes them.

## Commands

```bash
./dev.sh                        # start backend + frontend together (installs frontend deps, applies Flyway migrations automatically)
./dev.sh backend                # backend only
./dev.sh frontend                # frontend only

cd backend && ./gradlew bootRun          # backend alone, http://localhost:8080 (Swagger UI at /docs, spec at /api-docs)
cd backend && ./gradlew test             # unit + integration (integration tests use Testcontainers — needs Docker running)
cd backend && ./gradlew test --tests "com.cms.identity.account.StaffAuthServiceTest"   # single test class
cd backend && ./gradlew test --tests "*StaffAuthServiceTest.correctCredentialsResolveByEmailAndIssueStaffToken"   # single test method
cd backend && ./gradlew spotlessCheck    # formatting check only, no tests
cd backend && ./gradlew spotlessApply    # auto-fix formatting (also runs automatically before every compileJava)

cd frontend && npm run dev      # Vite dev server, http://localhost:5173
cd frontend && npm run test     # Vitest, full suite
cd frontend && npx vitest run path/to/File.test.tsx    # single test file
cd frontend && npm run lint     # oxlint (jsx-a11y, react, import-hygiene rules)
cd frontend && npx tsc -b       # type-check (also runs as part of `npm run build`)
```

These are exactly what CI (`.github/workflows/ci.yml`) runs — green locally means green in CI. Requires Java 21, Node 20+, and a local PostgreSQL 16+ reachable via the `DB_*` vars in `.env` (copied from `.env.example` by `dev.sh` on first run) — the project does not containerize its own database.

**JWT secrets and the Super Admin credential have no default values on purpose.** If `PATIENT_JWT_SECRET`/`STAFF_JWT_SECRET`/`SUPER_ADMIN_JWT_SECRET` or `SUPER_ADMIN_USERNAME`/`SUPER_ADMIN_PASSWORD` are unset, the backend generates a random value at startup and logs a warning — safe for local dev, but it means **every backend restart invalidates every previously-issued session token** and rotates the Super Admin login. Set fixed values in `.env` for a stable long-running dev session.

## Architecture

**Backend** (`backend/src/main/java/com/cms/<module>`): package-per-feature modules — `identity` (accounts/clinics/staff/doctors, further split into `account`/`admin`/`api`/`clinic`/`doctor`/`staff`), `booking`, `scheduling`, `waitlist`, `clinical`, `notification`, `inbox`, `discovery`, `common`. Cross-module effects (cancellation → waitlist offer, booking → notification event) go through explicit events, not direct reach-through into another module — preserve that boundary when touching either side of such a flow.

Three independent JWT realms, each with its own `SecurityConfig`/`JwtService` pair and no shared session state: `identity.account.SecurityConfig` + `StaffJwtService` (staff, path-scoped to `/api/v1/clinics/**` and `/api/v1/staff/**`), `patient.account.SecurityConfig` + `JwtService` (`/api/v1/patients/**`), `identity.admin.SuperAdminSecurityConfig` + `SuperAdminJwtService` (`/api/v1/admin/**`). `booking` and `discovery` each add their own filter chain for public, unauthenticated endpoints (public slot browsing, clinic search) — six path-scoped filter chains total across the three realms. A valid token in one realm does not satisfy another (a staff session does not satisfy the Super Admin guard, even though both now originate from the same `/staff/login` screen).

Flyway migrations (`backend/src/main/resources/db/migration`) are forward-only in production — a mistaken migration is corrected by a new migration, never by editing or deleting a shipped one.

**Frontend** (`frontend/src/`): `features/<domain>` for feature logic/components (32 feature dirs — booking, waitlist, staff pickers, day-sheet, inbox, etc.), `routes/<role>` for the three app shells (`PatientShell`/`StaffShell`/`AdminShell`, each a thin header + `<Outlet>`), `components/` for cross-feature shared UI (e.g. `RoleBadge`). `routes/guards.tsx` holds the three session guards (`RequireStaffSession`/`RequirePatientSession`/`RequireSuperAdminSession`) — these gate routes in `App.tsx`, not the shells themselves. `routes/PublicHeader.tsx` is a fourth, separate header used only by standalone pages (login, signup, discovery) that render outside all three shells.

Tailwind v4 config lives entirely in `frontend/src/index.css`'s `@theme` block — it **overrides** Tailwind's built-in `indigo`/`gray`/`red`/`green`/`amber` scales (so every existing `bg-indigo-600`/`text-gray-900`/etc. picks up palette changes with no per-component edits) and additionally defines a genuinely new `cobalt-*` scale for identity/status UI (avatars, role badges, certain status pills) that has no built-in Tailwind equivalent. Read `DESIGN.md` before changing any color, radius, shadow, or type-scale value — it documents the current rationale and is the source of truth over what any past commit message says.

**Spec-kit lifecycle**: `backlog/*.md` is the full 39-feature backlog in dependency-resolved build order (`backlog/build-order.md`), each a terse, groomed brief. `specs/<NNN-slug>/` holds the full spec-kit artifacts (`spec.md`, `plan.md`, `tasks.md`, `data-model.md`, `research.md`, `contracts/`) for a feature as it moves through the pipeline. The backlog's own README cites a source `clinic-management-system-BDD-2.md` (Business Design Document) as where the 39 features were decomposed from — that file is **not** in this repository; treat the backlog files themselves as authoritative, not a missing upstream doc.

## Testing conventions

Backend tests are one of three deliberately different shapes (see `CONTRIBUTING.md` for the full rationale): pure-Mockito unit tests (no Spring context), `@WebMvcTest` contract tests (real controller/validation, mocked service layer), and full-context `integration/` tests against a real ephemeral Postgres via Testcontainers. New business logic gets a unit test; a new endpoint gets contract coverage for its success *and* failure responses; a change to a documented business rule (a `backlog/*.md` file) gets integration coverage proving the rule holds against a real database — matching the constitution's test-first principle.
