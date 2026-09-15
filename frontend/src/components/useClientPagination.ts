import { useState } from 'react'

/**
 * None of this project's list endpoints paginate server-side - they return every matching
 * row in one response, and every browse/picker view used to render 100% of what it received
 * with a plain `.map()`. That's fine for a handful of rows; it stops being human-scannable
 * once a clinic has real volume behind it (e.g. a single busy session's slot list, or a
 * hospital's full staff roster). This renders only the first `pageSize` items and reveals
 * more in fixed batches on request - a client-side stopgap for the "one page, no scroll limit"
 * problem that doesn't require a backend contract change.
 *
 * Resets to the first page when `resetKey` changes (or, if omitted, whenever `items` itself is
 * replaced by a new array reference). Callers that locally mutate their own array after an
 * action - e.g. `setClinics(prev => prev.filter(...))` once a clinic is verified, or
 * `setSlots(prev => prev.filter(...))` once a slot is booked - MUST pass an explicit `resetKey`
 * (something that only changes on a genuinely new list: a tab, a search term, the clinicId being
 * fetched). Without it, every such filter produces a new array reference and silently resets the
 * viewer back to page 1, discarding however far they'd paged with "Show more".
 */
export function useClientPagination<T>(items: T[] | null, pageSize: number, resetKey?: unknown) {
  const [visibleCount, setVisibleCount] = useState(pageSize)
  // Adjust state during render (the React-documented alternative to an effect for this exact
  // case) rather than in a useEffect - resetting to the first page is "derived from a prop
  // change", not a sync with an external system, so it doesn't need the extra render+commit
  // pass an effect would add.
  const key = resetKey === undefined ? items : resetKey
  const [prevKey, setPrevKey] = useState(key)
  if (key !== prevKey) {
    setPrevKey(key)
    setVisibleCount(pageSize)
  }

  const visibleItems = items === null ? null : items.slice(0, visibleCount)
  const remaining = items === null ? 0 : items.length - visibleCount
  const hasMore = remaining > 0

  function showMore() {
    setVisibleCount((count) => count + pageSize)
  }

  return { visibleItems, hasMore, remaining, showMore }
}
