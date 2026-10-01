import { useEffect, useState, type FormEvent, type ReactNode } from 'react'
import {
  createAppointmentType,
  getClinicFees,
  removeClinicTypePrice,
  renameAppointmentType,
  setClinicDefaultFee,
  setClinicTypePrice,
  AppointmentTypeApiError,
  type ClinicAppointmentTypeFee,
  type ClinicDoctorFees,
} from './api'
import { loadStaffSession } from '../staff-login/token'
import { ListSkeleton } from '../../components/ListSkeleton'

function errorMessage(err: unknown): string {
  return err instanceof AppointmentTypeApiError ? err.message : 'Something went wrong. Please try again.'
}

function formatFee(amount: number): string {
  return `₹${amount.toFixed(2)}`
}

function RowButtons({
  submitting,
  onCancel,
  children,
}: {
  submitting: boolean
  onCancel: () => void
  children?: ReactNode
}) {
  return (
    <div className="flex gap-2">
      <button
        type="submit"
        disabled={submitting}
        className="rounded-lg bg-indigo-600 px-3 py-1.5 text-xs font-semibold text-white shadow-sm transition-all duration-150 ease-out hover:bg-indigo-500 disabled:opacity-50"
      >
        {submitting ? 'Saving…' : 'Save'}
      </button>
      {children}
      <button
        type="button"
        onClick={onCancel}
        disabled={submitting}
        className="rounded-lg bg-gray-100 px-3 py-1.5 text-xs font-semibold text-gray-700 transition-all duration-150 ease-out hover:bg-gray-200 disabled:opacity-50"
      >
        Cancel
      </button>
    </div>
  )
}

// real-bug-fix 2026-09-17: inline edit row for a single AppointmentType - fixes a real
// mistake-recovery gap (create/list had no way to correct a mistyped name afterward).
function EditAppointmentTypeRow({
  type,
  doctorProfileId,
  token,
  onSaved,
  onCancel,
}: {
  type: ClinicAppointmentTypeFee
  doctorProfileId: string
  token: string
  onSaved: () => void
  onCancel: () => void
}) {
  const [name, setName] = useState(type.name)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleSave(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      await renameAppointmentType(doctorProfileId, type.appointmentTypeId, { name }, token)
      onSaved()
    } catch (err) {
      setError(errorMessage(err))
      setSubmitting(false)
    }
  }

  return (
    <li className="rounded-lg border border-indigo-200 bg-white p-3">
      <form onSubmit={handleSave} className="space-y-2" aria-label={`Edit ${type.name}`}>
        {error && (
          <p role="alert" className="rounded-md bg-red-50 p-2 text-xs text-red-700">
            {error}
          </p>
        )}
        <div>
          <label htmlFor={`edit-name-${type.appointmentTypeId}`} className="block text-xs font-medium text-gray-700">
            Appointment type name
          </label>
          <input
            id={`edit-name-${type.appointmentTypeId}`}
            required
            value={name}
            onChange={(e) => setName(e.target.value)}
            className="input mt-1"
          />
        </div>
        <RowButtons submitting={submitting} onCancel={onCancel} />
      </form>
    </li>
  )
}

