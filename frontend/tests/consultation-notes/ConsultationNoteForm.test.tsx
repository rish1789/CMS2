import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ConsultationNoteForm } from '../../src/features/consultation-notes/ConsultationNoteForm'
import {
  createConsultationNote,
  getConsultationNote,
} from '../../src/features/consultation-notes/api'
import { ApiError } from '../../src/lib/apiClient'
import { storeStaffSession } from '../../src/features/staff-login/token'

// 065-phase1-stabilization (BUG-006): delay: null keeps every keystroke's events but drops
// user-event's per-keystroke setTimeout(0) yield, which queues behind other workers under
// full-suite parallel load (the cause of the intermittent 5 s timeouts in typing-heavy tests).
const TYPING_OPTIONS = { delay: null }

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
    const user = userEvent.setup(TYPING_OPTIONS)
    mockedGet.mockRejectedValueOnce(
      new ApiError(404, 'No consultation note exists for this booking yet.', {
        error: 'CONSULTATION_NOTE_NOT_FOUND',
      }),
    )
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
    const user = userEvent.setup(TYPING_OPTIONS)
    mockedGet.mockRejectedValueOnce(
      new ApiError(404, 'No consultation note exists for this booking yet.', {
        error: 'CONSULTATION_NOTE_NOT_FOUND',
      }),
    )
    mockedCreate.mockRejectedValueOnce(
      new ApiError(409, 'A consultation note already exists for this booking.', {
        error: 'CONSULTATION_NOTE_ALREADY_EXISTS',
      }),
    )

    render(<ConsultationNoteForm clinicId={CLINIC_ID} bookingId={BOOKING_ID} />)

    const textarea = await screen.findByLabelText('Consultation note')
    await user.type(textarea, 'Some note.')
    await user.click(screen.getByRole('button', { name: /save note/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/already exists/i)
  })

  it('shows the FORBIDDEN error message', async () => {
    const user = userEvent.setup(TYPING_OPTIONS)
    mockedGet.mockRejectedValueOnce(
      new ApiError(404, 'No consultation note exists for this booking yet.', {
        error: 'CONSULTATION_NOTE_NOT_FOUND',
      }),
    )
    mockedCreate.mockRejectedValueOnce(
      new ApiError(403, 'Only the treating doctor may write or view this note.', { error: 'FORBIDDEN' }),
    )

    render(<ConsultationNoteForm clinicId={CLINIC_ID} bookingId={BOOKING_ID} />)

    const textarea = await screen.findByLabelText('Consultation note')
    await user.type(textarea, 'Some note.')
    await user.click(screen.getByRole('button', { name: /save note/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/only the treating doctor/i)
  })

  // 054-forms-validation-consistency T014: pre-submit non-blank-content check blocks the
  // network call entirely (research.md Decision 3) - the only validation this endpoint has.
  // Whitespace-only content (not empty) is used because HTML5's native `required` on the
  // textarea already blocks a truly-empty submit before this component's own JS ever runs -
  // this is the one case that check does NOT catch, so it is what proves the new rule works.
  it('blocks submission and shows an inline error when the content is only whitespace', async () => {
    const user = userEvent.setup(TYPING_OPTIONS)
    mockedGet.mockRejectedValueOnce(
      new ApiError(404, 'No consultation note exists for this booking yet.', {
        error: 'CONSULTATION_NOTE_NOT_FOUND',
      }),
    )

    render(<ConsultationNoteForm clinicId={CLINIC_ID} bookingId={BOOKING_ID} />)

    const textarea = await screen.findByLabelText('Consultation note')
    await user.type(textarea, '   ')
    await user.click(screen.getByRole('button', { name: /save note/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/cannot be blank/i)
    expect(mockedCreate).not.toHaveBeenCalled()
  })

  // staff-console-audit-2026-09-10 P2: used to show this message as a banner above a form that
  // was still live and submittable, for a booking that doesn't exist.
  it('blocks the form entirely (no textarea, no save button) when the booking itself is not found', async () => {
    mockedGet.mockRejectedValueOnce(
      new ApiError(404, 'This booking could not be found.', { error: 'BOOKING_NOT_FOUND' }),
    )

    render(<ConsultationNoteForm clinicId={CLINIC_ID} bookingId={BOOKING_ID} />)

    expect(await screen.findByRole('alert')).toHaveTextContent(/could not be found/i)
    expect(screen.queryByLabelText('Consultation note')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /save note/i })).not.toBeInTheDocument()
  })

  // real-bug-fix 2026-09-17: same reasoning as the booking-not-found case above - a caller who
  // isn't the treating doctor used to see this message as a banner above a form that was still
  // live and submittable, inviting a doomed resubmit of the same denied request.
  it('blocks the form entirely (no textarea, no save button) when the caller is not the treating doctor', async () => {
    mockedGet.mockRejectedValueOnce(
      new ApiError(403, 'Only the treating doctor may write or view this note.', { error: 'FORBIDDEN' }),
    )

    render(<ConsultationNoteForm clinicId={CLINIC_ID} bookingId={BOOKING_ID} />)

    expect(await screen.findByRole('alert')).toHaveTextContent(/only the treating doctor/i)
    expect(screen.queryByLabelText('Consultation note')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /save note/i })).not.toBeInTheDocument()
  })
})
