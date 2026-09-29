import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { listClinicDoctors, listDoctorBookingReadiness, type DoctorSummary } from './api'
import { loadStaffSession } from '../staff-login/token'
import { PaginationControls } from '../../components/PaginationControls'
import { avatarGradientClass } from '../../components/avatarGradient'
import { EmptyState } from '../../components/EmptyState'
import { LoadingState } from '../../components/LoadingState'

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
  // real-bug-fix 2026-09-17: a doctor with zero appointment types and no default fee looks fully
  // staffed here otherwise - fetched separately (its own endpoint, own module) so a failure here
  // never blocks the doctor list itself from rendering, only the warning badges stay off.
  const [notBookingReadyIds, setNotBookingReadyIds] = useState<Set<string>>(new Set())
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

  useEffect(() => {
    if (!clinicId) return
    const session = loadStaffSession()
    if (!session) return
    listDoctorBookingReadiness(clinicId, session.token)
      .then((result) => {
        setNotBookingReadyIds(new Set(result.filter((r) => !r.bookingReady).map((r) => r.doctorProfileId)))
      })
      .catch(() => {
        // Non-fatal - the doctor list above still renders, only the warning badges stay off.
      })
  }, [clinicId])

  if (!clinicId) return null

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-lg font-semibold text-gray-900">Doctors</h1>
          <p className="mt-0.5 text-sm text-gray-600">Doctors staffed at this clinic.</p>
        </div>
        <div className="w-full sm:w-auto">
          <label htmlFor="doctor-search" className="sr-only">
            Search doctors
          </label>
          {/* 055-responsive-mobile-pass: was `w-72 max-w-full`, which doesn't actually clamp to
              the wrapped flex row's available width at mobile - caused real page-body horizontal
              overflow (measured 538px scrollWidth in a 375px viewport). `w-full sm:w-72` fixes
              it while keeping the exact desktop-width appearance (FR-006). */}
          <input
            id="doctor-search"
            type="search"
            value={searchInput}
            onChange={(event) => setSearchInput(event.target.value)}
            placeholder="Search by name, specialization, or staff code"
            className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm text-gray-700 placeholder:text-gray-400 focus:border-indigo-400 focus:outline-none focus:ring-2 focus:ring-indigo-500/30 sm:w-72"
          />
        </div>
      </div>

      {error && (
        <p role="alert" className="rounded-md bg-red-50 p-3 text-sm text-red-700">
          {error}
        </p>
      )}

      {doctors === null && !error && <LoadingState variant="list" rows={3} />}

      {doctors && doctors.length === 0 && (
        <EmptyState message={searchTerm ? 'No doctors match your search.' : 'No doctors staffed at this clinic yet.'} />
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
                        {notBookingReadyIds.has(doctor.doctorProfileId) && (
                          <span
                            title="No appointment type or default fee configured yet - patients can't book this doctor until this is set up."
                            className="inline-flex items-center gap-1 rounded-full bg-amber-50 px-2 py-0.5 text-xs font-medium text-amber-800"
                          >
                            <svg aria-hidden="true" viewBox="0 0 20 20" className="h-3 w-3 shrink-0" fill="currentColor">
                              <path
                                fillRule="evenodd"
                                clipRule="evenodd"
                                d="M8.257 3.099c.765-1.36 2.72-1.36 3.486 0l6.28 11.18c.75 1.334-.213 2.987-1.743 2.987H3.72c-1.53 0-2.493-1.653-1.743-2.987l6.28-11.18zM10 7a.75.75 0 01.75.75v3a.75.75 0 01-1.5 0v-3A.75.75 0 0110 7zm0 8a1 1 0 100-2 1 1 0 000 2z"
                              />
                            </svg>
                            Booking setup incomplete
                          </span>
                        )}
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
