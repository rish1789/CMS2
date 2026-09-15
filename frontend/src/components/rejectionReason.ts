// Shared rejection-reason vocabulary for the Super Admin console's Clinic and Doctor
// verification queues - same values on both entities' backend RejectionReason enums
// (Clinic.RejectionReason / DoctorProfile.RejectionReason), so one shared type and option
// list here instead of duplicating it per feature.
export type RejectionReason =
  | 'DUPLICATE_REGISTRATION'
  | 'SUSPECTED_FRAUD'
  | 'INVALID_DETAILS'
  | 'UNREACHABLE_CONTACT'
  | 'OTHER'

export const REJECTION_REASON_OPTIONS: { value: RejectionReason; label: string }[] = [
  { value: 'DUPLICATE_REGISTRATION', label: 'Duplicate registration' },
  { value: 'SUSPECTED_FRAUD', label: 'Suspected fraud' },
  { value: 'INVALID_DETAILS', label: 'Invalid details' },
  { value: 'UNREACHABLE_CONTACT', label: 'Unreachable contact' },
  { value: 'OTHER', label: 'Other' },
]
