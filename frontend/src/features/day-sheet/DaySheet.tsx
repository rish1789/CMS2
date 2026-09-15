import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { listSessions, type DoctorSummary, type SessionSummary } from './api'
import { loadStaffSession } from '../staff-login/token'
import { ListSkeleton } from '../../components/ListSkeleton'
import { avatarGradientClass } from '../../components/avatarGradient'

const SESSIONS_PAGE_SIZE = 20

function formatSessionDate(isoDate: string): string {
  const parsed = new Date(`${isoDate}T00:00:00`)
  if (Number.isNaN(parsed.getTime())) return isoDate
  return parsed.toLocaleDateString(undefined, { weekday: 'short', day: 'numeric', month: 'short' })
}

// staff-console-audit-2026-09-10 P1: today's local date, computed once per render - used to
// mark today's row so it doesn't look identical to one two weeks out.
function todayIsoDate(): string {
  const now = new Date()
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const day = String(now.getDate()).padStart(2, '0')
  return `${now.getFullYear()}-${month}-${day}`
}

// Defensive against a stale backend response (e.g. a frontend deploy that lands before the
// backend restart adding these fields) - degrades to a blank cell instead of crashing the whole
// page, which is exactly what happened when this was written as `startTime.slice(...)` with no
// guard and hit a real backend that hadn't picked up the new fields yet.
function formatSessionTimeRange(startTime: string | null | undefined, endTime: string | null | undefined): string {
  if (!startTime || !endTime) return ''
  return `${startTime.slice(0, 5)}–${endTime.slice(0, 5)}`
}

function modeBadgeClass(mode: SessionSummary['mode']): string {
  return mode === 'FIXED_TIME' ? 'bg-indigo-100 text-indigo-700' : 'bg-gray-100 text-gray-700'
}

