import { useEffect, useState } from 'react'
import { listClinicDoctors, type DoctorSummary } from './api'

// A generously large page size, not "no limit" - listClinicDoctors is now a real paginated
// endpoint (pagination-unification-2026-09-10), but this control still wants every doctor in
// one response to populate a plain <select>. Large enough that no real clinic's doctor roster
// exceeds it in practice; a clinic that somehow did would need this control redesigned as a
// search box (like PatientPicker), not a silently-truncated dropdown.
const ALL_DOCTORS_PAGE_SIZE = 500

// _diagnostics [P0] - [staff-console-audit-2026-09-10] - [RAW_ID_ENTRY]: backs the raw free-text
// "Doctor" input in StaffJoinWaitlistForm. A clinic's doctor list is small and bounded (unlike
// patients), so a plain <select> - not a search box - is the right control here, mirroring
// AppointmentTypeSelect. Includes staffCode in the option label since this clinic has two
// doctors both named "Gauresh Kumar" (staff-console-audit-2026-09-10 P1 finding).
export interface DoctorSelectProps {
  clinicId: string
  token: string
  value: string
  onChange: (doctorProfileId: string) => void
  id?: string
  required?: boolean
}

export function DoctorSelect({ clinicId, token, value, onChange, id, required }: DoctorSelectProps) {
  const [doctors, setDoctors] = useState<DoctorSummary[] | null>(null)

  useEffect(() => {
    let cancelled = false
    listClinicDoctors(clinicId, token, { size: ALL_DOCTORS_PAGE_SIZE })
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
        No doctors are staffed at this clinic yet — onboard one from the Doctors page first.
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
          {doctor.name} — {doctor.staffCode}
        </option>
      ))}
    </select>
  )
}
