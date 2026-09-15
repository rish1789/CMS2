// Client for the Super Admin clinic-verification endpoints under /api/v1/admin/clinics
// See specs/003-super-admin-verification/contracts/clinic-verification.md
//
// 040-super-admin-rbac-login: requests are now authenticated with the Super Admin bearer
// JWT issued at Clinic Portal login (replacing per-call HTTP Basic Auth) - the caller
// still owns where the token lives (super-admin/token.ts), this client just takes it as
// a parameter, keeping it stateless and easy to test.

import type { RejectionReason } from '../../components/rejectionReason'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

export type ClinicListStatus = 'PENDING' | 'VERIFIED' | 'REJECTED'

export interface ClinicSummary {
  clinicId: string
  name: string
  address: string
  contactEmail: string | null
  contactMobile: string | null
  createdAt: string
  rejected: boolean
  rejectionReason: RejectionReason | null
  rejectionDetail: string | null
  rejectedAt: string | null
  rejectedBy: string | null
}

export interface VerifyClinicResponse {
  clinicId: string
  verified: boolean
}

export interface BulkRejectResult {
  succeeded: string[]
  failed: Record<string, string>
}

export interface BulkDeleteResult {
  succeeded: string[]
  failed: Record<string, string>
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
  if (status === 404) return 'Clinic not found.'
  return `Request failed (${status}).`
}

async function messageFromBody(response: Response): Promise<string | undefined> {
  try {
    const body: unknown = await response.json()
    if (body && typeof body === 'object' && 'message' in body) {
      const message = (body as { message?: unknown }).message
      if (typeof message === 'string') return message
    }
  } catch {
    // No JSON body - fall back to the status-based default message.
  }
  return undefined
}

async function throwIfNotOk(response: Response): Promise<void> {
  if (!response.ok) {
    throw new AdminApiError(response.status, await messageFromBody(response))
  }
}

export interface ClinicListResult {
  clinics: ClinicSummary[]
  page: number
  pageSize: number
  totalCount: number
}

export type ClinicSortField = 'name' | 'createdAt' | 'rejectedAt'
export type SortDirection = 'asc' | 'desc'

export interface ListClinicsParams {
  page?: number
  size?: number
  q?: string
  reason?: RejectionReason
  sort?: ClinicSortField
  direction?: SortDirection
}

// pagination-unification-2026-09-10: paginated server-side - the platform-wide pending-
// verification queue grows without bound as more clinics register.
//
// super-admin-console-redesign-2026-09-11: `status` replaced the old boolean `verified` param -
// a third Rejected state now exists alongside Pending/Verified. `q`/`reason`/`sort`/`direction`
// added for the same stress-test reason as pagination itself - hundreds to thousands of clinics
// at scale need server-side search and sort, not a client-side filter over a downloaded page.
export async function listClinics(
  status: ClinicListStatus,
  token: string,
  params: ListClinicsParams = {},
): Promise<ClinicListResult> {
  const query = new URLSearchParams({ status })
  if (params.page !== undefined) query.set('page', String(params.page))
  if (params.size !== undefined) query.set('size', String(params.size))
  if (params.q) query.set('q', params.q)
  if (params.reason) query.set('reason', params.reason)
  if (params.sort) query.set('sort', params.sort)
  if (params.direction) query.set('direction', params.direction)

  const response = await fetch(`${API_BASE_URL}/api/v1/admin/clinics?${query.toString()}`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  await throwIfNotOk(response)
  return (await response.json()) as ClinicListResult
}

export async function verifyClinic(clinicId: string, token: string): Promise<VerifyClinicResponse> {
  const response = await fetch(`${API_BASE_URL}/api/v1/admin/clinics/${clinicId}/verify`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
  })
  await throwIfNotOk(response)
  return (await response.json()) as VerifyClinicResponse
}

export async function unverifyClinic(clinicId: string, token: string): Promise<VerifyClinicResponse> {
  const response = await fetch(`${API_BASE_URL}/api/v1/admin/clinics/${clinicId}/unverify`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
  })
  await throwIfNotOk(response)
  return (await response.json()) as VerifyClinicResponse
}

export async function rejectClinic(
  clinicId: string,
  reasonCode: RejectionReason,
  detail: string,
  token: string,
): Promise<ClinicSummary> {
  const response = await fetch(`${API_BASE_URL}/api/v1/admin/clinics/${clinicId}/reject`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify({ reasonCode, detail: detail === '' ? undefined : detail }),
  })
  await throwIfNotOk(response)
  return (await response.json()) as ClinicSummary
}

export async function rejectClinicsBulk(
  clinicIds: string[],
  reasonCode: RejectionReason,
  detail: string,
  token: string,
): Promise<BulkRejectResult> {
  const response = await fetch(`${API_BASE_URL}/api/v1/admin/clinics/reject-bulk`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify({ ids: clinicIds, reasonCode, detail: detail === '' ? undefined : detail }),
  })
  await throwIfNotOk(response)
  return (await response.json()) as BulkRejectResult
}

export async function restoreClinic(clinicId: string, token: string): Promise<ClinicSummary> {
  const response = await fetch(`${API_BASE_URL}/api/v1/admin/clinics/${clinicId}/restore`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
  })
  await throwIfNotOk(response)
  return (await response.json()) as ClinicSummary
}

/**
 * super-admin-console-redesign: permanent, irreversible delete - only ever valid on an
 * already-rejected clinic (enforced server-side too). Refused with a DELETION_BLOCKED error if
 * real activity (a patient record, schedule, or session) is attached.
 */
export async function deleteClinic(clinicId: string, token: string): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/api/v1/admin/clinics/${clinicId}`, {
    method: 'DELETE',
    headers: { Authorization: `Bearer ${token}` },
  })
  await throwIfNotOk(response)
}

export async function deleteClinicsBulk(clinicIds: string[], token: string): Promise<BulkDeleteResult> {
  const response = await fetch(`${API_BASE_URL}/api/v1/admin/clinics/delete-bulk`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify({ ids: clinicIds }),
  })
  await throwIfNotOk(response)
  return (await response.json()) as BulkDeleteResult
}
