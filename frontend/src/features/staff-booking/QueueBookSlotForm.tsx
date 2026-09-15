import { useState, type FormEvent } from 'react'
import { bookQueueSlot, QueueBookSlotApiError, type QueueBookingResponse } from './queueApi'
import { loadStaffSession } from '../staff-login/token'
import { AppointmentTypeSelect } from '../appointment-types/AppointmentTypeSelect'
import { PatientPicker } from '../patient-search/PatientPicker'

type PatientMode = 'existing' | 'new'

interface FormState {
  patientMode: PatientMode
  patientId: string
  patientName: string
  patientPhone: string
  appointmentTypeId: string
}

const initialState: FormState = {
  patientMode: 'existing',
  patientId: '',
  patientName: '',
  patientPhone: '',
  appointmentTypeId: '',
}

export interface QueueBookSlotFormProps {
  clinicId: string
  sessionId: string
  doctorProfileId: string
}

export function QueueBookSlotForm({ clinicId, sessionId, doctorProfileId }: QueueBookSlotFormProps) {
  const [session] = useState(() => loadStaffSession())
  const [form, setForm] = useState<FormState>(initialState)
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [result, setResult] = useState<QueueBookingResponse | null>(null)

  function updateField<K extends keyof FormState>(key: K, value: FormState[K]) {
    setForm((prev) => ({ ...prev, [key]: value }))
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!session) return
    setSubmitting(true)
    setFormError(null)

    try {
      const response = await bookQueueSlot(
        clinicId,
        sessionId,
        form.patientMode === 'existing'
          ? { patientId: form.patientId, appointmentTypeId: form.appointmentTypeId }
          : {
              patientName: form.patientName,
              patientPhone: form.patientPhone.trim() === '' ? undefined : form.patientPhone,
              appointmentTypeId: form.appointmentTypeId,
            },
        session.token,
      )
      setResult(response)
    } catch (err) {
      if (err instanceof QueueBookSlotApiError) {
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
      <div className="mx-auto max-w-md rounded-lg border border-gray-200 bg-white p-6 shadow-sm">
        <p className="text-sm text-gray-600">Please sign in as staff to book into this queue.</p>
      </div>
    )
  }

  if (result) {
    return (
      <div className="mx-auto max-w-md rounded-lg border border-gray-200 bg-white p-6 shadow-sm">
        <h1 className="text-lg font-semibold text-gray-900">Booking confirmed</h1>
        <p className="mt-2 text-sm text-gray-600">
          Token number: {result.tokenNumber} · Locked fee: ₹{result.lockedFee.toFixed(2)} ·{' '}
          {result.paymentStatus === 'PAID' ? 'Paid' : 'Payment pending'}
        </p>
      </div>
    )
  }

  return (
    <form
      onSubmit={handleSubmit}
      className="mx-auto max-w-md space-y-6 rounded-lg border border-gray-200 bg-white p-6 shadow-sm"
      aria-label="Book queue slot"
    >
      <h1 className="text-lg font-semibold text-gray-900">Book into this queue</h1>

      {formError && (
        <p role="alert" className="rounded-md bg-red-50 p-3 text-sm text-red-700">
          {formError}
        </p>
      )}

      <fieldset>
        <legend className="block text-sm font-medium text-gray-700">Patient</legend>
        <div className="mt-1 flex gap-4 text-sm text-gray-700">
          <label className="flex items-center gap-2">
            <input
              type="radio"
              checked={form.patientMode === 'existing'}
              onChange={() => updateField('patientMode', 'existing')}
            />
            Existing patient
          </label>
          <label className="flex items-center gap-2">
            <input
              type="radio"
              checked={form.patientMode === 'new'}
              onChange={() => updateField('patientMode', 'new')}
            />
            New walk-in patient
          </label>
        </div>
      </fieldset>

      {form.patientMode === 'existing' ? (
        <div>
          <label htmlFor="queuePatientId" className="block text-sm font-medium text-gray-700">
            Patient
          </label>
          <PatientPicker
            id="queuePatientId"
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
            <label htmlFor="queuePatientName" className="block text-sm font-medium text-gray-700">
              Patient name
            </label>
            <input
              id="queuePatientName"
              required
              value={form.patientName}
              onChange={(e) => updateField('patientName', e.target.value)}
              className="input mt-1"
            />
          </div>
          <div>
            <label htmlFor="queuePatientPhone" className="block text-sm font-medium text-gray-700">
              Phone (optional)
            </label>
            <input
              id="queuePatientPhone"
              value={form.patientPhone}
              onChange={(e) => updateField('patientPhone', e.target.value)}
              className="input mt-1"
            />
          </div>
        </>
      )}

      <div>
        <label htmlFor="queueAppointmentTypeId" className="block text-sm font-medium text-gray-700">
          Appointment type
        </label>
        <AppointmentTypeSelect
          id="queueAppointmentTypeId"
          required
          doctorProfileId={doctorProfileId}
          token={session.token}
          value={form.appointmentTypeId}
          onChange={(value) => updateField('appointmentTypeId', value)}
        />
      </div>

      <button
        type="submit"
        disabled={submitting}
        className="w-full rounded-lg bg-indigo-600 px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition-all duration-150 ease-out hover:bg-indigo-500 hover:shadow active:scale-[0.98] disabled:opacity-50 disabled:pointer-events-none disabled:active:scale-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-2"
      >
        {submitting ? 'Booking…' : 'Book into queue'}
      </button>
    </form>
  )
}
