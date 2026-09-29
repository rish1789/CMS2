// 060-booking-abuse-prevention T055: renders every setting, editing and saving one calls the
// update endpoint, expanding a setting's history calls the history endpoint and renders its
// entries.
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ProtectionSettingsPage } from '../../src/features/admin-protection-settings/ProtectionSettingsPage'
import {
  getProtectionSettingHistory,
  listProtectionSettings,
  updateProtectionSetting,
  type ProtectionSetting,
} from '../../src/features/admin-protection-settings/api'
import { storeSuperAdminSession } from '../../src/features/super-admin/token'

vi.mock('../../src/features/admin-protection-settings/api', async () => {
  const actual = await vi.importActual<typeof import('../../src/features/admin-protection-settings/api')>(
    '../../src/features/admin-protection-settings/api',
  )
  return {
    ...actual,
    listProtectionSettings: vi.fn(),
    updateProtectionSetting: vi.fn(),
    getProtectionSettingHistory: vi.fn(),
  }
})

const mockedList = vi.mocked(listProtectionSettings)
const mockedUpdate = vi.mocked(updateProtectionSetting)
const mockedHistory = vi.mocked(getProtectionSettingHistory)

const SETTINGS: ProtectionSetting[] = [
  { name: 'booking-limit.global-max-active', value: '15', isDefault: true, updatedAt: null, updatedBy: null },
  { name: 'booking-limit.enabled', value: 'true', isDefault: true, updatedAt: null, updatedBy: null },
  { name: 'rate-limit.max-attempts', value: '8', isDefault: true, updatedAt: null, updatedBy: null },
  { name: 'flagging.enabled', value: 'true', isDefault: true, updatedAt: null, updatedBy: null },
]

describe('ProtectionSettingsPage', () => {
  beforeEach(() => {
    mockedList.mockReset()
    mockedUpdate.mockReset()
    mockedHistory.mockReset()
    storeSuperAdminSession({ token: 'super-admin-jwt', username: 'super-admin' })
  })

  it('renders every setting, grouped by category', async () => {
    mockedList.mockResolvedValueOnce(SETTINGS)

    render(<ProtectionSettingsPage />)

    expect(await screen.findByText('booking-limit.global-max-active')).toBeInTheDocument()
    expect(screen.getByText('booking-limit.enabled')).toBeInTheDocument()
    expect(screen.getByText('rate-limit.max-attempts')).toBeInTheDocument()
    expect(screen.getByText('flagging.enabled')).toBeInTheDocument()
  })

  it('editing and saving a numeric setting calls the update endpoint', async () => {
    const user = userEvent.setup()
    mockedList.mockResolvedValueOnce(SETTINGS)
    mockedUpdate.mockResolvedValueOnce({
      name: 'rate-limit.max-attempts',
      value: '5',
      isDefault: false,
      updatedAt: '2026-09-10T08:00:00Z',
      updatedBy: 'super-admin',
    })

    render(<ProtectionSettingsPage />)

    await screen.findByText('rate-limit.max-attempts')
    const inputs = screen.getAllByRole('spinbutton')
    const rateLimitInput = inputs.find((el) => (el as HTMLInputElement).value === '8')!
    await user.clear(rateLimitInput)
    await user.type(rateLimitInput, '5')

    const saveButtons = screen.getAllByRole('button', { name: /^save$/i })
    await user.click(saveButtons[saveButtons.length - 1])

    await waitFor(() =>
      expect(mockedUpdate).toHaveBeenCalledWith('rate-limit.max-attempts', '5', 'super-admin-jwt'),
    )
  })

  it('toggling a boolean setting saves immediately', async () => {
    const user = userEvent.setup()
    mockedList.mockResolvedValueOnce(SETTINGS)
    mockedUpdate.mockResolvedValueOnce({
      name: 'booking-limit.enabled',
      value: 'false',
      isDefault: false,
      updatedAt: '2026-09-10T08:00:00Z',
      updatedBy: 'super-admin',
    })

    render(<ProtectionSettingsPage />)

    await screen.findByText('booking-limit.enabled')
    const checkboxes = screen.getAllByRole('checkbox')
    await user.click(checkboxes[0])

    await waitFor(() =>
      expect(mockedUpdate).toHaveBeenCalledWith('booking-limit.enabled', 'false', 'super-admin-jwt'),
    )
  })

  it('expanding a setting history calls the history endpoint and renders its entries', async () => {
    const user = userEvent.setup()
    mockedList.mockResolvedValueOnce(SETTINGS)
    mockedHistory.mockResolvedValueOnce([
      { previousValue: '15', newValue: '10', changedAt: '2026-09-10T08:00:00Z', changedBy: 'super-admin' },
    ])

    render(<ProtectionSettingsPage />)

    await screen.findByText('booking-limit.global-max-active')
    const historyButtons = screen.getAllByRole('button', { name: /history/i })
    await user.click(historyButtons[0])

    await waitFor(() => expect(mockedHistory).toHaveBeenCalledWith('booking-limit.global-max-active', 'super-admin-jwt'))
    expect(await screen.findByText(/15 → 10/)).toBeInTheDocument()
  })
})
