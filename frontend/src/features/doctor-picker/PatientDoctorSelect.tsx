import { useEffect, useState } from 'react'
import { listPatientClinicDoctors, type PatientDoctorSummary } from './api'

// See DoctorSelect's identical comment for why this is a plain <select>, not a search box - a
// clinic's doctor roster is small and bounded.
const ALL_DOCTORS_PAGE_SIZE = 500

// Replaces the raw free-text "Doctor" input on JoinWaitlistForm's "specific doctor" sub-option -
// a patient has no way to know a doctor's internal UUID. Labels include specialization and
// experience (not staffCode, an internal clinic-ops detail) to disambiguate two same-named
// doctors, the same information Discovery search already shows a patient.
export interface PatientDoctorSelectProps {
  clinicId: string
  token: string
  value: string
  onChange: (doctorProfileId: string) => void
  id?: string
  required?: boolean
}

export function PatientDoctorSelect({ clinicId, token, value, onChange, id, required }: PatientDoctorSelectProps) {
  const [doctors, setDoctors] = useState<PatientDoctorSummary[] | null>(null)

  useEffect(() => {
    let cancelled = false
    listPatientClinicDoctors(clinicId, token, { size: ALL_DOCTORS_PAGE_SIZE })
      .then((response) => {
        if (!cancelled) setDoctors(response.doctors)
      })
      .catch(() => {
        if (!cancelled) setDoctors([])
      })
    return () => {
      cancelled = true
    }
  }, [clinicId, token])

  // _diagnostics [MAJOR] - [full-repo-audit] - [SILENT_EMPTY_SELECT]: an empty roster previously
  // rendered as a silently unfillable required select with no explanation, unlike its sibling
  // AppointmentTypeSelect's identical case.
  if (doctors !== null && doctors.length === 0) {
    return (
      <p className="mt-1 rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-amber-800">
        This clinic has no doctors listed yet. Please check back later.
      </p>
    )
  }

  return (
    <select id={id} required={required} value={value} onChange={(e) => onChange(e.target.value)} className="input mt-1">
      <option value="" disabled>
        {doctors === null ? 'Loading…' : 'Select a doctor'}
      </option>
      {doctors?.map((doctor) => (
        <option key={doctor.doctorProfileId} value={doctor.doctorProfileId}>
          {doctor.name} — {doctor.specialization}, {doctor.experienceYears}{' '}
          {doctor.experienceYears === 1 ? 'yr' : 'yrs'}
        </option>
      ))}
    </select>
  )
}
