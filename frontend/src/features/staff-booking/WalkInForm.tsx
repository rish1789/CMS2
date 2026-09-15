import { useState, type FormEvent } from 'react'
import { insertWalkIn, WalkInApiError, type BookingResponse } from './api'
import { loadStaffSession } from '../staff-login/token'
import { PatientPicker } from '../patient-search/PatientPicker'
import { AppointmentTypeSelect } from '../appointment-types/AppointmentTypeSelect'

type PatientMode = 'existing' | 'new'

interface FormState {
  patientMode: PatientMode
  patientId: string
  patientName: string
  patientPhone: string
  appointmentTypeId: string
  overrideReason: string
}

const initialState: FormState = {
  patientMode: 'existing',
  patientId: '',
  patientName: '',
  patientPhone: '',
  appointmentTypeId: '',
  overrideReason: '',
}

export interface WalkInFormProps {
  clinicId: string
  sessionId: string
  doctorProfileId: string
}

export function WalkInForm({ clinicId, sessionId, doctorProfileId }: WalkInFormProps) {
  const [session] = useState(() => loadStaffSession())
  const [form, setForm] = useState<FormState>(initialState)
  const [formError, setFormError] = useState<string | null>(null)
  // The system - not the staff member - selects the target Slot via the priority search
  // (buffer, then no-show-freed, then regular). The client can't know in advance which
  // tier will apply, so the override-reason field only becomes required once the server
  // actually says so (contracts/walk-in-insertion.md).
  const [overrideReasonRequired, setOverrideReasonRequired] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [result, setResult] = useState<BookingResponse | null>(null)

  function updateField<K extends keyof FormState>(key: K, value: FormState[K]) {
    setForm((prev) => ({ ...prev, [key]: value }))
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!session) return
    setSubmitting(true)
    setFormError(null)

    try {
      const response = await insertWalkIn(
        clinicId,
        sessionId,
        {
          ...(form.patientMode === 'existing'
            ? { patientId: form.patientId }
            : {
                patientName: form.patientName,
                patientPhone: form.patientPhone.trim() === '' ? undefined : form.patientPhone,
              }),
          appointmentTypeId: form.appointmentTypeId,
          overrideReason: form.overrideReason.trim() === '' ? undefined : form.overrideReason,
        },
        session.token,
      )
      setResult(response)
    } catch (err) {
      if (err instanceof WalkInApiError) {
        if (err.body.error === 'OVERRIDE_REASON_REQUIRED') {
          setOverrideReasonRequired(true)
        }
        setFormError(err.message)
      } else {
        setFormError('Something went wrong. Please try again.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  if (!session) {
    return (
      <div className="mx-auto max-w-md rounded-xl border border-gray-200 bg-white p-6 shadow-sm">
        <p className="text-sm text-gray-600">Please sign in as staff to insert a walk-in.</p>
      </div>
    )
  }

  if (result) {
    return (
      <div className="mx-auto max-w-md rounded-xl border border-gray-200 bg-white p-6 shadow-md">
        <h1 className="text-lg font-semibold text-gray-900">Walk-in inserted</h1>
        <p className="mt-2 text-sm text-gray-600 tabular-nums">
          Locked fee: ₹{result.lockedFee.toFixed(2)} · {result.paymentStatus === 'PAID' ? 'Paid' : 'Payment pending'}
        </p>
      </div>
    )
  }

  return (
    <form
      onSubmit={handleSubmit}
      className="mx-auto max-w-md space-y-6 rounded-xl border border-gray-200 bg-white p-6 shadow-md"
      aria-label="Insert walk-in"
    >
      <h1 className="text-lg font-semibold text-gray-900">Insert a walk-in</h1>

      {formError && (
        <p role="alert" className="rounded-md bg-red-50 p-3 text-sm text-red-700">
          {formError}
        </p>
      )}

      <fieldset>
        <legend className="mb-2 block text-sm font-medium text-gray-700">Patient</legend>
        <div className="grid grid-cols-2 gap-2">
          <label
            className={`flex cursor-pointer items-center justify-center rounded-lg border px-3 py-2.5 text-center text-sm font-medium transition-all duration-200 ease-out ${
              form.patientMode === 'existing'
                ? 'border-indigo-600 bg-indigo-600 text-white shadow-md'
                : 'border-gray-200 bg-gray-50 text-gray-700 hover:-translate-y-0.5 hover:border-indigo-300 hover:bg-white hover:shadow-sm'
            }`}
          >
            <input
              type="radio"
              checked={form.patientMode === 'existing'}
              onChange={() => updateField('patientMode', 'existing')}
              className="sr-only"
            />
            Existing patient
          </label>
          <label
            className={`flex cursor-pointer items-center justify-center rounded-lg border px-3 py-2.5 text-center text-sm font-medium transition-all duration-200 ease-out ${
              form.patientMode === 'new'
                ? 'border-indigo-600 bg-indigo-600 text-white shadow-md'
                : 'border-gray-200 bg-gray-50 text-gray-700 hover:-translate-y-0.5 hover:border-indigo-300 hover:bg-white hover:shadow-sm'
            }`}
          >
            <input
              type="radio"
              checked={form.patientMode === 'new'}
              onChange={() => updateField('patientMode', 'new')}
              className="sr-only"
            />
            New walk-in patient
          </label>
        </div>
      </fieldset>

      {form.patientMode === 'existing' ? (
        <div>
          <label htmlFor="patientId" className="block text-sm font-medium text-gray-700">
            Patient
          </label>
          <PatientPicker
            id="patientId"
            required
            clinicId={clinicId}
            token={session.token}
            value={form.patientId}
            onChange={(patientId) => updateField('patientId', patientId)}
          />
        </div>
      ) : (
        <>
          <div>
            <label htmlFor="patientName" className="block text-sm font-medium text-gray-700">
              Patient name
            </label>
            <input
              id="patientName"
              required
              value={form.patientName}
              onChange={(e) => updateField('patientName', e.target.value)}
              className="input mt-1"
            />
          </div>
          <div>
            <label htmlFor="patientPhone" className="block text-sm font-medium text-gray-700">
              Phone (optional)
            </label>
            <input
              id="patientPhone"
              value={form.patientPhone}
              onChange={(e) => updateField('patientPhone', e.target.value)}
              className="input mt-1"
            />
          </div>
        </>
      )}

      <div>
        <label htmlFor="appointmentTypeId" className="block text-sm font-medium text-gray-700">
          Appointment type
        </label>
        <AppointmentTypeSelect
          id="appointmentTypeId"
          required
          doctorProfileId={doctorProfileId}
          token={session.token}
          value={form.appointmentTypeId}
          onChange={(value) => updateField('appointmentTypeId', value)}
        />
      </div>

      <div>
        <label htmlFor="overrideReason" className="block text-sm font-medium text-gray-700">
          Override reason{overrideReasonRequired ? '' : ' (only needed if no buffer or no-show slot is open)'}
        </label>
        <textarea
          id="overrideReason"
          required={overrideReasonRequired}
          value={form.overrideReason}
          onChange={(e) => updateField('overrideReason', e.target.value)}
          className="input mt-1"
          rows={2}
        />
      </div>

      <button
        type="submit"
        disabled={submitting}
        className="w-full rounded-lg bg-indigo-600 px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition-all duration-150 ease-out hover:bg-indigo-500 hover:shadow-md active:scale-[0.98] disabled:opacity-50 disabled:pointer-events-none disabled:active:scale-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-2"
      >
        {submitting ? 'Inserting…' : 'Insert walk-in'}
      </button>
    </form>
  )
}
