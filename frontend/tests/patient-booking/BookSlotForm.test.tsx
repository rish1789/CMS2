import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { BookSlotForm } from '../../src/features/patient-booking/BookSlotForm'
import { bookSlot, type OpenSlot } from '../../src/features/patient-booking/api'
import { storePatientSession } from '../../src/features/patient-account/token'
import { ApiError } from '../../src/lib/apiClient'

vi.mock('../../src/features/patient-booking/api', async () => {
  const actual = await vi.importActual<typeof import('../../src/features/patient-booking/api')>(
    '../../src/features/patient-booking/api',
  )
  return {
    ...actual,
    bookSlot: vi.fn(),
  }
})

const mockedBookSlot = vi.mocked(bookSlot)

const CLINIC_ID = 'clinic-1'

const SLOT: OpenSlot = {
  slotId: 'slot-1',
  doctorProfileId: 'doc-1',
  doctorName: 'Dr. Asha Rao',
  sessionDate: '2026-09-10',
  startTime: '09:00:00',
  endTime: '09:15:00',
  appointmentTypes: [{ id: 'type-1', doctorProfileId: 'doc-1', name: 'General Consultation', fee: null }],
}

describe('BookSlotForm', () => {
  beforeEach(() => {
    mockedBookSlot.mockReset()
    // patient-booking-modal-conversion: the form no longer asks the already-authenticated
    // patient for their name - it derives one from their own account email (the same
    // deriveDisplayNameFromEmail fallback the dashboard greeting already uses), so the session
    // that would exist for real in the app must exist here too.
    storePatientSession({ token: 'a.jwt.token', patientAccountId: 'account-1', email: 'jane.doe@example.com' })
  })

  it('books the slot and shows the locked fee', async () => {
    const user = userEvent.setup()
    mockedBookSlot.mockResolvedValueOnce({
      id: 'booking-1',
      slotId: SLOT.slotId,
      patientId: 'patient-1',
      appointmentTypeId: 'type-1',
      lockedFee: 300,
      paymentStatus: 'PENDING',
      createdAt: '2026-09-03T10:00:00Z',
    })

    render(<BookSlotForm clinicId={CLINIC_ID} slot={SLOT} token="a.jwt.token" onClose={vi.fn()} />)

    await user.click(screen.getByRole('button', { name: /book slot/i }))

    expect(await screen.findByText(/booking confirmed/i)).toBeInTheDocument()
    expect(screen.getByText(/300\.00/)).toBeInTheDocument()
    expect(mockedBookSlot).toHaveBeenCalledWith(
      CLINIC_ID,
      SLOT.slotId,
      { patientName: 'Jane Doe', appointmentTypeId: 'type-1' },
      'a.jwt.token',
    )
  })

  it('shows the SLOT_ALREADY_BOOKED error message', async () => {
    const user = userEvent.setup()
    mockedBookSlot.mockRejectedValueOnce(
      new ApiError(409, 'This slot is no longer available.', { error: 'SLOT_ALREADY_BOOKED' }),
    )

    render(<BookSlotForm clinicId={CLINIC_ID} slot={SLOT} token="a.jwt.token" onClose={vi.fn()} />)

    await user.click(screen.getByRole('button', { name: /book slot/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/no longer available/i)
  })

  it('shows the NO_FEE_CONFIGURED error message', async () => {
    const user = userEvent.setup()
    mockedBookSlot.mockRejectedValueOnce(
      new ApiError(422, 'No fee is configured for this doctor/appointment type — booking is blocked.', {
        error: 'NO_FEE_CONFIGURED',
      }),
    )

    render(<BookSlotForm clinicId={CLINIC_ID} slot={SLOT} token="a.jwt.token" onClose={vi.fn()} />)

    await user.click(screen.getByRole('button', { name: /book slot/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/no fee is configured/i)
  })

  // 046-frontend-api-client T008: proves the actual bug fix, not just the refactor - a
  // real backend-supplied message must win over the generic per-error-code default. Before
  // this feature, defaultMessageFor(body) ?? body.message made body.message unreachable dead
  // code, so this exact scenario (a specific backend message on a known error code) previously
  // could never surface to the user no matter what the backend actually said.
  it('shows the backend-specific message when present, not the generic per-error-code default', async () => {
    const user = userEvent.setup()
    mockedBookSlot.mockRejectedValueOnce(
      new ApiError(409, 'This slot was booked by another patient 3 seconds ago.', { error: 'SLOT_ALREADY_BOOKED' }),
    )

    render(<BookSlotForm clinicId={CLINIC_ID} slot={SLOT} token="a.jwt.token" onClose={vi.fn()} />)

    await user.click(screen.getByRole('button', { name: /book slot/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent('This slot was booked by another patient 3 seconds ago.')
  })

  it('calls onClose when the close button is clicked', async () => {
    const user = userEvent.setup()
    const onClose = vi.fn()

    render(<BookSlotForm clinicId={CLINIC_ID} slot={SLOT} token="a.jwt.token" onClose={onClose} />)

    await user.click(screen.getByRole('button', { name: /close/i }))

    expect(onClose).toHaveBeenCalled()
  })
})
