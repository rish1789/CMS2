// Client for POST /api/v1/patients/clinics/{clinicId}/waitlist (patient self-service)
// See specs/031-waitlist-matching-longest-waiting/contracts/waitlist-join.md
// and POST /api/v1/patients/waitlist-entries/{entryId}/claim|decline
// See specs/032-self-service-waitlist-claim/contracts/waitlist-claim.md

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

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

export class WaitlistJoinApiError extends Error {
  readonly body: WaitlistJoinErrorBody

  constructor(body: WaitlistJoinErrorBody) {
    super(defaultMessageFor(body) ?? body.message)
    this.name = 'WaitlistJoinApiError'
    this.body = body
  }
}

function defaultMessageFor(body: WaitlistJoinErrorBody): string {
  switch (body.error) {
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
  const response = await fetch(`${API_BASE_URL}/api/v1/patients/clinics/${clinicId}/waitlist`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(request),
  })

  if (!response.ok) {
    let body: WaitlistJoinErrorBody
    try {
      body = (await response.json()) as WaitlistJoinErrorBody
    } catch {
      body = { error: 'CLINIC_NOT_FOUND' }
    }
    throw new WaitlistJoinApiError(body)
  }

  return (await response.json()) as WaitlistEntryResponse
}

// _diagnostics [HIGH] - [WAITLIST_CLAIM] - [WORKFLOW_GAP]
export async function listMyWaitlistEntries(token: string): Promise<WaitlistEntryResponse[]> {
  const response = await fetch(`${API_BASE_URL}/api/v1/patients/waitlist-entries`, {
    headers: { Authorization: `Bearer ${token}` },
  })

  if (!response.ok) {
    throw new WaitlistJoinApiError({ error: 'UNAUTHORIZED' })
  }

  return (await response.json()) as WaitlistEntryResponse[]
}

// _diagnostics [HIGH] - [WAITLIST_JOIN staff] - [MISSING_STAFF_UI]
export async function staffJoinWaitlist(
  clinicId: string,
  request: StaffJoinWaitlistRequest,
  token: string,
): Promise<WaitlistEntryResponse> {
  const response = await fetch(`${API_BASE_URL}/api/v1/clinics/${clinicId}/waitlist`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(request),
  })

  if (!response.ok) {
    let body: WaitlistJoinErrorBody
    try {
      body = (await response.json()) as WaitlistJoinErrorBody
    } catch {
      body = { error: 'CLINIC_NOT_FOUND' }
    }
    throw new WaitlistJoinApiError(body)
  }

  return (await response.json()) as WaitlistEntryResponse
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
  | { error: 'WAITLIST_ENTRY_NOT_FOUND'; message?: string }
  | { error: 'WAITLIST_OFFER_NOT_CLAIMABLE'; message?: string }
  | { error: 'APPOINTMENT_TYPE_NOT_FOUND'; message?: string }
  | { error: 'NO_FEE_CONFIGURED'; message?: string }
  | { error: 'SLOT_ALREADY_BOOKED'; message?: string }
  | { error: 'UNAUTHORIZED'; message?: string }

export class WaitlistClaimApiError extends Error {
  readonly body: WaitlistClaimErrorBody

  constructor(body: WaitlistClaimErrorBody) {
    super(defaultClaimMessageFor(body) ?? body.message)
    this.name = 'WaitlistClaimApiError'
    this.body = body
  }
}

function defaultClaimMessageFor(body: WaitlistClaimErrorBody): string {
  switch (body.error) {
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
    default:
      return 'Something went wrong. Please try again.'
  }
}

export async function claimOffer(
  entryId: string,
  request: ClaimWaitlistRequest,
  token: string,
): Promise<WaitlistClaimResponse> {
  const response = await fetch(`${API_BASE_URL}/api/v1/patients/waitlist-entries/${entryId}/claim`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(request),
  })

  if (!response.ok) {
    let body: WaitlistClaimErrorBody
    try {
      body = (await response.json()) as WaitlistClaimErrorBody
    } catch {
      body = { error: 'WAITLIST_ENTRY_NOT_FOUND' }
    }
    throw new WaitlistClaimApiError(body)
  }

  return (await response.json()) as WaitlistClaimResponse
}

export interface WaitlistCountResponse {
  waitingCount: number
}

// dashboard-live-data-2026-09-10: the clinic tools dashboard's "waitlist backlog" tile.
export async function getWaitlistCount(clinicId: string, token: string): Promise<WaitlistCountResponse> {
  const response = await fetch(`${API_BASE_URL}/api/v1/clinics/${clinicId}/waitlist/count`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  if (!response.ok) {
    throw new WaitlistJoinApiError({ error: 'FORBIDDEN' })
  }
  return (await response.json()) as WaitlistCountResponse
}

export async function declineOffer(entryId: string, token: string): Promise<WaitlistEntryResponse> {
  const response = await fetch(`${API_BASE_URL}/api/v1/patients/waitlist-entries/${entryId}/decline`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${token}`,
    },
  })

  if (!response.ok) {
    let body: WaitlistClaimErrorBody
    try {
      body = (await response.json()) as WaitlistClaimErrorBody
    } catch {
      body = { error: 'WAITLIST_ENTRY_NOT_FOUND' }
    }
    throw new WaitlistClaimApiError(body)
  }

  return (await response.json()) as WaitlistEntryResponse
}
