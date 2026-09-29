import { useState, type FormEvent } from 'react'
import { staffJoinWaitlist, type WaitlistEntryResponse } from './api'
import { ApiError } from '../../lib/apiClient'
import { loadStaffSession } from '../staff-login/token'
import { DoctorSelect } from '../doctor-picker/DoctorSelect'
import { PatientPicker } from '../patient-search/PatientPicker'
import type { PatientSearchResult } from '../patient-search/api'

// _diagnostics [HIGH] - [WAITLIST_JOIN staff] - [MISSING_STAFF_UI]: StaffWaitlistController's
// POST /api/v1/clinics/{clinicId}/waitlist had no frontend client function or form anywhere.
//
// 2026-09-16 real bug found live: "Patient account" was a raw free-text UUID input (unlike the
// Doctor field, already a picker) - a staff member naturally typed the patient's name into it,
// which fell through every specific exception handler to the backend's generic "The request
// could not be processed." (ApiErrorController's last-resort fallback for an unhandled
// exception - here, a malformed-UUID JSON binding failure). Wired onto PatientPicker instead,
// the same fix already applied to WalkInForm/BookSlotForm for this exact bug class.
export interface StaffJoinWaitlistFormProps {
  clinicId: string
  onJoined?: (entry: WaitlistEntryResponse) => void
}

type Target = 'doctor' | 'specialization'

export function StaffJoinWaitlistForm({ clinicId, onJoined }: StaffJoinWaitlistFormProps) {
  const [session] = useState(() => loadStaffSession())
  const [patientId, setPatientId] = useState('')
  const [selectedPatient, setSelectedPatient] = useState<PatientSearchResult | null>(null)
  const [target, setTarget] = useState<Target>('doctor')
  const [doctorProfileId, setDoctorProfileId] = useState('')
  const [specialization, setSpecialization] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [entry, setEntry] = useState<WaitlistEntryResponse | null>(null)

  function handlePatientChange(id: string, patient: PatientSearchResult | null) {
    setPatientId(id)
    setSelectedPatient(patient)
  }

  // A clinic-scoped Patient record can exist with no linked PatientAccount (e.g. a walk-in who
  // never signed up) - the waitlist endpoint needs the account, since that's who logs back in
  // to claim or decline the eventual offer (029-self-service-waitlist-claim).
  const selectedHasNoAccount = selectedPatient !== null && selectedPatient.patientAccountId === null

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!session || !selectedPatient?.patientAccountId) return
    setSubmitting(true)
    setError(null)

    try {
      const patientAccountId = selectedPatient.patientAccountId
      const request =
        target === 'doctor' ? { patientAccountId, doctorProfileId } : { patientAccountId, specialization }
      const response = await staffJoinWaitlist(clinicId, request, session.token)
      setEntry(response)
      onJoined?.(response)
    } catch (err) {
      if (err instanceof ApiError) {
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
      <div className="mx-auto max-w-md rounded-lg border border-gray-200 bg-white p-6 shadow-sm">
        <p className="text-sm text-gray-600">Please sign in as staff to join a patient to the waitlist.</p>
      </div>
    )
  }

  if (entry) {
    return (
      <div className="mx-auto max-w-md rounded-lg border border-gray-200 bg-white p-6 shadow-sm">
        <p className="text-sm text-green-700">
          Patient added to the waitlist{entry.specialization ? ` for ${entry.specialization}` : ''}.
        </p>
      </div>
    )
  }

  // staff-console-audit-2026-09-10 P2: this form used to be "naked" - no card, no max-w, no
  // heading - unlike every sibling staff-tool form (Walk-in, Book slot, Onboard staff).
  return (
    <form
      onSubmit={handleSubmit}
      className="mx-auto max-w-md space-y-6 rounded-lg border border-gray-200 bg-white p-6 shadow-sm"
      aria-label="Join patient to waitlist"
    >
      <div>
        <h1 className="text-lg font-semibold text-gray-900">Join waitlist</h1>
        <p className="mt-1 text-sm text-gray-600">Add a patient to the waitlist for a doctor or specialization.</p>
      </div>

      {error && (
        <p role="alert" className="rounded-md bg-red-50 p-3 text-sm text-red-700">
          {error}
        </p>
      )}
      <div>
        <label htmlFor="patientAccountId" className="block text-sm font-medium text-gray-700">
          Patient
        </label>
        <PatientPicker
          id="patientAccountId"
          required
          clinicId={clinicId}
          token={session.token}
          value={patientId}
          onChange={handlePatientChange}
        />
        {selectedHasNoAccount && (
          <p className="mt-1.5 text-sm text-amber-700">
            {selectedPatient.name} doesn't have an online account yet, so they can't be added to the waitlist (they'd
            need one to be notified and claim the offer later).
          </p>
        )}
      </div>

      <fieldset className="space-y-2">
        <legend className="text-sm font-medium text-gray-700">Join for</legend>
        <label className="flex items-center gap-2 text-sm text-gray-700">
          <input
            type="radio"
            name="target"
            value="doctor"
            checked={target === 'doctor'}
            onChange={() => setTarget('doctor')}
          />
          A specific doctor
        </label>
        <label className="flex items-center gap-2 text-sm text-gray-700">
          <input
            type="radio"
            name="target"
            value="specialization"
            checked={target === 'specialization'}
            onChange={() => setTarget('specialization')}
          />
          Any doctor with a specialization
        </label>
      </fieldset>

      {target === 'doctor' ? (
        <div>
          <label htmlFor="doctorProfileId" className="block text-sm font-medium text-gray-700">
            Doctor
          </label>
          <DoctorSelect
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
        disabled={submitting || !selectedPatient?.patientAccountId}
        className="w-full rounded-lg bg-indigo-600 px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition-all duration-150 ease-out hover:bg-indigo-500 hover:shadow active:scale-[0.98] disabled:opacity-50 disabled:pointer-events-none disabled:active:scale-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-2"
      >
        {submitting ? 'Joining…' : 'Join waitlist'}
      </button>
    </form>
  )
}
