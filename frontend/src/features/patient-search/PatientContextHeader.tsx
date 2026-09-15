import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { getPatient, type PatientSearchResult } from './api'
import { loadStaffSession } from '../staff-login/token'

export interface PatientContextHeaderProps {
  clinicId: string
  patientId: string
}

// staff-console-audit-2026-09-10 P1: the Anonymize page previously rendered as a bare red
// button on an otherwise blank page - no patient name, no heading, nothing to confirm you had
// the right person before erasing their identifying information.
export function PatientContextHeader({ clinicId, patientId }: PatientContextHeaderProps) {
  const [patient, setPatient] = useState<PatientSearchResult | null>(null)
  const [failed, setFailed] = useState(false)

  useEffect(() => {
    const session = loadStaffSession()
    if (!session) return
    getPatient(clinicId, patientId, session.token)
      .then(setPatient)
      .catch(() => setFailed(true))
  }, [clinicId, patientId])

  // Silent on failure - AnonymizePatientButton's own action, if attempted, will surface its own
  // PATIENT_NOT_FOUND-style error; a second redundant banner here would just add noise.
  if (failed) return null

  if (!patient) {
    return (
      <output className="block mx-auto max-w-md space-y-2">
        <span className="sr-only">Loading patient details…</span>
        <div aria-hidden="true" className="h-4 w-32 animate-pulse rounded bg-gray-100" />
        <div aria-hidden="true" className="h-6 w-48 animate-pulse rounded bg-gray-100" />
      </output>
    )
  }

  return (
    <div className="mx-auto max-w-md">
      <Link
        to={`/staff/clinics/${clinicId}/patients/search`}
        className="inline-flex items-center gap-1 text-sm font-medium text-indigo-600 transition-colors duration-150 hover:text-indigo-700"
      >
        ← Back to patient search
      </Link>
      <h1 className="mt-2 text-lg font-semibold text-gray-900">{patient.name}</h1>
      {patient.phone && <p className="mt-0.5 text-sm text-gray-500">{patient.phone}</p>}
    </div>
  )
}
