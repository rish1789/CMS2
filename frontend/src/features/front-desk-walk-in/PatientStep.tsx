import { useEffect, useState } from 'react'
import { FormField } from '../../components/FormField'
import { PatientPicker } from '../patient-search/PatientPicker'
import { searchPatients, type PatientSearchResult } from '../patient-search/api'

export type PatientChoice =
  | { mode: 'existing'; patientId: string; name: string | null }
  | { mode: 'new'; name: string; phone: string; email: string }

export interface PatientStepProps {
  clinicId: string
  token: string
  value: PatientChoice
  onChange: (value: PatientChoice) => void
  error?: string
}

const TEN_DIGITS = /^\d{10}$/

// FR-003: a new patient's phone that already belongs to a patient of this clinic is offered back,
// so front-desk staff reuse that record instead of creating a duplicate.
function usePhoneMatches(clinicId: string, token: string, phone: string): PatientSearchResult[] {
  const [matches, setMatches] = useState<PatientSearchResult[]>([])
  const trimmed = phone.replace(/\s/g, '')
  useEffect(() => {
    if (!TEN_DIGITS.test(trimmed)) return
    let cancelled = false
    searchPatients(clinicId, trimmed, token, { size: 5 })
      .then((result) => {
        if (!cancelled) setMatches(result.patients.filter((p) => p.phone?.replace(/\s/g, '') === trimmed && !p.anonymizedAt))
      })
      .catch(() => {
        if (!cancelled) setMatches([])
      })
    return () => {
      cancelled = true
    }
  }, [clinicId, token, trimmed])
  return TEN_DIGITS.test(trimmed) ? matches : []
}

function modeButtonClass(active: boolean): string {
  return `flex cursor-pointer items-center justify-center rounded-lg border px-3 py-2 text-sm font-medium transition-colors ${
    active ? 'border-indigo-600 bg-indigo-600 text-white' : 'border-gray-200 bg-white text-gray-700 hover:border-indigo-300'
  }`
}

export function PatientStep({ clinicId, token, value, onChange, error }: PatientStepProps) {
  const phoneMatches = usePhoneMatches(clinicId, token, value.mode === 'new' ? value.phone : '')

  return (
    <div className="space-y-4">
      <div role="radiogroup" aria-label="Patient" className="grid grid-cols-2 gap-2">
        <label className={modeButtonClass(value.mode === 'existing')}>
          <input
            type="radio"
            className="sr-only"
            checked={value.mode === 'existing'}
            onChange={() => onChange({ mode: 'existing', patientId: '', name: null })}
          />
          Existing patient
        </label>
        <label className={modeButtonClass(value.mode === 'new')}>
          <input
            type="radio"
            className="sr-only"
            checked={value.mode === 'new'}
            onChange={() => onChange({ mode: 'new', name: '', phone: '', email: '' })}
          />
          New patient
        </label>
      </div>

      {value.mode === 'existing' && value.name !== null && value.patientId !== '' ? (
        <div className="flex items-center justify-between rounded-lg border border-gray-200 bg-gray-50 px-3 py-2 text-sm">
          <span>
            <span className="block text-xs text-gray-500">Selected patient</span>
            <span className="font-medium text-gray-900">{value.name}</span>
          </span>
          <button
            type="button"
            className="text-sm font-medium text-indigo-700 hover:text-indigo-600"
            onClick={() => onChange({ mode: 'existing', patientId: '', name: null })}
          >
            Change
          </button>
        </div>
      ) : value.mode === 'existing' ? (
        <div>
          <label htmlFor="walkInPatientId" className="block text-sm font-medium text-gray-700">
            Search by name or phone
          </label>
          <PatientPicker
            id="walkInPatientId"
            clinicId={clinicId}
            token={token}
            value={value.patientId}
            onChange={(patientId, patient) => onChange({ mode: 'existing', patientId, name: patient?.name ?? null })}
          />
        </div>
      ) : (
        <div className="grid gap-4 sm:grid-cols-2">
          <div className="sm:col-span-2">
            <FormField label="Name" htmlFor="walkInName" required>
              <input
                id="walkInName"
                className="input"
                autoComplete="off"
                value={value.name}
                onChange={(e) => onChange({ ...value, name: e.target.value })}
              />
            </FormField>
          </div>
          <FormField label="Phone" htmlFor="walkInPhone" hint="Optional">
            <input
              id="walkInPhone"
              className="input"
              inputMode="numeric"
              autoComplete="off"
              value={value.phone}
              onChange={(e) => onChange({ ...value, phone: e.target.value })}
            />
          </FormField>
          <FormField label="Email" htmlFor="walkInEmail" hint="Optional">
            <input
              id="walkInEmail"
              type="email"
              className="input"
              autoComplete="off"
              value={value.email}
              onChange={(e) => onChange({ ...value, email: e.target.value })}
            />
          </FormField>
          {phoneMatches.length > 0 && (
            <div className="sm:col-span-2 rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm">
              <p className="text-amber-900">This phone number already belongs to a patient of this clinic.</p>
              <ul className="mt-2 space-y-1">
                {phoneMatches.map((match) => (
                  <li key={match.patientId}>
                    <button
                      type="button"
                      className="font-medium text-indigo-700 hover:text-indigo-600"
                      onClick={() => onChange({ mode: 'existing', patientId: match.patientId, name: match.name })}
                    >
                      Use {match.name}
                    </button>
                  </li>
                ))}
              </ul>
            </div>
          )}
        </div>
      )}

      {error && (
        <p role="alert" className="text-sm text-red-600">
          {error}
        </p>
      )}
    </div>
  )
}