// 068-per-clinic-fees: this clinic's price for one type - or clear it so the clinic's default fee
// applies. Shown to this clinic's admin only.
function PriceAppointmentTypeRow({
  type,
  clinicId,
  doctorProfileId,
  token,
  onSaved,
  onCancel,
}: {
  type: ClinicAppointmentTypeFee
  clinicId: string
  doctorProfileId: string
  token: string
  onSaved: (fees: ClinicDoctorFees, notice: string) => void
  onCancel: () => void
}) {
  const [amount, setAmount] = useState(type.price === null ? '' : String(type.price))
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function run(action: () => Promise<ClinicDoctorFees>, notice: string) {
    setSubmitting(true)
    setError(null)
    try {
      onSaved(await action(), notice)
    } catch (err) {
      setError(errorMessage(err))
      setSubmitting(false)
    }
  }

  function handleSave(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    void run(
      () => setClinicTypePrice(clinicId, doctorProfileId, type.appointmentTypeId, Number(amount), token),
      'Price updated.',
    )
  }

  return (
    <li className="rounded-lg border border-indigo-200 bg-white p-3">
      <form onSubmit={handleSave} className="space-y-2" aria-label={`Price ${type.name}`}>
        {error && (
          <p role="alert" className="rounded-md bg-red-50 p-2 text-xs text-red-700">
            {error}
          </p>
        )}
        <div>
          <label htmlFor={`price-${type.appointmentTypeId}`} className="block text-xs font-medium text-gray-700">
            Price at this clinic
          </label>
          <input
            id={`price-${type.appointmentTypeId}`}
            type="number"
            step="0.01"
            min="0"
            required
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
            className="input mt-1"
          />
        </div>
        <RowButtons submitting={submitting} onCancel={onCancel}>
          {type.price !== null && (
            <button
              type="button"
              disabled={submitting}
              onClick={() =>
                void run(
                  () => removeClinicTypePrice(clinicId, doctorProfileId, type.appointmentTypeId, token),
                  'Price removed - the default fee applies.',
                )
              }
              className="rounded-lg bg-gray-100 px-3 py-1.5 text-xs font-semibold text-gray-700 transition-all duration-150 ease-out hover:bg-gray-200 disabled:opacity-50"
            >
              Use default fee
            </button>
          )}
        </RowButtons>
      </form>
    </li>
  )
}

function PriceLabel({ type }: { type: ClinicAppointmentTypeFee }) {
  if (type.price !== null) return <>{formatFee(type.price)}</>
  if (type.effectiveFee !== null) {
    return <span className="text-gray-500">Default fee ({formatFee(type.effectiveFee)})</span>
  }
  return <span className="text-amber-700">No price - not bookable here</span>
}

// _diagnostics [HIGH] - [APPOINTMENT_TYPE_CONFIG] - [MISSING_UI]
// 068-per-clinic-fees: prices are this clinic's own. Any staff member here can see them; only
// this clinic's admin (canEditPrices) can change them. Types themselves stay doctor-level.
export interface AppointmentTypeConfigFormProps {
  clinicId: string
  doctorProfileId: string
  canEditPrices: boolean
}

