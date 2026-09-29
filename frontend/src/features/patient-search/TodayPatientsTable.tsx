import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { getTodayPatients, type TodayPatient } from './api'
import { loadStaffSession } from '../staff-login/token'
import { ListSkeleton } from '../../components/ListSkeleton'

const STATUS_LABEL: Record<TodayPatient['slotStatus'], string> = {
  OPEN: 'Open',
  BOOKED: 'Booked',
  COMPLETED: 'Completed',
  NO_SHOW: 'No-show',
}

// Mirrors SessionSlotsView's own STATUS_BADGE_CLASS palette, so a slot's status reads the
// same way here as it does on the Day Sheet this table summarizes across doctors.
const STATUS_BADGE_CLASS: Record<TodayPatient['slotStatus'], string> = {
  OPEN: 'bg-gray-100 text-gray-600',
  BOOKED: 'bg-cobalt-100 text-cobalt-700',
  COMPLETED: 'bg-green-100 text-green-700',
  NO_SHOW: 'bg-red-100 text-red-700',
}

function formatTime(patient: TodayPatient): string {
  if (patient.tokenNumber !== null) return `Token ${patient.tokenNumber}`
  if (patient.startTime) return patient.startTime.slice(0, 5)
  return '—'
}

// real-bug-fix 2026-09-17: the Find a Patient page's default view - every appointment-based
// and walk-in patient with an active booking today, across every doctor, so front-desk staff
// has something useful to look at before typing a single character into the search box below.
export function TodayPatientsTable({ clinicId }: { clinicId: string }) {
  const [patients, setPatients] = useState<TodayPatient[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const session = loadStaffSession()
    if (!session) return
    getTodayPatients(clinicId, session.token)
      .then(setPatients)
      .catch(() => setError('Failed to load today’s patients.'))
  }, [clinicId])

  return (
    <div className="rounded-xl border border-gray-200 bg-white shadow-sm">
      <div className="border-b border-gray-100 px-4 py-3">
        <h2 className="text-sm font-semibold text-gray-900">Today’s patients</h2>
        <p className="mt-0.5 text-sm text-gray-500">Every appointment and walk-in booked today, across all doctors.</p>
      </div>

      {error && (
        <p role="alert" className="m-4 rounded-md bg-red-50 p-3 text-sm text-red-700">
          {error}
        </p>
      )}

      {patients === null && !error && (
        <div className="p-4">
          <ListSkeleton rows={3} />
        </div>
      )}

      {!error && patients !== null && patients.length === 0 && (
        <p className="p-4 text-sm text-gray-500">No patients booked today yet.</p>
      )}

      {!error && patients !== null && patients.length > 0 && (
        <div className="overflow-x-auto">
          <table className="w-full text-left">
            <thead>
              <tr className="border-b border-gray-100 bg-gray-50 text-xs font-semibold uppercase tracking-wide text-gray-400">
                <th scope="col" className="px-4 py-2 font-semibold">
                  Time
                </th>
                <th scope="col" className="px-4 py-2 font-semibold">
                  Patient
                </th>
                <th scope="col" className="px-4 py-2 font-semibold">
                  Doctor
                </th>
                <th scope="col" className="px-4 py-2 font-semibold">
                  Type
                </th>
                <th scope="col" className="px-4 py-2 font-semibold">
                  Status
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {patients.map((patient) => (
                <tr key={patient.bookingId} className="transition-colors duration-150 hover:bg-gray-50">
                  <td className="px-4 py-2.5 text-sm tabular-nums text-gray-900">{formatTime(patient)}</td>
                  <td className="px-4 py-2.5 text-sm">
                    <Link
                      to={`/staff/clinics/${clinicId}/patients/${patient.patientId}`}
                      className="font-medium text-gray-900 transition-colors duration-150 hover:text-indigo-700"
                    >
                      {patient.patientName}
                    </Link>
                    {patient.patientPhone && <p className="text-sm text-gray-500">{patient.patientPhone}</p>}
                  </td>
                  <td className="px-4 py-2.5 text-sm text-gray-600">{patient.doctorName}</td>
                  <td className="px-4 py-2.5 text-sm">
                    {patient.isWalkIn ? (
                      <span className="rounded-full bg-gray-100 px-1.5 py-0.5 text-xs font-medium text-gray-500">
                        Walk-in
                      </span>
                    ) : (
                      <span className="rounded-full bg-indigo-50 px-1.5 py-0.5 text-xs font-medium text-indigo-600">
                        Appointment
                      </span>
                    )}
                  </td>
                  <td className="px-4 py-2.5">
                    <span
                      className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-semibold ${STATUS_BADGE_CLASS[patient.slotStatus]}`}
                    >
                      <span aria-hidden="true" className="h-1.5 w-1.5 rounded-full bg-current" />
                      {STATUS_LABEL[patient.slotStatus]}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
