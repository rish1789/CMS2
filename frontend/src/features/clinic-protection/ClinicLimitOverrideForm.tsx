import { useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { loadStaffSession } from '../staff-login/token'
import {
  deleteLimitOverride,
  getLimitOverride,
  getLimitOverrideHistory,
  setLimitOverride,
  type ClinicLimitOverride,
  type ClinicLimitOverrideHistoryEntry,
} from './api'
import { ApiError } from '../../lib/apiClient'
import { LoadingState } from '../../components/LoadingState'

export interface ClinicLimitOverrideFormProps {
  clinicId: string
}

function formatInstant(iso: string): string {
  return new Date(iso).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' })
}

export function ClinicLimitOverrideForm({ clinicId }: ClinicLimitOverrideFormProps) {
  const session = loadStaffSession()
  const token = session?.token
  const [current, setCurrent] = useState<ClinicLimitOverride | null>(null)
  const [inputValue, setInputValue] = useState('')
  const [loadError, setLoadError] = useState<string | null>(null)
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [showHistory, setShowHistory] = useState(false)
  const [history, setHistory] = useState<ClinicLimitOverrideHistoryEntry[] | null>(null)

  useEffect(() => {
    if (!token) return
    getLimitOverride(clinicId, token)
      .then((result) => {
        setCurrent(result)
        setInputValue(result.maxActiveAppointments != null ? String(result.maxActiveAppointments) : '')
      })
      .catch((err: unknown) => setLoadError(err instanceof Error ? err.message : 'Failed to load the current limit'))
  }, [clinicId, token])

  function toggleHistory() {
    if (!session) return
    const next = !showHistory
    setShowHistory(next)
    if (next && !history) {
      getLimitOverrideHistory(clinicId, session.token)
        .then(setHistory)
        .catch(() => {
          // Best-effort only - the form above still works without history.
        })
    }
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!session) return
    const parsed = Number(inputValue)
    if (!Number.isInteger(parsed) || parsed <= 0) {
      setFormError('Enter a positive whole number.')
      return
    }
    if (current && parsed > current.globalMax) {
      setFormError(`The clinic limit cannot exceed the global limit of ${current.globalMax}.`)
      return
    }
    setSubmitting(true)
    setFormError(null)
    setLimitOverride(clinicId, parsed, session.token)
      .then((result) => {
        setCurrent(result)
        setHistory(null)
        setShowHistory(false)
      })
      .catch((err: unknown) => setFormError(err instanceof ApiError ? err.message : 'Failed to save the limit'))
      .finally(() => setSubmitting(false))
  }

  function handleRemove() {
    if (!session) return
    setSubmitting(true)
    setFormError(null)
    deleteLimitOverride(clinicId, session.token)
      .then(() => {
        setCurrent((prev) => (prev ? { ...prev, maxActiveAppointments: null } : prev))
        setInputValue('')
        setHistory(null)
        setShowHistory(false)
      })
      .catch((err: unknown) => setFormError(err instanceof ApiError ? err.message : 'Failed to remove the override'))
      .finally(() => setSubmitting(false))
  }

  if (!session) {
    return (
      <div className="mx-auto max-w-md rounded-lg border border-gray-200 bg-white p-6 shadow-sm">
        <p className="text-sm text-gray-600">Please sign in as a ClinicAdmin to manage this clinic's appointment limit.</p>
      </div>
    )
  }

  if (loadError) {
    return (
      <div className="mx-auto max-w-md rounded-md bg-red-50 p-4">
        <p role="alert" className="text-sm text-red-700">
          {loadError}
        </p>
      </div>
    )
  }

  if (!current) {
    return <LoadingState />
  }

  return (
    <div className="mx-auto max-w-md space-y-6 rounded-xl border border-gray-200 bg-white p-6 shadow-md">
      <div>
        <Link
          to={`/staff/clinics/${clinicId}/protection`}
          className="text-sm font-medium text-indigo-600 transition-colors duration-150 hover:text-indigo-700"
        >
          ← Back to flags
        </Link>
        <h1 className="mt-2 text-lg font-semibold text-gray-900">Clinic appointment limit</h1>
        <p className="mt-1 text-sm text-gray-600">
          Global limit: <span className="font-medium text-gray-900">{current.globalMax}</span>. Set a stricter cap for
          this clinic only, or leave it unset to use the global limit.
        </p>
      </div>

      {formError && (
        <p role="alert" className="rounded-md bg-red-50 p-3 text-sm text-red-700">
          {formError}
        </p>
      )}

      <form onSubmit={handleSubmit} className="space-y-4">
        <div>
          <label htmlFor="maxActiveAppointments" className="block text-sm font-medium text-gray-700">
            This clinic's limit
          </label>
          <input
            id="maxActiveAppointments"
            type="number"
            min={1}
            value={inputValue}
            onChange={(e) => setInputValue(e.target.value)}
            placeholder={`No override set (using ${current.globalMax})`}
            className="input mt-1"
          />
        </div>
        <div className="flex gap-2">
          <button
            type="submit"
            disabled={submitting || inputValue === ''}
            className="rounded-lg bg-indigo-600 px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition-all duration-150 ease-out hover:bg-indigo-500 hover:shadow-md active:scale-[0.98] disabled:opacity-50 disabled:pointer-events-none disabled:active:scale-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-2"
          >
            {submitting ? 'Saving…' : 'Save'}
          </button>
          {current.maxActiveAppointments != null && (
            <button
              type="button"
              onClick={handleRemove}
              disabled={submitting}
              className="rounded-lg border border-gray-300 px-4 py-2.5 text-sm font-semibold text-gray-700 transition-colors duration-150 hover:bg-gray-100 disabled:opacity-50 disabled:pointer-events-none focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-2"
            >
              Remove override
            </button>
          )}
        </div>
      </form>

      <div>
        <button
          type="button"
          onClick={toggleHistory}
          className="text-sm font-medium text-indigo-600 transition-colors duration-150 hover:text-indigo-700"
        >
          {showHistory ? 'Hide change history' : 'View change history'}
        </button>
        {showHistory &&
          (history === null ? (
            <LoadingState />
          ) : history.length === 0 ? (
            <p className="mt-2 text-sm text-gray-500">No changes recorded yet.</p>
          ) : (
            <ul className="mt-2 space-y-1.5 text-sm text-gray-600">
              {history.map((entry, i) => (
                <li key={i}>
                  {formatInstant(entry.changedAt)}:{' '}
                  {entry.previousMaxActiveAppointments ?? 'none'} → {entry.newMaxActiveAppointments ?? 'none'}
                  <span className="text-gray-400"> ({entry.changedBy})</span>
                </li>
              ))}
            </ul>
          ))}
      </div>
    </div>
  )
}
