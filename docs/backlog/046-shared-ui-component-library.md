# 046 — Shared UI Component Library

**Module:** Frontend / Design System
**Status:** Ready for spec-kit intake

## User Story
As a frontend developer working anywhere in CMS2, I want a small set of shared, reusable UI primitives (button, input, select, card, table, badge, modal, toast, empty state, loading state) so that every screen looks and behaves consistently, and a UX fix (e.g. a better focus ring, a consistent error style) happens in one file instead of N copy-pasted ones.

## Context
Verified current state (2026-09-15): `frontend/src/components/` contains only narrow-purpose components (`DeleteConfirmModal.tsx`, `RejectConfirmModal.tsx` — two separate, near-duplicate confirm dialogs instead of one generic Modal; `PaginationControls.tsx`; `ListSkeleton.tsx`; `SortableColumnHeader.tsx`; `FilterSelect.tsx`; `RoleBadge.tsx`; `ShowMoreButton.tsx`; `ActionMenu.tsx`). There is **no generic Button, Input, Select, Table, Card, Toast/notification system, or EmptyState component anywhere in the codebase.** The same visual treatment (e.g. `rounded-lg border border-gray-200 bg-white p-4 shadow-sm ... hover:shadow-md active:scale-[0.98]`) is copy-pasted across at least 6 different files (`AdminDashboard.tsx`, `PatientDashboard.tsx`, `ClinicToolsDashboard.tsx`, `PendingClinicsList.tsx`, `ScheduleForm.tsx`, `BookSlotForm.tsx`). Loading states have at least 3 distinct ad hoc patterns (bare "Loading…" text, the existing `ListSkeleton`, and separate `sr-only` spans). Empty states use the same wrapper markup copy-pasted 6+ times with different wording each time, no shared component. There is no toast/notification system anywhere — all feedback today is inline (`role="alert"` banners or a success panel replacing the form).

This is the foundational feature for all of 047-052 — those features will consume what this one builds. Per the constitution and `DESIGN.md`, existing design tokens (the `@theme` block in `frontend/src/index.css` — the teal/`indigo`-named scale, the new `cobalt` scale, `gray`/`red`/`green`/`amber`, radius/shadow/typography tokens) are the source of truth and MUST be reused, not reinvented — this feature codifies existing tokens into components, it does not introduce new colors or a new visual language.

## Business Rules
- Every new component MUST be built from the existing `@theme` tokens in `frontend/src/index.css` — no new colors, no new spacing scale, no new radius/shadow values introduced without a documented reason (per `DESIGN.md`, which the plan MUST read before proposing any visual value).
- The two existing near-duplicate confirm modals (`DeleteConfirmModal.tsx`, `RejectConfirmModal.tsx`) MUST be generalized into one reusable `Modal`/`Dialog` component (both already correctly use the native `<dialog>` element per Part 3's accessibility hardening — that native-dialog pattern MUST be preserved, not regressed back to a hand-rolled backdrop+keydown-listener approach).
- A Toast/notification system is new capability, not a refactor (nothing like it exists today) — it must not replace every existing inline `role="alert"` banner (those remain appropriate for in-form validation errors); it's specifically for transient, non-blocking success/error feedback after an action completes (e.g. "Patient created successfully"), matching the constitution's UX intent that users should never wonder whether their action worked.
- New components MUST meet WCAG 2.1 AA per `PRODUCT.md`'s stated accessibility floor — keyboard navigable, proper focus states, `aria-*` attributes where applicable, and respect `prefers-reduced-motion`.
- This feature builds the components; it does NOT migrate every existing screen to use them (that's 047-052's job, screen by screen) — but it MUST include at least 2-3 representative migrations as proof-of-concept (e.g., replace the two confirm modals with the new generic one; replace 2-3 of the copy-pasted empty-state wrappers with the new `EmptyState` component) so the library is validated against real usage, not built in a vacuum.
- No component should be more configurable than the current, real, confirmed use cases require (constitution Principle II — YAGNI) — build the props/variants actually needed by existing screens, not a speculative general-purpose kit.

## Acceptance Criteria
- Given the new component library, when a developer needs a button/input/select/card/table/badge/modal/toast/empty-state/loading-state anywhere in the app, then a shared component exists in `frontend/src/components/` covering that need, built from existing design tokens.
- Given `DeleteConfirmModal.tsx` and `RejectConfirmModal.tsx` after this feature, when inspected, then both are replaced by (or now built on top of) one shared `Modal` component, with no loss of the existing native-`<dialog>` accessibility behavior (verify: backdrop-click-to-dismiss, Escape-to-close, focus trapping all still work, per Part 3's existing polyfilled test coverage in `test-setup.ts`).
- Given the new Toast system, when a proof-of-concept screen triggers a toast (e.g. after a successful create action), then it appears, is dismissible or auto-dismisses, and doesn't block interaction with the rest of the page.
- Given the full frontend test suite, when run after this feature (including the proof-of-concept migrations), then all existing tests pass with zero regressions, plus new tests for the new components themselves.
- Given a manual accessibility check (keyboard-only navigation, screen reader landmark check) on at least the new Modal and Toast components, when performed, then both are fully keyboard-operable with correct focus management.

## Dependencies
- None of the 39 converged features block this.
- Should land before (or very early alongside) 047 (shell navigation), 048 (staff dashboard), 049 (patient hub), 050 (tables), 051 (forms) — those features are expected to consume this library, not duplicate its work.
- Independent of 040-045 (backend hardening) — can run in parallel.

## Explicitly Out of Scope
- Migrating every existing screen to the new components — that is explicitly the job of 047-052, done incrementally per-screen as each of those features lands.
- Introducing a component library dependency (e.g. Radix, shadcn/ui, MUI) — build directly on existing Tailwind tokens and React, consistent with the constitution's "no unnecessary new dependencies" (Principle II) and the existing codebase's zero-UI-library convention.
- Any new color, font, or spacing token not already defined in `frontend/src/index.css`'s `@theme` block — if the redesign genuinely needs one, that's a `DESIGN.md`-documented decision to flag explicitly during planning, not something to add silently inside this feature.

## Source References
- `DESIGN.md` (current color/type/motion tokens — read before proposing any visual value)
- `PRODUCT.md` (Design Principles, Accessibility & Inclusion)
- `HANDOFF.md` Part 1 (established elevation/chip conventions), Part 3 (native `<dialog>` accessibility pattern, `jsdom` polyfill precedent)
- Verified against current repository state via direct inspection, 2026-09-15 (no generic Button/Input/Select/Table/Card/Toast/EmptyState component exists; two near-duplicate confirm modals confirmed; copy-pasted tile/card markup confirmed across 6 files)
