import { render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { VisitRecordSection } from '../../src/features/patient-clinical-records/VisitRecordSection'
import { getConsultationNote, getPrescriptions, getExternalRecordReferences } from '../../src/features/patient-clinical-records/api'
import { storePatientSession } from '../../src/features/patient-account/token'

vi.mock('../../src/features/patient-clinical-records/api', async () => {
  const actual = await vi.importActual<typeof import('../../src/features/patient-clinical-records/api')>(
    '../../src/features/patient-clinical-records/api',
  )
  return { ...actual, getConsultationNote: vi.fn(), getPrescriptions: vi.fn(), getExternalRecordReferences: vi.fn() }
})

const mockedGetConsultationNote = vi.mocked(getConsultationNote)
const mockedGetPrescriptions = vi.mocked(getPrescriptions)
const mockedGetExternalRecordReferences = vi.mocked(getExternalRecordReferences)

const BOOKING_ID = 'booking-1'

describe('VisitRecordSection - consultation note (059-patient-clinical-record-access US1)', () => {
  beforeEach(() => {
    mockedGetConsultationNote.mockReset()
    mockedGetPrescriptions.mockReset()
    mockedGetPrescriptions.mockResolvedValue([])
    mockedGetExternalRecordReferences.mockReset()
    mockedGetExternalRecordReferences.mockResolvedValue([])
    storePatientSession({ token: 'a.jwt.token', patientAccountId: 'patient-1', email: 'patient@example.com' })
  })

  it("renders the consultation note's content when one exists", async () => {
    mockedGetConsultationNote.mockResolvedValueOnce({
      id: 'note-1',
      bookingId: BOOKING_ID,
      doctorProfileId: 'doctor-1',
      content: 'Discussed symptoms, prescribed rest.',
      createdAt: '2026-09-01T10:00:00Z',
    })

    render(<VisitRecordSection bookingId={BOOKING_ID} />)

    expect(await screen.findByText('Discussed symptoms, prescribed rest.')).toBeInTheDocument()
  })

  it('shows a clear empty state when no note exists', async () => {
    mockedGetConsultationNote.mockResolvedValueOnce(null)

    render(<VisitRecordSection bookingId={BOOKING_ID} />)

    expect(await screen.findByText(/no consultation note/i)).toBeInTheDocument()
  })

  it('never renders an edit, delete, or correction control', async () => {
    mockedGetConsultationNote.mockResolvedValueOnce({
      id: 'note-1',
      bookingId: BOOKING_ID,
      doctorProfileId: 'doctor-1',
      content: 'Discussed symptoms, prescribed rest.',
      createdAt: '2026-09-01T10:00:00Z',
    })

    render(<VisitRecordSection bookingId={BOOKING_ID} />)

    await screen.findByText('Discussed symptoms, prescribed rest.')
    expect(screen.queryByRole('button', { name: /edit|delete|correct/i })).not.toBeInTheDocument()
  })
})

describe('VisitRecordSection - prescriptions (059-patient-clinical-record-access US2)', () => {
  beforeEach(() => {
    mockedGetConsultationNote.mockReset()
    mockedGetConsultationNote.mockResolvedValue(null)
    mockedGetPrescriptions.mockReset()
    mockedGetExternalRecordReferences.mockReset()
    mockedGetExternalRecordReferences.mockResolvedValue([])
    storePatientSession({ token: 'a.jwt.token', patientAccountId: 'patient-1', email: 'patient@example.com' })
  })

  it('renders every prescription and its items when present', async () => {
    mockedGetPrescriptions.mockResolvedValueOnce([
      {
        id: 'rx-1',
        bookingId: BOOKING_ID,
        doctorProfileId: 'doctor-1',
        createdAt: '2026-09-01T10:00:00Z',
        items: [
          {
            id: 'item-1',
            medicationName: 'Paracetamol',
            dosage: '500mg',
            frequency: 'Twice daily',
            duration: '5 days',
            instructions: 'After food',
          },
        ],
      },
    ])

    render(<VisitRecordSection bookingId={BOOKING_ID} />)

    expect(await screen.findByText('Paracetamol')).toBeInTheDocument()
    expect(screen.getByText(/500mg/)).toBeInTheDocument()
  })

  it('shows a clear empty state when no prescription exists', async () => {
    mockedGetPrescriptions.mockResolvedValueOnce([])

    render(<VisitRecordSection bookingId={BOOKING_ID} />)

    expect(await screen.findByText(/no prescriptions/i)).toBeInTheDocument()
  })

  it('never renders an edit, delete, or correction control', async () => {
    mockedGetPrescriptions.mockResolvedValueOnce([
      {
        id: 'rx-1',
        bookingId: BOOKING_ID,
        doctorProfileId: 'doctor-1',
        createdAt: '2026-09-01T10:00:00Z',
        items: [
          {
            id: 'item-1',
            medicationName: 'Paracetamol',
            dosage: '500mg',
            frequency: 'Twice daily',
            duration: '5 days',
            instructions: 'After food',
          },
        ],
      },
    ])

    render(<VisitRecordSection bookingId={BOOKING_ID} />)

    await screen.findByText('Paracetamol')
    expect(screen.queryByRole('button', { name: /edit|delete|correct/i })).not.toBeInTheDocument()
  })
})

describe('VisitRecordSection - external record references (059-patient-clinical-record-access US3)', () => {
  beforeEach(() => {
    mockedGetConsultationNote.mockReset()
    mockedGetConsultationNote.mockResolvedValue(null)
    mockedGetPrescriptions.mockReset()
    mockedGetPrescriptions.mockResolvedValue([])
    mockedGetExternalRecordReferences.mockReset()
    storePatientSession({ token: 'a.jwt.token', patientAccountId: 'patient-1', email: 'patient@example.com' })
  })

  it('renders every external record reference when present', async () => {
    mockedGetExternalRecordReferences.mockResolvedValueOnce([
      {
        id: 'ref-1',
        bookingId: BOOKING_ID,
        doctorProfileId: 'doctor-1',
        recordType: 'Lab Report',
        sourceProvider: 'City Diagnostics',
        recordDate: '2026-08-01',
        summary: 'Blood panel reviewed.',
        createdAt: '2026-09-01T10:00:00Z',
      },
    ])

    render(<VisitRecordSection bookingId={BOOKING_ID} />)

    expect(await screen.findByText('Lab Report')).toBeInTheDocument()
    expect(screen.getByText(/blood panel reviewed/i)).toBeInTheDocument()
  })

  it('shows a clear empty state when no external record reference exists', async () => {
    mockedGetExternalRecordReferences.mockResolvedValueOnce([])

    render(<VisitRecordSection bookingId={BOOKING_ID} />)

    expect(await screen.findByText(/no external records/i)).toBeInTheDocument()
  })
})
