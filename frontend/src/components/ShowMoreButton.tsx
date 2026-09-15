export function ShowMoreButton({
  remaining,
  pageSize,
  onClick,
}: {
  remaining: number
  pageSize: number
  onClick: () => void
}) {
  const nextBatch = Math.min(remaining, pageSize)
  return (
    <button
      type="button"
      onClick={onClick}
      className="w-full rounded-lg border border-gray-200 bg-white py-2.5 text-sm font-semibold text-indigo-600 shadow-xs transition-colors duration-150 hover:border-indigo-300 hover:bg-indigo-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-2"
    >
      Show {nextBatch} more ({remaining} remaining)
    </button>
  )
}
