# Feature Specification: Shared UI Component Library

**Feature Branch**: `049-shared-ui-components`

**Created**: 2026-09-15

**Status**: Draft

**Input**: User description: "backlog/046-shared-ui-component-library.md" — build shared Button/Input/Select/Card/Table/Badge/Modal/Toast/EmptyState/LoadingState primitives from the existing `DESIGN.md` tokens, codifying patterns already used ad hoc across ~6+ files, and migrate the 3 real native-`<dialog>` consumers onto one shared `Modal`. First of the 046-052 design-system/UX-redesign sub-wave — foundational for every later feature in it.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - One Modal component, not three near-identical dialogs (Priority: P1)

A developer needs to add a new confirmation dialog and wants to reuse the existing native-`<dialog>` shell (backdrop-click-to-dismiss, Escape-to-close, focus trapping, close button, header) instead of copy-pasting one of the three existing near-identical implementations.

**Why this priority**: The most concrete, already-identified duplication in the codebase (confirmed: `DeleteConfirmModal.tsx`, `RejectConfirmModal.tsx`, `EmployeeModal.tsx` all hand-roll the identical native-`<dialog>` shell independently) — extracting it is the single highest-value, lowest-risk item, and directly continues Part 3's established native-`<dialog>` accessibility pattern rather than regressing it.

**Independent Test**: Render the shared `Modal` component standalone and confirm backdrop-click-to-dismiss, Escape-to-close, and focus trapping all work exactly as `DeleteConfirmModal`'s existing (already accessibility-hardened) behavior does today.

**Acceptance Scenarios**:

1. **Given** the shared `Modal` component, **When** rendered with a title and body content, **Then** it opens via native `showModal()`, traps focus, and closes on Escape or backdrop click — matching the existing `DeleteConfirmModal`/`RejectConfirmModal` behavior exactly.
2. **Given** `DeleteConfirmModal.tsx` and `RejectConfirmModal.tsx` migrated onto the shared `Modal`, **When** their existing tests run, **Then** they pass unmodified (same accessibility behavior, same visual shell).

---

### User Story 2 - Transient success/error feedback without a full-page reload or a permanent banner (Priority: P1)

A user completing an action (creating a patient, saving a schedule) wants brief, non-blocking confirmation that it worked, instead of wondering whether their click registered — a genuinely missing capability today (confirmed: no toast/notification system exists anywhere in this codebase; all feedback today is inline, permanent, and in-form).

**Why this priority**: The one component in this feature that's new capability, not a refactor — directly serves the constitution's "never leave users wondering whether their action worked" standard, and every later feature in this wave (048 dashboard, 050 tables, 051 forms) will want it.

**Independent Test**: Trigger a toast from a proof-of-concept screen and confirm it appears, is dismissible (or auto-dismisses), and never blocks interaction with the rest of the page.

**Acceptance Scenarios**:

1. **Given** an action completes successfully, **When** a toast is triggered, **Then** it appears without navigating away or blocking the page, and disappears (auto or dismissed) without leaving a stale message behind.
2. **Given** an in-form validation error banner already exists on a page (the current pattern for `role="alert"` inline errors), **When** a toast is also shown, **Then** the two don't conflict — the toast is for transient, post-action feedback; the inline banner stays the pattern for in-form validation.

---

### User Story 3 - Consistent buttons, inputs, cards, badges, tables, empty/loading states (Priority: P2)

A developer building any new screen wants to reach for one Button/Input/Select/Card/Badge/Table/EmptyState/LoadingState component instead of re-deriving the same Tailwind utility string that's already copy-pasted across at least 6 existing files.

**Why this priority**: High value but lower urgency than P1 items — these primitives codify already-consistent visual patterns (per `DESIGN.md`) into reusable components; the visual outcome for an end user doesn't change today, only what future development reuses.

**Independent Test**: Render each new primitive standalone and confirm it matches `DESIGN.md`'s documented tokens exactly (color, radius, shadow, spacing, motion).

**Acceptance Scenarios**:

1. **Given** a new Button/Input/Select/Card/Badge/Table/EmptyState/LoadingState component, **When** rendered, **Then** its visual output matches `DESIGN.md`'s documented values (colors, radius, shadow, type scale, motion) with no new token invented.
2. **Given** at least 2 real existing usages of the copy-pasted card/tile pattern, **When** migrated onto the shared `Card` component, **Then** they render identically to before.

---

### Edge Cases

- What happens to `prefers-reduced-motion` for the new Toast's enter/exit transition? Must respect the existing global `prefers-reduced-motion: reduce` neutralization already established in `frontend/src/index.css`'s `@layer base` — no new motion exception.
- What happens when a component needs a prop/variant none of the current 3 migrated consumers use? Build only what the real, current call sites actually need (Constitution Principle II) — do not add speculative variants for hypothetical future use.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: A shared `Modal` component MUST exist, built on the native `<dialog>` pattern already established (Part 3), and MUST be adopted by `DeleteConfirmModal.tsx`, `RejectConfirmModal.tsx`, and `EmployeeModal.tsx` — all 3 real current consumers, not a subset.
- **FR-002**: A Toast/notification system MUST exist as new capability: a way to show transient, non-blocking, auto-dismissing (or dismissible) feedback, distinct from the existing permanent inline `role="alert"` banner pattern.
- **FR-003**: Shared `Button`, `Input`, `Select`, `Card`, `Badge`, `Table` (or table-support primitives), `EmptyState`, and `LoadingState` components MUST exist, built from `DESIGN.md`'s documented tokens with no new color/radius/shadow/spacing value invented.
- **FR-004**: This feature MUST include at least 2-3 representative real-screen migrations beyond the 3 Modal consumers (proof-of-concept, per the backlog's own requirement) — not just building components in isolation with zero real usage.
- **FR-005**: All new components MUST meet WCAG 2.1 AA (keyboard operable, visible focus states, proper ARIA where applicable) and respect `prefers-reduced-motion`.
- **FR-006**: This feature MUST NOT migrate every existing screen onto the new components — that is explicitly 050 (tables)/051 (forms)'s job, done incrementally.

### Key Entities

N/A — no data model changes; this is a frontend component library.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: All 3 real native-`<dialog>` consumers (`DeleteConfirmModal`, `RejectConfirmModal`, `EmployeeModal`) use the shared `Modal` component, with their existing tests passing unmodified.
- **SC-002**: A working Toast system exists and is demonstrated on at least one real screen.
- **SC-003**: At least 2 real existing screens are migrated onto at least one of the new non-Modal primitives (Card, Button, EmptyState, etc.), rendering identically to before.
- **SC-004**: The full frontend test suite passes after this feature with zero regressions and no reduction in test count.

## Assumptions

- `DESIGN.md` (read in full before writing this spec) is the source of truth for every visual value — no new color, radius, shadow, or type-scale value is introduced by this feature.
- The 3 Modal consumers are the only current native-`<dialog>` users in the codebase (verified: `grep -rl "showModal\|<dialog>"` returns exactly `DeleteConfirmModal.tsx`, `RejectConfirmModal.tsx`, `EmployeeModal.tsx`, plus the `test-setup.ts` polyfill).
- The exact set of Card/Button/EmptyState migration targets for FR-004/SC-003 is a planning-time decision, chosen from real, already-identified duplication (the 6+ files sharing the copy-pasted tile/card pattern, and the 6+ files with inconsistent empty-state wrapper markup) documented in this project's own prior audit.
