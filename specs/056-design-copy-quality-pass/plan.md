# Implementation Plan: Visual Design & Copy Quality Pass

**Branch**: `056-design-copy-quality-pass` | **Date**: 2026-09-16 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/056-design-copy-quality-pass/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

Two independent, narrowly-scoped corrections to the shipped 046-052 redesign, both confirmed via `/speckit-clarify` (2026-09-16) rather than guessed:

1. **Layout**: `StaffShell.tsx` (`mx-auto max-w-5xl`) and `ClinicShell.tsx` (`flex gap-6` sidebar nested inside that constrained column) currently produce three nested, independently-centered boxes (outer shell → sidebar+content flex row → each page's own inner `max-w-xl`/`max-w-md` form), which is the root cause of the "wasted space / irrational placement" complaint. Fix: restructure `StaffShell`/`ClinicShell` to an edge-docked, sticky sidebar with full-width content — the structural pattern confirmed against the `orbit-1.0.0.zip` reference (`Layout.tsx`/`Sidebar.tsx`), adapted to `sticky` rather than a literal viewport-edge-to-edge `fixed`/full-height element so it doesn't collide with `StaffShell`'s existing header (research.md Decision 1) — reusing CMS2's own Tailwind tokens and zero new dependencies (not Orbit's dark skin, animation library, or component code). Applies once at the shared-shell level, so it fixes every screen `StaffShell`/`ClinicShell` wraps, not just the two originally inspected. `PatientShell`/`AdminShell` are untouched.
2. **Landing page** *(expanded 2026-09-16, mid-implementation — see spec.md FR-009a/FR-010/FR-011)*: rather than rewriting six strings in place, the product owner directed a full rebuild of `HomePage.tsx` from a supplied reference design (`design/landing-page-reference.html`), using this project's own brand/copy/tokens: patient-booking content now dominates the hero, and clinic/admin access is reduced to a single "Clinic login" link (header + footer) → the existing `/staff/login`, which already role-routes Super Admin sign-ins correctly. Clinic registration moved from a top-level homepage card to a new link on `/staff/login` itself. No other screen's copy changes.

No backend change, no new dependency, no new route (both `/register` and `/super-admin-console` already existed — only which pages link to them changed), no new entity.

## Technical Context

**Language/Version**: TypeScript ~6.0 / React 18.3.1 (existing frontend stack, no change)

**Primary Dependencies**: react-router-dom 6.30 (existing — no new package). Tailwind CSS v4 (`frontend/src/index.css`'s `@theme` block — existing indigo/gray/cobalt tokens reused as-is, no new tokens). The `orbit-1.0.0.zip` reference (MIT-licensed ThemeWagon "Orbit" template, React 19/Tailwind v4/framer-motion/lucide-react) is consulted for its layout **mechanics only** — none of its dependencies, components, or visual skin are added to this project (spec Assumptions).

**Storage**: N/A — presentation/copy-only feature, no data model change

**Testing**: Vitest + React Testing Library, tests under `frontend/tests/` (existing convention — see `frontend/tests/routes/`, `frontend/tests/staff/`, `frontend/tests/components/`)

**Target Platform**: Web (existing Vite/React SPA), responsive down to ~400px per this project's established standard; must preserve `SidebarDrawer`'s existing native-`<dialog>` off-canvas behavior below the `sm:` breakpoint unchanged

**Project Type**: Web application (frontend-only feature — no backend change)

**Performance Goals**: No new goal; layout restructuring is pure CSS/markup, no added network calls or client-side computation

**Constraints**: Zero new npm dependencies (FR-008a / spec Assumptions); zero change to `PatientShell`/`AdminShell` or any screen under them (FR-007); zero change to the six confirmed `HomePage.tsx` elements' *meaning* — only wording (FR-004); WCAG 2.1 AA must not regress (`PRODUCT.md` Design Principle 5) — sidebar nav landmarks, focus order, and the existing drawer's focus trap/Escape-to-close must keep working exactly as today

**Scale/Scope**: 2 shell files (`StaffShell.tsx`, `ClinicShell.tsx`) restructured; `MyClinicsList.tsx` gains a preserve-only width wrapper (research.md Decision 5 — a necessary side effect of removing `StaffShell`'s cap, not a new named problem); `OnboardStaffForm.tsx`'s own redundant inner `mx-auto max-w-xl` is adjusted since it was one of the two originally-named problem screens; `Sidebar.tsx` adjusted only if the new shell structure requires it (e.g. a `sticky` class moving there). 1 page (`HomePage.tsx`) with 6 text elements rewritten. No other page's own width wrapper is touched — only the two originally-named screens and the shell-level structural cause, per FR-003's "targeted, not a broader redesign."

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Principle I (Test-First)**: Constitution's explicit language targets backend logic and Flyway migrations; this feature touches neither. Per this project's own established frontend convention (every prior UI feature, e.g. 050-sidebar-navigation, ships matching `frontend/tests/**` coverage for new/changed rendered behavior), new tests are added for: `HomePage.tsx`'s six rewritten text elements (assert new copy renders, old copy does not), and `StaffShell`/`ClinicShell`'s restructured layout (sidebar present as a nav landmark, content region present, existing `ClinicToolsDashboard.test.tsx` and any other test rendering through these shells still passes unregressed). Written alongside implementation per this project's frontend precedent. PASS.
- **Principle II (Simplicity & YAGNI)**: PASS — this is a net simplification (collapsing three nested centering layers into one clear structure), reuses existing `Sidebar`/`SidebarDrawer`/`Card` components and existing Tailwind tokens, adds zero new dependency, and does not adopt Orbit's animation/theming machinery it doesn't need (research.md Decision 1).
- **Principle III (Modular, Library-First Architecture)**: N/A — frontend-only presentation/copy change; no backend module, no cross-module event touched.
- **Principle IV (Data Privacy & Integrity by Design)**: N/A — no patient-identifying or clinical data involved; no entity, migration, or concurrency-sensitive operation introduced.

No violations — Complexity Tracking table not needed.

## Project Structure

### Documentation (this feature)

```text
specs/056-design-copy-quality-pass/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md         # Phase 1 output (/speckit-plan command)
├── quickstart.md         # Phase 1 output (/speckit-plan command)
└── tasks.md              # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

No `contracts/` — this feature exposes no new API and calls none; it's a pure presentation/copy change to existing, already-rendered screens.

### Source Code (repository root)

```text
frontend/
├── src/
│   ├── routes/
│   │   ├── HomePage.tsx           # EDIT - 6 text elements rewritten (FR-009)
│   │   └── staff/
│   │       ├── StaffShell.tsx     # EDIT - drop mx-auto max-w-5xl centering (FR-008)
│   │       └── ClinicShell.tsx    # EDIT - sidebar docked full-height/edge, content fills remaining width (FR-008)
│   ├── features/
│   │   ├── staff-clinics/
│   │   │   └── MyClinicsList.tsx      # EDIT (preserve-only) - gains its own mx-auto max-w-5xl so removing StaffShell's cap doesn't change its appearance (research.md Decision 5)
│   │   └── staff-onboarding/
│   │       └── OnboardStaffForm.tsx   # EDIT - adjust its own redundant inner mx-auto max-w-xl (one of the 2 originally-named problem screens)
│   └── components/
│       └── Sidebar.tsx            # EDIT only if the new shell structure needs it (e.g. width/positioning classes move here)
└── tests/
    ├── routes/
    │   └── HomePage.test.tsx      # NEW - asserts the 6 rewritten text elements
    └── staff/
        ├── StaffShell.test.tsx    # NEW - asserts docked sidebar / full-width content structure
        └── ClinicShell.test.tsx   # NEW - same, at the clinic-scoped level; regression-checks breadcrumb/switch-clinic behavior unchanged
```

**Structure Decision**: Frontend-only change within the existing `frontend/src/routes/` (shells + HomePage) and `frontend/src/components/` (Sidebar, if needed). No backend directory touched — matches Technical Context (no backend dependency) and Constitution Principle III's N/A above. Mirrors 050-sidebar-navigation's precedent for where shell-level layout changes and their tests live.

## Complexity Tracking

*No violations — table not needed.*
