import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { listSessions, type DoctorSummary, type SessionSummary } from './api'
import { listClinicDoctors } from '../doctor-picker/api'
import { loadStaffSession } from '../staff-login/token'
import { avatarGradientClass } from '../../components/avatarGradient'
import { PaginationControls } from '../../components/PaginationControls'
import { EmptyState } from '../../components/EmptyState'
import { LoadingState } from '../../components/LoadingState'

const SESSIONS_PAGE_SIZE = 20
const DOCTOR_SEARCH_DEBOUNCE_MS = 300
// real-bug-fix 2026-09-17: covers a typical clinic's full roster in one request without
// building out a paginated combobox - listClinicDoctors' own `q` param (wired in below) is
// what actually keeps this correct if a clinic ever exceeds it, not raising this number.
const ALL_DOCTORS_PAGE_SIZE = 100

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
  // real-bug-fix 2026-09-17: previously sourced from listSessions' own `doctors` field, which
  // SessionRepository.findDistinctDoctorsInWindow derives from doctors who already have a
  // generated Session in the 14-day window - a newly-onboarded or just-edited doctor's schedule
  // has none yet (session generation only runs nightly, or on manual trigger), so they were
  // invisible in this search bar with no indication anything was wrong, purely a data-timing
  // gap unrelated to whether they're actually staffed. This now comes from listClinicDoctors -
  // every doctor staffed at the clinic, independent of session generation.
  const [allDoctors, setAllDoctors] = useState<DoctorSummary[]>([])
  // The current page's own doctors (from listSessions' response) - kept separately from
  // allDoctors purely for the merged single-doctor tab badge below, which needs a staffCode
  // that's guaranteed present the moment a Session exists (unlike SessionSummary itself, which
  // only carries doctorName/doctorProfileId) - no session-generation timing gap applies here,
  // since a page of Sessions already implies its doctor has at least one generated.
  const [sessionDoctors, setSessionDoctors] = useState<DoctorSummary[]>([])
  const [totalCount, setTotalCount] = useState(0)
  const [pageSize, setPageSize] = useState(SESSIONS_PAGE_SIZE)
  const [error, setError] = useState<string | null>(null)
  const [page, setPage] = useState(0)
  const [doctorFilter, setDoctorFilter] = useState('All')
  const [doctorSearchText, setDoctorSearchText] = useState('')
  const [doctorSearchInput, setDoctorSearchInput] = useState('')

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
        setSessionDoctors(result.doctors)
        setTotalCount(result.totalCount)
        setPageSize(result.pageSize)
      })
      .catch(() => setError('Failed to load sessions.'))
  }, [clinicId, page, doctorFilter])

  // The clinic's full doctor roster, loaded once independent of session data - a search-bar
  // option list that depends on session generation having already run is exactly the bug above.
  useEffect(() => {
    if (!clinicId) return
    const session = loadStaffSession()
    if (!session) return
    listClinicDoctors(clinicId, session.token, { size: ALL_DOCTORS_PAGE_SIZE })
      .then((result) => setAllDoctors(result.doctors))
      .catch(() => {
        // Non-fatal: the day sheet itself still works, only the search bar's suggestion list
        // stays empty - the sessions fetch above surfaces its own error banner already.
      })
  }, [clinicId])

  // real-bug-fix 2026-09-17: "more power" for the search bar - a clinic with more doctors than
  // ALL_DOCTORS_PAGE_SIZE (or a doctor onboarded after that initial fetch) still needs to be
  // findable by typing. Debounced server-side search merges hits into the known set instead of
  // replacing it, so results already shown never disappear mid-search. Mirrors PatientSearch/
  // StaffPicker's own identical 300ms debounce precedent.
  useEffect(() => {
    if (!clinicId) return
    const term = doctorSearchInput.trim()
    if (term.length < 2) return
    const timer = setTimeout(() => {
      const session = loadStaffSession()
      if (!session) return
      listClinicDoctors(clinicId, session.token, { q: term, size: ALL_DOCTORS_PAGE_SIZE })
        .then((result) => {
          setAllDoctors((prev) => {
            const byId = new Map(prev.map((doctor) => [doctor.doctorProfileId, doctor]))
            for (const doctor of result.doctors) byId.set(doctor.doctorProfileId, doctor)
            return Array.from(byId.values())
          })
        })
        .catch(() => {
          // Non-fatal - the locally-known doctor list (if any match) stays usable.
        })
    }, DOCTOR_SEARCH_DEBOUNCE_MS)
    return () => clearTimeout(timer)
  }, [clinicId, doctorSearchInput])

  if (!clinicId) return null

  // When every session on this page belongs to the same doctor - either because the doctor
  // filter is active, or just incidentally (a clinic with one scheduled doctor) - repeating
  // their name/avatar on every single row is pure noise. Show it once, as a tab merged into
  // the table's own top-left corner, and drop the per-row Doctor column entirely. Falls back
  // to the Doctor column whenever the page genuinely mixes more than one doctor.
  const distinctDoctorIds = new Set((sessions ?? []).map((s) => s.doctorProfileId))
  const singleDoctor =
    sessions && sessions.length > 0 && distinctDoctorIds.size === 1
      ? sessionDoctors.find((doctor) => doctor.doctorProfileId === sessions[0].doctorProfileId)
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
    return allDoctors.find(
      (doctor) =>
        doctor.name.toLowerCase() === normalized ||
        doctor.staffCode.toLowerCase() === normalized ||
        doctorSearchLabel(doctor).toLowerCase() === normalized,
    )
  }

  function handleDoctorSearchChange(text: string) {
    setDoctorSearchText(text)
    setDoctorSearchInput(text)
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
    const current = allDoctors.find((doctor) => doctor.doctorProfileId === doctorFilter)
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

      {allDoctors.length > 0 && (
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
            {allDoctors.map((doctor) => (
              <option key={doctor.doctorProfileId} value={doctorSearchLabel(doctor)} />
            ))}
          </datalist>
        </div>
      )}

      {sessions === null && !error && <LoadingState variant="list" rows={4} />}

      {sessions && sessions.length === 0 && (
        <EmptyState message="No sessions scheduled in the next 14 days." />
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
          {/* 055-responsive-mobile-pass: `contain-layout` fixes a real, verified tablet-width
              (~768px) bug - this table's own `min-w-[560px]` was leaking past this wrapper's
              `overflow-x-auto` into the page's own scrollWidth (a known browser quirk with
              table intrinsic sizing inside flex layouts), causing genuine page-body horizontal
              scroll even though the wrapper visually clipped/scrolled the table correctly.
              `contain: layout` isolates the wrapper as a containment boundary without touching
              the table's own column-sizing algorithm - confirmed zero effect on column widths
              at desktop width. */}
          <div
            className={`overflow-x-auto contain-layout rounded-xl border border-gray-200 bg-white shadow-sm ${singleDoctor ? '-mt-px rounded-tl-none' : ''}`}
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
                        <span className="inline-flex items-center gap-1.5">
                          <span className={`rounded-full px-2.5 py-1 text-xs font-semibold ${modeBadgeClass(s.mode)}`}>
                            {s.mode === 'FIXED_TIME' ? 'Fixed-Time' : 'Queue'}
                          </span>
                          {/* 065-phase1-stabilization (owner decision 3): a whole-cancelled session. */}
                          {s.cancelled && (
                            <span className="rounded-full bg-white px-2.5 py-1 text-xs font-semibold text-gray-600 ring-1 ring-inset ring-gray-300">
                              Cancelled
                            </span>
                          )}
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
          <PaginationControls
            page={page}
            pageSize={pageSize}
            totalCount={totalCount}
            onPageChange={setPage}
            itemLabel="sessions"
          />
        </>
      )}
    </div>
  )
}
