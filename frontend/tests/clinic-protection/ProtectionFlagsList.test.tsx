// 060-booking-abuse-prevention T045: renders outstanding flags with reason/timestamp;
// resolving one removes it from the default outstanding view.
import type { PropsWithChildren } from 'react'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ProtectionFlagsList } from '../../src/features/clinic-protection/ProtectionFlagsList'
import { listFlags, resolveFlag, type ProtectionFlag } from '../../src/features/clinic-protection/api'
import { storeStaffSession } from '../../src/features/staff-login/token'

vi.mock('../../src/features/clinic-protection/api', async () => {
  const actual = await vi.importActual<typeof import('../../src/features/clinic-protection/api')>(
    '../../src/features/clinic-protection/api',
  )
  return { ...actual, listFlags: vi.fn(), resolveFlag: vi.fn() }
})

const mockedListFlags = vi.mocked(listFlags)
const mockedResolveFlag = vi.mocked(resolveFlag)

const CLINIC_ID = 'clinic-1'

const FLAG: ProtectionFlag = {
  id: 'flag-1',
  patientAccountId: 'patient-1',
  patientDisplayName: 'jane@example.com',
  signalType: 'REPEATED_CANCELLATIONS',
  reason: 'Cancelled 5 appointments in the last 30 days (threshold: 4).',
  detectedAt: '2026-09-10T08:00:00Z',
  status: 'OUTSTANDING',
  resolvedAt: null,
  resolvedBy: null,
}

describe('ProtectionFlagsList', () => {
  beforeEach(() => {
    mockedListFlags.mockReset()
    mockedResolveFlag.mockReset()
    storeStaffSession({ token: 'staff-jwt', accountId: 'admin-1', email: 'admin@clinic.example' })
  })

  it('renders outstanding flags with reason and detected timestamp', async () => {
    mockedListFlags.mockResolvedValueOnce({ flags: [FLAG], page: 0, pageSize: 20, totalCount: 1 })

    render(<ProtectionFlagsList clinicId={CLINIC_ID} />, { wrapper: MemoryRouterWrapper })

    expect(await screen.findByText('jane@example.com')).toBeInTheDocument()
    expect(screen.getByText(/cancelled 5 appointments/i)).toBeInTheDocument()
    expect(screen.getByText(/repeated cancellations/i)).toBeInTheDocument()
    expect(mockedListFlags).toHaveBeenCalledWith(CLINIC_ID, 'staff-jwt', { status: 'OUTSTANDING' })
  })

  it('resolving a flag removes it from the outstanding view', async () => {
    const user = userEvent.setup()
    mockedListFlags.mockResolvedValueOnce({ flags: [FLAG], page: 0, pageSize: 20, totalCount: 1 })
    mockedResolveFlag.mockResolvedValueOnce({
      ...FLAG,
      status: 'RESOLVED',
      resolvedAt: '2026-09-11T08:00:00Z',
      resolvedBy: 'admin-1',
    })

    render(<ProtectionFlagsList clinicId={CLINIC_ID} />, { wrapper: MemoryRouterWrapper })

    expect(await screen.findByText('jane@example.com')).toBeInTheDocument()
    await user.click(screen.getByText('Mark resolved'))

    await waitFor(() => expect(mockedResolveFlag).toHaveBeenCalledWith(CLINIC_ID, 'flag-1', 'staff-jwt'))
    await waitFor(() => expect(screen.queryByText('jane@example.com')).not.toBeInTheDocument())
  })

  it('shows an empty state when there are no outstanding flags', async () => {
    mockedListFlags.mockResolvedValueOnce({ flags: [], page: 0, pageSize: 20, totalCount: 0 })

    render(<ProtectionFlagsList clinicId={CLINIC_ID} />, { wrapper: MemoryRouterWrapper })

    expect(await screen.findByText(/no outstanding flags/i)).toBeInTheDocument()
  })
})

// Local since the component only needs routing for its "Clinic limit" link, not full app routes.
function MemoryRouterWrapper({ children }: PropsWithChildren) {
  return <MemoryRouter>{children}</MemoryRouter>
}
