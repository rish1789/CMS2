import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { listClinicDoctors, type DoctorSummary } from './api'
import { loadStaffSession } from '../staff-login/token'
import { ListSkeleton } from '../../components/ListSkeleton'
import { PaginationControls } from '../../components/PaginationControls'
import { avatarGradientClass } from '../../components/avatarGradient'

const DOCTORS_PAGE_SIZE = 20
const SEARCH_DEBOUNCE_MS = 300

// pagination-unification-2026-09-10: real server-side pagination, replacing the "fetch every
// doctor, reveal more client-side" pattern - a clinic with a large doctor roster (the 100-doctor
// stress scenario this endpoint was originally audited against) no longer downloads it all up
// front just to show the first 20.
//
// doctors-search-2026-09-10: a searchable table, not a card grid - borrows the layout from a
// provided design reference (mockup's doctors-list.html) rebuilt in this app's own indigo/
// gray-* design tokens, matching Roster's/Day Sheet's identical table convention rather than
// introducing a new visual language.
export function DoctorPicker() {
  const { clinicId } = useParams<{ clinicId: string }>()
  const [doctors, setDoctors] = useState<DoctorSummary[] | null>(null)
  const [totalCount, setTotalCount] = useState(0)
  const [page, setPage] = useState(0)
  const [searchInput, setSearchInput] = useState('')
  const [searchTerm, setSearchTerm] = useState('')
  const [error, setError] = useState<string | null>(null)

  // Debounce free-text search only - see StaffPicker's identical 300ms debounce.
  useEffect(() => {
    const timer = setTimeout(() => setSearchTerm(searchInput), SEARCH_DEBOUNCE_MS)
    return () => clearTimeout(timer)
  }, [searchInput])

  // Reset to page 0 whenever the search term actually changes - adjusting state during render,
  // same pattern used by StaffPicker/PendingClinicsList for their own query-changed resets.
  const [prevSearchTerm, setPrevSearchTerm] = useState(searchTerm)
  if (searchTerm !== prevSearchTerm) {
    setPrevSearchTerm(searchTerm)
    if (page !== 0) setPage(0)
  }

  useEffect(() => {
    if (!clinicId) return
    const session = loadStaffSession()
    if (!session) return
    setDoctors(null)
    listClinicDoctors(clinicId, session.token, { q: searchTerm.trim() || undefined, page, size: DOCTORS_PAGE_SIZE })
      .then((result) => {
        setDoctors(result.doctors)
        setTotalCount(result.totalCount)
      })
      .catch(() => setError('Failed to load doctors.'))
  }, [clinicId, searchTerm, page])

  if (!clinicId) return null

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-lg font-semibold text-gray-900">Doctors</h1>
          <p className="mt-0.5 text-sm text-gray-600">Doctors staffed at this clinic.</p>
        </div>
        <div className="min-w-0">
          <label htmlFor="doctor-search" className="sr-only">
            Search doctors
          </label>
          <input
            id="doctor-search"
            type="search"
            value={searchInput}
            onChange={(event) => setSearchInput(event.target.value)}
            placeholder="Search by name, specialization, or staff code"
            className="w-72 max-w-full rounded-lg border border-gray-300 px-3 py-2 text-sm text-gray-700 placeholder:text-gray-400 focus:border-indigo-400 focus:outline-none focus:ring-2 focus:ring-indigo-500/30"
          />
        </div>
      </div>

      {error && (
        <p role="alert" className="rounded-md bg-red-50 p-3 text-sm text-red-700">
          {error}
        </p>
      )}

      {doctors === null && !error && <ListSkeleton rows={3} />}

      {doctors && doctors.length === 0 && (
        <p className="rounded-xl border border-gray-200 bg-white p-6 text-sm text-gray-500 shadow-sm">
          {searchTerm ? 'No doctors match your search.' : 'No doctors staffed at this clinic yet.'}
        </p>
      )}

      {doctors && doctors.length > 0 && (
        <>
          <div className="overflow-x-auto rounded-xl border border-gray-200 bg-white shadow-sm">
            <table className="w-full min-w-[640px] text-left text-sm">
              <thead>
                <tr className="border-b border-gray-200 bg-gray-50 text-xs font-semibold uppercase tracking-wide text-gray-500">
                  <th scope="col" className="px-4 py-3 font-semibold">
                    Staff code
                  </th>
                  <th scope="col" className="px-4 py-3 font-semibold">
                    Doctor
                  </th>
                  <th scope="col" className="px-4 py-3 font-semibold">
                    Specialization
                  </th>
                  <th scope="col" className="px-4 py-3 font-semibold">
                    <span className="sr-only">Actions</span>
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {doctors.map((doctor) => (
                  <tr key={doctor.doctorProfileId} className="transition-colors duration-150 hover:bg-gray-50">
                    <td className="px-4 py-3 text-gray-600">{doctor.staffCode}</td>
                    <td className="px-4 py-3">
                      <div className="flex items-center gap-3">
                        <span
                          aria-hidden="true"
                          className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-gradient-to-br text-xs font-semibold text-white shadow-sm ${avatarGradientClass(doctor.name)}`}
                        >
                          {doctor.name.charAt(0).toUpperCase()}
                        </span>
                        <span className="font-medium text-gray-900">{doctor.name}</span>
                      </div>
                    </td>
                    <td className="px-4 py-3">
                      <span className="rounded-full bg-indigo-50 px-2.5 py-1 text-xs font-semibold text-indigo-700">
                        {doctor.specialization}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-right">
                      <div className="flex justify-end gap-2">
                        <Link
                          to={`/staff/clinics/${clinicId}/doctors/${doctor.doctorProfileId}/schedule`}
                          className="inline-flex h-8 items-center rounded-lg border border-gray-300 px-3 text-xs font-medium text-gray-700 transition-colors duration-150 hover:border-indigo-300 hover:bg-indigo-50 hover:text-indigo-700"
                        >
                          Define schedule
                        </Link>
                        <Link
                          to={`/staff/clinics/${clinicId}/doctors/${doctor.doctorProfileId}/appointment-types`}
                          className="inline-flex h-8 items-center rounded-lg border border-gray-300 px-3 text-xs font-medium text-gray-700 transition-colors duration-150 hover:border-indigo-300 hover:bg-indigo-50 hover:text-indigo-700"
                        >
                          Appointment types
                        </Link>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <PaginationControls page={page} pageSize={DOCTORS_PAGE_SIZE} totalCount={totalCount} onPageChange={setPage} itemLabel="doctors" />
        </>
      )}
    </div>
  )
}
