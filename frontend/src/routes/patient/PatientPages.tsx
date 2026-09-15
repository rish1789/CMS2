import { useLocation, useParams, useSearchParams } from 'react-router-dom'
import { OpenSlotList } from '../../features/patient-booking/OpenSlotList'
import { QueueBookSlotForm } from '../../features/patient-booking/QueueBookSlotForm'
import { QueueSessionList } from '../../features/patient-booking/QueueSessionList'
import type { AppointmentTypeOption } from '../../features/patient-booking/api'
import { CancelBookingButton } from '../../features/booking-cancellation/CancelBookingButton'
import { QueuePositionIndicator } from '../../features/queue-position/QueuePositionIndicator'
import { JoinWaitlistForm } from '../../features/waitlist/JoinWaitlistForm'
import { MyWaitlistEntries } from '../../features/waitlist/MyWaitlistEntries'
import { MyClinics } from '../../features/patient-clinics/MyClinics'
import { MyBookings } from '../../features/patient-bookings/MyBookings'

export function BookAtClinicPage() {
  const { clinicId } = useParams<{ clinicId: string }>()
  const [searchParams] = useSearchParams()
  const doctorId = searchParams.get('doctorId') ?? undefined
  if (!clinicId) return null
  return <OpenSlotList clinicId={clinicId} doctorId={doctorId} />
}

export function PatientQueueSessionsPage() {
  const { clinicId } = useParams<{ clinicId: string }>()
  const [searchParams] = useSearchParams()
  const doctorId = searchParams.get('doctorId') ?? undefined
  if (!clinicId) return null
  return <QueueSessionList clinicId={clinicId} doctorId={doctorId} />
}

interface QueueBookRouterState {
  appointmentTypes?: AppointmentTypeOption[]
  doctorName?: string
  sessionDate?: string
  startTime?: string
  endTime?: string
}

export function PatientQueueBookPage() {
  const { clinicId, sessionId } = useParams<{ clinicId: string; sessionId: string }>()
  const location = useLocation()
  const state = (location.state as QueueBookRouterState | null) ?? {}
  if (!clinicId || !sessionId) return null
  return (
    <QueueBookSlotForm
      clinicId={clinicId}
      sessionId={sessionId}
      appointmentTypes={state.appointmentTypes}
      doctorName={state.doctorName}
      sessionDate={state.sessionDate}
      startTime={state.startTime}
      endTime={state.endTime}
    />
  )
}

export function MyClinicsPage() {
  return <MyClinics />
}

type BookingsTab = 'bookings' | 'waitlist'

// patient-bookings-view: one dedicated section for everything the task asks for - active
// booked slots, joined queues (both already unified inside MyBookings via its own `mode`
// field), and waitlist status - as two tabs sharing one page/URL, rather than the two
// previously-separate, cross-linked-nowhere "My bookings" and "My waitlist entries" pages.
export function MyBookingsPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const tab: BookingsTab = searchParams.get('tab') === 'waitlist' ? 'waitlist' : 'bookings'

  function selectTab(next: BookingsTab) {
    setSearchParams(next === 'bookings' ? {} : { tab: next })
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-lg font-semibold text-gray-900">My bookings</h1>
        <p className="mt-0.5 text-sm text-gray-600">Your booked slots, joined queues, and waitlist status - all in one place.</p>
      </div>

      <div role="tablist" aria-label="My bookings sections" className="inline-flex rounded-lg border border-gray-200 bg-gray-50 p-1">
        <button
          type="button"
          role="tab"
          aria-selected={tab === 'bookings'}
          onClick={() => selectTab('bookings')}
          className={`rounded-md px-3.5 py-1.5 text-sm font-medium transition-colors duration-150 ${
            tab === 'bookings' ? 'bg-white text-gray-900 shadow-sm' : 'text-gray-600 hover:text-gray-900'
          }`}
        >
          Bookings
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={tab === 'waitlist'}
          onClick={() => selectTab('waitlist')}
          className={`rounded-md px-3.5 py-1.5 text-sm font-medium transition-colors duration-150 ${
            tab === 'waitlist' ? 'bg-white text-gray-900 shadow-sm' : 'text-gray-600 hover:text-gray-900'
          }`}
        >
          Waitlist
        </button>
      </div>

      {tab === 'bookings' ? <MyBookings /> : <MyWaitlistEntries />}
    </div>
  )
}

export function PatientBookingDetailPage() {
  const { bookingId } = useParams<{ bookingId: string }>()
  if (!bookingId) return null
  return (
    <div className="mx-auto max-w-md space-y-4">
      <QueuePositionIndicator mode="patient" bookingId={bookingId} />
      <CancelBookingButton mode="patient" bookingId={bookingId} />
    </div>
  )
}

export function PatientJoinWaitlistPage() {
  const { clinicId } = useParams<{ clinicId: string }>()
  if (!clinicId) return null
  return <JoinWaitlistForm clinicId={clinicId} />
}
