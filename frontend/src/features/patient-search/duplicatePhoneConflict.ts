// 074-duplicate-patient-phone: the 409 PATIENT_PHONE_ALREADY_REGISTERED body shared by the staff
// fixed-time, staff queue and front-desk walk-in APIs.

export interface ExistingPatientRef {
  id: string
  name: string
}

export interface DuplicatePhoneConflictBody {
  error: 'PATIENT_PHONE_ALREADY_REGISTERED'
  message?: string
  // Null when only the database caught a concurrent registration - the record can't be named then.
  existingPatient: ExistingPatientRef | null
}

/** The conflict body from any of the three APIs' error shapes, or null when it is a different error. */
export function duplicatePhoneConflictOf(body: unknown): DuplicatePhoneConflictBody | null {
  const candidate = body as Partial<DuplicatePhoneConflictBody> | undefined
  if (candidate?.error !== 'PATIENT_PHONE_ALREADY_REGISTERED') return null
  return { error: 'PATIENT_PHONE_ALREADY_REGISTERED', message: candidate.message, existingPatient: candidate.existingPatient ?? null }
}
