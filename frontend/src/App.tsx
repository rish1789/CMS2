import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { HomePage } from './routes/HomePage'
import { NotFoundPage } from './routes/NotFoundPage'
import { PublicHeader } from './routes/PublicHeader'
import { RegistrationForm } from './features/clinic-registration/RegistrationForm'
import { DiscoverySearch } from './features/discovery/DiscoverySearch'
import { SignupForm } from './features/patient-account/SignupForm'
import { RequirePatientSession, RequireStaffSession, RequireSuperAdminSession } from './routes/guards'

import { StaffLoginPage } from './routes/staff/StaffLoginPage'
import { StaffShell } from './routes/staff/StaffShell'
import { StaffDashboard } from './routes/staff/StaffDashboard'
import { ClinicShell } from './routes/staff/ClinicShell'
import { ClinicToolsDashboard } from './routes/staff/ClinicToolsDashboard'
import {
  AnonymizePatientPage,
  AppointmentTypesPage,
  BookSlotPage,
  ConsultationNotePage,
  DefineSchedulePage,
  ExternalRecordReferencePage,
  InboxRoutePage,
  OnboardStaffPage,
  PrescriptionPage,
  QueueBookSlotPage,
  SessionOperationsPage,
  StaffCancelBookingPage,
  StaffJoinWaitlistPage,
  StaffQueuePositionPage,
  WalkInPage,
} from './routes/staff/ClinicToolPages'

import { PatientLoginPage } from './routes/patient/PatientLoginPage'
import { PatientShell } from './routes/patient/PatientShell'
import { PatientDashboard } from './routes/patient/PatientDashboard'
import { PatientClinicHubPage } from './routes/patient/PatientClinicHubPage'
import {
  BookAtClinicPage,
  MyBookingsPage,
  MyClinicsPage,
  PatientBookingDetailPage,
  PatientJoinWaitlistPage,
  PatientQueueBookPage,
  PatientQueueSessionsPage,
} from './routes/patient/PatientPages'

import { AdminShell } from './routes/admin/AdminShell'
import { AdminDashboard } from './routes/admin/AdminDashboard'
import { AdminSectionShell } from './routes/admin/AdminSectionShell'
import { PendingClinicsList } from './features/clinic-verification/PendingClinicsList'
import { PendingDoctorsList } from './features/doctor-verification/PendingDoctorsList'
import { TriggerSessionGeneration } from './features/session-generation/TriggerSessionGeneration'

import { DaySheet } from './features/day-sheet/DaySheet'
import { SessionSlotsView } from './features/day-sheet/SessionSlotsView'
import { PatientSearch } from './features/patient-search/PatientSearch'
import { DoctorPicker } from './features/doctor-picker/DoctorPicker'
import { StaffPicker } from './features/staff-picker/StaffPicker'

