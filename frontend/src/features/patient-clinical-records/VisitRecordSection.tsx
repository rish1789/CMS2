import { useEffect, useState } from 'react'
import {
  getConsultationNote,
  getPrescriptions,
  getExternalRecordReferences,
  type ConsultationNote,
  type Prescription,
  type ExternalRecordReference,
} from './api'
import { loadPatientSession } from '../patient-account/token'

// 059-patient-clinical-record-access: a patient's own read-only view of a past visit's
// consultation note, prescriptions, and external record references - rendered on the existing
// /patient/bookings/:bookingId page, alongside QueuePositionIndicator/CancelBookingButton
// (plan.md - extends the existing route, no new one).

export interface VisitRecordSectionProps {
  bookingId: string
}

export function VisitRecordSection({ bookingId }: VisitRecordSectionProps) {
  const [session] = useState(() => loadPatientSession())
  const [note, setNote] = useState<ConsultationNote | null | undefined>(undefined)
  const [prescriptions, setPrescriptions] = useState<Prescription[] | undefined>(undefined)
  const [externalRecords, setExternalRecords] = useState<ExternalRecordReference[] | undefined>(undefined)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!session) return
    setNote(undefined)
    getConsultationNote(bookingId, session.token)
      .then(setNote)
      .catch(() => setError('Could not load your visit record.'))

    setPrescriptions(undefined)
    getPrescriptions(bookingId, session.token)
      .then(setPrescriptions)
      .catch(() => setError('Could not load your visit record.'))

    setExternalRecords(undefined)
    getExternalRecordReferences(bookingId, session.token)
      .then(setExternalRecords)
      .catch(() => setError('Could not load your visit record.'))
  }, [bookingId, session])

  if (!session) return null

  return (
    <div className="rounded-xl border border-gray-200 bg-white p-4 shadow-sm">
      <h2 className="text-sm font-semibold text-gray-900">Visit record</h2>

      {error && (
        <p role="alert" className="mt-2 rounded-md bg-red-50 p-2 text-sm text-red-700">
          {error}
        </p>
      )}

      <div className="mt-3">
        <h3 className="text-xs font-medium tracking-wide text-gray-500 uppercase">Consultation note</h3>
        {note === undefined && !error && <p className="mt-1 text-sm text-gray-400">Loading…</p>}
        {note === null && <p className="mt-1 text-sm text-gray-500">No consultation note for this visit.</p>}
        {note && <p className="mt-1 whitespace-pre-wrap text-sm text-gray-700">{note.content}</p>}
      </div>

      <div className="mt-4">
        <h3 className="text-xs font-medium tracking-wide text-gray-500 uppercase">Prescriptions</h3>
        {prescriptions === undefined && !error && <p className="mt-1 text-sm text-gray-400">Loading…</p>}
        {prescriptions && prescriptions.length === 0 && (
          <p className="mt-1 text-sm text-gray-500">No prescriptions for this visit.</p>
        )}
        {prescriptions && prescriptions.length > 0 && (
          <ul className="mt-1 space-y-2">
            {prescriptions.map((prescription) => (
              <li key={prescription.id} className="space-y-1">
                {prescription.items.map((item) => (
                  <p key={item.id} className="text-sm text-gray-700">
                    <span className="font-medium">{item.medicationName}</span> · {item.dosage} · {item.frequency} ·{' '}
                    {item.duration}
                    {item.instructions && <span className="text-gray-500"> ({item.instructions})</span>}
                  </p>
                ))}
              </li>
            ))}
          </ul>
        )}
      </div>

      <div className="mt-4">
        <h3 className="text-xs font-medium tracking-wide text-gray-500 uppercase">External records</h3>
        {externalRecords === undefined && !error && <p className="mt-1 text-sm text-gray-400">Loading…</p>}
        {externalRecords && externalRecords.length === 0 && (
          <p className="mt-1 text-sm text-gray-500">No external records for this visit.</p>
        )}
        {externalRecords && externalRecords.length > 0 && (
          <ul className="mt-1 space-y-2">
            {externalRecords.map((reference) => (
              <li key={reference.id} className="text-sm text-gray-700">
                <span className="font-medium">{reference.recordType}</span> · {reference.sourceProvider} ·{' '}
                {reference.recordDate}
                <p className="text-gray-500">{reference.summary}</p>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  )
}
