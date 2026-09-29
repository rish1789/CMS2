import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import {
  getPatient,
  listPatientBookings,
  type PatientBookingSummary,
  type PatientSearchResult,
} from '../../features/patient-search/api'
import { loadStaffSession } from '../../features/staff-login/token'
import { PaginationControls } from '../../components/PaginationControls'
import { Badge } from '../../components/Badge'
import { EmptyState } from '../../components/EmptyState'
import { LoadingState } from '../../components/LoadingState'

const BOOKINGS_PAGE_SIZE = 10

type Tab = 'overview' | 'bookings' | 'consultations' | 'prescriptions' | 'external-records'

const TABS: { id: Tab; label: string }[] = [
  { id: 'overview', label: 'Overview' },
  { id: 'bookings', label: 'Bookings' },
  { id: 'consultations', label: 'Consultations' },
  { id: 'prescriptions', label: 'Prescriptions' },
  { id: 'external-records', label: 'External Records' },
]

function formatTime(time: string | null): string {
  return time ? time.slice(0, 5) : ''
}

function todayIsoDate(): string {
  const now = new Date()
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const day = String(now.getDate()).padStart(2, '0')
  return `${now.getFullYear()}-${month}-${day}`
}

// A `BOOKED` slot alone doesn't distinguish "genuinely upcoming" from "session already
// happened, not yet marked complete/no-show" - sessionDate is the real signal for "has this
// session started." Also excludes OPEN (a cancelled booking whose slot reverted to OPEN, 028 -
// no active booking ever had its consultation happen).
function sessionHasStarted(booking: PatientBookingSummary): boolean {
  return booking.slotStatus !== 'OPEN' && booking.sessionDate <= todayIsoDate()
}

function bookingStatusColor(booking: PatientBookingSummary): 'gray' | 'red' | 'green' | 'amber' {
  if (booking.bookingStatus === 'CANCELLED') return 'red'
  if (booking.slotStatus === 'COMPLETED') return 'green'
  if (booking.slotStatus === 'NO_SHOW') return 'amber'
  return 'gray'
}

function bookingStatusLabel(booking: PatientBookingSummary): string {
  if (booking.bookingStatus === 'CANCELLED') return 'Cancelled'
  return booking.slotStatus === 'BOOKED' ? 'Upcoming' : booking.slotStatus.replace('_', '-').toLowerCase()
}

