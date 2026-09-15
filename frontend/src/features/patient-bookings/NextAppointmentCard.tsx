import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { listMyBookings, type PatientBookingSummary } from './api'
import { loadPatientSession } from '../patient-account/token'
import { BookingIcon } from '../../components/patientIcons'

// A single patient's own booking history is personal-scale (not the platform-wide "hundreds to
// thousands" the admin verification queues have to handle) - fetching one page this size and
// picking the soonest upcoming ACTIVE booking client-side is safe, and avoids a second
// date-aware sort on the backend just for this one homepage widget.
const LOOKAHEAD_PAGE_SIZE = 50

function todayIsoDate(): string {
  const now = new Date()
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const day = String(now.getDate()).padStart(2, '0')
  return `${now.getFullYear()}-${month}-${day}`
}

function describeRelativeDate(sessionDate: string): string {
  const today = new Date(todayIsoDate())
  const target = new Date(sessionDate)
  const diffDays = Math.round((target.getTime() - today.getTime()) / (1000 * 60 * 60 * 24))
  if (diffDays === 0) return 'Today'
  if (diffDays === 1) return 'Tomorrow'
  // Anything further out gets a real formatted date - a raw "2026-09-20" reads like an
  // unfinished data dump next to "Today"/"Tomorrow".
  return target.toLocaleDateString(undefined, { month: 'short', day: 'numeric' })
}

// "10:15:00" -> "10:15" - the seconds are never meaningful to a patient.
function formatTime(time: string): string {
  return time.slice(0, 5)
}

function findNextUpcoming(bookings: PatientBookingSummary[]): PatientBookingSummary | null {
  const today = todayIsoDate()
  const upcoming = bookings.filter((b) => b.status === 'ACTIVE' && b.sessionDate >= today)
  upcoming.sort((a, b) => {
    if (a.sessionDate !== b.sessionDate) return a.sessionDate < b.sessionDate ? -1 : 1
    return (a.startTime ?? '') < (b.startTime ?? '') ? -1 : 1
  })
  return upcoming[0] ?? null
}

// patient-dashboard-entry-page: the "your next appointment" highlight - a welcoming home surfaces
// what's actually coming up, not just a menu of places to go. Renders nothing when there is no
// upcoming booking, so a new patient's dashboard stays uncluttered.
export function NextAppointmentCard() {
  const [session] = useState(() => loadPatientSession())
  const [next, setNext] = useState<PatientBookingSummary | null | undefined>(undefined)

  useEffect(() => {
    if (!session) return
    listMyBookings(session.token, { size: LOOKAHEAD_PAGE_SIZE })
      .then((result) => setNext(findNextUpcoming(result.bookings)))
      .catch(() => setNext(null))
  }, [session])

  if (!session || next === undefined) {
    return (
      <div aria-hidden="true" className="h-24 animate-pulse rounded-lg border border-gray-200 bg-white shadow-sm" />
    )
  }

  if (next === null) return null

  return (
    <Link
      to={`/patient/bookings/${next.id}`}
      className="flex items-center gap-4 rounded-lg border border-indigo-200 bg-indigo-50 p-4 shadow-sm transition-all duration-150 ease-out hover:border-indigo-300 hover:shadow-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-2"
    >
      <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-white text-indigo-600 shadow-xs">
        <BookingIcon />
      </span>
      <div className="min-w-0 flex-1">
        <h2 className="flex items-center gap-2 text-sm font-semibold text-indigo-900">
          Your next appointment
          <span className="rounded-full bg-indigo-600 px-2 py-0.5 text-xs font-semibold text-white">
            {describeRelativeDate(next.sessionDate)}
          </span>
        </h2>
        <p className="mt-0.5 truncate text-sm text-indigo-700 tabular-nums">
          {next.doctorName} · {next.clinicName}
          {next.mode === 'FIXED_TIME' && next.startTime ? ` · ${formatTime(next.startTime)}` : ''}
          {next.mode === 'QUEUE' && next.tokenNumber !== null ? ` · Token ${next.tokenNumber}` : ''}
        </p>
      </div>
      <span aria-hidden="true" className="shrink-0 text-indigo-600">
        →
      </span>
    </Link>
  )
}
