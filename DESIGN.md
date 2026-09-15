# Design

Visual system for CMS2 (see [PRODUCT.md](PRODUCT.md) for strategy/voice — this file wins on
visual decisions). Implemented entirely through Tailwind v4's `@theme` in
`frontend/src/index.css`, which overrides Tailwind's *built-in* `indigo` / `gray` / `red` /
`green` / `amber` scales rather than inventing new token names. Components use ordinary
Tailwind utility classes (`bg-indigo-600`, `text-gray-900`, `rounded-lg`, `shadow-sm`,
`text-sm`, …) — the palette, radius, shadow, and type-scale changes apply automatically
everywhere those utilities are used, with no per-component token references.

## Palette (OKLCH)

**Two accents with distinct jobs — teal for action, cobalt for identity/status** — a deliberate
revision of PRODUCT.md's original "Restrained, one accent" framing, made after live user
feedback that the single-accent approach (first "harbor blue," then "clinical terracotta") read
as generic, template-y AI-default work rather than "catchy, attractive, eye-warming." Three
bolder directions were built as live, switchable demos before landing here (a brass/navy
pairing, a single vivid coral, a two-color coral+cobalt system) — the two-color mechanic is what
worked: one hue owns "what can I click," a second owns "whose/what state is this," so nothing
is stretched across every role at once.

- **`indigo-*` (teal, hue 194)** — primary actions, links, focus rings, current selection. Not
  the same failure mode as the original "harbor blue": that was a washed-out, low-chroma
  blue-teal used as the *only* signal in the system; this is a saturated, deliberately-chosen
  teal that is one half of a two-color system, not a stand-in for "no color decision was made."
- **`cobalt-*` (hue 258)** — a genuinely new Tailwind color (not an override) for identity and
  status only: the shell header's brand mark and signed-in-user avatar, the `Doctor` role badge,
  a day-sheet slot's `Booked` status pill. Never used for a primary action, so it never competes
  with teal for "what do I click" — the same functional split validated in the demo mockup.

Verified by direct contrast measurement (canvas-rendered pixel readback, not just eyeballing),
not assumption:

- `indigo-600` (primary buttons) on white text: **5.5:1**
- `indigo-700` (hover) on white text: **7.6:1**
- `indigo-600` (links) on white background: **5.5:1**
- `cobalt-600` (brand mark, avatars) on white text: **6.1:1**
- `cobalt-700` on `cobalt-100` (role badge, status pill text) : **7.5:1**

All comfortably clear WCAG AA (4.5:1 body text, 3:1 UI components/large text) with real margin.

| Role | Tailwind key | 600 (primary) value |
|---|---|---|
| Primary accent — action | `indigo-*` (teal) | `oklch(0.50 0.115 194)` |
| Second accent — identity/status | `cobalt-*` | `oklch(0.50 0.16 258)` |
| Neutral (cool slate, hue 250 — deliberately NOT tied to either accent hue; see note below) | `gray-*` | `oklch(0.457 0.013 250)` (600) |
| Destructive | `red-*` | `oklch(0.50 0.19 25)` (600) |
| Success | `green-*` | `oklch(0.49 0.15 150)` (600) |
| Warning | `amber-*` | `oklch(0.60 0.16 78)` (600) |

Full 50–950 ramps are defined in `frontend/src/index.css`'s `@theme` block. `blue-*` is left at
Tailwind's default and reserved for one-off "info" callouts, distinct from both accents.

**Why the neutrals aren't tinted toward either accent**: a warm accent's natural complementary
neutral drifts toward cream/sand — the single most recognizable "AI-generated UI" tell there is
(near-white warm-tinted body backgrounds) — and teal/cobalt are both cool hues that a cool slate
neutral already sits comfortably next to without needing to chase either one. `gray-*` keeps the
same quiet, cool slate tint it has held across both rebrands, on purpose: neutrals recede,
accents carry the color decisions.

## Typography

Single family (product register: one well-tuned sans, no display/body pairing) — **Figtree**
(Google Fonts, weights 400–800), replacing the previous no-font-declared browser default.
Chosen over Inter/Plus Jakarta Sans/Geist/Space Grotesk specifically because those are now
themselves the saturated AI-default choice; Figtree gives warmth (rounded-humanist terminals)
without reading generic.

Fixed rem scale, ~1.12–1.2 step ratio, each with a paired line-height (product register: no
fluid `clamp()`):

| Utility | Size | Line-height |
|---|---|---|
| `text-xs` | 13px | 1.4 |
| `text-sm` | 15px | 1.57 |
| `text-base` | 17px | 1.65 |
| `text-lg` | 19px | 1.5 |
| `text-xl` | 22px | 1.4 |
| `text-2xl` | 27px | 1.3 |
| `text-3xl` | 33px | 1.25 |

Headings (`h1`–`h3`): weight 700, letter-spacing `-0.015em`, `text-wrap: balance`.

## Surface

- Radius: `rounded-md` → 8px, `rounded-lg` → 12px, `rounded-xl` → 16px (was 6/8px) — slightly
  more human/warm without becoming bubbly.
- Shadow: `shadow-sm` is a soft, ink-tinted two-layer shadow (`oklch(0.2 0.02 250 / …)` — tied to
  the cool neutral scale, not the warm accent, so elevation reads as neutral "ink," not tinted
  brown) instead of flat neutral-gray — the one place elevation is used, so it earns real craft.
- Cards: `rounded-lg/xl border border-gray-200 bg-white shadow-sm`, `p-4`–`p-6`. Used for real
  content groupings (forms, list rows), not decoratively nested.

## Components

- **`.input`** (`frontend/src/index.css` `@layer components`): `rounded-lg`, taller padding
  (`px-3.5 py-2.5` — deliberately larger touch target for the patient-facing accessibility
  commitment in PRODUCT.md), `hover`/`focus`/`disabled`/`aria-invalid` states, soft focus ring
  (`ring-2 ring-indigo-500/30`).
- **Buttons** (utility classes, no component class — product register default): primary
  (`bg-indigo-600` + white text + `shadow-sm`), secondary (`bg-gray-100` + `text-gray-700`),
  destructive (`bg-red-600` + white text). All three: `rounded-lg`, `font-semibold`,
  `transition-all duration-150 ease-out`, `active:scale-[0.98]` press feedback,
  `focus-visible:ring-2` (this + input focus rings closed a real WCAG gap — no interactive
  element anywhere had a visible focus indicator before this pass).
- **Links**: `text-indigo-600`, `hover:text-indigo-700` + `hover:underline`, `transition-colors`.
- **`:focus-visible`**: global 2px `indigo-500` outline as a base-layer fallback beyond the
  component-level rings above.

## Motion

150ms, `ease-out`, on hover/active/focus only — state feedback, not decoration (product
register: no orchestrated page-load sequences). `prefers-reduced-motion: reduce` neutralizes
all transitions/animations globally in `@layer base`.

## Layout

Three shells (`PatientShell`, `StaffShell`, `AdminShell`) share one header pattern: a small
brand mark (rounded `indigo-600` square, "C") + wordmark on the left, session/role indicator on
the right. `ClinicShell` uses a breadcrumb (`Clinic tools / {clinicId}`) instead of two stacked
plain links. Content areas: `max-w-3xl`–`max-w-4xl`, `p-6 sm:p-8`.
