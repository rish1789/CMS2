import { useEffect, useState } from 'react'
import { listPatientAppointmentTypes, type AppointmentTypeResponse } from './api'

// Patient-facing analog of AppointmentTypeSelect - backs ClaimOfferCard's picker, replacing the
// raw free-text "Appointment Type ID" input a patient had no way to fill in correctly.
export interface PatientAppointmentTypeSelectProps {
  doctorProfileId: string
  token: string
  value: string
  onChange: (appointmentTypeId: string) => void
  id?: string
  required?: boolean
}

export function PatientAppointmentTypeSelect({
  doctorProfileId,
  token,
  value,
  onChange,
  id,
  required,
}: PatientAppointmentTypeSelectProps) {
  const [types, setTypes] = useState<AppointmentTypeResponse[] | null>(null)

  useEffect(() => {
    let cancelled = false
    listPatientAppointmentTypes(doctorProfileId, token)
      .then((response) => {
        if (!cancelled) setTypes(response)
      })
      .catch(() => {
        if (!cancelled) setTypes([])
      })
    return () => {
      cancelled = true
    }
  }, [doctorProfileId, token])

  if (types !== null && types.length === 0) {
    return (
      <p className="mt-1 rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-amber-800">
        This doctor has no appointment types configured yet. Please contact the clinic.
      </p>
    )
  }

  return (
    <select
      id={id}
      required={required}
      value={value}
      onChange={(e) => onChange(e.target.value)}
      className="input mt-1"
    >
      <option value="" disabled>
        {types === null ? 'Loading…' : 'Select an appointment type'}
      </option>
      {types?.map((type) => (
        <option key={type.id} value={type.id}>
          {type.name}
        </option>
      ))}
    </select>
  )
}
