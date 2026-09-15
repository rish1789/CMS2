import { useState } from 'react'
import { anonymizePatient, PatientAnonymizationApiError } from './api'
import { loadStaffSession, storeStaffSession } from '../staff-login/token'

export interface AnonymizePatientButtonProps {
  clinicId: string
  patientId: string
  onAnonymized?: () => void
}

export function AnonymizePatientButton({ clinicId, patientId, onAnonymized }: AnonymizePatientButtonProps) {
  const [session, setSession] = useState(() => loadStaffSession())
  const [confirming, setConfirming] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [anonymized, setAnonymized] = useState(false)

  async function handleConfirm() {
    if (!session) return
    setSubmitting(true)
    setError(null)

    try {
      await anonymizePatient(clinicId, patientId, session.token)
      setAnonymized(true)
      setConfirming(false)
      onAnonymized?.()
    } catch (err) {
      if (err instanceof PatientAnonymizationApiError) {
        if (err.body.error === 'UNAUTHORIZED') {
          storeStaffSession(null)
          setSession(null)
        }
        setError(err.message)
      } else {
        setError('Something went wrong. Please try again.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  if (!session) {
    return null
  }

  if (anonymized) {
    return <p className="text-sm text-green-700">Patient anonymized.</p>
  }

  return (
    <div className="space-y-2">
      {error && (
        <p role="alert" className="rounded-md bg-red-50 p-2 text-sm text-red-700">
          {error}
        </p>
      )}
      {confirming ? (
        <div className="flex items-center gap-2">
          <span className="text-sm text-gray-700">Anonymize this patient's identifying information?</span>
          <button
            type="button"
            onClick={handleConfirm}
            disabled={submitting}
            className="rounded-lg bg-red-600 px-3.5 py-2 text-sm font-semibold text-white shadow-sm transition-all duration-150 ease-out hover:bg-red-500 hover:shadow active:scale-[0.98] disabled:opacity-50 disabled:pointer-events-none disabled:active:scale-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-red-500 focus-visible:ring-offset-2"
          >
            {submitting ? 'Anonymizing…' : 'Confirm'}
          </button>
          <button
            type="button"
            onClick={() => setConfirming(false)}
            disabled={submitting}
            className="rounded-lg bg-gray-100 px-3.5 py-2 text-sm font-semibold text-gray-700 transition-all duration-150 ease-out hover:bg-gray-200 active:scale-[0.98] disabled:opacity-50 disabled:pointer-events-none disabled:active:scale-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-gray-400 focus-visible:ring-offset-2"
          >
            Cancel
          </button>
        </div>
      ) : (
        <button
          type="button"
          onClick={() => setConfirming(true)}
          className="rounded-lg bg-red-600 px-3.5 py-2 text-sm font-semibold text-white shadow-sm transition-all duration-150 ease-out hover:bg-red-500 hover:shadow active:scale-[0.98] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-red-500 focus-visible:ring-offset-2"
        >
          Anonymize patient
        </button>
      )}
    </div>
  )
}
