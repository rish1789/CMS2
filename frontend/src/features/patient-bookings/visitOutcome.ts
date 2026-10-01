import type { CancellationRefusal, PatientBookingSummary, VisitOutcome } from './api'

// 069-patient-visit-outcomes (spec "Decision table"): one vocabulary for the patient's own visit
// outcome across My bookings, the booking detail and the dashboard.
export const VISIT_OUTCOME_LABEL: Record<VisitOutcome, string> = {
  SCHEDULED: 'Upcoming',
  CHECKED_IN: 'Checked in',
  COMPLETED: 'Visit complete',
  NO_SHOW: 'Missed appointment',
  CANCELLED: 'Cancelled',
  NOT_RECORDED: 'Outcome not recorded',
}

export function visitOutcomeBadgeClass(outcome: VisitOutcome): string {
  switch (outcome) {
    case 'SCHEDULED':
    case 'CHECKED_IN':
      return 'bg-green-50 text-green-700'
    case 'COMPLETED':
      return 'bg-indigo-50 text-indigo-700'
    case 'NO_SHOW':
      return 'bg-amber-50 text-amber-800'
    default:
      return 'bg-gray-100 text-gray-600'
  }
}

/** A next-visit candidate. Today's still-booked visit stays upcoming even after its time (delayed sessions). */
export function isUpcoming(booking: PatientBookingSummary): boolean {
  return booking.visitOutcome === 'SCHEDULED' || booking.visitOutcome === 'CHECKED_IN'
}

/** Earliest upcoming visit: by date, then timed before untimed (by start time), then by token. */
export function selectNextVisit(bookings: PatientBookingSummary[]): PatientBookingSummary | null {
  const upcoming = bookings.filter(isUpcoming)
  upcoming.sort((a, b) => {
    if (a.sessionDate !== b.sessionDate) return a.sessionDate < b.sessionDate ? -1 : 1
    if (a.startTime !== b.startTime) {
      if (a.startTime === null) return 1
      if (b.startTime === null) return -1
      return a.startTime < b.startTime ? -1 : 1
    }
    return (a.tokenNumber ?? 0) - (b.tokenNumber ?? 0)
  })
  return upcoming[0] ?? null
}

export const CANCELLATION_REFUSAL_TEXT: Record<CancellationRefusal, string> = {
  ALREADY_CANCELLED: 'This booking is already cancelled.',
  VISIT_RESOLVED: "This visit has already been recorded by the clinic, so it can't be cancelled.",
  QUEUE_BOOKING: "Queue bookings can't be cancelled online. Please contact the clinic.",
  WALK_IN: "Walk-in visits are managed by the clinic's front desk. Please speak to the clinic.",
  CUTOFF_PASSED:
    "This appointment can no longer be cancelled online because it starts within 2 hours or has already started. Please contact the clinic.",
}
