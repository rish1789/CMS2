import { useEffect, useState } from 'react'
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
import { IconBadge, DoctorIcon, ArrowIcon } from '../../components/adminIcons'

const DEBOUNCE_MS = 300

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

  const [results, setResults] = useState<DiscoveryResult[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    listDiscoveryCities().then(setCities).catch(() => setCities([]))
    listDiscoverySpecializations().then(setSpecializations).catch(() => setSpecializations([]))
  }, [])

  useEffect(() => {
    const timer = window.setTimeout(() => setDebouncedQuery(query), DEBOUNCE_MS)
    return () => window.clearTimeout(timer)
  }, [query])

  useEffect(() => {
    let cancelled = false
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
    })
      .then((data) => {
        if (!cancelled) setResults(data)
      })
      .catch(() => {
        if (!cancelled) setError('Something went wrong. Please try again.')
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })

    return () => {
      cancelled = true
    }
  }, [debouncedQuery, city, specialization, minExperienceYears, sortKey])

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
        <FilterSelect value={city} onChange={setCity} ariaLabel="Filter by city">
          <option value="">All cities</option>
          {cities.map((c) => (
            <option key={c} value={c}>
              {c}
            </option>
          ))}
        </FilterSelect>

        <FilterSelect value={specialization} onChange={setSpecialization} ariaLabel="Filter by specialization">
          <option value="">All specializations</option>
          {specializations.map((s) => (
            <option key={s} value={s}>
              {s}
            </option>
          ))}
        </FilterSelect>

        <FilterSelect value={minExperienceYears} onChange={setMinExperienceYears} ariaLabel="Filter by experience">
          {EXPERIENCE_OPTIONS.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </FilterSelect>

        <FilterSelect value={sortKey} onChange={setSortKey} ariaLabel="Sort results">
          {SORT_OPTIONS.map((option) => (
            <option key={sortKeyOf(option.field, option.direction)} value={sortKeyOf(option.field, option.direction)}>
              {option.label}
            </option>
          ))}
        </FilterSelect>
      </div>

      {error && (
        <p role="alert" className="rounded-md bg-red-50 p-3 text-sm text-red-700">
          {error}
        </p>
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
    </div>
  )
}
