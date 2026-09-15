import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { BookSlotForm } from '../../src/features/patient-booking/BookSlotForm'
import { bookSlot, BookSlotApiError, type OpenSlot } from '../../src/features/patient-booking/api'

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
  appointmentTypes: [{ id: 'type-1', doctorProfileId: 'doc-1', name: 'General Consultation', feeOverride: null }],
}

describe('BookSlotForm', () => {
  beforeEach(() => {
    mockedBookSlot.mockReset()
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

    render(<BookSlotForm clinicId={CLINIC_ID} slot={SLOT} token="a.jwt.token" />)

    await user.type(screen.getByLabelText(/your name/i), 'Jane Doe')
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
    mockedBookSlot.mockRejectedValueOnce(new BookSlotApiError({ error: 'SLOT_ALREADY_BOOKED' }))

    render(<BookSlotForm clinicId={CLINIC_ID} slot={SLOT} token="a.jwt.token" />)

    await user.type(screen.getByLabelText(/your name/i), 'Jane Doe')
    await user.click(screen.getByRole('button', { name: /book slot/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/no longer available/i)
  })

  it('shows the NO_FEE_CONFIGURED error message', async () => {
    const user = userEvent.setup()
    mockedBookSlot.mockRejectedValueOnce(new BookSlotApiError({ error: 'NO_FEE_CONFIGURED' }))

    render(<BookSlotForm clinicId={CLINIC_ID} slot={SLOT} token="a.jwt.token" />)

    await user.type(screen.getByLabelText(/your name/i), 'Jane Doe')
    await user.click(screen.getByRole('button', { name: /book slot/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/no fee is configured/i)
  })
})
