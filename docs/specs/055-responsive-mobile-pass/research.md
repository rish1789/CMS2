# Research: Responsive & Mobile Pass

## Decision 1: Verify-first, fix-only-on-finding — not a preemptive rebuild of any of the 4 named surfaces

**Decision**: For each of the backlog's 4 named surface categories (shell, tables, forms, day sheet), first inspect the current, real implementation; only write code where that inspection (source read + live browser check) finds an actual gap against the acceptance criteria. Where a surface is already correct, the "task" is the verification itself plus a stated result — not a rewrite.

**Rationale**: Verified before writing spec.md that 3 of the 4 named surfaces are already substantially correct: the sidebar shell (047) has a working, previously live-verified `sm:hidden`/`sm:block` drawer at 640px; every 051 form uses Tailwind's mobile-first `grid`/`sm:grid-cols-2` (single-column by default); `prefers-reduced-motion` is already a single global CSS override covering every current and future transition. Treating these as "verify" rather than "rebuild" tasks avoids Constitution Principle II's forbidden speculative work, and matches the backlog's own framing of this feature as "a verification-and-fix pass... not a feature that introduces new functionality."

**Alternatives considered**: Rebuild all 4 surfaces' mobile behavior from scratch to be maximally sure — rejected; would re-solve 3 already-solved problems and risk regressing genuinely working code for no benefit, directly contradicting the backlog's own "not new functionality" framing.

## Decision 2: Tables keep their existing contained-horizontal-scroll pattern; no card-view rebuild

**Decision**: All 6 table-based screens (`DaySheet.tsx`, `SessionSlotsView.tsx`, `StaffPicker.tsx`, `DoctorPicker.tsx`, `PendingClinicsList.tsx`, `PendingDoctorsList.tsx`) keep their current `overflow-x-auto` + `min-w-[Npx]` pattern. Verification confirms each is legible and fully operable at ~400px; a genuine defect (if found) is fixed with a targeted responsive-utility change (e.g. `hidden sm:table-cell` on one low-value column), never a parallel card-per-row rendering mode.

**Rationale**: This project's own cited responsive standard (referenced by the backlog itself) explicitly grants tables/code/diagrams a contained-internal-scroll exception to the "page body never scrolls horizontally" rule. All 6 tables already use this exception identically and consistently, with reasonable min-widths (560-720px) sized to their actual column count. A bespoke card-view alternative for 6 structurally different tables (verification-queue action rows, a roster, a per-row-linked day sheet, 2 pickers) is a substantial net-new rendering mode per screen — real new capability, not a fix, and exactly what the backlog's own "not a feature that introduces new functionality" line forbids.

**Alternatives considered**: Build one shared `ResponsiveTable`/card-list primitive all 6 could adopt — rejected as premature abstraction (Constitution Principle II): no verification has yet shown the existing pattern is actually broken for any of the 6, so there is no confirmed problem this primitive would solve; 050's own `research.md` already rejected building a generic `Table` component for materially the same reason (no consumer need proven beyond what already exists).

## Decision 3: Touch-target verification is scoped to the patient-facing surface

**Decision**: FR-004's minimum comfortable tap-target check (informally targeting ~44×44 CSS pixels, the common Apple HIG / near-WCAG-2.5.5-AAA convention, since neither the backlog nor `PRODUCT.md` names an exact number) is applied only to `SignupForm.tsx` and patient `BookSlotForm.tsx` — not uniformly across staff/admin screens.

**Rationale**: `PRODUCT.md`'s Accessibility & Inclusion section scopes "larger touch targets" explicitly to "the patient-facing surface specifically," with WCAG 2.1 AA (this codebase's stated floor, which has no minimum-target-size criterion — that arrived in WCAG 2.2 AA / 2.1 AAA) applying everywhere else. Applying an enhanced size standard to staff/admin surfaces the project's own design principles don't ask for would be scope creep beyond what either the backlog or `PRODUCT.md` actually requires.

**Alternatives considered**: Apply the same enlarged minimum everywhere for consistency — rejected; contradicts `PRODUCT.md`'s own explicit, deliberate differentiation between the patient surface (reassurance-oriented, older/less-tech-savvy audience) and staff/admin surfaces (speed/density-oriented, per the same document's "One system, three audiences" principle).

## Decision 4: Live browser verification, not a new automated-test category

**Decision**: The primary verification method is live inspection in the Browser pane at ~400px/~768px/desktop presets (this project's own established workflow, used for every prior 046-051 responsive/visual check), not a new suite of viewport-simulating automated tests. A fix gets a new/updated Vitest test only when the defect has a stable DOM signature independent of actual rendered layout (e.g. a column's `className` gaining a breakpoint-scoped `hidden` class is testable; whether text visually wraps at 400px is not, since jsdom has no real layout engine).

**Rationale**: jsdom (this project's test environment) does not perform real CSS layout — it cannot detect whether content overflows, wraps, or clips at a given viewport width, which is precisely what this feature verifies. Live browser inspection is the only way to genuinely test the acceptance criteria's actual claims ("no page requires horizontal body scrolling," "no interactive element is obscured"). This mirrors this session's own established practice for prior visual-only verification (e.g. 046's hover-state checks, 047's drawer-collapse checks) — live-verified, not fabricated as jsdom assertions that wouldn't actually prove the claim.

**Alternatives considered**: Add `resize_window`-driven snapshot assertions to Vitest — rejected; Vitest/jsdom has no real rendering engine to snapshot against, so such a test would only assert the CSS classes present, not that they produce the claimed visual result — indistinguishable in value from just reading the className correctly, while adding false confidence that visual correctness was "tested."
