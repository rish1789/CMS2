import { useEffect, useState } from 'react'
import { loadSuperAdminSession } from '../super-admin/token'
import {
  getProtectionSettingHistory,
  listProtectionSettings,
  updateProtectionSetting,
  type ProtectionSetting,
  type ProtectionSettingHistoryEntry,
} from './api'
import { ApiError } from '../../lib/apiClient'
import { Badge } from '../../components/Badge'
import { LoadingState } from '../../components/LoadingState'

const GROUPS: { title: string; prefix: string }[] = [
  { title: 'Booking limit', prefix: 'booking-limit.' },
  { title: 'Rate limiting', prefix: 'rate-limit.' },
  { title: 'Admin flagging', prefix: 'flagging.' },
]

function isBoolean(setting: ProtectionSetting): boolean {
  return setting.value === 'true' || setting.value === 'false'
}

function formatInstant(iso: string): string {
  return new Date(iso).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' })
}

function SettingRow({
  setting,
  onSaved,
}: {
  setting: ProtectionSetting
  onSaved: (updated: ProtectionSetting) => void
}) {
  const session = loadSuperAdminSession()
  const [draft, setDraft] = useState(setting.value)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [showHistory, setShowHistory] = useState(false)
  const [history, setHistory] = useState<ProtectionSettingHistoryEntry[] | null>(null)

  useEffect(() => {
    setDraft(setting.value)
  }, [setting.value])

  function save(nextValue: string) {
    if (!session) return
    setSaving(true)
    setError(null)
    updateProtectionSetting(setting.name, nextValue, session.token)
      .then((updated) => {
        onSaved(updated)
        setHistory(null)
        setShowHistory(false)
      })
      .catch((err: unknown) => setError(err instanceof ApiError ? err.message : 'Failed to save this setting'))
      .finally(() => setSaving(false))
  }

  function toggleHistory() {
    if (!session) return
    const next = !showHistory
    setShowHistory(next)
    if (next && !history) {
      getProtectionSettingHistory(setting.name, session.token)
        .then(setHistory)
        .catch(() => {
          // Best-effort only - the setting's current value above is unaffected.
        })
    }
  }

  return (
    <div className="border-b border-gray-100 p-4 last:border-b-0">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="min-w-0">
          <p className="font-mono text-sm text-gray-900">{setting.name}</p>
          <p className="mt-0.5 text-xs text-gray-500">
            {setting.isDefault ? (
              <Badge color="gray">Default</Badge>
            ) : (
              <>
                Last changed {setting.updatedAt ? formatInstant(setting.updatedAt) : ''} by {setting.updatedBy}
              </>
            )}
          </p>
        </div>
        <div className="flex items-center gap-2">
          {isBoolean(setting) ? (
            <label className="flex items-center gap-2 text-sm text-gray-700">
              <input
                type="checkbox"
                checked={draft === 'true'}
                disabled={saving}
                onChange={(e) => {
                  const nextValue = e.target.checked ? 'true' : 'false'
                  setDraft(nextValue)
                  save(nextValue)
                }}
                className="h-4 w-4 rounded border-gray-300 text-indigo-600 focus:ring-indigo-500"
              />
              {draft === 'true' ? 'Enabled' : 'Disabled'}
            </label>
          ) : (
            <>
              <input
                type="number"
                min={1}
                value={draft}
                onChange={(e) => setDraft(e.target.value)}
                className="input w-28"
              />
              <button
                type="button"
                onClick={() => save(draft)}
                disabled={saving || draft === setting.value}
                className="rounded-lg border border-gray-300 px-3 py-1.5 text-sm font-medium text-gray-700 transition-colors duration-150 hover:bg-gray-100 disabled:opacity-50 disabled:pointer-events-none focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-2"
              >
                {saving ? 'Saving…' : 'Save'}
              </button>
            </>
          )}
          <button
            type="button"
            onClick={toggleHistory}
            className="text-sm font-medium text-indigo-600 transition-colors duration-150 hover:text-indigo-700"
          >
            History
          </button>
        </div>
      </div>
      {error && (
        <p role="alert" className="mt-2 rounded-md bg-red-50 p-2.5 text-sm text-red-700">
          {error}
        </p>
      )}
      {showHistory &&
        (history === null ? (
          <LoadingState />
        ) : history.length === 0 ? (
          <p className="mt-2 text-sm text-gray-500">No changes recorded yet.</p>
        ) : (
          <ul className="mt-2 space-y-1 text-sm text-gray-600">
            {history.map((entry, i) => (
              <li key={i}>
                {formatInstant(entry.changedAt)}: {entry.previousValue ?? 'default'} → {entry.newValue}
                <span className="text-gray-400"> ({entry.changedBy})</span>
              </li>
            ))}
          </ul>
        ))}
    </div>
  )
}

export function ProtectionSettingsPage() {
  const session = loadSuperAdminSession()
  const token = session?.token
  const [settings, setSettings] = useState<ProtectionSetting[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!token) return
    listProtectionSettings(token)
      .then(setSettings)
      .catch((err: unknown) => setError(err instanceof Error ? err.message : 'Failed to load settings'))
  }, [token])

  if (!session) {
    return (
      <div className="mx-auto max-w-2xl rounded-lg border border-gray-200 bg-white p-6 shadow-sm">
        <p className="text-sm text-gray-600">Please sign in as the Super Admin to manage booking protection settings.</p>
      </div>
    )
  }

  function handleSaved(updated: ProtectionSetting) {
    setSettings((current) => (current ?? []).map((s) => (s.name === updated.name ? updated : s)))
  }

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <div>
        <h1 className="text-lg font-semibold text-gray-900">Booking protection settings</h1>
        <p className="mt-1 text-sm text-gray-600">
          Changes take effect on the very next relevant request across the platform - no restart needed.
        </p>
      </div>

      {error && (
        <p role="alert" className="rounded-md bg-red-50 p-3 text-sm text-red-700">
          {error}
        </p>
      )}

      {settings === null ? (
        <LoadingState variant="list" rows={4} />
      ) : (
        GROUPS.map((group) => {
          const groupSettings = settings.filter((s) => s.name.startsWith(group.prefix))
          if (groupSettings.length === 0) return null
          return (
            <div key={group.prefix} className="space-y-2">
              <h2 className="text-xs font-semibold tracking-wide text-gray-500 uppercase">{group.title}</h2>
              <div className="rounded-lg border border-gray-200 bg-white">
                {groupSettings.map((setting) => (
                  <SettingRow key={setting.name} setting={setting} onSaved={handleSaved} />
                ))}
              </div>
            </div>
          )
        })
      )}
    </div>
  )
}
