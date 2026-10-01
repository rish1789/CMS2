import { useState, type FormEvent } from 'react'
import { bookSlot, type BookSlotErrorBody, type BookSlotRequest, type BookingResponse } from './api'
import { ApiError } from '../../lib/apiClient'
import { loadStaffSession, storeStaffSession } from '../staff-login/token'
import { AppointmentTypeSelect } from '../appointment-types/AppointmentTypeSelect'
import { PatientPicker } from '../patient-search/PatientPicker'
import { FormField } from '../../components/FormField'
import { DuplicatePhoneConflict } from '../patient-search/DuplicatePhoneConflict'
import { duplicatePhoneConflictOf, type DuplicatePhoneConflictBody } from '../patient-search/duplicatePhoneConflict'

type PatientMode = 'existing' | 'new'

interface FormState {
  patientMode: PatientMode
  patientId: string
  patientName: string
  patientPhone: string
  appointmentTypeId: string
}

interface FieldErrors {
  patient?: string
  appointmentTypeId?: string
}

const initialState: FormState = {
  patientMode: 'existing',
  patientId: '',
  patientName: '',
  patientPhone: '',
  appointmentTypeId: '',
}

export interface BookSlotFormProps {
  clinicId: string
  slotId: string
  doctorProfileId: string
}

export function BookSlotForm({ clinicId, slotId, doctorProfileId }: BookSlotFormProps) {
  const [session, setSession] = useState(() => loadStaffSession())
  const [form, setForm] = useState<FormState>(initialState)
  const [formError, setFormError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({})
  const [submitting, setSubmitting] = useState(false)
  const [result, setResult] = useState<BookingResponse | null>(null)
  const [phoneConflict, setPhoneConflict] = useState<DuplicatePhoneConflictBody | null>(null)

  function updateField<K extends keyof FormState>(key: K, value: FormState[K]) {
    setForm((prev) => ({ ...prev, [key]: value }))
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!session) return
    setFormError(null)

    const errors: FieldErrors = {}
    if (form.patientMode === 'existing' && form.patientId.trim() === '') {
      errors.patient = 'Please select a patient.'
    } else if (form.patientMode === 'new' && form.patientName.trim() === '') {
      errors.patient = 'Patient name is required.'
    }
    if (form.appointmentTypeId.trim() === '') {
      errors.appointmentTypeId = 'Please select an appointment type.'
    }
    setFieldErrors(errors)
    if (Object.keys(errors).length > 0) {
      return
    }

    await submitBooking(
      form.patientMode === 'existing'
        ? { patientId: form.patientId, appointmentTypeId: form.appointmentTypeId }
        : {
            patientName: form.patientName,
            patientPhone: form.patientPhone.trim() === '' ? undefined : form.patientPhone,
            appointmentTypeId: form.appointmentTypeId,
          },
    )
  }

  async function submitBooking(request: BookSlotRequest) {
    if (!session) return
    setSubmitting(true)
    setFormError(null)
    setPhoneConflict(null)

    try {
      const response = await bookSlot(clinicId, slotId, request, session.token)
      setResult(response)
    } catch (err) {
      // 074-duplicate-patient-phone: the typed values stay; staff choose what to do next.
      const conflict = err instanceof ApiError ? duplicatePhoneConflictOf(err.body) : null
      if (conflict) {
        setPhoneConflict(conflict)
      } else if (err instanceof ApiError) {
        const body = err.body as BookSlotErrorBody | undefined
        if (body?.error === 'UNAUTHORIZED') {
          storeStaffSession(null)
          setSession(null)
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
      <div className="mx-auto max-w-md rounded-lg border border-gray-200 bg-white p-6 shadow-sm">
        <p className="text-sm text-gray-600">Please sign in as staff to book this slot.</p>
      </div>
    )
  }

  if (result) {
    return (
      <div className="mx-auto max-w-md rounded-lg border border-gray-200 bg-white p-6 shadow-sm">
        <h1 className="text-lg font-semibold text-gray-900">Booking confirmed</h1>
        <p className="mt-2 text-sm text-gray-600">
          Locked fee: ₹{result.lockedFee.toFixed(2)} · {result.paymentStatus === 'PAID' ? 'Paid' : 'Payment pending'}
        </p>
      </div>
    )
  }

  return (
    <form
      onSubmit={handleSubmit}
      className="mx-auto max-w-md space-y-6 rounded-lg border border-gray-200 bg-white p-6 shadow-sm"
      aria-label="Book slot"
    >
      <h1 className="text-lg font-semibold text-gray-900">Book this slot</h1>

      {formError && (
        <p role="alert" className="rounded-md bg-red-50 p-3 text-sm text-red-700">
          {formError}
        </p>
      )}

      {phoneConflict && (
        <DuplicatePhoneConflict
          conflict={phoneConflict}
          busy={submitting}
          actionLabel={(name) => `Book ${name} instead`}
          onUseExisting={(existing) =>
            void submitBooking({ patientId: existing.id, appointmentTypeId: form.appointmentTypeId })
          }
        />
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
          <label htmlFor="patientId" className="block text-sm font-medium text-gray-700">
            Patient
          </label>
          {/* PatientPicker manages its own top margin - wrapping it in FormField would double
              it, so its error is rendered manually below in FormField's own style. */}
          <PatientPicker
            id="patientId"
            required
            clinicId={clinicId}
            token={session.token}
            value={form.patientId}
            onChange={(patientId) => updateField('patientId', patientId)}
          />
          {fieldErrors.patient && (
            <p role="alert" className="mt-1 text-sm text-red-600">
              {fieldErrors.patient}
            </p>
          )}
        </div>
      ) : (
        <>
          <FormField label="Patient name" htmlFor="patientName" error={fieldErrors.patient}>
            <input
              id="patientName"
              value={form.patientName}
              onChange={(e) => updateField('patientName', e.target.value)}
              className="input"
            />
          </FormField>
          <FormField label="Phone (optional)" htmlFor="patientPhone">
            <input
              id="patientPhone"
              value={form.patientPhone}
              onChange={(e) => updateField('patientPhone', e.target.value)}
              className="input"
            />
          </FormField>
        </>
      )}

      <div>
        <label htmlFor="appointmentTypeId" className="block text-sm font-medium text-gray-700">
          Appointment type
        </label>
        {/* AppointmentTypeSelect manages its own top margin - see PatientPicker note above.
            No `required` prop: the pre-submit fieldErrors.appointmentTypeId check below is the
            one that actually surfaces (native required would otherwise block the submit event
            itself, showing a browser tooltip instead of this form's own inline error). */}
        <AppointmentTypeSelect
          id="appointmentTypeId"
          doctorProfileId={doctorProfileId}
          token={session.token}
          value={form.appointmentTypeId}
          onChange={(value) => updateField('appointmentTypeId', value)}
        />
        {fieldErrors.appointmentTypeId && (
          <p role="alert" className="mt-1 text-sm text-red-600">
            {fieldErrors.appointmentTypeId}
          </p>
        )}
      </div>

      <button
        type="submit"
        disabled={submitting}
        className="w-full rounded-lg bg-indigo-600 px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition-all duration-150 ease-out hover:bg-indigo-500 hover:shadow active:scale-[0.98] disabled:opacity-50 disabled:pointer-events-none disabled:active:scale-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-2"
      >
        {submitting ? 'Booking…' : 'Book slot'}
      </button>
    </form>
  )
}
