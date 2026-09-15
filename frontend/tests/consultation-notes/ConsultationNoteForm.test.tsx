import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ConsultationNoteForm } from '../../src/features/consultation-notes/ConsultationNoteForm'
import {
  createConsultationNote,
  getConsultationNote,
  ConsultationNoteApiError,
} from '../../src/features/consultation-notes/api'
import { storeStaffSession } from '../../src/features/staff-login/token'

vi.mock('../../src/features/consultation-notes/api', async () => {
  const actual = await vi.importActual<typeof import('../../src/features/consultation-notes/api')>(
    '../../src/features/consultation-notes/api',
  )
  return {
    ...actual,
    createConsultationNote: vi.fn(),
    getConsultationNote: vi.fn(),
  }
})

const mockedCreate = vi.mocked(createConsultationNote)
const mockedGet = vi.mocked(getConsultationNote)

const CLINIC_ID = 'clinic-1'
const BOOKING_ID = 'booking-1'

describe('ConsultationNoteForm', () => {
  beforeEach(() => {
    mockedCreate.mockReset()
    mockedGet.mockReset()
    storeStaffSession({ token: 'a.jwt.token', accountId: 'doctor-1', email: 'doctor@example.com' })
  })

  it('creates a note and then shows it read-only', async () => {
    const user = userEvent.setup()
    mockedGet.mockRejectedValueOnce(new ConsultationNoteApiError({ error: 'CONSULTATION_NOTE_NOT_FOUND' }))
    mockedCreate.mockResolvedValueOnce({
      id: 'note-1',
      bookingId: BOOKING_ID,
      doctorProfileId: 'doctor-profile-1',
      content: 'Patient presented with mild fever.',
      createdAt: '2026-09-04T10:00:00Z',
    })

    render(<ConsultationNoteForm clinicId={CLINIC_ID} bookingId={BOOKING_ID} />)

    const textarea = await screen.findByLabelText('Consultation note')
    await user.type(textarea, 'Patient presented with mild fever.')
    await user.click(screen.getByRole('button', { name: /save note/i }))

    expect(await screen.findByText('Patient presented with mild fever.')).toBeInTheDocument()
    expect(mockedCreate).toHaveBeenCalledWith(
      CLINIC_ID,
      BOOKING_ID,
      'Patient presented with mild fever.',
      'a.jwt.token',
    )
  })

  it('shows the existing note read-only without a form when one already exists', async () => {
    mockedGet.mockResolvedValueOnce({
      id: 'note-1',
      bookingId: BOOKING_ID,
      doctorProfileId: 'doctor-profile-1',
      content: 'Existing note content.',
      createdAt: '2026-09-04T10:00:00Z',
    })

    render(<ConsultationNoteForm clinicId={CLINIC_ID} bookingId={BOOKING_ID} />)

    expect(await screen.findByText('Existing note content.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /save note/i })).not.toBeInTheDocument()
  })

  it('shows the CONSULTATION_NOTE_ALREADY_EXISTS error message', async () => {
    const user = userEvent.setup()
    mockedGet.mockRejectedValueOnce(new ConsultationNoteApiError({ error: 'CONSULTATION_NOTE_NOT_FOUND' }))
    mockedCreate.mockRejectedValueOnce(new ConsultationNoteApiError({ error: 'CONSULTATION_NOTE_ALREADY_EXISTS' }))

    render(<ConsultationNoteForm clinicId={CLINIC_ID} bookingId={BOOKING_ID} />)

    const textarea = await screen.findByLabelText('Consultation note')
    await user.type(textarea, 'Some note.')
    await user.click(screen.getByRole('button', { name: /save note/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/already exists/i)
  })

  it('shows the FORBIDDEN error message', async () => {
    const user = userEvent.setup()
    mockedGet.mockRejectedValueOnce(new ConsultationNoteApiError({ error: 'CONSULTATION_NOTE_NOT_FOUND' }))
    mockedCreate.mockRejectedValueOnce(new ConsultationNoteApiError({ error: 'FORBIDDEN' }))

    render(<ConsultationNoteForm clinicId={CLINIC_ID} bookingId={BOOKING_ID} />)

    const textarea = await screen.findByLabelText('Consultation note')
    await user.type(textarea, 'Some note.')
    await user.click(screen.getByRole('button', { name: /save note/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/only the treating doctor/i)
  })

  // staff-console-audit-2026-09-10 P2: used to show this message as a banner above a form that
  // was still live and submittable, for a booking that doesn't exist.
  it('blocks the form entirely (no textarea, no save button) when the booking itself is not found', async () => {
    mockedGet.mockRejectedValueOnce(new ConsultationNoteApiError({ error: 'BOOKING_NOT_FOUND' }))

    render(<ConsultationNoteForm clinicId={CLINIC_ID} bookingId={BOOKING_ID} />)

    expect(await screen.findByRole('alert')).toHaveTextContent(/could not be found/i)
    expect(screen.queryByLabelText('Consultation note')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /save note/i })).not.toBeInTheDocument()
  })
})