export function AppointmentTypeConfigForm({ clinicId, doctorProfileId, canEditPrices }: AppointmentTypeConfigFormProps) {
  const [session] = useState(() => loadStaffSession())
  const [fees, setFees] = useState<ClinicDoctorFees | null>(null)
  const [name, setName] = useState('')
  const [defaultFeeAmount, setDefaultFeeAmount] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [editingId, setEditingId] = useState<string | null>(null)
  const [pricingId, setPricingId] = useState<string | null>(null)

  function refresh() {
    if (!session) return
    getClinicFees(clinicId, doctorProfileId, session.token)
      .then(setFees)
      .catch((err: unknown) => setError(err instanceof Error ? err.message : 'Failed to load appointment types.'))
  }

  useEffect(refresh, [session, clinicId, doctorProfileId])

  async function handleCreate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!session) return
    setSubmitting(true)
    setError(null)
    setNotice(null)

    try {
      await createAppointmentType(doctorProfileId, { name }, session.token)
      setName('')
      setNotice('Appointment type added.')
      refresh()
    } catch (err) {
      setError(errorMessage(err))
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
      setFees(await setClinicDefaultFee(clinicId, doctorProfileId, Number(defaultFeeAmount), session.token))
      setDefaultFeeAmount('')
      setNotice('Default fee updated.')
    } catch (err) {
      setError(errorMessage(err))
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

  // staff-console-audit-2026-09-10 P2: Default fee comes first, since every appointment type
  // without its own price uses that default - the form that produces the fallback value belongs
  // before the form that depends on it.
  return (
    <div className="mx-auto max-w-md space-y-6 rounded-xl border border-gray-200 bg-white p-6 shadow-md">
      <div>
        <h1 className="text-lg font-semibold text-gray-900">Appointment types and prices</h1>
        <p className="mt-1 text-xs text-gray-500">
          Prices apply at this clinic only.{!canEditPrices && " Only this clinic's admin can change them."}
        </p>
      </div>

      {error && (
        <p role="alert" className="rounded-md bg-red-50 p-3 text-sm text-red-700">
          {error}
        </p>
      )}
      {notice && <p className="rounded-md bg-green-50 p-3 text-sm text-green-700">{notice}</p>}

      <section>
        <h2 className="text-sm font-medium text-gray-900">Default fee at this clinic</h2>
        <p className="mt-1 text-sm tabular-nums text-gray-700">
          {fees === null ? '…' : fees.defaultFee !== null ? formatFee(fees.defaultFee) : 'Not set'}
        </p>
        {canEditPrices && (
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
        )}
      </section>

      <section className="border-t border-gray-100 pt-6">
        <h2 className="text-sm font-medium text-gray-900">Appointment types</h2>
        {fees === null ? (
          <div className="mt-2">
            <ListSkeleton rows={2} />
          </div>
        ) : fees.appointmentTypes.length === 0 ? (
          <p className="mt-2 text-sm text-gray-600">No appointment types yet.</p>
        ) : (
          <ul className="mt-2 space-y-1.5">
            {fees.appointmentTypes.map((type) =>
              editingId === type.appointmentTypeId ? (
                <EditAppointmentTypeRow
                  key={type.appointmentTypeId}
                  type={type}
                  doctorProfileId={doctorProfileId}
                  token={session.token}
                  onCancel={() => setEditingId(null)}
                  onSaved={() => {
                    setEditingId(null)
                    setNotice('Appointment type updated.')
                    refresh()
                  }}
                />
              ) : pricingId === type.appointmentTypeId ? (
                <PriceAppointmentTypeRow
                  key={type.appointmentTypeId}
                  type={type}
                  clinicId={clinicId}
                  doctorProfileId={doctorProfileId}
                  token={session.token}
                  onCancel={() => setPricingId(null)}
                  onSaved={(updated, message) => {
                    setPricingId(null)
                    setFees(updated)
                    setNotice(message)
                  }}
                />
              ) : (
                <li
                  key={type.appointmentTypeId}
                  className="flex items-center justify-between gap-3 rounded-lg border border-gray-200 bg-gray-50 px-3 py-2"
                >
                  <span className="text-sm font-medium text-gray-900">{type.name}</span>
                  <span className="flex shrink-0 items-center gap-3">
                    <span className="text-sm tabular-nums text-gray-600">
                      <PriceLabel type={type} />
                    </span>
                    {canEditPrices && (
                      <button
                        type="button"
                        onClick={() => setPricingId(type.appointmentTypeId)}
                        aria-label={`Set price for ${type.name}`}
                        className="text-sm font-medium text-indigo-600 transition-colors duration-150 hover:text-indigo-700"
                      >
                        Price
                      </button>
                    )}
                    <button
                      type="button"
                      onClick={() => setEditingId(type.appointmentTypeId)}
                      className="text-sm font-medium text-indigo-600 transition-colors duration-150 hover:text-indigo-700"
                    >
                      Edit
                    </button>
                  </span>
                </li>
              ),
            )}
          </ul>
        )}

        <form onSubmit={handleCreate} className="mt-3 space-y-3" aria-label="Add appointment type">
          <div>
            <label htmlFor="appointmentTypeName" className="block text-sm font-medium text-gray-700">
              Appointment type name
            </label>
            <input
              id="appointmentTypeName"
              required
              placeholder="e.g. General Consultation"
              value={name}
              onChange={(e) => setName(e.target.value)}
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
