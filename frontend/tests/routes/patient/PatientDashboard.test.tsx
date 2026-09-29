import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { PatientDashboard } from '../../../src/routes/patient/PatientDashboard'
import { listMyBookings } from '../../../src/features/patient-bookings/api'
import { storePatientSession } from '../../../src/features/patient-account/token'

vi.mock('../../../src/features/patient-bookings/api', async () => {
  const actual = await vi.importActual<typeof import('../../../src/features/patient-bookings/api')>(
    '../../../src/features/patient-bookings/api',
  )
  return { ...actual, listMyBookings: vi.fn() }
})

const mockedListMyBookings = vi.mocked(listMyBookings)

function renderDashboard() {
  render(
    <MemoryRouter>
      <PatientDashboard />
    </MemoryRouter>,
  )
}

// 056-design-copy-quality-pass (2026-09-16 dashboard rebuild): PatientAccount has no name
// field (see token.ts's deriveDisplayNameFromEmail) - the greeting is derived from the
// authenticated email's local part, not a fabricated name.
describe('PatientDashboard', () => {
  beforeEach(() => {
    sessionStorage.clear()
    mockedListMyBookings.mockReset()
    mockedListMyBookings.mockResolvedValue({ bookings: [], page: 0, pageSize: 50, totalCount: 0 })
  })

  it('greets the signed-in patient by a name derived from their email', () => {
    storePatientSession({ token: 'jwt', patientAccountId: 'account-1', email: 'priya.sharma@example.com' })
    renderDashboard()

    expect(screen.getByRole('heading', { level: 1, name: 'Welcome back, Priya Sharma.' })).toBeInTheDocument()
  })

  it('falls back to a plain greeting when there is no session', () => {
    renderDashboard()

    expect(screen.getByRole('heading', { level: 1, name: 'Welcome back.' })).toBeInTheDocument()
  })

  it('renders the three action cards, in order, linking to the real routes', () => {
    renderDashboard()

    const headings = screen.getAllByRole('heading', { level: 2 })
    expect(headings.map((heading) => heading.textContent?.replace('→', '').trim())).toEqual([
      'Find a doctor',
      'My bookings',
      'My clinics',
    ])
    expect(screen.getByRole('link', { name: /Find a doctor/ })).toHaveAttribute('href', '/discover')
    expect(screen.getByRole('link', { name: /My bookings/ })).toHaveAttribute('href', '/patient/bookings')
    expect(screen.getByRole('link', { name: /My clinics/ })).toHaveAttribute('href', '/patient/clinics')
  })
})
