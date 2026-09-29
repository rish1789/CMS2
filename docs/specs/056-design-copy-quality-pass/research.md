# Research: Visual Design & Copy Quality Pass

All Technical Context unknowns were resolved during `/speckit-clarify` (2026-09-16) or by direct code inspection — no `NEEDS CLARIFICATION` markers remain. This file documents the *how* decisions needed to turn the confirmed *what* (spec.md FR-008/FR-008a/FR-009) into an implementable approach.

## Decision 1: Reimplement Orbit's layout mechanics with `sticky`, not a literal `fixed` + `margin-left` port

**Decision**: Restructure `ClinicShell.tsx`'s sidebar/content row using CSS `position: sticky` on the sidebar column, inside a flex row that spans the full available width (i.e. `StaffShell.tsx`'s `mx-auto max-w-5xl` centering is removed, not replaced with a different fixed width).

**Rationale**: `orbit-1.0.0.zip`'s own `Sidebar.tsx` uses `fixed left-0 top-0 h-screen` with the content column offset by an animated `margin-left`, because Orbit's `Layout.tsx` owns the *entire* page — there is no separate outer header to reconcile against. CMS2's `StaffShell.tsx` renders one shared top header across every `/staff/**` route, including the clinic-agnostic `/staff` picker page that never mounts `ClinicShell`/`Sidebar` at all. Porting `fixed` literally would require re-deriving a top offset so the sidebar doesn't sit under (or over) that header, touching stacking/z-index concerns that are outside the confirmed scope (FR-003: targeted fix, not a broader redesign). A `sticky`-positioned sidebar inside a full-width flex row delivers the same confirmed, visible outcome — sidebar at the true viewport edge, content filling the remaining width, no nested centered gutter — with a much smaller diff limited to the two files already named in FR-008.

**Alternatives considered**:
- Literal `fixed` + `margin-left` port (Orbit's exact mechanism) — rejected: header-collision complexity, bigger diff than FR-008's confirmed scope.
- CSS Grid two-column shell — viable, roughly equal complexity; not chosen only because `ClinicShell` already uses `flex gap-6`, so `sticky` inside the existing flex row is the smaller, lower-risk change.

## Decision 2: One content-pane max-width, not three nested ones

**Decision**: The content pane inside `ClinicShell` (the div currently at `ClinicShell.tsx:87`, `min-w-0 flex-1 space-y-6 px-4 py-3`) keeps a single reading-width cap, using `DESIGN.md`'s already-documented `max-w-3xl`–`max-w-4xl` convention, applied once at that level. Individual pages/forms rendered inside it (e.g. `OnboardStaffForm.tsx`'s own `mx-auto max-w-xl`) drop their own redundant width/centering wrapper where it duplicates a constraint the shell now already provides, reviewed file-by-file at implementation time so no form's actual field grid regresses.

**Rationale**: `StaffShell` → `ClinicShell` → per-page component was independently applying up to three nested max-width/centering constraints — the exact structural cause confirmed in spec.md's Clarifications. `DESIGN.md` already documents the intended content width (`max-w-3xl`–`max-w-4xl`); reusing that number is truer to "targeted fix" (FR-003) than inventing a new one.

**Alternatives considered**:
- Leave every page's own inner max-width untouched — rejected: that duplication is the confirmed bug, not a side effect.
- Remove all width caps everywhere ("always full-bleed") — rejected: `DESIGN.md`'s cap exists for line-length readability; the product owner asked to stop wasting space via redundant nesting, not to remove the readability cap outright.

## Decision 3: `SidebarDrawer`'s mobile (off-canvas) behavior is untouched

**Decision**: `SidebarDrawer.tsx`'s existing `sm:hidden` hamburger-triggered native-`<dialog>` branch is not modified. Only the `hidden sm:block` static-desktop branch's *containing* layout changes (from a flex item in a centered row, to a sticky item in a full-width row).

**Rationale**: The confirmed complaint and clarify session were about desktop wasted space; mobile drawer behavior was never named as a problem (FR-007 keeps unnamed things unchanged) and already has passing coverage from 050-sidebar-navigation (`SidebarDrawer.test.tsx`). Changing it would be exactly the un-requested broader change FR-003 forbids.

**Alternatives considered**: N/A — this is a scope guardrail, not a technical tradeoff.

## Decision 4: HomePage copy — draft for review before applying

**Decision**: For each of the six confirmed `HomePage.tsx` elements (spec.md FR-009), draft concrete, screen-specific replacement wording grounded in `PRODUCT.md`'s Design Principle 1 ("trust through craft, not decoration") and Principle 3 ("warmth lives in typography, color, and copy... not decoration") — avoiding both the current generic/slogan-style text and the "overly playful... cutesy" anti-pattern `PRODUCT.md` explicitly warns against. Draft wording is presented to the product owner for a quick reaction pass before being applied in code (a `tasks.md` checkpoint task), not finalized unilaterally.

**Rationale**: Clarify confirmed *which* text is a problem, but not what replacement wording the product owner actually wants — FR-004's "concrete and specific, not generic relabeling" is a judgment call on the actual sentences, which is exactly the kind of guess this feature's Business Rules say not to make unconfirmed.

**Alternatives considered**: Finalizing copy now without a review step — rejected: conflicts with the feature's explicit "discuss specifics, don't guess" instruction, read here as covering replacement wording, not only target identification.

## Decision 5: `StaffShell.tsx`'s width cap moves down to `MyClinicsList.tsx`, not deleted

**Decision**: `StaffShell.tsx`'s single `<Outlet/>` renders two different things depending on route: the clinic-agnostic `/staff` picker page (`MyClinicsList.tsx`, no sidebar, has no width wrapper of its own — it silently relies on `StaffShell`'s `mx-auto max-w-5xl` today) and every `/staff/clinics/:id/*` route (`ClinicShell.tsx`, which per FR-008 needs the outer cap *removed* so its sidebar can reach the true edge). These two needs conflict at a single shared `<main>` wrapper. Resolution: remove the cap from `StaffShell.tsx`'s `<main>` entirely, and add an equivalent explicit `mx-auto max-w-5xl` wrapper directly inside `MyClinicsList.tsx` so its own appearance is pixel-unchanged.

**Rationale**: `MyClinicsList.tsx` (the `/staff` picker) was never named as having a layout problem — FR-007 requires it stay visually unchanged. The only way to give `ClinicShell` full width without regressing this untouched page is to move the width constraint to where it's actually needed (the page that wants it) instead of applying it blanket at the shared shell level.

**Alternatives considered**: Route-conditional width in `StaffShell.tsx` itself (e.g. checking the current path) — rejected, adds branching logic to a shell for a concern that belongs to the page being rendered, not the shell.

## Explicitly not adopted from `orbit-1.0.0.zip`

Per spec.md Assumptions (structure-only): Orbit's dark/glow visual skin, its `framer-motion`/`lucide-react`/`recharts`/`class-variance-authority` dependencies, its React 19 / Tailwind v4-via-Vite-plugin / react-router v7 versions (CMS2 stays on its own React 18.3.1 / Tailwind v4 / react-router 6.30 stack, already compatible with the mechanics above), and its own component code are not copied, ported, or installed. Only the structural *idea* (edge-anchored sidebar, full-width content, no nested centering) is reused, reimplemented from scratch against CMS2's existing tokens and dependencies.
