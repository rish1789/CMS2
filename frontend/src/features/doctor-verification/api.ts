// Client for the Super Admin doctor license-verification endpoints under /api/v1/admin/doctors
// See specs/007-doctor-profile-license-queue/contracts/doctor-verification.md
//
// 040-super-admin-rbac-login: requests are now authenticated with the Super Admin bearer
// JWT issued at Clinic Portal login (replacing per-call HTTP Basic Auth) - same mechanism
// as clinic-verification/api.ts.

import type { RejectionReason } from '../../components/rejectionReason'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

export type DoctorListStatus = 'PENDING' | 'VERIFIED' | 'REJECTED'

export interface DoctorProfileSummary {
  doctorProfileId: string
  accountId: string
  accountName: string
  accountEmail: string
  specialization: string
  licenseNumber: string
  experienceYears: number
  licenseVerified: boolean
  visible: boolean
  rejected: boolean
  rejectionReason: RejectionReason | null
  rejectionDetail: string | null
  rejectedAt: string | null
  rejectedBy: string | null
}

export interface BulkRejectResult {
  succeeded: string[]
  failed: Record<string, string>
}

export interface BulkDeleteResult {
  succeeded: string[]
  failed: Record<string, string>
}

export interface VerifyDoctorResponse {
  doctorProfileId: string
  licenseVerified: boolean
}

export interface RevokeDoctorResponse {
  doctorProfileId: string
  licenseVerified: boolean
}

export interface EditDoctorRequest {
  specialization: string
  licenseNumber: string
  experienceYears: number
  visible: boolean
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
  if (status === 404) return 'Doctor profile not found.'
  if (status === 409) return 'This license number is already on file for a different doctor.'
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

export interface DoctorProfileListResult {
  doctors: DoctorProfileSummary[]
  page: number
  pageSize: number
  totalCount: number
}

export type DoctorSortField = 'specialization' | 'licenseNumber' | 'experienceYears' | 'createdAt' | 'rejectedAt'
export type SortDirection = 'asc' | 'desc'

export interface ListDoctorsParams {
  page?: number
  size?: number
  q?: string
  reason?: RejectionReason
  sort?: DoctorSortField
  direction?: SortDirection
}

// pagination-unification-2026-09-10: paginated server-side - the platform-wide license-
// verification queue grows without bound as more doctors onboard.
//
// super-admin-console-redesign-2026-09-11: `status` replaced the old boolean `verified` param -
// a third Rejected state now exists alongside Pending/Verified. `q`/`reason`/`sort`/`direction`
// added for the same stress-test reason as pagination itself - hundreds to thousands of doctors
// at scale need server-side search and sort, not a client-side filter over a downloaded page.
export async function listDoctors(
  status: DoctorListStatus,
  token: string,
  params: ListDoctorsParams = {},
): Promise<DoctorProfileListResult> {
  const query = new URLSearchParams({ status })
  if (params.page !== undefined) query.set('page', String(params.page))
  if (params.size !== undefined) query.set('size', String(params.size))
  if (params.q) query.set('q', params.q)
  if (params.reason) query.set('reason', params.reason)
  if (params.sort) query.set('sort', params.sort)
  if (params.direction) query.set('direction', params.direction)

  const response = await fetch(`${API_BASE_URL}/api/v1/admin/doctors?${query.toString()}`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  await throwIfNotOk(response)
  return (await response.json()) as DoctorProfileListResult
}

export async function verifyDoctor(doctorProfileId: string, token: string): Promise<VerifyDoctorResponse> {
  const response = await fetch(`${API_BASE_URL}/api/v1/admin/doctors/${doctorProfileId}/verify`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
  })
  await throwIfNotOk(response)
  return (await response.json()) as VerifyDoctorResponse
}

// See specs/033-deverification-cascade-auto-cancel/contracts/doctor-revoke.md
export async function revokeDoctor(doctorProfileId: string, token: string): Promise<RevokeDoctorResponse> {
  const response = await fetch(`${API_BASE_URL}/api/v1/admin/doctors/${doctorProfileId}/revoke`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
  })
  await throwIfNotOk(response)
  return (await response.json()) as RevokeDoctorResponse
}

export async function rejectDoctor(
  doctorProfileId: string,
  reasonCode: RejectionReason,
  detail: string,
  token: string,
): Promise<DoctorProfileSummary> {
  const response = await fetch(`${API_BASE_URL}/api/v1/admin/doctors/${doctorProfileId}/reject`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify({ reasonCode, detail: detail === '' ? undefined : detail }),
  })
  await throwIfNotOk(response)
  return (await response.json()) as DoctorProfileSummary
}

export async function rejectDoctorsBulk(
  doctorProfileIds: string[],
  reasonCode: RejectionReason,
  detail: string,
  token: string,
): Promise<BulkRejectResult> {
  const response = await fetch(`${API_BASE_URL}/api/v1/admin/doctors/reject-bulk`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify({ ids: doctorProfileIds, reasonCode, detail: detail === '' ? undefined : detail }),
  })
  await throwIfNotOk(response)
  return (await response.json()) as BulkRejectResult
}

export async function restoreDoctor(doctorProfileId: string, token: string): Promise<DoctorProfileSummary> {
  const response = await fetch(`${API_BASE_URL}/api/v1/admin/doctors/${doctorProfileId}/restore`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
  })
  await throwIfNotOk(response)
  return (await response.json()) as DoctorProfileSummary
}

/**
 * super-admin-console-redesign: permanent, irreversible delete - only ever valid on an
 * already-rejected profile (enforced server-side too). Refused with a DELETION_BLOCKED error if
 * real activity (a schedule, session, appointment type, default fee, or waitlist entry) is
 * attached.
 */
export async function deleteDoctor(doctorProfileId: string, token: string): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/api/v1/admin/doctors/${doctorProfileId}`, {
    method: 'DELETE',
    headers: { Authorization: `Bearer ${token}` },
  })
  await throwIfNotOk(response)
}

export async function deleteDoctorsBulk(doctorProfileIds: string[], token: string): Promise<BulkDeleteResult> {
  const response = await fetch(`${API_BASE_URL}/api/v1/admin/doctors/delete-bulk`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify({ ids: doctorProfileIds }),
  })
  await throwIfNotOk(response)
  return (await response.json()) as BulkDeleteResult
}

// See specs/008-doctor-license-reverification-reset/contracts/doctor-profile-edit.md
export async function editDoctor(
  doctorProfileId: string,
  payload: EditDoctorRequest,
  token: string,
): Promise<DoctorProfileSummary> {
  const response = await fetch(`${API_BASE_URL}/api/v1/admin/doctors/${doctorProfileId}`, {
    method: 'PATCH',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify(payload),
  })
  await throwIfNotOk(response)
  return (await response.json()) as DoctorProfileSummary
}
