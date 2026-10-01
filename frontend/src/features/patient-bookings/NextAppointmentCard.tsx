import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { listMyBookings, type PatientBookingSummary } from './api'
import { selectNextVisit } from './visitOutcome'
import { loadPatientSession } from '../patient-account/token'
import { BookingIcon } from '../../components/patientIcons'

// A single patient's own booking history is personal-scale (not the platform-wide "hundreds to
// thousands" the admin verification queues have to handle) - fetching one page this size and
// picking the soonest upcoming booking client-side is safe. 069-patient-visit-outcomes: "upcoming"
// comes from the server's own visitOutcome (live-audit finding 2), never booking state or the
// browser's date alone - a no-show today is no longer "Your next visit".
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

// patient-dashboard-entry-page: the "your next appointment" highlight - a welcoming home surfaces
// what's actually coming up, not just a menu of places to go. Renders nothing when there is no
// upcoming booking, so a new patient's dashboard stays uncluttered.
export function NextAppointmentCard() {
  const [session] = useState(() => loadPatientSession())
  const [next, setNext] = useState<PatientBookingSummary | null | undefined>(undefined)

  useEffect(() => {
    if (!session) return
    listMyBookings(session.token, { size: LOOKAHEAD_PAGE_SIZE })
      .then((result) => setNext(selectNextVisit(result.bookings)))
      .catch(() => setNext(null))
  }, [session])

  if (!session || next === undefined) {
    return <div aria-hidden="true" className="h-[92px] animate-pulse rounded-2xl bg-gray-100" />
  }

  if (next === null) return null

  return (
    // 056-design-copy-quality-pass (2026-09-16 dashboard rebuild): restyled to match
    // design/patient-dashboard-reference.html's bold teal "next visit" strip - the
    // above conditional logic (fetch, find-soonest-upcoming, render-nothing-if-none) is
    // unchanged, already exactly what that reference's spec calls for.
    <div className="mb-10 flex flex-wrap items-center justify-between gap-5 rounded-2xl bg-indigo-600 p-6 text-white">
      <div className="flex items-center gap-4">
        <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-white/15">
          <BookingIcon />
        </span>
        <div className="min-w-0">
          <p className="text-sm text-indigo-100">Your next visit</p>
          <p className="truncate text-base font-semibold">
            {next.doctorName} · {next.clinicName}
          </p>
          <p className="text-sm text-indigo-100 tabular-nums">
            {describeRelativeDate(next.sessionDate)}
            {next.mode === 'FIXED_TIME' && next.startTime ? `, ${formatTime(next.startTime)}` : ''}
            {next.mode === 'QUEUE' && next.tokenNumber !== null ? `, Token ${next.tokenNumber}` : ''}
          </p>
        </div>
      </div>
      <Link
        to={`/patient/bookings/${next.id}`}
        className="shrink-0 rounded-lg bg-white px-5 py-2.5 text-sm font-semibold text-indigo-700 transition-opacity duration-150 hover:opacity-90 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white focus-visible:ring-offset-2 focus-visible:ring-offset-indigo-600"
      >
        View details
      </Link>
    </div>
  )
}
