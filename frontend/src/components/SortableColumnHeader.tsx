export interface SortableColumnHeaderProps<TField extends string> {
  label: string
  field: TField
  currentSort: TField
  currentDirection: 'asc' | 'desc'
  onSort: (field: TField) => void
  className?: string
}

// super-admin-console-redesign-2026-09-11: shared by the Clinic and Doctor verification
// queues' sortable columns - clicking the active column flips direction, clicking a different
// one selects it (starting ascending).
export function SortableColumnHeader<TField extends string>({
  label,
  field,
  currentSort,
  currentDirection,
  onSort,
  className,
}: SortableColumnHeaderProps<TField>) {
  const isActive = currentSort === field
  return (
    <th
      scope="col"
      className={className ?? 'px-4 py-3 font-semibold'}
      aria-sort={isActive ? (currentDirection === 'asc' ? 'ascending' : 'descending') : 'none'}
    >
      <button
        type="button"
        onClick={() => onSort(field)}
        className="inline-flex items-center gap-1 transition-colors duration-150 hover:text-gray-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-1"
      >
        {label}
        <span aria-hidden="true" className={`text-[10px] ${isActive ? 'text-gray-700' : 'text-gray-300'}`}>
          {isActive && currentDirection === 'asc' ? '▲' : '▼'}
        </span>
      </button>
    </th>
  )
}
