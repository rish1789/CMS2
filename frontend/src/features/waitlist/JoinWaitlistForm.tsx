import { useState, type FormEvent } from 'react'
import { joinWaitlist, WaitlistJoinApiError, type WaitlistEntryResponse } from './api'
import { loadPatientSession } from '../patient-account/token'
import { PatientDoctorSelect } from '../doctor-picker/PatientDoctorSelect'
import { IconBadge, CheckIcon } from '../../components/adminIcons'
import { ClockIcon } from '../../components/staffIcons'

export interface JoinWaitlistFormProps {
  clinicId: string
  onJoined?: (entry: WaitlistEntryResponse) => void
}

type Target = 'doctor' | 'specialization'

export function JoinWaitlistForm({ clinicId, onJoined }: JoinWaitlistFormProps) {
  const [session] = useState(() => loadPatientSession())
  const [target, setTarget] = useState<Target>('doctor')
  const [doctorProfileId, setDoctorProfileId] = useState('')
  const [specialization, setSpecialization] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [entry, setEntry] = useState<WaitlistEntryResponse | null>(null)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!session) return
    setSubmitting(true)
    setError(null)

    try {
      const request =
        target === 'doctor' ? { doctorProfileId } : { specialization }
      const response = await joinWaitlist(clinicId, request, session.token)
      setEntry(response)
      onJoined?.(response)
    } catch (err) {
      if (err instanceof WaitlistJoinApiError) {
        setError(err.message)
      } else {
        setError('Something went wrong. Please try again.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  if (!session) {
    return (
      <div className="mx-auto max-w-md rounded-xl border border-gray-200 bg-white p-6 shadow-sm">
        <p className="text-sm text-gray-600">Please sign in to join the waitlist.</p>
      </div>
    )
  }

  if (entry) {
    return (
      <div className="mx-auto max-w-md rounded-xl border border-gray-200 bg-white p-6 shadow-md">
        <div className="flex items-center gap-3">
          <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-green-50 text-green-600">
            <CheckIcon />
          </span>
          <h1 className="text-lg font-semibold text-gray-900">You&apos;re on the waitlist</h1>
        </div>
        <p className="mt-3 text-sm text-gray-600">
          {entry.specialization ? `For ${entry.specialization}. ` : ''}We&apos;ll notify you if a slot opens up.
        </p>
      </div>
    )
  }

  return (
    <form
      onSubmit={handleSubmit}
      className="mx-auto max-w-md space-y-6 rounded-xl border border-gray-200 bg-white p-6 shadow-md"
      aria-label="Join waitlist"
    >
      <div className="flex items-center gap-3">
        <IconBadge>
          <ClockIcon />
        </IconBadge>
        <h1 className="text-lg font-semibold text-gray-900">Join the waitlist</h1>
      </div>

      {error && (
        <p role="alert" className="rounded-md bg-red-50 p-3 text-sm text-red-700">
          {error}
        </p>
      )}
      <fieldset>
        <legend className="mb-2 text-sm font-medium text-gray-700">Join for</legend>
        <div className="grid grid-cols-2 gap-2">
          <label
            className={`flex cursor-pointer items-center justify-center rounded-lg border px-3 py-2.5 text-center text-sm font-medium transition-all duration-200 ease-out ${
              target === 'doctor'
                ? 'border-indigo-600 bg-indigo-600 text-white shadow-md'
                : 'border-gray-200 bg-gray-50 text-gray-700 hover:-translate-y-0.5 hover:border-indigo-300 hover:bg-white hover:shadow-sm'
            }`}
          >
            <input
              type="radio"
              name="target"
              value="doctor"
              checked={target === 'doctor'}
              onChange={() => setTarget('doctor')}
              className="sr-only"
            />
            A specific doctor
          </label>
          <label
            className={`flex cursor-pointer items-center justify-center rounded-lg border px-3 py-2.5 text-center text-sm font-medium transition-all duration-200 ease-out ${
              target === 'specialization'
                ? 'border-indigo-600 bg-indigo-600 text-white shadow-md'
                : 'border-gray-200 bg-gray-50 text-gray-700 hover:-translate-y-0.5 hover:border-indigo-300 hover:bg-white hover:shadow-sm'
            }`}
          >
            <input
              type="radio"
              name="target"
              value="specialization"
              checked={target === 'specialization'}
              onChange={() => setTarget('specialization')}
              className="sr-only"
            />
            Any doctor with a specialization
          </label>
        </div>
      </fieldset>

      {target === 'doctor' ? (
        <div>
          <label htmlFor="doctorProfileId" className="block text-sm font-medium text-gray-700">
            Doctor
          </label>
          <PatientDoctorSelect
            id="doctorProfileId"
            required
            clinicId={clinicId}
            token={session.token}
            value={doctorProfileId}
            onChange={setDoctorProfileId}
          />
        </div>
      ) : (
        <div>
          <label htmlFor="specialization" className="block text-sm font-medium text-gray-700">
            Specialization
          </label>
          <input
            id="specialization"
            required
            value={specialization}
            onChange={(e) => setSpecialization(e.target.value)}
            className="input mt-1"
          />
        </div>
      )}

      <button
        type="submit"
        disabled={submitting}
        className="w-full rounded-lg bg-indigo-600 px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition-all duration-150 ease-out hover:bg-indigo-500 hover:shadow-md active:scale-[0.98] disabled:opacity-50 disabled:pointer-events-none disabled:active:scale-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-2"
      >
        {submitting ? 'Joining…' : 'Join waitlist'}
      </button>
    </form>
  )
}
