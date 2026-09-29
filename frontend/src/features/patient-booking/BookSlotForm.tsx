import { useState, type FormEvent } from 'react'
import { bookSlot, type BookingResponse, type OpenSlot } from './api'
import { ApiError } from '../../lib/apiClient'
import { rateLimitMessage } from '../../lib/rateLimitMessage'
import { deriveDisplayNameFromEmail, loadPatientSession } from '../patient-account/token'
import { IconBadge, CheckIcon } from '../../components/adminIcons'
import { BookingIcon } from '../../components/patientIcons'
import { FormField } from '../../components/FormField'
import { Modal } from '../../components/Modal'

export interface BookSlotFormProps {
  clinicId: string
  slot: OpenSlot
  token: string
  onClose: () => void
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

export function BookSlotForm({ clinicId, slot, token, onClose, onBooked }: BookSlotFormProps) {
  const [appointmentTypeId, setAppointmentTypeId] = useState(slot.appointmentTypes[0]?.id ?? '')
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [result, setResult] = useState<BookingResponse | null>(null)

  const selectedType = slot.appointmentTypes.find((type) => type.id === appointmentTypeId)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSubmitting(true)
    setFormError(null)

    // The patient is already authenticated - there's no separate "who is this" question to ask
    // here. This only seeds Patient.name the very first time this account books at this clinic
    // (patientLinkingService.findOrCreatePatient); every later booking at the same clinic reuses
    // the already-linked record and ignores this value entirely, mirroring the same
    // honest-derived-from-email fallback the patient dashboard greeting already uses (no separate
    // "real name" concept exists anywhere above the per-clinic Patient record - see
    // deriveDisplayNameFromEmail's own note).
    const patientName = deriveDisplayNameFromEmail(loadPatientSession()?.email ?? '')

    try {
      const response = await bookSlot(clinicId, slot.slotId, { patientName, appointmentTypeId }, token)
      setResult(response)
      onBooked?.(response)
    } catch (err) {
      if (err instanceof ApiError) {
        // 060-booking-abuse-prevention: RATE_LIMITED carries retryAfterSeconds alongside the
        // usual message - BOOKING_LIMIT_REACHED (and every other existing error) already
        // surfaces correctly via err.message with no change needed here.
        const body = err.body as { error?: string; retryAfterSeconds?: number } | undefined
        setFormError(body?.error === 'RATE_LIMITED' ? rateLimitMessage(body.retryAfterSeconds) : err.message)
      } else {
        setFormError('Something went wrong. Please try again.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal onClose={onClose} ariaLabel={result ? 'Booking confirmed' : 'Book slot'} className="m-auto w-full max-w-md overflow-hidden rounded-xl border-0 bg-white p-0 shadow-xl backdrop:bg-gray-900/50">
      <div className="p-6">
        <div className="flex items-start justify-between gap-3">
          <div className="flex items-center gap-3">
            <IconBadge>{result ? <CheckIcon /> : <BookingIcon />}</IconBadge>
            <h1 className="text-lg font-semibold text-gray-900">{result ? 'Booking confirmed' : 'Book this slot'}</h1>
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="Close"
            className="rounded-md p-1 text-gray-400 transition-colors duration-150 hover:bg-gray-100 hover:text-gray-600"
          >
            <svg aria-hidden="true" viewBox="0 0 20 20" className="h-5 w-5" fill="currentColor">
              <path d="M6.28 5.22a.75.75 0 00-1.06 1.06L8.94 10l-3.72 3.72a.75.75 0 101.06 1.06L10 11.06l3.72 3.72a.75.75 0 101.06-1.06L11.06 10l3.72-3.72a.75.75 0 00-1.06-1.06L10 8.94 6.28 5.22z" />
            </svg>
          </button>
        </div>

        {result ? (
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
        ) : (
          <form onSubmit={handleSubmit} className="mt-5 space-y-6" aria-label="Book slot">
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

            <FormField label="Appointment type" htmlFor="appointmentTypeId">
              <select
                id="appointmentTypeId"
                required
                value={appointmentTypeId}
                onChange={(e) => setAppointmentTypeId(e.target.value)}
                className="input"
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
            </FormField>

            <button
              type="submit"
              disabled={submitting}
              className="w-full rounded-lg bg-indigo-600 px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition-all duration-150 ease-out hover:bg-indigo-500 hover:shadow-md active:scale-[0.98] disabled:opacity-50 disabled:pointer-events-none disabled:active:scale-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-2"
            >
              {submitting ? 'Booking…' : 'Book slot'}
            </button>
          </form>
        )}
      </div>
    </Modal>
  )
}
