import { useEffect, useState } from 'react'
import { listAppointmentTypes, type AppointmentTypeResponse } from './api'

// _diagnostics [HIGH] - [APPOINTMENT_TYPE_CONFIG] - [MISSING_UI]: backs the raw free-text
// "Appointment Type ID" inputs that previously required the operator/patient to already know the
// UUID out-of-band.
export interface AppointmentTypeSelectProps {
  doctorProfileId: string
  token: string
  value: string
  onChange: (appointmentTypeId: string) => void
  id?: string
  required?: boolean
}

export function AppointmentTypeSelect({
  doctorProfileId,
  token,
  value,
  onChange,
  id,
  required,
}: AppointmentTypeSelectProps) {
  const [types, setTypes] = useState<AppointmentTypeResponse[] | null>(null)

  useEffect(() => {
    let cancelled = false
    listAppointmentTypes(doctorProfileId, token)
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

  // staff-console-audit-2026-09-10 P1: an empty list previously rendered as a silently
  // unfillable required select with no explanation - surface it explicitly instead.
  if (types !== null && types.length === 0) {
    return (
      <p className="mt-1 rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-amber-800">
        This doctor has no appointment types configured yet — add one from their Doctors page
        before booking.
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
