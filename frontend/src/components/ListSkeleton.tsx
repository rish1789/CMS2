// Shared loading placeholder for the staff console's browse/picker lists
// (MyClinicsList, DaySheet, DoctorPicker, StaffPicker) - replaces a plain "Loading…"
// line with a shape that previews the list about to arrive.
// staff-console-audit-2026-09-10 P1: was aria-hidden with no status role at all, so every one
// of these lists loaded silently for screen reader users - role="status" + visually-hidden text
// announces it without changing anything visually.
export function ListSkeleton({ rows = 3 }: { rows?: number }) {
  return (
    <output className="block">
      <span className="sr-only">Loading…</span>
      <ul aria-hidden="true" className="space-y-2">
        {Array.from({ length: rows }).map((_, index) => (
          <li key={index} className="animate-pulse rounded-lg border border-gray-200 bg-white p-4 shadow-sm">
            <div className="h-4 w-2/5 rounded bg-gray-200" />
            <div className="mt-2.5 h-3 w-3/5 rounded bg-gray-100" />
          </li>
        ))}
      </ul>
    </output>
  )
}
