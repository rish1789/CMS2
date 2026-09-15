import { useState, type FormEvent } from 'react'
import { bookSlot, BookSlotApiError, type BookingResponse, type OpenSlot } from './api'
import { IconBadge, CheckIcon } from '../../components/adminIcons'
import { BookingIcon } from '../../components/patientIcons'

export interface BookSlotFormProps {
  clinicId: string
  slot: OpenSlot
  token: string
  onBooked?: (booking: BookingResponse) => void
}

// "10:15:00" -> "10:15" - the seconds are never meaningful to a patient booking an appointment.
function formatTime(time: string): string {
  return time.slice(0, 5)
}

function formatSessionDate(iso: string): string {
  const date = new Date(`${iso}T00:00:00`)
  if (Number.isNaN(date.getTime())) return iso
  return date.toLocaleDateString(undefined, { weekday: 'long', month: 'long', day: 'numeric' })
}

export function BookSlotForm({ clinicId, slot, token, onBooked }: BookSlotFormProps) {
  const [patientName, setPatientName] = useState('')
  const [appointmentTypeId, setAppointmentTypeId] = useState(slot.appointmentTypes[0]?.id ?? '')
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [result, setResult] = useState<BookingResponse | null>(null)

  const selectedType = slot.appointmentTypes.find((type) => type.id === appointmentTypeId)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSubmitting(true)
    setFormError(null)

    try {
      const response = await bookSlot(clinicId, slot.slotId, { patientName, appointmentTypeId }, token)
      setResult(response)
      onBooked?.(response)
    } catch (err) {
      if (err instanceof BookSlotApiError) {
        setFormError(err.message)
      } else {
        setFormError('Something went wrong. Please try again.')
      }
    } finally {
      setSubmitting(false)
    }
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
          <div className="flex justify-between gap-4">
            <dt className="text-gray-500">Doctor</dt>
            <dd className="font-medium text-gray-900">{slot.doctorName}</dd>
          </div>
          <div className="flex justify-between gap-4">
            <dt className="text-gray-500">When</dt>
            <dd className="font-medium text-gray-900 tabular-nums">
              {formatSessionDate(slot.sessionDate)} · {formatTime(slot.startTime)}–{formatTime(slot.endTime)}
            </dd>
          </div>
          <div className="flex justify-between gap-4 border-t border-gray-200 pt-2.5">
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

  return (
    <form
      onSubmit={handleSubmit}
      className="mx-auto max-w-md space-y-6 rounded-xl border border-gray-200 bg-white p-6 shadow-md"
      aria-label="Book slot"
    >
      <div className="flex items-center gap-3">
        <IconBadge>
          <BookingIcon />
        </IconBadge>
        <h1 className="text-lg font-semibold text-gray-900">Book this slot</h1>
      </div>

      <div className="space-y-0.5 rounded-lg border border-gray-100 bg-gray-50 p-3.5 text-sm">
        <p className="font-medium text-gray-900">{slot.doctorName}</p>
        <p className="text-gray-600 tabular-nums">
          {formatSessionDate(slot.sessionDate)} · {formatTime(slot.startTime)}–{formatTime(slot.endTime)}
        </p>
      </div>

      {formError && (
        <p role="alert" className="rounded-md bg-red-50 p-3 text-sm text-red-700">
          {formError}
        </p>
      )}

      <div>
        <label htmlFor="patientName" className="block text-sm font-medium text-gray-700">
          Your name
        </label>
        <input
          id="patientName"
          required
          value={patientName}
          onChange={(e) => setPatientName(e.target.value)}
          className="input mt-1"
        />
      </div>

      <div>
        <label htmlFor="appointmentTypeId" className="block text-sm font-medium text-gray-700">
          Appointment type
        </label>
        <select
          id="appointmentTypeId"
          required
          value={appointmentTypeId}
          onChange={(e) => setAppointmentTypeId(e.target.value)}
          className="input mt-1"
        >
          {slot.appointmentTypes.map((type) => (
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
        {submitting ? 'Booking…' : 'Book slot'}
      </button>
    </form>
  )
}
