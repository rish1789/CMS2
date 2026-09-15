import type { ReactNode } from 'react'

// The native <select> arrow doesn't respect a custom border-radius consistently across
// browsers and its reserved space isn't controllable via padding alone - appearance-none
// removes it entirely in favor of one hand-drawn chevron, precisely and consistently placed.
// Originally built for the Roster page's filter row; extracted here (042-day-sheet-hardening)
// so the Day Sheet's doctor filter reuses it instead of duplicating the same markup.
const SELECT_CLASS =
  'w-full appearance-none rounded-lg border border-gray-300 bg-white py-2 pl-3 pr-8 text-sm text-gray-700 focus:border-indigo-400 focus:outline-none focus:ring-2 focus:ring-indigo-500/30'

export function FilterSelect({
  value,
  onChange,
  ariaLabel,
  children,
}: {
  value: string
  onChange: (value: string) => void
  ariaLabel: string
  children: ReactNode
}) {
  return (
    <div className="relative">
      <select value={value} onChange={(event) => onChange(event.target.value)} aria-label={ariaLabel} className={SELECT_CLASS}>
        {children}
      </select>
      <svg
        aria-hidden="true"
        viewBox="0 0 20 20"
        className="pointer-events-none absolute right-2.5 top-1/2 h-4 w-4 -translate-y-1/2 text-gray-400"
      >
        <path
          fillRule="evenodd"
          clipRule="evenodd"
          fill="currentColor"
          d="M5.23 7.21a.75.75 0 011.06.02L10 10.94l3.71-3.71a.75.75 0 111.06 1.06l-4.24 4.25a.75.75 0 01-1.06 0L5.21 8.29a.75.75 0 01.02-1.08z"
        />
      </svg>
    </div>
  )
}
