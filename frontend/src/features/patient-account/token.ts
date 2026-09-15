// Shared storage for the PATIENT-audience JWT issued by POST /api/v1/patients/login.
// Mirrors ../staff-login/token.ts: a bearer token, so sessionStorage (not localStorage) is
// appropriate for the same reason credentials are never persisted beyond the tab elsewhere
// in this codebase - it's a credential, not app state.
//
// 021-patient-self-service-booking is the first feature needing this: no prior
// patient-authenticated frontend feature existed, so login never had anywhere to put the
// token it already receives.

const SESSION_STORAGE_KEY = 'cms.patientToken'

export interface StoredPatientSession {
  token: string
  patientAccountId: string
  email: string
}

export function loadPatientSession(): StoredPatientSession | null {
  try {
    const raw = sessionStorage.getItem(SESSION_STORAGE_KEY)
    return raw ? (JSON.parse(raw) as StoredPatientSession) : null
  } catch {
    return null
  }
}

export function storePatientSession(session: StoredPatientSession | null): void {
  try {
    if (session) {
      sessionStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(session))
    } else {
      sessionStorage.removeItem(SESSION_STORAGE_KEY)
    }
  } catch {
    // sessionStorage unavailable - session just won't survive a reload.
  }
}
