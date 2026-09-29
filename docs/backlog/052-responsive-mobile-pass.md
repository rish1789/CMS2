# 052 — Responsive & Mobile Pass

**Module:** Frontend / Design System Application
**Status:** Ready for spec-kit intake

## User Story
As any user of CMS2 on a tablet or phone (a patient checking their booking on their phone, a doctor glancing at their schedule between consultations), I want every screen — shell, tables, forms, and the day sheet/calendar-like views — to work intentionally at smaller widths, not just shrink desktop content until it breaks, so that the application is genuinely usable off a desktop/laptop.

## Context
Per `PRODUCT.md`, all three user roles are described as "primarily on desktop/laptop," so this is explicitly a hardening/completeness pass, not a mobile-first redesign — but the user's request explicitly calls for intentional responsive behavior on tablet/mobile as part of a production-quality standard, and this codebase's own established responsive convention (per `HANDOFF.md`/CLAUDE.md context: "~400px" as the tested narrow width, side-gutter and wrap/stack conventions) should be applied consistently, not spot-checked ad hoc per feature as it has been.

This feature is deliberately sequenced last among the redesign features (046-052) because it depends on the new shell (047), component library (046), dashboard (048), patient hub (049), tables (050), and forms (051) all being in their near-final shape — testing responsiveness against components that are about to be redesigned again would be wasted, repeated work.

## Business Rules
- The new sidebar shell (047) MUST have a confirmed, tested mobile behavior (collapsed rail, off-canvas drawer, or bottom nav — whichever 047 chose) verified at ~400px width, not just assumed to work because 047's plan mentioned responsiveness.
- Tables/lists migrated in 050 MUST have an explicit narrow-width alternative (e.g. a card-per-row layout instead of horizontal-scrolling a wide table) — per this project's own standing rule that only tables/diagrams/code blocks may need internal horizontal scroll, and even then each should be in its own contained scroll area, never the page body itself scrolling horizontally.
- Forms migrated in 051 MUST remain usable at mobile width — labels/inputs stack to single-column, touch targets meet a reasonable minimum size (consistent with `PRODUCT.md`'s "larger touch targets" accessibility guidance, which explicitly calls this out for the patient-facing surface).
- The existing calendar/day-sheet views (`DaySheet.tsx` and related session/slot views) MUST be checked specifically, since dense schedule-grid UIs are historically the hardest pattern to make work at narrow widths — the plan must state the specific chosen mobile pattern (e.g. a scrollable single-day view, a list-of-slots view) rather than leaving it to "whatever CSS grid does by default."
- `prefers-reduced-motion` MUST continue to be respected (already an established convention per `PRODUCT.md`) in any new responsive transition/animation added by this pass.
- This is a verification-and-fix pass across surfaces built by 046-051, not a feature that introduces new functionality — any behavior change should be a responsive-layout fix, not new capability.

## Acceptance Criteria
- Given every major screen touched by 046-051 (shell, dashboard, patient hub, tables, forms, day sheet), when viewed at a ~400px-wide viewport, then no page requires horizontal body scrolling, no interactive element is obscured or unreachable, and all text remains legible without zooming.
- Given the sidebar shell at mobile width, when a user needs to navigate, then a clear, discoverable mechanism (drawer/hamburger/bottom nav) provides access to the same navigation available at desktop width.
- Given a data table from 050 at mobile width, when viewed, then it presents via its defined narrow-width alternative, not a horizontally-scrolling full-width table forced into a narrow viewport.
- Given the day sheet/session views, when viewed at tablet and mobile widths, then the plan's chosen specific pattern (stated explicitly, not left to default CSS behavior) is implemented and legible.
- Given the full frontend test suite plus a manual pass at mobile/tablet/desktop presets (per this project's existing browser-preview responsive testing workflow), when run after this feature, then all tests pass and no visual/functional regression is found at any tested width.

## Dependencies
- Depends on 046, 047, 048, 049, 050, and 051 all being substantially complete — this is intentionally the last feature in the redesign sequence.

## Explicitly Out of Scope
- A native mobile app or PWA — out of scope; this is responsive web behavior only, within the existing React/Vite/Tailwind stack.
- Redesigning any screen's desktop-width layout — this feature only addresses behavior at narrower widths, building on whatever 046-051 already established at desktop width.
- Any new feature/page not already covered by 046-051.

## Source References
- `PRODUCT.md` (Accessibility & Inclusion — larger touch targets, `prefers-reduced-motion`)
- This project's own established responsive/artifact standards (~400px minimum tested width, side-gutter convention, contained horizontal scroll only for tables/code/diagrams)
- `HANDOFF.md` Part 1/3 (existing responsive/motion conventions already applied piecemeal across patient-facing screens)
