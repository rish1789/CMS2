import { useParams, useSearchParams } from 'react-router-dom'
import { OnboardStaffForm } from '../../features/staff-onboarding/OnboardStaffForm'
import { ScheduleForm } from '../../features/scheduling/ScheduleForm'
import { AppointmentTypeConfigForm } from '../../features/appointment-types/AppointmentTypeConfigForm'
import { BookSlotForm as StaffBookSlotForm } from '../../features/staff-booking/BookSlotForm'
import { QueueBookSlotForm as StaffQueueBookSlotForm } from '../../features/staff-booking/QueueBookSlotForm'
import { WalkInForm } from '../../features/staff-booking/WalkInForm'
import { SessionOperationsPanel } from '../../features/session-delay/SessionOperationsPanel'
import { CancelBookingButton } from '../../features/booking-cancellation/CancelBookingButton'
import { QueuePositionIndicator } from '../../features/queue-position/QueuePositionIndicator'
import { ConsultationNoteForm } from '../../features/consultation-notes/ConsultationNoteForm'
import { PrescriptionForm } from '../../features/prescriptions/PrescriptionForm'
import { ExternalRecordReferenceForm } from '../../features/external-record-references/ExternalRecordReferenceForm'
import { AnonymizePatientButton } from '../../features/patient-anonymization/AnonymizePatientButton'
import { StaffJoinWaitlistForm } from '../../features/waitlist/StaffJoinWaitlistForm'
import { InboxPage } from '../../features/inbox/InboxPage'
import { BookingContextHeader } from '../../features/booking-detail/BookingContextHeader'
import { PatientContextHeader } from '../../features/patient-search/PatientContextHeader'

// _diagnostics [HIGH] - [APP_SHELL] - [NO_ROUTING_INFRASTRUCTURE]: one thin page per clinic-scoped
// tool, extracting ids from the URL and rendering the already-built, already-tested feature
// component - every one of these previously had no way to be reached by a real user at all.

export function OnboardStaffPage() {
  const { clinicId } = useParams<{ clinicId: string }>()
  if (!clinicId) return null
  return <OnboardStaffForm clinicId={clinicId} />
}

export function DefineSchedulePage() {
  const { clinicId, doctorProfileId } = useParams<{ clinicId: string; doctorProfileId: string }>()
  if (!clinicId || !doctorProfileId) return null
  return <ScheduleForm clinicId={clinicId} doctorProfileId={doctorProfileId} />
}

export function AppointmentTypesPage() {
  const { doctorProfileId } = useParams<{ doctorProfileId: string }>()
  if (!doctorProfileId) return null
  return <AppointmentTypeConfigForm doctorProfileId={doctorProfileId} />
}

export function BookSlotPage() {
  const { clinicId, slotId } = useParams<{ clinicId: string; slotId: string }>()
  const [searchParams] = useSearchParams()
  const doctorProfileId = searchParams.get('doctorProfileId')
  if (!clinicId || !slotId || !doctorProfileId) return null
  return <StaffBookSlotForm clinicId={clinicId} slotId={slotId} doctorProfileId={doctorProfileId} />
}

export function QueueBookSlotPage() {
  const { clinicId, sessionId } = useParams<{ clinicId: string; sessionId: string }>()
  const [searchParams] = useSearchParams()
  const doctorProfileId = searchParams.get('doctorProfileId')
  if (!clinicId || !sessionId || !doctorProfileId) return null
  return <StaffQueueBookSlotForm clinicId={clinicId} sessionId={sessionId} doctorProfileId={doctorProfileId} />
}

export function WalkInPage() {
  const { clinicId, sessionId } = useParams<{ clinicId: string; sessionId: string }>()
  const [searchParams] = useSearchParams()
  const doctorProfileId = searchParams.get('doctorProfileId')
  if (!clinicId || !sessionId || !doctorProfileId) return null
  return <WalkInForm clinicId={clinicId} sessionId={sessionId} doctorProfileId={doctorProfileId} />
}

export function SessionOperationsPage() {
  const { clinicId, sessionId } = useParams<{ clinicId: string; sessionId: string }>()
  const [searchParams] = useSearchParams()
  const slotId = searchParams.get('slotId')
  // bookingId is optional here (this route is session+slot scoped, not booking scoped) - when
  // present (SessionSlotsView's "Mark complete" link supplies it), it powers the entity-context
  // header only; the panel itself still works from sessionId/slotId alone.
  const bookingId = searchParams.get('bookingId')
  if (!clinicId || !sessionId || !slotId) return null
  return (
    <div className="space-y-4">
      {bookingId && <BookingContextHeader clinicId={clinicId} bookingId={bookingId} />}
      <SessionOperationsPanel clinicId={clinicId} sessionId={sessionId} slotId={slotId} />
    </div>
  )
}

export function StaffCancelBookingPage() {
  const { clinicId, bookingId } = useParams<{ clinicId: string; bookingId: string }>()
  if (!clinicId || !bookingId) return null
  return (
    <div className="space-y-4">
      <BookingContextHeader clinicId={clinicId} bookingId={bookingId} />
      <CancelBookingButton mode="staff" clinicId={clinicId} bookingId={bookingId} />
    </div>
  )
}

export function StaffQueuePositionPage() {
  const { clinicId, bookingId } = useParams<{ clinicId: string; bookingId: string }>()
  if (!clinicId || !bookingId) return null
  return (
    <div className="space-y-4">
      <BookingContextHeader clinicId={clinicId} bookingId={bookingId} />
      <QueuePositionIndicator mode="staff" clinicId={clinicId} bookingId={bookingId} />
    </div>
  )
}

export function ConsultationNotePage() {
  const { clinicId, bookingId } = useParams<{ clinicId: string; bookingId: string }>()
  if (!clinicId || !bookingId) return null
  return (
    <div className="space-y-4">
      <BookingContextHeader clinicId={clinicId} bookingId={bookingId} />
      <ConsultationNoteForm clinicId={clinicId} bookingId={bookingId} />
    </div>
  )
}

export function PrescriptionPage() {
  const { clinicId, bookingId } = useParams<{ clinicId: string; bookingId: string }>()
  if (!clinicId || !bookingId) return null
  return (
    <div className="space-y-4">
      <BookingContextHeader clinicId={clinicId} bookingId={bookingId} />
      <PrescriptionForm clinicId={clinicId} bookingId={bookingId} />
    </div>
  )
}

export function ExternalRecordReferencePage() {
  const { clinicId, bookingId } = useParams<{ clinicId: string; bookingId: string }>()
  if (!clinicId || !bookingId) return null
  return (
    <div className="space-y-4">
      <BookingContextHeader clinicId={clinicId} bookingId={bookingId} />
      <ExternalRecordReferenceForm clinicId={clinicId} bookingId={bookingId} />
    </div>
  )
}

export function AnonymizePatientPage() {
  const { clinicId, patientId } = useParams<{ clinicId: string; patientId: string }>()
  if (!clinicId || !patientId) return null
  return (
    <div className="space-y-4">
      <PatientContextHeader clinicId={clinicId} patientId={patientId} />
      <AnonymizePatientButton clinicId={clinicId} patientId={patientId} />
    </div>
  )
}

export function StaffJoinWaitlistPage() {
  const { clinicId } = useParams<{ clinicId: string }>()
  if (!clinicId) return null
  return <StaffJoinWaitlistForm clinicId={clinicId} />
}

export function InboxRoutePage() {
  const { clinicId } = useParams<{ clinicId: string }>()
  if (!clinicId) return null
  return <InboxPage clinicId={clinicId} />
}
