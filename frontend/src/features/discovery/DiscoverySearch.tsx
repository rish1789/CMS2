import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  listDiscoveryCities,
  listDiscoverySpecializations,
  searchDiscovery,
  type DiscoveryResult,
  type DiscoverySortField,
  type SortDirection,
} from './api'
import { FilterSelect } from '../../components/FilterSelect'
import { ListSkeleton } from '../../components/ListSkeleton'
import { PaginationControls } from '../../components/PaginationControls'
import { IconBadge, DoctorIcon, ArrowIcon } from '../../components/adminIcons'

const DEBOUNCE_MS = 300
// 072-discovery-pagination: the server's default page size, sent explicitly.
const PAGE_SIZE = 20

const EXPERIENCE_OPTIONS = [
  { label: 'Any experience', value: '' },
  { label: '1+ years', value: '1' },
  { label: '3+ years', value: '3' },
  { label: '5+ years', value: '5' },
  { label: '10+ years', value: '10' },
]

const SORT_OPTIONS: { label: string; field: DiscoverySortField; direction: SortDirection }[] = [
  { label: 'Name (A-Z)', field: 'doctorName', direction: 'asc' },
  { label: 'Experience: high to low', field: 'experienceYears', direction: 'desc' },
  { label: 'Experience: low to high', field: 'experienceYears', direction: 'asc' },
  { label: 'Clinic name (A-Z)', field: 'clinicName', direction: 'asc' },
]

function sortKeyOf(field: DiscoverySortField, direction: SortDirection): string {
  return `${field}:${direction}`
}

