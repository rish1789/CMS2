// A muted, professional two-tone gradient per avatar, deterministic from the name so the same
// person always gets the same color and different people are visually distinguishable at a
// glance - not just a flat brand-color tint.
// Originally built for the Roster page's staff avatars; extracted here (042-day-sheet-hardening)
// so the Day Sheet's doctor avatars reuse it instead of duplicating the same palette/hash.
//
// staff-console-audit-2026-09-10 P3: these five stops used to be raw, untuned Tailwind defaults
// (violet/sky/blue/emerald/teal/orange/rose/pink) - the only place in the app where an
// off-palette color appeared, next to a design system DESIGN.md describes as "one accent,
// Restrained strategy." Retuned to the project's own oklch(L, C, H) formula for the brand's
// indigo 400/500 stops (frontend/src/index.css), just rotated to five new hues that don't
// collide with the app's existing semantic colors (210 brand blue, 25 destructive red, 150
// success green, 78 warning amber) - so decorative avatar variety now reads as one deliberate
// family instead of five different stock palettes stitched together.
const AVATAR_GRADIENTS = [
  'from-[oklch(0.72_0.11_290)] to-[oklch(0.62_0.135_290)]', // violet
  'from-[oklch(0.72_0.11_230)] to-[oklch(0.62_0.135_230)]', // sky
  'from-[oklch(0.72_0.11_165)] to-[oklch(0.62_0.135_165)]', // teal
  'from-[oklch(0.72_0.11_50)] to-[oklch(0.62_0.135_50)]', // gold
  'from-[oklch(0.72_0.11_330)] to-[oklch(0.62_0.135_330)]', // magenta
  'from-gray-400 to-gray-600',
]

export function avatarGradientClass(name: string): string {
  let hash = 0
  for (let i = 0; i < name.length; i += 1) hash = (hash * 31 + name.charCodeAt(i)) | 0
  return AVATAR_GRADIENTS[Math.abs(hash) % AVATAR_GRADIENTS.length]
}
