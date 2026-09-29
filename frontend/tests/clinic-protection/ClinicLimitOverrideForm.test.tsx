// 060-booking-abuse-prevention T064: renders current override (or "none set"), saving a value
// calls the update endpoint, an over-cap value shows a validation message before submit, a
// history view renders past changes.
import type { PropsWithChildren } from 'react'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ClinicLimitOverrideForm } from '../../src/features/clinic-protection/ClinicLimitOverrideForm'
import {
  getLimitOverride,
  getLimitOverrideHistory,
  setLimitOverride,
  type ClinicLimitOverrideHistoryEntry,
} from '../../src/features/clinic-protection/api'
import { storeStaffSession } from '../../src/features/staff-login/token'

vi.mock('../../src/features/clinic-protection/api', async () => {
  const actual = await vi.importActual<typeof import('../../src/features/clinic-protection/api')>(
    '../../src/features/clinic-protection/api',
  )
  return {
    ...actual,
    getLimitOverride: vi.fn(),
    setLimitOverride: vi.fn(),
    getLimitOverrideHistory: vi.fn(),
  }
})

const mockedGetLimitOverride = vi.mocked(getLimitOverride)
const mockedSetLimitOverride = vi.mocked(setLimitOverride)
const mockedGetLimitOverrideHistory = vi.mocked(getLimitOverrideHistory)

const CLINIC_ID = 'clinic-1'

function MemoryRouterWrapper({ children }: PropsWithChildren) {
  return <MemoryRouter>{children}</MemoryRouter>
}

describe('ClinicLimitOverrideForm', () => {
  beforeEach(() => {
    mockedGetLimitOverride.mockReset()
    mockedSetLimitOverride.mockReset()
    mockedGetLimitOverrideHistory.mockReset()
    storeStaffSession({ token: 'staff-jwt', accountId: 'admin-1', email: 'admin@clinic.example' })
  })

  it('shows no override set when none exists, with the global limit as a placeholder', async () => {
    mockedGetLimitOverride.mockResolvedValueOnce({ maxActiveAppointments: null, globalMax: 15 })

    render(<ClinicLimitOverrideForm clinicId={CLINIC_ID} />, { wrapper: MemoryRouterWrapper })

    expect(await screen.findByPlaceholderText(/no override set \(using 15\)/i)).toBeInTheDocument()
  })

  it('shows the current override value when one is set', async () => {
    mockedGetLimitOverride.mockResolvedValueOnce({ maxActiveAppointments: 5, globalMax: 15 })

    render(<ClinicLimitOverrideForm clinicId={CLINIC_ID} />, { wrapper: MemoryRouterWrapper })

    expect(await screen.findByDisplayValue('5')).toBeInTheDocument()
  })

  it('saving a valid value calls the update endpoint', async () => {
    const user = userEvent.setup()
    mockedGetLimitOverride.mockResolvedValueOnce({ maxActiveAppointments: null, globalMax: 15 })
    mockedSetLimitOverride.mockResolvedValueOnce({ maxActiveAppointments: 5, globalMax: 15 })

    render(<ClinicLimitOverrideForm clinicId={CLINIC_ID} />, { wrapper: MemoryRouterWrapper })

    const input = await screen.findByLabelText(/this clinic's limit/i)
    await user.type(input, '5')
    await user.click(screen.getByRole('button', { name: /^save$/i }))

    await waitFor(() => expect(mockedSetLimitOverride).toHaveBeenCalledWith(CLINIC_ID, 5, 'staff-jwt'))
  })

  it('shows a validation message for an over-cap value before submitting', async () => {
    const user = userEvent.setup()
    mockedGetLimitOverride.mockResolvedValueOnce({ maxActiveAppointments: null, globalMax: 15 })

    render(<ClinicLimitOverrideForm clinicId={CLINIC_ID} />, { wrapper: MemoryRouterWrapper })

    const input = await screen.findByLabelText(/this clinic's limit/i)
    await user.type(input, '20')
    await user.click(screen.getByRole('button', { name: /^save$/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/cannot exceed the global limit of 15/i)
    expect(mockedSetLimitOverride).not.toHaveBeenCalled()
  })

  it('renders past changes in the history view', async () => {
    const user = userEvent.setup()
    mockedGetLimitOverride.mockResolvedValueOnce({ maxActiveAppointments: 5, globalMax: 15 })
    const entry: ClinicLimitOverrideHistoryEntry = {
      previousMaxActiveAppointments: null,
      newMaxActiveAppointments: 5,
      changedAt: '2026-09-10T08:00:00Z',
      changedBy: 'admin-1',
    }
    mockedGetLimitOverrideHistory.mockResolvedValueOnce([entry])

    render(<ClinicLimitOverrideForm clinicId={CLINIC_ID} />, { wrapper: MemoryRouterWrapper })

    await screen.findByDisplayValue('5')
    await user.click(screen.getByRole('button', { name: /view change history/i }))

    expect(await screen.findByText(/none.*→.*5/)).toBeInTheDocument()
    expect(mockedGetLimitOverrideHistory).toHaveBeenCalledWith(CLINIC_ID, 'staff-jwt')
  })
})
