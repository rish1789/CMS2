// 074-duplicate-patient-phone (PB-001, PB-002): the one conflict notice the staff fixed-time,
// staff queue and front-desk walk-in forms show when a new patient's phone already belongs to an
// unlinked patient at this clinic. Staff decide - book the existing patient, or fix the number;
// nothing is linked or merged automatically.

import type { DuplicatePhoneConflictBody, ExistingPatientRef } from './duplicatePhoneConflict'

export interface DuplicatePhoneConflictProps {
  conflict: DuplicatePhoneConflictBody
  /** Button text for the named patient, e.g. "Book Asha Rao instead". */
  actionLabel: (name: string) => string
  onUseExisting: (patient: ExistingPatientRef) => void
  busy?: boolean
}

export function DuplicatePhoneConflict({ conflict, actionLabel, onUseExisting, busy = false }: DuplicatePhoneConflictProps) {
  const existing = conflict.existingPatient
  return (
    <div role="alert" className="space-y-2 rounded-md bg-red-50 p-3 text-sm text-red-700">
      <p>
        A patient with this phone number is already registered at this clinic
        {existing ? (
          <>
            : <span className="font-semibold">{existing.name}</span>.
          </>
        ) : (
          '.'
        )}{' '}
        {existing
          ? 'Book them instead, or check the number if this is someone else.'
          : 'Search for them under Existing patient, or check the number if this is someone else.'}
      </p>
      {existing && (
        <button
          type="button"
          disabled={busy}
          onClick={() => onUseExisting(existing)}
          className="rounded-lg border border-red-200 bg-white px-3 py-1.5 font-medium text-red-700 transition-colors duration-150 hover:bg-red-50 disabled:opacity-50"
        >
          {actionLabel(existing.name)}
        </button>
      )}
    </div>
  )
}
