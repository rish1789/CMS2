import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { BatchCancelBar } from '../../src/features/booking-cancellation/BatchCancelBar'
import { cancelBookingsBatch, BatchCancelApiError } from '../../src/features/booking-cancellation/api'
import { storeStaffSession } from '../../src/features/staff-login/token'

vi.mock('../../src/features/booking-cancellation/api', async () => {
  const actual = await vi.importActual<typeof import('../../src/features/booking-cancellation/api')>(
    '../../src/features/booking-cancellation/api',
  )
  return { ...actual, cancelBookingsBatch: vi.fn() }
})

const mockedCancelBookingsBatch = vi.mocked(cancelBookingsBatch)

const CLINIC_ID = 'clinic-1'
const SESSION_ID = 'session-1'

describe('BatchCancelBar', () => {
  beforeEach(() => {
    mockedCancelBookingsBatch.mockReset()
    storeStaffSession({ token: 'a.jwt.token', accountId: 'acct-1', email: 'ops@example.com' })
  })

  it('renders nothing when no slots are selected', () => {
    const { container } = render(
      <BatchCancelBar
        clinicId={CLINIC_ID}
        sessionId={SESSION_ID}
        selectedBookingIds={[]}
        onCancelled={vi.fn()}
        onClear={vi.fn()}
      />,
    )

    expect(container).toBeEmptyDOMElement()
  })

  it('shows the selection count and cancels the selected bookings', async () => {
    const user = userEvent.setup()
    const onCancelled = vi.fn()
    mockedCancelBookingsBatch.mockResolvedValueOnce({ cancelled: ['booking-1', 'booking-2'], failed: [] })

    render(
      <BatchCancelBar
        clinicId={CLINIC_ID}
        sessionId={SESSION_ID}
        selectedBookingIds={['booking-1', 'booking-2']}
        onCancelled={onCancelled}
        onClear={vi.fn()}
      />,
    )

    expect(screen.getByText('2 slots selected')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /cancel slots/i }))

    expect(mockedCancelBookingsBatch).toHaveBeenCalledWith(
      CLINIC_ID,
      SESSION_ID,
      ['booking-1', 'booking-2'],
      'a.jwt.token',
    )
    expect(onCancelled).toHaveBeenCalledWith({ cancelled: ['booking-1', 'booking-2'], failed: [] })
  })

  it('calls onClear when Clear is clicked', async () => {
    const user = userEvent.setup()
    const onClear = vi.fn()

    render(
      <BatchCancelBar
        clinicId={CLINIC_ID}
        sessionId={SESSION_ID}
        selectedBookingIds={['booking-1']}
        onCancelled={vi.fn()}
        onClear={onClear}
      />,
    )

    await user.click(screen.getByRole('button', { name: /clear/i }))

    expect(onClear).toHaveBeenCalledOnce()
  })

  it('shows an error message when the batch call fails (e.g. a Doctor caller)', async () => {
    const user = userEvent.setup()
    mockedCancelBookingsBatch.mockRejectedValueOnce(new BatchCancelApiError({ error: 'FORBIDDEN' }))

    render(
      <BatchCancelBar
        clinicId={CLINIC_ID}
        sessionId={SESSION_ID}
        selectedBookingIds={['booking-1']}
        onCancelled={vi.fn()}
        onClear={vi.fn()}
      />,
    )

    await user.click(screen.getByRole('button', { name: /cancel slot/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/only front-desk operations staff or a clinicadmin/i)
  })
})