// patient-search-advanced-filtering: LinkedIn-style search - one free-text box for
// doctor/clinic name (matches specialization and address too, server-side, unchanged from
// 035's original behavior) plus a row of independent filter facets (City, Specialization,
// Experience) and a sort dropdown, replacing the single unfiltered text box.
export function DiscoverySearch() {
  const [query, setQuery] = useState('')
  const [debouncedQuery, setDebouncedQuery] = useState('')
  const [city, setCity] = useState('')
  const [specialization, setSpecialization] = useState('')
  const [minExperienceYears, setMinExperienceYears] = useState('')
  const [sortKey, setSortKey] = useState(sortKeyOf('doctorName', 'asc'))

  const [cities, setCities] = useState<string[]>([])
  const [specializations, setSpecializations] = useState<string[]>([])

  const [page, setPage] = useState(0)
  const [results, setResults] = useState<DiscoveryResult[]>([])
  const [totalCount, setTotalCount] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [attempt, setAttempt] = useState(0)

  // 072-discovery-pagination FR-006: only the newest request may update the results.
  const latestRequest = useRef(0)
  const resultsHeading = useRef<HTMLHeadingElement>(null)
  const focusResultsAfterLoad = useRef(false)

  // FR-005: a new search text, filter or sort starts again from the first page, in the same
  // update, so no request ever pairs the new filters with the old page.
  function changeFilter(set: (value: string) => void) {
    return (value: string) => {
      set(value)
      setPage(0)
    }
  }

  function changePage(next: number) {
    focusResultsAfterLoad.current = true
    setPage(next)
  }

  useEffect(() => {
    listDiscoveryCities().then(setCities).catch(() => setCities([]))
    listDiscoverySpecializations().then(setSpecializations).catch(() => setSpecializations([]))
  }, [])

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setDebouncedQuery((previous) => {
        if (previous !== query) setPage(0)
        return query
      })
    }, DEBOUNCE_MS)
    return () => window.clearTimeout(timer)
  }, [query])

  useEffect(() => {
    const request = ++latestRequest.current
    const isLatest = () => request === latestRequest.current
    setLoading(true)
    setError(null)

    const [sortField, sortDirection] = sortKey.split(':') as [DiscoverySortField, SortDirection]

    searchDiscovery({
      q: debouncedQuery,
      city: city || undefined,
      specialization: specialization || undefined,
      minExperienceYears: minExperienceYears ? Number(minExperienceYears) : undefined,
      sort: sortField,
      direction: sortDirection,
      page,
      size: PAGE_SIZE,
    })
      .then((data) => {
        if (!isLatest()) return
        setResults(data.results)
        setTotalCount(data.totalCount)
      })
      .catch(() => {
        if (isLatest()) setError('Something went wrong. Please try again.')
      })
      .finally(() => {
        if (isLatest()) setLoading(false)
      })
  }, [debouncedQuery, city, specialization, minExperienceYears, sortKey, page, attempt])

  // After a page change, move focus to the top of the new results for keyboard and screen-reader users.
  useEffect(() => {
    if (!loading && focusResultsAfterLoad.current) {
      focusResultsAfterLoad.current = false
      resultsHeading.current?.focus()
    }
  }, [loading])

  return (
    <div className="mx-auto max-w-3xl space-y-6 rounded-lg border border-gray-200 bg-white p-6 shadow-sm">
      <div>
        <h1 className="text-lg font-semibold text-gray-900">Find a clinic or doctor</h1>
        <p className="mt-1 text-sm text-gray-600">
          Search verified clinics and doctors. No account or login needed.
        </p>
      </div>

      <div>
        <label htmlFor="discoverySearch" className="sr-only">
          Search by doctor or clinic name
        </label>
        <input
          id="discoverySearch"
          type="search"
          placeholder="Search by doctor or clinic name"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          className="input w-full"
        />
      </div>

      <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        <FilterSelect value={city} onChange={changeFilter(setCity)} ariaLabel="Filter by city">
          <option value="">All cities</option>
          {cities.map((c) => (
            <option key={c} value={c}>
              {c}
            </option>
          ))}
        </FilterSelect>

        <FilterSelect value={specialization} onChange={changeFilter(setSpecialization)} ariaLabel="Filter by specialization">
          <option value="">All specializations</option>
          {specializations.map((s) => (
            <option key={s} value={s}>
              {s}
            </option>
          ))}
        </FilterSelect>

        <FilterSelect value={minExperienceYears} onChange={changeFilter(setMinExperienceYears)} ariaLabel="Filter by experience">
          {EXPERIENCE_OPTIONS.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </FilterSelect>

        <FilterSelect value={sortKey} onChange={changeFilter(setSortKey)} ariaLabel="Sort results">
          {SORT_OPTIONS.map((option) => (
            <option key={sortKeyOf(option.field, option.direction)} value={sortKeyOf(option.field, option.direction)}>
              {option.label}
            </option>
          ))}
        </FilterSelect>
      </div>

      <h2 ref={resultsHeading} tabIndex={-1} className="sr-only">
        Search results
      </h2>

      {error && (
        <div role="alert" className="flex items-center justify-between gap-3 rounded-md bg-red-50 p-3 text-sm text-red-700">
          <span>{error}</span>
          <button
            type="button"
            onClick={() => setAttempt((n) => n + 1)}
            className="rounded-lg border border-red-200 bg-white px-3 py-1.5 font-medium text-red-700 transition-colors duration-150 hover:bg-red-50"
          >
            Try again
          </button>
        </div>
      )}

      {!error && loading && <ListSkeleton rows={4} />}

      {!error && !loading && results.length === 0 && (
        <p className="rounded-lg border border-gray-200 bg-white p-6 text-sm text-gray-500 shadow-sm">
          No matching clinics or doctors found.
        </p>
      )}

      {!error && !loading && results.length > 0 && (
        <ul className="space-y-2">
          {results.map((result) => (
            <li key={`${result.doctorProfileId}-${result.clinicId}`}>
              <Link
                to={`/patient/clinics/${result.clinicId}?doctorId=${result.doctorProfileId}`}
                className="flex items-center gap-3 rounded-lg border border-gray-200 bg-white p-3 shadow-sm transition-all duration-150 ease-out hover:border-indigo-300 hover:shadow active:scale-[0.99] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-2"
              >
                <IconBadge>
                  <DoctorIcon />
                </IconBadge>
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-medium text-gray-900">{result.doctorName}</p>
                  <p className="truncate text-sm text-gray-600">
                    {result.specialization} · {result.experienceYears}{' '}
                    {result.experienceYears === 1 ? 'year' : 'years'} experience
                  </p>
                  <p className="mt-1 truncate text-sm text-gray-600">
                    <span>{result.clinicName}</span>
                    {result.clinicCity ? <span>{` · ${result.clinicCity}`}</span> : null}
                  </p>
                </div>
                <ArrowIcon className="shrink-0 text-gray-400" />
              </Link>
            </li>
          ))}
        </ul>
      )}

      {!error && !loading && totalCount > 0 && (
        <PaginationControls page={page} pageSize={PAGE_SIZE} totalCount={totalCount} onPageChange={changePage} itemLabel="doctors" />
      )}
    </div>
  )
}
