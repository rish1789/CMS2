// Client for POST /api/v1/admin/sessions/generate
// See specs/015-nightly-session-generation/contracts/session-generation.md
//
// 040-super-admin-rbac-login: authenticated with the Super Admin bearer JWT issued at
// Clinic Portal login (replacing per-call HTTP Basic Auth), mirroring clinic-verification/api.ts.

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

export interface GenerateSessionsResponse {
  runDate: string
  sessionsCreated: number
}

export class AdminApiError extends Error {
  readonly status: number

  constructor(status: number, message?: string) {
    super(message ?? defaultMessageFor(status))
    this.name = 'AdminApiError'
    this.status = status
  }
}

function defaultMessageFor(status: number): string {
  if (status === 401) return 'Your Super Admin session has expired. Please sign in again.'
  return `Request failed (${status}).`
}

export async function generateSessions(token: string): Promise<GenerateSessionsResponse> {
  const response = await fetch(`${API_BASE_URL}/api/v1/admin/sessions/generate`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
  })

  if (!response.ok) {
    throw new AdminApiError(response.status)
  }

  return (await response.json()) as GenerateSessionsResponse
}
