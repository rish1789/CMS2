import { useEffect, useState, type FormEvent } from 'react'
import { claimOffer, declineOffer, WaitlistClaimApiError, type WaitlistClaimResponse } from './api'
import { loadPatientSession } from '../patient-account/token'
import { PatientAppointmentTypeSelect } from '../appointment-types/PatientAppointmentTypeSelect'
import { listPatientAppointmentTypes, type AppointmentTypeResponse } from '../appointment-types/api'
import { CheckIcon } from '../../components/adminIcons'

export interface ClaimOfferCardProps {
  entryId: string
  // _diagnostics [MEDIUM] - [WAITLIST_CLAIM] - [NO_EXPIRY_VISIBILITY]: now sourced from
  // WaitlistEntryResponse.offerExpiresAt (previously never serialized to any patient-facing client).
  offerExpiresAt?: string | null
  // _diagnostics [HIGH] - [WAITLIST_CLAIM] - [RAW_ID_ENTRY]: sourced from WaitlistEntryResponse
  // .offeredDoctorProfileId - drives the appointment-type picker below, replacing a raw text
  // field. Nullable only for a caller that hasn't wired the new field through yet.
  offeredDoctorProfileId?: string | null
  onClaimed?: (booking: WaitlistClaimResponse) => void
  onDeclined?: () => void
}

function useCountdown(deadline?: string | null): string | null {
  const [now, setNow] = useState(() => Date.now())

  useEffect(() => {
    if (!deadline) return
    const intervalId = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(intervalId)
  }, [deadline])

  if (!deadline) return null
  const remainingMs = new Date(deadline).getTime() - now
  if (remainingMs <= 0) return 'Expired'
  const minutes = Math.floor(remainingMs / 60000)
  const seconds = Math.floor((remainingMs % 60000) / 1000)
  return `${minutes}:${seconds.toString().padStart(2, '0')} remaining`
}

export function ClaimOfferCard({
  entryId,
  offerExpiresAt,
  offeredDoctorProfileId,
  onClaimed,
  onDeclined,
}: ClaimOfferCardProps) {
  const countdown = useCountdown(offerExpiresAt)
  const [session] = useState(() => loadPatientSession())
  const [appointmentTypeId, setAppointmentTypeId] = useState('')
  const [patientName, setPatientName] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [claimedBooking, setClaimedBooking] = useState<WaitlistClaimResponse | null>(null)
  const [declined, setDeclined] = useState(false)
  // Fetched independently of PatientAppointmentTypeSelect's own internal fetch (a second,
  // identical GET) purely to read the selected type's fee - the select only reports back an id,
  // same fee-preview parity BookSlotForm/QueueBookSlotForm already have for their own pickers.
  const [types, setTypes] = useState<AppointmentTypeResponse[] | null>(null)

  useEffect(() => {
    if (!session || !offeredDoctorProfileId) return
    let cancelled = false
    listPatientAppointmentTypes(offeredDoctorProfileId, session.token)
      .then((response) => {
        if (!cancelled) setTypes(response)
      })
      .catch(() => {
        if (!cancelled) setTypes([])
      })
    return () => {
      cancelled = true
    }
  }, [offeredDoctorProfileId, session])

  const selectedType = types?.find((type) => type.id === appointmentTypeId)

  async function handleClaim(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!session) return
    setSubmitting(true)
    setError(null)

    try {
      const booking = await claimOffer(entryId, { appointmentTypeId, patientName }, session.token)
      setClaimedBooking(booking)
      onClaimed?.(booking)
    } catch (err) {
      if (err instanceof WaitlistClaimApiError) {
        setError(err.message)
      } else {
        setError('Something went wrong. Please try again.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  async function handleDecline() {
    if (!session) return
    setSubmitting(true)
    setError(null)

    try {
      await declineOffer(entryId, session.token)
      setDeclined(true)
      onDeclined?.()
    } catch (err) {
      if (err instanceof WaitlistClaimApiError) {
        setError(err.message)
      } else {
        setError('Something went wrong. Please try again.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  if (!session) {
    return <p className="text-sm text-gray-600">Please sign in to view your waitlist offer.</p>
  }

  if (claimedBooking) {
    return (
      <div className="flex items-start gap-2.5">
        <span className="mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-green-50 text-green-600">
          <CheckIcon />
        </span>
        <div>
          <p className="text-sm font-medium text-green-700">Slot claimed — your booking is confirmed.</p>
          <p className="mt-0.5 text-sm text-gray-600 tabular-nums">
            ₹{claimedBooking.lockedFee.toFixed(2)} ·{' '}
            {claimedBooking.paymentStatus === 'PAID' ? 'Paid' : 'Payment pending'}
          </p>
        </div>
      </div>
    )
  }

  if (declined) {
    return <p className="text-sm text-gray-600">Offer declined.</p>
  }

  return (
    <form onSubmit={handleClaim} className="space-y-3" aria-label="Claim offer">
      {countdown && (
        <p className={`text-sm ${countdown === 'Expired' ? 'text-red-700' : 'text-gray-600'}`}>{countdown}</p>
      )}
      {error && (
        <p role="alert" className="rounded-md bg-red-50 p-2 text-sm text-red-700">
          {error}
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
        {offeredDoctorProfileId ? (
          <PatientAppointmentTypeSelect
            id="appointmentTypeId"
            required
            doctorProfileId={offeredDoctorProfileId}
            token={session.token}
            value={appointmentTypeId}
            onChange={setAppointmentTypeId}
          />
        ) : (
          <p className="mt-1 rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-amber-800">
            Could not determine the doctor for this offer. Please refresh and try again.
          </p>
        )}
        {selectedType?.feeOverride != null && (
          <p className="mt-1.5 text-sm text-gray-600">
            Fee: <span className="font-semibold text-gray-900 tabular-nums">₹{selectedType.feeOverride.toFixed(2)}</span>
          </p>
        )}
      </div>
      <div className="flex gap-2">
        <button
          type="submit"
          disabled={submitting}
          className="rounded-lg bg-indigo-600 px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition-all duration-150 ease-out hover:bg-indigo-500 hover:shadow active:scale-[0.98] disabled:opacity-50 disabled:pointer-events-none disabled:active:scale-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-2"
        >
          {submitting ? 'Claiming…' : 'Claim slot'}
        </button>
        <button
          type="button"
          disabled={submitting}
          onClick={handleDecline}
          className="rounded-md border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:opacity-60"
        >
          Decline
        </button>
      </div>
    </form>
  )
}
