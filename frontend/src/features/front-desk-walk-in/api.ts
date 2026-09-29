// 063-front-desk-walk-in (contracts/front-desk-walk-in.md section 1):
// POST /api/v1/clinics/{clinicId}/walk-ins - one front-desk registration for both session modes.

import { apiRequest } from '../../lib/apiClient'
import type { VisitReason } from './visitReasons'

export interface RegisterWalkInRequest {
  sessionId: string
  patientId?: string
  patientName?: string
  patientPhone?: string
  patientEmail?: string
  appointmentTypeId: string
  visitReason: VisitReason
  visitReasonDetail?: string
  confirmDuplicate: boolean
}

export interface WalkInRegistration {
  bookingId: string
  slotId: string
  sessionId: string
  mode: 'FIXED_TIME' | 'QUEUE'
  tokenNumber: number | null
  // The place in a Fixed-Time session's walk-in line; null for a Queue token.
  walkInPosition: number | null
  patientId: string
  patientName: string
  doctorName: string
  appointmentTypeId: string
  lockedFee: number
  visitReason: VisitReason
  visitReasonDetail: string | null
}

export type WalkInErrorCode =
  | 'VISIT_REASON_REQUIRED'
  | 'VISIT_REASON_DETAIL_REQUIRED'
  | 'PATIENT_REQUIRED'
  | 'INVALID_MOBILE_NUMBER'
  | 'INVALID_EMAIL'
  | 'DUPLICATE_WALK_IN'
  | 'NO_FEE_CONFIGURED'
  | 'CLINIC_NOT_ACCEPTING_APPOINTMENTS'
  | 'CLINIC_NOT_ACTIVE'
  | 'FORBIDDEN'
  | 'TOKEN_ISSUANCE_FAILED'

function fallbackMessage(body: unknown): string {
  const error = (body as { error?: WalkInErrorCode } | undefined)?.error
  switch (error) {
    case 'VISIT_REASON_REQUIRED':
      return 'Choose why the patient came in.'
    case 'VISIT_REASON_DETAIL_REQUIRED':
      return 'Describe the reason for the visit (up to 200 characters).'
    case 'PATIENT_REQUIRED':
      return "Select an existing patient or enter the new patient's name."
    case 'INVALID_MOBILE_NUMBER':
      return 'Enter a valid 10-digit mobile number, or leave it blank.'
    case 'INVALID_EMAIL':
      return 'Enter a valid email address, or leave it blank.'
    case 'DUPLICATE_WALK_IN':
      return 'This patient is already in this session today. Register them again?'
    case 'NO_FEE_CONFIGURED':
      return 'No fee is set up for this doctor and appointment type yet.'
    case 'CLINIC_NOT_ACCEPTING_APPOINTMENTS':
      return 'This clinic is not accepting appointments.'
    case 'FORBIDDEN':
      return 'Only front-desk staff and clinic admins can register walk-ins.'
    case 'TOKEN_ISSUANCE_FAILED':
      return 'Could not add the patient to the line right now. Please try again.'
    default:
      return 'Something went wrong. Please try again.'
  }
}

export function registerWalkIn(clinicId: string, payload: RegisterWalkInRequest, token: string): Promise<WalkInRegistration> {
  return apiRequest<WalkInRegistration>(`/api/v1/clinics/${clinicId}/walk-ins`, {
    method: 'POST',
    token,
    body: payload,
    fallbackMessage,
  })
}
