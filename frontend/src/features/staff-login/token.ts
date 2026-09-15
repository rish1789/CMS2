// Shared storage for the STAFF-audience JWT issued by POST /api/v1/staff/login.
// A bearer token, so sessionStorage (not localStorage) is appropriate for the same
// reason credentials are never persisted beyond the tab in clinic-verification (003)
// and patient-account (002) - it's a credential, not app state.
//
// Used by both this feature (staff-login) and staff-onboarding, which needs the token
// to call the ClinicAdmin-only onboarding endpoint.

const SESSION_STORAGE_KEY = 'cms.staffToken'

export interface StoredStaffSession {
  token: string
  accountId: string
  email: string
}

export function loadStaffSession(): StoredStaffSession | null {
  try {
    const raw = sessionStorage.getItem(SESSION_STORAGE_KEY)
    return raw ? (JSON.parse(raw) as StoredStaffSession) : null
  } catch {
    return null
  }
}

export function storeStaffSession(session: StoredStaffSession | null): void {
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
