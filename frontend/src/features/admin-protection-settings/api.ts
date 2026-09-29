// Client for /api/v1/admin/protection-settings
// See specs/060-booking-abuse-prevention/contracts/booking-protection.md #4

import { apiRequest } from '../../lib/apiClient'

export interface ProtectionSetting {
  name: string
  value: string
  isDefault: boolean
  updatedAt: string | null
  updatedBy: string | null
}

export interface ProtectionSettingHistoryEntry {
  previousValue: string | null
  newValue: string
  changedAt: string
  changedBy: string
}

export type AdminProtectionSettingsErrorBody =
  | { error: 'UNAUTHORIZED'; message?: string }
  | { error: 'UNRECOGNIZED_SETTING'; message?: string }
  | { error: 'INVALID_SETTING_VALUE'; message?: string }
  | { error: 'SETTING_NOT_FOUND'; message?: string }

function defaultMessageFor(body: unknown): string {
  const error = (body as AdminProtectionSettingsErrorBody | undefined)?.error
  switch (error) {
    case 'UNAUTHORIZED':
      return 'Your session has expired. Please sign in again.'
    case 'UNRECOGNIZED_SETTING':
      return 'This is not a recognized setting name.'
    case 'INVALID_SETTING_VALUE':
      return 'That value is not valid for this setting.'
    case 'SETTING_NOT_FOUND':
      return 'This setting could not be found.'
    default:
      return 'Something went wrong. Please try again.'
  }
}

export async function listProtectionSettings(token: string): Promise<ProtectionSetting[]> {
  return apiRequest<ProtectionSetting[]>('/api/v1/admin/protection-settings', {
    token,
    fallbackMessage: defaultMessageFor,
  })
}

export async function updateProtectionSetting(
  name: string,
  value: string,
  token: string,
): Promise<ProtectionSetting> {
  return apiRequest<ProtectionSetting>(`/api/v1/admin/protection-settings/${name}`, {
    method: 'PUT',
    token,
    body: { value },
    fallbackMessage: defaultMessageFor,
  })
}

export async function getProtectionSettingHistory(
  name: string,
  token: string,
): Promise<ProtectionSettingHistoryEntry[]> {
  return apiRequest<ProtectionSettingHistoryEntry[]>(`/api/v1/admin/protection-settings/${name}/history`, {
    token,
    fallbackMessage: defaultMessageFor,
  })
}
