import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { getBookingDetail, type BookingDetail } from './api'
import { loadStaffSession } from '../staff-login/token'

export interface BookingContextHeaderProps {
  clinicId: string
  bookingId: string
}

function formatSessionDate(isoDate: string): string {
  const parsed = new Date(`${isoDate}T00:00:00`)
  if (Number.isNaN(parsed.getTime())) return isoDate
  return parsed.toLocaleDateString(undefined, { weekday: 'short', day: 'numeric', month: 'short' })
}

// staff-console-audit-2026-09-10 P1: every booking-scoped tool page ("Mark complete", "Cancel
// booking", consultation note, prescription, external record) previously rendered with no
// heading at all - a receptionist mid-task had no way to confirm which patient/doctor/session
// they were acting on without going back. Shared here so the fix (and its loading/error states)
// lives in one place instead of five near-duplicate fetches.
export function BookingContextHeader({ clinicId, bookingId }: BookingContextHeaderProps) {
  const [detail, setDetail] = useState<BookingDetail | null>(null)
  const [failed, setFailed] = useState(false)

  useEffect(() => {
    const session = loadStaffSession()
    if (!session) return
    getBookingDetail(clinicId, bookingId, session.token)
      .then(setDetail)
      .catch(() => setFailed(true))
  }, [clinicId, bookingId])

  // Silent on failure - the page's own form already surfaces a "could not be found" error for
  // the same booking id; a second, redundant error banner here would just add noise.
  if (failed) return null

  if (!detail) {
    return (
      <output className="block mx-auto max-w-md space-y-2">
        <span className="sr-only">Loading session details…</span>
        <div aria-hidden="true" className="h-4 w-40 animate-pulse rounded bg-gray-100" />
        <div aria-hidden="true" className="h-6 w-48 animate-pulse rounded bg-gray-100" />
        <div aria-hidden="true" className="h-4 w-64 animate-pulse rounded bg-gray-100" />
      </output>
    )
  }

  return (
    <div className="mx-auto max-w-md">
      <Link
        to={`/staff/clinics/${clinicId}/day-sheet/${detail.sessionId}`}
        className="inline-flex items-center gap-1 text-sm font-medium text-indigo-600 transition-colors duration-150 hover:text-indigo-700"
      >
        ← Back to {detail.doctorName}&apos;s session
      </Link>
      <h1 className="mt-2 text-lg font-semibold text-gray-900">{detail.patientName}</h1>
      <p className="mt-0.5 text-sm text-gray-500">
        {detail.appointmentTypeName} · {detail.doctorName} · {formatSessionDate(detail.sessionDate)}
      </p>
    </div>
  )
}
