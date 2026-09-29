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

// 056-design-copy-quality-pass (2026-09-16 dashboard rebuild): PatientAccount (backend
// domain/PatientAccount.java) has no name field at all - only email, password hash, mobile,
// notification opt-ins. There is no "the patient's name" concept at the account level anywhere
// in this system (names only exist on per-clinic Patient records, which may differ or not
// exist yet). Adding a real name field would be a schema change (migration + signup field +
// backfill decision), not a restyle - out of scope here. This derives a first-name-like display
// string from the email's local part instead, the same honest-fallback approach this codebase
// already uses elsewhere (LoginForm's own post-login state shows the email, not a fabricated
// name) - "priya.sharma@example.com" -> "Priya Sharma", "priya@example.com" -> "Priya".
export function deriveDisplayNameFromEmail(email: string): string {
  const localPart = email.split('@')[0] || email
  const words = localPart.split(/[._-]+/).filter(Boolean)
  if (words.length === 0) return email
  return words.map((word) => word.charAt(0).toUpperCase() + word.slice(1)).join(' ')
}