// 052-patient-clinical-hub T008: a navigation hub, not a content-aggregation hub
// (research.md Decision 1) - Consultations/Prescriptions/External Records list the same
// booking data as Bookings, filtered to bookings whose session has started (excluding both
// still-upcoming and cancelled-and-reverted-to-OPEN bookings, per 028's own Slot-status
// semantics), as rows linking into the existing, unmodified per-booking pages. This page never
// fetches or renders a note/prescription/record's own content.
export function PatientHubPage() {
  const { clinicId, patientId } = useParams<{ clinicId: string; patientId: string }>()
  const [tab, setTab] = useState<Tab>('overview')
  const [patient, setPatient] = useState<PatientSearchResult | null>(null)
  const [bookings, setBookings] = useState<PatientBookingSummary[] | null>(null)
  const [totalCount, setTotalCount] = useState(0)
  const [page, setPage] = useState(0)

  useEffect(() => {
    if (!clinicId || !patientId) return
    const session = loadStaffSession()
    if (!session) return
    getPatient(clinicId, patientId, session.token)
      .then(setPatient)
      .catch(() => {})
  }, [clinicId, patientId])

  useEffect(() => {
    if (!clinicId || !patientId) return
    const session = loadStaffSession()
    if (!session) return
    setBookings(null)
    listPatientBookings(clinicId, patientId, session.token, { page, size: BOOKINGS_PAGE_SIZE })
      .then((result) => {
        setBookings(result.bookings)
        setTotalCount(result.totalCount)
      })
      .catch(() => setBookings([]))
  }, [clinicId, patientId, page])

  if (!clinicId || !patientId) return null

  const clinicalTabBookings = (bookings ?? []).filter(sessionHasStarted)
  const clinicalTabPath: Record<'consultations' | 'prescriptions' | 'external-records', string> = {
    consultations: 'consultation-note',
    prescriptions: 'prescription',
    'external-records': 'external-record',
  }

  return (
    <div className="space-y-6">
      <Link
        to={`/staff/clinics/${clinicId}/patients/search`}
        className="inline-flex items-center gap-1 text-sm font-medium text-indigo-600 transition-colors duration-150 hover:text-indigo-700"
      >
        ← Back to patient search
      </Link>

      {patient && (
        <div>
          <div className="flex items-center gap-2">
            <h1 className="text-lg font-semibold text-gray-900">{patient.name}</h1>
            {patient.anonymizedAt && <Badge color="red">Anonymized</Badge>}
          </div>
          {patient.phone && <p className="mt-0.5 text-sm text-gray-500">{patient.phone}</p>}
        </div>
      )}

      <div role="tablist" className="flex gap-1 border-b border-gray-200">
        {TABS.map((t) => (
          <button
            key={t.id}
            type="button"
            role="tab"
            aria-selected={tab === t.id}
            onClick={() => setTab(t.id)}
            className={`px-3 py-2 text-sm font-medium transition-colors duration-150 ${
              tab === t.id
                ? 'border-b-2 border-indigo-600 text-indigo-700'
                : 'text-gray-500 hover:text-gray-900'
            }`}
          >
            {t.label}
          </button>
        ))}
      </div>

      {tab === 'overview' && (
        <div className="space-y-1 text-sm text-gray-600">
          <p>Name: {patient?.name ?? '—'}</p>
          <p>Phone: {patient?.phone ?? '—'}</p>
          <p>Total bookings at this clinic: {totalCount}</p>
        </div>
      )}

      {tab === 'bookings' &&
        (bookings === null ? (
          <LoadingState variant="list" />
        ) : bookings.length === 0 ? (
          <EmptyState message="No bookings at this clinic yet." />
        ) : (
          <>
            <ul className="divide-y divide-gray-100 rounded-lg border border-gray-200 bg-white">
              {bookings.map((b) => (
                <li key={b.bookingId} className="flex items-center justify-between gap-3 p-3 text-sm">
                  <div className="flex items-center gap-3">
                    <span className="font-medium text-gray-900">{b.sessionDate}</span>
                    <span className="text-gray-500">{formatTime(b.startTime)}</span>
                    <span className="text-gray-700">{b.doctorName}</span>
                    <span className="text-gray-500">{b.appointmentTypeName}</span>
                  </div>
                  <Badge color={bookingStatusColor(b)}>{bookingStatusLabel(b)}</Badge>
                </li>
              ))}
            </ul>
            <PaginationControls
              page={page}
              pageSize={BOOKINGS_PAGE_SIZE}
              totalCount={totalCount}
              onPageChange={setPage}
              itemLabel="bookings"
            />
          </>
        ))}

      {(tab === 'consultations' || tab === 'prescriptions' || tab === 'external-records') &&
        (bookings === null ? (
          <LoadingState variant="list" />
        ) : clinicalTabBookings.length === 0 ? (
          <EmptyState message="No bookings with a started session yet." />
        ) : (
          <ul className="divide-y divide-gray-100 rounded-lg border border-gray-200 bg-white">
            {clinicalTabBookings.map((b) => (
              <li key={b.bookingId}>
                <Link
                  to={`/staff/clinics/${clinicId}/bookings/${b.bookingId}/${clinicalTabPath[tab]}`}
                  className="flex items-center justify-between gap-3 p-3 text-sm transition-colors duration-150 hover:bg-gray-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-2"
                >
                  <div className="flex items-center gap-3">
                    <span className="font-medium text-gray-900">{b.sessionDate}</span>
                    <span className="text-gray-500">{formatTime(b.startTime)}</span>
                    <span className="text-gray-700">{b.doctorName}</span>
                  </div>
                  <Badge color={bookingStatusColor(b)}>{bookingStatusLabel(b)}</Badge>
                </Link>
              </li>
            ))}
          </ul>
        ))}
    </div>
  )
}
