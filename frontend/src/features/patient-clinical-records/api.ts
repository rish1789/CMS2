// Clients for the patient-facing read-only clinical record endpoints (059-patient-clinical-record-access)

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

export interface ConsultationNote {
  id: string
  bookingId: string
  doctorProfileId: string
  content: string
  createdAt: string
}

export async function getConsultationNote(bookingId: string, token: string): Promise<ConsultationNote | null> {
  const response = await fetch(`${API_BASE_URL}/api/v1/patients/bookings/${bookingId}/consultation-note`, {
    headers: { Authorization: `Bearer ${token}` },
  })

  if (!response.ok) {
    throw new Error('Could not load the consultation note for this visit.')
  }

  const text = await response.text()
  return text ? (JSON.parse(text) as ConsultationNote) : null
}

export interface PrescriptionItem {
  id: string
  medicationName: string
  dosage: string
  frequency: string
  duration: string
  instructions: string
}

export interface Prescription {
  id: string
  bookingId: string
  doctorProfileId: string
  createdAt: string
  items: PrescriptionItem[]
}

export async function getPrescriptions(bookingId: string, token: string): Promise<Prescription[]> {
  const response = await fetch(`${API_BASE_URL}/api/v1/patients/bookings/${bookingId}/prescriptions`, {
    headers: { Authorization: `Bearer ${token}` },
  })

  if (!response.ok) {
    throw new Error('Could not load the prescriptions for this visit.')
  }

  return (await response.json()) as Prescription[]
}

export interface ExternalRecordReference {
  id: string
  bookingId: string
  doctorProfileId: string
  recordType: string
  sourceProvider: string
  recordDate: string
  summary: string
  createdAt: string
}

export async function getExternalRecordReferences(bookingId: string, token: string): Promise<ExternalRecordReference[]> {
  const response = await fetch(`${API_BASE_URL}/api/v1/patients/bookings/${bookingId}/external-record-references`, {
    headers: { Authorization: `Bearer ${token}` },
  })

  if (!response.ok) {
    throw new Error('Could not load the external record references for this visit.')
  }

  return (await response.json()) as ExternalRecordReference[]
}

export async function getClinicalRecordAvailability(bookingIds: string[], token: string): Promise<Set<string>> {
  if (bookingIds.length === 0) return new Set()

  const query = new URLSearchParams()
  for (const id of bookingIds) query.append('bookingIds', id)

  const response = await fetch(`${API_BASE_URL}/api/v1/patients/bookings/clinical-record-availability?${query.toString()}`, {
    headers: { Authorization: `Bearer ${token}` },
  })

  if (!response.ok) {
    throw new Error('Could not check which visits have records available.')
  }

  const body = (await response.json()) as { bookingIdsWithRecords: string[] }
  return new Set(body.bookingIdsWithRecords)
}
