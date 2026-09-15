// Client for POST /api/v1/patients/signup and POST /api/v1/patients/login
// See specs/002-patient-account-login/contracts/patient-account.md

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

export interface SignupPatientRequest {
  email: string
  password: string
  mobile?: string
}

export interface SignupPatientResponse {
  patientAccountId: string
  email: string
}

export type SignupPatientErrorBody =
  | { error: 'INVALID_PASSWORD'; message: string; failedRules: string[] }
  | { error: 'INVALID_MOBILE_NUMBER'; message: string }
  | { error: 'EMAIL_ALREADY_IN_USE'; message: string }
  | { error: 'MISSING_REQUIRED_FIELD'; field: string; message?: string }
  | { error: 'SIGNUP_FAILED'; message?: string }

export class SignupPatientApiError extends Error {
  readonly body: SignupPatientErrorBody

  constructor(body: SignupPatientErrorBody) {
    super(body.message ?? body.error)
    this.name = 'SignupPatientApiError'
    this.body = body
  }
}

export async function signupPatient(
  payload: SignupPatientRequest,
): Promise<SignupPatientResponse> {
  const response = await fetch(`${API_BASE_URL}/api/v1/patients/signup`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })

  if (!response.ok) {
    const body = (await response.json()) as SignupPatientErrorBody
    throw new SignupPatientApiError(body)
  }

  return (await response.json()) as SignupPatientResponse
}

export interface LoginPatientRequest {
  email: string
  password: string
}

export interface LoginPatientResponse {
  token: string
  patientAccountId: string
  email: string
}

export type LoginPatientErrorBody =
  | { error: 'INVALID_CREDENTIALS'; message: string }
  | { error: 'MISSING_REQUIRED_FIELD'; field: string; message?: string }

export class LoginPatientApiError extends Error {
  readonly body: LoginPatientErrorBody

  constructor(body: LoginPatientErrorBody) {
    super(body.message ?? body.error)
    this.name = 'LoginPatientApiError'
    this.body = body
  }
}

export async function loginPatient(
  payload: LoginPatientRequest,
): Promise<LoginPatientResponse> {
  const response = await fetch(`${API_BASE_URL}/api/v1/patients/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })

  if (!response.ok) {
    const body = (await response.json()) as LoginPatientErrorBody
    throw new LoginPatientApiError(body)
  }

  return (await response.json()) as LoginPatientResponse
}
