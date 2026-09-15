import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { bookQueueSlot, QueueBookSlotApiError, type QueueBookingResponse } from './queueApi'
import { loadPatientSession } from '../patient-account/token'
import type { AppointmentTypeOption } from './api'
import { IconBadge, SessionIcon, CheckIcon } from '../../components/adminIcons'

export interface QueueBookSlotFormProps {
  clinicId: string
  sessionId: string
  // patient-booking-flow-rebuild: passed in by the queue-session picker (already fetched
  // alongside the session listing) - replaces the raw "Appointment Type ID" text field. Absent
  // only on a direct/refreshed visit to this route, which has no other way to recover them (the
  // patient audience has no standalone "appointment types for a doctor" endpoint - see
  // AppointmentTypeSelect's own staff-only equivalent).
  appointmentTypes?: AppointmentTypeOption[]
  // patient-booking-visual-polish: same "absent only on a direct/refreshed visit" caveat as
  // appointmentTypes above - the summary panel below is simply skipped when these are missing.
  doctorName?: string
  sessionDate?: string
  startTime?: string
  endTime?: string
}

// "10:15:00" -> "10:15" - the seconds are never meaningful to a patient.
function formatTime(time: string): string {
  return time.slice(0, 5)
}

function formatSessionDate(iso: string): string {
  const date = new Date(`${iso}T00:00:00`)
  if (Number.isNaN(date.getTime())) return iso
  return date.toLocaleDateString(undefined, { weekday: 'long', month: 'long', day: 'numeric' })
}

export function QueueBookSlotForm({
  clinicId,
  sessionId,
  appointmentTypes,
  doctorName,
  sessionDate,
  startTime,
  endTime,
}: QueueBookSlotFormProps) {
  const [session] = useState(() => loadPatientSession())
  const [patientName, setPatientName] = useState('')
  const [appointmentTypeId, setAppointmentTypeId] = useState(appointmentTypes?.[0]?.id ?? '')
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [result, setResult] = useState<QueueBookingResponse | null>(null)

  const selectedType = appointmentTypes?.find((type) => type.id === appointmentTypeId)
  const hasSummary = doctorName && sessionDate && startTime && endTime

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!session) return
    setSubmitting(true)
    setFormError(null)

    try {
      const response = await bookQueueSlot(clinicId, sessionId, { patientName, appointmentTypeId }, session.token)
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
      <div className="mx-auto max-w-md rounded-xl border border-gray-200 bg-white p-6 shadow-sm">
        <p className="text-sm text-gray-600">Please log in to book into this queue.</p>
      </div>
    )
  }

  if (result) {
    return (
      <div className="mx-auto max-w-md rounded-xl border border-gray-200 bg-white p-6 shadow-md">
        <div className="flex items-center gap-3">
          <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-green-50 text-green-600">
            <CheckIcon />
          </span>
          <h1 className="text-lg font-semibold text-gray-900">Booking confirmed</h1>
        </div>
        <dl className="mt-5 space-y-2.5 rounded-lg border border-gray-100 bg-gray-50 p-4 text-sm">
          {hasSummary && (
            <>
              <div className="flex justify-between gap-4">
                <dt className="text-gray-500">Doctor</dt>
                <dd className="font-medium text-gray-900">{doctorName}</dd>
              </div>
              <div className="flex justify-between gap-4">
                <dt className="text-gray-500">When</dt>
                <dd className="font-medium text-gray-900 tabular-nums">
                  {formatSessionDate(sessionDate!)} · {formatTime(startTime!)}–{formatTime(endTime!)}
                </dd>
              </div>
            </>
          )}
          <div className="flex justify-between gap-4 border-t border-gray-200 pt-2.5 first:border-t-0 first:pt-0">
            <dt className="text-gray-500">Token number</dt>
            <dd className="font-semibold text-gray-900 tabular-nums">{result.tokenNumber}</dd>
          </div>
          <div className="flex justify-between gap-4">
            <dt className="text-gray-500">Fee</dt>
            <dd className="font-semibold text-gray-900 tabular-nums">
              ₹{result.lockedFee.toFixed(2)}{' '}
              <span className="font-normal text-gray-500">
                · {result.paymentStatus === 'PAID' ? 'Paid' : 'Payment pending'}
              </span>
            </dd>
          </div>
        </dl>
      </div>
    )
  }

  if (!appointmentTypes || appointmentTypes.length === 0) {
    return (
      <div className="mx-auto max-w-md rounded-xl border border-gray-200 bg-white p-6 shadow-sm">
        <p className="text-sm text-gray-600">
          Please pick a session from your clinic's queue list to book into it.
        </p>
        <Link
          to={`/patient/clinics/${clinicId}/queue-sessions`}
          className="mt-3 inline-block text-sm font-medium text-indigo-600 transition-colors duration-150 hover:text-indigo-700 hover:underline"
        >
          Browse queue sessions
        </Link>
      </div>
    )
  }

  return (
    <form
      onSubmit={handleSubmit}
      className="mx-auto max-w-md space-y-6 rounded-xl border border-gray-200 bg-white p-6 shadow-md"
      aria-label="Book queue slot"
    >
      <div className="flex items-center gap-3">
        <IconBadge>
          <SessionIcon />
        </IconBadge>
        <h1 className="text-lg font-semibold text-gray-900">Book into this queue</h1>
      </div>

      {hasSummary && (
        <div className="space-y-0.5 rounded-lg border border-gray-100 bg-gray-50 p-3.5 text-sm">
          <p className="font-medium text-gray-900">{doctorName}</p>
          <p className="text-gray-600 tabular-nums">
            {formatSessionDate(sessionDate!)} · {formatTime(startTime!)}–{formatTime(endTime!)}
          </p>
        </div>
      )}

      {formError && (
        <p role="alert" className="rounded-md bg-red-50 p-3 text-sm text-red-700">
          {formError}
        </p>
      )}

      <div>
        <label htmlFor="queuePatientName" className="block text-sm font-medium text-gray-700">
          Your name
        </label>
        <input
          id="queuePatientName"
          required
          value={patientName}
          onChange={(e) => setPatientName(e.target.value)}
          className="input mt-1"
        />
      </div>

      <div>
        <label htmlFor="queueAppointmentTypeId" className="block text-sm font-medium text-gray-700">
          Appointment type
        </label>
        <select
          id="queueAppointmentTypeId"
          required
          value={appointmentTypeId}
          onChange={(e) => setAppointmentTypeId(e.target.value)}
          className="input mt-1"
        >
          {appointmentTypes.map((type) => (
            <option key={type.id} value={type.id}>
              {type.name}
            </option>
          ))}
        </select>
        {selectedType?.feeOverride != null && (
          <p className="mt-1.5 text-sm text-gray-600">
            Fee: <span className="font-semibold text-gray-900 tabular-nums">₹{selectedType.feeOverride.toFixed(2)}</span>
          </p>
        )}
      </div>

      <button
        type="submit"
        disabled={submitting}
        className="w-full rounded-lg bg-indigo-600 px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition-all duration-150 ease-out hover:bg-indigo-500 hover:shadow-md active:scale-[0.98] disabled:opacity-50 disabled:pointer-events-none disabled:active:scale-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-2"
      >
        {submitting ? 'Booking…' : 'Book into queue'}
      </button>
    </form>
  )
}
