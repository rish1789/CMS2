// Client for POST /api/v1/patients/clinics/{clinicId}/waitlist (patient self-service)
// See specs/031-waitlist-matching-longest-waiting/contracts/waitlist-join.md
// and POST /api/v1/patients/waitlist-entries/{entryId}/claim|decline
// See specs/032-self-service-waitlist-claim/contracts/waitlist-claim.md
// 046-frontend-api-client: migrated onto the shared apiClient (see its own ApiError export).

import { apiRequest } from '../../lib/apiClient'

export interface WaitlistEntryResponse {
  id: string
  clinicId: string
  doctorProfileId: string | null
  specialization: string | null
  status: 'WAITING' | 'OFFERED' | 'CLAIMED' | 'EXPIRED'
  joinedAt: string
  offeredAt: string | null
  offerExpiresAt: string | null
  // The *matched* doctor for an OFFERED entry - distinct from doctorProfileId above, which is
  // the originally-requested doctor and is null for a specialization-only join even once
  // matched. ClaimOfferCard needs this to fetch that doctor's appointment types.
  offeredDoctorProfileId: string | null
}

export interface JoinWaitlistRequest {
  doctorProfileId?: string
  specialization?: string
}

// _diagnostics [HIGH] - [WAITLIST_JOIN staff] - [MISSING_STAFF_UI]
export interface StaffJoinWaitlistRequest {
  patientAccountId: string
  doctorProfileId?: string
  specialization?: string
}

export type WaitlistJoinErrorBody =
  | { error: 'CLINIC_NOT_FOUND'; message?: string }
  | { error: 'DOCTOR_NOT_STAFFED_AT_CLINIC'; message?: string }
  | { error: 'WAITLIST_TARGET_REQUIRED'; message?: string }
  | { error: 'PATIENT_ACCOUNT_NOT_FOUND'; message?: string }
  | { error: 'DOCTOR_PROFILE_NOT_FOUND'; message?: string }
  | { error: 'UNAUTHORIZED'; message?: string }
  | { error: 'FORBIDDEN'; message?: string }

function defaultMessageFor(body: unknown): string {
  const error = (body as WaitlistJoinErrorBody | undefined)?.error
  switch (error) {
    case 'CLINIC_NOT_FOUND':
      return 'This clinic could not be found.'
    case 'DOCTOR_NOT_STAFFED_AT_CLINIC':
      return 'That doctor is not staffed at this clinic.'
    case 'WAITLIST_TARGET_REQUIRED':
      return 'Please choose either a doctor or a specialization.'
    case 'PATIENT_ACCOUNT_NOT_FOUND':
      return 'Your patient account could not be found.'
    case 'DOCTOR_PROFILE_NOT_FOUND':
      return 'That doctor could not be found.'
    case 'UNAUTHORIZED':
      return 'Your session has expired. Please sign in again.'
    case 'FORBIDDEN':
      return 'Only front-desk Operations staff or a ClinicAdmin can join a patient to the waitlist.'
    default:
      return 'Something went wrong. Please try again.'
  }
}

export async function joinWaitlist(
  clinicId: string,
  request: JoinWaitlistRequest,
  token: string,
): Promise<WaitlistEntryResponse> {
  return apiRequest<WaitlistEntryResponse>(`/api/v1/patients/clinics/${clinicId}/waitlist`, {
    method: 'POST',
    token,
    body: request,
    fallbackMessage: defaultMessageFor,
  })
}

// _diagnostics [HIGH] - [WAITLIST_CLAIM] - [WORKFLOW_GAP]
export async function listMyWaitlistEntries(token: string): Promise<WaitlistEntryResponse[]> {
  return apiRequest<WaitlistEntryResponse[]>('/api/v1/patients/waitlist-entries', {
    token,
    fallbackMessage: 'Your session has expired. Please sign in again.',
  })
}

// _diagnostics [HIGH] - [WAITLIST_JOIN staff] - [MISSING_STAFF_UI]
export async function staffJoinWaitlist(
  clinicId: string,
  request: StaffJoinWaitlistRequest,
  token: string,
): Promise<WaitlistEntryResponse> {
  return apiRequest<WaitlistEntryResponse>(`/api/v1/clinics/${clinicId}/waitlist`, {
    method: 'POST',
    token,
    body: request,
    fallbackMessage: defaultMessageFor,
  })
}

export interface ClaimWaitlistRequest {
  appointmentTypeId: string
  patientName: string
}

export interface WaitlistClaimResponse {
  id: string
  slotId: string
  patientId: string
  appointmentTypeId: string
  lockedFee: number
  paymentStatus: 'PENDING' | 'PAID'
  status: 'ACTIVE' | 'CANCELLED'
  createdAt: string
}

export type WaitlistClaimErrorBody =
  | { error: 'CLINIC_NOT_ACCEPTING_APPOINTMENTS'; message?: string }
  | { error: 'WAITLIST_ENTRY_NOT_FOUND'; message?: string }
  | { error: 'WAITLIST_OFFER_NOT_CLAIMABLE'; message?: string }
  | { error: 'APPOINTMENT_TYPE_NOT_FOUND'; message?: string }
  | { error: 'NO_FEE_CONFIGURED'; message?: string }
  | { error: 'SLOT_ALREADY_BOOKED'; message?: string }
  | { error: 'UNAUTHORIZED'; message?: string }

function defaultClaimMessageFor(body: unknown): string {
  const error = (body as WaitlistClaimErrorBody | undefined)?.error
  switch (error) {
    case 'WAITLIST_ENTRY_NOT_FOUND':
      return 'This waitlist offer could not be found.'
    case 'WAITLIST_OFFER_NOT_CLAIMABLE':
      return 'This offer is no longer available to claim.'
    case 'APPOINTMENT_TYPE_NOT_FOUND':
      return 'That appointment type could not be found.'
    case 'NO_FEE_CONFIGURED':
      return 'No fee is configured for this doctor yet.'
    case 'SLOT_ALREADY_BOOKED':
      return 'This slot was just booked by someone else.'
    case 'UNAUTHORIZED':
      return 'Your session has expired. Please sign in again.'
    case 'CLINIC_NOT_ACCEPTING_APPOINTMENTS':
      return 'This clinic is not accepting appointments.'
    default:
      return 'Something went wrong. Please try again.'
  }
}

export async function claimOffer(
  entryId: string,
  request: ClaimWaitlistRequest,
  token: string,
): Promise<WaitlistClaimResponse> {
  return apiRequest<WaitlistClaimResponse>(`/api/v1/patients/waitlist-entries/${entryId}/claim`, {
    method: 'POST',
    token,
    body: request,
    fallbackMessage: defaultClaimMessageFor,
  })
}

export interface WaitlistCountResponse {
  waitingCount: number
}

// dashboard-live-data-2026-09-10: the clinic tools dashboard's "waitlist backlog" tile.
export async function getWaitlistCount(clinicId: string, token: string): Promise<WaitlistCountResponse> {
  return apiRequest<WaitlistCountResponse>(`/api/v1/clinics/${clinicId}/waitlist/count`, {
    token,
    fallbackMessage: 'Only front-desk Operations staff or a ClinicAdmin can view the waitlist count.',
  })
}

export async function declineOffer(entryId: string, token: string): Promise<WaitlistEntryResponse> {
  return apiRequest<WaitlistEntryResponse>(`/api/v1/patients/waitlist-entries/${entryId}/decline`, {
    method: 'POST',
    token,
    fallbackMessage: defaultClaimMessageFor,
  })
}
