import { useEffect, useState, type FormEvent } from 'react'
import {
  createAppointmentType,
  listAppointmentTypes,
  setDefaultFee,
  AppointmentTypeApiError,
  type AppointmentTypeResponse,
} from './api'
import { loadStaffSession } from '../staff-login/token'
import { ListSkeleton } from '../../components/ListSkeleton'

// _diagnostics [HIGH] - [APPOINTMENT_TYPE_CONFIG] - [MISSING_UI]
export interface AppointmentTypeConfigFormProps {
  doctorProfileId: string
}

export function AppointmentTypeConfigForm({ doctorProfileId }: AppointmentTypeConfigFormProps) {
  const [session] = useState(() => loadStaffSession())
  const [types, setTypes] = useState<AppointmentTypeResponse[] | null>(null)
  const [name, setName] = useState('')
  const [feeOverride, setFeeOverride] = useState('')
  const [defaultFeeAmount, setDefaultFeeAmount] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  function refresh() {
    if (!session) return
    listAppointmentTypes(doctorProfileId, session.token)
      .then(setTypes)
      .catch((err: unknown) => setError(err instanceof Error ? err.message : 'Failed to load appointment types.'))
  }

  useEffect(refresh, [session, doctorProfileId])

  async function handleCreate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!session) return
    setSubmitting(true)
    setError(null)
    setNotice(null)

    try {
      await createAppointmentType(
        doctorProfileId,
        { name, feeOverride: feeOverride.trim() === '' ? undefined : Number(feeOverride) },
        session.token,
      )
      setName('')
      setFeeOverride('')
      setNotice('Appointment type added.')
      refresh()
    } catch (err) {
      setError(err instanceof AppointmentTypeApiError ? err.message : 'Something went wrong. Please try again.')
    } finally {
      setSubmitting(false)
    }
  }

  async function handleSetDefaultFee(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!session) return
    setSubmitting(true)
    setError(null)
    setNotice(null)

    try {
      await setDefaultFee(doctorProfileId, Number(defaultFeeAmount), session.token)
      setNotice('Default fee updated.')
    } catch (err) {
      setError(err instanceof AppointmentTypeApiError ? err.message : 'Something went wrong. Please try again.')
    } finally {
      setSubmitting(false)
    }
  }

  if (!session) {
    return (
      <div className="mx-auto max-w-md rounded-xl border border-gray-200 bg-white p-6 shadow-sm">
        <p className="text-sm text-gray-600">Please sign in as staff to manage appointment types.</p>
      </div>
    )
  }

  // staff-console-audit-2026-09-10 P2: this used to be "naked" - no card, no max-w, no h1 -
  // unlike every sibling staff-tool form. Also reordered: Default fee now comes first, since
  // every appointment type without its own override uses that default - the form that produces
  // the fallback value belongs before the form that depends on it, not after.
  return (
    <div className="mx-auto max-w-md space-y-6 rounded-xl border border-gray-200 bg-white p-6 shadow-md">
      <h1 className="text-lg font-semibold text-gray-900">Appointment types</h1>

      {error && (
        <p role="alert" className="rounded-md bg-red-50 p-3 text-sm text-red-700">
          {error}
        </p>
      )}
      {notice && <p className="rounded-md bg-green-50 p-3 text-sm text-green-700">{notice}</p>}

      <section>
        <h2 className="text-sm font-medium text-gray-900">Default fee</h2>
        <form onSubmit={handleSetDefaultFee} className="mt-2 flex items-end gap-2" aria-label="Set default fee">
          <div>
            <label htmlFor="defaultFeeAmount" className="block text-sm font-medium text-gray-700">
              Amount
            </label>
            <input
              id="defaultFeeAmount"
              type="number"
              step="0.01"
              min="0"
              required
              value={defaultFeeAmount}
              onChange={(e) => setDefaultFeeAmount(e.target.value)}
              className="input mt-1"
            />
          </div>
          <button
            type="submit"
            disabled={submitting}
            className="rounded-lg bg-indigo-600 px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition-all duration-150 ease-out hover:bg-indigo-500 hover:shadow-md active:scale-[0.98] disabled:opacity-50 disabled:pointer-events-none disabled:active:scale-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-2"
          >
            {submitting ? 'Saving…' : 'Set default fee'}
          </button>
        </form>
      </section>

      <section className="border-t border-gray-100 pt-6">
        <h2 className="text-sm font-medium text-gray-900">Appointment types</h2>
        {types === null ? (
          <div className="mt-2">
            <ListSkeleton rows={2} />
          </div>
        ) : types.length === 0 ? (
          <p className="mt-2 text-sm text-gray-600">No appointment types yet.</p>
        ) : (
          <ul className="mt-2 space-y-1.5">
            {types.map((type) => (
              <li
                key={type.id}
                className="flex items-center justify-between gap-3 rounded-lg border border-gray-200 bg-gray-50 px-3 py-2"
              >
                <span className="text-sm font-medium text-gray-900">{type.name}</span>
                <span className="shrink-0 text-sm tabular-nums text-gray-600">
                  {type.feeOverride !== null ? (
                    <>₹{type.feeOverride.toFixed(2)}</>
                  ) : (
                    <span className="text-gray-400">Uses default fee</span>
                  )}
                </span>
              </li>
            ))}
          </ul>
        )}

        <form onSubmit={handleCreate} className="mt-3 space-y-3" aria-label="Add appointment type">
          <div>
            <label htmlFor="appointmentTypeName" className="block text-sm font-medium text-gray-700">
              Name
            </label>
            <input
              id="appointmentTypeName"
              required
              value={name}
              onChange={(e) => setName(e.target.value)}
              className="input mt-1"
            />
          </div>
          <div>
            <label htmlFor="feeOverride" className="block text-sm font-medium text-gray-700">
              Fee override (optional — leave blank to use the default fee)
            </label>
            <input
              id="feeOverride"
              type="number"
              step="0.01"
              min="0"
              value={feeOverride}
              onChange={(e) => setFeeOverride(e.target.value)}
              className="input mt-1"
            />
          </div>
          <button
            type="submit"
            disabled={submitting}
            className="w-full rounded-lg bg-indigo-600 px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition-all duration-150 ease-out hover:bg-indigo-500 hover:shadow-md active:scale-[0.98] disabled:opacity-50 disabled:pointer-events-none disabled:active:scale-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-2"
          >
            {submitting ? 'Adding…' : 'Add appointment type'}
          </button>
        </form>
      </section>
    </div>
  )
}
