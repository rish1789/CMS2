// Client for POST /api/v1/clinics/{clinicId}/patients/{patientId}/anonymize
// See specs/037-patient-immediate-anonymization/contracts/patient-anonymization.md

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

export interface PatientAnonymizationResponse {
  patientId: string
  anonymized: boolean
  anonymizedAt: string
}

export type PatientAnonymizationErrorBody =
  | { error: 'FORBIDDEN'; message?: string }
  | { error: 'PATIENT_NOT_FOUND'; message?: string }
  | { error: 'PATIENT_HAS_ACTIVE_FUTURE_BOOKING'; message?: string }
  | { error: 'UNAUTHORIZED'; message?: string }

export class PatientAnonymizationApiError extends Error {
  readonly body: PatientAnonymizationErrorBody

  constructor(body: PatientAnonymizationErrorBody) {
    super(defaultMessageFor(body) ?? body.message)
    this.name = 'PatientAnonymizationApiError'
    this.body = body
  }
}

function defaultMessageFor(body: PatientAnonymizationErrorBody): string {
  switch (body.error) {
    case 'FORBIDDEN':
      return 'Only Operations or ClinicAdmin staff may anonymize a patient.'
    case 'PATIENT_NOT_FOUND':
      return 'This patient could not be found.'
    case 'PATIENT_HAS_ACTIVE_FUTURE_BOOKING':
      return 'This patient has an active future booking — cancel it first.'
    case 'UNAUTHORIZED':
      return 'Your session has expired. Please sign in again.'
    default:
      return 'Something went wrong. Please try again.'
  }
}

export async function anonymizePatient(
  clinicId: string,
  patientId: string,
  token: string,
): Promise<PatientAnonymizationResponse> {
  const response = await fetch(`${API_BASE_URL}/api/v1/clinics/${clinicId}/patients/${patientId}/anonymize`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
  })

  if (!response.ok) {
    let body: PatientAnonymizationErrorBody
    try {
      body = (await response.json()) as PatientAnonymizationErrorBody
    } catch {
      body = { error: 'PATIENT_NOT_FOUND' }
    }
    throw new PatientAnonymizationApiError(body)
  }

  return (await response.json()) as PatientAnonymizationResponse
}
