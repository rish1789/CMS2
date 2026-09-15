// Shared storage for the SUPER_ADMIN-audience JWT issued by POST /api/v1/staff/login
// when the submitted credentials resolve to the configured Super Admin (040-super-admin-rbac-login).
// A bearer token, so sessionStorage (not localStorage) - mirrors staff-login/token.ts and
// patient-account/token.ts exactly: it's a credential, not app state.

const SESSION_STORAGE_KEY = 'cms.superAdminToken'

export interface StoredSuperAdminSession {
  token: string
  username: string
}

export function loadSuperAdminSession(): StoredSuperAdminSession | null {
  try {
    const raw = sessionStorage.getItem(SESSION_STORAGE_KEY)
    return raw ? (JSON.parse(raw) as StoredSuperAdminSession) : null
  } catch {
    return null
  }
}

export function storeSuperAdminSession(session: StoredSuperAdminSession | null): void {
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