export function DaySheet() {
  const { clinicId } = useParams<{ clinicId: string }>()
  const [sessions, setSessions] = useState<SessionSummary[] | null>(null)
  const [doctors, setDoctors] = useState<DoctorSummary[]>([])
  const [totalCount, setTotalCount] = useState(0)
  const [pageSize, setPageSize] = useState(SESSIONS_PAGE_SIZE)
  const [error, setError] = useState<string | null>(null)
  const [page, setPage] = useState(0)
  const [doctorFilter, setDoctorFilter] = useState('All')
  const [doctorSearchText, setDoctorSearchText] = useState('')

  useEffect(() => {
    if (!clinicId) return
    const session = loadStaffSession()
    if (!session) return
    setSessions(null)
    listSessions(clinicId, session.token, {
      page,
      size: SESSIONS_PAGE_SIZE,
      doctorProfileId: doctorFilter === 'All' ? undefined : doctorFilter,
    })
      .then((result) => {
        setSessions(result.sessions)
        setDoctors(result.doctors)
        setTotalCount(result.totalCount)
        setPageSize(result.pageSize)
      })
      .catch(() => setError('Failed to load sessions.'))
  }, [clinicId, page, doctorFilter])

  if (!clinicId) return null

  const totalPages = Math.max(1, Math.ceil(totalCount / pageSize))

  // When every session on this page belongs to the same doctor - either because the doctor
  // filter is active, or just incidentally (a clinic with one scheduled doctor) - repeating
  // their name/avatar on every single row is pure noise. Show it once, as a tab merged into
  // the table's own top-left corner, and drop the per-row Doctor column entirely. Falls back
  // to the Doctor column whenever the page genuinely mixes more than one doctor.
  const distinctDoctorIds = new Set((sessions ?? []).map((s) => s.doctorProfileId))
  const singleDoctor =
    sessions && sessions.length > 0 && distinctDoctorIds.size === 1
      ? doctors.find((doctor) => doctor.doctorProfileId === sessions[0].doctorProfileId)
      : null

  // A plain <select> doesn't scale to a clinic with many doctors - scrolling through a flat
  // list of 50-100 names is unwieldy. A text input + <datalist> gives native browser type-to-
  // filter with zero extra JS/dependency, and works exactly as well at small counts too, so
  // there's no separate "small clinic" code path to maintain. Matches by name OR Staff ID
  // (staffCode) - mirrors the Roster page's own "search by name or staff code" precedent.
  function doctorSearchLabel(doctor: DoctorSummary): string {
    return `${doctor.name} — ${doctor.staffCode}`
  }

  function findDoctorByExactSearchText(text: string): DoctorSummary | undefined {
    const normalized = text.trim().toLowerCase()
    return doctors.find(
      (doctor) =>
        doctor.name.toLowerCase() === normalized ||
        doctor.staffCode.toLowerCase() === normalized ||
        doctorSearchLabel(doctor).toLowerCase() === normalized,
    )
  }

  function handleDoctorSearchChange(text: string) {
    setDoctorSearchText(text)
    if (text.trim() === '') {
      setDoctorFilter('All')
      setPage(0)
      return
    }
    const match = findDoctorByExactSearchText(text)
    if (match) {
      setDoctorFilter(match.doctorProfileId)
      setPage(0)
    }
    // Otherwise: a partial, not-yet-resolved name/code - don't re-fetch on every keystroke,
    // wait for either an exact match (above) or blur (below) to settle.
  }

  function handleDoctorSearchBlur() {
    if (doctorFilter === 'All') {
      setDoctorSearchText('')
      return
    }
    const current = doctors.find((doctor) => doctor.doctorProfileId === doctorFilter)
    setDoctorSearchText(current ? doctorSearchLabel(current) : '')
  }

  return (
    <div className="space-y-4">
      <div>
        <h1 className="text-lg font-semibold text-gray-900">Day sheet</h1>
        <p className="mt-0.5 text-sm text-gray-500">Sessions for the next 14 days.</p>
      </div>

      {error && (
        <p role="alert" className="rounded-md bg-red-50 p-3 text-sm text-red-700">
          {error}
        </p>
      )}

      {doctors.length > 0 && (
        <div className="max-w-xs">
          <label htmlFor="doctor-search" className="sr-only">
            Filter by doctor
          </label>
          <input
            id="doctor-search"
            type="text"
            list="doctor-search-options"
            value={doctorSearchText}
            onChange={(event) => handleDoctorSearchChange(event.target.value)}
            onBlur={handleDoctorSearchBlur}
            placeholder="All doctors"
            aria-label="Filter by doctor"
            className="w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm text-gray-700 placeholder:text-gray-400 focus:border-indigo-400 focus:outline-none focus:ring-2 focus:ring-indigo-500/30"
          />
          <datalist id="doctor-search-options">
            {doctors.map((doctor) => (
              <option key={doctor.doctorProfileId} value={doctorSearchLabel(doctor)} />
            ))}
          </datalist>
        </div>
      )}

      {sessions === null && !error && <ListSkeleton rows={4} />}

      {sessions && sessions.length === 0 && (
        <p className="rounded-xl border border-gray-200 bg-white p-6 text-sm text-gray-500 shadow-sm">
          No sessions scheduled in the next 14 days.
        </p>
      )}

      {sessions && sessions.length > 0 && (
        <>
          {singleDoctor && (
            <div className="inline-flex w-fit items-center gap-2 rounded-t-lg border border-b-0 border-gray-200 bg-gray-50 px-3 py-1.5">
              <span
                aria-hidden="true"
                className={`flex h-6 w-6 shrink-0 items-center justify-center rounded-md bg-gradient-to-br text-[10px] font-semibold text-white ${avatarGradientClass(singleDoctor.name)}`}
              >
                {singleDoctor.name.charAt(0).toUpperCase()}
              </span>
              <span className="text-xs font-medium text-gray-500">{singleDoctor.staffCode}</span>
              <span className="text-sm font-semibold text-gray-900">{singleDoctor.name}</span>
            </div>
          )}
          <div
            className={`overflow-x-auto rounded-xl border border-gray-200 bg-white shadow-sm ${singleDoctor ? '-mt-px rounded-tl-none' : ''}`}
          >
            <table className="w-full min-w-[560px] text-left text-sm">
              <thead>
                <tr className="border-b border-gray-200 bg-gray-50 text-xs font-semibold uppercase tracking-wide text-gray-500">
                  {!singleDoctor && (
                    <th scope="col" className="px-4 py-3 font-semibold">
                      Doctor
                    </th>
                  )}
                  <th scope="col" className="px-4 py-3 font-semibold">
                    Date
                  </th>
                  <th scope="col" className="px-4 py-3 font-semibold">
                    Time
                  </th>
                  <th scope="col" className="px-4 py-3 font-semibold">
                    Mode
                  </th>
                  <th scope="col" className="px-4 py-3 font-semibold">
                    Booked
                  </th>
                  <th scope="col" className="px-4 py-3 font-semibold">
                    <span className="sr-only">Open</span>
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {sessions.map((s) => {
                  const isToday = s.sessionDate === todayIsoDate()
                  return (
                    // staff-console-audit-2026-09-10 P1: `relative` here plus `after:absolute
                    // after:inset-0` on the trailing arrow link below is the "stretched link"
                    // technique - the whole row becomes the click target of that ONE real
                    // anchor (proper keyboard/middle-click/screen-reader behavior), instead of
                    // `hover:bg-gray-50` promising a clickable row that only a 34x24px arrow
                    // actually was.
                    <tr key={s.sessionId} className="relative transition-colors duration-150 hover:bg-gray-50">
                      {!singleDoctor && (
                        <td className="px-4 py-3">
                          <Link
                            to={`/staff/clinics/${clinicId}/day-sheet/${s.sessionId}`}
                            className="relative z-10 flex items-center gap-3 rounded-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-400"
                          >
                            <span
                              aria-hidden="true"
                              className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-gradient-to-br text-xs font-semibold text-white shadow-sm ${avatarGradientClass(s.doctorName)}`}
                            >
                              {s.doctorName.charAt(0).toUpperCase()}
                            </span>
                            <span className="font-medium text-gray-900">{s.doctorName}</span>
                          </Link>
                        </td>
                      )}
                      <td className="px-4 py-3 text-gray-600">
                        <span className="inline-flex items-center gap-2">
                          {formatSessionDate(s.sessionDate)}
                          {isToday && (
                            <span className="rounded-full bg-indigo-50 px-2 py-0.5 text-[11px] font-semibold text-indigo-600">
                              Today
                            </span>
                          )}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-gray-600 tabular-nums">
                        {formatSessionTimeRange(s.startTime, s.endTime)}
                      </td>
                      <td className="px-4 py-3">
                        <span className={`rounded-full px-2.5 py-1 text-xs font-semibold ${modeBadgeClass(s.mode)}`}>
                          {s.mode === 'FIXED_TIME' ? 'Fixed-Time' : 'Queue'}
                        </span>
                      </td>
                      <td className="px-4 py-3">
                        {s.totalSlotCount === 0 ? (
                          <span className="text-xs text-gray-400">No slots yet</span>
                        ) : (
                          <div className="flex items-center gap-1.5 tabular-nums">
                            <div className="h-1 w-8 shrink-0 overflow-hidden rounded-full bg-gray-100">
                              <div
                                className="h-full rounded-full bg-indigo-500"
                                style={{ width: `${Math.round((s.bookedSlotCount / s.totalSlotCount) * 100)}%` }}
                              />
                            </div>
                            <span className="text-xs text-gray-500">
                              {s.bookedSlotCount}/{s.totalSlotCount}
                            </span>
                          </div>
                        )}
                      </td>
                      <td className="px-4 py-3 text-right">
                        <Link
                          to={`/staff/clinics/${clinicId}/day-sheet/${s.sessionId}`}
                          aria-label={`Open ${s.doctorName}'s session on ${formatSessionDate(s.sessionDate)}`}
                          className="text-gray-300 transition-colors duration-150 hover:text-indigo-500 after:absolute after:inset-0"
                        >
                          →
                        </Link>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
          <div className="flex items-center justify-between text-sm text-gray-500">
            <p>
              Page {page + 1} of {totalPages} ({totalCount} sessions)
            </p>
            <div className="flex gap-2">
              <button
                type="button"
                onClick={() => setPage((current) => Math.max(0, current - 1))}
                disabled={page === 0}
                className="rounded-lg border border-gray-300 px-3 py-1.5 font-medium text-gray-700 transition-colors duration-150 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-40"
              >
                Previous
              </button>
              <button
                type="button"
                onClick={() => setPage((current) => Math.min(totalPages - 1, current + 1))}
                disabled={page + 1 >= totalPages}
                className="rounded-lg border border-gray-300 px-3 py-1.5 font-medium text-gray-700 transition-colors duration-150 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-40"
              >
                Next
              </button>
            </div>
          </div>
        </>
      )}
    </div>
  )
}
