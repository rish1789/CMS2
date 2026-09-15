import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { MyBookingsPage } from '../../src/routes/patient/PatientPages'
import { listMyBookings } from '../../src/features/patient-bookings/api'
import { listMyWaitlistEntries } from '../../src/features/waitlist/api'
import { storePatientSession } from '../../src/features/patient-account/token'

vi.mock('../../src/features/patient-bookings/api', async () => {
  const actual = await vi.importActual<typeof import('../../src/features/patient-bookings/api')>(
    '../../src/features/patient-bookings/api',
  )
  return { ...actual, listMyBookings: vi.fn() }
})

vi.mock('../../src/features/waitlist/api', async () => {
  const actual = await vi.importActual<typeof import('../../src/features/waitlist/api')>('../../src/features/waitlist/api')
  return { ...actual, listMyWaitlistEntries: vi.fn() }
})

const mockedListMyBookings = vi.mocked(listMyBookings)
const mockedListMyWaitlistEntries = vi.mocked(listMyWaitlistEntries)

function renderAt(initialPath: string) {
  return render(
    <MemoryRouter initialEntries={[initialPath]}>
      <Routes>
        <Route path="/patient/bookings" element={<MyBookingsPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('MyBookingsPage', () => {
  beforeEach(() => {
    mockedListMyBookings.mockReset().mockResolvedValue({ bookings: [], page: 0, pageSize: 20, totalCount: 0 })
    mockedListMyWaitlistEntries.mockReset().mockResolvedValue([])
    storePatientSession({ token: 'a.jwt.token', patientAccountId: 'acct-1', email: 'patient@example.com' })
  })

  it('defaults to the Bookings tab and fetches bookings, not waitlist entries', async () => {
    renderAt('/patient/bookings')

    expect(screen.getByRole('tab', { name: 'Bookings' })).toHaveAttribute('aria-selected', 'true')
    expect(screen.getByRole('tab', { name: 'Waitlist' })).toHaveAttribute('aria-selected', 'false')
    await waitFor(() => expect(mockedListMyBookings).toHaveBeenCalled())
    expect(mockedListMyWaitlistEntries).not.toHaveBeenCalled()
  })

  it('opens directly on the Waitlist tab when linked with ?tab=waitlist', async () => {
    renderAt('/patient/bookings?tab=waitlist')

    expect(screen.getByRole('tab', { name: 'Waitlist' })).toHaveAttribute('aria-selected', 'true')
    await waitFor(() => expect(mockedListMyWaitlistEntries).toHaveBeenCalled())
    expect(mockedListMyBookings).not.toHaveBeenCalled()
  })

  it('switches to the Waitlist tab and fetches its data when clicked', async () => {
    const user = userEvent.setup()
    renderAt('/patient/bookings')
    await waitFor(() => expect(mockedListMyBookings).toHaveBeenCalled())

    await user.click(screen.getByRole('tab', { name: 'Waitlist' }))

    expect(screen.getByRole('tab', { name: 'Waitlist' })).toHaveAttribute('aria-selected', 'true')
    await waitFor(() => expect(mockedListMyWaitlistEntries).toHaveBeenCalled())
    expect(await screen.findByText(/not on any waitlist/i)).toBeInTheDocument()
  })

  it('shows the shared "My bookings" heading regardless of which tab is active', async () => {
    renderAt('/patient/bookings')
    expect(screen.getByRole('heading', { name: 'My bookings' })).toBeInTheDocument()
  })
})
