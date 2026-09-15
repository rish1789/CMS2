import { useState, type FormEvent } from 'react'

export interface PaginationControlsProps {
  /** 0-indexed, matching the backend's Spring Data Pageable convention - displayed to the user as page+1. */
  page: number
  pageSize: number
  totalCount: number
  onPageChange: (page: number) => void
  /** Plural noun for the count line, e.g. "sessions", "doctors" - defaults to "Records". */
  itemLabel?: string
}

// pagination-unification-2026-09-10: one shared control, replacing three different pagination
// UIs/mechanisms that had accumulated across the app - Day Sheet's plain "Page X of Y (Z
// sessions)" prose, Roster's richer Prev/Next/Jump-to-page/record-range UI (secretly still
// backed by a client-side fetch-everything query), and a "Show more" cumulative-reveal pattern
// elsewhere. This is Roster's UI (the more complete of the two genuine pagination footers),
// generalized to work off backend-supplied page/pageSize/totalCount instead of a locally-paged
// in-memory array - every consumer of this component is backed by a real paginated endpoint.
export function PaginationControls({ page, pageSize, totalCount, onPageChange, itemLabel = 'Records' }: PaginationControlsProps) {
  const [jumpToPageInput, setJumpToPageInput] = useState('')
  const totalPages = Math.max(1, Math.ceil(totalCount / pageSize))
  const startRecord = totalCount === 0 ? 0 : page * pageSize + 1
  const endRecord = Math.min((page + 1) * pageSize, totalCount)

  function handleJumpToPage(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const target = Number.parseInt(jumpToPageInput, 10)
    if (Number.isNaN(target)) return
    onPageChange(Math.min(totalPages, Math.max(1, target)) - 1)
    setJumpToPageInput('')
  }

  return (
    <div className="flex flex-wrap items-center justify-between gap-3 text-sm">
      <p className="flex items-center gap-2 tabular-nums">
        <span className="font-medium text-gray-700">
          Page {page + 1} of {totalPages}
        </span>
        <span aria-hidden="true" className="text-gray-300">
          •
        </span>
        <span className="text-gray-500">
          {startRecord}-{endRecord} of {totalCount} {itemLabel}
        </span>
      </p>
      <div className="flex items-center gap-2">
        <button
          type="button"
          onClick={() => onPageChange(Math.max(0, page - 1))}
          disabled={page === 0}
          className="rounded-lg border border-gray-300 px-3 py-1.5 font-medium text-gray-700 transition-colors duration-150 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-40"
        >
          Previous
        </button>
        <form onSubmit={handleJumpToPage} className="flex items-center gap-1.5">
          <label htmlFor="pagination-jump-to-page" className="sr-only">
            Jump to page
          </label>
          <input
            id="pagination-jump-to-page"
            type="number"
            // No min/max here: the component clamps the submitted value itself (below), and a
            // native max would make the browser's own constraint validation silently block
            // "Go" for an out-of-range value before that clamp ever runs - one clamp mechanism,
            // not two fighting each other.
            value={jumpToPageInput}
            onChange={(event) => setJumpToPageInput(event.target.value)}
            placeholder={`${page + 1}`}
            aria-label="Jump to page"
            className="w-16 rounded-lg border border-gray-300 px-2 py-1.5 text-sm text-gray-700 focus:border-indigo-400 focus:outline-none focus:ring-2 focus:ring-indigo-500/30"
          />
          <button
            type="submit"
            className="rounded-lg border border-gray-300 px-3 py-1.5 font-medium text-gray-700 transition-colors duration-150 hover:bg-gray-50"
          >
            Go
          </button>
        </form>
        <button
          type="button"
          onClick={() => onPageChange(Math.min(totalPages - 1, page + 1))}
          disabled={page + 1 >= totalPages}
          className="rounded-lg border border-gray-300 px-3 py-1.5 font-medium text-gray-700 transition-colors duration-150 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-40"
        >
          Next
        </button>
      </div>
    </div>
  )
}
