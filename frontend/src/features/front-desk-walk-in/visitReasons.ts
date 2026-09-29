// 063-front-desk-walk-in (FR-004): the standard visit-reason list, in the order staff see it.
// "Other" always comes last and needs a short free-text reason.

export type VisitReason =
  | 'FEVER_COLD_COUGH'
  | 'PAIN'
  | 'FOLLOW_UP'
  | 'TEST_REPORT_REVIEW'
  | 'PRESCRIPTION_REFILL'
  | 'INJURY'
  | 'GENERAL_CHECKUP'
  | 'OTHER'

export const VISIT_REASONS: { value: VisitReason; label: string }[] = [
  { value: 'FEVER_COLD_COUGH', label: 'Fever / Cold & cough' },
  { value: 'PAIN', label: 'Pain' },
  { value: 'FOLLOW_UP', label: 'Follow-up visit' },
  { value: 'TEST_REPORT_REVIEW', label: 'Test / report review' },
  { value: 'PRESCRIPTION_REFILL', label: 'Prescription refill' },
  { value: 'INJURY', label: 'Injury' },
  { value: 'GENERAL_CHECKUP', label: 'General check-up' },
  { value: 'OTHER', label: 'Other' },
]

export const VISIT_REASON_DETAIL_MAX = 200

export function visitReasonLabel(reason: VisitReason | null | undefined, detail?: string | null): string | null {
  if (!reason) return null
  if (reason === 'OTHER') return detail ? `Other: ${detail}` : 'Other'
  return VISIT_REASONS.find((r) => r.value === reason)?.label ?? null
}
