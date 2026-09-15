import { useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router-dom'
import { searchPatients, type PatientSearchResult } from './api'
import { loadStaffSession } from '../staff-login/token'
import { ListSkeleton } from '../../components/ListSkeleton'
import { PaginationControls } from '../../components/PaginationControls'

const RESULTS_PAGE_SIZE = 15

// pagination-unification-2026-09-10: real server-side pagination, replacing the "fetch every
// match, reveal more client-side" pattern - a large clinic's patient panel can produce hundreds
// of hits for a common name/phone prefix.
export function PatientSearch() {
  const { clinicId } = useParams<{ clinicId: string }>()
  const [term, setTerm] = useState('')
  const [results, setResults] = useState<PatientSearchResult[] | null>(null)
  const [totalCount, setTotalCount] = useState(0)
  const [page, setPage] = useState(0)
  const [error, setError] = useState<string | null>(null)
  const [searching, setSearching] = useState(false)

  async function runSearch(searchPage: number) {
    if (!clinicId || term.trim().length < 2) return
    const session = loadStaffSession()
    if (!session) return
    setSearching(true)
    setError(null)
    try {
      const result = await searchPatients(clinicId, term.trim(), session.token, { page: searchPage, size: RESULTS_PAGE_SIZE })
      setResults(result.patients)
      setTotalCount(result.totalCount)
      setPage(searchPage)
    } catch {
      setError('Search failed. Please try again.')
    } finally {
      setSearching(false)
    }
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    await runSearch(0)
  }

  if (!clinicId) return null

  return (
    <div className="space-y-4">
      <div>
        <h1 className="text-lg font-semibold text-gray-900">Find a patient</h1>
        <p className="mt-0.5 text-sm text-gray-600">Search this clinic's patients by name or phone number.</p>
      </div>

      <form onSubmit={handleSubmit} className="flex max-w-md gap-2">
        <input
          aria-label="Search by name or phone"
          placeholder="Search by name or phone"
          value={term}
          onChange={(e) => setTerm(e.target.value)}
          className="input"
        />
        <button
          type="submit"
          disabled={searching || term.trim().length < 2}
          className="shrink-0 rounded-lg bg-indigo-600 px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition-all duration-150 ease-out hover:bg-indigo-500 hover:shadow active:scale-[0.98] disabled:opacity-50 disabled:pointer-events-none disabled:active:scale-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-2"
        >
          {searching ? 'Searching…' : 'Search'}
        </button>
      </form>

      {error && (
        <p role="alert" className="rounded-md bg-red-50 p-3 text-sm text-red-700">
          {error}
        </p>
      )}

      {results === null && !error && !searching && term.trim().length === 0 && (
        <p className="max-w-md rounded-lg border border-dashed border-gray-300 p-6 text-center text-sm text-gray-500">
          Type at least 2 characters of a name or phone number to search.
        </p>
      )}

      {searching && <ListSkeleton rows={3} />}

      {!searching && results !== null && results.length === 0 && (
        <p className="max-w-md rounded-lg border border-gray-200 bg-white p-6 text-sm text-gray-500 shadow-sm">
          No matching patients.
        </p>
      )}

      {!searching && results !== null && results.length > 0 && (
        <>
          {/* staff-console-audit-2026-09-10 P1: the whole card used to be a single Link straight
              to the irreversible anonymize flow - clicking a patient's name to look them up (the
              most natural act here) landed on the destructive path. Only the explicit
              "Anonymize" action is a link now; the patient's info itself is plain, inert text. */}
          <ul className="grid gap-3 sm:grid-cols-2">
            {results.map((patient) => (
              <li
                key={patient.patientId}
                className="flex items-center justify-between gap-3 rounded-lg border border-gray-200 bg-white p-4 shadow-sm"
              >
                <div className="min-w-0">
                  <p className="truncate font-medium text-gray-900">{patient.name}</p>
                  {patient.phone && <p className="text-sm text-gray-600">{patient.phone}</p>}
                </div>
                <Link
                  to={`/staff/clinics/${clinicId}/patients/${patient.patientId}/anonymize`}
                  className="inline-flex h-9 shrink-0 items-center rounded-lg border border-red-300 bg-white px-3 text-sm font-medium text-red-700 transition-colors duration-150 hover:bg-red-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-red-500 focus-visible:ring-offset-2"
                >
                  Anonymize
                </Link>
              </li>
            ))}
          </ul>
          <PaginationControls
            page={page}
            pageSize={RESULTS_PAGE_SIZE}
            totalCount={totalCount}
            onPageChange={(newPage) => void runSearch(newPage)}
            itemLabel="patients"
          />
        </>
      )}
    </div>
  )
}
