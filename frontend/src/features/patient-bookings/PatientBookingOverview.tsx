import { useCallback, useEffect, useState } from 'react'
import { getMyBooking, type PatientBookingSummary } from './api'
import { CANCELLATION_REFUSAL_TEXT, VISIT_OUTCOME_LABEL, visitOutcomeBadgeClass } from './visitOutcome'
import { loadPatientSession } from '../patient-account/token'
import { CancelBookingButton } from '../booking-cancellation/CancelBookingButton'

// "10:45:00" -> "10:45" - the seconds are never meaningful to a patient.
function formatTime(time: string): string {
  return time.slice(0, 5)
}

function formatDate(iso: string): string {
  return new Date(`${iso}T00:00:00`).toLocaleDateString(undefined, { weekday: 'short', month: 'short', day: 'numeric', year: 'numeric' })
}

// 069-patient-visit-outcomes (live-audit findings 1 and 3): the booking detail's header - the
// patient's own visit outcome, and cancellation offered only when the server says it can succeed
// (otherwise the reason is explained). The server re-checks on submit, so a stale "allowed" still
// gets the real refusal shown by CancelBookingButton.
export function PatientBookingOverview({ bookingId }: { bookingId: string }) {
  const [session] = useState(() => loadPatientSession())
  const [booking, setBooking] = useState<PatientBookingSummary | null>(null)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(() => {
    if (!session) return
    getMyBooking(bookingId, session.token)
      .then((result) => {
        setBooking(result)
        setError(null)
      })
      .catch(() => setError('Could not load this booking.'))
  }, [bookingId, session])

  useEffect(load, [load])

  if (error) {
    return (
      <p role="alert" className="rounded-md bg-red-50 p-3 text-sm text-red-700">
        {error}
      </p>
    )
  }

  if (!booking) {
    return <div aria-hidden="true" className="h-[120px] animate-pulse rounded-xl bg-gray-100" />
  }

  return (
    <section aria-label="Booking" className="space-y-4 rounded-xl border border-gray-200 bg-white p-5 shadow-sm">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="truncate font-semibold text-gray-900">{booking.doctorName}</p>
          <p className="truncate text-sm text-gray-600">
            {booking.clinicName} · {booking.appointmentTypeName}
          </p>
          <p className="mt-0.5 text-sm text-gray-500 tabular-nums">
            {formatDate(booking.sessionDate)}
            {booking.mode === 'FIXED_TIME' && booking.startTime ? ` · ${formatTime(booking.startTime)}` : ''}
            {booking.mode === 'QUEUE' && booking.tokenNumber !== null ? ` · Token ${booking.tokenNumber}` : ''}
          </p>
        </div>
        <span className={`shrink-0 rounded-full px-2 py-0.5 text-xs font-medium ${visitOutcomeBadgeClass(booking.visitOutcome)}`}>
          {VISIT_OUTCOME_LABEL[booking.visitOutcome]}
        </span>
      </div>

      {booking.cancellation.allowed ? (
        <CancelBookingButton mode="patient" bookingId={booking.id} onCancelled={load} />
      ) : (
        booking.cancellation.reason && (
          <p className="text-sm text-gray-600">{CANCELLATION_REFUSAL_TEXT[booking.cancellation.reason]}</p>
        )
      )}
    </section>
  )
}
