import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { CancelBookingButton } from '../../src/features/booking-cancellation/CancelBookingButton'
import {
  cancelBookingAsPatient,
  cancelBookingAsStaff,
  BookingCancellationApiError,
} from '../../src/features/booking-cancellation/api'
import { storeStaffSession } from '../../src/features/staff-login/token'
import { storePatientSession } from '../../src/features/patient-account/token'

vi.mock('../../src/features/booking-cancellation/api', async () => {
  const actual = await vi.importActual<typeof import('../../src/features/booking-cancellation/api')>(
    '../../src/features/booking-cancellation/api',
  )
  return {
    ...actual,
    cancelBookingAsStaff: vi.fn(),
    cancelBookingAsPatient: vi.fn(),
  }
})

const mockedCancelAsStaff = vi.mocked(cancelBookingAsStaff)
const mockedCancelAsPatient = vi.mocked(cancelBookingAsPatient)

const CLINIC_ID = 'clinic-1'
const BOOKING_ID = 'booking-1'

const SUCCESS_RESPONSE = {
  id: BOOKING_ID,
  slotId: 'slot-1',
  patientId: 'patient-1',
  appointmentTypeId: 'type-1',
  lockedFee: 300,
  paymentStatus: 'PENDING' as const,
  status: 'CANCELLED' as const,
  createdAt: '2026-09-04T10:00:00Z',
}

describe('CancelBookingButton', () => {
  beforeEach(() => {
    mockedCancelAsStaff.mockReset()
    mockedCancelAsPatient.mockReset()
    storeStaffSession({ token: 'a.jwt.token', accountId: 'acct-1', email: 'ops@example.com' })
    storePatientSession({ token: 'a.patient.token', patientAccountId: 'pt-1', email: 'patient@example.com' })
  })

  it('requires confirmation before cancelling - a single click does not call the API', async () => {
    const user = userEvent.setup()
    render(<CancelBookingButton mode="staff" clinicId={CLINIC_ID} bookingId={BOOKING_ID} />)

    await user.click(screen.getByRole('button', { name: /cancel booking/i }))

    expect(mockedCancelAsStaff).not.toHaveBeenCalled()
    expect(screen.getByText(/are you sure you want to cancel this booking/i)).toBeInTheDocument()
  })

  it('clicking Cancel on the confirmation step backs out without calling the API', async () => {
    const user = userEvent.setup()
    render(<CancelBookingButton mode="staff" clinicId={CLINIC_ID} bookingId={BOOKING_ID} />)

    await user.click(screen.getByRole('button', { name: /cancel booking/i }))
    await user.click(screen.getByRole('button', { name: /^cancel$/i }))

    expect(mockedCancelAsStaff).not.toHaveBeenCalled()
    expect(screen.getByRole('button', { name: /cancel booking/i })).toBeInTheDocument()
  })

  it('cancels a booking in staff mode after Confirm', async () => {
    const user = userEvent.setup()
    mockedCancelAsStaff.mockResolvedValueOnce(SUCCESS_RESPONSE)

    render(<CancelBookingButton mode="staff" clinicId={CLINIC_ID} bookingId={BOOKING_ID} />)

    await user.click(screen.getByRole('button', { name: /cancel booking/i }))
    await user.click(screen.getByRole('button', { name: /^confirm$/i }))

    expect(await screen.findByText(/booking cancelled/i)).toBeInTheDocument()
    expect(mockedCancelAsStaff).toHaveBeenCalledWith(CLINIC_ID, BOOKING_ID, 'a.jwt.token')
  })

  it('requires a reason before Confirm is clickable in patient mode', async () => {
    const user = userEvent.setup()
    render(<CancelBookingButton mode="patient" bookingId={BOOKING_ID} />)

    await user.click(screen.getByRole('button', { name: /cancel booking/i }))

    expect(screen.getByRole('button', { name: /confirm cancellation/i })).toBeDisabled()
    expect(mockedCancelAsPatient).not.toHaveBeenCalled()
  })

  it('cancels a booking in patient mode after selecting a reason and confirming', async () => {
    const user = userEvent.setup()
    mockedCancelAsPatient.mockResolvedValueOnce(SUCCESS_RESPONSE)

    render(<CancelBookingButton mode="patient" bookingId={BOOKING_ID} />)

    await user.click(screen.getByRole('button', { name: /cancel booking/i }))
    await user.selectOptions(screen.getByLabelText(/reason for cancelling/i), 'SCHEDULE_CONFLICT')
    await user.type(screen.getByLabelText(/anything else/i), 'Clashes with a meeting')
    await user.click(screen.getByRole('button', { name: /confirm cancellation/i }))

    expect(await screen.findByText(/booking cancelled/i)).toBeInTheDocument()
    expect(mockedCancelAsPatient).toHaveBeenCalledWith(BOOKING_ID, 'a.patient.token', {
      reason: 'SCHEDULE_CONFLICT',
      reasonDetail: 'Clashes with a meeting',
    })
  })

  it('"Keep booking" backs out without calling the API and resets the reason', async () => {
    const user = userEvent.setup()
    render(<CancelBookingButton mode="patient" bookingId={BOOKING_ID} />)

    await user.click(screen.getByRole('button', { name: /cancel booking/i }))
    await user.selectOptions(screen.getByLabelText(/reason for cancelling/i), 'FEELING_BETTER')
    await user.click(screen.getByRole('button', { name: /keep booking/i }))

    expect(mockedCancelAsPatient).not.toHaveBeenCalled()
    expect(screen.getByRole('button', { name: /cancel booking/i })).toBeInTheDocument()
  })

  it('shows the CANCELLATION_CUTOFF_PASSED error message after Confirm', async () => {
    const user = userEvent.setup()
    mockedCancelAsPatient.mockRejectedValueOnce(new BookingCancellationApiError({ error: 'CANCELLATION_CUTOFF_PASSED' }))

    render(<CancelBookingButton mode="patient" bookingId={BOOKING_ID} />)

    await user.click(screen.getByRole('button', { name: /cancel booking/i }))
    await user.selectOptions(screen.getByLabelText(/reason for cancelling/i), 'SCHEDULE_CONFLICT')
    await user.click(screen.getByRole('button', { name: /confirm cancellation/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/less than 2 hours away/i)
  })

  it('shows the BOOKING_NOT_CANCELLABLE error message after Confirm', async () => {
    const user = userEvent.setup()
    mockedCancelAsStaff.mockRejectedValueOnce(new BookingCancellationApiError({ error: 'BOOKING_NOT_CANCELLABLE' }))

    render(<CancelBookingButton mode="staff" clinicId={CLINIC_ID} bookingId={BOOKING_ID} />)

    await user.click(screen.getByRole('button', { name: /cancel booking/i }))
    await user.click(screen.getByRole('button', { name: /^confirm$/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/no longer be cancelled/i)
  })
})