// _diagnostics [HIGH] - [APP_SHELL] - [NO_ROUTING_INFRASTRUCTURE]: the umbrella finding - App.tsx
// previously rendered exactly one component (RegistrationForm) and no router of any kind existed
// in the dependency tree, leaving all 23 feature folders' components correct, tested, and
// individually wired to their backend contracts, but unreachable by a real user.
function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<HomePage />} />
        <Route
          path="/register"
          element={
            <div className="min-h-screen bg-gray-50">
              <PublicHeader />
              <main className="py-12">
                <RegistrationForm />
              </main>
            </div>
          }
        />
        <Route
          path="/discover"
          element={
            <div className="min-h-screen bg-gray-50">
              <PublicHeader />
              <main className="py-12">
                <DiscoverySearch />
              </main>
            </div>
          }
        />

        <Route path="/patient/login" element={<PatientLoginPage />} />
        <Route
          path="/patient/signup"
          element={
            <div className="min-h-screen bg-gray-50">
              <PublicHeader />
              <div className="flex items-center justify-center px-6 py-12">
                <SignupForm />
              </div>
            </div>
          }
        />
        <Route element={<RequirePatientSession />}>
          <Route element={<PatientShell />}>
            <Route path="/patient" element={<PatientDashboard />} />
            <Route path="/patient/clinics" element={<MyClinicsPage />} />
            <Route path="/patient/clinics/:clinicId" element={<PatientClinicHubPage />} />
            <Route path="/patient/clinics/:clinicId/book" element={<BookAtClinicPage />} />
            <Route path="/patient/clinics/:clinicId/queue-sessions" element={<PatientQueueSessionsPage />} />
            <Route path="/patient/clinics/:clinicId/sessions/:sessionId/queue-book" element={<PatientQueueBookPage />} />
            <Route path="/patient/clinics/:clinicId/waitlist/join" element={<PatientJoinWaitlistPage />} />
            <Route path="/patient/bookings" element={<MyBookingsPage />} />
            <Route path="/patient/bookings/:bookingId" element={<PatientBookingDetailPage />} />
            {/* patient-bookings-view: waitlist status now lives as a tab inside /patient/bookings - this keeps any existing bookmark/link working. */}
            <Route path="/patient/waitlist" element={<Navigate to="/patient/bookings?tab=waitlist" replace />} />
          </Route>
        </Route>

        <Route path="/staff/login" element={<StaffLoginPage />} />
        <Route element={<RequireStaffSession />}>
          <Route element={<StaffShell />}>
            <Route path="/staff" element={<StaffDashboard />} />
            <Route path="/staff/clinics/:clinicId" element={<ClinicShell />}>
              <Route index element={<ClinicToolsDashboard />} />
              <Route path="onboard" element={<OnboardStaffPage />} />
              <Route path="doctors/:doctorProfileId/schedule" element={<DefineSchedulePage />} />
              <Route path="doctors/:doctorProfileId/appointment-types" element={<AppointmentTypesPage />} />
              <Route path="slots/:slotId/book" element={<BookSlotPage />} />
              <Route path="sessions/:sessionId/queue-book" element={<QueueBookSlotPage />} />
              <Route path="sessions/:sessionId/walk-in" element={<WalkInPage />} />
              <Route path="sessions/:sessionId/operations" element={<SessionOperationsPage />} />
              <Route path="bookings/:bookingId/cancel" element={<StaffCancelBookingPage />} />
              <Route path="bookings/:bookingId/queue-position" element={<StaffQueuePositionPage />} />
              <Route path="bookings/:bookingId/consultation-note" element={<ConsultationNotePage />} />
              <Route path="bookings/:bookingId/prescription" element={<PrescriptionPage />} />
              <Route path="bookings/:bookingId/external-record" element={<ExternalRecordReferencePage />} />
              <Route path="patients/:patientId/anonymize" element={<AnonymizePatientPage />} />
              <Route path="waitlist/join" element={<StaffJoinWaitlistPage />} />
              <Route path="inbox" element={<InboxRoutePage />} />
              {/* 041-staff-console-pickers: browse/pick views replacing typed-ID entry */}
              <Route path="day-sheet" element={<DaySheet />} />
              <Route path="day-sheet/:sessionId" element={<SessionSlotsView />} />
              <Route path="patients/search" element={<PatientSearch />} />
              <Route path="doctors" element={<DoctorPicker />} />
              <Route path="staff" element={<StaffPicker />} />
            </Route>
          </Route>
        </Route>

        {/* 040-super-admin-rbac-login: guarded, replacing the prior unguarded /admin path */}
        <Route element={<RequireSuperAdminSession />}>
          <Route path="/super-admin-console" element={<AdminShell />}>
            <Route index element={<AdminDashboard />} />
            <Route element={<AdminSectionShell />}>
              <Route path="clinics" element={<PendingClinicsList />} />
              <Route path="doctors" element={<PendingDoctorsList />} />
              <Route path="sessions/generate" element={<TriggerSessionGeneration />} />
            </Route>
          </Route>
        </Route>

        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </BrowserRouter>
  )
}

export default App
