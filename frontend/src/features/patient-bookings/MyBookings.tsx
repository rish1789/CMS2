import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { listMyBookings, type PatientBookingSummary } from './api'
import { loadPatientSession } from '../patient-account/token'
import { todayIsoDate } from '../patient-booking/DateStrip'
import { ListSkeleton } from '../../components/ListSkeleton'
import { PaginationControls } from '../../components/PaginationControls'
import { IconBadge } from '../../components/adminIcons'
import { BookingIcon } from '../../components/patientIcons'

const BOOKINGS_PAGE_SIZE = 20

function statusBadgeClass(status: PatientBookingSummary['status']): string {
  return status === 'ACTIVE' ? 'bg-green-50 text-green-700' : 'bg-gray-100 text-gray-600'
}

// "10:45:00" -> "10:45" - the seconds are never meaningful to a patient.
function formatTime(time: string): string {
  return time.slice(0, 5)
}

// Mirrors QueueSessionList's/NextAppointmentCard's identical Today/Tomorrow relative labeling -
// a raw "2026-09-14" reads like an unfinished data dump next to a nicely formatted time.
function formatBookingDate(iso: string): string {
  const today = todayIsoDate()
  if (iso === today) return 'Today'
  const tomorrow = new Date(`${today}T00:00:00`)
  tomorrow.setDate(tomorrow.getDate() + 1)
  const tomorrowIso = `${tomorrow.getFullYear()}-${String(tomorrow.getMonth() + 1).padStart(2, '0')}-${String(tomorrow.getDate()).padStart(2, '0')}`
  if (iso === tomorrowIso) return 'Tomorrow'
  return new Date(`${iso}T00:00:00`).toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' })
}

// patient-booking-flow-rebuild: replaces the dashboard's "type a Booking ID" entry point.
export function MyBookings() {
  const [session] = useState(() => loadPatientSession())
  const [bookings, setBookings] = useState<PatientBookingSummary[] | null>(null)
  const [totalCount, setTotalCount] = useState(0)
  const [page, setPage] = useState(0)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!session) return
    setBookings(null)
    listMyBookings(session.token, { page, size: BOOKINGS_PAGE_SIZE })
      .then((result) => {
        setBookings(result.bookings)
        setTotalCount(result.totalCount)
      })
      .catch(() => setError('Failed to load your bookings.'))
  }, [page, session])

  if (!session) {
    return (
      <div className="max-w-md rounded-xl border border-gray-200 bg-white p-6 shadow-sm">
        <p className="text-sm text-gray-600">Please sign in to view your bookings.</p>
      </div>
    )
  }

  return (
    <div className="space-y-4">
      {error && (
        <p role="alert" className="rounded-md bg-red-50 p-3 text-sm text-red-700">
          {error}
        </p>
      )}

      {bookings === null && !error && <ListSkeleton rows={3} />}

      {bookings && bookings.length === 0 && (
        <div className="rounded-xl border border-gray-200 bg-white p-6 text-sm text-gray-500 shadow-sm">
          <p>You don't have any bookings yet.</p>
          <Link
            to="/discover"
            className="mt-2 inline-block font-medium text-indigo-600 transition-colors duration-150 hover:text-indigo-700 hover:underline"
          >
            Find a doctor
          </Link>
        </div>
      )}

      {bookings && bookings.length > 0 && (
        <>
          <ul className="space-y-3">
            {bookings.map((booking) => (
              <li key={booking.id}>
                <Link
                  to={`/patient/bookings/${booking.id}`}
                  className="flex items-center gap-3 rounded-xl border border-gray-200 bg-white p-4 shadow-sm transition-all duration-200 ease-out hover:-translate-y-0.5 hover:border-indigo-300 hover:shadow-md active:scale-[0.99] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-2"
                >
                  <IconBadge>
                    <BookingIcon />
                  </IconBadge>
                  <div className="min-w-0 flex-1">
                    <p className="truncate font-medium text-gray-900">{booking.doctorName}</p>
                    <p className="truncate text-sm text-gray-600">
                      {booking.clinicName} · {booking.appointmentTypeName}
                    </p>
                    <p className="mt-0.5 text-sm text-gray-500 tabular-nums">
                      {formatBookingDate(booking.sessionDate)}
                      {booking.mode === 'FIXED_TIME' && booking.startTime ? ` · ${formatTime(booking.startTime)}` : ''}
                      {booking.mode === 'QUEUE' && booking.tokenNumber !== null ? ` · Token ${booking.tokenNumber}` : ''}
                    </p>
                  </div>
                  <span className={`shrink-0 rounded-full px-2 py-0.5 text-xs font-medium ${statusBadgeClass(booking.status)}`}>
                    {booking.status === 'ACTIVE' ? 'Active' : 'Cancelled'}
                  </span>
                </Link>
              </li>
            ))}
          </ul>
          <PaginationControls page={page} pageSize={BOOKINGS_PAGE_SIZE} totalCount={totalCount} onPageChange={setPage} itemLabel="bookings" />
        </>
      )}
    </div>
  )
}
