import { Navigate, useOutletContext, useParams, useSearchParams } from 'react-router-dom'
import type { ClinicShellOutletContext } from './ClinicShell'
import { OnboardStaffForm } from '../../features/staff-onboarding/OnboardStaffForm'
import { DoctorScheduleManager } from '../../features/scheduling/DoctorScheduleManager'
import { AppointmentTypeConfigForm } from '../../features/appointment-types/AppointmentTypeConfigForm'
import { BookSlotForm as StaffBookSlotForm } from '../../features/staff-booking/BookSlotForm'
import { QueueBookSlotForm as StaffQueueBookSlotForm } from '../../features/staff-booking/QueueBookSlotForm'
import { FrontDeskWalkInPage } from '../../features/front-desk-walk-in/FrontDeskWalkInPage'
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
import { ProtectionFlagsList } from '../../features/clinic-protection/ProtectionFlagsList'
import { ClinicLimitOverrideForm } from '../../features/clinic-protection/ClinicLimitOverrideForm'

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
  return <DoctorScheduleManager clinicId={clinicId} doctorProfileId={doctorProfileId} />
}

// 068-per-clinic-fees: prices are this clinic's own, and only its admin can change them.
export function AppointmentTypesPage() {
  const { clinicId, doctorProfileId } = useParams<{ clinicId: string; doctorProfileId: string }>()
  const { role } = useOutletContext<ClinicShellOutletContext>()
  if (!clinicId || !doctorProfileId) return null
  return (
    <AppointmentTypeConfigForm
      clinicId={clinicId}
      doctorProfileId={doctorProfileId}
      canEditPrices={role === 'ClinicAdmin'}
    />
  )
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

// 063-front-desk-walk-in (FR-020): the retired per-session walk-in URL now lands on the front-desk
// screen with that session pre-selected, so old links and bookmarks keep working.
export function LegacyWalkInRedirect() {
  const { clinicId, sessionId } = useParams<{ clinicId: string; sessionId: string }>()
  if (!clinicId || !sessionId) return null
  return <Navigate replace to={`/staff/clinics/${clinicId}/walk-in?sessionId=${sessionId}`} />
}

export function FrontDeskWalkInRoutePage() {
  const { clinicId } = useParams<{ clinicId: string }>()
  if (!clinicId) return null
  return <FrontDeskWalkInPage clinicId={clinicId} />
}

export function SessionOperationsPage() {
  const { clinicId, sessionId } = useParams<{ clinicId: string; sessionId: string }>()
  const [searchParams] = useSearchParams()
  const slotId = searchParams.get('slotId')
  // bookingId is optional here (this route is session+slot scoped, not booking scoped) - when
  // present (SessionSlotsView's "Mark complete" link supplies it), it powers the entity-context
  // header only; the panel itself still works from sessionId/slotId alone.
  const bookingId = searchParams.get('bookingId')
  const { role } = useOutletContext<ClinicShellOutletContext>()
  if (!clinicId || !sessionId || !slotId) return null
  return (
    <div className="space-y-4">
      {bookingId && <BookingContextHeader clinicId={clinicId} bookingId={bookingId} />}
      <SessionOperationsPanel
        clinicId={clinicId}
        sessionId={sessionId}
        slotId={slotId}
        isDoctor={role === 'Doctor'}
      />
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

export function ProtectionFlagsPage() {
  const { clinicId } = useParams<{ clinicId: string }>()
  if (!clinicId) return null
  return <ProtectionFlagsList clinicId={clinicId} />
}

export function ClinicLimitOverridePage() {
  const { clinicId } = useParams<{ clinicId: string }>()
  if (!clinicId) return null
  return <ClinicLimitOverrideForm clinicId={clinicId} />
}
